package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewSourceNavigationTest {
    @Test
    fun `returns deepest mapped visible view at point`() {
        val parent = nativeView(
            className = "android.widget.FrameLayout",
            bounds = bounds(0, 0, 200, 200),
            sourceLine = 10,
            children = listOf(
                nativeView(
                    className = "android.widget.TextView",
                    bounds = bounds(40, 50, 140, 90),
                    sourceLine = 24,
                ),
            ),
        )

        val result = findMappedNativeViewAt(listOf(parent), x = 70, y = 60)

        assertEquals("android.widget.TextView", result?.className)
        assertEquals(24, result?.sourceCallSites?.single()?.lineNumber)
    }

    @Test
    fun `falls back to mapped ancestor when platform child has no mapping`() {
        val parent = nativeView(
            className = "android.widget.FrameLayout",
            bounds = bounds(0, 0, 200, 200),
            sourceLine = 10,
            children = listOf(
                nativeView(
                    className = "android.widget.TextView",
                    bounds = bounds(40, 50, 140, 90),
                    sourceLine = null,
                ),
            ),
        )

        val result = findMappedNativeViewAt(listOf(parent), x = 70, y = 60)

        assertEquals("android.widget.FrameLayout", result?.className)
        assertEquals(10, result?.sourceCallSites?.single()?.lineNumber)
    }

    @Test
    fun `ignores invisible and out of bounds views`() {
        val hidden = nativeView(
            className = "android.widget.TextView",
            bounds = bounds(0, 0, 100, 100),
            sourceLine = 20,
            visibility = "GONE",
        )

        assertNull(findMappedNativeViewAt(listOf(hidden), x = 20, y = 20))
        assertNull(findMappedNativeViewAt(listOf(hidden.copy(visibility = "VISIBLE")), x = 120, y = 20))
    }

    @Test
    fun `hit testing excludes the clipped portion of a mapped view`() {
        val clipped = nativeView(
            className = "android.widget.TextView",
            bounds = bounds(0, 0, 100, 40),
            sourceLine = 20,
        ).copy(
            visibleBounds = bounds(0, 0, 60, 40),
            clippingState = StudioPreviewClippingState.PartiallyClipped,
        )

        assertEquals(clipped, findMappedNativeViewAt(listOf(clipped), x = 40, y = 20))
        assertNull(findMappedNativeViewAt(listOf(clipped), x = 80, y = 20))
    }

    private fun nativeView(
        className: String,
        bounds: StudioPreviewLayoutBounds,
        sourceLine: Int?,
        visibility: String = "VISIBLE",
        children: List<StudioPreviewNativeViewNode> = emptyList(),
    ): StudioPreviewNativeViewNode {
        return StudioPreviewNativeViewNode(
            className = className,
            bounds = bounds,
            measuredWidth = bounds.width,
            measuredHeight = bounds.height,
            visibility = visibility,
            visibleBounds = bounds,
            clippingState = StudioPreviewClippingState.NotClipped,
            clippingAncestorClassName = null,
            clippingAncestorNodeId = null,
            clippingExpected = false,
            nodeId = sourceLine?.let { "node-$it" },
            sourceCallSites = sourceLine?.let { line ->
                listOf(
                    StudioPreviewSourceCallSite(
                        className = "com.example.PreviewKt",
                        methodName = "Preview",
                        fileName = "Preview.kt",
                        lineNumber = line,
                    ),
                )
            }.orEmpty(),
            synthetic = false,
            children = children,
        )
    }

    private fun bounds(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): StudioPreviewLayoutBounds {
        return StudioPreviewLayoutBounds(left, top, right, bottom)
    }
}
