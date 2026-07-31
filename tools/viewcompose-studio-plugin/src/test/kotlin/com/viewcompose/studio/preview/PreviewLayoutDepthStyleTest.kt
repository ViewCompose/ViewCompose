package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewLayoutDepthStyleTest {
    @Test
    fun `deeper Views receive progressively stronger stable warning styles`() {
        val styles = (1..6).map(::previewLayoutDepthStyle)

        assertTrue(styles.zipWithNext().all { (shallower, deeper) ->
            deeper.strokeAlpha > shallower.strokeAlpha
        })
        assertEquals(styles.last(), previewLayoutDepthStyle(20))
    }

    @Test
    fun `equal nested bounds collapse to the deepest visible layer`() {
        val sharedBounds = StudioPreviewLayoutBounds(0, 0, 100, 100)
        val roots = listOf(
            view(
                bounds = sharedBounds,
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

    private fun view(
        bounds: StudioPreviewLayoutBounds,
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
            nodeId = null,
            sourceCallSites = emptyList(),
            synthetic = false,
            children = children,
        )
    }
}
