package com.viewcompose.camerax

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.AndroidViewCommitScope
import com.viewcompose.host.android.AndroidViewCreateScope
import com.viewcompose.host.android.AndroidViewReusePolicy
import com.viewcompose.host.android.AndroidViewUpdateScope
import com.viewcompose.lifecycle.AndroidViewLifecycleEventScope
import com.viewcompose.lifecycle.LifecycleAndroidViewAdapter
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier

/** Selects the physical lens direction requested by [CameraXPreviewView]. */
enum class CameraXLensFacing {
    /** Requests a rear-facing camera. */
    Back,

    /** Requests a front-facing camera. */
    Front,
}

/** Selects the constructor-owned native Surface strategy used by [CameraXPreviewView]. */
enum class CameraXPreviewImplementationMode {
    /**
     * Prefers CameraX's lower-power `SurfaceView` path when the device supports it.
     *
     * This mode requires a `Fit` [CameraXPreviewScaleType]. A fill transform can enlarge the
     * external Surface beyond its declarative bounds, and Android cannot guarantee ancestor
     * clipping for that Surface on every supported device.
     */
    Performance,

    /** Uses CameraX's transform-friendly `TextureView` path and supports every scale type. */
    Compatible,
}

/** Selects how the camera stream is scaled and aligned inside the preview bounds. */
enum class CameraXPreviewScaleType {
    /**
     * Fills the bounds, preserves aspect ratio, crops overflow, and centers the result.
     * Requires [CameraXPreviewImplementationMode.Compatible].
     */
    FillCenter,

    /**
     * Fills the bounds, preserves aspect ratio, crops overflow, and aligns to the start.
     * Requires [CameraXPreviewImplementationMode.Compatible].
     */
    FillStart,

    /**
     * Fills the bounds, preserves aspect ratio, crops overflow, and aligns to the end.
     * Requires [CameraXPreviewImplementationMode.Compatible].
     */
    FillEnd,

    /** Fits the whole stream, preserves aspect ratio, and centers uncovered space. */
    FitCenter,

    /** Fits the whole stream, preserves aspect ratio, and aligns uncovered space to the start. */
    FitStart,

    /** Fits the whole stream, preserves aspect ratio, and aligns uncovered space to the end. */
    FitEnd,
}

/** Selects the orientation used to configure the integration-owned CameraX preview use case. */
enum class CameraXPreviewRotation {
    /** Follows the current Android display rotation and updates after display-layout changes. */
    Display,

    /** Uses [Surface.ROTATION_0]. */
    Rotation0,

    /** Uses [Surface.ROTATION_90]. */
    Rotation90,

    /** Uses [Surface.ROTATION_180]. */
    Rotation180,

    /** Uses [Surface.ROTATION_270]. */
    Rotation270,
}

/** Reports whether a committed preview owns a live CameraX binding. */
enum class CameraXPreviewBindingState {
    /** No preview is bound because the nearest lifecycle is below `STARTED`. */
    Inactive,

    /** The lifecycle is started but the caller has not supplied a camera provider. */
    WaitingForProvider,

    /** The integration-owned preview is bound to the caller-owned provider. */
    Bound,

    /** The most recent binding attempt failed and no preview use case remains bound. */
    Failed,
}

/** Reports whether CameraX is currently delivering frames to the native preview Surface. */
enum class CameraXPreviewStreamState {
    /** The native Surface is not currently receiving camera frames. */
    Idle,

    /** CameraX is delivering frames to the native Surface. */
    Streaming,
}

/** Classifies a failed CameraX preview binding without hiding the original exception. */
enum class CameraXPreviewFailureReason {
    /** Android denied camera access because the application does not hold runtime permission. */
    PermissionDenied,

    /** The requested lens or use-case combination cannot resolve to an available camera. */
    CameraUnavailable,

    /** Existing provider bindings conflict with this preview's lifecycle or selector. */
    ConflictingUseCases,

    /** The provider is in a mode, such as concurrent camera, that rejects individual unbinding. */
    UnsupportedProviderState,

    /** CameraX failed for a reason not represented by the stable classifications above. */
    Unknown,
}

