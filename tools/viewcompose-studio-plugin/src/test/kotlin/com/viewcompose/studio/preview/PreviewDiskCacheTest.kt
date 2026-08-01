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

class PreviewDiskCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `restores the most recently used variant without invoking Gradle`() {
        val root = temporaryFolder.newFolder("cache").toPath()
        var now = 1_000L
        val cache = PreviewDiskCache(root, nowMillis = { now })
        val selection = selection("SampleCard")
        cache.write(result(selection, "light"))
        now += 1
        cache.write(result(selection, "dark"))

        val restored = cache.read(selection, requestedVariantId = null)

        assertEquals("dark", restored?.selectedVariantId)
        assertEquals(3, restored?.image?.height)
        assertTrue(restored?.cacheHit == true)
    }

    @Test
    fun `requested variant is restored independently`() {
        val root = temporaryFolder.newFolder("variant-cache").toPath()
        val cache = PreviewDiskCache(root)
        val selection = selection("SampleCard")
        cache.write(result(selection, "light"))
        cache.write(result(selection, "dark"))

        assertEquals("light", cache.read(selection, "light")?.selectedVariantId)
        assertEquals("dark", cache.read(selection, "dark")?.selectedVariantId)
    }

    @Test
    fun `entry count and age are bounded`() {
        val root = temporaryFolder.newFolder("bounded-cache").toPath()
        var now = 1_000L
        val cache = PreviewDiskCache(
            cacheRoot = root,
            maxEntries = 2,
            maxAge = Duration.ofMillis(100),
            nowMillis = { now },
        )
        cache.write(result(selection("One"), "default"))
        now += 10
        cache.write(result(selection("Two"), "default"))
        now += 10
        cache.write(result(selection("Three"), "default"))

        assertNull(cache.read(selection("One"), null))
        assertEquals("Two", cache.read(selection("Two"), null)?.selection?.symbolName)
        now += 200
        cache.prune()
        assertNull(cache.read(selection("Two"), null))
        assertNull(cache.read(selection("Three"), null))
    }

    @Test
    fun `total cache bytes are bounded`() {
        val root = temporaryFolder.newFolder("byte-bounded-cache").toPath()
        val cache = PreviewDiskCache(
            cacheRoot = root,
            maxBytes = 1,
        )

        cache.write(result(selection("TooLarge"), "default"))

        assertNull(cache.read(selection("TooLarge"), null))
    }

    private fun result(
        selection: PreviewSourceSelection,
        variantId: String,
    ): PreviewRenderOutcome.Success {
        val artifactRoot = temporaryFolder.newFolder().toPath()
        val image = artifactRoot.resolve("preview.png")
        ImageIO.write(
            BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB),
            "png",
            image.toFile(),
        )
        val snapshot = artifactRoot.resolve("render-tree.json")
        Files.writeString(snapshot, "{}")
        return PreviewRenderOutcome.Success(
            selection = selection,
            descriptorId = selection.symbolName.lowercase(),
            descriptorName = selection.symbolName,
            variants = listOf(
                StudioPreviewVariant(variantId, variantId.replaceFirstChar(Char::uppercase)),
            ),
            selectedVariantId = variantId,
            variantName = variantId.replaceFirstChar(Char::uppercase),
            image = BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB),
            imagePath = image,
            renderTreePath = snapshot,
            renderSnapshot = StudioPreviewProtocolReader.readRenderSnapshot(snapshot),
            diagnostics = emptyList(),
            durationMillis = 12,
            cacheHit = false,
        )
    }

    private fun selection(symbol: String): PreviewSourceSelection {
        return PreviewSourceSelection(
            filePath = Path.of("/project/$symbol.kt").toString(),
            symbolName = symbol,
            line = 10,
        )
    }
}
