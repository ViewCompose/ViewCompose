package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewNodeSelectionCoordinatorTest {
    @Test
    fun `source caret selects mapped runtime node and publishes to every surface`() {
        val published = mutableListOf<String?>()
        val canvasSelections = mutableListOf<String?>()
        val treeSelections = mutableListOf<String?>()
        val coordinator = PreviewNodeSelectionCoordinator(
            snapshot = snapshot(),
            initialNodeId = null,
            onSelectionChanged = published::add,
        )
        coordinator.register(canvasSelections::add)
        coordinator.register(treeSelections::add)

        coordinator.selectSource(
            filePath = "/project/app/src/main/java/com/example/AboutPage.kt",
            line = 42,
        )

        assertEquals("text-node", coordinator.selectedNodeId)
        assertEquals(listOf(null, "text-node"), canvasSelections)
        assertEquals(listOf(null, "text-node"), treeSelections)
        assertEquals(listOf("text-node"), published)
    }

    @Test
    fun `source caret tolerates a multiline DSL invocation`() {
        val coordinator = PreviewNodeSelectionCoordinator(
            snapshot = snapshot(),
            initialNodeId = null,
            onSelectionChanged = {},
        )

        coordinator.selectSource(
            filePath = "/project/app/src/main/java/com/example/AboutPage.kt",
            lineCandidates = listOf(46, 42),
        )

        assertEquals("text-node", coordinator.selectedNodeId)
    }

    @Test
    fun `unmapped source clears linked selection`() {
        val coordinator = PreviewNodeSelectionCoordinator(
            snapshot = snapshot(),
            initialNodeId = "text-node",
            onSelectionChanged = {},
        )

        coordinator.selectSource(
            filePath = "/project/app/src/main/java/com/example/OtherPage.kt",
            line = 42,
        )

        assertNull(coordinator.selectedNodeId)
    }

    @Test
    fun `surface selection is normalized against committed snapshot`() {
        val coordinator = PreviewNodeSelectionCoordinator(
            snapshot = snapshot(),
            initialNodeId = "missing",
            onSelectionChanged = {},
        )

        assertNull(coordinator.selectedNodeId)
        coordinator.select("missing")
        assertNull(coordinator.selectedNodeId)
        coordinator.select("text-node")
        assertEquals("text-node", coordinator.selectedNodeId)
    }

    private fun snapshot(): StudioPreviewRenderSnapshot {
        val callSite = StudioPreviewSourceCallSite(
            className = "com.example.AboutPageKt",
            methodName = "AboutPage",
            fileName = "AboutPage.kt",
            lineNumber = 42,
        )
        return StudioPreviewRenderSnapshot(
            stats = StudioPreviewRenderStats(
                inserts = 1,
                reuses = 0,
                removals = 0,
                reboundNodes = 1,
                patchedNodes = 0,
                skippedBindings = 0,
                skippedSubtrees = 0,
            ),
            structure = StudioPreviewRenderStructure(
                vnodeCount = 1,
                mountedNodeCount = 1,
                maxVNodeDepth = 0,
                maxMountedDepth = 0,
            ),
            warnings = emptyList(),
            tree = listOf(
                StudioPreviewRenderTreeNode(
                    type = "Text",
                    key = null,
                    nodeId = "text-node",
                    sourceCallSites = listOf(callSite),
                    synthetic = false,
                    children = emptyList(),
                ),
            ),
            nativeViewTree = listOf(
                StudioPreviewNativeViewNode(
                    className = "android.widget.TextView",
                    bounds = StudioPreviewLayoutBounds(
                        left = 0,
                        top = 0,
                        right = 100,
                        bottom = 40,
                    ),
                    measuredWidth = 100,
                    measuredHeight = 40,
                    visibility = "VISIBLE",
                    visibleBounds = StudioPreviewLayoutBounds(
                        left = 0,
                        top = 0,
                        right = 100,
                        bottom = 40,
                    ),
                    clippingState = StudioPreviewClippingState.NotClipped,
                    clippingAncestorClassName = null,
                    clippingAncestorNodeId = null,
                    clippingExpected = false,
                    nodeId = "text-node",
                    sourceCallSites = listOf(callSite),
                    synthetic = false,
                    children = emptyList(),
                ),
            ),
            layoutDiagnostics = emptyList(),
            patches = emptyList(),
            composition = StudioPreviewCompositionSnapshot(
                invalidatedScopeCount = 0,
                recomposedScopeCount = 0,
                skippedScopeCount = 0,
                scopes = emptyList(),
            ),
        )
    }
}
