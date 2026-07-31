package com.viewcompose.studio.preview

import com.google.gson.Gson
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import kotlin.io.path.name

/**
 * Studio-owned preview cache.
 *
 * Gradle's render cache is build-fingerprint based and may disappear after a clean. This cache is
 * intentionally independent: once a preview was shown in Studio it can be restored immediately
 * until the user explicitly refreshes it or the bounded retention policy evicts it.
 */
internal class PreviewDiskCache(
    cacheRoot: Path,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxAge: Duration = DEFAULT_MAX_AGE,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val cacheRoot = cacheRoot.toAbsolutePath().normalize()
    private val gson = Gson()

    init {
        require(maxEntries > 0) { "Preview cache entry limit must be positive." }
        require(maxBytes > 0L) { "Preview cache byte limit must be positive." }
        require(!maxAge.isNegative && !maxAge.isZero) {
            "Preview cache maximum age must be positive."
        }
    }

    fun read(
        selection: PreviewSourceSelection,
        requestedVariantId: String?,
    ): PreviewRenderOutcome.Success? = synchronized(GLOBAL_CACHE_LOCK) {
        prune()
        val identity = selection.cacheIdentity()
        val candidate = entries()
            .filter { entry ->
                entry.metadata.sourceIdentity == identity &&
                    (
                        requestedVariantId == null ||
                            entry.metadata.selectedVariantId == requestedVariantId
                    )
            }
            .maxByOrNull { entry -> entry.metadata.lastAccessMillis }
            ?: return null
        return runCatching {
            val imagePath = candidate.directory.resolve(IMAGE_FILE_NAME)
            val snapshotPath = candidate.directory
                .resolve(RENDER_SNAPSHOT_FILE_NAME)
                .takeIf(Files::isRegularFile)
            val accessTime = nowMillis()
            val metadata = candidate.metadata.copy(lastAccessMillis = accessTime)
            writeMetadata(candidate.directory, metadata)
            PreviewRenderOutcome.Success(
                selection = selection,
                descriptorId = metadata.descriptorId,
                descriptorName = metadata.descriptorName,
                variants = metadata.variants,
                selectedVariantId = metadata.selectedVariantId,
                variantName = metadata.variantName,
                image = loadBoundedPreviewImage(imagePath),
                imagePath = imagePath,
                renderTreePath = snapshotPath,
                renderSnapshot = snapshotPath?.let(StudioPreviewProtocolReader::readRenderSnapshot),
                diagnostics = metadata.diagnostics,
                durationMillis = metadata.durationMillis,
                cacheHit = true,
            )
        }.getOrElse {
            deleteRecursively(candidate.directory)
            null
        }
    }

    fun write(result: PreviewRenderOutcome.Success) = synchronized(GLOBAL_CACHE_LOCK) {
        Files.createDirectories(cacheRoot)
        val identity = result.selection.cacheIdentity()
        val entryKey = sha256("$identity\u0000${result.selectedVariantId}")
        val destination = cacheRoot.resolve(entryKey)
        val temporary = cacheRoot.resolve(".tmp-${UUID.randomUUID()}")
        runCatching {
            Files.createDirectories(temporary)
            Files.copy(
                result.imagePath,
                temporary.resolve(IMAGE_FILE_NAME),
                StandardCopyOption.REPLACE_EXISTING,
            )
            result.renderTreePath
                ?.takeIf(Files::isRegularFile)
                ?.let { snapshot ->
                    Files.copy(
                        snapshot,
                        temporary.resolve(RENDER_SNAPSHOT_FILE_NAME),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            val timestamp = nowMillis()
            writeMetadata(
                temporary,
                CachedPreviewMetadata(
                    schemaVersion = CACHE_SCHEMA_VERSION,
                    sourceIdentity = identity,
                    descriptorId = result.descriptorId,
                    descriptorName = result.descriptorName,
                    variants = result.variants,
                    selectedVariantId = result.selectedVariantId,
                    variantName = result.variantName,
                    diagnostics = result.diagnostics,
                    durationMillis = result.durationMillis,
                    createdAtMillis = timestamp,
                    lastAccessMillis = timestamp,
                ),
            )
            deleteRecursively(destination)
            moveDirectory(temporary, destination)
        }.onFailure {
            deleteRecursively(temporary)
        }.getOrThrow()
        prune()
    }

    fun prune() = synchronized(GLOBAL_CACHE_LOCK) {
        if (!Files.isDirectory(cacheRoot)) return@synchronized
        Files.list(cacheRoot).use { children ->
            children
                .filter { path -> path.name.startsWith(".tmp-") }
                .forEach(::deleteRecursively)
        }
        val oldestAllowed = nowMillis() - maxAge.toMillis()
        entries().filter { entry ->
            entry.metadata.lastAccessMillis < oldestAllowed
        }.forEach { entry ->
            deleteRecursively(entry.directory)
        }

        val retained = entries().sortedByDescending { entry ->
            entry.metadata.lastAccessMillis
        }
        retained.drop(maxEntries).forEach { entry ->
            deleteRecursively(entry.directory)
        }

        var retainedBytes = 0L
        entries()
            .sortedByDescending { entry -> entry.metadata.lastAccessMillis }
            .forEach { entry ->
                val size = directorySize(entry.directory)
                if (retainedBytes + size > maxBytes) {
                    deleteRecursively(entry.directory)
                } else {
                    retainedBytes += size
                }
            }
    }

    private fun entries(): List<CachedPreviewEntry> {
        if (!Files.isDirectory(cacheRoot)) return emptyList()
        return Files.list(cacheRoot).use { children ->
            children
                .filter(Files::isDirectory)
                .filter { path -> !path.name.startsWith(".tmp-") }
                .map { directory ->
                    runCatching {
                        CachedPreviewEntry(
                            directory = directory,
                            metadata = readMetadata(directory),
                        )
                    }.getOrElse {
                        deleteRecursively(directory)
                        null
                    }
                }
                .filter { entry -> entry != null }
                .map { entry -> checkNotNull(entry) }
                .toList()
        }
    }

    private fun readMetadata(directory: Path): CachedPreviewMetadata {
        val path = directory.resolve(METADATA_FILE_NAME)
        require(Files.isRegularFile(path)) { "Preview cache metadata is missing." }
        require(Files.size(path) in 1..MAXIMUM_METADATA_BYTES) {
            "Preview cache metadata has an unsupported size."
        }
        val metadata = gson.fromJson(
            Files.readString(path),
            CachedPreviewMetadata::class.java,
        )
        require(metadata.schemaVersion == CACHE_SCHEMA_VERSION) {
            "Unsupported preview cache schema ${metadata.schemaVersion}."
        }
        require(metadata.sourceIdentity.isNotBlank())
        require(metadata.descriptorId.isNotBlank())
        require(metadata.descriptorName.isNotBlank())
        require(metadata.selectedVariantId.isNotBlank())
        return metadata
    }

    private fun writeMetadata(
        directory: Path,
        metadata: CachedPreviewMetadata,
    ) {
        Files.writeString(
            directory.resolve(METADATA_FILE_NAME),
            gson.toJson(metadata),
        )
    }
}

internal fun previewCacheRoot(
    ideSystemPath: Path,
): Path {
    return ideSystemPath
        .toAbsolutePath()
        .normalize()
        .resolve("viewcompose-preview")
}

private data class CachedPreviewEntry(
    val directory: Path,
    val metadata: CachedPreviewMetadata,
)

private data class CachedPreviewMetadata(
    val schemaVersion: Int,
    val sourceIdentity: String,
    val descriptorId: String,
    val descriptorName: String,
    val variants: List<StudioPreviewVariant>,
    val selectedVariantId: String,
    val variantName: String,
    val diagnostics: List<StudioPreviewDiagnostic>,
    val durationMillis: Long?,
    val createdAtMillis: Long,
    val lastAccessMillis: Long,
)

private fun PreviewSourceSelection.cacheIdentity(): String {
    val normalizedPath = runCatching {
        Path.of(filePath).toAbsolutePath().normalize().toString()
    }.getOrDefault(filePath)
    return "$normalizedPath\u0000$symbolName"
}

private fun sha256(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun moveDirectory(
    source: Path,
    destination: Path,
) {
    try {
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, destination)
    }
}

private fun directorySize(directory: Path): Long {
    return runCatching {
        Files.walk(directory).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .mapToLong(Files::size)
                .sum()
        }
    }.getOrDefault(Long.MAX_VALUE)
}

private fun deleteRecursively(path: Path) {
    if (!Files.exists(path)) return
    runCatching {
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}

private const val CACHE_SCHEMA_VERSION = 1
private const val METADATA_FILE_NAME = "metadata.json"
private const val IMAGE_FILE_NAME = "preview.png"
private const val RENDER_SNAPSHOT_FILE_NAME = "render-snapshot.json"
private const val MAXIMUM_METADATA_BYTES = 2L * 1024L * 1024L
private const val DEFAULT_MAX_ENTRIES = 64
private const val DEFAULT_MAX_BYTES = 256L * 1024L * 1024L
private val DEFAULT_MAX_AGE: Duration = Duration.ofDays(30)
private val GLOBAL_CACHE_LOCK = Any()
