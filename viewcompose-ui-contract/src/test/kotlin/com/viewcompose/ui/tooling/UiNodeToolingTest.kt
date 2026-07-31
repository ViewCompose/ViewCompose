package com.viewcompose.ui.tooling

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UiNodeToolingTest {
    @Test
    fun `normal nodes do not allocate tooling metadata`() {
        val node = UiNodeTooling.attach(vnode())

        assertNull(UiNodeTooling.metadataOf(node))
    }

    @Test
    fun `source capture records a bounded runtime call chain`() {
        val node = UiNodeTooling.withSourceCapture {
            createCapturedNode()
        }
        val metadata = requireNotNull(UiNodeTooling.metadataOf(node))

        assertTrue(metadata.nodeId.startsWith("node-"))
        assertTrue(metadata.callSites.size in 1..16)
        assertTrue(
            metadata.callSites.any { site ->
                site.fileName == "UiNodeToolingTest.kt" &&
                    site.methodName.contains("createCapturedNode") &&
                    site.lineNumber > 0
            },
        )
    }

    @Test
    fun `semantic copies preserve identity and synthetic hosts derive it`() {
        val source = UiNodeTooling.withSourceCapture {
            createCapturedNode()
        }
        val sourceMetadata = requireNotNull(UiNodeTooling.metadataOf(source))

        val copy = UiNodeTooling.inheritCopy(
            target = source.copy(key = "copy"),
            source = source,
        )
        val synthetic = UiNodeTooling.inheritSynthetic(
            target = vnode(),
            source = source,
            discriminator = "host-0",
        )

        assertSame(sourceMetadata, UiNodeTooling.metadataOf(copy))
        assertEquals(
            "${sourceMetadata.nodeId}/host-0",
            UiNodeTooling.metadataOf(synthetic)?.nodeId,
        )
        assertTrue(requireNotNull(UiNodeTooling.metadataOf(synthetic)).synthetic)
    }

    private fun createCapturedNode(): VNode = UiNodeTooling.attach(vnode())

    private fun vnode(): VNode {
        return VNode(
            type = NodeType.Box,
            spec = EmptyNodeSpec,
        )
    }
}
