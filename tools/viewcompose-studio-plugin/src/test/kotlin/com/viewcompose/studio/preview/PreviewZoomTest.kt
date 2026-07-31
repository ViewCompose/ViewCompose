package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewZoomTest {
    @Test
    fun `fit scale keeps the entire tall preview visible`() {
        assertEquals(
            0.5,
            calculatePreviewScale(
                option = PreviewZoomOption.Fit,
                imageWidth = 400,
                imageHeight = 800,
                viewportWidth = 416,
                viewportHeight = 416,
            ),
            0.0001,
        )
    }

    @Test
    fun `fit scale shrinks a wide preview to the viewport width`() {
        assertEquals(
            0.5,
            calculatePreviewScale(
                option = PreviewZoomOption.Fit,
                imageWidth = 800,
                imageHeight = 400,
                viewportWidth = 416,
                viewportHeight = 900,
            ),
            0.0001,
        )
    }

    @Test
    fun `fit scale does not upscale small previews`() {
        assertEquals(
            1.0,
            calculatePreviewScale(
                option = PreviewZoomOption.Fit,
                imageWidth = 200,
                imageHeight = 300,
                viewportWidth = 1200,
                viewportHeight = 900,
            ),
            0.0001,
        )
    }

    @Test
    fun `fixed zoom ignores viewport dimensions`() {
        assertEquals(
            1.5,
            calculatePreviewScale(
                option = PreviewZoomOption.Percent150,
                imageWidth = 400,
                imageHeight = 800,
                viewportWidth = 100,
                viewportHeight = 100,
            ),
            0.0001,
        )
    }

    @Test
    fun `native magnification supports continuous zoom within safe bounds`() {
        assertEquals(1.25, calculateMagnifiedPreviewScale(1.0, 0.25), 0.0001)
        assertEquals(0.75, calculateMagnifiedPreviewScale(1.0, -0.25), 0.0001)
        assertEquals(4.0, calculateMagnifiedPreviewScale(3.0, 1.0), 0.0001)
        assertEquals(0.05, calculateMagnifiedPreviewScale(0.2, -1.0), 0.0001)
    }

    @Test
    fun `toolbar and trackpad zoom share the same safe range`() {
        assertEquals(1.25, calculateButtonPreviewScale(1.0, 1), 0.0001)
        assertEquals(0.8, calculateButtonPreviewScale(1.0, -1), 0.0001)
        assertEquals(4.0, calculateButtonPreviewScale(4.0, 1), 0.0001)
        assertEquals(0.05, calculateButtonPreviewScale(0.05, -1), 0.0001)
    }

    @Test
    fun `control wheel fallback zooms continuously in both directions`() {
        val zoomedIn = calculateWheelPreviewScale(1.0, -1.0)
        val zoomedOut = calculateWheelPreviewScale(1.0, 1.0)

        assertEquals(1.1, zoomedIn, 0.0001)
        assertEquals(1.0 / 1.1, zoomedOut, 0.0001)
    }

    @Test
    fun `trackpad scrolling remains precise and bounded`() {
        assertEquals(124, calculatePreviewScrollPosition(100, 500, 0.5))
        assertEquals(0, calculatePreviewScrollPosition(10, 500, -2.0))
        assertEquals(500, calculatePreviewScrollPosition(490, 500, 2.0))
    }

    @Test
    fun `anchored zoom keeps the same image point under the pointer`() {
        assertEquals(
            400,
            calculateAnchoredPreviewPosition(
                imageOffset = 0,
                imageAnchor = 300.0,
                scale = 2.0,
                anchorViewportOffset = 200,
                maximumPosition = 1_000,
            ),
        )
        assertEquals(
            0,
            calculateAnchoredPreviewPosition(
                imageOffset = 100,
                imageAnchor = 50.0,
                scale = 0.5,
                anchorViewportOffset = 200,
                maximumPosition = 1_000,
            ),
        )
    }

}
