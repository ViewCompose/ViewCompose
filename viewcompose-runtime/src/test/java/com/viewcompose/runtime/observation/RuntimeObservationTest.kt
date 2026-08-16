package com.viewcompose.runtime.observation

/*
 * Test responsibility: covers Runtime Observation behavior in runtime and guards the contract against regressions.
 */

import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.SnapshotApplyResult
import com.viewcompose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RuntimeObservationTest {
    @Test
    fun `replacement retains stable subscriptions without observer churn`() {
        val state = TrackingObservableState()
        var invalidations = 0
        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = { invalidations += 1 },
        ) {
            state.read()
        }

        val (_, replacement) = RuntimeObservation.prepareReplacement(observation) {
            state.read()
        }
        replacement.commit()
        state.invalidate()

        assertEquals(1, state.additions)
        assertEquals(0, state.removals)
        assertEquals(1, invalidations)
        observation.dispose()
        assertEquals(1, state.removals)
    }

    @Test
    fun `aborted replacement releases only candidate dependencies`() {
        val committed = TrackingObservableState()
        val candidate = TrackingObservableState()
        var invalidations = 0
        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = { invalidations += 1 },
        ) {
            committed.read()
        }

        val (_, replacement) = RuntimeObservation.prepareReplacement(observation) {
            candidate.read()
        }
        candidate.invalidate()
        replacement.abort()
        committed.invalidate()
        candidate.invalidate()

        assertEquals(2, invalidations)
        assertEquals(1, committed.additions)
        assertEquals(0, committed.removals)
        assertEquals(1, candidate.additions)
        assertEquals(1, candidate.removals)
        observation.dispose()
    }

    @Test
    fun `committed replacement switches dependencies after guarded read`() {
        val previous = TrackingObservableState()
        val next = TrackingObservableState()
        var invalidations = 0
        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = { invalidations += 1 },
        ) {
            previous.read()
        }

        val (_, replacement) = RuntimeObservation.prepareReplacement(observation) {
            next.read()
        }
        replacement.commit()
        previous.invalidate()
        next.invalidate()

        assertEquals(1, invalidations)
        assertEquals(1, previous.removals)
        assertEquals(1, next.additions)
        assertEquals(0, next.removals)
        observation.dispose()
    }

    @Test
    fun `one apply during replacement invalidates the observation only once`() {
        val committed = mutableStateOf(0)
        val candidate = mutableStateOf(0)
        var invalidations = 0
        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = { invalidations += 1 },
        ) {
            committed.value
        }

        val (_, replacement) = RuntimeObservation.prepareReplacement(observation) {
            candidate.value
        }
        Snapshot.withMutableSnapshot {
            committed.value = 1
            candidate.value = 1
        }
        assertEquals(1, invalidations)

        replacement.commit()
        committed.value = 2
        candidate.value = 2
        assertEquals(2, invalidations)
        observation.dispose()
    }

    @Test
    fun `failed replacement read restores the committed dependency owner`() {
        val committed = TrackingObservableState()
        val candidate = TrackingObservableState()
        val (_, observation) = RuntimeObservation.observeReads(onInvalidated = {}) {
            committed.read()
        }

        runCatching {
            RuntimeObservation.prepareReplacement(observation) {
                candidate.read()
                error("candidate failure")
            }
        }
        val (_, retry) = RuntimeObservation.prepareReplacement(observation) {
            committed.read()
        }
        retry.abort()

        assertEquals(1, candidate.additions)
        assertEquals(1, candidate.removals)
        assertEquals(0, committed.removals)
        observation.dispose()
    }

    @Test
    fun `one observation cannot prepare overlapping replacements`() {
        val state = mutableStateOf(0)
        val (_, observation) = RuntimeObservation.observeReads(onInvalidated = {}) {
            state.value
        }
        val (_, first) = RuntimeObservation.prepareReplacement(observation) {
            state.value
        }

        val failure = runCatching {
            RuntimeObservation.prepareReplacement(observation) {
                state.value
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)

        first.abort()
        val (_, retry) = RuntimeObservation.prepareReplacement(observation) {
            state.value
        }
        retry.abort()
        observation.dispose()
    }

    @Test
    fun `nested observeReads restores outer context`() {
        val state = mutableStateOf(0)
        var outerInvalidations = 0
        var innerInvalidations = 0

        val (_, outer) = RuntimeObservation.observeReads(
            onInvalidated = { outerInvalidations += 1 },
        ) {
            state.value
            val (_, inner) = RuntimeObservation.observeReads(
                onInvalidated = { innerInvalidations += 1 },
            ) {
                state.value
            }
            inner.dispose()
            state.value
        }

        state.value = 1

        assertEquals(1, outerInvalidations)
        assertEquals(0, innerInvalidations)
        outer.dispose()
    }

    @Test
    fun `repeated reads in one observation do not duplicate subscriptions`() {
        val state = mutableStateOf(0)
        var invalidations = 0

        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = { invalidations += 1 },
        ) {
            state.value
            state.value
            state.value
        }
        state.value = 1

        assertEquals(1, invalidations)
        observation.dispose()
    }

    @Test
    fun `dispose detaches observation from future invalidations`() {
        val state = mutableStateOf(0)
        var invalidations = 0

        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = { invalidations += 1 },
        ) {
            state.value
        }
        observation.dispose()
        state.value = 1

        assertEquals(0, invalidations)
    }

    @Test
    fun `one global apply invalidates one observation at most once on the applying thread`() {
        val first = mutableStateOf(0)
        val second = mutableStateOf(0)
        val callbackThreads = mutableListOf<Thread>()
        var invalidations = 0
        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = {
                invalidations += 1
                callbackThreads += Thread.currentThread()
            },
        ) {
            first.value to second.value
        }
        val applyingThread = Thread.currentThread()

        Snapshot.withMutableSnapshot {
            first.value = 1
            second.value = 1
        }
        assertEquals(1, invalidations)
        assertSame(applyingThread, callbackThreads.single())

        first.value = 2
        second.value = 2
        assertEquals(3, invalidations)
        observation.dispose()
    }

    @Test
    fun `no-op and failed applies do not add invalidations`() {
        val first = mutableStateOf(0)
        val second = mutableStateOf(0)
        var invalidations = 0
        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = { invalidations += 1 },
        ) {
            first.value to second.value
        }

        Snapshot.withMutableSnapshot {
            first.value = 0
            second.value = 0
        }
        assertEquals(0, invalidations)

        val conflicting = Snapshot.takeMutableSnapshot()
        try {
            conflicting.enter { first.value = 1 }
            first.value = 2
            assertEquals(1, invalidations)
            assertTrue(conflicting.apply() is SnapshotApplyResult.Failure)
            assertEquals(1, invalidations)
        } finally {
            conflicting.dispose()
            observation.dispose()
        }
    }

    @Test
    fun `dispose racing an active callback permits only the callback already begun`() {
        val state = mutableStateOf(0)
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        var invalidations = 0
        val (_, observation) = RuntimeObservation.observeReads(
            onInvalidated = {
                invalidations += 1
                callbackStarted.countDown()
                releaseCallback.await(10, TimeUnit.SECONDS)
            },
        ) {
            state.value
        }
        val writer = Executors.newSingleThreadExecutor()

        try {
            val write = writer.submit { state.value = 1 }
            assertTrue(callbackStarted.await(10, TimeUnit.SECONDS))
            observation.dispose()
            releaseCallback.countDown()
            write.get(10, TimeUnit.SECONDS)
            state.value = 2
            assertEquals(1, invalidations)
        } finally {
            releaseCallback.countDown()
            observation.dispose()
            writer.shutdownNow()
        }
    }

    @Test
    fun `observer churn is safe while state writes snapshot subscriptions`() {
        val state = mutableStateOf(0)
        val persistent = List(128) {
            RuntimeObservation.observeReads(onInvalidated = {}) {
                state.value
            }.second
        }
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()

        try {
            val writer = executor.submit {
                start.await()
                runCatching {
                    repeat(5_000) { value ->
                        state.value = value + 1
                    }
                }.exceptionOrNull()?.let { error ->
                    failure.compareAndSet(null, error)
                }
            }
            val churn = executor.submit {
                start.await()
                runCatching {
                    repeat(10_000) {
                        val observation = RuntimeObservation.observeReads(onInvalidated = {}) {
                            state.value
                        }.second
                        observation.dispose()
                    }
                }.exceptionOrNull()?.let { error ->
                    failure.compareAndSet(null, error)
                }
            }

            start.countDown()
            writer.get(20, TimeUnit.SECONDS)
            churn.get(20, TimeUnit.SECONDS)
            assertNull(failure.get())
        } finally {
            persistent.forEach(Observation::dispose)
            executor.shutdownNow()
        }
    }

    private class TrackingObservableState : ObservableState {
        private val observers = LinkedHashSet<Observation>()
        var additions: Int = 0
            private set
        var removals: Int = 0
            private set

        fun read(): Int {
            RuntimeObservation.recordRead(this)
            return 0
        }

        fun invalidate() {
            observers.toList().forEach(Observation::invalidate)
        }

        override fun addObserver(observer: Observation) {
            additions += 1
            observers += observer
        }

        override fun removeObserver(observer: Observation) {
            removals += 1
            observers -= observer
        }
    }
}
