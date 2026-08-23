package com.viewcompose.host.android.runtime

import android.view.View
import android.widget.FrameLayout
import com.viewcompose.renderer.view.tree.MountedNode
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidCoreRenderEngineNodeInspectionTest {
    @Test
    fun `requested inspection is depth first weak and deterministically bounded`() {
        val context = RuntimeEnvironment.getApplication()
        val parentView = FrameLayout(context)
        val firstView = View(context)
        val secondView = View(context)
        val thirdView = View(context)
        val parent = mounted(NodeType.Column, parentView).apply {
            children = listOf(
                mounted(NodeType.Text, firstView),
                mounted(NodeType.Spacer, secondView),
                mounted(NodeType.Divider, thirdView),
            )
        }

        val result = AndroidCoreRenderEngine().inspectMountedNodes(
            mountedNodes = listOf(parent),
            maxVisitedNodes = 3,
            maxReturnedNodes = 2,
            maxDepth = 64,
        )

        assertTrue(result.supported)
        assertTrue(result.truncated)
        assertEquals(3, result.visitedNodes)
        assertEquals(1, result.droppedNodes)
        assertEquals(listOf(NodeType.Column, NodeType.Text), result.nodes.map { it.type })
        assertEquals(null, result.nodes[0].parentIndex)
        assertEquals(0, result.nodes[1].parentIndex)
        assertSame(parentView, result.nodes[0].platformTarget.resolve())
        assertSame(firstView, result.nodes[1].platformTarget.resolve())
    }

    @Test
    fun `renderer infrastructure is explicit and depth overflow stops descendants`() {
        val context = RuntimeEnvironment.getApplication()
        val synthetic = mounted(NodeType.LayoutConstraintHost, FrameLayout(context)).apply {
            children = listOf(mounted(NodeType.Text, View(context)))
        }

        val result = AndroidCoreRenderEngine().inspectMountedNodes(
            mountedNodes = listOf(synthetic),
            maxVisitedNodes = 10,
            maxReturnedNodes = 10,
            maxDepth = 0,
        )

        assertEquals(2, result.visitedNodes)
        assertEquals(1, result.nodes.size)
        assertTrue(result.nodes.single().synthetic)
        assertTrue(result.truncated)
        assertEquals(1, result.droppedNodes)
        assertFalse(result.nodes.single().sourceCallSites.isNotEmpty())
    }

    private fun mounted(
        type: NodeType,
        view: View,
    ): MountedNode {
        return MountedNode(
            vnode = VNode(
                type = type,
                key = "private-key-must-not-leave-renderer",
                spec = EmptyNodeSpec,
            ),
            view = view,
        )
    }
}
