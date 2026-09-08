package com.viewcompose.ui.foundation

import org.junit.Assert.*
import org.junit.Test

class OverlayCleanupFailureTest {
    @Test fun `clear attempts all handles preserves the first failure and isolates sessions`() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val released = mutableListOf<String>()
        val host = host { key ->
            released += key
            when (key) { "a" -> throw first; "b" -> throw second }
        }
        val session = OverlaySessionId("one")
        host.commit(session, requests("a", "b", "c"))
        host.commit(OverlaySessionId("two"), requests("other"))
        assertSame(first, runCatching { host.clear(session) }.exceptionOrNull())
        assertEquals(listOf("a", "b", "c"), released)
        assertEquals(listOf(second), first.suppressed.toList())
        host.clear(session)
        assertEquals(3, released.size)
        host.clear(OverlaySessionId("two"))
        assertEquals(listOf("a", "b", "c", "other"), released)
    }

    @Test fun `omitting several surfaces attempts every obsolete dismissal`() {
        val released = mutableListOf<String>()
        val host = host { key -> released += key; if (key == "a") error("first") }
        val session = OverlaySessionId("one")
        host.commit(session, requests("a", "b"))
        assertNotNull(runCatching { host.commit(session, emptyList()) }.exceptionOrNull())
        assertEquals(listOf("a", "b"), released)
        host.clear(session)
        assertEquals(2, released.size)
    }

    @Test fun `the same throwable instance can fail more than one cleanup`() {
        val error = IllegalStateException("same")
        var calls = 0
        val host = host { calls++; throw error }
        val session = OverlaySessionId("one")
        host.commit(session, requests("a", "b"))
        assertSame(error, runCatching { host.clear(session) }.exceptionOrNull())
        assertEquals(2, calls)
        assertTrue(error.suppressed.isEmpty())
    }

    @Test fun `failed transient dismissal clears pending siblings and unblocks other sessions`() {
        val shown = mutableListOf<OverlayEntryId>()
        val failure = IllegalStateException("dismiss")
        val presenter = object : SnackbarOverlayPresenter {
            override fun show(entryId: OverlayEntryId, spec: SnackbarOverlaySpec,
                onDismissed: (TransientFeedbackDismissReason) -> Unit) { shown += entryId }
            override fun dismiss(entryId: OverlayEntryId, reason: TransientFeedbackDismissReason) {
                throw failure
            }
        }
        val toast = object : ToastOverlayPresenter {
            override fun show(entryId: OverlayEntryId, spec: ToastOverlaySpec,
                onDismissed: (TransientFeedbackDismissReason) -> Unit) = Unit
            override fun dismiss(entryId: OverlayEntryId, reason: TransientFeedbackDismissReason) = Unit
        }
        val host = TransientFeedbackOverlayHost(presenter, toast)
        fun request(key: String) = OverlayRequest(key, OverlayType.Snackbar, SnackbarOverlaySpec(message = key))
        val a = OverlaySessionId("a")
        val b = OverlaySessionId("b")
        host.commit(a, listOf(request("first"), request("pending")))
        host.commit(b, listOf(request("other")))
        assertSame(failure, runCatching { host.clear(a) }.exceptionOrNull())
        assertEquals(listOf(OverlayEntryId(a, "first"), OverlayEntryId(b, "other")), shown)
        assertEquals(OverlayEntryId(b, "other"), host.snapshot().active)
        assertTrue(host.snapshot().pending.isEmpty())
        host.clear(a)
    }

    private fun requests(vararg keys: String) = keys.map { OverlayRequest(it, OverlayType.Dialog) }

    private fun host(dismiss: (String) -> Unit) =
        object : SessionBoundSurfaceOverlayHost<String, String, String>(OverlayType.Dialog, { "spec" to "content" }) {
            override fun onShow(entryId: OverlayEntryId, spec: String, content: String) = entryId.requestKey
            override fun onUpdate(handle: String, spec: String, content: String) = Unit
            override fun onDismiss(handle: String) = dismiss(handle)
        }
}
