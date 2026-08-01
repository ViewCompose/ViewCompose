package com.viewcompose.preview.gradle

import java.io.File
import java.util.zip.ZipFile

/**
 * Extracts only generated Android resource symbol classes for the retained worker classpath.
 * Project implementation classes stay on the per-render reloadable classpath.
 */
internal object PreviewResourceSymbolClasspath {
    fun materialize(
        projectClasspath: List<File>,
        artifactRoot: File,
        compatibilityFingerprint: String,
    ): File {
        val destination = artifactRoot
            .resolve("worker")
            .resolve("resource-symbols")
            .resolve(compatibilityFingerprint)
        val marker = destination.resolve(RESOURCE_SYMBOL_MARKER)
        if (marker.isFile && marker.readText() == compatibilityFingerprint) return destination

        val temporary = File(checkNotNull(destination.parentFile), "${destination.name}.tmp")
        if (temporary.exists()) {
            check(temporary.deleteRecursively()) {
                "Could not clear temporary resource symbols '${temporary.absolutePath}'."
            }
        }
        check(temporary.mkdirs()) {
            "Could not create resource symbols directory '${temporary.absolutePath}'."
        }
        projectClasspath.forEach { entry ->
            when {
                entry.isDirectory -> entry.walkTopDown()
                    .filter(File::isFile)
                    .forEach { file ->
                        val relativePath = file.relativeTo(entry).invariantSeparatorsPath
                        if (RESOURCE_SYMBOL_CLASS_PATTERN.matches(relativePath)) {
                            copySymbol(file.readBytes(), relativePath, temporary)
                        }
                    }

                entry.isFile -> ZipFile(entry).use { zip ->
                    zip.entries().asSequence()
                        .filterNot { item -> item.isDirectory }
                        .filter { item -> RESOURCE_SYMBOL_CLASS_PATTERN.matches(item.name) }
                        .forEach { item ->
                            val bytes = zip.getInputStream(item).use { input -> input.readBytes() }
                            copySymbol(bytes, item.name, temporary)
                        }
                }
            }
        }
        temporary.resolve(RESOURCE_SYMBOL_MARKER).writeText(compatibilityFingerprint)
        destination.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) {
                "Could not create resource symbols parent '${parent.absolutePath}'."
            }
        }
        if (destination.exists()) {
            check(destination.deleteRecursively()) {
                "Could not replace resource symbols '${destination.absolutePath}'."
            }
        }
        check(temporary.renameTo(destination)) {
            "Could not publish resource symbols '${destination.absolutePath}'."
        }
        return destination
    }

    private fun copySymbol(
        bytes: ByteArray,
        relativePath: String,
        destination: File,
    ) {
        val target = destination.resolve(relativePath)
        if (target.isFile) {
            require(target.readBytes().contentEquals(bytes)) {
                "Conflicting resource symbol '$relativePath' in preview classpath."
            }
            return
        }
        target.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) {
                "Could not create resource symbol package '${parent.absolutePath}'."
            }
        }
        target.writeBytes(bytes)
    }
}

private const val RESOURCE_SYMBOL_MARKER = ".complete"
private val RESOURCE_SYMBOL_CLASS_PATTERN = Regex("""(?:^|.*/)R(?:\x24[^/]+)?\.class""")
