package com.viewcompose.studio.preview

import com.google.gson.Gson
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.io.path.name
import kotlin.math.roundToInt

/**
 * High-cardinality cache for the all-previews gallery.
 *
 * Gallery entries contain a Retina-ready thumbnail, a bounded quick-look image, and source
 * metadata. Render trees and diagnostics stay in [PreviewDiskCache]. The quick-look image is loaded
 * lazily by the UI, while byte and entry limits keep high-cardinality projects bounded.
 */
internal class PreviewGalleryDiskCache(
    cacheRoot: Path,
    private val maxEntries: Int = DEFAULT_GALLERY_MAX_ENTRIES,
    private val maxBytes: Long = DEFAULT_GALLERY_MAX_BYTES,
    private val maxAge: Duration = DEFAULT_GALLERY_MAX_AGE,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val cacheRoot = cacheRoot.toAbsolutePath().normalize()
    private val gson = Gson()
    private var lastPruneMillis: Long? = null

    init {
        require(maxEntries > 0)
        require(maxBytes > 0L)
        require(!maxAge.isNegative && !maxAge.isZero)
    }

    fun readAll(selections: Collection<PreviewSourceSelection>): List<PreviewGalleryItem> =
        synchronized(GALLERY_CACHE_LOCK) {
            pruneIfDue()
            val selectionOrder = selections.withIndex().associate { (index, selection) ->
                selection.galleryCacheIdentity() to (selection to index)
            }
            galleryEntries()
                .mapNotNull { entry ->
                    val (selection, sourceIndex) = selectionOrder[entry.metadata.sourceIdentity]
                        ?: return@mapNotNull null
                    runCatching {
                        val thumbnailPath = entry.directory.resolve(GALLERY_THUMBNAIL_FILE_NAME)
                        val detailImagePath = entry.directory.resolve(GALLERY_DETAIL_IMAGE_FILE_NAME)
                        val thumbnail = loadBoundedPreviewImage(thumbnailPath)
                        require(Files.isRegularFile(detailImagePath))
                        writeMetadata(
                            entry.directory,
                            entry.metadata.copy(lastAccessMillis = nowMillis()),
                        )
                        sourceIndex to PreviewGalleryItem(
                            selection = selection,
                            descriptorName = entry.metadata.descriptorName,
                            variantId = entry.metadata.variantId,
                            variantName = entry.metadata.variantName,
                            variantIndex = entry.metadata.variantIndex,
                            thumbnail = thumbnail,
                            thumbnailPath = thumbnailPath,
                            detailImagePath = detailImagePath,
                            cacheHit = true,
                        )
                    }.getOrElse {
                        deleteGalleryEntry(entry.directory)
                        null
                    }
                }
                .sortedWith(
                    compareBy(
                        { (sourceIndex, _) -> sourceIndex },
                        { (_, item) -> item.variantIndex },
                        { (_, item) -> item.variantId },
                    ),
                )
                .map { (_, item) -> item }
        }

    fun write(result: PreviewRenderOutcome.Success): PreviewGalleryItem =
        synchronized(GALLERY_CACHE_LOCK) {
        Files.createDirectories(cacheRoot)
        val identity = result.selection.galleryCacheIdentity()
        val destination = cacheRoot.resolve(
            galleryCacheHash("$identity\u0000${result.selectedVariantId}"),
        )
        val temporary = cacheRoot.resolve(".tmp-${UUID.randomUUID()}")
        val thumbnail = result.image.toGalleryThumbnail()
        val detailImage = result.image.toGalleryDetailImage()
        val item = PreviewGalleryItem(
            selection = result.selection,
            descriptorName = result.descriptorName,
            variantId = result.selectedVariantId,
            variantName = result.variantName,
            variantIndex = result.variants.indexOfFirst { variant ->
                variant.id == result.selectedVariantId
            }.coerceAtLeast(0),
            thumbnail = thumbnail,
            thumbnailPath = destination.resolve(GALLERY_THUMBNAIL_FILE_NAME),
            detailImagePath = destination.resolve(GALLERY_DETAIL_IMAGE_FILE_NAME),
            cacheHit = result.cacheHit,
        )
        runCatching {
            Files.createDirectories(temporary)
            ImageIO.write(
                thumbnail,
                "png",
                temporary.resolve(GALLERY_THUMBNAIL_FILE_NAME).toFile(),
            )
            ImageIO.write(
                detailImage,
                "png",
                temporary.resolve(GALLERY_DETAIL_IMAGE_FILE_NAME).toFile(),
            )
            val timestamp = nowMillis()
            writeMetadata(
                temporary,
                GalleryCacheMetadata(
                    schemaVersion = GALLERY_CACHE_SCHEMA_VERSION,
                    sourceIdentity = identity,
                    descriptorName = result.descriptorName,
                    variantId = result.selectedVariantId,
                    variantName = result.variantName,
                    variantIndex = item.variantIndex,
                    createdAtMillis = timestamp,
                    lastAccessMillis = timestamp,
                ),
            )
            deleteGalleryEntry(destination)
            moveGalleryEntry(temporary, destination)
        }.onFailure {
            deleteGalleryEntry(temporary)
        }.getOrThrow()
        if (galleryEntryCount() > maxEntries) {
            prune()
        } else {
            pruneIfDue()
        }
        item
    }

    fun prune() = synchronized(GALLERY_CACHE_LOCK) {
        lastPruneMillis = nowMillis()
        if (!Files.isDirectory(cacheRoot)) return@synchronized
        Files.list(cacheRoot).use { children ->
            children
                .filter { path -> path.name.startsWith(".tmp-") }
                .forEach(::deleteGalleryEntry)
        }
        val oldestAllowed = nowMillis() - maxAge.toMillis()
        galleryEntries().filter { entry ->
            entry.metadata.lastAccessMillis < oldestAllowed
        }.forEach { entry ->
            deleteGalleryEntry(entry.directory)
        }

        galleryEntries()
            .sortedByDescending { entry -> entry.metadata.lastAccessMillis }
            .drop(maxEntries)
            .forEach { entry -> deleteGalleryEntry(entry.directory) }

        var retainedBytes = 0L
        galleryEntries()
            .sortedByDescending { entry -> entry.metadata.lastAccessMillis }
            .forEach { entry ->
                val size = galleryDirectorySize(entry.directory)
                if (retainedBytes + size > maxBytes) {
                    deleteGalleryEntry(entry.directory)
                } else {
                    retainedBytes += size
                }
            }
    }

    private fun pruneIfDue() {
        val now = nowMillis()
        val last = lastPruneMillis
        if (last == null || now - last >= GALLERY_PRUNE_INTERVAL_MILLIS) {
            prune()
        }
    }

    private fun galleryEntryCount(): Int {
        if (!Files.isDirectory(cacheRoot)) return 0
        return Files.list(cacheRoot).use { children ->
            children
                .filter(Files::isDirectory)
                .filter { path -> !path.name.startsWith(".tmp-") }
                .limit((maxEntries + 1).toLong())
                .count()
                .toInt()
        }
    }

    private fun galleryEntries(): List<GalleryCacheEntry> {
        if (!Files.isDirectory(cacheRoot)) return emptyList()
        return Files.list(cacheRoot).use { children ->
            children
                .filter(Files::isDirectory)
                .filter { path -> !path.name.startsWith(".tmp-") }
                .map { directory ->
                    runCatching {
                        GalleryCacheEntry(directory, readMetadata(directory))
                    }.getOrElse {
                        deleteGalleryEntry(directory)
                        null
                    }
                }
                .filter { entry -> entry != null }
                .map { entry -> checkNotNull(entry) }
                .toList()
        }
    }

    private fun readMetadata(directory: Path): GalleryCacheMetadata {
        val path = directory.resolve(GALLERY_METADATA_FILE_NAME)
        require(Files.isRegularFile(path))
        require(Files.size(path) in 1..MAXIMUM_GALLERY_METADATA_BYTES)
        val metadata = gson.fromJson(Files.readString(path), GalleryCacheMetadata::class.java)
        require(metadata.schemaVersion == GALLERY_CACHE_SCHEMA_VERSION)
        require(metadata.sourceIdentity.isNotBlank())
        require(metadata.descriptorName.isNotBlank())
        require(metadata.variantId.isNotBlank())
        require(metadata.variantIndex >= 0)
        return metadata
    }

    private fun writeMetadata(
        directory: Path,
        metadata: GalleryCacheMetadata,
    ) {
        Files.writeString(
            directory.resolve(GALLERY_METADATA_FILE_NAME),
            gson.toJson(metadata),
        )
    }
}

