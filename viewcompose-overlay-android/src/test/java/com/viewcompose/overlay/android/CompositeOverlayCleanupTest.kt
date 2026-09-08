package com.viewcompose.overlay.android

import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayRequest
import com.viewcompose.ui.foundation.OverlaySessionId
import org.junit.Assert.*
import org.junit.Test

class CompositeOverlayCleanupTest {
    @Test fun `all delegate types receive cleanup when the first and last fail`() {
        val calls = mutableListOf<Int>()
        val first = IllegalStateException("first")
        val last = IllegalArgumentException("last")
        fun delegate(id: Int, failure: Throwable? = null) = object : OverlayHost {
            override fun commit(sessionId: OverlaySessionId, requests: List<OverlayRequest>) = Unit
            override fun clear(sessionId: OverlaySessionId) {
                calls += id
                failure?.let { throw it }
            }
        }
        val host = CompositeOverlayHost(delegate(1, first), delegate(2), delegate(3, last))
        assertSame(first, runCatching { host.clear(OverlaySessionId("test")) }.exceptionOrNull())
        assertEquals(listOf(1, 2, 3), calls)
        assertEquals(listOf(last), first.suppressed.toList())
    }
}
