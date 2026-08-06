package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

/*
 * 测试职责：覆盖 renderer view/tree 中的 Render Structure Stats 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Render Structure Stats behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.view.View
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderStructureStatsTest {
    @Test
    fun `computes vnode and mounted tree depth`() {
        val vnodeTree = listOf(
            VNode(
                type = NodeType.Column,
                spec = EmptyNodeSpec,
                modifier = Modifier,
                children = listOf(
                    VNode(
                        type = NodeType.Row,
                        spec = EmptyNodeSpec,
                        modifier = Modifier,
                        children = listOf(
                            VNode(
                                type = NodeType.Text,
                                spec = EmptyNodeSpec,
                                modifier = Modifier,
                            ),
                        ),
                    ),
                    VNode(
                        type = NodeType.Button,
                        spec = EmptyNodeSpec,
                        modifier = Modifier,
                    ),
                ),
            ),
        )
        val mountedTree = listOf(
            MountedNode(
                vnode = vnodeTree.first(),
                view = View(null),
                children = listOf(
                    MountedNode(
                        vnode = vnodeTree.first().children.first(),
                        view = View(null),
                        children = listOf(
                            MountedNode(
                                vnode = vnodeTree.first().children.first().children.first(),
                                view = View(null),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val stats = RenderStructureStats.from(
            nodes = vnodeTree,
            mountedNodes = mountedTree,
        )

        assertEquals(4, stats.vnodeCount)
        assertEquals(3, stats.mountedNodeCount)
        assertEquals(3, stats.maxVNodeDepth)
        assertEquals(3, stats.maxMountedDepth)
    }
}
