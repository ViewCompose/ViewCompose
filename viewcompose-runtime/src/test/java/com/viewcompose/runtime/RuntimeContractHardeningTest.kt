package com.viewcompose.runtime

import com.viewcompose.runtime.observation.RuntimeObservation
import com.viewcompose.runtime.state.SnapshotStateObject
import org.junit.Assert.*
import org.junit.Test

class RuntimeContractHardeningTest {
    @Test fun `notification reads committed state when apply is called inside enter`() {
        val source = mutableStateOf(0)
        val sideEffect = mutableStateOf(0)
        val derived = derivedStateOf { source.value * 10 }
        val seen = mutableListOf<Int>()
        val observer = RuntimeObservation.observeReads({
            seen += source.value
            sideEffect.value = source.value
        }) { source.value }.second
        try {
            Snapshot.takeMutableSnapshot().use { snapshot ->
                snapshot.enter {
                    source.value = 1
                    assertEquals(10, derived.value)
                    assertEquals(SnapshotApplyResult.Success, snapshot.apply())
                    assertThrows(IllegalStateException::class.java) { source.value }
                    assertThrows(IllegalStateException::class.java) { derived.value }
                }
            }
            assertEquals(listOf(1), seen)
            assertEquals(1, sideEffect.value)
        } finally { observer.dispose() }
    }

    @Test fun `sibling snapshots never share derived cache entries`() {
        val source = mutableStateOf(0)
        val derived = derivedStateOf { source.value * 10 }
        Snapshot.takeMutableSnapshot().use { first ->
            Snapshot.takeMutableSnapshot().use { second ->
                first.enter { source.value = 1; assertEquals(10, derived.value) }
                second.enter { source.value = 2; assertEquals(20, derived.value) }
                first.enter { assertEquals(10, derived.value) }
                assertEquals(0, derived.value)
            }
        }
    }

    @Test fun `read and mutable children freeze pending parent values including null`() {
        val source = mutableStateOf<String?>("global")
        val derived = derivedStateOf { source.value }
        Snapshot.takeMutableSnapshot().use { parent ->
            parent.enter {
                source.value = null
                Snapshot.takeSnapshot().use { read ->
                    Snapshot.takeMutableSnapshot().use { child ->
                        source.value = "later"
                        read.enter { assertNull(source.value); assertNull(derived.value) }
                        child.enter { assertNull(source.value); assertNull(derived.value) }
                        assertEquals("later", derived.value)
                    }
                }
            }
        }
    }

    @Test fun `nested transaction applies after parent write and publishes only at root`() {
        val state = mutableStateOf(0)
        var notifications = 0
        val observation = RuntimeObservation.observeReads({ notifications++ }) { state.value }.second
        try {
            Snapshot.withMutableSnapshot {
                state.value = 1
                Snapshot.withMutableSnapshot { state.value = 2 }
                assertEquals(2, state.value)
                assertEquals(0, notifications)
            }
            assertEquals(2, state.value)
            assertEquals(1, notifications)
        } finally { observation.dispose() }
    }

    @Test fun `grandchild uses frozen inherited baseline without watching live ancestors`() {
        val state = mutableStateOf(0)
        Snapshot.takeMutableSnapshot().use { parent -> parent.enter {
            state.value = 1
            Snapshot.takeMutableSnapshot().use { child ->
                state.value = 9
                child.enter {
                    assertEquals(1, state.value)
                    Snapshot.withMutableSnapshot { state.value = 2 }
                    assertEquals(2, state.value)
                }
                assertEquals(SnapshotApplyResult.Failure(1), child.apply())
                assertEquals(9, state.value)
            }
        } }
        assertEquals(0, state.value)
    }

