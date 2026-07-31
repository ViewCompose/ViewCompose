package com.viewcompose.studio.preview

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

internal data class PreviewGradleInvocation(
    val executable: Path,
    val workingDirectory: Path,
    val arguments: List<String>,
)

internal data class PreviewGradleResult(
    val exitCode: Int,
    val standardOutput: String,
    val errorOutput: String,
)

internal fun interface PreviewGradleExecutor {
    fun execute(
        invocation: PreviewGradleInvocation,
        indicator: ProgressIndicator,
    ): PreviewGradleResult
}

internal class IdePreviewGradleExecutor : PreviewGradleExecutor {
    override fun execute(
        invocation: PreviewGradleInvocation,
        indicator: ProgressIndicator,
    ): PreviewGradleResult {
        val commandLine = GeneralCommandLine(invocation.executable.toString())
            .withWorkingDirectory(invocation.workingDirectory)
            .withParameters(invocation.arguments)
            .withRedirectErrorStream(false)
        val output = CapturingProcessHandler(commandLine)
            .runProcessWithProgressIndicator(indicator)
        if (output.isCancelled) {
            throw ProcessCanceledException()
        }
        return PreviewGradleResult(
            exitCode = output.exitCode,
            standardOutput = output.stdout,
            errorOutput = output.stderr,
        )
    }
}

internal sealed interface PreviewRenderOutcome {
    data class Success(
        val selection: PreviewSourceSelection,
        val descriptorId: String,
        val descriptorName: String,
        val variants: List<StudioPreviewVariant>,
        val selectedVariantId: String,
        val variantName: String,
        val image: BufferedImage,
        val imagePath: Path,
        val renderTreePath: Path?,
        val diagnostics: List<StudioPreviewDiagnostic>,
        val durationMillis: Long?,
        val cacheHit: Boolean,
    ) : PreviewRenderOutcome

    data class Failure(
        val selection: PreviewSourceSelection,
        val title: String,
        val diagnostics: List<StudioPreviewDiagnostic>,
        val details: String? = null,
    ) : PreviewRenderOutcome
}

