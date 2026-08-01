package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.PreviewBuildInput
import com.viewcompose.preview.tooling.PreviewBuildInputKind
import com.viewcompose.preview.tooling.PreviewBuildManifest
import com.viewcompose.preview.tooling.PreviewDescriptorCatalog
import java.io.File
import java.util.Properties

/**
 * Rebuilds only the volatile part of a previously discovered preview target.
 *
 * A saved Kotlin/Java source still goes through the Android variant compiler, but resources,
 * dependency resolution and the full discovery task remain represented by the last complete
 * manifest. Current project bytecode is rescanned so signature and annotation changes fail closed
 * instead of rendering a stale descriptor.
 */
internal fun prepareFastPreviewRefresh(
    baselineManifest: PreviewBuildManifest,
    baselineCatalog: PreviewDescriptorCatalog,
    projectClassJars: Collection<File>,
    projectClassDirectories: Collection<File>,
): FastPreviewRefreshInputs {
    require(baselineCatalog.modulePath == baselineManifest.modulePath) {
        "$FAST_REFRESH_FALLBACK_MARKER Preview catalog module does not match its build manifest."
    }
    require(baselineCatalog.buildVariant == baselineManifest.buildVariant) {
        "$FAST_REFRESH_FALLBACK_MARKER Preview catalog variant does not match its build manifest."
    }
    require(baselineCatalog.buildFingerprint == baselineManifest.inputFingerprint) {
        "$FAST_REFRESH_FALLBACK_MARKER Preview catalog is stale relative to its build manifest."
    }

    val classJars = projectClassJars.normalizedExistingFiles()
    val classDirectories = projectClassDirectories.normalizedExistingFiles()
    require(classJars.isNotEmpty() || classDirectories.isNotEmpty()) {
        "$FAST_REFRESH_FALLBACK_MARKER No compiled project classes are available."
    }
    val sourceDirectories = baselineManifest.paths(PreviewBuildInputKind.SourceDirectory)
    val annotationClasspath = baselineManifest.paths(PreviewBuildInputKind.RuntimeClasspath)
    val refreshedFingerprint = PreviewInputFingerprint.combine(
        mapOf(
            "baseline" to baselineManifest.inputFingerprint,
            "project-bytecode" to PreviewInputFingerprint.calculate(
                mapOf(
                    "project-class-directories" to classDirectories,
                    "project-class-jars" to classJars,
                ),
            ),
        ),
    )
    val refreshedInputs = baselineManifest.inputs
        .filterNot { input ->
            input.kind == PreviewBuildInputKind.ProjectClassDirectory ||
                input.kind == PreviewBuildInputKind.ProjectClassJar
        }
        .toMutableList()
        .apply {
            addInput(PreviewBuildInputKind.ProjectClassDirectory, classDirectories)
            addInput(PreviewBuildInputKind.ProjectClassJar, classJars)
        }
        .sortedBy { input -> input.kind.ordinal }
    val discovery = CompiledPreviewScanner(
        projectClassDirectories = classDirectories,
        projectClassJars = classJars,
        annotationClasspath = annotationClasspath,
        sourceDirectories = sourceDirectories,
    ).scan()
    return FastPreviewRefreshInputs(
        manifest = baselineManifest.copy(
            inputs = refreshedInputs,
            inputFingerprint = refreshedFingerprint,
        ),
        catalog = baselineCatalog.copy(
            buildFingerprint = refreshedFingerprint,
            descriptors = discovery.descriptors,
            diagnostics = discovery.diagnostics,
        ),
    )
}

internal data class FastPreviewRefreshInputs(
    val manifest: PreviewBuildManifest,
    val catalog: PreviewDescriptorCatalog,
)