private data class GalleryCacheEntry(
    val directory: Path,
    val metadata: GalleryCacheMetadata,
)

private data class GalleryCacheMetadata(
    val schemaVersion: Int,
    val sourceIdentity: String,
    val descriptorName: String,
    val variantId: String,
    val variantName: String,
    val variantIndex: Int,
    val createdAtMillis: Long,
    val lastAccessMillis: Long,
)

private fun PreviewSourceSelection.galleryCacheIdentity(): String {
    val normalizedPath = runCatching {
        Path.of(filePath).toAbsolutePath().normalize().toString()
    }.getOrDefault(filePath)
    return "$normalizedPath\u0000$symbolName"
}

private fun galleryCacheHash(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

/** In-memory fallback used when the gallery cache is unavailable or cannot be written. */
internal fun PreviewRenderOutcome.Success.toBoundedGalleryItem(): PreviewGalleryItem {
    return PreviewGalleryItem(
        selection = selection,
        descriptorName = descriptorName,
        variantId = selectedVariantId,
        variantName = variantName,
        variantIndex = variants.indexOfFirst { variant ->
            variant.id == selectedVariantId
        }.coerceAtLeast(0),
        thumbnail = image.toGalleryThumbnail(),
        thumbnailPath = imagePath,
        detailImagePath = imagePath,
        cacheHit = cacheHit,
    )
}

private fun BufferedImage.toGalleryThumbnail(): BufferedImage {
    return scaleToGalleryBounds(
        maximumWidth = GALLERY_THUMBNAIL_MAX_WIDTH,
        maximumHeight = GALLERY_THUMBNAIL_MAX_HEIGHT,
    )
}

private fun BufferedImage.toGalleryDetailImage(): BufferedImage {
    return scaleToGalleryBounds(
        maximumWidth = GALLERY_DETAIL_MAX_WIDTH,
        maximumHeight = GALLERY_DETAIL_MAX_HEIGHT,
    )
}

private fun BufferedImage.scaleToGalleryBounds(
    maximumWidth: Int,
    maximumHeight: Int,
): BufferedImage {
    val scale = minOf(
        maximumWidth.toDouble() / width,
        maximumHeight.toDouble() / height,
        1.0,
    )
    if (scale >= 1.0) return this
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val scaled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = scaled.createGraphics()
    try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC,
        )
        graphics.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY,
        )
        graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
    } finally {
        graphics.dispose()
    }
    return scaled
}

