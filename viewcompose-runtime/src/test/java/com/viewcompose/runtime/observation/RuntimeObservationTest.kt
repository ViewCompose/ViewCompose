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
}
