package com.viewcompose.overlay.android

import android.widget.FrameLayout
import com.viewcompose.ui.foundation.OverlayEntryId
import com.viewcompose.ui.foundation.OverlayRequest
import com.viewcompose.ui.foundation.OverlaySessionId
import com.viewcompose.ui.foundation.OverlayType
import com.viewcompose.ui.foundation.SnackbarOverlayPresenter
import com.viewcompose.ui.foundation.SnackbarOverlaySpec
import com.viewcompose.ui.foundation.TransientFeedbackDismissReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidOverlayHostTest {
    @Test
    fun `missing snackbar presenter reports unsupported without Material substitution`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val dismissals = mutableListOf<TransientFeedbackDismissReason>()
        val host = AndroidOverlayHost(root)

        host.commit(
            sessionId = OverlaySessionId("neutral"),
            requests = listOf(
                OverlayRequest(
                    key = "message",
                    type = OverlayType.Snackbar,
                    payload = SnackbarOverlaySpec(
                        message = "Neutral",
                        onDismiss = dismissals::add,
                    ),
                ),
            ),
        )

        assertEquals(listOf(TransientFeedbackDismissReason.Platform), dismissals)
    }

    @Test
    fun `explicit snackbar presenter is selected for the root`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val presenter = RecordingSnackbarPresenter()
        val host = AndroidOverlayHost(
            rootView = root,
            snackbarPresenter = presenter,
        )

        host.commit(
            sessionId = OverlaySessionId("material"),
            requests = listOf(
                OverlayRequest(
                    key = "message",
                    type = OverlayType.Snackbar,
                    payload = SnackbarOverlaySpec(message = "Selected"),
                ),
            ),
        )

        assertEquals("Selected", presenter.lastSpec?.message)
        assertEquals(OverlayEntryId(OverlaySessionId("material"), "message"), presenter.lastEntryId)
    }

    @Test
    fun `service provider creates the neutral transport`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())

        val host = AndroidOverlayHostFactoryProvider().create(root)

        assertNotNull(host)
        assertEquals(AndroidOverlayHost::class.java, host::class.java)
    }

    @Test
    fun `neutral attribution reports platform transport and unsupported design presenters`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val host = AndroidOverlayHost(root)

        assertEquals(
            "android.app.Dialog",
            host.integrationAttribution.single { it.capabilityId == "overlay.dialog" }.presenterId,
        )
        assertEquals(
            "unsupported",
            host.integrationAttribution.single { it.capabilityId == "overlay.snackbar" }.presenterId,
        )
        assertEquals(
            "platform-toast",
            host.integrationAttribution.single { it.capabilityId == "overlay.toast" }.fallback,
        )
    }

    private class RecordingSnackbarPresenter : SnackbarOverlayPresenter {
        var lastEntryId: OverlayEntryId? = null
        var lastSpec: SnackbarOverlaySpec? = null

        override fun show(
            entryId: OverlayEntryId,
            spec: SnackbarOverlaySpec,
            onDismissed: (TransientFeedbackDismissReason) -> Unit,
        ) {
            lastEntryId = entryId
            lastSpec = spec
        }

        override fun dismiss(
            entryId: OverlayEntryId,
            reason: TransientFeedbackDismissReason,
        ) = Unit
    }
}