    @Test fun `later parent writes conflict even when they equal the child candidate`() {
        val state = mutableStateOf(0)
        Snapshot.takeMutableSnapshot().use { parent -> parent.enter {
            state.value = 1
            Snapshot.takeMutableSnapshot().use { child ->
                child.enter { state.value = 2 }
                state.value = 2
                assertEquals(SnapshotApplyResult.Failure(1), child.apply())
                assertEquals(2, state.value)
            }
        } }
    }

    @Test fun `nested merge receives the value visible when the child was created`() {
        var previousSeen = -1
        val state = mutableStateOf(0, object : SnapshotMutationPolicy<Int> {
            override fun equivalent(a: Int, b: Int) = a == b
            override fun merge(previous: Int, current: Int, applied: Int): Int {
                previousSeen = previous
                return applied + current - previous
            }
        })
        Snapshot.withMutableSnapshot {
            state.value = 10
            Snapshot.takeMutableSnapshot().use { child ->
                child.enter { state.value = 12 }
                state.value = 20
                assertEquals(SnapshotApplyResult.Success, child.apply())
                assertEquals(22, state.value)
            }
        }
        assertEquals(10, previousSeen)
        assertEquals(22, state.value)
    }

    @Test fun `nullable parent baseline applies without a false conflict`() {
        val state = mutableStateOf<String?>("initial")
        Snapshot.withMutableSnapshot {
            state.value = null
            Snapshot.withMutableSnapshot { state.value = "child" }
        }
        assertEquals("child", state.value)
    }

    @Test fun `successful apply is terminal for enter and for further writes in an active block`() {
        val state = mutableStateOf(0)
        Snapshot.takeMutableSnapshot().use { snapshot ->
            snapshot.enter {
                state.value = 1
                snapshot.apply()
                assertTrue(runCatching { state.value = 2 }.exceptionOrNull() is IllegalStateException)
            }
            assertTrue(runCatching { snapshot.enter {} }.exceptionOrNull() is IllegalStateException)
            assertTrue(runCatching { snapshot.apply() }.exceptionOrNull() is IllegalStateException)
        }
        assertEquals(1, state.value)
    }

    @Test fun `child cannot apply into a terminal parent even with no writes`() {
        Snapshot.takeMutableSnapshot().use { parent ->
            val child = parent.enter { Snapshot.takeMutableSnapshot() }
            child.use {
                parent.apply()
                assertTrue(runCatching { child.apply() }.exceptionOrNull() is IllegalStateException)
            }
        }
    }

    @Test fun `temporary derived consumers release every upstream subscription`() {
        val source = mutableStateOf(0)
        repeat(100) {
            val derived = derivedStateOf { source.value + 1 }
            val observation = RuntimeObservation.observeReads({}) { derived.value }.second
            observation.dispose()
        }
        assertEquals(0, (source as SnapshotStateObject).snapshotObservers().size)
    }

    @Test fun `unobserved derived reads remain cached and fresh without retaining subscriptions`() {
        val source = mutableStateOf(1)
        var calls = 0
        val derived = derivedStateOf { calls++; source.value * 2 }
        assertEquals(2, derived.value)
        assertEquals(2, derived.value)
        assertEquals(1, calls)
        assertTrue((source as SnapshotStateObject).snapshotObservers().isEmpty())
        source.value = 2
        assertEquals(4, derived.value)
        assertTrue(source.snapshotObservers().isEmpty())
    }

    @Test fun `reobservation reconnects dependencies after an unobserved cache hit`() {
        val source = mutableStateOf(1)
        val derived = derivedStateOf { source.value }
        RuntimeObservation.observeReads({}) { derived.value }.second.dispose()
        source.value = 2
        assertEquals(2, derived.value)
        var notified = 0
        val observer = RuntimeObservation.observeReads({ notified++ }) { derived.value }.second
        try {
            source.value = 3
            assertEquals(1, notified)
            assertEquals(3, derived.value)
        } finally { observer.dispose() }
        assertTrue((source as SnapshotStateObject).snapshotObservers().isEmpty())
    }

