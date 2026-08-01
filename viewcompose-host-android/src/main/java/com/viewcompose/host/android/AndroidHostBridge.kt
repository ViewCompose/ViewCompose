package com.viewcompose.host.android

import android.content.Context
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStoreOwner
import com.viewcompose.host.android.runtime.AndroidMonotonicFrameClock
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import com.viewcompose.widget.core.ProvideAnimationCoroutineContext
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.ProvideMonotonicFrameClock
import com.viewcompose.widget.core.ProvideSaveableStateRegistry
import com.viewcompose.widget.core.ProvideLocal
import com.viewcompose.widget.core.AndroidDynamicColorPolicy
import com.viewcompose.widget.core.AndroidResolvedTheme
import com.viewcompose.widget.core.AndroidThemeBridge
import com.viewcompose.widget.core.AndroidThemeRefreshController
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.RenderTreeResult
import com.viewcompose.widget.core.RenderFailure
import com.viewcompose.widget.core.LocalRenderResultListener
import com.viewcompose.widget.core.UiEnvironment
import com.viewcompose.widget.core.UiTheme
import com.viewcompose.widget.core.UiTreeBuilder
import java.util.WeakHashMap
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

private val defaultMonotonicFrameClock = AndroidMonotonicFrameClock()
private val defaultAnimationCoroutineContext: CoroutineContext = Dispatchers.Main.immediate

/**
 * 创建并返回 Fragment 内容根节点，并将内部 RenderSession 绑定到 Fragment view lifecycle。
 * Creates and returns a Fragment content root and binds the internal RenderSession to the Fragment view lifecycle.
 *
 * 当 view lifecycle 销毁或 Fragment 销毁时，会自动释放 session。
 * The session is disposed automatically when either the view lifecycle or Fragment lifecycle is destroyed.
 */
fun Fragment.setUiContent(
    debug: Boolean = false,
    debugTag: String = "ViewCompose",
    dynamicColorPolicy: AndroidDynamicColorPolicy = AndroidDynamicColorPolicy.UseIfAvailable,
    themeRefreshController: AndroidThemeRefreshController? = null,
    overlayHostFactory: (ViewGroup) -> OverlayHost = { root -> OverlayHostDefaults.androidOrNoOp(root) },
    onRenderStats: ((RenderStats) -> Unit)? = null,
    onRenderResult: ((RenderTreeResult) -> Unit)? = null,
    onRenderFailure: ((RenderFailure) -> Unit)? = null,
    content: UiTreeBuilder.(ViewGroup) -> Unit,
): ViewGroup {
    requireActiveHost(
        owner = this,
        hostName = "Fragment",
    )
    FragmentRenderSessionRegistry.clear(this)
    val saveableStateRegistry = viewComposeSaveableStateRegistry(this)
    val resolvedTheme = AndroidThemeBridge.resolveContext(
        context = requireContext(),
        dynamicColorPolicy = dynamicColorPolicy,
    )
    val root = buildUiContentRoot(
        context = resolvedTheme.context,
    )
    val session = renderInto(
        container = root,
        debug = debug,
        debugTag = debugTag,
        overlayHost = overlayHostFactory(root),
        onRenderStats = onRenderStats,
        onRenderResult = onRenderResult,
        onRenderFailure = onRenderFailure,
    ) {
        withHostEnvironment(
            root = root,
            lifecycleOwner = this@setUiContent,
            viewModelStoreOwner = this@setUiContent,
            saveableStateRegistry = saveableStateRegistry,
            resolvedTheme = resolvedTheme,
            themeRefreshController = themeRefreshController,
            onRenderResult = onRenderResult,
            content = content,
        )
    }
    FragmentRenderSessionRegistry.bind(
        fragment = this,
        session = session,
    )
    return root
}

/**
 * 为 ComponentActivity 创建根容器、调用 setContentView，并启动 ViewCompose 渲染会话。
 * Creates the root container for ComponentActivity, calls setContentView, and starts a ViewCompose render session.
 *
 * 重复调用会先释放之前绑定到该 Activity 的 session。
 * Repeated calls dispose the previous session bound to the Activity before rendering new content.
 */
fun ComponentActivity.setUiContent(
    debug: Boolean = false,
    debugTag: String = "ViewCompose",
    dynamicColorPolicy: AndroidDynamicColorPolicy = AndroidDynamicColorPolicy.UseIfAvailable,
    themeRefreshController: AndroidThemeRefreshController? = null,
    overlayHostFactory: (ViewGroup) -> OverlayHost = { root -> OverlayHostDefaults.androidOrNoOp(root) },
    onRenderStats: ((RenderStats) -> Unit)? = null,
    onRenderResult: ((RenderTreeResult) -> Unit)? = null,
    onRenderFailure: ((RenderFailure) -> Unit)? = null,
    content: UiTreeBuilder.(ViewGroup) -> Unit,
): ViewGroup {
    requireActiveHost(
        owner = this,
        hostName = "ComponentActivity",
    )
    ActivityRenderSessionRegistry.clear(this)
    val saveableStateRegistry = viewComposeSaveableStateRegistry(this)
    val resolvedTheme = AndroidThemeBridge.resolveContext(
        context = this,
        dynamicColorPolicy = dynamicColorPolicy,
    )
    val root = buildUiContentRoot(
        context = resolvedTheme.context,
    )
    setContentView(root)
    val session = renderInto(
        container = root,
        debug = debug,
        debugTag = debugTag,
        overlayHost = overlayHostFactory(root),
        onRenderStats = onRenderStats,
        onRenderResult = onRenderResult,
        onRenderFailure = onRenderFailure,
    ) {
        withHostEnvironment(
            root = root,
            lifecycleOwner = this@setUiContent,
            viewModelStoreOwner = this@setUiContent,
            saveableStateRegistry = saveableStateRegistry,
            resolvedTheme = resolvedTheme,
            themeRefreshController = themeRefreshController,
            onRenderResult = onRenderResult,
            content = content,
        )
    }
    ActivityRenderSessionRegistry.bind(
        activity = this,
        session = session,
    )
    return root
}

