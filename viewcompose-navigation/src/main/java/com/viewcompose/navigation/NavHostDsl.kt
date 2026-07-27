package com.viewcompose.navigation

import android.view.View
import android.view.ViewGroup
import com.viewcompose.host.android.AndroidView
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.captureUiLocalSnapshot

/**
 * Mounts one framework-owned navigation stack as a native Android View hierarchy.
 *
 * Destination content receives the active [NavEntry]. Its lifecycle, ViewModel store, saved-state
 * namespace, and child render session are owned by this host rather than an Activity or Fragment.
 * [LocalNavGraphOwnerScope] exposes every active parent-graph owner, while
 * [ProvideNavGraphOwner] lets a subtree use one graph's shared state directly.
 * When [systemBackEnabled] is true, the host participates in the nearest AndroidX back dispatcher
 * only while its stack can pop. Predictive-back progress previews the previous destination without
 * changing the committed stack until the gesture completes.
 * [panePolicy] can adapt the same committed entries into multiple native View panes without
 * recreating their lifecycle, ViewModel, or saved-state owners.
 */
fun UiTreeBuilder.NavHost(
    controller: NavHostController,
    modifier: Modifier = Modifier,
    transitionSpec: NavTransitionSpec = NavTransitionSpec.Default,
    panePolicy: NavPanePolicy = NavPanePolicy.Single,
    systemBackEnabled: Boolean = true,
    debug: Boolean = false,
    debugTag: String = "ViewComposeNavigation",
    overlayHostFactory: (ViewGroup) -> OverlayHost = defaultNavOverlayHostFactory,
    onFailure: ((NavFailure) -> Unit)? = null,
    key: Any? = null,
    content: UiTreeBuilder.(NavEntry) -> Unit,
) {
    val lifecycleOwner = checkNotNull(LocalLifecycleOwner.current) {
        "NavHost requires LocalLifecycleOwner. Mount it under Activity/Fragment setUiContent " +
            "or ProvideLifecycleOwner."
    }
    val config = NavHostRuntimeConfig(
        localSnapshot = captureUiLocalSnapshot(),
        lifecycleOwner = lifecycleOwner,
        transitionSpec = transitionSpec,
        panePolicy = panePolicy,
        systemBackEnabled = systemBackEnabled,
        onFailure = onFailure,
        content = content,
    )
    AndroidView(
        factory = { context ->
            NavHostRuntime.create(
                context = context,
                controller = controller,
                initialConfig = config,
                overlayHostFactory = overlayHostFactory,
                debug = debug,
                debugTag = debugTag,
            ).hostView
        },
        update = { view ->
            view.requireNavHostRuntime().stage(config)
        },
        onCommit = { view ->
            view.requireNavHostRuntime().commitStaged()
        },
        onRelease = { view ->
            val hostView = view as NavHostView
            try {
                hostView.runtime?.destroy()
            } finally {
                hostView.runtime = null
            }
        },
        key = NavHostNodeKey(
            controller = controller,
            lifecycleOwner = lifecycleOwner,
            userKey = key,
            debug = debug,
            debugTag = debugTag,
            overlayHostFactory = overlayHostFactory,
        ),
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
    )
}

private fun View.requireNavHostRuntime(): NavHostRuntime {
    val hostView = this as? NavHostView
        ?: error("NavHost AndroidView mounted an unexpected View type: ${this::class.java.name}.")
    return checkNotNull(hostView.runtime) {
        "NavHost View has no runtime owner."
    }
}

private class NavHostNodeKey(
    private val controller: NavHostController,
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val userKey: Any?,
    private val debug: Boolean,
    private val debugTag: String,
    private val overlayHostFactory: (ViewGroup) -> OverlayHost,
) {
    override fun equals(other: Any?): Boolean {
        return other is NavHostNodeKey &&
            other.controller === controller &&
            other.lifecycleOwner === lifecycleOwner &&
            other.userKey == userKey &&
            other.debug == debug &&
            other.debugTag == debugTag &&
            other.overlayHostFactory === overlayHostFactory
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(controller)
        result = 31 * result + System.identityHashCode(lifecycleOwner)
        result = 31 * result + (userKey?.hashCode() ?: 0)
        result = 31 * result + debug.hashCode()
        result = 31 * result + debugTag.hashCode()
        result = 31 * result + System.identityHashCode(overlayHostFactory)
        return result
    }
}

private val defaultNavOverlayHostFactory: (ViewGroup) -> OverlayHost = { root ->
    OverlayHostDefaults.androidOrNoOp(root)
}
