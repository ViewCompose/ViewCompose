package com.viewcompose.lifecycle

/*
 * 测试职责：覆盖 lifecycle integration 中的 Flow Collect As State 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Flow Collect As State behavior in lifecycle integration and guards the contract against regressions.
 */

import com.viewcompose.runtime.State
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowCollectAsStateTest {
    @Test
    fun `stateFlow collectAsState uses current value and updates`() = runBlocking {
        val harness = WidgetCoreRuntimeHarness()
        val source = MutableStateFlow(1)

        val state = harness.render {
            source.collectAsState(context = Dispatchers.Unconfined)
        }
        assertEquals(1, state.value)

        source.value = 2
        awaitValue(state) { it == 2 }
        harness.dispose()
    }

    @Test
    fun `collectAsState keeps collector when flow identity is stable`() = runBlocking {
        val harness = WidgetCoreRuntimeHarness()
        var starts = 0
        val source = flow {
            starts += 1
            emit(5)
            awaitCancellation()
        }

        val first = harness.render {
            source.collectAsState(
                initial = 0,
                context = Dispatchers.Unconfined,
            )
        }
        val second = harness.render {
            source.collectAsState(
                initial = 99,
                context = Dispatchers.Unconfined,
            )
        }

        assertSame(first, second)
        assertEquals(1, starts)
        harness.dispose()
    }

    @Test
    fun `collectAsState restarts collector when flow identity changes`() = runBlocking {
        val harness = WidgetCoreRuntimeHarness()
        var firstStarts = 0
        var secondStarts = 0
        val firstFlow = flow {
            firstStarts += 1
            emit(1)
            awaitCancellation()
        }
        val secondFlow = flow {
            secondStarts += 1
            emit(2)
            awaitCancellation()
        }

        harness.render {
            firstFlow.collectAsState(
                initial = 0,
                context = Dispatchers.Unconfined,
            )
        }
        val state = harness.render {
            secondFlow.collectAsState(
                initial = 0,
                context = Dispatchers.Unconfined,
            )
        }

        assertEquals(1, firstStarts)
        assertEquals(1, secondStarts)
        awaitValue(state) { it == 2 }
        harness.dispose()
    }

    @Test
    fun `collectAsState cancels collector on dispose`() = runBlocking {
        val harness = WidgetCoreRuntimeHarness()
        var canceled = 0
        val source = flow {
            emit(1)
            try {
                awaitCancellation()
            } finally {
                canceled += 1
            }
        }

        harness.render {
            source.collectAsState(
                initial = 0,
                context = Dispatchers.Unconfined,
            )
        }
        harness.dispose()

        withTimeout(1.seconds) {
            while (canceled == 0) {
                delay(10)
            }
        }
        assertEquals(1, canceled)
    }

    @Test
    fun `collectAsState rejects a detached job context`() {
        val harness = WidgetCoreRuntimeHarness()
        val source = MutableStateFlow(1)

        val invocationError = runCatching {
            harness.render {
                source.collectAsState(context = Job())
            }
        }.exceptionOrNull()
        val error = invocationError?.cause ?: invocationError

        assertTrue(error is IllegalArgumentException)
        harness.dispose()
    }

    private suspend fun <T> awaitValue(
        state: State<T>,
        predicate: (T) -> Boolean,
    ) {
        withTimeout(1.seconds) {
            while (!predicate(state.value)) {
                delay(10)
            }
        }
    }
}
