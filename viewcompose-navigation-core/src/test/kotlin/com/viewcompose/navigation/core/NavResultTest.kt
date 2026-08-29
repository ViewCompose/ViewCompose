package com.viewcompose.navigation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class NavResultTest {
    @Test
    fun `built in keys round trip every supported scalar type`() {
        roundTrip(NavResultKey.text("text"), "value")
        roundTrip(NavResultKey.int("int"), 7)
        roundTrip(NavResultKey.long("long"), Long.MAX_VALUE)
        roundTrip(NavResultKey.boolean("boolean"), true)
        roundTrip(NavResultKey.float("float"), 1.5f)
        roundTrip(NavResultKey.double("double"), 2.5)
    }

    @Test
    fun `custom key keeps stable identity and rejects another codec`() {
        val first = NavResultKey(
            name = "selection",
            typeId = "sample.Selection.v1",
            encoder = { value: Selection -> NavValue.Text(value.id) },
            decoder = { value -> Selection((value as NavValue.Text).value) },
        )
        val equivalent = NavResultKey(
            name = "selection",
            typeId = "sample.Selection.v1",
            encoder = { value: Selection -> NavValue.Text(value.id) },
            decoder = { value -> Selection((value as NavValue.Text).value) },
        )
        val wrongType = NavResultKey.text("selection")
        val payload = first.encode(Selection("primary"))

        assertEquals(first, equivalent)
        assertNotEquals(first, wrongType)
        assertEquals(Selection("primary"), equivalent.decode(payload))
        assertThrows<IllegalArgumentException> { wrongType.decode(payload) }
    }

    private fun <T> roundTrip(key: NavResultKey<T>, value: T) {
        assertEquals(value, key.decode(key.encode(value)))
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

    private data class Selection(val id: String)
}
