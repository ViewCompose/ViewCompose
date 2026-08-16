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
        tracker.recordBootstrapUpperBound(key, elapsedNanos = 8_000_000L)
        assertFalse(tracker.shouldPrepare(key))
    }

    @Test
    fun `observed cheap type becomes eligible and an expensive observation revokes it`() {
        val tracker = LazyPreparationCostTracker(budgetNanos = 4_000_000L)

        tracker.recordBootstrapUpperBound(key, elapsedNanos = 2_000_000L)
        assertTrue(tracker.shouldPrepare(key))

        tracker.recordPreparation(key, elapsedNanos = 14_000_000L)
        assertFalse(tracker.shouldPrepare(key))
    }

    @Test
    fun `one over-budget sample revokes preparation after a very cheap history`() {
        val tracker = LazyPreparationCostTracker(budgetNanos = 4_000_000L)

        tracker.recordPreparation(key, elapsedNanos = 1_000_000L)
        assertTrue(tracker.shouldPrepare(key))

        tracker.recordPreparation(key, elapsedNanos = 8_000_000L)

        assertFalse(tracker.shouldPrepare(key))
        assertTrue(checkNotNull(tracker.estimatedCostNanos(key)) >= 8_000_000L)
    }

    @Test
    fun `actual preparation replaces bootstrap upper bound and later activations cannot pollute it`() {
        val tracker = LazyPreparationCostTracker(budgetNanos = 4_000_000L)

        tracker.recordBootstrapUpperBound(key, elapsedNanos = 8_000_000L)
        assertFalse(tracker.shouldPrepare(key))
        assertTrue(tracker.hasBootstrapUpperBound(key))

        tracker.recordPreparation(key, elapsedNanos = 1_000_000L)
        assertFalse(tracker.hasBootstrapUpperBound(key))
        tracker.recordBootstrapUpperBound(key, elapsedNanos = 20_000_000L)

        assertTrue(tracker.shouldPrepare(key))
        assertFalse(tracker.hasBootstrapUpperBound(key))
        assertTrue(checkNotNull(tracker.estimatedCostNanos(key)) <= 1_000_000L)
    }

    @Test
    fun `clear forgets learned eligibility`() {
        val tracker = LazyPreparationCostTracker()
        tracker.recordBootstrapUpperBound(key, elapsedNanos = 1L)
        assertTrue(tracker.shouldPrepare(key))

        tracker.clear()

        assertFalse(tracker.shouldPrepare(key))
    }
}
