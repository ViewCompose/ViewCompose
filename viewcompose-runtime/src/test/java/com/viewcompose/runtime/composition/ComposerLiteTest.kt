package com.viewcompose.runtime.composition

import com.viewcompose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerLiteTest {
    @Test
    fun `compacts invalidations to nearest dirty ancestor`() {
        val parentState = mutableStateOf(0)
        val childState = mutableStateOf(0)
        val composer = ComposerLite()
        lateinit var parentScope: RecomposeScope

        composer.composeRoot {
            composer.runGroup(signature = "parent") { scope ->
                parentScope = scope
                parentState.value
                composer.runGroup(signature = "child") {
                    childState.value
                    Unit
                }
                Unit
            }
        }

        childState.value = 1
        parentState.value = 1

        val compacted = composer.drainInvalidations()
        assertEquals(1, compacted.size)
        assertSame(parentScope, compacted.first())
    }

    @Test
    fun `marks group dirty when explicit inputs change`() {
        val composer = ComposerLite()
        var runs = 0
        val counter = mutableStateOf(0)

        val compose = {
            composer.composeRoot {
                composer.runGroup(
                    signature = "node",
                    inputs = listOf(counter.value),
                ) {
                    runs += 1
                    Unit
                }
            }
        }

        compose()
        compose()
        counter.value = 1
        compose()

        assertEquals(2, runs)
    }

    @Test
    fun `recomposes only invalidated group while skipping clean sibling`() {
        val left = mutableStateOf(0)
        val right = mutableStateOf(0)
        val composer = ComposerLite()
        var leftRuns = 0
        var rightRuns = 0

        val compose = {
            composer.composeRoot {
                composer.runGroup(
                    signature = "left",
                    inputs = listOf(left.value),
                ) {
                    leftRuns += 1
                    left.value
                }
                composer.runGroup(
                    signature = "right",
                    inputs = listOf(right.value),
                ) {
                    rightRuns += 1
                    right.value
                }
            }
        }

        compose()
        left.value = 1
        compose()

        assertEquals(2, leftRuns)
        assertEquals(1, rightRuns)
    }

    @Test
    fun `composeRoot reads remain consistent within one snapshot pass`() {
        val state = mutableStateOf(0)
        val composer = ComposerLite()
        var firstRead = -1
        var secondRead = -1

        composer.composeRoot {
            firstRead = state.value
            state.value = 1
            secondRead = state.value
            Unit
        }

        assertEquals(0, firstRead)
        assertEquals(0, secondRead)
        assertEquals(1, state.value)
    }

    @Test
    fun `state invalidated during composition remains dirty for next pass`() {
        val state = mutableStateOf(0)
        val composer = ComposerLite()
        var runs = 0

        val first = composer.composeRoot {
            runs += 1
            val current = state.value
            if (current == 0) {
                state.value = 1
            }
            current
        }

        assertEquals(0, first)
        assertEquals(1, state.value)
        assertTrue(composer.hasPendingInvalidations())

        val second = composer.composeRoot {
            runs += 1
            state.value
        }

        assertEquals(1, second)
        assertEquals(2, runs)
        assertTrue(!composer.hasPendingInvalidations())
    }

    @Test
    fun `composeRoot rejects re-entrant invocation`() {
        val composer = ComposerLite()

        val error = runCatching {
            composer.composeRoot {
                composer.composeRoot { Unit }
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `side effect runs only after commit`() {
        val composer = ComposerLite()
        var executions = 0

        composer.composeRoot {
            composer.sideEffect { executions += 1 }
            Unit
        }

        assertEquals(0, executions)
        composer.commitSideEffects()
        assertEquals(1, executions)
    }

    @Test
    fun `disposable effect starts and replaces only after commit`() {
        val composer = ComposerLite()
        var starts = 0
        var disposals = 0
        var key = 1

        fun compose() {
            composer.requestRootRecompose()
            composer.composeRoot {
                composer.disposableEffect(keys = listOf(key)) {
                    starts += 1
                    { disposals += 1 }
                }
                Unit
            }
        }

        compose()
        assertEquals(0, starts)
        composer.commitSideEffects()
        assertEquals(1, starts)
        assertEquals(0, disposals)

        compose()
        composer.commitSideEffects()
        assertEquals(1, starts)
        assertEquals(0, disposals)

        key = 2
        compose()
        assertEquals(1, starts)
        assertEquals(0, disposals)
        composer.commitSideEffects()
        assertEquals(2, starts)
        assertEquals(1, disposals)

        composer.dispose()
        assertEquals(2, disposals)
    }

    @Test
    fun `dispose clears pending invalidations and stops future enqueue`() {
        val state = mutableStateOf(0)
        val composer = ComposerLite()
        composer.composeRoot {
            composer.runGroup(signature = "node", inputs = listOf(state.value)) {
                state.value
            }
        }
        state.value = 1
        assertTrue(composer.hasPendingInvalidations())

        composer.dispose()
        assertTrue(!composer.hasPendingInvalidations())

        state.value = 2
        assertTrue(!composer.hasPendingInvalidations())
    }
}
