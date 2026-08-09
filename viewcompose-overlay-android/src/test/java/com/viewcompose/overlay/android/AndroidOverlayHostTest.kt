package com.viewcompose.overlay.android

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupWindow
import com.viewcompose.host.android.renderInto
import com.viewcompose.overlay.android.presenter.AndroidPopupOverlayPresenter
import com.viewcompose.overlay.android.presenter.AndroidToastOverlayPresenter
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Dialog
import com.viewcompose.ui.foundation.OverlayEntryId
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayRequest
import com.viewcompose.ui.foundation.OverlaySessionId
import com.viewcompose.ui.foundation.OverlayType
import com.viewcompose.ui.foundation.Popup
import com.viewcompose.ui.foundation.PopupOverlayContent
import com.viewcompose.ui.foundation.PopupOverlaySpec
import com.viewcompose.ui.foundation.SnackbarOverlayPresenter
import com.viewcompose.ui.foundation.SnackbarOverlaySpec
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.ToastDuration
import com.viewcompose.ui.foundation.ToastOverlaySpec
import com.viewcompose.ui.foundation.TransientFeedbackDismissReason
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.overlayAnchor
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.unit.dp
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

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

    @Test
    fun `disposing the root session dismisses its Android dialog`() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val root = FrameLayout(activityController.get())
        activityController.get().setContentView(root)
        val session = renderInto(
            container = root,
            overlayHost = AndroidOverlayHost(root),
        ) {
            Dialog(visible = true, requestKey = "owned-dialog") {
                Text("Dialog content")
            }
        }
        val dialog = ShadowDialog.getLatestDialog()

        assertNotNull(dialog)
        assertTrue(dialog.isShowing)

        session.dispose()

        assertFalse(dialog.isShowing)
        activityController.destroy()
    }

    @Test
    fun `toast presenter completes its queue entry after the platform duration`() {
        val dismissals = mutableListOf<TransientFeedbackDismissReason>()
        val presenter = AndroidToastOverlayPresenter(RuntimeEnvironment.getApplication())

        presenter.show(
            entryId = OverlayEntryId(OverlaySessionId("toast"), "message"),
            spec = ToastOverlaySpec(message = "Queued", duration = ToastDuration.Short),
            onDismissed = dismissals::add,
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertEquals(listOf(TransientFeedbackDismissReason.Timeout), dismissals)
    }

    @Test
    fun `popup presenter releases its platform window on dismissal`() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val root = FrameLayout(activityController.get())
        val requests = mutableListOf<OverlayRequest>()
        activityController.get().setContentView(root)
        val session = renderInto(
            container = root,
            overlayHost = object : OverlayHost {
                override fun commit(
                    sessionId: OverlaySessionId,
                    desired: List<OverlayRequest>,
                ) {
                    requests.clear()
                    requests.addAll(desired)
                }

                override fun clear(sessionId: OverlaySessionId) {
                    requests.clear()
                }
            },
        ) {
            Box(modifier = Modifier.size(40.dp, 40.dp).overlayAnchor("menu-anchor")) { }
            Popup(visible = true, anchorId = "menu-anchor", requestKey = "menu") {
                Text("Popup content")
            }
        }
        measureAndLayout(root, width = 400, height = 400)
        val request = requests.single { it.type == OverlayType.Popup }
        val handle = AndroidPopupOverlayPresenter(root).show(
            entryId = OverlayEntryId(OverlaySessionId("popup"), "menu"),
            spec = request.payload as PopupOverlaySpec,
            content = request.contentToken as PopupOverlayContent,
        )
        val popupWindow = handle.javaClass.getDeclaredField("popupWindow").run {
            isAccessible = true
            get(handle) as PopupWindow
        }

        assertTrue(popupWindow.isShowing)

        handle.dismiss()

        assertFalse(popupWindow.isShowing)
        session.dispose()
        activityController.destroy()
    }

    private fun measureAndLayout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
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
