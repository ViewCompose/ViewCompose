package com.viewcompose.preview.device

import com.viewcompose.animation.tooling.AnimationTimelineTooling
import com.viewcompose.ui.foundation.RenderSessionInspectionTooling
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDeviceToolingInitializerTest {
    @Test
    fun `initializer installs both neutral ports without discovery`() {
        var renderSessionTooling: RenderSessionInspectionTooling? = null
        var animationTooling: AnimationTimelineTooling? = null

        initializeAndroidDeviceTooling(
            debuggable = true,
            installRenderSessionTooling = { tooling -> renderSessionTooling = tooling },
            installAnimationTooling = { tooling -> animationTooling = tooling },
        )

        assertTrue(renderSessionTooling is AndroidDeviceDslInspectionTooling)
        assertTrue(animationTooling is AndroidAnimationTimelineTooling)
    }

    @Test
    fun `non-debuggable process installs no tooling port`() {
        var installationCount = 0

        initializeAndroidDeviceTooling(
            debuggable = false,
            installRenderSessionTooling = { installationCount += 1 },
            installAnimationTooling = { installationCount += 1 },
        )

        assertTrue(installationCount == 0)
    }
}