private fun moveGalleryEntry(
    source: Path,
    destination: Path,
) {
    try {
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, destination)
    }
}

private fun galleryDirectorySize(directory: Path): Long {
    return runCatching {
        Files.walk(directory).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .mapToLong(Files::size)
                .sum()
        }
    }.getOrDefault(Long.MAX_VALUE)
}

private fun deleteGalleryEntry(path: Path) {
    if (!Files.exists(path)) return
    runCatching {
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}

private const val GALLERY_CACHE_SCHEMA_VERSION = 6
private const val GALLERY_METADATA_FILE_NAME = "metadata.json"
private const val GALLERY_THUMBNAIL_FILE_NAME = "thumbnail.png"
private const val GALLERY_DETAIL_IMAGE_FILE_NAME = "detail.png"
private const val MAXIMUM_GALLERY_METADATA_BYTES = 256L * 1024L
private const val GALLERY_THUMBNAIL_MAX_WIDTH = 480
private const val GALLERY_THUMBNAIL_MAX_HEIGHT = 720
private const val GALLERY_DETAIL_MAX_WIDTH = 1_080
private const val GALLERY_DETAIL_MAX_HEIGHT = 4_096
private const val DEFAULT_GALLERY_MAX_ENTRIES = 1024
private const val DEFAULT_GALLERY_MAX_BYTES = 128L * 1024L * 1024L
private const val GALLERY_PRUNE_INTERVAL_MILLIS = 60L * 1000L
private val DEFAULT_GALLERY_MAX_AGE: Duration = Duration.ofDays(30)
private val GALLERY_CACHE_LOCK = Any()
