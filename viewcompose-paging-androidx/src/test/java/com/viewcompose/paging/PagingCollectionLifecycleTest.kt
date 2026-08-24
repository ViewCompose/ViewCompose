package com.viewcompose.paging

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PagingCollectionLifecycleTest {
    @Test
    fun `cached visible flow survives hide reveal and composition recreation without duplicate collection`() =
        pagingTest {
            val factory = ControlledSourceFactory()
            val applicationScope = CoroutineScope(
                SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
            )
            val cachedPages = factory.pager().flow.cachedIn(applicationScope)
            val starts = AtomicInteger()
            val active = AtomicInteger()
            val maximumActive = AtomicInteger()
            val cancellations = AtomicInteger()
            val pages: Flow<PagingData<Int>> = flow {
                starts.incrementAndGet()
                val activeCollectors = active.incrementAndGet()
                maximumActive.updateAndGet { previous -> maxOf(previous, activeCollectors) }
                try {
                    emitAll(cachedPages)
                } finally {
                    active.decrementAndGet()
                    cancellations.incrementAndGet()
                }
            }
            val owner = TestLifecycleOwner().also { lifecycleOwner ->
                lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
                lifecycleOwner.handle(Lifecycle.Event.ON_START)
            }
            val firstHarness = PagingCompositionHarness()
            lateinit var firstItems: ViewComposePagingItems<Int>
            firstHarness.render {
                ProvideLifecycleOwner(owner) {
                    firstItems = pages.collectAsViewComposePagingItems(
                        context = Dispatchers.Unconfined,
                    )
                }
            }
            runCurrent()
            val source = factory.nextSource()
            source.nextRequest().completePage(listOf(4, 5), prevKey = null, nextKey = null)
            runCurrent()
            assertEquals(listOf(4, 5), firstItems.values())
            assertEquals(1, starts.get())
            assertEquals(1, active.get())

            owner.handle(Lifecycle.Event.ON_STOP)
            runCurrent()
            assertEquals(listOf(4, 5), firstItems.values())
            assertEquals(1, cancellations.get())
            assertEquals(0, active.get())

            owner.handle(Lifecycle.Event.ON_START)
            runCurrent()
            assertEquals(listOf(4, 5), firstItems.values())
            assertEquals(2, starts.get())
            assertEquals(1, factory.createdSources.size)

            firstHarness.dispose()
            runCurrent()
            assertEquals(2, cancellations.get())
            val secondHarness = PagingCompositionHarness()
            lateinit var recreatedItems: ViewComposePagingItems<Int>
            secondHarness.render {
                ProvideLifecycleOwner(owner) {
                    recreatedItems = pages.collectAsViewComposePagingItems(
                        context = Dispatchers.Unconfined,
                    )
                }
            }
            runCurrent()

            assertNotSame(firstItems, recreatedItems)
            assertEquals(listOf(4, 5), recreatedItems.values())
            assertEquals(3, starts.get())
            assertEquals(1, factory.createdSources.size)
            assertEquals(1, maximumActive.get())

            secondHarness.dispose()
            applicationScope.cancel()
            runCurrent()
            assertEquals(3, cancellations.get())
            assertEquals(0, active.get())
        }

    @Test
    fun `retained policy remains active while stopped and cancels at destroy`() = pagingTest {
        val starts = AtomicInteger()
        val cancellations = AtomicInteger()
        val pages = flow<PagingData<Int>> {
            starts.incrementAndGet()
            try {
                emit(PagingData.empty())
                awaitCancellation()
            } finally {
                cancellations.incrementAndGet()
            }
        }
        val owner = TestLifecycleOwner().also { it.handle(Lifecycle.Event.ON_CREATE) }
        val harness = PagingCompositionHarness()
        harness.render {
            ProvideLifecycleOwner(owner) {
                pages.collectAsViewComposePagingItems(
                    lifecyclePolicy = PagingLifecyclePolicy.Retained,
                    context = Dispatchers.Unconfined,
                )
            }
        }
        runCurrent()
        assertEquals(1, starts.get())

        owner.handle(Lifecycle.Event.ON_START)
        owner.handle(Lifecycle.Event.ON_STOP)
        runCurrent()
        assertEquals(1, starts.get())
        assertEquals(0, cancellations.get())

        owner.handle(Lifecycle.Event.ON_DESTROY)
        runCurrent()
        assertEquals(1, cancellations.get())
        harness.dispose()
        runCurrent()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun `composition policy ignores a destroyed lifecycle and ends with composition`() = pagingTest {
        val starts = AtomicInteger()
        val cancellations = AtomicInteger()
        val pages = flow<PagingData<Int>> {
            starts.incrementAndGet()
            try {
                emit(PagingData.empty())
                awaitCancellation()
            } finally {
                cancellations.incrementAndGet()
            }
        }
        val owner = TestLifecycleOwner().also { lifecycleOwner ->
            lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handle(Lifecycle.Event.ON_DESTROY)
        }
        val harness = PagingCompositionHarness()
        harness.render {
            ProvideLifecycleOwner(owner) {
                pages.collectAsViewComposePagingItems(
                    lifecyclePolicy = PagingLifecyclePolicy.Composition,
                    context = Dispatchers.Unconfined,
                )
            }
        }
        runCurrent()

        assertEquals(1, starts.get())
        assertEquals(0, cancellations.get())
        harness.dispose()
        runCurrent()
        assertEquals(1, cancellations.get())
    }

    @Test
    fun `visible policy retains presentation while stopped and replaces it after restart`() =
        pagingTest {
            val harness = PagingCompositionHarness()
            val owner = TestLifecycleOwner()
            val factory = ControlledSourceFactory()
            val pages: Flow<PagingData<Int>> = flow {
                emitAll(factory.pager().flow)
            }
            owner.handle(Lifecycle.Event.ON_CREATE)

            lateinit var items: ViewComposePagingItems<Int>
            harness.render {
                ProvideLifecycleOwner(owner) {
                    items = pages.collectAsViewComposePagingItems(
                        context = Dispatchers.Unconfined,
                    )
                }
            }
            runCurrent()
            assertEquals(0, factory.createdSources.size)

            owner.handle(Lifecycle.Event.ON_START)
            runCurrent()
            val firstSource = factory.nextSource()
            runCurrent()
            firstSource.nextRequest().completePage(listOf(0, 1), prevKey = null, nextKey = null)
            runCurrent()
            assertEquals(listOf(0, 1), items.values())

            owner.handle(Lifecycle.Event.ON_STOP)
            runCurrent()
            firstSource.invalidate()
            runCurrent()
            assertEquals(listOf(0, 1), items.values())
            assertEquals(1, factory.createdSources.size)

            owner.handle(Lifecycle.Event.ON_START)
            runCurrent()
            val restartedSource = factory.nextSource()
            runCurrent()
            restartedSource.nextRequest().completePage(
                listOf(10, 11),
                prevKey = null,
                nextKey = null,
            )
            runCurrent()

            assertEquals(listOf(10, 11), items.values())
            harness.dispose()
        }

    @Test
    fun `flow identity replaces the owner while context changes retain it`() = pagingTest {
        val harness = PagingCompositionHarness()
        val firstFlow: Flow<PagingData<Int>> = flow { awaitCancellation() }
        val secondFlow: Flow<PagingData<Int>> = flow { awaitCancellation() }

        val first = harness.render {
            firstFlow.collectAsViewComposePagingItems(
                lifecyclePolicy = PagingLifecyclePolicy.Composition,
                context = Dispatchers.Unconfined,
            )
        }
        val contextRestart = harness.render {
            firstFlow.collectAsViewComposePagingItems(
                lifecyclePolicy = PagingLifecyclePolicy.Composition,
                context = kotlin.coroutines.EmptyCoroutineContext,
            )
        }
        val replacement = harness.render {
            secondFlow.collectAsViewComposePagingItems(
                lifecyclePolicy = PagingLifecyclePolicy.Composition,
            )
        }

        assertSame(first, contextRestart)
        assertNotSame(first, replacement)
        assertTrue(runCatching { first.retry() }.exceptionOrNull() is IllegalStateException)
        harness.dispose()
    }

    @Test
    fun `composition release cancels collection exactly once and closes commands`() = pagingTest {
        val harness = PagingCompositionHarness()
        val cancellations = AtomicInteger()
        val pages = flow<PagingData<Int>> {
            try {
                emit(PagingData.empty())
                awaitCancellation()
            } finally {
                cancellations.incrementAndGet()
            }
        }
        val items = harness.render {
            pages.collectAsViewComposePagingItems(
                lifecyclePolicy = PagingLifecyclePolicy.Composition,
                context = Dispatchers.Unconfined,
            )
        }
        runCurrent()

        harness.dispose()
        withTimeout(5_000) {
            while (cancellations.get() != 1) {
                delay(1)
            }
        }

        assertEquals(1, cancellations.get())
        assertTrue(runCatching { items.refresh() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `collector rejects detached jobs and missing visible lifecycle owner`() = pagingTest {
        val harness = PagingCompositionHarness()
        val pages: Flow<PagingData<Int>> = emptyFlow()

        val jobError = runCatching {
            harness.render {
                pages.collectAsViewComposePagingItems(
                    lifecyclePolicy = PagingLifecyclePolicy.Composition,
                    context = kotlinx.coroutines.Job(),
                )
            }
        }.exceptionOrNull()
        val ownerError = runCatching {
            harness.render {
                pages.collectAsViewComposePagingItems()
            }
        }.exceptionOrNull()

        assertTrue(jobError is IllegalArgumentException)
        assertTrue(ownerError is IllegalArgumentException)
        assertTrue(ownerError?.message.orEmpty().contains("ProvideLifecycleOwner"))
        harness.dispose()
    }

    private fun ViewComposePagingItems<Int>.values(): List<Int?> =
        (0 until itemCount).map(::peek)

    private fun pagingTest(
        block: suspend kotlinx.coroutines.test.TestScope.() -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)

        override val lifecycle: Lifecycle
            get() = registry

        fun handle(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }
}
