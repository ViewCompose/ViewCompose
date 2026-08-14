package com.viewcompose.renderer.view.lazy.reuse

import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.ReusableItemPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class MountedTreeReuseCacheTest {
    private val typeA = MountedTreeReuseCache.ReuseKey(LazyListItemKind.Item, "a")
    private val typeB = MountedTreeReuseCache.ReuseKey(LazyListItemKind.Item, "b")

    @Test
    fun `matching type transfers ownership without releasing presentation`() {
        val cache = MountedTreeReuseCache(capacity = 2)
        val presentation = RecordingPresentation()
        cache.offer(typeA, presentation)

        assertSame(presentation, cache.take(typeA))
        assertEquals(0, presentation.releaseCount)
        assertNull(cache.take(typeA))
    }

    @Test
    fun `oldest presentation is released when bounded cache overflows`() {
        val cache = MountedTreeReuseCache(capacity = 1)
        val oldest = RecordingPresentation()
        val newest = RecordingPresentation()

        cache.offer(typeA, oldest)
        cache.offer(typeB, newest)

        assertEquals(1, oldest.releaseCount)
        assertEquals(0, newest.releaseCount)
        assertSame(newest, cache.take(typeB))
    }

    @Test
    fun `capacity reduction and clear release every remaining presentation exactly once`() {
        val cache = MountedTreeReuseCache(capacity = 3)
        val first = RecordingPresentation()
        val second = RecordingPresentation()
        val third = RecordingPresentation()
        cache.offer(typeA, first)
        cache.offer(typeA, second)
        cache.offer(typeB, third)

        cache.capacity = 1
        cache.clear()
        cache.clear()

        assertEquals(1, first.releaseCount)
        assertEquals(1, second.releaseCount)
        assertEquals(1, third.releaseCount)
    }

    @Test
    fun `clear attempts every release and aggregates failures without retaining entries`() {
        val cache = MountedTreeReuseCache(capacity = 3)
        val first = RecordingPresentation(failureMessage = "first failed")
        val second = RecordingPresentation(failureMessage = "second failed")
        val third = RecordingPresentation()
        cache.offer(typeA, first)
        cache.offer(typeA, second)
        cache.offer(typeB, third)

        val failure = assertThrows(IllegalStateException::class.java, cache::clear)

        assertEquals("first failed", failure.message)
        assertEquals(listOf("second failed"), failure.suppressed.map { it.message })
        assertEquals(1, first.releaseCount)
        assertEquals(1, second.releaseCount)
        assertEquals(1, third.releaseCount)
        cache.clear()
    }

    private class RecordingPresentation(
        private val failureMessage: String? = null,
    ) : ReusableItemPresentation {
        var releaseCount: Int = 0

        override fun release() {
            releaseCount += 1
            failureMessage?.let { error(it) }
        }
    }
}
