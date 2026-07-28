package com.viewcompose.renderer.view.tree

/*
 * 测试职责：覆盖 renderer view/tree 中的 Render Warning Collector 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Render Warning Collector behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderWarningCollectorTest {
    @Test
    fun `warns about duplicate and repeated unkeyed siblings`() {
        val warnings = RenderWarningCollector.collect(
            nodes = listOf(
                vnode(NodeType.Text),
                vnode(NodeType.Text),
                vnode(NodeType.Button, key = "cta"),
                vnode(NodeType.Button, key = "cta"),
            ),
            structure = RenderStructureStats(
                vnodeCount = 4,
                mountedNodeCount = 4,
                maxVNodeDepth = 2,
                maxMountedDepth = 2,
            ),
            stats = RenderStats(),
        )

        assertTrue(warnings.any { it.contains("Repeated unkeyed siblings under root: Text x2") })
        assertTrue(warnings.any { it.contains("Duplicate sibling keys under root: [cta]") })
    }

    @Test
    fun `warns about deep tree and high rebind churn`() {
        val warnings = RenderWarningCollector.collect(
            nodes = listOf(vnode(NodeType.Column)),
            structure = RenderStructureStats(
                vnodeCount = 12,
                mountedNodeCount = 12,
                maxVNodeDepth = 9,
                maxMountedDepth = 11,
            ),
            stats = RenderStats(
                inserts = 1,
                removals = 2,
                reboundNodes = 9,
                patchedNodes = 2,
                skippedBindings = 1,
            ),
        )

        assertTrue(warnings.any { it.contains("Deep mounted view tree detected") })
        assertTrue(warnings.any { it.contains("High rebind churn detected") })
    }

    @Test
    fun `warns about high structural churn`() {
        val warnings = RenderWarningCollector.collect(
            nodes = listOf(vnode(NodeType.Column)),
            structure = RenderStructureStats(
                vnodeCount = 5,
                mountedNodeCount = 5,
                maxVNodeDepth = 2,
                maxMountedDepth = 2,
            ),
            stats = RenderStats(
                inserts = 6,
                removals = 4,
            ),
        )

        assertTrue(warnings.any { it.contains("High structural churn detected") })
    }

    private fun vnode(
        type: NodeType,
        key: Any? = null,
        children: List<VNode> = emptyList(),
    ): VNode {
        return VNode(
            type = type,
            key = key,
            spec = EmptyNodeSpec,
            modifier = Modifier,
            children = children,
        )
    }
}