private fun buildUiContentRoot(
    context: Context,
): ViewDecorationHostLayout {
    return ViewDecorationHostLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

/**
 * 向 DSL 子树注入 Android host 层提供的生命周期、状态、主题和动画上下文。
 * Injects lifecycle, state, theme, and animation context provided by the Android host into a DSL subtree.
 */
private fun UiTreeBuilder.withHostEnvironment(
    root: ViewGroup,
    lifecycleOwner: LifecycleOwner,
    viewModelStoreOwner: ViewModelStoreOwner,
    saveableStateRegistry: com.viewcompose.widget.core.SaveableStateRegistry,
    resolvedTheme: AndroidResolvedTheme,
    themeRefreshController: AndroidThemeRefreshController?,
    onRenderResult: ((RenderTreeResult) -> Unit)?,
    content: UiTreeBuilder.(ViewGroup) -> Unit,
) {
    ProvideLifecycleOwner(lifecycleOwner) {
        ProvideViewModelStoreOwner(viewModelStoreOwner) {
            ProvideSaveableStateRegistry(saveableStateRegistry) {
                ProvideAnimationCoroutineContext(defaultAnimationCoroutineContext) {
                    ProvideMonotonicFrameClock(defaultMonotonicFrameClock) {
                        ProvideLocal(LocalRenderResultListener, onRenderResult) {
                            UiEnvironment(androidContext = root.context) {
                                UiTheme(
                                    resolvedAndroidTheme = resolvedTheme,
                                    refreshController = themeRefreshController,
                                ) {
                                    content(root)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Activity 与 RenderSession 的弱引用注册表。
 * Weak registry that binds ComponentActivity instances to RenderSession objects.
 */
private object ActivityRenderSessionRegistry {
    private val sessions = WeakHashMap<ComponentActivity, RenderSession>()
    private val observers = WeakHashMap<ComponentActivity, DefaultLifecycleObserver>()

    fun clear(activity: ComponentActivity) {
        sessions.remove(activity)?.dispose()
        observers.remove(activity)?.let(activity.lifecycle::removeObserver)
    }

    fun bind(
        activity: ComponentActivity,
        session: RenderSession,
    ) {
        clear(activity)

        // Activity 销毁时释放 session，并移除 observer 避免重复回调。
        // Dispose the session when the Activity is destroyed and remove the observer to avoid repeated callbacks.
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                sessions.remove(activity)?.dispose()
                observers.remove(activity)
                owner.lifecycle.removeObserver(this)
            }
        }
        sessions[activity] = session
        observers[activity] = observer
        activity.lifecycle.addObserver(observer)
    }
}

/**
 * Fragment 与 RenderSession 的弱引用注册表。
 * Weak registry that binds Fragment instances to RenderSession objects.
 *
 * 它同时观察 Fragment lifecycle 和 viewLifecycleOwnerLiveData，确保 view 重建时 session 跟随正确释放。
 * It observes both Fragment lifecycle and viewLifecycleOwnerLiveData so sessions are disposed correctly across view recreation.
 */
private object FragmentRenderSessionRegistry {
    private class Binding(
        val session: RenderSession,
    ) {
        var disposed: Boolean = false
        var fragmentObserver: DefaultLifecycleObserver? = null
        var ownerObserver: Observer<LifecycleOwner>? = null
        var viewLifecycleBinding: LifecycleBoundDisposer? = null
    }

    private val bindings = WeakHashMap<Fragment, Binding>()

    fun bind(
        fragment: Fragment,
        session: RenderSession,
    ) {
        clear(fragment)
        val binding = Binding(session)
        bindings[fragment] = binding
        binding.viewLifecycleBinding = LifecycleBoundDisposer {
            clear(fragment)
        }

        val fragmentObserver = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                clear(fragment)
            }
        }
        binding.fragmentObserver = fragmentObserver
        fragment.lifecycle.addObserver(fragmentObserver)

        val ownerObserver = Observer<LifecycleOwner> { owner ->
            bindViewLifecycle(
                fragment = fragment,
                binding = binding,
                owner = owner,
            )
        }
        binding.ownerObserver = ownerObserver
        fragment.viewLifecycleOwnerLiveData.observe(fragment, ownerObserver)
        fragment.viewLifecycleOwnerLiveData.value?.let { owner ->
            bindViewLifecycle(
                fragment = fragment,
                binding = binding,
                owner = owner,
            )
        }
    }

    private fun bindViewLifecycle(
        fragment: Fragment,
        binding: Binding,
        owner: LifecycleOwner,
    ) {
        if (binding.disposed || bindings[fragment] !== binding) {
            return
        }
        binding.viewLifecycleBinding?.bind(owner)
    }

    fun clear(
        fragment: Fragment,
    ) {
        val binding = bindings.remove(fragment) ?: return
        binding.ownerObserver?.let(fragment.viewLifecycleOwnerLiveData::removeObserver)
        binding.fragmentObserver?.let(fragment.lifecycle::removeObserver)
        binding.viewLifecycleBinding?.clearObserver()
        dispose(binding)
    }

    private fun dispose(
        binding: Binding,
    ) {
        if (binding.disposed) {
            return
        }
        binding.disposed = true
        binding.session.dispose()
    }
}
