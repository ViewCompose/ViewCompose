package com.viewcompose.runtime.composition

/*
 * 测试职责：覆盖 runtime 中的 Invalidation Queue 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Invalidation Queue behavior in runtime and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvalidationQueueTest {
    @Test
    fun `drain compacts descendant scopes into ancestor`() {
        val queue = InvalidationQueue()
        val root = RecomposeScope(signature = "root", parent = null)
        val parent = childOf(root, "parent")
        val child = childOf(parent, "child")

        queue.enqueue(child)
        queue.enqueue(parent)
        val compacted = queue.drainCompacted()

        assertEquals(1, compacted.size)
        assertEquals(parent, compacted.first())
    }

    @Test
    fun `enqueue ignores disposed scopes`() {
        val queue = InvalidationQueue()
        val disposed = RecomposeScope(signature = "disposed", parent = null).apply {
            disposeRecursively()
        }

        queue.enqueue(disposed)

        assertTrue(queue.drainCompacted().isEmpty())
    }

    @Test
    fun `drain removes duplicated descendant paths`() {
        val queue = InvalidationQueue()
        val root = RecomposeScope(signature = "root", parent = null)
        val parent = childOf(root, "parent")
        val firstChild = childOf(parent, "child-1")
        val secondChild = childOf(parent, "child-2")

        queue.enqueue(firstChild)
        queue.enqueue(secondChild)
        queue.enqueue(parent)
        val compacted = queue.drainCompacted()

        assertEquals(1, compacted.size)
        assertEquals(parent, compacted.first())
    }

    private fun childOf(
        parent: RecomposeScope,
        signature: String,
    ): RecomposeScope {
        val child = RecomposeScope(signature = signature, parent = parent)
        parent.children += child
        return child
    }
}
