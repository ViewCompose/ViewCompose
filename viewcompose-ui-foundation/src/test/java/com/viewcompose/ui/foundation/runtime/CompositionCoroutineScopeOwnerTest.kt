package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core runtime 中的 Composition Coroutine Scope Owner 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Composition Coroutine Scope Owner behavior in widget-core runtime and guards DSL, state, or theme contracts against regressions.
 */

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionCoroutineScopeOwnerTest {
    @Test
    fun `parent cancellation cancels composition descendants`() = runBlocking {
        val parent = Job()
        val owner = CompositionCoroutineScopeOwner(
            parentContext = coroutineContext + parent,
            onError = {},
        )
        val started = CompletableDeferred<Unit>()
        val child = launch(
            context = owner.coroutineContext,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
        }

        started.await()
        assertTrue(child.isActive)
        parent.cancel()
        child.join()

        assertFalse(child.isActive)
    }

    @Test
    fun `child failure is isolated and reported`() = runBlocking {
        val errors = mutableListOf<Throwable>()
        val owner = CompositionCoroutineScopeOwner(
            parentContext = coroutineContext,
            onError = errors::add,
        )
        val siblingGate = CompletableDeferred<Unit>()
        val sibling = launch(owner.coroutineContext) {
            siblingGate.await()
        }
        val failure = launch(owner.coroutineContext) {
            error("boom")
        }

        failure.join()

        assertTrue(sibling.isActive)
        assertEquals("boom", errors.single().message)
        sibling.cancelAndJoin()
        owner.cancel()
    }

    @Test
    fun `owner cancellation cancels all children`() = runBlocking {
        val owner = CompositionCoroutineScopeOwner(
            parentContext = coroutineContext,
            onError = {},
        )
        val first = launch(owner.coroutineContext) {
            CompletableDeferred<Unit>().await()
        }
        val second = launch(owner.coroutineContext) {
            CompletableDeferred<Unit>().await()
        }

        owner.cancel()
        first.join()
        second.join()

        assertFalse(first.isActive)
        assertFalse(second.isActive)
    }
}
