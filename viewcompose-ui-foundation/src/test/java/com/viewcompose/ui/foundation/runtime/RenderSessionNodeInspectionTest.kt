package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.RenderContainerHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderSessionNodeInspectionTest {
    @Test
    fun `request creates fresh tokens and preserves retained parent order`() {
        val state = RenderSessionMountedNodeState().apply {
            mountedNodes = listOf("mounted-root")
        }
        var limits = emptyList<Int>()
        val target = RenderNodePlatformTarget { "native" }
        val engine = fakeEngine { mounted, visited, returned, depth ->
            assertEquals(listOf("mounted-root"), mounted)
            limits = listOf(visited, returned, depth)
            CoreMountedNodeInspection(
                nodes = listOf(
                    CoreInspectedMountedNode(
                        parentIndex = null,
                        type = NodeType.Column,
                        depth = 0,
                        synthetic = false,
                        sourceCallSites = emptyList(),
                        platformTarget = target,
                    ),
                    CoreInspectedMountedNode(
                        parentIndex = 0,
                        type = NodeType.Text,
                        depth = 1,
                        synthetic = false,
                        sourceCallSites = emptyList(),
                        platformTarget = target,
                    ),
                ),
                visitedNodes = 2,
                droppedNodes = 0,
                truncated = false,
                supported = true,
            )
        }
        val inspection = DefaultRenderSessionNodeInspection(state, engine)

        val first = inspection.snapshot()
        val second = inspection.snapshot()

        assertEquals(listOf(2_048, 512, 64), limits)
        assertEquals(2, first.nodes.size)
        assertNull(first.nodes.first().parentToken)
        assertEquals(first.nodes.first().token, first.nodes.last().parentToken)
        assertEquals("native", first.nodes.last().platformTarget.resolve())
        assertNotEquals(first.nodes.first().token, second.nodes.first().token)
        assertFalse(first.truncated)
    }

    @Test
    fun `ended session never calls renderer inspection`() {
        val state = RenderSessionMountedNodeState().apply { ended = true }
        var calls = 0
        val inspection = DefaultRenderSessionNodeInspection(
            state = state,
            renderEngine = fakeEngine { _, _, _, _ ->
                calls += 1
                CoreMountedNodeInspection.Unsupported
            },
        )

        val snapshot = inspection.snapshot()

        assertTrue(snapshot.ended)
        assertTrue(snapshot.supported)
        assertTrue(snapshot.nodes.isEmpty())
        assertEquals(0, calls)
    }

    private fun fakeEngine(
        inspect: (List<Any>, Int, Int, Int) -> CoreMountedNodeInspection,
    ): CoreRenderEngine {
        return object : CoreRenderEngine {
            override fun renderInto(
                container: RenderContainerHandle,
                previousMountedNodes: List<Any>,
                nodes: List<com.viewcompose.ui.node.VNode>,
                diagnosticLevel: RenderFrameDiagnosticLevel,
            ): CoreRenderFrame = CoreRenderFrame(emptyList())

            override fun inspectMountedNodes(
                mountedNodes: List<Any>,
                maxVisitedNodes: Int,
                maxReturnedNodes: Int,
                maxDepth: Int,
            ): CoreMountedNodeInspection {
                return inspect(mountedNodes, maxVisitedNodes, maxReturnedNodes, maxDepth)
            }

            override fun disposeMounted(
                container: RenderContainerHandle,
                mountedNodes: List<Any>,
            ): List<CoreRenderCommitFailure> = emptyList()
        }
    }
}
