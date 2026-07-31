package com.viewcompose.studio.preview

import java.awt.image.BufferedImage
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PreviewImageMemoryCacheTest {
    @Test
    fun `evicts least recently used images by decoded bytes`() {
        val cache = PreviewImageMemoryCache(maxBytes = 64)
        val first = image(width = 4, height = 2)
        val second = image(width = 4, height = 2)
        val third = image(width = 4, height = 2)

        cache.put(Path.of("first.png"), first)
        cache.put(Path.of("second.png"), second)
        assertSame(first, cache[Path.of("first.png")])
        cache.put(Path.of("third.png"), third)

        assertSame(first, cache[Path.of("first.png")])
        assertNull(cache[Path.of("second.png")])
        assertSame(third, cache[Path.of("third.png")])
        assertEquals(2, cache.entryCount())
        assertEquals(64L, cache.retainedBytes())
    }

    @Test
    fun `does not retain one image larger than the whole budget`() {
        val cache = PreviewImageMemoryCache(maxBytes = 32)

        cache.put(Path.of("large.png"), image(width = 4, height = 4))

        assertEquals(0, cache.entryCount())
        assertEquals(0L, cache.retainedBytes())
    }

    private fun image(
        width: Int,
        height: Int,
    ): BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
}
