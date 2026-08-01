package com.viewcompose.studio.preview

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewGalleryDiskCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stores a Retina thumbnail and a lazy bounded detail image`() {
        val root = temporaryFolder.newFolder("gallery").toPath()
        val cache = PreviewGalleryDiskCache(root)
        val selection = selection("LargePreview")

        val written = cache.write(result(selection, width = 800, height = 1600))
        val item = cache.readAll(listOf(selection)).singleOrNull()

        assertEquals(360, written.thumbnail.width)
        assertEquals(720, written.thumbnail.height)
        assertEquals(360, item?.thumbnail?.width)
        assertEquals(720, item?.thumbnail?.height)
        assertEquals(800, ImageIO.read(written.detailImagePath.toFile()).width)
        assertEquals(1600, ImageIO.read(written.detailImagePath.toFile()).height)
        assertEquals(411, item?.logicalWidthDp)
        assertTrue(item?.cacheHit == true)
        assertEquals(3, Files.walk(root).use { paths -> paths.filter(Files::isRegularFile).count() })
        val firstDecode = checkNotNull(item).thumbnail
        item.releaseThumbnail()
        assertNotSame(firstDecode, item.thumbnail)
    }

    @Test
    fun `stores every variant of the same preview function independently`() {
        val root = temporaryFolder.newFolder("gallery-variants").toPath()
        val cache = PreviewGalleryDiskCache(root)
        val selection = selection("ThemedPreview")
        val variants = listOf(
            StudioPreviewVariant("light", "Light"),
            StudioPreviewVariant("dark", "Dark"),
        )

        cache.write(result(selection, selectedVariantId = "light", variants = variants))
        cache.write(result(selection, selectedVariantId = "dark", variants = variants))

        val items = cache.readAll(listOf(selection))
        assertEquals(listOf("light", "dark"), items.map(PreviewGalleryItem::variantId))
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

        assertTrue(cache.readAll(listOf(selection("One"))).isEmpty())
        assertEquals(
            "Two",
            cache.readAll(listOf(selection("Two"))).singleOrNull()?.selection?.symbolName,
        )
        now += 200
        cache.prune()
        assertTrue(cache.readAll(listOf(selection("Two"))).isEmpty())
    }

    private fun result(
        selection: PreviewSourceSelection,
        width: Int = 4,
        height: Int = 6,
        selectedVariantId: String = "default",
        variants: List<StudioPreviewVariant> = listOf(
            StudioPreviewVariant("default", "Default"),
        ),
    ): PreviewRenderOutcome.Success {
        val artifactRoot = temporaryFolder.newFolder().toPath()
        val imagePath = artifactRoot.resolve("preview.png")
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        ImageIO.write(image, "png", imagePath.toFile())
        return PreviewRenderOutcome.Success(
            selection = selection,
            descriptorId = selection.symbolName.lowercase(),
            descriptorName = selection.symbolName,
            variants = variants,
            selectedVariantId = selectedVariantId,
            variantName = variants.single { variant -> variant.id == selectedVariantId }.displayName,
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
