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
import com.viewcompose.widget.core.RenderStats
import com.viewcompose.widget.core.RenderTreeResult
import com.viewcompose.widget.core.LocalRenderResultListener
import com.viewcompose.widget.core.UiEnvironment
import com.viewcompose.widget.core.UiTreeBuilder
import java.util.WeakHashMap
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

private val defaultMonotonicFrameClock = AndroidMonotonicFrameClock()
private val defaultAnimationCoroutineContext: CoroutineContext = Dispatchers.Main.immediate

/**
 * Creates and returns a Fragment content root and binds the internal [RenderSession]
 * to the Fragment view lifecycle. Session disposal is handled automatically.
 */
fun Fragment.setUiContent(
    debug: Boolean = false,
    debugTag: String = "ViewCompose",
    overlayHostFactory: (ViewGroup) -> OverlayHost = { root -> OverlayHostDefaults.androidOrNoOp(root) },
    onRenderStats: ((RenderStats) -> Unit)? = null,
    onRenderResult: ((RenderTreeResult) -> Unit)? = null,
    content: UiTreeBuilder.(ViewGroup) -> Unit,
): ViewGroup {
    FragmentRenderSessionRegistry.clear(this)
    val saveableStateRegistry = AndroidSaveableStateRegistryStore.registryFor(this)
    val root = buildUiContentRoot(
        context = requireContext(),
    )
    val session = renderInto(
        container = root,
        debug = debug,
        debugTag = debugTag,
        overlayHost = overlayHostFactory(root),
        onRenderStats = onRenderStats,
        onRenderResult = onRenderResult,
    ) {
        withHostEnvironment(
            root = root,
            lifecycleOwner = this@setUiContent,
            viewModelStoreOwner = this@setUiContent,
            saveableStateRegistry = saveableStateRegistry,
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

fun ComponentActivity.setUiContent(
    debug: Boolean = false,
    debugTag: String = "ViewCompose",
    overlayHostFactory: (ViewGroup) -> OverlayHost = { root -> OverlayHostDefaults.androidOrNoOp(root) },
    onRenderStats: ((RenderStats) -> Unit)? = null,
    onRenderResult: ((RenderTreeResult) -> Unit)? = null,
    content: UiTreeBuilder.(ViewGroup) -> Unit,
): ViewGroup {
    ActivityRenderSessionRegistry.clear(this)
    val saveableStateRegistry = AndroidSaveableStateRegistryStore.registryFor(this)
    val root = buildUiContentRoot(
        context = this,
    )
    setContentView(root)
    val session = renderInto(
        container = root,
        debug = debug,
        debugTag = debugTag,
        overlayHost = overlayHostFactory(root),
        onRenderStats = onRenderStats,
        onRenderResult = onRenderResult,
    ) {
        withHostEnvironment(
            root = root,
            lifecycleOwner = this@setUiContent,
            viewModelStoreOwner = this@setUiContent,
            saveableStateRegistry = saveableStateRegistry,
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

private fun UiTreeBuilder.withHostEnvironment(
    root: ViewGroup,
    lifecycleOwner: LifecycleOwner,
    viewModelStoreOwner: ViewModelStoreOwner,
    saveableStateRegistry: com.viewcompose.widget.core.SaveableStateRegistry,
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
                                content(root)
                            }
                        }
                    }
                }
            }
        }
    }
}

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
