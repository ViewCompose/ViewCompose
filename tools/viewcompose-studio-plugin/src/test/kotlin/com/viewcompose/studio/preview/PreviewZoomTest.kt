package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewZoomTest {
    @Test
    fun `fit scale keeps preview width visible and lets tall content scroll`() {
        assertEquals(
            1.0,
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
        assertEquals(0.1, calculateMagnifiedPreviewScale(0.2, -1.0), 0.0001)
    }

    @Test
    fun `control wheel fallback zooms continuously in both directions`() {
        val zoomedIn = calculateWheelPreviewScale(1.0, -1.0)
        val zoomedOut = calculateWheelPreviewScale(1.0, 1.0)

        assertEquals(1.1, zoomedIn, 0.0001)
        assertEquals(1.0 / 1.1, zoomedOut, 0.0001)
    }
}
