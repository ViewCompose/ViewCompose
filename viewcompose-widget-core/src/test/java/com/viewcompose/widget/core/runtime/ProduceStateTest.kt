package com.viewcompose.widget.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProduceStateTest {
    @Test
    fun `produceState reuses state holder across renders`() {
        val harness = ComposerRuntimeHarness()

        val first = harness.render {
            produceState(initialValue = "initial") {
                value = "first"
                null
            }
        }
        val second = harness.render {
            produceState(initialValue = "other") {
                value = "second"
                null
            }
        }

        assertSame(first, second)
        assertEquals("first", first.value)
        assertEquals("first", second.value)
        harness.dispose()
    }

    @Test
    fun `produceState reruns producer when key changes`() {
        val harness = ComposerRuntimeHarness()

        harness.render {
            produceState(initialValue = 0, 1) {
                value = 1
                null
            }
        }
        val state = harness.render {
            produceState(initialValue = 0, 2) {
                value = 2
                null
            }
        }

        assertEquals(2, state.value)
        harness.dispose()
    }

    @Test
    fun `produceState keeps previous value when key is unchanged`() {
        val harness = ComposerRuntimeHarness()
        var starts = 0

        harness.render {
            produceState(initialValue = 0, "stable") {
                starts += 1
                value = starts
                null
            }
        }
        val state = harness.render {
            produceState(initialValue = 99, "stable") {
                starts += 1
                value = 999
                null
            }
        }

        assertEquals(1, starts)
        assertEquals(1, state.value)
        harness.dispose()
    }
}
