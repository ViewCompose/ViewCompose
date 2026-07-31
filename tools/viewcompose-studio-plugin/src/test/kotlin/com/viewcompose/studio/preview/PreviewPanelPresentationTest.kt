package com.viewcompose.studio.preview

import java.awt.image.BufferedImage
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewPanelPresentationTest {
    @Test
    fun `empty state restores the default tool window title`() {
        val presentation = ViewComposePreviewPanelState.Empty.previewPresentation()

        assertNull(presentation.title)
        assertNull(presentation.source)
    }

    @Test
    fun `rendered state uses descriptor name and keeps preview entry source`() {
        val selection = selection()
        val result = PreviewRenderOutcome.Success(
            selection = selection,
            descriptorId = "static-demo",
            descriptorName = "StaticDemoPreview",
            variants = emptyList(),
            selectedVariantId = "light",
            variantName = "Light",
            image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            imagePath = Path.of("/project/build/preview.png"),
            renderTreePath = null,
            renderSnapshot = null,
            diagnostics = emptyList(),
            durationMillis = 10,
            cacheHit = false,
        )

        val presentation =
            ViewComposePreviewPanelState.Rendered(result).previewPresentation()

        assertEquals("StaticDemoPreview", presentation.title)
        assertEquals(selection, presentation.source)
    }

    @Test
    fun `gallery uses the default tool window title and no source action`() {
        val presentation = ViewComposePreviewPanelState.Gallery(
            PreviewGalleryResult(items = emptyList(), failures = emptyList()),
        ).previewPresentation()

        assertNull(presentation.title)
        assertNull(presentation.source)
    }

    private fun selection(): PreviewSourceSelection {
        return PreviewSourceSelection(
            filePath = "/project/app/src/debug/java/com/viewcompose/StaticDemoPreviewEntrypoints.kt",
            symbolName = "StaticDemoPreview",
            line = 41,
        )
    }
}
