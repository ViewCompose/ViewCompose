package com.viewcompose.android

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelStoreOwner
import com.viewcompose.host.android.RenderSession
import com.viewcompose.host.android.renderInto
import com.viewcompose.host.android.resources.AndroidResourceEnvironment
import com.viewcompose.host.android.resources.AndroidResourceRefreshController
import com.viewcompose.host.android.runtime.AndroidMonotonicFrameClock
import com.viewcompose.host.android.viewComposeSaveableStateRegistry
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.overlay.android.AndroidOverlayHost
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import com.viewcompose.ui.foundation.ProvideAnimationCoroutineContext
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.ProvideMonotonicFrameClock
import com.viewcompose.ui.foundation.ProvideSaveableStateRegistry
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.RenderStats
import com.viewcompose.ui.foundation.RenderTreeResult
import com.viewcompose.ui.foundation.RenderFailure
import com.viewcompose.ui.foundation.LocalRenderResultListener
import com.viewcompose.ui.foundation.UiTreeBuilder
import java.util.WeakHashMap
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

private val defaultMonotonicFrameClock = AndroidMonotonicFrameClock()
private val defaultAnimationCoroutineContext: CoroutineContext = Dispatchers.Main.immediate

/**
 * Creates a Fragment ViewCompose root and binds its render session to the view lifecycle.
 *
 * The returned root should be returned from `onCreateView`. Rendering starts as soon as Android
 * publishes that root's `viewLifecycleOwner`, so [content] always receives the View lifecycle rather
 * than the longer-lived Fragment lifecycle. Its session is disposed when either the current View
 * lifecycle or the Fragment lifecycle is destroyed. A repeated call first disposes the previous
 * session. ViewModel and saved-state ownership remain scoped to the Fragment. Android environment,
 * frame clock, and overlay services are also provided to [content]. This neutral entry point does
 * not select a design system or wrap [rootContext]; install design-system tokens inside [content],
 * or use a named Android design-system integration.
 *
 * @sample com.viewcompose.android.samples.fragmentHostSample
 * @param debug enables render diagnostics and logging
 * @param debugTag log tag used by debug rendering
 * @param rootContext context used to create the root, native descendants, and default overlays;
 * changing the root design system requires another call with its newly resolved context
 * @param resourceRefreshController optional host-scoped controller for imperative Android resource
 * or theme mutations that do not dispatch a configuration change
 * @param onBeforeResourceRefresh optional advanced callback that updates a stable themed Context
 * wrapper before the host rereads resources and environment values
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
    rootContext: Context = requireContext(),
    resourceRefreshController: AndroidResourceRefreshController? = null,
    onBeforeResourceRefresh: (() -> Unit)? = null,
    overlayHostFactory: (ViewGroup) -> OverlayHost = { root -> AndroidOverlayHost(root) },
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
    val platform = resolveAndroidHostPlatform(
        rootContext = rootContext,
        resourceRefreshController = resourceRefreshController,
        onBeforeResourceRefresh = onBeforeResourceRefresh,
    )
    val root = buildUiContentRoot(
        context = platform.rootContext,
    )
    FragmentRenderSessionRegistry.bind(
        fragment = this,
        createSession = { viewLifecycleOwner ->
            renderInto(
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
                    lifecycleOwner = viewLifecycleOwner,
                    viewModelStoreOwner = this@setUiContent,
                    saveableStateRegistry = saveableStateRegistry,
                    platform = platform,
                    onRenderResult = onRenderResult,
                    content = content,
                )
            }
        },
    )
    return root
}

/**
 * Installs a ViewCompose root as this Activity's content and starts its render session.
 *
 * A repeated call disposes the previous session before replacing the Activity content View. The
 * host provides lifecycle, ViewModel, saved state, Android environment, frame clock, and overlay
 * services to [content]. This neutral entry point does not select a design system or wrap
 * [rootContext]; install design-system tokens inside [content], or use a named Android
 * design-system integration.
 *
 * @sample com.viewcompose.android.samples.activityHostSample
 * @param debug enables render diagnostics and logging
 * @param debugTag log tag used by debug rendering
 * @param rootContext context used to create the root, native descendants, and default overlays;
 * changing the root design system requires another call with its newly resolved context
 * @param resourceRefreshController optional host-scoped controller for imperative Android resource
 * or theme mutations that do not dispatch a configuration change
 * @param onBeforeResourceRefresh optional advanced callback that updates a stable themed Context
 * wrapper before the host rereads resources and environment values
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
    rootContext: Context = this,
    resourceRefreshController: AndroidResourceRefreshController? = null,
    onBeforeResourceRefresh: (() -> Unit)? = null,
    overlayHostFactory: (ViewGroup) -> OverlayHost = { root -> AndroidOverlayHost(root) },
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
    val platform = resolveAndroidHostPlatform(
        rootContext = rootContext,
        resourceRefreshController = resourceRefreshController,
        onBeforeResourceRefresh = onBeforeResourceRefresh,
    )
    val root = buildUiContentRoot(
        context = platform.rootContext,
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
            platform = platform,
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

/** One immutable platform snapshot shared by root construction and environment provision. */
private data class ResolvedAndroidHostPlatform(
    val rootContext: Context,
    val resourceRefreshController: AndroidResourceRefreshController?,
    val onBeforeResourceRefresh: (() -> Unit)?,
)

