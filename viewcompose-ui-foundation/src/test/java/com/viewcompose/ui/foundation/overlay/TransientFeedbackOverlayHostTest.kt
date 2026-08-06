package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core overlay 中的 Transient Feedback Overlay Host 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Transient Feedback Overlay Host behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class TransientFeedbackOverlayHostTest {
    @Test
    fun `shows first feedback request and does not re-show equal declaration`() {
        val fixture = Fixture()
        val request = snackbarRequest(key = "snackbar", message = "Saved")

        fixture.host.commit(fixture.sessionId, listOf(request))
        fixture.host.commit(fixture.sessionId, listOf(request))

        assertEquals(
            listOf("show:session-1:snackbar:Saved"),
            fixture.snackbarPresenter.events,
        )
        assertEquals(
            TransientFeedbackQueueSnapshot(
                active = OverlayEntryId(fixture.sessionId, "snackbar"),
                pending = emptyList(),
                consumed = emptySet(),
            ),
            fixture.host.snapshot(),
        )
    }

    @Test
    fun `queues snackbar and toast in one deterministic lane`() {
        val fixture = Fixture()

        fixture.host.commit(
            fixture.sessionId,
            listOf(
                snackbarRequest(key = "save", message = "Saved"),
                toastRequest(key = "copy", message = "Copied"),
            ),
        )

        assertEquals(listOf("show:session-1:save:Saved"), fixture.snackbarPresenter.events)
        assertEquals(emptyList<String>(), fixture.toastPresenter.events)

        fixture.snackbarPresenter.complete(
            entryId = OverlayEntryId(fixture.sessionId, "save"),
            reason = TransientFeedbackDismissReason.Timeout,
        )

        assertEquals(listOf("show:session-1:copy:Copied"), fixture.toastPresenter.events)
    }

    @Test
    fun `replace current policy dismisses active feedback before replacement`() {
        val fixture = Fixture()
        fixture.host.commit(
            fixture.sessionId,
            listOf(snackbarRequest(key = "save", message = "Saved")),
        )

        fixture.host.commit(
            fixture.sessionId,
            listOf(
                snackbarRequest(key = "save", message = "Saved"),
                toastRequest(
                    key = "error",
                    message = "Failed",
                    queuePolicy = TransientFeedbackQueuePolicy.ReplaceCurrent,
                ),
            ),
        )

        assertEquals(
            listOf(
                "show:session-1:save:Saved",
                "dismiss:session-1:save:Replaced",
            ),
            fixture.snackbarPresenter.events,
        )
        assertEquals(listOf("show:session-1:error:Failed"), fixture.toastPresenter.events)
    }

    @Test
    fun `changed request with same key replaces its active version`() {
        val fixture = Fixture()
        fixture.host.commit(
            fixture.sessionId,
            listOf(snackbarRequest(key = "status", message = "Saving")),
        )

        fixture.host.commit(
            fixture.sessionId,
            listOf(snackbarRequest(key = "status", message = "Saved")),
        )

        assertEquals(
            listOf(
                "show:session-1:status:Saving",
                "dismiss:session-1:status:Replaced",
                "show:session-1:status:Saved",
            ),
            fixture.snackbarPresenter.events,
        )
    }

    @Test
    fun `drop if busy reports reason once and stays consumed while declared`() {
        val fixture = Fixture()
        val dismissReasons = mutableListOf<TransientFeedbackDismissReason>()
        val active = snackbarRequest(key = "save", message = "Saved")
        val dropped = toastRequest(
            key = "copy",
            message = "Copied",
            queuePolicy = TransientFeedbackQueuePolicy.DropIfBusy,
            onDismiss = dismissReasons::add,
        )

        fixture.host.commit(fixture.sessionId, listOf(active, dropped))
        fixture.host.commit(fixture.sessionId, listOf(active, dropped))

        assertEquals(listOf(TransientFeedbackDismissReason.Dropped), dismissReasons)
        assertEquals(emptyList<String>(), fixture.toastPresenter.events)
        assertEquals(
            setOf(OverlayEntryId(fixture.sessionId, "copy")),
            fixture.host.snapshot().consumed,
        )
    }

    @Test
    fun `removed request is dismissed with structured reason`() {
        val fixture = Fixture()
        val dismissReasons = mutableListOf<TransientFeedbackDismissReason>()
        fixture.host.commit(
            fixture.sessionId,
            listOf(
                snackbarRequest(
                    key = "snackbar",
                    message = "Saved",
                    onDismiss = dismissReasons::add,
                ),
            ),
        )

        fixture.host.commit(fixture.sessionId, emptyList())

        assertEquals(
            listOf(
                "show:session-1:snackbar:Saved",
                "dismiss:session-1:snackbar:Removed",
            ),
            fixture.snackbarPresenter.events,
        )
        assertEquals(listOf(TransientFeedbackDismissReason.Removed), dismissReasons)
        assertEquals(
            TransientFeedbackQueueSnapshot(null, emptyList(), emptySet()),
            fixture.host.snapshot(),
        )
    }

    @Test
    fun `clear removes only matching session and advances other session`() {
        val fixture = Fixture()
        val secondSession = OverlaySessionId("session-2")
        fixture.host.commit(
            fixture.sessionId,
            listOf(toastRequest(key = "first", message = "Copied")),
        )
        fixture.host.commit(
            secondSession,
            listOf(toastRequest(key = "second", message = "Pinned")),
        )

        fixture.host.clear(fixture.sessionId)

        assertEquals(
            listOf(
                "show:session-1:first:Copied",
                "dismiss:session-1:first:SessionCleared",
                "show:session-2:second:Pinned",
            ),
            fixture.toastPresenter.events,
        )
    }

    private class Fixture {
        val sessionId = OverlaySessionId("session-1")
        val snackbarPresenter = RecordingSnackbarPresenter()
        val toastPresenter = RecordingToastPresenter()
        val host = TransientFeedbackOverlayHost(snackbarPresenter, toastPresenter)
    }

    private class RecordingSnackbarPresenter : SnackbarOverlayPresenter {
        val events = mutableListOf<String>()
        private val callbacks = mutableMapOf<OverlayEntryId, (TransientFeedbackDismissReason) -> Unit>()

        override fun show(
            entryId: OverlayEntryId,
            spec: SnackbarOverlaySpec,
            onDismissed: (TransientFeedbackDismissReason) -> Unit,
        ) {
            events += "show:${entryId.sessionId.value}:${entryId.requestKey}:${spec.message}"
            callbacks[entryId] = onDismissed
        }

        override fun dismiss(
            entryId: OverlayEntryId,
            reason: TransientFeedbackDismissReason,
        ) {
            events += "dismiss:${entryId.sessionId.value}:${entryId.requestKey}:$reason"
            complete(entryId, reason)
        }

        fun complete(
            entryId: OverlayEntryId,
            reason: TransientFeedbackDismissReason,
        ) {
            callbacks.remove(entryId)?.invoke(reason)
        }
    }

    private class RecordingToastPresenter : ToastOverlayPresenter {
        val events = mutableListOf<String>()
        private val callbacks = mutableMapOf<OverlayEntryId, (TransientFeedbackDismissReason) -> Unit>()

        override fun show(
            entryId: OverlayEntryId,
            spec: ToastOverlaySpec,
            onDismissed: (TransientFeedbackDismissReason) -> Unit,
        ) {
            events += "show:${entryId.sessionId.value}:${entryId.requestKey}:${spec.message}"
            callbacks[entryId] = onDismissed
        }

        override fun dismiss(
            entryId: OverlayEntryId,
            reason: TransientFeedbackDismissReason,
        ) {
            events += "dismiss:${entryId.sessionId.value}:${entryId.requestKey}:$reason"
            callbacks.remove(entryId)?.invoke(reason)
        }
    }

    private fun snackbarRequest(
        key: String,
        message: String,
        queuePolicy: TransientFeedbackQueuePolicy = TransientFeedbackQueuePolicy.Enqueue,
        onDismiss: ((TransientFeedbackDismissReason) -> Unit)? = null,
    ): OverlayRequest {
        return OverlayRequest(
            key = key,
            type = OverlayType.Snackbar,
            payload = SnackbarOverlaySpec(
                message = message,
                queuePolicy = queuePolicy,
                onDismiss = onDismiss,
            ),
        )
    }

    private fun toastRequest(
        key: String,
        message: String,
        queuePolicy: TransientFeedbackQueuePolicy = TransientFeedbackQueuePolicy.Enqueue,
        onDismiss: ((TransientFeedbackDismissReason) -> Unit)? = null,
    ): OverlayRequest {
        return OverlayRequest(
            key = key,
            type = OverlayType.Toast,
            payload = ToastOverlaySpec(
                message = message,
                queuePolicy = queuePolicy,
                onDismiss = onDismiss,
            ),
        )
    }
}
