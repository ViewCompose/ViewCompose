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
 * 将框架持有的导航栈挂载为原生 Android View 层级。
 * Mounts one framework-owned navigation stack as a native Android View hierarchy.
 *
 * 目的地内容会接收当前活跃的 [NavEntry]。其 lifecycle、ViewModel store、保存状态命名空间和
 * 子渲染会话由此宿主管理，而不是由 Activity 或 Fragment 管理。
 * Destination content receives the active [NavEntry]. Its lifecycle, ViewModel store, saved-state
 * namespace, and child render session are owned by this host rather than an Activity or Fragment.
 *
 * [LocalNavGraphOwnerScope] 暴露所有活跃父图 owner，[ProvideNavGraphOwner] 允许子树直接
 * 使用某个图的共享状态。
 * [LocalNavGraphOwnerScope] exposes every active parent-graph owner, while [ProvideNavGraphOwner]
 * lets a subtree use one graph's shared state directly.
 *
 * 当 [systemBackEnabled] 为 true 时，仅在当前栈可弹出时参与最近的 AndroidX back dispatcher。
 * predictive back 会预览上一个目的地，但在手势完成前不会改变已提交栈。
 * When [systemBackEnabled] is true, the host participates in the nearest AndroidX back dispatcher
 * only while its stack can pop. Predictive-back progress previews the previous destination without
 * changing the committed stack until the gesture completes.
 *
 * [panePolicy] 可以把同一批已提交 entry 自适应展示为多个原生 View pane，不会重建对应的
 * lifecycle、ViewModel 或 saved-state owner。
 * [panePolicy] can adapt the same committed entries into multiple native View panes without
 * recreating their lifecycle, ViewModel, or saved-state owners.
 *
 * 当目的地内容或继承 local 依赖不可观察的父级值时，应变更 [contentKey]。可观察状态会直接
 * 使目的地会话失效，所以普通导航和状态变化不会同步刷新每个保留页面。
 * [contentKey] must change when destination content or inherited locals depend on a non-observable
 * parent value. Observable state invalidates destination sessions directly, so ordinary navigation
 * and state changes do not synchronously refresh every retained page.
 */
fun UiTreeBuilder.NavHost(
    controller: NavHostController,
    modifier: Modifier = Modifier,
    transitionSpec: NavTransitionSpec = NavTransitionSpec.Default,
    panePolicy: NavPanePolicy = NavPanePolicy.Single,
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
    val config = NavHostRuntimeConfig(
        localSnapshot = captureUiLocalSnapshot(),
        lifecycleOwner = lifecycleOwner,
        transitionSpec = transitionSpec,
        panePolicy = panePolicy,
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

/**
 * 只在会改变 AndroidView 身份的宿主输入发生变化时触发重建。
 * Recreates AndroidView only when host inputs that affect identity change.
 */
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

/**
 * 默认复用宿主容器的 overlay 能力，缺失时安全降级为空实现。
 * Reuses the host container overlay by default and falls back to a no-op implementation.
 */
private val defaultNavOverlayHostFactory: (ViewGroup) -> OverlayHost = { root ->
    OverlayHostDefaults.androidOrNoOp(root)
}
