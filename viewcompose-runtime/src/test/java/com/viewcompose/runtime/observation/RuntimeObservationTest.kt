package com.viewcompose.runtime.observation

/*
 * 测试职责：覆盖 runtime 中的 Runtime Observation 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Runtime Observation behavior in runtime and guards the contract against regressions.
 */

import com.viewcompose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
