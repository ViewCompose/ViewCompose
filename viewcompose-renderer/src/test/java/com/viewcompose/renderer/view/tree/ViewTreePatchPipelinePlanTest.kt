package com.viewcompose.renderer.view.tree

/*
 * 测试职责：覆盖 renderer view/tree 中的 View Tree Patch Pipeline Plan 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers View Tree Patch Pipeline Plan behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewTreePatchPipelinePlanTest {
    @Test
    fun `skip subtree plan bypasses child reconcile`() {
        assertFalse(ViewTreePatchPipeline.shouldReconcileChildren(NodeBindingPlan.SkipSubtree))
    }

    @Test
    fun `rebind and skip-self plans still reconcile children`() {
        assertTrue(ViewTreePatchPipeline.shouldReconcileChildren(NodeBindingPlan.Rebind))
        assertTrue(ViewTreePatchPipeline.shouldReconcileChildren(NodeBindingPlan.SkipSelfOnly))
        // Patch branch remains "reconcile children" by design and is covered by integration paths.
    }
}
