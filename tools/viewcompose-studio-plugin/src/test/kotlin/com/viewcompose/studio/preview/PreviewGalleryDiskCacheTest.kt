package com.viewcompose.studio.preview

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewGalleryDiskCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stores a bounded thumbnail without a diagnostic snapshot`() {
        val root = temporaryFolder.newFolder("gallery").toPath()
        val cache = PreviewGalleryDiskCache(root)
        val selection = selection("LargePreview")

        val written = cache.write(result(selection, width = 800, height = 1600))
        val item = cache.read(selection)

        assertEquals(180, written.image.width)
        assertEquals(360, written.image.height)
        assertEquals(180, item?.image?.width)
        assertEquals(360, item?.image?.height)
        assertTrue(item?.cacheHit == true)
        assertEquals(2, Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).count() })
    }

    @Test
    fun `gallery retention supports many more entries but remains bounded`() {
        val root = temporaryFolder.newFolder("bounded-gallery").toPath()
        var now = 1_000L
        val cache = PreviewGalleryDiskCache(
            cacheRoot = root,
            maxEntries = 2,
            maxAge = Duration.ofMillis(100),
            nowMillis = { now },
        )
        cache.write(result(selection("One")))
        now += 10
        cache.write(result(selection("Two")))
        now += 10
        cache.write(result(selection("Three")))

        assertNull(cache.read(selection("One")))
        assertEquals("Two", cache.read(selection("Two"))?.selection?.symbolName)
        now += 200
        cache.prune()
        assertNull(cache.read(selection("Two")))
    }

    private fun result(
        selection: PreviewSourceSelection,
        width: Int = 4,
        height: Int = 6,
    ): PreviewRenderOutcome.Success {
        val artifactRoot = temporaryFolder.newFolder().toPath()
        val imagePath = artifactRoot.resolve("preview.png")
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        ImageIO.write(image, "png", imagePath.toFile())
        return PreviewRenderOutcome.Success(
            selection = selection,
            descriptorId = selection.symbolName.lowercase(),
            descriptorName = selection.symbolName,
            variants = listOf(StudioPreviewVariant("default", "Default")),
            selectedVariantId = "default",
            variantName = "Default",
            image = image,
            imagePath = imagePath,
            renderTreePath = null,
            renderSnapshot = null,
            diagnostics = emptyList(),
            durationMillis = 1,
            cacheHit = false,
        )
    }

    private fun selection(symbol: String): PreviewSourceSelection {
        return PreviewSourceSelection(
            filePath = Path.of("/project/$symbol.kt").toString(),
            symbolName = symbol,
            line = 1,
        )
    }
}
