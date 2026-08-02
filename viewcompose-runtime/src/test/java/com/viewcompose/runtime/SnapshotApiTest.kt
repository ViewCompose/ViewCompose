package com.viewcompose.runtime

/*
 * Test responsibility: covers Snapshot Api behavior in runtime and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotApiTest {
    @Test
    fun `withMutableSnapshot applies writes on success`() {
        val state = mutableStateOf(0)

        Snapshot.withMutableSnapshot {
            state.value = 9
        }

        assertEquals(9, state.value)
    }

    @Test
    fun `withMutableSnapshot throws conflict when concurrent write wins`() {
        val state = mutableStateOf(0)
        val concurrent = Snapshot.takeMutableSnapshot()
        concurrent.enter {
            state.value = 1
        }

        val error = runCatching {
            Snapshot.withMutableSnapshot {
                state.value = 2
                assertTrue(concurrent.apply() is SnapshotApplyResult.Success)
            }
        }.exceptionOrNull()

        assertTrue(error is SnapshotApplyConflictException)
        assertEquals(1, state.value)
        concurrent.dispose()
    }

    @Test
    fun `mutable snapshot cannot apply twice`() {
        val state = mutableStateOf(0)
        val snapshot = Snapshot.takeMutableSnapshot()
        snapshot.enter {
            state.value = 5
        }
        assertTrue(snapshot.apply() is SnapshotApplyResult.Success)

        val error = runCatching {
            snapshot.apply()
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        snapshot.dispose()
    }

    @Test
    fun `disposed snapshot cannot enter`() {
        val snapshot = Snapshot.takeSnapshot()
        snapshot.dispose()

        val error = runCatching {
            snapshot.enter { 1 }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }
}
