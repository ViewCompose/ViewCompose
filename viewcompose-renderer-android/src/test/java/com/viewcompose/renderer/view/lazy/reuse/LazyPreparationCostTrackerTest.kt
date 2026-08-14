package com.viewcompose.renderer.view.lazy.reuse

import com.viewcompose.ui.node.LazyListItemKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyPreparationCostTrackerTest {
    private val key = MountedTreeReuseCache.ReuseKey(LazyListItemKind.Item, "fixture")

    @Test
    fun `unknown and expensive types do not perform synchronous speculative preparation`() {
        val tracker = LazyPreparationCostTracker(budgetNanos = 4_000_000L)

        assertFalse(tracker.shouldPrepare(key))
        tracker.record(key, elapsedNanos = 8_000_000L)
        assertFalse(tracker.shouldPrepare(key))
    }

    @Test
    fun `observed cheap type becomes eligible and exponentially weighted cost can revoke it`() {
        val tracker = LazyPreparationCostTracker(budgetNanos = 4_000_000L)

        tracker.record(key, elapsedNanos = 2_000_000L)
        assertTrue(tracker.shouldPrepare(key))

        tracker.record(key, elapsedNanos = 14_000_000L)
        assertFalse(tracker.shouldPrepare(key))
    }

    @Test
    fun `clear forgets learned eligibility`() {
        val tracker = LazyPreparationCostTracker()
        tracker.record(key, elapsedNanos = 1L)
        assertTrue(tracker.shouldPrepare(key))

        tracker.clear()

        assertFalse(tracker.shouldPrepare(key))
    }
}
