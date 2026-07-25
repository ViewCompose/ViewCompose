package com.viewcompose.runtime.composition

import com.viewcompose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `aborting prepared composition restores remember slots`() {
        val composer = ComposerLite()
        val original = composer.composeRoot {
            composer.remember(keys = listOf("stable")) { Any() }
        }
        lateinit var abandoned: Any

        composer.requestRootRecompose()
        val prepared = composer.prepareRoot {
            abandoned = composer.remember(keys = listOf("replacement")) { Any() }
            abandoned
        }
        prepared.abort()

        composer.requestRootRecompose()
        val restored = composer.composeRoot {
            composer.remember(keys = listOf("stable")) { Any() }
        }

        assertSame(original, restored)
        assertFalse(abandoned === restored)
    }

    @Test
    fun `aborting prepared composition keeps old effect and discards side effects`() {
        val observed = mutableStateOf(0)
        val composer = ComposerLite()
        var effectKey = 1
        var starts = 0
        var disposals = 0
        var sideEffects = 0

        fun prepare(): ComposerLite.PreparedComposition<Unit> {
            return composer.prepareRoot {
                observed.value
                composer.disposableEffect(keys = listOf(effectKey)) {
                    starts += 1
                    { disposals += 1 }
                }
                composer.sideEffect {
                    sideEffects += 1
                }
            }
        }

        prepare().commit()
        composer.commitSideEffects()
        assertEquals(1, starts)
        assertEquals(0, disposals)
        assertEquals(1, sideEffects)

        observed.value = 1
        effectKey = 2
        prepare().abort()
        composer.commitSideEffects()

        assertEquals(1, starts)
        assertEquals(0, disposals)
        assertEquals(1, sideEffects)
        assertTrue(composer.hasPendingInvalidations())

        effectKey = 1
        prepare().commit()
        composer.commitSideEffects()
        assertEquals(1, starts)
        assertEquals(0, disposals)
        assertEquals(2, sideEffects)
    }

    @Test
    fun `aborting structure removal does not dispose retained child`() {
        val composer = ComposerLite()
        var includeChild = true
        var starts = 0
        var disposals = 0

        fun prepare(): ComposerLite.PreparedComposition<Unit> {
            composer.requestRootRecompose()
            return composer.prepareRoot {
                if (includeChild) {
                    composer.runGroup(signature = "child") {
                        composer.disposableEffect(keys = emptyList()) {
                            starts += 1
                            { disposals += 1 }
                        }
                    }
                }
            }
        }

        prepare().commit()
        composer.commitSideEffects()
        assertEquals(1, starts)

        includeChild = false
        prepare().abort()
        assertEquals(0, disposals)

        includeChild = true
        prepare().commit()
        composer.commitSideEffects()
        assertEquals(1, starts)
        assertEquals(0, disposals)
    }

    @Test
    fun `throwing composition automatically restores previous observation`() {
        val observed = mutableStateOf(0)
        val composer = ComposerLite()
        composer.composeRoot {
            observed.value
        }

        composer.requestRootRecompose()
        runCatching {
            composer.prepareRoot {
                observed.value
                error("boom")
            }
        }

        observed.value = 1

        assertTrue(composer.hasPendingInvalidations())
    }

    @Test
    fun `remember observer follows commit forget and abandon lifecycle`() {
        val composer = ComposerLite()
        val events = mutableListOf<String>()
        var key = 1

        fun observer(name: String) = object : RememberObserver {
            override fun onRemembered() {
                events += "$name:remembered"
            }

            override fun onForgotten() {
                events += "$name:forgotten"
            }

            override fun onAbandoned() {
                events += "$name:abandoned"
            }
        }

        val first = observer("first")
        val second = observer("second")
        val abandoned = observer("abandoned")

        composer.prepareRoot {
            composer.remember(keys = listOf(key)) { first }
        }.commit()
        assertEquals(listOf("first:remembered"), events)

        key = 2
        composer.requestRootRecompose()
        composer.prepareRoot {
            composer.remember(keys = listOf(key)) { abandoned }
        }.abort()
        assertEquals(
            listOf(
                "first:remembered",
                "abandoned:abandoned",
            ),
            events,
        )

        composer.requestRootRecompose()
        composer.prepareRoot {
            composer.remember(keys = listOf(key)) { second }
        }.commit()
        assertEquals(
            listOf(
                "first:remembered",
                "abandoned:abandoned",
                "first:forgotten",
                "second:remembered",
            ),
            events,
        )

        composer.requestRootRecompose()
        composer.prepareRoot { Unit }.commit()
        assertEquals("second:forgotten", events.last())
    }

    @Test
    fun `abandon callback failure does not prevent transaction rollback`() {
        val composer = ComposerLite()
        val retained = composer.composeRoot {
            composer.remember(keys = listOf("retained")) { Any() }
        }
        val throwingObserver = object : RememberObserver {
            override fun onRemembered() = Unit

            override fun onForgotten() = Unit

            override fun onAbandoned() {
                error("abandon failed")
            }
        }

        composer.requestRootRecompose()
        val prepared = composer.prepareRoot {
            composer.remember(keys = listOf("replacement")) {
                throwingObserver
            }
        }
        val error = runCatching(prepared::abort).exceptionOrNull()

        composer.requestRootRecompose()
        val restored = composer.composeRoot {
            composer.remember(keys = listOf("retained")) { Any() }
        }
        assertTrue(error is IllegalStateException)
        assertSame(retained, restored)
    }

    @Test
    fun `side effect failure does not skip later committed effects`() {
        val composer = ComposerLite()
        val events = mutableListOf<String>()
        composer.composeRoot {
            composer.sideEffect {
                events += "first"
                error("side effect failed")
            }
            composer.sideEffect {
                events += "second"
            }
        }

        val error = runCatching(composer::commitSideEffects).exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf("first", "second"), events)
    }

    @Test
    fun `saveable slot keys are deterministic across composer recreation`() {
        fun collectKeys(): List<String> {
            val composer = ComposerLite()
            return buildList {
                composer.composeRoot {
                    add(composer.nextSaveableKey())
                    composer.runGroup(signature = "child") {
                        add(composer.nextSaveableKey())
                        composer.withKeys(listOf("stable-item")) {
                            add(composer.nextSaveableKey())
                        }
                    }
                }
            }
        }

        assertEquals(collectKeys(), collectKeys())
    }

    @Test
    fun `saveable slot keys distinguish positions and explicit keys`() {
        val composer = ComposerLite()
        val keys = buildList {
            composer.composeRoot {
                add(composer.nextSaveableKey())
                add(composer.nextSaveableKey())
                composer.withKeys(listOf("first")) {
                    add(composer.nextSaveableKey())
                }
                composer.withKeys(listOf("second")) {
                    add(composer.nextSaveableKey())
                }
            }
        }

        assertEquals(keys.size, keys.toSet().size)
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
