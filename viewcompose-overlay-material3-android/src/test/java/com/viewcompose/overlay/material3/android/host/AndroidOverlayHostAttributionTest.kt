package com.viewcompose.overlay.material3.android.host

import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidOverlayHostAttributionTest {
    @Test
    fun `Material adapter reports only Material owned presenter slots`() {
        val host = AndroidOverlayHost(FrameLayout(RuntimeEnvironment.getApplication()))

        assertEquals(
            "viewcompose-overlay-android/dialog",
            host.integrationAttribution.single { it.capabilityId == "overlay.dialog" }.transportId,
        )
        assertEquals(
            "viewcompose-material3/captured-dialog-content",
            host.integrationAttribution.single { it.capabilityId == "overlay.dialog" }.presenterId,
        )
        assertEquals(
            "material-components/snackbar",
            host.integrationAttribution.single { it.capabilityId == "overlay.snackbar" }.presenterId,
        )
        assertEquals(
            "material-components/bottom-sheet-dialog",
            host.integrationAttribution
                .single { it.capabilityId == "overlay.modal-bottom-sheet" }
                .presenterId,
        )
        assertEquals(
            "android.widget.Toast",
            host.integrationAttribution.single { it.capabilityId == "overlay.toast" }.presenterId,
        )
    }
}