/**
 * Describes one failed CameraX preview binding attempt.
 *
 * The integration owns this immutable report, while [exception] remains the original CameraX or
 * Android failure for diagnostics. Retrying is controlled by the caller: provide permission,
 * resolve provider conflicts, or commit a changed provider or lens selection.
 *
 * @property reason stable failure classification suitable for application UI and diagnostics
 * @property exception original failure thrown while binding or cleaning the owned preview use case
 */
class CameraXPreviewFailure internal constructor(
    val reason: CameraXPreviewFailureReason,
    val exception: Throwable,
)

/**
 * Defines replay-safe presentation state for one native CameraX preview.
 *
 * Every value is reapplied during renderer rollback. [targetRotation] updates the active Preview
 * use case without replacing the native View, while [scaleType] and [contentDescription] update
 * `PreviewView` directly on the Android main thread.
 *
 * @property scaleType scaling and alignment applied within the native preview bounds
 * @property targetRotation display orientation used by CameraX output transformation
 * @property contentDescription optional accessibility description for the non-interactive preview
 */
data class CameraXPreviewConfiguration(
    val scaleType: CameraXPreviewScaleType = CameraXPreviewScaleType.FillCenter,
    val targetRotation: CameraXPreviewRotation = CameraXPreviewRotation.Display,
    val contentDescription: CharSequence? = null,
) {
    /** Provides the stable default configuration used by [CameraXPreviewView]. */
    companion object {
        /** Fills the bounds, follows display rotation, and supplies no accessibility label. */
        @JvmField
        val Default: CameraXPreviewConfiguration = CameraXPreviewConfiguration()
    }
}

/**
 * Hosts one lifecycle-bound CameraX preview in a native `PreviewView`.
 *
 * This Q3 integration never requests camera permission, obtains or shuts down a process provider,
 * or calls `unbindAll()`. The caller owns [cameraProvider], CameraX process configuration,
 * permission policy, and every unrelated use case. The integration verifies that the application
 * already holds `android.permission.CAMERA` before invoking the provider; a missing grant reports
 * [CameraXPreviewFailureReason.PermissionDenied] without opening a camera or launching permission
 * UI. After a successful ViewCompose commit and while the nearest [LifecycleOwner] is at least
 * `STARTED`, the integration creates one [Preview], binds it to the requested [lensFacing], and
 * unbinds that exact instance before stop, provider or lens replacement, owner replacement, or
 * final View release. A `null` provider preserves the native placeholder and reports
 * [CameraXPreviewBindingState.WaitingForProvider] without opening a camera.
 *
 * [implementationMode] is construction identity because CameraX requires it before installing the
 * Surface provider; changing it atomically replaces the native View. [configuration] is complete
 * replay-safe state. Performance mode prefers `SurfaceView` for lower compositing cost, but is
 * accepted only with a `Fit` scale type because Android cannot guarantee clipping an enlarged
 * external Surface to declarative bounds on every supported device. The default compatible mode
 * uses an integration-owned clipping host so fill transforms remain inside declarative bounds and
 * supports transforms, animation, and wider device behavior.
 *
 * Callbacks run on the Android main thread and always use the latest committed functions.
 * [onBindingStateChanged] reports state transitions, [onCameraBound] runs once for each successful
 * owned binding, [onStreamStateChanged] follows CameraX Surface delivery, and [onFailure] follows a
 * `Failed` state report. A `Camera` delivered to [onCameraBound] is caller-commandable but must not
 * be used after a later non-bound state. Binding failures leave no integration-owned use case
 * attached and are reported instead of being treated as renderer success.
 *
 * [modifier] configures the component's layout, input, semantics, and native View properties. The
 * component has no children and requires the Android host or `ProvideLifecycleOwner` to supply a
 * usable owner. Static Preview callers should pass `null` and render an explicit surrounding
 * placeholder; Layoutlib does not emulate camera frames.
 *
 * @sample com.viewcompose.camerax.samples.cameraXPreviewViewSample
 * @receiver ViewCompose tree receiving the native preview node
 * @param cameraProvider caller-owned lifecycle provider, or `null` while unavailable
 * @param modifier declarative layout, input, semantics, and native View configuration
 * @param lensFacing physical lens direction to bind
 * @param implementationMode constructor-owned native Surface strategy
 * @param configuration complete replay-safe preview presentation and rotation state
 * @param onBindingStateChanged optional latest callback for binding lifecycle transitions
 * @param onCameraBound optional latest callback receiving each successfully bound CameraX camera
 * @param onStreamStateChanged optional latest callback for native Surface frame delivery
 * @param onFailure optional latest callback for a failed binding attempt after owned cleanup
 * @param key optional stable logical identity used for keyed ViewCompose reconciliation
 * @throws IllegalArgumentException when performance mode is combined with a fill scale type
 * @throws IllegalStateException when no usable lifecycle owner exists, or when a callback throws
 */
