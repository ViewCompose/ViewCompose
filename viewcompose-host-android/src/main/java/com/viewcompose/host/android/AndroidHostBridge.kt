package com.viewcompose.host.android

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStoreOwner
import com.viewcompose.host.android.runtime.AndroidMonotonicFrameClock
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
 * Creates a Fragment ViewCompose root and binds its render session to the view lifecycle.
 *
 * The returned root should be returned from `onCreateView`. Its session is disposed when either the
 * current view lifecycle or the Fragment lifecycle is destroyed. A repeated call first disposes the
 * previous session. Lifecycle, ViewModel, saved state, Android environment, theme, frame clock, and
 * overlay services are provided to [content].
 *
 * @sample com.viewcompose.host.android.samples.fragmentHostSample
 * @param debug enables render diagnostics and logging
 * @param debugTag log tag used by debug rendering
 * @param dynamicColorPolicy policy used while resolving Android theme tokens
 * @param themeRefreshController optional controller that invalidates the theme after configuration changes
 * @param overlayHostFactory creates the overlay host for the new root
 * @param onRenderStats optional callback after every attempted frame
 * @param onRenderResult optional callback for collected render diagnostics
 * @param onRenderFailure optional callback when a frame fails
 * @param content declarative content; its ViewGroup argument is the returned root
 * @return the newly created full-size Fragment root
 * @throws IllegalStateException when the Fragment lifecycle is already destroyed
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
 * Installs a ViewCompose root as this Activity's content and starts its render session.
 *
 * A repeated call disposes the previous session before replacing the Activity content View. The
 * host provides lifecycle, ViewModel, saved state, Android environment, theme, frame clock, and
 * overlay services to [content].
 *
 * @sample com.viewcompose.host.android.samples.activityHostSample
 * @param debug enables render diagnostics and logging
 * @param debugTag log tag used by debug rendering
 * @param dynamicColorPolicy policy used while resolving Android theme tokens
 * @param themeRefreshController optional controller that invalidates the theme after configuration changes
 * @param overlayHostFactory creates the overlay host for the new root
 * @param onRenderStats optional callback after every attempted frame
 * @param onRenderResult optional callback for collected render diagnostics
 * @param onRenderFailure optional callback when a frame fails
 * @param content declarative content; its ViewGroup argument is the installed root
 * @return the newly installed full-size Activity root
 * @throws IllegalStateException when the Activity lifecycle is already destroyed
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
): FrameLayout {
    return FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

/** Provides the Android host's lifecycle, state, theme, and animation context to one DSL subtree. */
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

/** Weak registry that binds Activity instances to render sessions without extending their lifetime. */
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

        // Dispose once at destruction and detach the observer to prevent duplicate callbacks.
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
 * Weak registry that follows both Fragment and view lifecycles across View recreation.
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
