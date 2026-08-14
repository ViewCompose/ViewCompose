package com.viewcompose.runtime.composition

/*
 * Test responsibility: covers Composer Lite behavior in runtime and guards the contract against regressions.
 */

import com.viewcompose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerLiteTest {
    @Test
    fun `coalesces repeated invalidation callbacks before next composition`() {
        val state = mutableStateOf(0)
        var invalidationCallbacks = 0
        val composer = ComposerLite(
            onInvalidated = { invalidationCallbacks += 1 },
        )

        composer.composeRoot {
            state.value
        }

        state.value = 1
        state.value = 2
        state.value = 3

        assertEquals(1, invalidationCallbacks)
        assertEquals(1, composer.drainInvalidations().size)
    }

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
    fun `remember lifecycle starts and replaces during commit`() {
        val composer = ComposerLite()
        val events = mutableListOf<String>()
        var key = 1

        fun observer(name: String) = object : RememberObserver {
            override fun onRemembered() {
                events += "start:$name"
            }

            override fun onForgotten() {
                events += "dispose:$name"
            }

            override fun onAbandoned() {
                events += "abandon:$name"
            }
        }

        fun prepare(): ComposerLite.PreparedComposition<Unit> {
            composer.requestRootRecompose()
            return composer.prepareRoot {
                val currentKey = key
                composer.remember(keys = listOf(currentKey)) { observer(currentKey.toString()) }
                Unit
            }
        }

        val first = prepare()
        assertTrue(events.isEmpty())
        first.commit()
        assertEquals(listOf("start:1"), events)

        prepare().commit()
        assertEquals(listOf("start:1"), events)

        key = 2
        val replacement = prepare()
        assertEquals(listOf("start:1"), events)
        replacement.commit()
        assertEquals(listOf("start:1", "dispose:1", "start:2"), events)

        composer.dispose()
        assertEquals("dispose:2", events.last())
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
    fun `aborting prepared composition keeps old observer and discards side effects`() {
        val observed = mutableStateOf(0)
        val composer = ComposerLite()
        var effectKey = 1
        val events = mutableListOf<String>()
        var sideEffects = 0

        fun observer(name: String) = object : RememberObserver {
            override fun onRemembered() {
                events += "start:$name"
            }

            override fun onForgotten() {
                events += "dispose:$name"
            }

            override fun onAbandoned() {
                events += "abandon:$name"
            }
        }

        fun prepare(): ComposerLite.PreparedComposition<Unit> {
            return composer.prepareRoot {
                observed.value
                val currentKey = effectKey
                composer.remember(keys = listOf(currentKey)) { observer(currentKey.toString()) }
                composer.sideEffect {
                    sideEffects += 1
                }
            }
        }

        prepare().commit()
        composer.commitSideEffects()
        assertEquals(listOf("start:1"), events)
        assertEquals(1, sideEffects)

        observed.value = 1
        effectKey = 2
        prepare().abort()
        composer.commitSideEffects()

        assertEquals(listOf("start:1", "abandon:2"), events)
        assertEquals(1, sideEffects)
        assertTrue(composer.hasPendingInvalidations())

        effectKey = 1
        prepare().commit()
        composer.commitSideEffects()
        assertEquals(listOf("start:1", "abandon:2"), events)
        assertEquals(2, sideEffects)
    }

    @Test
    fun `aborting structure removal does not dispose retained child`() {
        val composer = ComposerLite()
        var includeChild = true
        val events = mutableListOf<String>()
        val observer = object : RememberObserver {
            override fun onRemembered() {
                events += "start"
            }

            override fun onForgotten() {
                events += "dispose"
            }

            override fun onAbandoned() {
                events += "abandon"
            }
        }

        fun prepare(): ComposerLite.PreparedComposition<Unit> {
            composer.requestRootRecompose()
            return composer.prepareRoot {
                if (includeChild) {
                    composer.runGroup(signature = "child") {
                        composer.remember<RememberObserver>(keys = emptyList()) { observer }
                        Unit
                    }
                }
            }
        }

        prepare().commit()
        assertEquals(listOf("start"), events)

        includeChild = false
        prepare().abort()
        assertEquals(listOf("start"), events)

        includeChild = true
        prepare().commit()
        assertEquals(listOf("start"), events)
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
    fun `commit dispatches every outgoing lifecycle before any incoming lifecycle`() {
        val composer = ComposerLite()
        val events = mutableListOf<String>()
        var generation = 1

        fun observer(name: String) = object : RememberObserver {
            override fun onRemembered() {
                events += "enter:$name"
            }

            override fun onForgotten() {
                events += "leave:$name"
            }

            override fun onAbandoned() {
                events += "abandon:$name"
            }
        }

        fun compose() {
            composer.requestRootRecompose()
            composer.prepareRoot {
                val current = generation
                composer.remember(keys = listOf("left", current)) {
                    observer("left:$current")
                }
                composer.remember(keys = listOf("right", current)) {
                    observer("right:$current")
                }
            }.commit()
        }

        compose()
        events.clear()
        generation = 2
        compose()

        assertEquals(
            listOf(
                "leave:left:1",
                "leave:right:1",
                "enter:left:2",
                "enter:right:2",
            ),
            events,
        )
    }

    @Test
    fun `throwing lifecycle callback is terminal and does not skip independent transitions`() {
        val composer = ComposerLite()
        val events = mutableListOf<String>()
        var generation = 1

        fun observer(name: String, throwOnLeave: Boolean = false) = object : RememberObserver {
            override fun onRemembered() {
                events += "enter:$name"
            }

            override fun onForgotten() {
                events += "leave:$name"
                if (throwOnLeave) error("leave failed:$name")
            }

            override fun onAbandoned() {
                events += "abandon:$name"
            }
        }

        fun prepare(): ComposerLite.PreparedComposition<Unit> {
            composer.requestRootRecompose()
            return composer.prepareRoot {
                val current = generation
                composer.remember(keys = listOf("first", current)) {
                    observer("first:$current", throwOnLeave = current == 1)
                }
                composer.remember<RememberObserver>(keys = listOf("second", current)) {
                    observer("second:$current")
                }
                Unit
            }
        }

        prepare().commit()
        events.clear()
        generation = 2

        val error = runCatching { prepare().commit() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(
            listOf(
                "leave:first:1",
                "leave:second:1",
                "enter:first:2",
                "enter:second:2",
            ),
            events,
        )

        composer.dispose()
        assertEquals(1, events.count { it == "leave:first:1" })
    }

    @Test
    fun `failed remember activation retries while successful siblings stay active`() {
        val composer = ComposerLite()
        var failingAttempts = 0
        var successfulAttempts = 0
        var failingForgotten = 0
        var failingAbandoned = 0

        val failing = object : RememberObserver {
            override fun onRemembered() {
                failingAttempts += 1
                if (failingAttempts == 1) {
                    error("transient activation failure")
                }
            }

            override fun onForgotten() {
                failingForgotten += 1
            }

            override fun onAbandoned() {
                failingAbandoned += 1
            }
        }
        val successful = object : RememberObserver {
            override fun onRemembered() {
                successfulAttempts += 1
            }

            override fun onForgotten() = Unit

            override fun onAbandoned() = Unit
        }

        fun commit(): Throwable? {
            composer.requestRootRecompose()
            return runCatching {
                composer.composeRoot {
                    composer.remember<RememberObserver>(keys = listOf("failing")) { failing }
                    composer.remember<RememberObserver>(keys = listOf("successful")) { successful }
                }
            }.exceptionOrNull()
        }

        assertTrue(commit() is IllegalStateException)
        assertEquals(1, failingAttempts)
        assertEquals(1, successfulAttempts)

        assertTrue(commit() == null)
        assertEquals(2, failingAttempts)
        assertEquals(1, successfulAttempts)

        composer.dispose()
        assertEquals(1, failingForgotten)
        assertEquals(0, failingAbandoned)
    }

    @Test
    fun `disposing a never activated remembered value abandons it`() {
        val composer = ComposerLite()
        var remembered = 0
        var forgotten = 0
        var abandoned = 0
        val observer = object : RememberObserver {
            override fun onRemembered() {
                remembered += 1
                error("activation failed")
            }

            override fun onForgotten() {
                forgotten += 1
            }

            override fun onAbandoned() {
                abandoned += 1
            }
        }

        val error = runCatching {
            composer.composeRoot {
                composer.remember<RememberObserver>(keys = listOf("pending")) { observer }
            }
        }.exceptionOrNull()
        composer.dispose()

        assertTrue(error is IllegalStateException)
        assertEquals(1, remembered)
        assertEquals(0, forgotten)
        assertEquals(1, abandoned)
    }

    @Test
    fun `remember updated state isolates candidate value until commit`() {
        val composer = ComposerLite()
        var input = "committed"

        val initial = composer.prepareRoot {
            composer.rememberUpdatedState(input)
        }
        initial.commit()
        val holder = initial.value
        assertEquals("committed", holder.value)

        input = "aborted"
        composer.requestRootRecompose()
        var candidateRead = ""
        val aborted = composer.prepareRoot {
            val sameHolder = composer.rememberUpdatedState(input)
            assertSame(holder, sameHolder)
            candidateRead = sameHolder.value
            sameHolder
        }

        assertEquals("aborted", candidateRead)
        assertEquals("committed", holder.value)
        aborted.abort()
        assertEquals("committed", holder.value)

        input = "published"
        composer.requestRootRecompose()
        val committed = composer.prepareRoot {
            composer.rememberUpdatedState(input)
        }
        assertEquals("committed", holder.value)
        committed.commit()
        assertEquals("published", holder.value)
    }

    @Test
    fun `remember updated state publishes before outgoing and incoming callbacks`() {
        val composer = ComposerLite()
        val events = mutableListOf<String>()
        var input = "first"
        var generation = 1

        fun compose() {
            composer.requestRootRecompose()
            composer.prepareRoot {
                val latest = composer.rememberUpdatedState(input)
                val current = generation
                composer.remember(keys = listOf(current)) {
                    object : RememberObserver {
                        override fun onRemembered() {
                            events += "enter:$current:${latest.value}"
                        }

                        override fun onForgotten() {
                            events += "leave:$current:${latest.value}"
                        }

                        override fun onAbandoned() = Unit
                    }
                }
            }.commit()
        }

        compose()
        input = "second"
        generation = 2
        compose()

        assertEquals(
            listOf(
                "enter:1:first",
                "leave:1:second",
                "enter:2:second",
            ),
            events,
        )
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
    fun `multiple side effect failures keep the first and suppress later failures`() {
        val first = IllegalArgumentException("first failed")
        val second = IllegalStateException("second failed")
        val composer = ComposerLite()

        composer.composeRoot {
            composer.sideEffect { throw first }
            composer.sideEffect { throw second }
        }
        val error = runCatching(composer::commitSideEffects).exceptionOrNull()

        assertSame(first, error)
        assertTrue(first.suppressed.contains(second))
        assertTrue(first.suppressed.any { it.message.orEmpty().contains("slot=0") })
        assertTrue(second.suppressed.any { it.message.orEmpty().contains("slot=1") })
        composer.commitSideEffects()
        composer.dispose()
    }

    @Test
    fun `effect failures retain original throwable and append bounded operation diagnostics`() {
        val observerFailure = IllegalArgumentException("observer failed")
        val sideEffectFailure = IllegalStateException("side effect failed")
        val opaqueKey = object {
            override fun toString(): String = error("diagnostics must not call arbitrary toString")
        }
        val composer = ComposerLite()

        val rememberError = runCatching {
            composer.composeRoot {
                composer.remember<RememberObserver>(keys = listOf(opaqueKey)) {
                    object : RememberObserver {
                        override fun onRemembered() = throw observerFailure

                        override fun onForgotten() = Unit

                        override fun onAbandoned() = Unit
                    }
                }
                Unit
            }
        }.exceptionOrNull()

        composer.requestRootRecompose()
        composer.composeRoot {
            composer.withKeys(listOf("publication")) {
                composer.sideEffect { throw sideEffectFailure }
            }
        }
        val sideEffectError = runCatching(composer::commitSideEffects).exceptionOrNull()

        assertSame(observerFailure, rememberError)
        assertTrue(
            observerFailure.suppressed.single().message.orEmpty().contains(
                "operation=remember scope=root slot=0",
            ),
        )
        assertSame(sideEffectFailure, sideEffectError)
        assertTrue(
            sideEffectFailure.suppressed.single().message.orEmpty().contains(
                "effect=SideEffect operation=run scope=root slot=0 keys=[String(publication)]",
            ),
        )
    }

    @Test
    fun `slow synchronous effect warnings are opt in and identify lifecycle and side effect`() {
        val warnings = mutableListOf<String>()
        val composer = ComposerLite(
            warningLogger = warnings::add,
            synchronousEffectWarningThresholdNanos = 0L,
            effectFrameIdProvider = { 42L },
        )

        composer.composeRoot {
            composer.remember<RememberObserver>(keys = listOf("resource")) {
                object : RememberObserver {
                    override fun onRemembered() = Unit

                    override fun onForgotten() = Unit

                    override fun onAbandoned() = Unit
                }
            }
            composer.sideEffect {}
        }
        composer.commitSideEffects()
        composer.dispose()

        assertTrue(warnings.any { it.contains("operation=remember") })
        assertTrue(warnings.any { it.contains("effect=SideEffect operation=run") })
        assertTrue(warnings.any { it.contains("operation=forget") })
        assertTrue(warnings.all { it.contains("frame=42") })
    }

    @Test
    fun `negative synchronous effect warning threshold is rejected`() {
        val error = runCatching {
            ComposerLite(synchronousEffectWarningThresholdNanos = -1L)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `remember and side effect callbacks cannot reenter composition`() {
        val composer = ComposerLite()
        val errors = mutableListOf<Throwable?>()

        composer.composeRoot {
            composer.remember<RememberObserver>(keys = listOf("observer")) {
                object : RememberObserver {
                    override fun onRemembered() {
                        errors += runCatching {
                            composer.composeRoot { Unit }
                        }.exceptionOrNull()
                    }

                    override fun onForgotten() = Unit

                    override fun onAbandoned() = Unit
                }
            }
            composer.sideEffect {
                errors += runCatching {
                    composer.composeRoot { Unit }
                }.exceptionOrNull()
            }
        }
        composer.commitSideEffects()

        assertEquals(2, errors.size)
        assertTrue(errors.all { it is IllegalStateException })
        assertTrue(errors.all { it?.message.orEmpty().contains("Re-entrant") })
        composer.dispose()
    }

    @Test
    fun `effect callbacks cannot dispose the composer reentrantly`() {
        val composer = ComposerLite()
        val errors = mutableListOf<Throwable?>()

        composer.composeRoot {
            composer.remember<RememberObserver>(keys = listOf("observer")) {
                object : RememberObserver {
                    override fun onRemembered() {
                        errors += runCatching(composer::dispose).exceptionOrNull()
                    }

                    override fun onForgotten() = Unit

                    override fun onAbandoned() = Unit
                }
            }
            composer.sideEffect {
                errors += runCatching(composer::dispose).exceptionOrNull()
            }
        }
        composer.commitSideEffects()

        assertEquals(2, errors.size)
        assertTrue(errors.all { it is IllegalStateException })
        assertTrue(errors.all { it?.message.orEmpty().contains("disposed") })
        composer.dispose()
    }

    @Test
    fun `composer disposal is idempotent and terminal`() {
        val composer = ComposerLite()
        composer.composeRoot { Unit }

        composer.dispose()
        composer.dispose()

        val composeError = runCatching {
            composer.composeRoot { Unit }
        }.exceptionOrNull()
        val requestError = runCatching(composer::requestRootRecompose).exceptionOrNull()

        assertTrue(composeError is IllegalStateException)
        assertTrue(composeError?.message.orEmpty().contains("disposed"))
        assertTrue(requestError is IllegalStateException)
        assertTrue(requestError?.message.orEmpty().contains("disposed"))
    }

    @Test
    fun `prepared commit is confined to the composer owner thread`() {
        val composer = ComposerLite()
        val prepared = composer.prepareRoot { Unit }
        var crossThreadError: Throwable? = null
        val thread = Thread {
            crossThreadError = runCatching(prepared::commit).exceptionOrNull()
        }

        thread.start()
        thread.join()

        assertTrue(crossThreadError is IllegalStateException)
        assertTrue(crossThreadError?.message.orEmpty().contains("owner thread"))
        prepared.commit()
        composer.dispose()
    }

    @Test
    fun `composition operations reject access after prepare returns`() {
        val composer = ComposerLite()
        val prepared = composer.prepareRoot { Unit }

        val errors = listOf(
            runCatching {
                composer.runGroup(signature = "late") { Unit }
            }.exceptionOrNull(),
            runCatching {
                composer.remember(keys = emptyList()) { Unit }
            }.exceptionOrNull(),
            runCatching {
                composer.sideEffect {}
            }.exceptionOrNull(),
            runCatching {
                composer.nextSaveableKey()
            }.exceptionOrNull(),
            runCatching {
                composer.withKeys(emptyList()) { Unit }
            }.exceptionOrNull(),
        )

        assertTrue(errors.all { it is IllegalStateException })
        assertTrue(errors.all { it?.message.orEmpty().contains("actively executing") })
        prepared.abort()
        composer.dispose()
    }

    @Test
    fun `composition operations are confined before mutating positional cursors`() {
        val composer = ComposerLite()
        var crossThreadError: Throwable? = null

        composer.composeRoot {
            val thread = Thread {
                crossThreadError = runCatching {
                    composer.remember(keys = listOf("cross-thread")) { Unit }
                }.exceptionOrNull()
            }
            thread.start()
            thread.join()

            composer.remember(keys = listOf("owner")) { "retained" }
        }

        assertTrue(crossThreadError is IllegalStateException)
        assertTrue(crossThreadError?.message.orEmpty().contains("owner thread"))
        composer.requestRootRecompose()
        assertEquals(
            "retained",
            composer.composeRoot {
                composer.remember(keys = listOf("owner")) { "replaced" }
            },
        )
        composer.dispose()
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
    fun `keyed group state follows identity across sibling reorder`() {
        val composer = ComposerLite()
        val retained = linkedMapOf<String, Any>()

        fun compose(order: List<String>) {
            composer.requestRootRecompose()
            composer.composeRoot {
                order.forEach { key ->
                    composer.withKeys(listOf(key)) {
                        composer.runGroup(signature = "item") {
                            retained[key] = composer.remember(emptyList()) { Any() }
                        }
                    }
                }
            }
        }

        compose(listOf("A", "B", "C"))
        val first = retained.toMap()
        compose(listOf("C", "A", "B"))

        assertSame(first.getValue("A"), retained.getValue("A"))
        assertSame(first.getValue("B"), retained.getValue("B"))
        assertSame(first.getValue("C"), retained.getValue("C"))
    }

    @Test
    fun `duplicate effective keyed siblings fail without saveable state`() {
        val composer = ComposerLite()

        val failure = runCatching {
            composer.composeRoot {
                repeat(2) {
                    composer.withKeys(listOf("duplicate")) {
                        composer.runGroup(signature = "item") { Any() }
                    }
                }
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("Duplicate effective keyed group identity"))
        composer.dispose()
    }

    @Test
    fun `keyed insertion deletion and reversal preserve identity with balanced lifecycle`() {
        val composer = ComposerLite()
        val events = mutableListOf<String>()

        fun compose(order: List<String>): Map<String, Any> {
            composer.requestRootRecompose()
            return composer.composeRoot {
                order.associateWith { key ->
                    composer.withKeys(listOf(key)) {
                        composer.runGroup(signature = "item") {
                            composer.remember<RememberObserver>(emptyList()) {
                                object : RememberObserver {
                                    override fun onRemembered() {
                                        events += "remember:$key"
                                    }

                                    override fun onForgotten() {
                                        events += "forget:$key"
                                    }

                                    override fun onAbandoned() {
                                        events += "abandon:$key"
                                    }
                                }
                            }
                            composer.remember(emptyList()) { Any() }
                        }
                    }
                }
            }
        }

        val initial = compose(listOf("A", "B", "C"))
        val reversed = compose(listOf("C", "B", "A"))
        assertSame(initial.getValue("A"), reversed.getValue("A"))
        assertSame(initial.getValue("B"), reversed.getValue("B"))
        assertSame(initial.getValue("C"), reversed.getValue("C"))

        val changed = compose(listOf("D", "C", "A"))
        assertSame(initial.getValue("A"), changed.getValue("A"))
        assertSame(initial.getValue("C"), changed.getValue("C"))
        assertEquals(1, events.count { it == "remember:D" })
        assertEquals(1, events.count { it == "forget:B" })
        assertTrue(events.none { it.startsWith("abandon:") })

        composer.dispose()
        assertEquals(1, events.count { it == "forget:A" })
        assertEquals(1, events.count { it == "forget:C" })
        assertEquals(1, events.count { it == "forget:D" })
    }

    @Test
    fun `state observation ownership follows keyed scope movement`() {
        val composer = ComposerLite()
        val states = listOf("A", "B", "C").associateWith { mutableStateOf(0) }
        val runs = mutableMapOf<String, Int>()

        fun compose(order: List<String>): Map<String, Int> {
            return composer.composeRoot {
                order.associateWith { key ->
                    composer.withKeys(listOf(key)) {
                        composer.runGroup(signature = "item") {
                            runs[key] = runs.getOrDefault(key, 0) + 1
                            states.getValue(key).value
                        }
                    }
                }
            }
        }

        assertEquals(mapOf("A" to 0, "B" to 0, "C" to 0), compose(listOf("A", "B", "C")))
        states.getValue("B").value = 1
        assertEquals(mapOf("C" to 0, "B" to 1, "A" to 0), compose(listOf("C", "B", "A")))
        assertEquals(mapOf("A" to 1, "B" to 2, "C" to 1), runs)
        composer.dispose()
    }

    @Test
    fun `aborted keyed reorder restores structure and invalidation ownership`() {
        val composer = ComposerLite()
        val states = listOf("A", "B", "C").associateWith { mutableStateOf(0) }
        val tokens = mutableMapOf<String, Any>()
        val runs = mutableMapOf<String, Int>()

        fun content(order: List<String>): Map<String, Pair<Any, Int>> {
            return order.associateWith { key ->
                composer.withKeys(listOf(key)) {
                    composer.runGroup(signature = "item") {
                        runs[key] = runs.getOrDefault(key, 0) + 1
                        val token = composer.remember(emptyList()) { Any() }
                        tokens.putIfAbsent(key, token)
                        token to states.getValue(key).value
                    }
                }
            }
        }

        val initial = composer.composeRoot { content(listOf("A", "B", "C")) }
        states.getValue("B").value = 1
        val prepared = composer.prepareRoot { content(listOf("C", "B", "A")) }
        assertSame(initial.getValue("A").first, prepared.value.getValue("A").first)
        assertEquals(1, prepared.value.getValue("B").second)
        prepared.abort()

        assertTrue(composer.hasPendingInvalidations())
        val committed = composer.composeRoot { content(listOf("A", "B", "C")) }
        assertSame(initial.getValue("A").first, committed.getValue("A").first)
        assertSame(initial.getValue("B").first, committed.getValue("B").first)
        assertSame(initial.getValue("C").first, committed.getValue("C").first)
        assertEquals(mapOf("A" to 1, "B" to 3, "C" to 1), runs)
        composer.dispose()
    }

    @Test
    fun `explicit saveable namespace is stable across keyed sibling reorder`() {
        val composer = ComposerLite()
        val keysByIdentity = linkedMapOf<String, String>()

        fun compose(order: List<String>) {
            composer.requestRootRecompose()
            composer.composeRoot {
                order.forEach { key ->
                    composer.withKeys(listOf(key)) {
                        composer.runGroup(signature = "item") {
                            keysByIdentity[key] = composer.scopedExplicitSaveableKey("field")
                        }
                    }
                }
            }
        }

        compose(listOf("A", "B"))
        val first = keysByIdentity.toMap()
        compose(listOf("B", "A"))

        assertEquals(first, keysByIdentity)
        assertEquals(2, keysByIdentity.values.toSet().size)
    }

    @Test
    fun `saveable namespace rejects unequal keyed sibling hash collisions`() {
        val composer = ComposerLite()
        val first = CollidingKey("first")
        val second = CollidingKey("second")

        val failure = runCatching {
            composer.composeRoot {
                listOf(first, second).forEach { key ->
                    composer.withKeys(listOf(key)) {
                        composer.runGroup(signature = "item") {
                            composer.scopedExplicitSaveableKey("field")
                        }
                    }
                }
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("collision-free hashCode"))
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

    private data class CollidingKey(
        val value: String,
    ) {
        override fun hashCode(): Int = 1
    }
}
