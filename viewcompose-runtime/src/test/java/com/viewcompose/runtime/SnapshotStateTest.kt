package com.viewcompose.runtime

import com.viewcompose.runtime.state.MutableStateImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotStateTest {
    @Test
    fun `read snapshot sees pre-commit value after global update`() {
        val state = mutableStateOf(0)
        val snapshot = Snapshot.takeSnapshot()
        state.value = 1

        val snapValue = snapshot.enter { state.value }

        assertEquals(0, snapValue)
        assertEquals(1, state.value)
        snapshot.dispose()
    }

    @Test
    fun `concurrent mutable snapshots fail when merge is unavailable`() {
        val state = mutableStateOf(0)
        val first = Snapshot.takeMutableSnapshot()
        val second = Snapshot.takeMutableSnapshot()
        first.enter { state.value = 1 }
        second.enter { state.value = 2 }

        assertTrue(first.apply() is SnapshotApplyResult.Success)
        val secondResult = second.apply()
        assertTrue(secondResult is SnapshotApplyResult.Failure)
        assertEquals(1, state.value)

        first.dispose()
        second.dispose()
    }

    @Test
    fun `concurrent mutable snapshots can merge with custom policy`() {
        val mergePolicy = object : SnapshotMutationPolicy<Int> {
            override fun equivalent(
                a: Int,
                b: Int,
            ): Boolean = a == b

            override fun merge(
                previous: Int,
                current: Int,
                applied: Int,
            ): Int = current + (applied - previous)
        }
        val state = mutableStateOf(0, mergePolicy)
        val first = Snapshot.takeMutableSnapshot()
        val second = Snapshot.takeMutableSnapshot()
        first.enter { state.value = 1 }
        second.enter { state.value = 2 }

        assertTrue(first.apply() is SnapshotApplyResult.Success)
        assertTrue(second.apply() is SnapshotApplyResult.Success)
        assertEquals(3, state.value)

        first.dispose()
        second.dispose()
    }

    @Test
    fun `autocommit write updates state without explicit snapshot`() {
        val before = Snapshot.currentGlobalId()
        val state = mutableStateOf(0)

        state.value = 42

        assertEquals(42, state.value)
        assertTrue(Snapshot.currentGlobalId() > before)
    }

    @Test
    fun `nested mutable snapshot apply merges into parent then global`() {
        val state = mutableStateOf(0)
        val parent = Snapshot.takeMutableSnapshot()
        parent.enter {
            val child = Snapshot.takeMutableSnapshot()
            child.enter {
                state.value = 7
            }
            assertTrue(child.apply() is SnapshotApplyResult.Success)
            child.dispose()
            assertEquals(7, state.value)
        }
        assertTrue(parent.apply() is SnapshotApplyResult.Success)
        assertEquals(7, state.value)
        parent.dispose()
    }

    @Test
    fun `nullable value remains visible inside mutable snapshot and applies globally`() {
        val state = mutableStateOf<String?>("ready")
        val snapshot = Snapshot.takeMutableSnapshot()

        snapshot.enter {
            state.value = null
            assertNull(state.value)
        }

        assertEquals("ready", state.value)
        assertTrue(snapshot.apply() is SnapshotApplyResult.Success)
        assertNull(state.value)
        snapshot.dispose()
    }

    @Test
    fun `nullable value applies through nested mutable snapshots`() {
        val state = mutableStateOf<String?>("ready")
        val parent = Snapshot.takeMutableSnapshot()

        parent.enter {
            val child = Snapshot.takeMutableSnapshot()
            child.enter {
                state.value = null
                assertNull(state.value)
            }
            assertTrue(child.apply() is SnapshotApplyResult.Success)
            child.dispose()
            assertNull(state.value)
        }

        assertTrue(parent.apply() is SnapshotApplyResult.Success)
        assertNull(state.value)
        parent.dispose()
    }

    @Test
    fun `never equal policy supports uncontended autocommit writes`() {
        val state = mutableStateOf(1, neverEqualPolicy())
        val before = Snapshot.currentGlobalId()

        state.value = 1

        assertEquals(1, state.value)
        assertTrue(Snapshot.currentGlobalId() > before)
    }

    @Test
    fun `autocommit writes prune obsolete state records`() {
        val state = mutableStateOf(0)

        repeat(1_000) { value ->
            state.value = value + 1
        }

        assertEquals(1, (state as MutableStateImpl<Int>).recordCount())
    }

    @Test
    fun `active read snapshot retains visible record until disposed`() {
        val state = mutableStateOf(0)
        val readSnapshot = Snapshot.takeSnapshot()

        repeat(100) { value ->
            state.value = value + 1
        }

        val implementation = state as MutableStateImpl<Int>
        assertTrue(implementation.recordCount() > 1)
        assertEquals(0, readSnapshot.enter { state.value })

        readSnapshot.dispose()

        assertEquals(1, implementation.recordCount())
        assertEquals(100, state.value)
    }
}
