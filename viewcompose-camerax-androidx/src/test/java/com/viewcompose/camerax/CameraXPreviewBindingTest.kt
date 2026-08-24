package com.viewcompose.camerax

import android.Manifest
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class CameraXPreviewBindingTest {
    @Before
    fun grantCameraPermission() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.CAMERA,
        )
    }

    @Test
    fun `lens replacement and stop unbind only the exact owned previews`() {
        val owner = RecordingLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val view = PreviewView(RuntimeEnvironment.getApplication())
        val backend = RecordingBackend()
        val states = mutableListOf<CameraXPreviewBindingState>()
        val binding = CameraXPreviewBinding()

        binding.commit(view, state(owner, backend, CameraXLensFacing.Back, states))
        binding.start(view, state(owner, backend, CameraXLensFacing.Back, states))
        val first = backend.bound.single().preview
        assertEquals(CameraXPreviewRotation.Rotation90.nativeRotation, first.targetRotation)
        assertEquals(CameraXPreviewBindingState.Bound, states.last())

        binding.commit(view, state(owner, backend, CameraXLensFacing.Front, states))
        val second = backend.bound.last().preview
        assertNotSame(first, second)
        assertEquals(listOf(first), backend.unbound)

        binding.stop(view)
        assertEquals(listOf(first, second), backend.unbound)
        assertEquals(CameraXPreviewBindingState.Inactive, states.last())
        assertTrue(backend.bound.all { it.owner === owner })
        assertEquals(
            listOf(CameraXLensFacing.Back, CameraXLensFacing.Front),
            backend.bound.map(BoundPreview::lensFacing),
        )
    }

    @Test
    fun `same provider and lens update target rotation without rebinding`() {
        val owner = RecordingLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val view = PreviewView(RuntimeEnvironment.getApplication())
        val backend = RecordingBackend()
        val binding = CameraXPreviewBinding()

        binding.start(view, state(owner, backend, CameraXLensFacing.Back))
        val preview = backend.bound.single().preview
        val updated = state(owner, backend, CameraXLensFacing.Back).copy(
            configuration = CameraXPreviewConfiguration(
                targetRotation = CameraXPreviewRotation.Rotation270,
            ),
        )
        binding.commit(view, updated)

        assertEquals(1, backend.bound.size)
        assertTrue(backend.unbound.isEmpty())
        assertSame(preview, backend.bound.single().preview)
        assertEquals(CameraXPreviewRotation.Rotation270.nativeRotation, preview.targetRotation)
        binding.clear(view)
    }

    @Test
    fun `permission failure reports classification after bounded cleanup`() {
        val owner = RecordingLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val view = PreviewView(RuntimeEnvironment.getApplication())
        val backend = RecordingBackend(bindFailure = SecurityException("camera denied"))
        val states = mutableListOf<CameraXPreviewBindingState>()
        val failures = mutableListOf<CameraXPreviewFailure>()
        val binding = CameraXPreviewBinding()
        val state = state(owner, backend, CameraXLensFacing.Back, states).copy(
            callbacks = CameraXPreviewCallbacks(
                onBindingStateChanged = states::add,
                onCameraBound = null,
                onStreamStateChanged = null,
                onFailure = failures::add,
            ),
        )

        binding.start(view, state)

        assertEquals(CameraXPreviewBindingState.Failed, states.last())
        assertEquals(CameraXPreviewFailureReason.PermissionDenied, failures.single().reason)
        assertSame(backend.attempted.single(), backend.unbound.single())
        binding.clear(view)
    }

    @Test
    fun `missing runtime permission fails before invoking provider`() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(
            Manifest.permission.CAMERA,
        )
        val owner = RecordingLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val view = PreviewView(RuntimeEnvironment.getApplication())
        val backend = RecordingBackend()
        val states = mutableListOf<CameraXPreviewBindingState>()
        val failures = mutableListOf<CameraXPreviewFailure>()
        val binding = CameraXPreviewBinding()
        val state = state(owner, backend, CameraXLensFacing.Back, states).copy(
            callbacks = CameraXPreviewCallbacks(
                onBindingStateChanged = states::add,
                onCameraBound = null,
                onStreamStateChanged = null,
                onFailure = failures::add,
            ),
        )

        binding.start(view, state)

        assertEquals(CameraXPreviewBindingState.Failed, states.last())
        assertEquals(CameraXPreviewFailureReason.PermissionDenied, failures.single().reason)
        assertTrue(failures.single().exception is SecurityException)
        assertTrue(backend.attempted.isEmpty())
        assertTrue(backend.bound.isEmpty())
        assertTrue(backend.unbound.isEmpty())
        binding.clear(view)
    }

    @Test
    fun `callback failure releases the successful owned binding before propagating`() {
        val owner = RecordingLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val view = PreviewView(RuntimeEnvironment.getApplication())
        val backend = RecordingBackend()
        val binding = CameraXPreviewBinding()
        val state = state(owner, backend, CameraXLensFacing.Back).copy(
            callbacks = CameraXPreviewCallbacks(
                onBindingStateChanged = { state ->
                    if (state == CameraXPreviewBindingState.Bound) error("callback failed")
                },
                onCameraBound = null,
                onStreamStateChanged = null,
                onFailure = null,
            ),
        )

        assertThrows(IllegalStateException::class.java) {
            binding.start(view, state)
        }

        assertSame(backend.bound.single().preview, backend.unbound.single())
    }

    private fun state(
        owner: LifecycleOwner,
        backend: CameraXPreviewProviderBackend,
        lensFacing: CameraXLensFacing,
        states: MutableList<CameraXPreviewBindingState> = mutableListOf(),
    ): CameraXPreviewViewState = CameraXPreviewViewState(
        lifecycleOwner = owner,
        provider = backend,
        lensFacing = lensFacing,
        configuration = CameraXPreviewConfiguration(
            targetRotation = CameraXPreviewRotation.Rotation90,
        ),
        callbacks = CameraXPreviewCallbacks(
            onBindingStateChanged = states::add,
            onCameraBound = null,
            onStreamStateChanged = null,
            onFailure = null,
        ),
    )
}

private class RecordingBackend(
    private val bindFailure: Throwable? = null,
) : CameraXPreviewProviderBackend {
    override val identity: Any = Any()
    val attempted = mutableListOf<Preview>()
    val bound = mutableListOf<BoundPreview>()
    val unbound = mutableListOf<Preview>()

    override fun bind(
        lifecycleOwner: LifecycleOwner,
        lensFacing: CameraXLensFacing,
        preview: Preview,
    ): Camera? {
        attempted += preview
        bindFailure?.let { throw it }
        bound += BoundPreview(lifecycleOwner, lensFacing, preview)
        return null
    }

    override fun unbind(preview: Preview) {
        unbound += preview
    }
}

private data class BoundPreview(
    val owner: LifecycleOwner,
    val lensFacing: CameraXLensFacing,
    val preview: Preview,
)

private class RecordingLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}

private val CameraXPreviewRotation.nativeRotation: Int
    get() = when (this) {
        CameraXPreviewRotation.Display,
        CameraXPreviewRotation.Rotation0,
        -> android.view.Surface.ROTATION_0

        CameraXPreviewRotation.Rotation90 -> android.view.Surface.ROTATION_90
        CameraXPreviewRotation.Rotation180 -> android.view.Surface.ROTATION_180
        CameraXPreviewRotation.Rotation270 -> android.view.Surface.ROTATION_270
    }
