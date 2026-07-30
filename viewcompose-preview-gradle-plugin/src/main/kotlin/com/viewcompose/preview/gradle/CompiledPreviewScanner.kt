package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.PreviewConfiguration
import com.viewcompose.preview.tooling.PreviewDefaults
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewDiagnostic
import com.viewcompose.preview.tooling.PreviewDiagnosticSeverity
import com.viewcompose.preview.tooling.PreviewJvmEntryPoint
import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewSourceLocation
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.PreviewVariant
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal data class PreviewDiscoveryOutput(
    val descriptors: List<PreviewDescriptor>,
    val diagnostics: List<PreviewDiagnostic>,
)

/**
 * Discovers compiled preview functions without loading application classes into the Gradle daemon.
 */
internal class CompiledPreviewScanner(
    private val projectClassDirectories: Collection<File>,
    private val projectClassJars: Collection<File>,
    private val annotationClasspath: Collection<File>,
    private val sourceDirectories: Collection<File>,
) {
    private val classpath = AnnotationClassPath(
        roots = projectClassDirectories + projectClassJars + annotationClasspath,
    )
    private val scannedClasses = linkedMapOf<String, ScannedClass>()
    private val metaPreviewCache = mutableMapOf<String, List<RawPreview>>()

    fun scan(): PreviewDiscoveryOutput {
        projectClassDirectories
            .filter(File::isDirectory)
            .sortedBy(File::getAbsolutePath)
            .forEach { directory ->
                directory.walkTopDown()
                    .filter { file -> file.isFile && file.extension == "class" }
                    .sortedBy { file -> file.relativeTo(directory).invariantSeparatorsPath }
                    .forEach { file -> scanClass(file.readBytes()) }
            }
        projectClassJars
            .filter(File::isFile)
            .sortedBy(File::getAbsolutePath)
            .forEach { jar ->
                ZipFile(jar).use { zip ->
                    zip.entries().asSequence()
                        .filter { entry -> !entry.isDirectory && entry.name.endsWith(".class") }
                        .sortedBy { entry -> entry.name }
                        .forEach { entry ->
                            scanClass(zip.getInputStream(entry).use { input -> input.readBytes() })
                        }
                }
            }

        val descriptors = mutableListOf<PreviewDescriptor>()
        val diagnostics = mutableListOf<PreviewDiagnostic>()
        scannedClasses.values
            .sortedBy(ScannedClass::internalName)
            .forEach { owner ->
                owner.methods
                    .sortedWith(compareBy(ScannedMethod::name, ScannedMethod::descriptor))
                    .forEach { method ->
                        val previews = method.directPreviews +
                            method.annotations.flatMap(::resolvePreviews)
                        if (previews.isEmpty()) return@forEach
                        val sourceLocation = sourceLocation(owner, method)
                        if (!method.isSupportedEntryPoint()) {
                            diagnostics += PreviewDiagnostic(
                                severity = PreviewDiagnosticSeverity.Error,
                                message = "ViewCompose preview function has an unsupported JVM signature.",
                                phase = "discovery",
                                sourceLocation = sourceLocation,
                                details = "Expected public static $ENTRY_POINT_DESCRIPTOR, found " +
                                    "${method.name}${method.descriptor}.",
                            )
                            return@forEach
                        }

                        previews.groupBy(RawPreview::group)
                            .toSortedMap()
                            .forEach { (group, groupPreviews) ->
                                val variants = mutableListOf<PreviewVariant>()
                                groupPreviews.forEachIndexed { index, preview ->
                                    val result = runCatching {
                                        preview.toVariant(
                                            index = index,
                                            total = groupPreviews.size,
                                            existingIds = variants.mapTo(mutableSetOf()) {
                                                variant -> variant.id
                                            },
                                        )
                                    }
                                    result.onSuccess(variants::add)
                                    result.onFailure { error ->
                                        diagnostics += PreviewDiagnostic(
                                            severity = PreviewDiagnosticSeverity.Error,
                                            message = "ViewCompose preview configuration is invalid.",
                                            phase = "discovery",
                                            sourceLocation = sourceLocation,
                                            details = error.message,
                                        )
                                    }
                                }
                                if (variants.isNotEmpty()) {
                                    descriptors += PreviewDescriptor(
                                        id = descriptorId(owner, method, group),
                                        displayName = method.name,
                                        group = group,
                                        entryPoint = PreviewJvmEntryPoint(
                                            ownerClassName = owner.internalName.replace('/', '.'),
                                            methodName = method.name,
                                            methodDescriptor = method.descriptor,
                                        ),
                                        variants = variants,
                                        sourceLocation = sourceLocation,
                                    )
                                }
                            }
                    }
            }

        val duplicateIds = descriptors.groupingBy(PreviewDescriptor::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        check(duplicateIds.isEmpty()) {
            "Compiled preview discovery produced duplicate descriptor ids: " +
                duplicateIds.sorted().joinToString()
        }
        return PreviewDiscoveryOutput(
            descriptors = descriptors.sortedBy(PreviewDescriptor::id),
            diagnostics = diagnostics.sortedWith(
                compareBy(
                    { diagnostic -> diagnostic.sourceLocation?.filePath.orEmpty() },
                    { diagnostic -> diagnostic.sourceLocation?.line ?: 0 },
                    PreviewDiagnostic::message,
                ),
            ),
        )
    }

    private fun scanClass(bytes: ByteArray): ScannedClass {
        val visitor = PreviewClassVisitor()
        ClassReader(bytes).accept(visitor, ClassReader.SKIP_FRAMES)
        return visitor.result().also { scanned ->
            scannedClasses[scanned.annotationDescriptor] = scanned
            classpath.remember(scanned.internalName, bytes)
        }
    }

    private fun resolvePreviews(annotationDescriptor: String): List<RawPreview> {
        if (annotationDescriptor == PREVIEW_DESCRIPTOR) {
            return emptyList()
        }
        return resolveMetaPreviews(annotationDescriptor, visiting = linkedSetOf())
    }

    private fun resolveMetaPreviews(
        annotationDescriptor: String,
        visiting: MutableSet<String>,
    ): List<RawPreview> {
        metaPreviewCache[annotationDescriptor]?.let { return it }
        if (!visiting.add(annotationDescriptor)) return emptyList()
        val scanned = scannedClasses[annotationDescriptor]
            ?: classpath.find(annotationDescriptor)?.let(::scanClass)
        val resolved = if (scanned == null) {
            emptyList()
        } else {
            scanned.directPreviews + scanned.annotations.flatMap { nested ->
                resolveMetaPreviews(nested, visiting)
            }
        }
        visiting.remove(annotationDescriptor)
        return resolved.also { metaPreviewCache[annotationDescriptor] = it }
    }

    private fun sourceLocation(
        owner: ScannedClass,
        method: ScannedMethod,
    ): PreviewSourceLocation {
        val sourceFile = owner.sourceFile
        val packagePath = owner.internalName.substringBeforeLast('/', missingDelimiterValue = "")
        val relativePath = listOf(packagePath, sourceFile.orEmpty())
            .filter(String::isNotBlank)
            .joinToString("/")
        val resolvedFile = sourceDirectories.asSequence()
            .map { root -> root.resolve(relativePath) }
            .firstOrNull(File::isFile)
        return PreviewSourceLocation(
            filePath = resolvedFile?.absolutePath
                ?: sourceFile
                ?: "${owner.internalName}.class",
            line = method.firstLine.coerceAtLeast(1),
            symbolName = method.name,
        )
    }
}

private class PreviewClassVisitor : ClassVisitor(Opcodes.ASM9) {
    private lateinit var internalName: String
    private var access: Int = 0
    private var sourceFile: String? = null
    private val annotations = mutableListOf<String>()
    private val directPreviews = mutableListOf<RawPreview>()
    private val methods = mutableListOf<ScannedMethod>()

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        internalName = name
        this.access = access
    }

    override fun visitSource(
        source: String?,
        debug: String?,
    ) {
        sourceFile = source
    }

    override fun visitAnnotation(
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor? {
        return annotationVisitor(
            descriptor = descriptor,
            markerAnnotations = annotations,
            directPreviews = directPreviews,
        )
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        return PreviewMethodVisitor(access, name, descriptor) { method ->
            methods += method
        }
    }

    fun result(): ScannedClass = ScannedClass(
        internalName = internalName,
        access = access,
        sourceFile = sourceFile,
        annotations = annotations,
        directPreviews = directPreviews,
        methods = methods,
    )
}

private class PreviewMethodVisitor(
    private val access: Int,
    private val name: String,
    private val descriptor: String,
    private val onComplete: (ScannedMethod) -> Unit,
) : MethodVisitor(Opcodes.ASM9) {
    private val annotations = mutableListOf<String>()
    private val directPreviews = mutableListOf<RawPreview>()
    private var firstLine: Int = Int.MAX_VALUE

    override fun visitAnnotation(
        descriptor: String,
        visible: Boolean,
    ): AnnotationVisitor? {
        return annotationVisitor(
            descriptor = descriptor,
            markerAnnotations = annotations,
            directPreviews = directPreviews,
        )
    }

    override fun visitLineNumber(
        line: Int,
        start: org.objectweb.asm.Label,
    ) {
        firstLine = minOf(firstLine, line)
    }

    override fun visitEnd() {
        onComplete(
            ScannedMethod(
                access = access,
                name = name,
                descriptor = descriptor,
                annotations = annotations,
                directPreviews = directPreviews,
                firstLine = firstLine.takeUnless { line -> line == Int.MAX_VALUE } ?: 1,
            ),
        )
    }
}

private fun annotationVisitor(
    descriptor: String,
    markerAnnotations: MutableList<String>,
    directPreviews: MutableList<RawPreview>,
): AnnotationVisitor? {
    return when (descriptor) {
        PREVIEW_DESCRIPTOR -> RawPreviewAnnotationVisitor(directPreviews::add)
        PREVIEWS_DESCRIPTOR -> RawPreviewContainerVisitor(directPreviews::add)
        else -> {
            markerAnnotations += descriptor
            null
        }
    }
}

private class RawPreviewContainerVisitor(
    private val onPreview: (RawPreview) -> Unit,
) : AnnotationVisitor(Opcodes.ASM9) {
    override fun visitArray(name: String): AnnotationVisitor? {
        if (name != "value") return null
        return object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(
                name: String?,
                descriptor: String,
            ): AnnotationVisitor? {
                return if (descriptor == PREVIEW_DESCRIPTOR) {
                    RawPreviewAnnotationVisitor(onPreview)
                } else {
                    null
                }
            }
        }
    }
}

