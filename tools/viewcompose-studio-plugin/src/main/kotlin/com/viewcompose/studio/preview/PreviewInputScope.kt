package com.viewcompose.studio.preview

import java.nio.file.Files
import java.nio.file.Path

/** Build-manifest-derived input boundary used to avoid unrelated Ctrl+S preview rebuilds. */
internal data class PreviewInputScope(
    val projectRoot: String?,
    val moduleRoots: Set<String>,
    val directoryRoots: Set<String>,
) {
    fun matches(
        selection: PreviewSourceSelection,
        changedPaths: List<String>,
    ): Boolean {
        val selectedPath = selection.filePath.normalizedPathStringOrNull() ?: return false
        val root = projectRoot?.let(Path::of)
        val modules = moduleRoots.mapTo(linkedSetOf(), Path::of)
        val directories = directoryRoots.mapTo(linkedSetOf(), Path::of)
        return changedPaths.any { rawPath ->
            val path = rawPath.normalizedPathOrNull() ?: return@any false
            if (path.toString() == selectedPath) return@any true
            if (root != null && path.isProjectWidePreviewConfiguration(root)) return@any true
            if (path.isIgnoredGeneratedPath(root)) return@any false
            if (directories.any(path::startsWith)) return@any true
            val extension = path.previewInputExtension()
            if (extension !in PREVIEW_INPUT_EXTENSIONS) return@any false
            modules.any(path::startsWith)
        }
    }

    companion object {
        fun create(
            projectRoot: Path,
            moduleRoot: Path,
            manifestInputPaths: List<String>,
        ): PreviewInputScope {
            val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()
            val normalizedModuleRoot = moduleRoot.toAbsolutePath().normalize()
            val modules = linkedSetOf(normalizedModuleRoot)
            val directories = linkedSetOf<Path>()
            manifestInputPaths.forEach { rawPath ->
                val input = rawPath.normalizedPathOrNull() ?: return@forEach
                if (!input.startsWith(normalizedProjectRoot)) return@forEach
                val relative = normalizedProjectRoot.relativize(input)
                val buildIndex = relative.indexOfFirst { segment -> segment.toString() == "build" }
                if (buildIndex > 0) {
                    modules.add(normalizedProjectRoot.resolve(relative.subpath(0, buildIndex)))
                } else if (Files.isDirectory(input)) {
                    directories.add(input)
                }
            }
            return PreviewInputScope(
                projectRoot = normalizedProjectRoot.toString(),
                moduleRoots = modules.mapTo(linkedSetOf(), Path::toString),
                directoryRoots = directories.mapTo(linkedSetOf(), Path::toString),
            )
        }

        fun forSelection(
            projectRoot: Path?,
            selection: PreviewSourceSelection,
        ): PreviewInputScope? {
            val root = projectRoot?.toAbsolutePath()?.normalize() ?: return null
            val source = selection.filePath.normalizedPathOrNull() ?: return null
            val moduleRoot = generateSequence(source.parent) { parent -> parent.parent }
                .takeWhile { directory -> directory.startsWith(root) }
                .firstOrNull { directory ->
                    Files.isRegularFile(directory.resolve("build.gradle.kts")) ||
                        Files.isRegularFile(directory.resolve("build.gradle"))
                }
                ?: return null
            return create(root, moduleRoot, emptyList())
        }
    }
}

private fun Path.isProjectWidePreviewConfiguration(projectRoot: Path): Boolean {
    if (!startsWith(projectRoot)) return false
    val relative = projectRoot.relativize(this)
    if (relative.nameCount == 0) return false
    val normalized = relative.joinToString("/") { segment -> segment.toString() }
    return normalized in PROJECT_WIDE_PREVIEW_FILES || normalized.startsWith("gradle/")
}

private fun Path.isIgnoredGeneratedPath(projectRoot: Path?): Boolean {
    val relative = if (projectRoot != null && startsWith(projectRoot)) {
        projectRoot.relativize(this)
    } else {
        this
    }
    return relative.any { segment -> segment.toString() in IGNORED_INPUT_DIRECTORIES }
}

private fun Path.previewInputExtension(): String {
    return fileName?.toString()
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        .orEmpty()
}

private fun String.normalizedPathStringOrNull(): String? = normalizedPathOrNull()?.toString()

internal fun String.normalizedPathOrNull(): Path? {
    return runCatching { Path.of(this).toAbsolutePath().normalize() }.getOrNull()
}

internal val IGNORED_INPUT_DIRECTORIES = setOf(
    ".git",
    ".gradle",
    ".idea",
    "build",
    "out",
)

internal val PREVIEW_INPUT_EXTENSIONS = setOf(
    "aar", "gradle", "gif", "jar", "java", "jpeg", "jpg", "json", "kt", "kts", "otf", "png",
    "properties", "svg", "toml", "ttf", "webp", "xml",
)

private val PROJECT_WIDE_PREVIEW_FILES = setOf(
    "build.gradle",
    "build.gradle.kts",
    "gradle.properties",
    "settings.gradle",
    "settings.gradle.kts",
)
