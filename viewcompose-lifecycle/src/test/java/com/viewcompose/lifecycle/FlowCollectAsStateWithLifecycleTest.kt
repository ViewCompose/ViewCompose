package com.viewcompose.lifecycle

/*
 * 测试职责：覆盖 lifecycle integration 中的 Flow Collect As State With Lifecycle 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Flow Collect As State With Lifecycle behavior in lifecycle integration and guards the contract against regressions.
 */

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.runtime.State
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowCollectAsStateWithLifecycleTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `collectAsStateWithLifecycle starts and stops with lifecycle state`() = runBlocking {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        val source = MutableStateFlow(10)

        owner.handle(Lifecycle.Event.ON_CREATE)
        val state = harness.render {
            source.collectAsStateWithLifecycle(
                initial = -1,
                lifecycle = owner.lifecycle,
                context = Dispatchers.Unconfined,
            )
        }
        assertEquals(-1, state.value)

        owner.handle(Lifecycle.Event.ON_START)
        awaitValue(state) { it == 10 }

        source.value = 11
        awaitValue(state) { it == 11 }

        owner.handle(Lifecycle.Event.ON_STOP)
        source.value = 12
        delay(50)
        assertEquals(11, state.value)

        owner.handle(Lifecycle.Event.ON_START)
        awaitValue(state) { it == 12 }
        harness.dispose()
    }

    @Test
    fun `collectAsStateWithLifecycle resolves lifecycle owner from local`() = runBlocking {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        val source = MutableStateFlow(1)
        lateinit var state: State<Int>

        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_START)
        harness.renderTree {
            ProvideLifecycleOwner(owner) {
                state = source.collectAsStateWithLifecycle(
                    initial = 0,
                    context = Dispatchers.Unconfined,
                )
            }
        }
        awaitValue(state) { it == 1 }

        source.value = 2
        awaitValue(state) { it == 2 }
        harness.dispose()
    }

    @Test
    fun `collectAsStateWithLifecycle throws when lifecycle owner is missing`() {
        val source = MutableStateFlow(1)
        val error = runCatching {
            source.collectAsStateWithLifecycle(initial = 0)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("ProvideLifecycleOwner"))
    }

    @Test
    fun `collectAsStateWithLifecycle cancels collector on dispose`() = runBlocking {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        var canceled = 0
        val source = flow {
            emit(1)
            try {
                awaitCancellation()
            } finally {
                canceled += 1
            }
        }

        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_START)
        harness.render {
            source.collectAsStateWithLifecycle(
                initial = 0,
                lifecycle = owner.lifecycle,
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
    fun `collectAsStateWithLifecycle rejects non-active lifecycle thresholds`() {
        val source = MutableStateFlow(1)
        val owner = TestLifecycleOwner()

        listOf(
            Lifecycle.State.INITIALIZED,
            Lifecycle.State.DESTROYED,
        ).forEach { invalidState ->
            val error = runCatching {
                source.collectAsStateWithLifecycle(
                    lifecycleOwner = owner,
                    minActiveState = invalidState,
                )
            }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
        }
    }

    @Test
    fun `rapid lifecycle restart serializes collectors through cancellation cleanup`() = runBlocking {
        val harness = WidgetCoreRuntimeHarness()
        val owner = TestLifecycleOwner()
        var activeCollectors = 0
        var maxActiveCollectors = 0
        var starts = 0
        val source = flow {
            activeCollectors += 1
            starts += 1
            maxActiveCollectors = maxOf(maxActiveCollectors, activeCollectors)
            try {
                emit(starts)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    delay(25)
                    activeCollectors -= 1
                }
            }
        }

        owner.handle(Lifecycle.Event.ON_CREATE)
        owner.handle(Lifecycle.Event.ON_START)
        val state = harness.render {
            source.collectAsStateWithLifecycle(
                initial = 0,
                lifecycle = owner.lifecycle,
                context = Dispatchers.Unconfined,
            )
        }
        awaitValue(state) { it == 1 }

        owner.handle(Lifecycle.Event.ON_STOP)
        owner.handle(Lifecycle.Event.ON_START)
        awaitValue(state) { it == 2 }

        assertEquals(1, maxActiveCollectors)
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

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle
            get() = registry

        fun handle(
            event: Lifecycle.Event,
        ) {
            registry.handleLifecycleEvent(event)
        }
    }
}
