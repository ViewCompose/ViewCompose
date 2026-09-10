package com.viewcompose.ui.foundation.samples

import com.viewcompose.ui.foundation.*

/** Demonstrates complete, terminal cleanup when one platform dismissal fails. */
fun overlayCleanupFailureSample() {
    val released = mutableListOf<String>()
    val host = object : SessionBoundSurfaceOverlayHost<String, String, String>(
        OverlayType.Dialog, { "spec" to "content" },
    ) {
        override fun onShow(entryId: OverlayEntryId, spec: String, content: String) = entryId.requestKey
        override fun onUpdate(handle: String, spec: String, content: String) = Unit
        override fun onDismiss(handle: String) {
            released += handle
            if (handle == "first") error("Platform dismissal failed")
        }
    }
    val session = OverlaySessionId("sample")
    host.commit(session, listOf(OverlayRequest("first", OverlayType.Dialog), OverlayRequest("second", OverlayType.Dialog)))
    val failure = runCatching { host.clear(session) }.exceptionOrNull()
    check(failure != null && released == listOf("first", "second"))
    host.clear(session)
    check(released.size == 2)
}
