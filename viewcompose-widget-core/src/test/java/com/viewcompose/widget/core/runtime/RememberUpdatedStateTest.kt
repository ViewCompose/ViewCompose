package com.viewcompose.widget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RememberUpdatedStateTest {
    @Test
    fun `reuses same state holder across renders`() {
        val harness = ComposerRuntimeHarness()

        val first = harness.render {
            rememberUpdatedState("A")
        }
        val second = harness.render {
            rememberUpdatedState("B")
        }

        assertSame(first, second)
        harness.dispose()
    }

    @Test
    fun `exposes latest value on every render`() {
        val harness = ComposerRuntimeHarness()

        val first = harness.render {
            rememberUpdatedState("A")
        }
        val second = harness.render {
            rememberUpdatedState("B")
        }

        assertSame(first, second)
        assertEquals("B", second.value)
        harness.dispose()
    }
}
