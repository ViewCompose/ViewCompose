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
}
