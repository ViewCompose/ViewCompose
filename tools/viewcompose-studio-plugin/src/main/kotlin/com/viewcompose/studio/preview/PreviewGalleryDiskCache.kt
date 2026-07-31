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
 * Gallery entries deliberately contain only a bounded thumbnail and source metadata. Full images,
 * render trees, and diagnostics stay in [PreviewDiskCache], so a large gallery cannot evict the
 * detailed previews the user recently inspected.
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

    fun read(selection: PreviewSourceSelection): PreviewGalleryItem? =
        synchronized(GALLERY_CACHE_LOCK) {
            pruneIfDue()
            val identity = selection.galleryCacheIdentity()
            val directory = cacheRoot.resolve(galleryCacheHash(identity))
            runCatching {
                val metadata = readMetadata(directory)
                require(metadata.sourceIdentity == identity)
                val imagePath = directory.resolve(GALLERY_IMAGE_FILE_NAME)
                val image = loadBoundedPreviewImage(imagePath)
                writeMetadata(
                    directory,
                    metadata.copy(lastAccessMillis = nowMillis()),
                )
                PreviewGalleryItem(
                    selection = selection,
                    descriptorName = metadata.descriptorName,
                    variantName = metadata.variantName,
                    image = image,
                    imagePath = imagePath,
                    cacheHit = true,
                )
            }.getOrElse {
                deleteGalleryEntry(directory)
                null
            }
        }

    fun write(result: PreviewRenderOutcome.Success): PreviewGalleryItem =
        synchronized(GALLERY_CACHE_LOCK) {
        Files.createDirectories(cacheRoot)
        val identity = result.selection.galleryCacheIdentity()
        val destination = cacheRoot.resolve(galleryCacheHash(identity))
        val temporary = cacheRoot.resolve(".tmp-${UUID.randomUUID()}")
        val item = result.toBoundedGalleryItem(
            imagePath = destination.resolve(GALLERY_IMAGE_FILE_NAME),
        )
        runCatching {
            Files.createDirectories(temporary)
            ImageIO.write(
                item.image,
                "png",
                temporary.resolve(GALLERY_IMAGE_FILE_NAME).toFile(),
            )
            val timestamp = nowMillis()
            writeMetadata(
                temporary,
                GalleryCacheMetadata(
                    schemaVersion = GALLERY_CACHE_SCHEMA_VERSION,
                    sourceIdentity = identity,
                    descriptorName = result.descriptorName,
                    variantName = result.variantName,
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
    val variantName: String,
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

internal fun PreviewRenderOutcome.Success.toBoundedGalleryItem(
    imagePath: Path = this.imagePath,
): PreviewGalleryItem {
    return PreviewGalleryItem(
        selection = selection,
        descriptorName = descriptorName,
        variantName = variantName,
        image = image.toGalleryThumbnail(),
        imagePath = imagePath,
        cacheHit = cacheHit,
    )
}

private fun BufferedImage.toGalleryThumbnail(): BufferedImage {
    val scale = minOf(
        GALLERY_THUMBNAIL_MAX_WIDTH.toDouble() / width,
        GALLERY_THUMBNAIL_MAX_HEIGHT.toDouble() / height,
        1.0,
    )
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val thumbnail = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = thumbnail.createGraphics()
    try {
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        graphics.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY,
        )
        graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
    } finally {
        graphics.dispose()
    }
    return thumbnail
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

private const val GALLERY_CACHE_SCHEMA_VERSION = 1
private const val GALLERY_METADATA_FILE_NAME = "metadata.json"
private const val GALLERY_IMAGE_FILE_NAME = "thumbnail.png"
private const val MAXIMUM_GALLERY_METADATA_BYTES = 256L * 1024L
private const val GALLERY_THUMBNAIL_MAX_WIDTH = 240
private const val GALLERY_THUMBNAIL_MAX_HEIGHT = 360
private const val DEFAULT_GALLERY_MAX_ENTRIES = 1024
private const val DEFAULT_GALLERY_MAX_BYTES = 128L * 1024L * 1024L
private const val GALLERY_PRUNE_INTERVAL_MILLIS = 60L * 1000L
private val DEFAULT_GALLERY_MAX_AGE: Duration = Duration.ofDays(30)
private val GALLERY_CACHE_LOCK = Any()
