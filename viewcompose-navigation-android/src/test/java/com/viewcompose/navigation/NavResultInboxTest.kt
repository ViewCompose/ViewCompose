package com.viewcompose.navigation

import android.os.Bundle
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavResultDelivery
import com.viewcompose.navigation.core.NavResultKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavResultInboxTest {
    @Test
    fun `mailbox consumes same-key values in FIFO order and suppresses plan replay`() {
        val key = NavResultKey.text("selection")
        val inbox = NavResultInbox(null)

        inbox.deliver(delivery(1L, key.encode("first")))
        inbox.deliver(delivery(1L, key.encode("first")))
        inbox.deliver(delivery(2L, key.encode("second")))

        assertEquals(2, inbox.pendingCount)
        assertEquals("first", inbox.peek(key))
        assertEquals("first", inbox.consume(key))
        assertEquals("second", inbox.consume(key))
        assertNull(inbox.consume(key))
        assertFalse(inbox.hasResult(key))
    }

    @Test
    fun `codec mismatch preserves the pending payload`() {
        val inbox = NavResultInbox(null)
        val textKey = NavResultKey.text("selection")
        inbox.deliver(delivery(1L, textKey.encode("first")))

        assertThrows<IllegalArgumentException> {
            inbox.consume(NavResultKey.int("selection"))
        }

        assertEquals(1, inbox.pendingCount)
        assertEquals("first", inbox.consume(textKey))
    }

    @Test
    fun `saved state restores values and sequence order`() {
        val textKey = NavResultKey.text("text")
        val numberKey = NavResultKey.long("number")
        val inbox = NavResultInbox(null)
        inbox.deliver(delivery(1L, textKey.encode("first")))
        inbox.deliver(delivery(2L, numberKey.encode(42L)))
        inbox.deliver(delivery(3L, textKey.encode("second")))

        val restored = NavResultInbox(inbox.saveState())

        assertEquals("first", restored.consume(textKey))
        assertEquals("second", restored.consume(textKey))
        assertEquals(42L, restored.consume(numberKey))
        assertEquals(0, restored.pendingCount)
    }

    @Test
    fun `unknown saved-state version fails closed to an empty inbox`() {
        val restored = NavResultInbox(
            Bundle().apply { putInt("formatVersion", Int.MAX_VALUE) },
        )

        assertEquals(0, restored.pendingCount)
        assertTrue(!restored.hasResult(NavResultKey.text("selection")))
    }

    private fun delivery(
        transactionId: Long,
        payload: com.viewcompose.navigation.core.NavResultPayload,
    ): NavResultDelivery {
        return NavResultDelivery(
            transactionId = transactionId,
            targetEntryId = NavEntryId("target"),
            payload = payload,
        )
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is T) return throwable
            throw throwable
        }
        fail("Expected ${T::class.simpleName} to be thrown.")
        error("Unreachable")
    }
}
