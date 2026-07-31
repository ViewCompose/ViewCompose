package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewLayoutDepthStyleTest {
    @Test
    fun `deeper semantic nodes receive progressively stronger stable warning styles`() {
        val styles = (1..6).map(::previewLayoutDepthStyle)

        assertTrue(styles.zipWithNext().all { (shallower, deeper) ->
            deeper.strokeAlpha > shallower.strokeAlpha
        })
        assertEquals(styles.last(), previewLayoutDepthStyle(20))
    }

    @Test
    fun `equal semantic bounds collapse through a platform root to the deepest layer`() {
        val sharedBounds = StudioPreviewLayoutBounds(0, 0, 100, 100)
        val roots = listOf(
            view(
                bounds = sharedBounds,
                mapped = false,
                children = listOf(
                    view(
                        bounds = sharedBounds,
                        children = listOf(view(bounds = sharedBounds)),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(PreviewLayoutBoundLayer(sharedBounds, depth = 2)),
            previewLayoutBoundLayers(roots),
        )
    }

    @Test
    fun `platform and synthetic wrappers do not consume semantic depth tiers`() {
        val parentBounds = StudioPreviewLayoutBounds(0, 0, 100, 100)
        val childBounds = StudioPreviewLayoutBounds(10, 10, 90, 40)
        val roots = listOf(
            view(
                bounds = parentBounds,
                mapped = false,
                children = listOf(
                    view(
                        bounds = parentBounds,
                        mapped = false,
                        children = listOf(
                            view(
                                bounds = parentBounds,
                                children = listOf(
                                    view(
                                        bounds = childBounds,
                                        synthetic = true,
                                        children = listOf(
                                            view(bounds = childBounds, mapped = false),
                                            view(bounds = childBounds),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                PreviewLayoutBoundLayer(parentBounds, depth = 1),
                PreviewLayoutBoundLayer(childBounds, depth = 2),
            ),
            previewLayoutBoundLayers(roots),
        )
    }

    private fun view(
        bounds: StudioPreviewLayoutBounds,
        mapped: Boolean = true,
        synthetic: Boolean = false,
        children: List<StudioPreviewNativeViewNode> = emptyList(),
    ): StudioPreviewNativeViewNode {
        return StudioPreviewNativeViewNode(
            className = "android.view.View",
            bounds = bounds,
            measuredWidth = bounds.width,
            measuredHeight = bounds.height,
            visibility = "VISIBLE",
            visibleBounds = bounds,
            clippingState = StudioPreviewClippingState.NotClipped,
            clippingAncestorClassName = null,
            clippingAncestorNodeId = null,
            clippingExpected = false,
            nodeId = if (mapped) {
                "node-${bounds.left}-${bounds.top}-${bounds.right}-${bounds.bottom}"
            } else {
                null
            },
            sourceCallSites = if (mapped) {
                listOf(
                    StudioPreviewSourceCallSite(
                        className = "sample.PreviewKt",
                        methodName = "Preview",
                        fileName = "Preview.kt",
                        lineNumber = 10,
                    ),
                )
            } else {
                emptyList()
            },
            synthetic = synthetic,
            children = children,
        )
    }
}
