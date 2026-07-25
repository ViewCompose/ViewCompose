package com.viewcompose.widget.core

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
            }
        }
        val second = harness.render {
            produceState(initialValue = "other") {
                value = "second"
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
            }
        }
        val state = harness.render {
            produceState(initialValue = 0, 2) {
                value = 2
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
            }
        }
        val state = harness.render {
            produceState(initialValue = 99, "stable") {
                starts += 1
                value = 999
            }
        }

        assertEquals(1, starts)
        assertEquals(1, state.value)
        harness.dispose()
    }

    @Test
    fun `produceState awaitDispose runs when key changes and on disposal`() = runBlocking {
        val harness = ComposerRuntimeHarness()
        val disposals = mutableListOf<Int>()
        var key = 1

        fun render() {
            harness.render {
                val launchedKey = key
                produceState(initialValue = 0, launchedKey) {
                    value = launchedKey
                    awaitDispose {
                        disposals += launchedKey
                    }
                }
            }
        }

        render()
        key = 2
        render()
        yield()
        assertEquals(listOf(1), disposals)

        harness.dispose()
        yield()
        assertEquals(listOf(1, 2), disposals)
    }

    @Test
    fun `produceState coroutine is cancelled when composition is disposed`() = runBlocking {
        val harness = ComposerRuntimeHarness()
        var cancelled = false

        harness.render {
            produceState(initialValue = 0) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        harness.dispose()
        yield()

        assertEquals(true, cancelled)
    }
}