private class RawPreviewAnnotationVisitor(
    private val onPreview: (RawPreview) -> Unit,
) : AnnotationVisitor(Opcodes.ASM9) {
    private var preview = RawPreview()

    override fun visit(
        name: String,
        value: Any,
    ) {
        preview = when (name) {
            "name" -> preview.copy(name = value as String)
            "group" -> preview.copy(group = value as String)
            "widthDp" -> preview.copy(widthDp = value as Int)
            "heightDp" -> preview.copy(heightDp = value as Int)
            "density" -> preview.copy(density = value as Float)
            "fontScale" -> preview.copy(fontScale = value as Float)
            "localeTag" -> preview.copy(localeTag = value as String)
            "apiLevel" -> preview.copy(apiLevel = value as Int)
            else -> preview
        }
    }

    override fun visitEnum(
        name: String,
        descriptor: String,
        value: String,
    ) {
        preview = when (name) {
            "layoutDirection" -> preview.copy(
                layoutDirection = PreviewLayoutDirection.valueOf(value),
            )
            "theme" -> preview.copy(theme = PreviewTheme.valueOf(value))
            else -> preview
        }
    }

    override fun visitEnd() {
        onPreview(preview)
    }
}

private class AnnotationClassPath(
    roots: Collection<File>,
) {
    private val roots = roots.distinctBy(File::getAbsolutePath)
    private val memory = mutableMapOf<String, ByteArray>()
    private val misses = mutableSetOf<String>()

    fun remember(
        internalName: String,
        bytes: ByteArray,
    ) {
        memory[internalName] = bytes
    }

    fun find(descriptor: String): ByteArray? {
        val internalName = descriptor
            .removePrefix("L")
            .removeSuffix(";")
        memory[internalName]?.let { return it }
        if (!misses.add(internalName)) return null
        val entryName = "$internalName.class"
        roots.forEach { root ->
            if (root.isDirectory) {
                val candidate = root.resolve(entryName)
                if (candidate.isFile) {
                    return candidate.readBytes().also { bytes -> memory[internalName] = bytes }
                }
            } else if (root.isFile && root.extension == "jar") {
                ZipFile(root).use { zip ->
                    val entry = zip.getEntry(entryName)
                    if (entry != null) {
                        return zip.getInputStream(entry).use { input -> input.readBytes() }
                            .also { bytes -> memory[internalName] = bytes }
                    }
                }
            }
        }
        return null
    }
}