/** Immutable paths resolved by a complete render and reused by source-only refreshes. */
internal data class PreviewRenderToolchain(
    val workerHostClasspath: List<File>,
    val runnerClasspath: List<File>,
    val layoutlibRuntimeArchive: File,
    val layoutlibResourcesArchive: File,
    val renderRuntimeFingerprint: String,
) {
    init {
        require(workerHostClasspath.isNotEmpty()) {
            "Preview worker host classpath must not be empty."
        }
        require(renderRuntimeFingerprint.matches(SHA_256_PATTERN)) {
            "Preview render runtime fingerprint must be SHA-256."
        }
    }

    fun writeTo(file: File) {
        val properties = Properties().apply {
            setProperty("formatVersion", TOOLCHAIN_FORMAT_VERSION)
            setProperty("renderRuntimeFingerprint", renderRuntimeFingerprint)
            setProperty("layoutlibRuntimeArchive", layoutlibRuntimeArchive.normalizedPath())
            setProperty("layoutlibResourcesArchive", layoutlibResourcesArchive.normalizedPath())
            putFileList("workerHostClasspath", workerHostClasspath)
            putFileList("runnerClasspath", runnerClasspath)
        }
        file.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) {
                "Could not create preview toolchain directory '${parent.absolutePath}'."
            }
        }
        val temporary = File(checkNotNull(file.parentFile), "${file.name}.tmp")
        temporary.outputStream().use { output -> properties.store(output, null) }
        if (file.exists()) {
            check(file.delete()) { "Could not replace preview toolchain '${file.absolutePath}'." }
        }
        check(temporary.renameTo(file)) {
            "Could not publish preview toolchain '${file.absolutePath}'."
        }
    }

    companion object {
        fun readFrom(file: File): PreviewRenderToolchain {
            require(file.isFile) {
                "$FAST_REFRESH_FALLBACK_MARKER Preview render toolchain has not been prepared."
            }
            val properties = Properties().apply {
                file.inputStream().use(::load)
            }
            require(properties.getProperty("formatVersion") == TOOLCHAIN_FORMAT_VERSION) {
                "$FAST_REFRESH_FALLBACK_MARKER Preview render toolchain format changed."
            }
            return PreviewRenderToolchain(
                workerHostClasspath = properties.readFileList("workerHostClasspath"),
                runnerClasspath = properties.readFileList("runnerClasspath"),
                layoutlibRuntimeArchive = properties.requiredFile("layoutlibRuntimeArchive"),
                layoutlibResourcesArchive = properties.requiredFile("layoutlibResourcesArchive"),
                renderRuntimeFingerprint = properties.requiredValue("renderRuntimeFingerprint"),
            ).also { toolchain ->
                toolchain.allFiles().forEach { input ->
                    require(input.exists()) {
                        "$FAST_REFRESH_FALLBACK_MARKER Cached preview toolchain input is missing: " +
                            input.absolutePath
                    }
                }
            }
        }
    }
}

private fun PreviewRenderToolchain.allFiles(): List<File> =
    workerHostClasspath + runnerClasspath + layoutlibRuntimeArchive + layoutlibResourcesArchive

private fun MutableList<PreviewBuildInput>.addInput(
    kind: PreviewBuildInputKind,
    files: Collection<File>,
) {
    val paths = files.map(File::normalizedPath).distinct().sorted()
    if (paths.isNotEmpty()) add(PreviewBuildInput(kind = kind, paths = paths))
}

private fun PreviewBuildManifest.paths(kind: PreviewBuildInputKind): List<File> = inputs
    .firstOrNull { input -> input.kind == kind }
    ?.paths
    .orEmpty()
    .map(::File)
    .filter(File::exists)

private fun Collection<File>.normalizedExistingFiles(): List<File> = asSequence()
    .map(File::getAbsoluteFile)
    .map(File::normalize)
    .filter(File::exists)
    .distinctBy(File::getPath)
    .sortedBy(File::getPath)
    .toList()

private fun File.normalizedPath(): String = absoluteFile.normalize().path

private fun Properties.putFileList(key: String, files: List<File>) {
    val normalized = files.map(File::normalizedPath).distinct().sorted()
    setProperty("$key.count", normalized.size.toString())
    normalized.forEachIndexed { index, path -> setProperty("$key.$index", path) }
}

private fun Properties.readFileList(key: String): List<File> {
    val count = requiredValue("$key.count").toIntOrNull()
    require(count != null && count >= 0) {
        "$FAST_REFRESH_FALLBACK_MARKER Invalid cached preview toolchain '$key' count."
    }
    return (0 until count).map { index -> File(requiredValue("$key.$index")) }
}

private fun Properties.requiredFile(key: String): File = File(requiredValue(key))

private fun Properties.requiredValue(key: String): String =
    getProperty(key)?.takeIf(String::isNotBlank)
        ?: error("$FAST_REFRESH_FALLBACK_MARKER Cached preview toolchain is missing '$key'.")

internal const val FAST_REFRESH_FALLBACK_MARKER = "VIEWCOMPOSE_FAST_REFRESH_FALLBACK"
internal const val FAST_BUILD_MANIFEST_FILE_NAME = "fast-build-manifest.json"
internal const val FAST_DESCRIPTOR_CATALOG_FILE_NAME = "fast-descriptors.json"
internal const val RENDER_TOOLCHAIN_FILE_NAME = "render-toolchain.properties"
private const val TOOLCHAIN_FORMAT_VERSION = "1"
private val SHA_256_PATTERN = Regex("[a-f0-9]{64}")