fun UiTreeBuilder.CameraXPreviewView(
    cameraProvider: ProcessCameraProvider?,
    modifier: Modifier = Modifier,
    lensFacing: CameraXLensFacing = CameraXLensFacing.Back,
    implementationMode: CameraXPreviewImplementationMode =
        CameraXPreviewImplementationMode.Compatible,
    configuration: CameraXPreviewConfiguration = CameraXPreviewConfiguration.Default,
    onBindingStateChanged: ((CameraXPreviewBindingState) -> Unit)? = null,
    onCameraBound: ((Camera) -> Unit)? = null,
    onStreamStateChanged: ((CameraXPreviewStreamState) -> Unit)? = null,
    onFailure: ((CameraXPreviewFailure) -> Unit)? = null,
    key: Any? = null,
) {
    require(
        implementationMode != CameraXPreviewImplementationMode.Performance ||
            configuration.scaleType.isFit,
    ) {
        "CameraXPreviewImplementationMode.Performance requires a Fit scale type because an " +
            "enlarged SurfaceView cannot be clipped reliably to declarative bounds."
    }
    val lifecycleOwner = checkNotNull(LocalLifecycleOwner.current) {
        "CameraXPreviewView requires a LifecycleOwner from the Android host or " +
            "ProvideLifecycleOwner."
    }
    AndroidView(
        adapter = CameraXPreviewViewAdapter(implementationMode),
        state = CameraXPreviewViewState(
            lifecycleOwner = lifecycleOwner,
            provider = cameraProvider?.let(::ProcessCameraProviderBackend),
            lensFacing = lensFacing,
            configuration = configuration,
            callbacks = CameraXPreviewCallbacks(
                onBindingStateChanged = onBindingStateChanged,
                onCameraBound = onCameraBound,
                onStreamStateChanged = onStreamStateChanged,
                onFailure = onFailure,
            ),
        ),
        key = key,
        constructionKey = implementationMode,
        modifier = modifier,
    )
}

internal data class CameraXPreviewViewState(
    val lifecycleOwner: LifecycleOwner,
    val provider: CameraXPreviewProviderBackend?,
    val lensFacing: CameraXLensFacing,
    val configuration: CameraXPreviewConfiguration,
    val callbacks: CameraXPreviewCallbacks,
)

internal data class CameraXPreviewCallbacks(
    val onBindingStateChanged: ((CameraXPreviewBindingState) -> Unit)?,
    val onCameraBound: ((Camera) -> Unit)?,
    val onStreamStateChanged: ((CameraXPreviewStreamState) -> Unit)?,
    val onFailure: ((CameraXPreviewFailure) -> Unit)?,
)

