package com.viewcompose.studio.preview

import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewNativeViewTooltipTest {
    @Test
    fun `native View tooltip includes bounded common properties and escapes text`() {
        val view = StudioPreviewNativeViewNode(
            className = "android.widget.TextView",
            bounds = StudioPreviewLayoutBounds(10, 20, 110, 60),
            measuredWidth = 100,
            measuredHeight = 40,
            visibility = "VISIBLE",
            visibleBounds = StudioPreviewLayoutBounds(10, 20, 110, 60),
            clippingState = StudioPreviewClippingState.NotClipped,
            clippingAncestorClassName = null,
            clippingAncestorNodeId = null,
            clippingExpected = false,
            properties = mapOf(
                "enabled" to "true",
                "text" to "<Preview & title>",
            ),
            nodeId = "node-title",
            sourceCallSites = emptyList(),
            synthetic = false,
            children = emptyList(),
        )

        val tooltip = nativeViewToolTip(
            view = view,
            messages = PreviewUiMessages.forLanguage(PreviewUiLanguage.English),
        )

        assertTrue(tooltip.contains("TextView"))
        assertTrue(tooltip.contains("100 × 40 @ 10, 20"))
        assertTrue(tooltip.contains("enabled = true"))
        assertTrue(tooltip.contains("text = &lt;Preview &amp; title&gt;"))
    }
}