private fun resolveAndroidHostPlatform(
    rootContext: Context,
    resourceRefreshController: AndroidResourceRefreshController?,
    onBeforeResourceRefresh: (() -> Unit)?,
): ResolvedAndroidHostPlatform {
    return ResolvedAndroidHostPlatform(
        rootContext = rootContext,
        resourceRefreshController = resourceRefreshController,
        onBeforeResourceRefresh = onBeforeResourceRefresh,
    )
}

/** Provides the Android host's lifecycle, state, environment, and animation context to one subtree. */
private fun UiTreeBuilder.withHostEnvironment(
    root: ViewGroup,
    lifecycleOwner: LifecycleOwner,
    viewModelStoreOwner: ViewModelStoreOwner,
    saveableStateRegistry: com.viewcompose.ui.foundation.SaveableStateRegistry,
    platform: ResolvedAndroidHostPlatform,
    onRenderResult: ((RenderTreeResult) -> Unit)?,
    content: UiTreeBuilder.(ViewGroup) -> Unit,
) {
    ProvideLifecycleOwner(lifecycleOwner) {
        ProvideViewModelStoreOwner(viewModelStoreOwner) {
            ProvideSaveableStateRegistry(saveableStateRegistry) {
                ProvideAnimationCoroutineContext(defaultAnimationCoroutineContext) {
                    ProvideMonotonicFrameClock(defaultMonotonicFrameClock) {
                        ProvideLocal(LocalRenderResultListener, onRenderResult) {
                            AndroidResourceEnvironment(
                                context = platform.rootContext,
                                refreshController = platform.resourceRefreshController,
                                onBeforeRefresh = platform.onBeforeResourceRefresh,
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
        val createSession: (LifecycleOwner) -> RenderSession,
    ) {
        var disposed: Boolean = false
        var session: RenderSession? = null
        var viewLifecycleOwner: LifecycleOwner? = null
        var fragmentObserver: DefaultLifecycleObserver? = null
        var ownerObserver: Observer<LifecycleOwner?>? = null
        var viewLifecycleBinding: LifecycleBoundDisposer? = null
    }

    private val bindings = WeakHashMap<Fragment, Binding>()

    fun bind(
        fragment: Fragment,
        createSession: (LifecycleOwner) -> RenderSession,
    ) {
        clear(fragment)
        val binding = Binding(createSession)
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

        val ownerObserver = object : Observer<LifecycleOwner?> {
            override fun onChanged(owner: LifecycleOwner?) {
                if (owner == null) return
                bindViewLifecycle(
                    fragment = fragment,
                    binding = binding,
                    owner = owner,
                )
            }
        }
        binding.ownerObserver = ownerObserver
        // onCreateView returns before the View owner is published. An always-active observer lets
        // the first frame mount immediately at publication instead of waiting for Fragment STARTED.
        fragment.viewLifecycleOwnerLiveData.observeForever(ownerObserver)
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
        if (binding.viewLifecycleOwner === owner) {
            return
        }
        check(binding.session == null) {
            "Fragment View lifecycle changed without disposing its previous render session."
        }
        binding.viewLifecycleOwner = owner
        binding.session = try {
            binding.createSession(owner)
        } catch (error: Throwable) {
            clear(fragment)
            throw error
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
        binding.session?.dispose()
        binding.session = null
        binding.viewLifecycleOwner = null
    }
}