internal class CameraXPreviewViewAdapter(
    private val implementationMode: CameraXPreviewImplementationMode,
) : LifecycleAndroidViewAdapter<CameraXPreviewHostView, CameraXPreviewViewState>() {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Never

    override fun lifecycleOwner(state: CameraXPreviewViewState): LifecycleOwner = state.lifecycleOwner

    override fun create(scope: AndroidViewCreateScope): CameraXPreviewHostView =
        CameraXPreviewHostView(scope.context).apply {
            previewView.implementationMode =
                this@CameraXPreviewViewAdapter.implementationMode.toNativeImplementationMode()
        }

    override fun update(
        scope: AndroidViewUpdateScope<CameraXPreviewHostView>,
        state: CameraXPreviewViewState,
    ) {
        scope.view.previewView.scaleType = state.configuration.scaleType.toNativeScaleType()
        scope.view.previewView.contentDescription = state.configuration.contentDescription
    }

    override fun onViewCommit(
        scope: AndroidViewCommitScope<CameraXPreviewHostView>,
        state: CameraXPreviewViewState,
    ) {
        val previewView = scope.view.previewView
        val binding = CameraXPreviewBindingStore.bindingFor(previewView)
        try {
            binding.commit(previewView, state)
        } catch (error: Throwable) {
            val cleanupFailure = runCatching {
                CameraXPreviewBindingStore.remove(previewView)?.clear(previewView)
            }.exceptionOrNull()
            cleanupFailure?.let(error::addSuppressed)
            throw error
        }
    }

    override fun onLifecycleEvent(
        scope: AndroidViewLifecycleEventScope<CameraXPreviewHostView>,
        state: CameraXPreviewViewState,
        event: Lifecycle.Event,
    ) {
        val previewView = scope.view.previewView
        val binding = CameraXPreviewBindingStore.bindingFor(previewView)
        when (event) {
            Lifecycle.Event.ON_CREATE -> Unit
            Lifecycle.Event.ON_START -> binding.start(previewView, state)
            Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE,
            -> Unit

            Lifecycle.Event.ON_STOP -> binding.stop(previewView)
            Lifecycle.Event.ON_DESTROY -> binding.clear(previewView)
            Lifecycle.Event.ON_ANY -> Unit
        }
    }

    override fun onViewRelease(view: CameraXPreviewHostView) {
        CameraXPreviewBindingStore.remove(view.previewView)?.clear(view.previewView)
    }
}

internal class CameraXPreviewHostView(context: Context) : FrameLayout(context) {
    val previewView: PreviewView = PreviewView(context)

