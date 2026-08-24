package com.viewcompose.camerax

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.host.android.renderInto
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.ui.foundation.UiTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class CameraXPreviewViewTest {
    @Test
    fun `null provider mounts explicit waiting state without a camera backend`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val states = mutableListOf<CameraXPreviewBindingState>()

        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                CameraXPreviewView(
                    cameraProvider = null,
                    onBindingStateChanged = states::add,
                )
            }
        }

        assertEquals(CameraXPreviewBindingState.WaitingForProvider, states.last())
        assertNotNull(root.requirePreviewDescendant())
        session.dispose()
    }

    @Test
    fun `presentation updates reuse view while implementation mode replaces it`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.CREATED) }
        var implementationMode = CameraXPreviewImplementationMode.Compatible
        var configuration = CameraXPreviewConfiguration(
            scaleType = CameraXPreviewScaleType.FitEnd,
            targetRotation = CameraXPreviewRotation.Rotation90,
            contentDescription = "Rear preview",
        )
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                CameraXPreviewView(
                    cameraProvider = null,
                    implementationMode = implementationMode,
                    configuration = configuration,
                    key = "camera",
                )
            }
        }
        val compatibleView = root.requirePreviewDescendant()
        val clippingHost = compatibleView.parent as CameraXPreviewHostView
        assertTrue(clippingHost.clipChildren)
        assertTrue(clippingHost.clipToPadding)
        assertEquals(PreviewView.ImplementationMode.COMPATIBLE, compatibleView.implementationMode)
        assertEquals(PreviewView.ScaleType.FIT_END, compatibleView.scaleType)
        assertEquals("Rear preview", compatibleView.contentDescription)

        configuration = configuration.copy(
            scaleType = CameraXPreviewScaleType.FillStart,
            contentDescription = "Updated preview",
        )
        session.render()
        assertSame(compatibleView, root.requirePreviewDescendant())
        assertEquals(PreviewView.ScaleType.FILL_START, compatibleView.scaleType)
        assertEquals("Updated preview", compatibleView.contentDescription)

        implementationMode = CameraXPreviewImplementationMode.Performance
        configuration = configuration.copy(scaleType = CameraXPreviewScaleType.FitEnd)
        session.render()
        val performanceView = root.requirePreviewDescendant()
        assertTrue(compatibleView !== performanceView)
        assertEquals(PreviewView.ImplementationMode.PERFORMANCE, performanceView.implementationMode)
        assertNull(compatibleView.getTag(R.id.viewcompose_camerax_preview_binding))

        session.dispose()
    }

    @Test
    fun `performance mode rejects fill scaling before native construction`() {
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.CREATED) }

        val failure = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            UiTreeBuilder().ProvideLifecycleOwner(owner) {
                CameraXPreviewView(
                    cameraProvider = null,
                    implementationMode = CameraXPreviewImplementationMode.Performance,
                    configuration = CameraXPreviewConfiguration(
                        scaleType = CameraXPreviewScaleType.FillCenter,
                    ),
                )
            }
        }

        assertTrue(failure.message.orEmpty().contains("requires a Fit scale type"))
    }

    @Test
    fun `integration artifact does not select a CameraX hardware backend`() {
        org.junit.Assert.assertThrows(ClassNotFoundException::class.java) {
            Class.forName("androidx.camera.camera2.Camera2Config")
        }
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}

private fun View.requirePreviewDescendant(): PreviewView {
    if (this is PreviewView) return this
    if (this is ViewGroup) {
        repeat(childCount) { index ->
            runCatching { getChildAt(index).requirePreviewDescendant() }.getOrNull()?.let { return it }
        }
    }
    error("Missing descendant ${PreviewView::class.java.name}")
}
