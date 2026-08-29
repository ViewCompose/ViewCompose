package com.viewcompose.navigation

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import com.viewcompose.host.android.AndroidView
import com.viewcompose.overlay.android.AndroidOverlayHost
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.captureUiLocalSnapshot
import com.viewcompose.viewmodel.LocalViewModelStoreOwner

/**
 * Mounts one framework-owned navigation stack as a native Android View hierarchy.
 *
 * Destination content receives the active [NavEntry]. Its lifecycle, ViewModel store, saved-state
 * namespace, and child render session are owned by this host rather than an Activity or Fragment.
 *
 * [LocalNavGraphOwnerScope] exposes every active parent-graph owner, while [ProvideNavGraphOwner]
 * lets a subtree use one graph's shared state directly.
 *
 * At native-host creation, destination and graph owners require and capture the nearest
 * [LocalViewModelStoreOwner]. Their stores are retained below that parent by the shared scoped-owner
 * provider. When the parent implements [HasDefaultViewModelProviderFactory], its default Factory and
 * starting [CreationExtras] are inherited while each navigation owner replaces the ViewModelStore
 * owner, saved-state owner, and default route arguments with its own values. Changing the parent
 * owner identity recreates this host so retained navigation owners never mix provider contracts
 * from different parents.
 *
 * When [systemBackEnabled] is true, a started host that can pop participates directly in the nearest
 * AndroidX NavigationEvent dispatcher. If that View tree has no NavigationEvent owner, the host
 * uses the nearest Activity Back dispatcher as a compatibility fallback. At a root entry the host
 * disables its handler so an outer handler or dispatcher fallback can continue. Predictive-back
 * progress previews the previous destination without changing the committed stack until the
 * gesture completes.
 *
 * [panePolicy] can adapt the same committed entries into multiple native View panes without
 * recreating their lifecycle, ViewModel, or saved-state owners.
 *
 * [presentationRetentionPolicy] controls only hidden child render sessions and native Views. The
 * logical entry and its owners remain retained independently, and a disposed presentation is
 * rebuilt before a pop, stack selection, predictive-Back preview, or pane expansion reveals it.
 *
 * [contentKey] must change when destination content or inherited locals depend on a non-observable
 * parent value. Observable state invalidates active destination sessions directly. Hidden retained
 * destinations keep the latest captured environment and render synchronously before a pop, stack
 * selection, predictive-Back preview, or pane expansion makes them visible; the host keeps the
 * previous stack and scene when that refresh fails. Ordinary state changes never refresh every
 * hidden retained page eagerly.
 *
 * @sample com.viewcompose.navigation.samples.rememberedNavHostSample
 * @sample com.viewcompose.navigation.samples.customOverlayNavHostSample
 * @sample com.viewcompose.navigation.samples.inheritedNavViewModelFactorySample
 * @sample com.viewcompose.navigation.samples.retainedDestinationThemeSample
 * @sample com.viewcompose.navigation.samples.BoundedPresentationNavigation
 * @param controller stable controller mounted exclusively by this host
 * @param modifier layout modifier applied after the host's required fill constraint
 * @param transitionSpec visual policy for committed and predictive-Back transitions
 * @param panePolicy width-dependent pane projection
 * @param presentationRetentionPolicy resource policy for fully hidden destination presentations
 * @param systemBackEnabled whether this started host may consume backward platform navigation
 * @param contentKey invalidation key for non-observable destination-content dependencies
 * @param debug enables navigation runtime diagnostics
 * @param debugTag Android log tag used by diagnostics
 * @param overlayHostFactory creates destination overlay support when the native host is created;
 * change [key] to rebuild an existing host with a different factory
 * @param onFailure optional failure handler; unhandled failures throw [NavHostException]
 * @param key application identity component that can force a new native host
 * @param content destination renderer receiving each prepared or refreshed [NavEntry]
 * @throws IllegalStateException when no lifecycle or ViewModelStore owner is provided
 */
fun UiTreeBuilder.NavHost(
    controller: NavHostController,
    modifier: Modifier = Modifier,
    transitionSpec: NavTransitionSpec = NavTransitionSpec.Default,
    panePolicy: NavPanePolicy = NavPanePolicy.Single,
    presentationRetentionPolicy: NavPresentationRetentionPolicy =
        NavPresentationRetentionPolicy.Default,
    systemBackEnabled: Boolean = true,
    contentKey: Any? = Unit,
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
    val parentViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "NavHost requires LocalViewModelStoreOwner. Mount it under Activity/Fragment setUiContent " +
            "or ProvideViewModelStoreOwner."
    }
    val config = NavHostRuntimeConfig(
        localSnapshot = captureUiLocalSnapshot(),
        lifecycleOwner = lifecycleOwner,
        parentViewModelStoreOwner = parentViewModelStoreOwner,
        transitionSpec = transitionSpec,
        panePolicy = panePolicy,
        presentationRetentionPolicy = presentationRetentionPolicy,
        systemBackEnabled = systemBackEnabled,
        onFailure = onFailure,
        contentKey = contentKey,
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
            parentViewModelStoreOwner = parentViewModelStoreOwner,
            userKey = key,
            debug = debug,
            debugTag = debugTag,
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

/** Recreates AndroidView only when host inputs that affect identity change. */
private class NavHostNodeKey(
    private val controller: NavHostController,
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val parentViewModelStoreOwner: ViewModelStoreOwner,
    private val userKey: Any?,
    private val debug: Boolean,
    private val debugTag: String,
) {
    override fun equals(other: Any?): Boolean {
        return other is NavHostNodeKey &&
            other.controller === controller &&
            other.lifecycleOwner === lifecycleOwner &&
            other.parentViewModelStoreOwner === parentViewModelStoreOwner &&
            other.userKey == userKey &&
            other.debug == debug &&
            other.debugTag == debugTag
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(controller)
        result = 31 * result + System.identityHashCode(lifecycleOwner)
        result = 31 * result + System.identityHashCode(parentViewModelStoreOwner)
        result = 31 * result + (userKey?.hashCode() ?: 0)
        result = 31 * result + debug.hashCode()
        result = 31 * result + debugTag.hashCode()
        return result
    }
}

/** Immutable parent defaults captured once when one native NavHost runtime is created. */
internal data class NavViewModelProviderDefaults(
    val factory: ViewModelProvider.Factory?,
    val creationExtras: CreationExtras,
)

internal fun captureNavViewModelProviderDefaults(
    owner: ViewModelStoreOwner?,
): NavViewModelProviderDefaults {
    val providerOwner = owner as? HasDefaultViewModelProviderFactory
        ?: return NavViewModelProviderDefaults(
            factory = null,
            creationExtras = CreationExtras.Empty,
        )
    return try {
        NavViewModelProviderDefaults(
            factory = providerOwner.defaultViewModelProviderFactory,
            creationExtras = MutableCreationExtras(providerOwner.defaultViewModelCreationExtras),
        )
    } catch (failure: RuntimeException) {
        throw IllegalStateException(
            "NavHost could not inherit ViewModel provider defaults from " +
                "${owner.javaClass.name}.",
            failure,
        )
    }
}

/** Creates a neutral, root-scoped Android overlay transport for the nested navigation host. */
private val defaultNavOverlayHostFactory: (ViewGroup) -> OverlayHost = { root ->
    AndroidOverlayHost(root)
}