    init {
        // PreviewView deliberately transforms its render child beyond its own bounds for Fill
        // scaling. The declarative AndroidView host may allow visual overflow for shadows, so this
        // integration owns a dedicated clipping boundary around the transform-friendly TextureView.
        clipChildren = true
        clipToPadding = true
        addView(
            previewView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
}

internal interface CameraXPreviewProviderBackend {
    val identity: Any

    fun bind(
        lifecycleOwner: LifecycleOwner,
        lensFacing: CameraXLensFacing,
        preview: Preview,
    ): Camera?

    fun unbind(preview: Preview)
}

private class ProcessCameraProviderBackend(
    private val provider: ProcessCameraProvider,
) : CameraXPreviewProviderBackend {
    override val identity: Any
        get() = provider

    override fun bind(
        lifecycleOwner: LifecycleOwner,
        lensFacing: CameraXLensFacing,
        preview: Preview,
    ): Camera = provider.bindToLifecycle(
        lifecycleOwner,
        when (lensFacing) {
            CameraXLensFacing.Back -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraXLensFacing.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
        },
        preview,
    )

    override fun unbind(preview: Preview) {
        provider.unbind(preview)
    }
}

private object CameraXPreviewBindingStore {
    fun bindingFor(view: PreviewView): CameraXPreviewBinding {
        val existing = view.getTag(R.id.viewcompose_camerax_preview_binding)
        check(existing == null || existing is CameraXPreviewBinding) {
            "CameraX preview binding tag is owned by an incompatible value."
        }
        return (existing as? CameraXPreviewBinding) ?: CameraXPreviewBinding().also { binding ->
            view.setTag(R.id.viewcompose_camerax_preview_binding, binding)
        }
    }

    fun remove(view: PreviewView): CameraXPreviewBinding? {
        val existing = view.getTag(R.id.viewcompose_camerax_preview_binding)
        check(existing == null || existing is CameraXPreviewBinding) {
            "CameraX preview binding tag is owned by an incompatible value."
        }
        view.setTag(R.id.viewcompose_camerax_preview_binding, null)
        return existing as? CameraXPreviewBinding
    }
}

internal class CameraXPreviewBinding {
    private var started = false
    private var committedState: CameraXPreviewViewState? = null
    private var activeProvider: CameraXPreviewProviderBackend? = null
    private var activeOwner: LifecycleOwner? = null
    private var activeLensFacing: CameraXLensFacing? = null
    private var activePreview: Preview? = null
    private var streamObserver: Observer<PreviewView.StreamState>? = null
    private var layoutListenerInstalled = false
    private var lastBindingState = CameraXPreviewBindingState.Inactive
    private var lastBindingCallback: ((CameraXPreviewBindingState) -> Unit)? = null

    private val displayRotationListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        val state = committedState ?: return@OnLayoutChangeListener
        if (state.configuration.targetRotation == CameraXPreviewRotation.Display) {
            activePreview?.targetRotation = state.configuration.targetRotation.resolve(view)
        }
    }

    fun commit(
        view: PreviewView,
        state: CameraXPreviewViewState,
    ) {
        committedState = state
        ensureDisplayRotationListener(view)
        if (started) {
            sync(view)
        } else {
            reportBindingState(CameraXPreviewBindingState.Inactive)
        }
    }

    fun start(
        view: PreviewView,
        state: CameraXPreviewViewState,
    ) {
        committedState = state
        ensureDisplayRotationListener(view)
        started = true
        sync(view)
    }

    fun stop(view: PreviewView) {
        started = false
        releaseActive(view)
        reportBindingState(CameraXPreviewBindingState.Inactive)
    }

    fun clear(view: PreviewView) {
        started = false
        releaseActive(view)
        if (layoutListenerInstalled) {
            view.removeOnLayoutChangeListener(displayRotationListener)
            layoutListenerInstalled = false
        }
        committedState = null
        lastBindingState = CameraXPreviewBindingState.Inactive
        lastBindingCallback = null
    }

    private fun sync(view: PreviewView) {
        val state = checkNotNull(committedState)
        val provider = state.provider
        if (provider == null) {
            releaseActive(view)
            reportBindingState(CameraXPreviewBindingState.WaitingForProvider)
            return
        }

        val preview = activePreview
        if (
            preview != null &&
            activeProvider?.identity === provider.identity &&
            activeOwner === state.lifecycleOwner &&
            activeLensFacing == state.lensFacing
        ) {
            preview.targetRotation = state.configuration.targetRotation.resolve(view)
            ensureStreamObserver(view, state.lifecycleOwner)
            reportBindingState(CameraXPreviewBindingState.Bound)
            return
        }

        releaseActive(view)
        bindNewPreview(view, state, provider)
    }

    private fun bindNewPreview(
        view: PreviewView,
        state: CameraXPreviewViewState,
        provider: CameraXPreviewProviderBackend,
    ) {
        if (view.context.checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            reportBindingState(CameraXPreviewBindingState.Failed)
            state.callbacks.onFailure?.invoke(
                CameraXPreviewFailure(
                    reason = CameraXPreviewFailureReason.PermissionDenied,
                    exception = SecurityException(
                        "CameraXPreviewView requires android.permission.CAMERA before binding.",
                    ),
                ),
            )
            return
        }

        val preview = Preview.Builder()
            .setTargetRotation(state.configuration.targetRotation.resolve(view))
            .build()
        preview.setSurfaceProvider(view.surfaceProvider)

        val camera = try {
            provider.bind(state.lifecycleOwner, state.lensFacing, preview)
        } catch (error: Throwable) {
            val cleanupFailure = runCatching { provider.unbind(preview) }.exceptionOrNull()
            cleanupFailure?.let(error::addSuppressed)
            preview.setSurfaceProvider(null)
            reportBindingState(CameraXPreviewBindingState.Failed)
            state.callbacks.onFailure?.invoke(error.toPreviewFailure())
            return
        }

        activeProvider = provider
        activeOwner = state.lifecycleOwner
        activeLensFacing = state.lensFacing
        activePreview = preview
        try {
            ensureStreamObserver(view, state.lifecycleOwner)
            reportBindingState(CameraXPreviewBindingState.Bound)
            camera?.let { state.callbacks.onCameraBound?.invoke(it) }
        } catch (error: Throwable) {
            val cleanupFailure = runCatching { releaseActive(view) }.exceptionOrNull()
            cleanupFailure?.let(error::addSuppressed)
            throw error
        }
    }

    private fun ensureStreamObserver(
        view: PreviewView,
        owner: LifecycleOwner,
    ) {
        if (streamObserver != null) return
        val observer = Observer<PreviewView.StreamState> { nativeState ->
            val callback = committedState?.callbacks?.onStreamStateChanged ?: return@Observer
            callback(
                when (nativeState) {
                    PreviewView.StreamState.IDLE -> CameraXPreviewStreamState.Idle
                    PreviewView.StreamState.STREAMING -> CameraXPreviewStreamState.Streaming
                },
            )
        }
        streamObserver = observer
        view.previewStreamState.observe(owner, observer)
    }

    private fun releaseActive(view: PreviewView) {
        streamObserver?.let(view.previewStreamState::removeObserver)
        streamObserver = null
        val provider = activeProvider
        val preview = activePreview
        activeProvider = null
        activeOwner = null
        activeLensFacing = null
        activePreview = null
        if (preview != null) {
            var failure: Throwable? = null
            if (provider != null) {
                failure = runCatching { provider.unbind(preview) }.exceptionOrNull()
            }
            runCatching { preview.setSurfaceProvider(null) }
                .exceptionOrNull()
                ?.let { surfaceFailure ->
                    if (failure == null) failure = surfaceFailure else failure.addSuppressed(surfaceFailure)
                }
            failure?.let { throw it }
        }
    }

    private fun reportBindingState(state: CameraXPreviewBindingState) {
        val callback = committedState?.callbacks?.onBindingStateChanged
        if (state == lastBindingState && callback === lastBindingCallback) return
        lastBindingState = state
        lastBindingCallback = callback
        callback?.invoke(state)
    }

    private fun ensureDisplayRotationListener(view: PreviewView) {
        if (layoutListenerInstalled) return
        view.addOnLayoutChangeListener(displayRotationListener)
        layoutListenerInstalled = true
    }
}

private fun CameraXPreviewImplementationMode.toNativeImplementationMode():
    PreviewView.ImplementationMode = when (this) {
    CameraXPreviewImplementationMode.Performance -> PreviewView.ImplementationMode.PERFORMANCE
    CameraXPreviewImplementationMode.Compatible -> PreviewView.ImplementationMode.COMPATIBLE
}

private fun CameraXPreviewScaleType.toNativeScaleType(): PreviewView.ScaleType = when (this) {
    CameraXPreviewScaleType.FillCenter -> PreviewView.ScaleType.FILL_CENTER
    CameraXPreviewScaleType.FillStart -> PreviewView.ScaleType.FILL_START
    CameraXPreviewScaleType.FillEnd -> PreviewView.ScaleType.FILL_END
    CameraXPreviewScaleType.FitCenter -> PreviewView.ScaleType.FIT_CENTER
    CameraXPreviewScaleType.FitStart -> PreviewView.ScaleType.FIT_START
    CameraXPreviewScaleType.FitEnd -> PreviewView.ScaleType.FIT_END
}

private val CameraXPreviewScaleType.isFit: Boolean
    get() = when (this) {
        CameraXPreviewScaleType.FitCenter,
        CameraXPreviewScaleType.FitStart,
        CameraXPreviewScaleType.FitEnd,
        -> true

        CameraXPreviewScaleType.FillCenter,
        CameraXPreviewScaleType.FillStart,
        CameraXPreviewScaleType.FillEnd,
        -> false
    }

private fun CameraXPreviewRotation.resolve(view: View): Int = when (this) {
    CameraXPreviewRotation.Display -> view.display?.rotation ?: Surface.ROTATION_0
    CameraXPreviewRotation.Rotation0 -> Surface.ROTATION_0
    CameraXPreviewRotation.Rotation90 -> Surface.ROTATION_90
    CameraXPreviewRotation.Rotation180 -> Surface.ROTATION_180
    CameraXPreviewRotation.Rotation270 -> Surface.ROTATION_270
}

private fun Throwable.toPreviewFailure(): CameraXPreviewFailure = CameraXPreviewFailure(
    reason = when (this) {
        is SecurityException -> CameraXPreviewFailureReason.PermissionDenied
        is IllegalArgumentException -> CameraXPreviewFailureReason.CameraUnavailable
        is UnsupportedOperationException -> CameraXPreviewFailureReason.UnsupportedProviderState
        is IllegalStateException -> CameraXPreviewFailureReason.ConflictingUseCases
        else -> CameraXPreviewFailureReason.Unknown
    },
    exception = this,
)
