package com.viewcompose.runtime

import com.viewcompose.runtime.observation.ObservableState
import com.viewcompose.runtime.observation.Observation
import com.viewcompose.runtime.observation.RuntimeObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotFlowTest {
    @Test
    fun `emits initial and structurally distinct values`() = runBlocking {
        val source = mutableStateOf(0)
        val values = async(Dispatchers.Unconfined) {
            snapshotFlow { source.value }
                .take(3)
                .toList()
        }

        source.value = 1
        source.value = 1
        source.value = 2

        assertEquals(listOf(0, 1, 2), values.await())
    }

    @Test
    fun `conflates invalidations and suppresses equal calculated results`() = runBlocking {
        val source = mutableStateOf(0)
        var calculations = 0
        val values = async(Dispatchers.Unconfined) {
            snapshotFlow {
                calculations += 1
                source.value % 2
            }
                .take(2)
                .toList()
        }

        source.value = 2
        source.value = 4
        source.value = 5

        assertEquals(listOf(0, 1), values.await())
        assertTrue(calculations >= 2)
    }

    @Test
    fun `multi-state transaction produces one recalculation opportunity`() = runBlocking {
        val first = mutableStateOf(0)
        val second = mutableStateOf(0)
        var calculations = 0
        val values = async(Dispatchers.Unconfined) {
            snapshotFlow {
                calculations += 1
                first.value to second.value
            }
                .take(2)
                .toList()
        }

        Snapshot.withMutableSnapshot {
            first.value = 1
            second.value = 2
        }

        assertEquals(listOf(0 to 0, 1 to 2), values.await())
        assertEquals(2, calculations)
    }

    @Test
    fun `replaces conditional dependencies after each calculation`() = runBlocking {
        val selectFirst = mutableStateOf(true)
        val first = mutableStateOf("first")
        val second = mutableStateOf("second")
        val values = async(Dispatchers.Unconfined) {
            snapshotFlow {
                if (selectFirst.value) first.value else second.value
            }
                .take(4)
                .toList()
        }

        first.value = "first-updated"
        selectFirst.value = false
        first.value = "ignored"
        second.value = "second-updated"

        assertEquals(
            listOf("first", "first-updated", "second", "second-updated"),
            values.await(),
        )
    }

    @Test
    fun `completion disposes dependency observation`() = runBlocking {
        val source = TrackingState(0)
        var calculations = 0

        val values = snapshotFlow {
            calculations += 1
            source.value
        }.take(1).toList()

        assertEquals(listOf(0), values)
        assertEquals(1, calculations)
        assertEquals(0, source.observerCount)
    }

    @Test
    fun `calculation failure terminates collector`() = runBlocking {
        val source = TrackingState(0)
        val error = runCatching {
            snapshotFlow<Int> {
                source.value
                error("calculation failed")
            }.take(1).toList()
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("calculation failed", error?.message)
        assertEquals(0, source.observerCount)
    }

    @Test
    fun `each collector owns an independent observation`() = runBlocking {
        val source = TrackingState(0)
        val flow = snapshotFlow { source.value }
        val first = async(Dispatchers.Unconfined) {
            flow.take(2).toList()
        }
        val second = async(Dispatchers.Unconfined) {
            flow.take(2).toList()
        }

        assertEquals(2, source.observerCount)
        source.value = 1

        assertEquals(listOf(0, 1), first.await())
        assertEquals(listOf(0, 1), second.await())
        assertEquals(0, source.observerCount)
    }

    private class TrackingState<T>(initialValue: T) : State<T>, ObservableState {
        private val observers = LinkedHashSet<Observation>()
        private var currentValue: T = initialValue

        override var value: T
            get() {
                RuntimeObservation.recordRead(this)
                return currentValue
            }
            set(value) {
                if (currentValue == value) return
                currentValue = value
                observers.toList().forEach(Observation::invalidate)
            }

        val observerCount: Int
            get() = observers.size

        override fun addObserver(observer: Observation) {
            observers += observer
        }

        override fun removeObserver(observer: Observation) {
            observers -= observer
        }
    }
}
