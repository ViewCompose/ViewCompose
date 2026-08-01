package com.viewcompose.studio.preview

import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.LinkedHashMap

/** Access-ordered image cache bounded by decoded pixel memory rather than compressed file size. */
internal class PreviewImageMemoryCache(
    private val maxBytes: Long,
) : AutoCloseable {
    private val images = LinkedHashMap<Path, BufferedImage>(8, 0.75f, true)
    private var retainedBytes = 0L

    init {
        require(maxBytes > 0L) { "Preview image memory limit must be positive." }
    }

    @Synchronized
    operator fun get(path: Path): BufferedImage? = images[path]

    @Synchronized
    fun put(
        path: Path,
        image: BufferedImage,
    ) {
        val imageBytes = image.estimatedHeapBytes()
        val previous = images.remove(path)
        if (previous != null) {
            retainedBytes -= previous.estimatedHeapBytes()
            if (previous !== image) previous.flush()
        }
        if (imageBytes > maxBytes) return
        images[path] = image
        retainedBytes += imageBytes
        evictToLimit()
    }

    @Synchronized
    fun retainedBytes(): Long = retainedBytes

    @Synchronized
    fun entryCount(): Int = images.size

    @Synchronized
    fun clear() {
        images.values.forEach(BufferedImage::flush)
        images.clear()
        retainedBytes = 0L
    }

    override fun close() = clear()

    private fun evictToLimit() {
        val iterator = images.entries.iterator()
        while (retainedBytes > maxBytes && iterator.hasNext()) {
            val image = iterator.next().value
            iterator.remove()
            retainedBytes -= image.estimatedHeapBytes()
            image.flush()
        }
    }
}

internal fun BufferedImage.estimatedHeapBytes(): Long {
    return width.toLong() * height.toLong() * ESTIMATED_PREVIEW_BYTES_PER_PIXEL
}

private const val ESTIMATED_PREVIEW_BYTES_PER_PIXEL = 4L