    @Test fun `disposing a derived chain releases sources after its last consumer`() {
        val source = mutableStateOf(1)
        val first = derivedStateOf { source.value * 2 }
        val second = derivedStateOf { first.value + 1 }
        val a = RuntimeObservation.observeReads({}) { second.value }.second
        val b = RuntimeObservation.observeReads({}) { second.value }.second
        a.dispose()
        assertEquals(1, (source as SnapshotStateObject).snapshotObservers().size)
        b.dispose()
        assertTrue(source.snapshotObservers().isEmpty())
        source.value = 2
        assertEquals(5, second.value)
        assertTrue(source.snapshotObservers().isEmpty())
    }

    @Test fun `throwing callbacks do not prevent siblings or reopen an applied snapshot`() {
        val state = mutableStateOf(0)
        val first = IllegalArgumentException("first")
        val second = IllegalStateException("second")
        val delivered = mutableListOf<Int>()
        val observers = listOf(
            RuntimeObservation.observeReads({ delivered += 1; throw first }) { state.value }.second,
            RuntimeObservation.observeReads({ delivered += 2 }) { state.value }.second,
            RuntimeObservation.observeReads({ delivered += 3; throw second }) { state.value }.second,
        )
        try {
            Snapshot.takeMutableSnapshot().use { snapshot ->
                snapshot.enter { state.value = 1 }
                assertSame(first, runCatching { snapshot.apply() }.exceptionOrNull())
                assertEquals(listOf(1, 2, 3), delivered)
                assertEquals(listOf(second), first.suppressed.toList())
                assertEquals(1, state.value)
                assertTrue(runCatching { snapshot.apply() }.exceptionOrNull() is IllegalStateException)
            }
        } finally { observers.forEach { it.dispose() } }
    }

    @Test fun `direct and derived diamond paths notify one consumer once per apply`() {
        val source = mutableStateOf(0)
        val left = derivedStateOf { source.value + 1 }
        val right = derivedStateOf { source.value + 2 }
        var notifications = 0
        val observer = RuntimeObservation.observeReads({ notifications++ }) {
            left.value + right.value + source.value
        }.second
        try {
            source.value = 1
            assertEquals(1, notifications)
        } finally { observer.dispose() }
    }

    @Test fun `derived callback failure cannot prevent a sibling consumer`() {
        val source = mutableStateOf(0)
        val derived = derivedStateOf { source.value }
        val error = IllegalStateException("derived")
        val a = RuntimeObservation.observeReads({ throw error }) { derived.value }.second
        var calls = 0
        val b = RuntimeObservation.observeReads({ calls++ }) { derived.value }.second
        try {
            assertSame(error, runCatching { source.value = 1 }.exceptionOrNull())
            assertEquals(1, calls)
        } finally { a.dispose(); b.dispose() }
    }

    @Test fun `failed recalculation retains dependencies and allows later invalidation`() {
        val source = mutableStateOf(0)
        var fail = false
        val derived = derivedStateOf { source.value.also { check(!fail) } }
        var notifications = 0
        val observer = RuntimeObservation.observeReads({ notifications++ }) { derived.value }.second
        try {
            fail = true
            source.value = 1
            assertNotNull(runCatching { derived.value }.exceptionOrNull())
            fail = false
            source.value = 2
            assertEquals(2, notifications)
            assertEquals(2, derived.value)
        } finally { observer.dispose() }
        assertTrue((source as SnapshotStateObject).snapshotObservers().isEmpty())
    }

    @Test fun `reentrant applies retain independent notification opportunities`() {
        val source = mutableStateOf(0)
        var calls = 0
        val observer = RuntimeObservation.observeReads({
            calls++
            if (source.value == 1) source.value = 2
        }) { source.value }.second
        try {
            source.value = 1
            assertEquals(2, source.value)
            assertEquals(2, calls)
        } finally { observer.dispose() }
    }
}
