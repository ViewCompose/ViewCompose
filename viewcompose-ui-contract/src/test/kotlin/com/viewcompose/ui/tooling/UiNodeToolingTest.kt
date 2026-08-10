package com.viewcompose.ui.tooling

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
        assertTrue(metadata.callSites.size in 1..32)
        assertTrue(
            metadata.callSites.any { site ->
                site.fileName == "UiNodeToolingTest.kt" &&
                    site.methodName.contains("createCapturedNode") &&
                    site.lineNumber > 0
            },
        )
    }

    @Test
    fun `first source capture reports one node without attaching metadata`() {
        val captures = mutableListOf<List<UiSourceCallSite>>()
        lateinit var first: VNode
        lateinit var second: VNode

        UiNodeTooling.withFirstSourceCapture(captures::add) {
            first = createCapturedNode()
            second = createCapturedNode()
        }

        assertEquals(1, captures.size)
        assertTrue(
            captures.single().any { site ->
                site.fileName == "UiNodeToolingTest.kt" &&
                    site.methodName.contains("createCapturedNode")
            },
        )
        assertNull(UiNodeTooling.metadataOf(first))
        assertNull(UiNodeTooling.metadataOf(second))
    }

    @Test
    fun `first source capture is silent when no node is emitted`() {
        var callbackCount = 0

        val result = UiNodeTooling.withFirstSourceCapture(
            onSourceCaptured = { callbackCount += 1 },
        ) {
            "no-node"
        }

        assertEquals("no-node", result)
        assertEquals(0, callbackCount)
    }

    @Test
    fun `nested first source captures independently observe their next node`() {
        var outerCaptures = 0
        var innerCaptures = 0

        UiNodeTooling.withFirstSourceCapture(onSourceCaptured = { outerCaptures += 1 }) {
            createCapturedNode()
            UiNodeTooling.withFirstSourceCapture(onSourceCaptured = { innerCaptures += 1 }) {
                createCapturedNode()
                createCapturedNode()
            }
        }

        assertEquals(1, outerCaptures)
        assertEquals(1, innerCaptures)
    }

    @Test
    fun `first source capture restores state after callback failure`() {
        try {
            UiNodeTooling.withFirstSourceCapture(
                onSourceCaptured = { error("capture failed") },
            ) {
                createCapturedNode()
            }
            fail("Expected the capture callback to fail")
        } catch (expected: IllegalStateException) {
            assertEquals("capture failed", expected.message)
        }

        assertNull(UiNodeTooling.metadataOf(createCapturedNode()))
    }

    @Test
    fun `source candidate capture retains distinct scaffold and content chains`() {
        var candidates = emptyList<List<UiSourceCallSite>>()
        lateinit var scaffoldNode: VNode
        lateinit var contentNode: VNode

        UiNodeTooling.withSourceCandidateCapture(
            onSourceCandidatesCaptured = { captured -> candidates = captured },
        ) {
            scaffoldNode = createScaffoldCandidateNode()
            contentNode = createContentCandidateNode()
        }

        assertTrue(candidates.size >= 2)
        assertTrue(
            candidates.any { chain ->
                chain.any { source -> source.methodName.contains("createScaffoldCandidateNode") }
            },
        )
        assertTrue(
            candidates.any { chain ->
                chain.any { source -> source.methodName.contains("createContentCandidateNode") }
            },
        )
        assertNull(UiNodeTooling.metadataOf(scaffoldNode))
        assertNull(UiNodeTooling.metadataOf(contentNode))
    }

    @Test
    fun `source candidate capture is silent when block fails`() {
        var callbackCount = 0

        try {
            UiNodeTooling.withSourceCandidateCapture(
                onSourceCandidatesCaptured = { callbackCount += 1 },
            ) {
                createContentCandidateNode()
                error("tree failed")
            }
            fail("Expected tree construction to fail")
        } catch (expected: IllegalStateException) {
            assertEquals("tree failed", expected.message)
        }

        assertEquals(0, callbackCount)
        assertNull(UiNodeTooling.metadataOf(createCapturedNode()))
    }

    @Test
    fun `source selection removes runtime infrastructure and retains deep application callers`() {
        val infrastructure = List(24) { index ->
            StackTraceElement(
                "com.viewcompose.host.android.runtime.FrameRuntime$index",
                "render",
                "FrameRuntime.kt",
                index + 1,
            )
        }
        val application = StackTraceElement(
            "com.example.DeepPreviewKt",
            "DeepPreview",
            "DeepPreview.kt",
            73,
        )

        val callSites = UiNodeTooling.selectSourceCallSites(
            (infrastructure + application).asSequence(),
        )

        assertEquals(1, callSites.size)
        assertEquals("com.example.DeepPreviewKt", callSites.single().className)
        assertEquals(73, callSites.single().lineNumber)
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

    private fun createScaffoldCandidateNode(): VNode = UiNodeTooling.attach(vnode())

    private fun createContentCandidateNode(): VNode = UiNodeTooling.attach(vnode())

    private fun vnode(): VNode {
        return VNode(
            type = NodeType.Box,
            spec = EmptyNodeSpec,
        )
    }
}
