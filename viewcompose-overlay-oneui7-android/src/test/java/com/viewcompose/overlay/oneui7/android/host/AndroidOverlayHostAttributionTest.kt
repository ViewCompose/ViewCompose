package com.viewcompose.overlay.oneui7.android.host

import android.app.Activity
import android.widget.FrameLayout
import com.viewcompose.oneui7.OneUi7ThemeDefaults
import com.viewcompose.ui.foundation.UiDesignConformance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidOverlayHostAttributionTest {
    @Test
    fun hostReportsOneUiPresentersWithoutMaterialFallback() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)

        val attribution = AndroidOverlayHost(
            rootView = root,
            tokens = OneUi7ThemeDefaults.light(),
        ).integrationAttribution

        assertEquals(
            "viewcompose-oneui7/native-snackbar",
            attribution.single { it.capabilityId == "overlay.snackbar" }.presenterId,
        )
        assertEquals(
            UiDesignConformance.Equivalent,
            attribution.single { it.capabilityId == "overlay.snackbar" }.conformance,
        )
        assertEquals(
            "viewcompose-oneui7/bottom-sheet-dialog",
            attribution.single { it.capabilityId == "overlay.modal-bottom-sheet" }.presenterId,
        )
        assertFalse(attribution.any { it.presenterId.contains("material", ignoreCase = true) })
    }
}