private data class ScannedClass(
    val internalName: String,
    val access: Int,
    val sourceFile: String?,
    val annotations: List<String>,
    val directPreviews: List<RawPreview>,
    val methods: List<ScannedMethod>,
) {
    val annotationDescriptor: String
        get() = "L$internalName;"
}

private data class ScannedMethod(
    val access: Int,
    val name: String,
    val descriptor: String,
    val annotations: List<String>,
    val directPreviews: List<RawPreview>,
    val firstLine: Int,
) {
    fun isSupportedEntryPoint(): Boolean {
        return access and Opcodes.ACC_PUBLIC != 0 &&
            access and Opcodes.ACC_STATIC != 0 &&
            descriptor == ENTRY_POINT_DESCRIPTOR
    }
}

private data class RawPreview(
    val name: String = "",
    val group: String = "",
    val widthDp: Int = PreviewDefaults.WIDTH_DP,
    val heightDp: Int = PreviewDefaults.HEIGHT_DP,
    val density: Float = PreviewDefaults.DENSITY,
    val fontScale: Float = PreviewDefaults.FONT_SCALE,
    val localeTag: String = PreviewDefaults.LOCALE_TAG,
    val layoutDirection: PreviewLayoutDirection = PreviewLayoutDirection.Ltr,
    val theme: PreviewTheme = PreviewTheme.Light,
    val apiLevel: Int = PreviewDefaults.UNSPECIFIED_API_LEVEL,
) {
    fun toVariant(
        index: Int,
        total: Int,
        existingIds: Set<String>,
    ): PreviewVariant {
        val configuration = PreviewConfiguration(
            widthDp = widthDp,
            heightDp = heightDp,
            density = density,
            fontScale = fontScale,
            localeTags = listOf(localeTag),
            layoutDirection = layoutDirection,
            theme = theme,
            apiLevel = apiLevel.takeIf { value ->
                value != PreviewDefaults.UNSPECIFIED_API_LEVEL
            },
        )
        val displayName = name.ifBlank {
            if (total == 1) "Default" else "Variant ${index + 1}"
        }
        val baseId = "${stableSlug(displayName)}-${shortSha256(configuration.toString())}"
        var id = baseId
        var duplicate = 2
        while (id in existingIds) {
            id = "$baseId-$duplicate"
            duplicate += 1
        }
        return PreviewVariant(
            id = id,
            displayName = displayName,
            configuration = configuration,
        )
    }
}

private fun descriptorId(
    owner: ScannedClass,
    method: ScannedMethod,
    group: String,
): String {
    val ownerSimpleName = owner.internalName.substringAfterLast('/').substringBefore('$')
    val readable = stableSlug("$ownerSimpleName-${method.name}")
    val identity = "${owner.internalName}#${method.name}${method.descriptor}#$group"
    return "$readable-${shortSha256(identity, length = 12)}"
}

private fun stableSlug(value: String): String {
    return value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "preview" }
}

private fun shortSha256(
    value: String,
    length: Int = 8,
): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }.take(length)
}

private const val PREVIEW_DESCRIPTOR =
    "Lcom/viewcompose/preview/tooling/ViewComposePreview;"
private const val PREVIEWS_DESCRIPTOR =
    "Lcom/viewcompose/preview/tooling/ViewComposePreviews;"
private const val ENTRY_POINT_DESCRIPTOR =
    "(Lcom/viewcompose/widget/core/UiTreeBuilder;)V"