internal class ViewComposePreviewRenderCoordinator(
    projectRoot: Path,
    private val executor: PreviewGradleExecutor = IdePreviewGradleExecutor(),
) {
    private val projectRoot = projectRoot.toAbsolutePath().normalize()

    fun render(
        selection: PreviewSourceSelection,
        requestedVariantId: String? = null,
        indicator: ProgressIndicator,
        onProgress: (String) -> Unit = {},
    ): PreviewRenderOutcome {
        return runCatching {
            val target = locateGradleTarget(selection)
            onProgress("Compiling preview descriptors…")
            indicator.text = "Compiling ViewCompose preview descriptors"
            val discovery = executor.execute(
                invocation = target.invocation(
                    taskName = "viewComposePreviewDescriptors",
                ),
                indicator = indicator,
            )
            if (discovery.exitCode != 0) {
                return PreviewRenderOutcome.Failure(
                    selection = selection,
                    title = "Preview discovery failed",
                    diagnostics = emptyList(),
                    details = discovery.presentableFailure(),
                )
            }

            indicator.checkCanceled()
            onProgress("Matching compiled preview…")
            val match = findMatchingPreview(
                target = target,
                selection = selection,
                requestedVariantId = requestedVariantId,
            )
                ?: return PreviewRenderOutcome.Failure(
                    selection = selection,
                    title = "No compiled preview matched this function",
                    diagnostics = readCatalogDiagnostics(target),
                    details = "The function may have an unsupported signature, or the module may " +
                        "not apply the com.viewcompose.preview Gradle plugin.",
                )

            onProgress("Rendering ${match.descriptor.displayName}…")
            indicator.text = "Rendering ${match.descriptor.displayName}"
            val render = executor.execute(
                invocation = target.invocation(
                    taskName = renderTaskName(match.catalog.buildVariant),
                    additionalArguments = listOf(
                        "--preview-id",
                        match.descriptor.id,
                        "--variant-id",
                        match.variant.id,
                    ),
                ),
                indicator = indicator,
            )
            if (render.exitCode != 0) {
                return PreviewRenderOutcome.Failure(
                    selection = selection,
                    title = "Preview render failed",
                    diagnostics = match.readResponseOrNull()
                        ?.diagnostics
                        .orEmpty(),
                    details = render.presentableFailure(),
                )
            }

            indicator.checkCanceled()
            val response = match.readResponse()
            require(response.previewId == match.descriptor.id) {
                "Preview response '${response.previewId}' does not match " +
                    "'${match.descriptor.id}'."
            }
            require(response.variantId == match.variant.id) {
                "Preview response variant '${response.variantId}' does not match " +
                    "'${match.variant.id}'."
            }
            if (response.status != StudioPreviewRenderStatus.Success) {
                return PreviewRenderOutcome.Failure(
                    selection = selection,
                    title = "Preview ${response.status.name}",
                    diagnostics = response.diagnostics,
                    details = render.presentableOutput(),
                )
            }
            val imagePath = match.resolveArtifact(response.imagePath, "preview image")
            val renderTreePath = response.renderTreePath?.let { path ->
                match.resolveArtifact(path, "render tree")
            }
            PreviewRenderOutcome.Success(
                selection = selection,
                descriptorId = match.descriptor.id,
                descriptorName = match.descriptor.displayName,
                variants = match.descriptor.variants,
                selectedVariantId = match.variant.id,
                variantName = match.variant.displayName,
                image = loadBoundedPreviewImage(imagePath),
                imagePath = imagePath,
                renderTreePath = renderTreePath,
                diagnostics = response.diagnostics,
                durationMillis = response.durationMillis,
                cacheHit = CACHE_HIT_MARKER in render.standardOutput,
            )
        }.getOrElse { error ->
            if (error is ProcessCanceledException) throw error
            PreviewRenderOutcome.Failure(
                selection = selection,
                title = "Preview tooling failed",
                diagnostics = emptyList(),
                details = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private fun locateGradleTarget(selection: PreviewSourceSelection): PreviewGradleTarget {
        require(Files.isDirectory(projectRoot)) {
            "Android Studio project root does not exist: '$projectRoot'."
        }
        val sourcePath = Path.of(selection.filePath).toAbsolutePath().normalize()
        require(sourcePath.startsWith(projectRoot)) {
            "Preview source '$sourcePath' is outside project '$projectRoot'."
        }
        val moduleRoot = generateSequence(sourcePath.parent) { directory -> directory.parent }
            .takeWhile { directory -> directory.startsWith(projectRoot) }
            .firstOrNull(::containsGradleBuildFile)
            ?: error("Could not locate the Gradle module for '$sourcePath'.")
        val relativeModule = projectRoot.relativize(moduleRoot)
        val moduleSegments = relativeModule.map(Path::toString)
        require(moduleSegments.all(GRADLE_PROJECT_SEGMENT::matches)) {
            "Preview module path contains unsupported Gradle project segments: '$relativeModule'."
        }
        val modulePath = if (moduleSegments.isEmpty()) {
            ":"
        } else {
            ":" + moduleSegments.joinToString(":")
        }
        val wrapper = listOf(
            projectRoot.resolve("gradlew"),
            projectRoot.resolve("gradlew.bat"),
        ).firstOrNull(Files::isRegularFile)
            ?: error("Could not find a Gradle wrapper under '$projectRoot'.")
        return PreviewGradleTarget(
            projectRoot = projectRoot,
            moduleRoot = moduleRoot,
            modulePath = modulePath,
            wrapper = wrapper,
        )
    }

    private fun findMatchingPreview(
        target: PreviewGradleTarget,
        selection: PreviewSourceSelection,
        requestedVariantId: String?,
    ): PreviewCatalogMatch? {
        val candidates = readCatalogFiles(target)
            .flatMap { catalogFile ->
                val catalog = StudioPreviewProtocolReader.readCatalog(catalogFile)
                catalog.descriptors.mapNotNull { descriptor ->
                    val source = descriptor.sourceLocation ?: return@mapNotNull null
                    if (!source.matches(selection)) return@mapNotNull null
                    PreviewCatalogMatch(
                        catalogPath = catalogFile,
                        catalog = catalog,
                        descriptor = descriptor,
                        variant = descriptor.variants
                            .firstOrNull { variant -> variant.id == requestedVariantId }
                            ?: descriptor.variants.first(),
                    )
                }
            }
        return candidates.sortedWith(
            compareBy(
                { match -> buildVariantPriority(match.catalog.buildVariant) },
                { match -> match.catalog.buildVariant },
                { match -> match.descriptor.id },
                { match -> match.variant.id },
            ),
        ).firstOrNull()
    }

    private fun readCatalogDiagnostics(target: PreviewGradleTarget): List<StudioPreviewDiagnostic> {
        return readCatalogFiles(target).flatMap { path ->
            runCatching { StudioPreviewProtocolReader.readCatalog(path).diagnostics }
                .getOrDefault(emptyList())
        }
    }

    private fun readCatalogFiles(target: PreviewGradleTarget): List<Path> {
        val previewRoot = target.moduleRoot.resolve("build/viewcompose-preview")
        if (!Files.isDirectory(previewRoot)) return emptyList()
        return try {
            Files.list(previewRoot).use { variants ->
                variants
                    .filter(Files::isDirectory)
                    .map { directory -> directory.resolve(DESCRIPTOR_CATALOG_FILE_NAME) }
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList()
            }
        } catch (_: IOException) {
            emptyList()
        }
    }
}

private data class PreviewGradleTarget(
    val projectRoot: Path,
    val moduleRoot: Path,
    val modulePath: String,
    val wrapper: Path,
) {
    fun invocation(
        taskName: String,
        additionalArguments: List<String> = emptyList(),
    ): PreviewGradleInvocation {
        val qualifiedTask = if (modulePath == ":") {
            ":$taskName"
        } else {
            "$modulePath:$taskName"
        }
        return PreviewGradleInvocation(
            executable = wrapper,
            workingDirectory = projectRoot,
            arguments = listOf(
                qualifiedTask,
                "--console=plain",
                "--stacktrace",
            ) + additionalArguments,
        )
    }
}

private data class PreviewCatalogMatch(
    val catalogPath: Path,
    val catalog: StudioPreviewCatalog,
    val descriptor: StudioPreviewDescriptor,
    val variant: StudioPreviewVariant,
) {
    private val artifactRoot: Path
        get() = checkNotNull(catalogPath.parent).toAbsolutePath().normalize()

    private val responsePath: Path
        get() = artifactRoot
            .resolve("render-cache")
            .resolve(catalog.buildFingerprint)
            .resolve(descriptor.id)
            .resolve(variant.id)
            .resolve(RESPONSE_FILE_NAME)

    fun readResponse(): StudioPreviewRenderResponse {
        return StudioPreviewProtocolReader.readResponse(responsePath)
    }

    fun readResponseOrNull(): StudioPreviewRenderResponse? {
        return runCatching(::readResponse).getOrNull()
    }

    fun resolveArtifact(
        rawPath: String?,
        label: String,
    ): Path {
        require(!rawPath.isNullOrBlank()) { "Successful preview response has no $label." }
        val path = Path.of(rawPath).toAbsolutePath().normalize()
        require(path.startsWith(artifactRoot)) {
            "Preview $label '$path' is outside artifact root '$artifactRoot'."
        }
        require(Files.isRegularFile(path)) { "Preview $label does not exist: '$path'." }
        return path
    }
}

private fun StudioPreviewSourceLocation.matches(selection: PreviewSourceSelection): Boolean {
    if (symbolName != selection.symbolName) return false
    val selectedPath = Path.of(selection.filePath).toAbsolutePath().normalize()
    val catalogPath = runCatching {
        Path.of(filePath).toAbsolutePath().normalize()
    }.getOrNull()
    return catalogPath == selectedPath ||
        (
            catalogPath?.fileName == selectedPath.fileName &&
                line == selection.line
        )
}

private fun containsGradleBuildFile(directory: Path): Boolean {
    return Files.isRegularFile(directory.resolve("build.gradle.kts")) ||
        Files.isRegularFile(directory.resolve("build.gradle"))
}

private fun renderTaskName(buildVariant: String): String {
    require(buildVariant.isNotBlank()) { "Preview build variant must not be blank." }
    return "render${buildVariant.replaceFirstChar(Char::uppercase)}ViewComposePreview"
}

private fun buildVariantPriority(buildVariant: String): Int {
    return when {
        buildVariant == "debug" -> 0
        buildVariant.endsWith("Debug", ignoreCase = true) -> 1
        else -> 2
    }
}

private fun PreviewGradleResult.presentableFailure(): String {
    val output = listOf(errorOutput, standardOutput)
        .filter(String::isNotBlank)
        .joinToString("\n")
    return "Gradle exited with code $exitCode.\n${output.takeLast(MAXIMUM_GRADLE_OUTPUT_LENGTH)}"
        .trim()
}

private fun PreviewGradleResult.presentableOutput(): String? {
    return standardOutput
        .takeLast(MAXIMUM_GRADLE_OUTPUT_LENGTH)
        .takeIf(String::isNotBlank)
}

private fun loadBoundedPreviewImage(path: Path): BufferedImage {
    val size = Files.size(path)
    require(size in 1..MAXIMUM_PREVIEW_IMAGE_BYTES) {
        "Preview image '$path' has unsupported size $size bytes."
    }
    ImageIO.createImageInputStream(path.toFile()).use { input ->
        requireNotNull(input) { "Could not open preview image '$path'." }
        val readers = ImageIO.getImageReaders(input)
        require(readers.hasNext()) { "Preview image '$path' uses an unsupported format." }
        val reader = readers.next()
        return try {
            reader.input = input
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            require(width > 0 && height > 0) { "Preview image dimensions must be positive." }
            require(width.toLong() * height.toLong() <= MAXIMUM_PREVIEW_IMAGE_PIXELS) {
                "Preview image dimensions ${width}x$height exceed the tooling limit."
            }
            checkNotNull(reader.read(0)) { "Could not decode preview image '$path'." }
        } finally {
            reader.dispose()
        }
    }
}

private const val DESCRIPTOR_CATALOG_FILE_NAME = "descriptors.json"
private const val RESPONSE_FILE_NAME = "response.json"
private const val CACHE_HIT_MARKER = "ViewCompose preview cache hit:"
private const val MAXIMUM_GRADLE_OUTPUT_LENGTH = 30_000
private const val MAXIMUM_PREVIEW_IMAGE_BYTES = 64L * 1024L * 1024L
private const val MAXIMUM_PREVIEW_IMAGE_PIXELS = 64L * 1024L * 1024L
private val GRADLE_PROJECT_SEGMENT = Regex("[A-Za-z0-9_.-]+")
