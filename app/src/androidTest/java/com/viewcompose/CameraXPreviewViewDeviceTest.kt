package com.viewcompose

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraXPreviewViewDeviceTest {
    @Test
    fun permissionDenied_reportsFailureWithoutRequestingPermission() {
        assumeFalse(cameraPermissionGranted())
        launchDemoScenarioActivity(
            activityClass = CameraXActivity::class.java,
            scenarioId = "camera.camerax-preview-view",
        ).use { scenario ->
            waitUntil("CameraX reports the denied permission") {
                scenario.readCameraStatus().contains(
                    targetContext().getString(R.string.demo_camerax_failure_permission),
                )
            }

            assertTrue(scenario.hasNativePreviewView())
            assertTrue(
                scenario.readCameraStatus().contains(
                    targetContext().getString(R.string.demo_camerax_binding_failed),
                ),
            )
            assertFalse(cameraPermissionGranted())
        }
    }

    @Test
    fun grantedPermission_streamsAcrossLensSurfaceLifecycleRotationAndRecreation() {
        assumeTrue(cameraPermissionGranted())
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            launchDemoScenarioActivity(
                activityClass = CameraXActivity::class.java,
                scenarioId = "camera.camerax-preview-view",
            ).use { scenario ->
                scenario.waitForStreaming()
                val initial = scenario.readNativePreviewView()
                scenario.assertNativeSurfaceContainedByTarget()
                assertEquals(
                    targetContext().getString(R.string.demo_camerax_content_description),
                    initial.contentDescription,
                )

                scenario.onActivity { activity ->
                    activity.clickScenarioViewById(
                        R.id.demo_camera_camerax_preview_view_primary_action,
                    )
                }
                waitUntil("front CameraX lens streams") {
                    val status = scenario.readCameraStatus()
                    status.contains(targetContext().getString(R.string.demo_camerax_lens_front)) &&
                        status.contains(
                            targetContext().getString(R.string.demo_camerax_stream_streaming),
                        )
                }
                assertSame(initial, scenario.readNativePreviewView())

                scenario.onActivity { activity ->
                    activity.clickScenarioViewById(
                        R.id.demo_camera_camerax_preview_view_secondary_action,
                    )
                }
                scenario.waitForStreaming()
                val compatible = scenario.readNativePreviewView()
                assertNotSame(initial, compatible)
                assertFalse(initial.isAttachedToWindow)
                assertNull(
                    initial.getTag(
                        com.viewcompose.camerax.R.id.viewcompose_camerax_preview_binding,
                    ),
                )
                scenario.assertNativeSurfaceContainedByTarget()

                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.waitForStreaming()
                assertSame(compatible, scenario.readNativePreviewView())

                device.setOrientationLeft()
                waitForUiIdle()
                scenario.waitForStreaming()
                device.setOrientationNatural()
                waitForUiIdle()
                scenario.waitForStreaming()

                val beforeRecreation = scenario.readNativePreviewView()
                scenario.recreate()
                scenario.waitForStreaming()
                assertNotSame(beforeRecreation, scenario.readNativePreviewView())
                assertNull(
                    beforeRecreation.getTag(
                        com.viewcompose.camerax.R.id.viewcompose_camerax_preview_binding,
                    ),
                )
            }
        } finally {
            device.setOrientationNatural()
            waitForUiIdle()
        }
    }
}

private fun cameraPermissionGranted(): Boolean =
    targetContext().checkSelfPermission(Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun targetContext() = InstrumentationRegistry.getInstrumentation().targetContext

private fun ActivityScenario<CameraXActivity>.hasNativePreviewView(): Boolean {
    var result = false
    onActivity { activity ->
        val target = activity.requireScenarioViewById<View>(
            R.id.demo_camera_camerax_preview_view_target,
        )
        result = findCameraDescendant(target, PreviewView::class.java) != null
    }
    return result
}

private fun ActivityScenario<CameraXActivity>.readNativePreviewView(): PreviewView {
    var result: PreviewView? = null
    onActivity { activity ->
        val target = activity.requireScenarioViewById<View>(
            R.id.demo_camera_camerax_preview_view_target,
        )
        result = findCameraDescendant(target, PreviewView::class.java)
    }
    return requireNotNull(result)
}

private fun ActivityScenario<CameraXActivity>.readCameraStatus(): String {
    var result = ""
    onActivity { activity ->
        result = activity.requireScenarioViewById<TextView>(
            R.id.demo_camera_camerax_preview_view_state,
        ).text.toString()
    }
    return result
}

private fun ActivityScenario<CameraXActivity>.assertNativeSurfaceContainedByTarget() {
    onActivity { activity ->
        val target = activity.requireScenarioViewById<View>(
            R.id.demo_camera_camerax_preview_view_target,
        )
        val preview = requireNotNull(findCameraDescendant(target, PreviewView::class.java))
        val targetRect = Rect()
        val previewRect = Rect()
        assertTrue(target.getGlobalVisibleRect(targetRect))
        assertTrue(preview.getGlobalVisibleRect(previewRect))
        assertEquals(targetRect, previewRect)

        val renderingSurfaces = findCameraDescendants(target) { child ->
            child is SurfaceView || child is TextureView
        }
        assertTrue("CameraX PreviewView has no rendering Surface", renderingSurfaces.isNotEmpty())
        renderingSurfaces.forEach { surface ->
            val surfaceRect = Rect()
            assertTrue(surface.getGlobalVisibleRect(surfaceRect))
            assertTrue(
                "${surface.javaClass.simpleName} $surfaceRect escapes target $targetRect",
                targetRect.contains(surfaceRect),
            )
        }
    }
}

private fun ActivityScenario<CameraXActivity>.waitForStreaming() {
    waitUntil("CameraX streams physical frames") {
        readCameraStatus().contains(
            targetContext().getString(R.string.demo_camerax_stream_streaming),
        )
    }
}

private fun <T : View> findCameraDescendant(
    root: View,
    type: Class<T>,
): T? {
    if (type.isInstance(root)) return type.cast(root)
    if (root is ViewGroup) {
        repeat(root.childCount) { index ->
            findCameraDescendant(root.getChildAt(index), type)?.let { return it }
        }
    }
    return null
}

private fun findCameraDescendants(
    root: View,
    predicate: (View) -> Boolean,
): List<View> = buildList {
    if (predicate(root)) add(root)
    if (root is ViewGroup) {
        repeat(root.childCount) { index ->
            addAll(findCameraDescendants(root.getChildAt(index), predicate))
        }
    }
}

private fun waitUntil(
    description: String,
    timeoutMillis: Long = 20_000L,
    condition: () -> Boolean,
) {
    val deadline = android.os.SystemClock.uptimeMillis() + timeoutMillis
    while (android.os.SystemClock.uptimeMillis() < deadline) {
        waitForUiIdle()
        if (condition()) return
        android.os.SystemClock.sleep(100L)
    }
    assertTrue("Timed out waiting for $description", condition())
}
