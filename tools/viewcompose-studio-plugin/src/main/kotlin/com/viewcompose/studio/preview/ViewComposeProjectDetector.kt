package com.viewcompose.studio.preview

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal enum class ViewComposeProjectEvidenceKind {
    GradlePlugin,
    DescriptorCatalog,
}

internal data class ViewComposeProjectDetection(
    val isViewComposeProject: Boolean,
    val evidenceKind: ViewComposeProjectEvidenceKind? = null,
    val evidencePath: Path? = null,
)

/**
 * Performs bounded file-system detection without resolving Gradle models or Android plugin APIs.
 */
internal class ViewComposeProjectDetector(
    private val maximumDepth: Int = DEFAULT_MAXIMUM_DEPTH,
    private val maximumVisitedFiles: Int = DEFAULT_MAXIMUM_VISITED_FILES,
) {
    fun detect(projectRoot: Path?): ViewComposeProjectDetection {
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            return ViewComposeProjectDetection(isViewComposeProject = false)
        }
        var result: ViewComposeProjectDetection? = null
        var visitedFiles = 0
        try {
            Files.walkFileTree(
                projectRoot,
                emptySet(),
                maximumDepth,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        directory: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        if (directory != projectRoot && directory.fileName.toString() in EXCLUDED_DIRECTORIES) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        if (directory.fileName.toString() == BUILD_DIRECTORY_NAME) {
                            findDescriptorCatalog(directory)?.let { catalog ->
                                result = ViewComposeProjectDetection(
                                    isViewComposeProject = true,
                                    evidenceKind = ViewComposeProjectEvidenceKind.DescriptorCatalog,
                                    evidencePath = catalog,
                                )
                                return FileVisitResult.TERMINATE
                            }
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        visitedFiles += 1
                        if (visitedFiles > maximumVisitedFiles) {
                            return FileVisitResult.TERMINATE
                        }
                        if (file.fileName.toString() !in GRADLE_MARKER_FILES) {
                            return FileVisitResult.CONTINUE
                        }
                        if (containsViewComposePluginMarker(file)) {
                            result = ViewComposeProjectDetection(
                                isViewComposeProject = true,
                                evidenceKind = ViewComposeProjectEvidenceKind.GradlePlugin,
                                evidencePath = file,
                            )
                            return FileVisitResult.TERMINATE
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (_: IOException) {
            return result ?: ViewComposeProjectDetection(isViewComposeProject = false)
        }
        return result ?: ViewComposeProjectDetection(isViewComposeProject = false)
    }

    private fun findDescriptorCatalog(buildDirectory: Path): Path? {
        val previewRoot = buildDirectory.resolve(VIEWCOMPOSE_PREVIEW_DIRECTORY_NAME)
        if (!Files.isDirectory(previewRoot)) return null
        return try {
            Files.walk(previewRoot, DESCRIPTOR_SCAN_DEPTH).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter { path -> path.fileName.toString() == DESCRIPTOR_FILE_NAME }
                    .sorted()
                    .findFirst()
                    .orElse(null)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun containsViewComposePluginMarker(file: Path): Boolean {
        return try {
            if (Files.size(file) > MAXIMUM_MARKER_FILE_BYTES) return false
            val content = Files.readString(file, StandardCharsets.UTF_8)
            VIEWCOMPOSE_PREVIEW_PLUGIN_ID in content ||
                VIEWCOMPOSE_PREVIEW_PLUGIN_MODULE in content
        } catch (_: IOException) {
            false
        }
    }
}

private val EXCLUDED_DIRECTORIES = setOf(
    ".git",
    ".gradle",
    ".idea",
    ".kotlin",
    "node_modules",
)
private val GRADLE_MARKER_FILES = setOf(
    "build.gradle",
    "build.gradle.kts",
    "settings.gradle",
    "settings.gradle.kts",
    "libs.versions.toml",
)
private const val VIEWCOMPOSE_PREVIEW_PLUGIN_ID = "com.viewcompose.preview"
private const val VIEWCOMPOSE_PREVIEW_PLUGIN_MODULE = "viewcompose-preview-gradle-plugin"
private const val VIEWCOMPOSE_PREVIEW_DIRECTORY_NAME = "viewcompose-preview"
private const val BUILD_DIRECTORY_NAME = "build"
private const val DESCRIPTOR_FILE_NAME = "descriptors.json"
private const val DESCRIPTOR_SCAN_DEPTH = 4
private const val DEFAULT_MAXIMUM_DEPTH = 8
private const val DEFAULT_MAXIMUM_VISITED_FILES = 10_000
private const val MAXIMUM_MARKER_FILE_BYTES = 1_048_576L
