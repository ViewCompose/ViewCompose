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
    val task: String,
    val buildArguments: List<String>,
) {
    init {
        require(task.isNotBlank()) { "Preview Gradle invocation task must not be blank." }
        require(buildArguments.none(String::isBlank)) {
            "Preview Gradle build arguments must not contain blank values."
        }
    }

    val commandLineArguments: List<String>
        get() = listOf(task) + buildArguments
}

internal data class PreviewGradleResult(
    val exitCode: Int,
    val standardOutput: String,
    val errorOutput: String,
    val durationMillis: Long = 0L,
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
        val startedAtNanos = System.nanoTime()
        val commandLine = GeneralCommandLine(invocation.executable.toString())
            .withWorkingDirectory(invocation.workingDirectory)
            .withParameters(invocation.commandLineArguments)
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
            durationMillis = elapsedMillis(startedAtNanos),
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
        val renderSnapshot: StudioPreviewRenderSnapshot?,
        val diagnostics: List<StudioPreviewDiagnostic>,
        val durationMillis: Long?,
        val cacheHit: Boolean,
        val performanceTrace: PreviewPerformanceTrace = PreviewPerformanceTrace(),
        val inputScope: PreviewInputScope? = null,
    ) : PreviewRenderOutcome

    data class Failure(
        val selection: PreviewSourceSelection,
        val title: String,
        val diagnostics: List<StudioPreviewDiagnostic>,
        val details: String? = null,
    ) : PreviewRenderOutcome
}

internal val PreviewRenderOutcome.Success.logicalWidthDp: Int
    get() = variants.firstOrNull { variant -> variant.id == selectedVariantId }
        ?.widthDp
        ?.takeIf { widthDp -> widthDp > 0 }
        ?: DEFAULT_RENDER_LOGICAL_WIDTH_DP

internal class ViewComposePreviewRenderCoordinator(
    projectRoot: Path,
    private val executor: PreviewGradleExecutor = IdePreviewGradleExecutor(),
) {
    private val projectRoot = projectRoot.toAbsolutePath().normalize()

    fun render(
        selection: PreviewSourceSelection,
        requestedVariantId: String? = null,
        forceRerender: Boolean = false,
        indicator: ProgressIndicator,
        onProgress: (String) -> Unit = {},
    ): PreviewRenderOutcome {
        return runCatching {
            val target = locateGradleTarget(selection)
            onProgress("Compiling preview descriptors…")
            indicator.text = "Compiling ViewCompose preview descriptors"
            val discovery = discoverPreviews(target, indicator)
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
            renderMatch(
                target = target,
                selection = selection,
                match = match,
                forceRerender = forceRerender,
                indicator = indicator,
                onProgress = onProgress,
            ).withPerformancePhase("gradle-discovery", discovery.durationMillis)
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

    /**
     * Fast path for an already compiled preview. The render task owns its discovery dependency, so
     * Gradle can incrementally compile, rediscover, and render in one invocation after a save.
     * Returns null only when the stable descriptor or conventional debug task no longer exists.
     */
    fun renderKnownDebug(
        selection: PreviewSourceSelection,
        descriptorId: String,
        requestedVariantId: String,
        forceRerender: Boolean = false,
        indicator: ProgressIndicator,
        onProgress: (String) -> Unit = {},
    ): PreviewRenderOutcome? {
        return runCatching {
            val target = locateGradleTarget(selection)
            val taskName = renderTaskName(PREFERRED_BUILD_VARIANT)
            onProgress("Incrementally compiling and rendering preview…")
            indicator.text = "Incrementally compiling ViewCompose preview"
            val render = executor.execute(
                invocation = target.invocation(
                    taskName = taskName,
                    additionalBuildArguments = previewSelectionBuildArguments(
                        descriptorId = descriptorId,
                        variantId = requestedVariantId,
                        forceRerender = forceRerender,
                    ),
                ),
                indicator = indicator,
            )
            if (
                render.isMissingTaskFailure(taskName) ||
                render.isUnknownPreviewFailure(descriptorId)
            ) {
                return null
            }
            if (render.exitCode != 0) {
                return PreviewRenderOutcome.Failure(
                    selection = selection,
                    title = "Preview render failed",
                    diagnostics = emptyList(),
                    details = render.presentableFailure(),
                )
            }
            indicator.checkCanceled()
            val match = findMatchingPreviewById(
                target = target,
                descriptorId = descriptorId,
                requestedVariantId = requestedVariantId,
                buildVariant = PREFERRED_BUILD_VARIANT,
            ) ?: return null
            readMatchOutcome(
                target = target,
                selection = selection,
                match = match,
                render = render,
            )
        }.getOrElse { error ->
            if (error is ProcessCanceledException) throw error
            toolingFailure(selection, error)
        }
    }

    /** Renders every variant while compiling each Gradle module only once. */
    fun renderAll(
        selections: List<PreviewSourceSelection>,
        forceRerender: Boolean = false,
        indicator: ProgressIndicator,
        onProgress: (String) -> Unit = {},
    ): List<PreviewRenderOutcome> {
        val outcomes = mutableListOf<PreviewRenderOutcome>()
        renderAllEach(
            selections = selections,
            forceRerender = forceRerender,
            indicator = indicator,
            onProgress = onProgress,
            onOutcome = outcomes::add,
        )
        return outcomes
    }

    /**
     * Streaming gallery renderer. A full-size image is handed to [onOutcome] as soon as it is
     * available so callers can downsample and release it before the next preview is rendered.
     */
    fun renderAllEach(
        selections: List<PreviewSourceSelection>,
        forceRerender: Boolean = false,
        indicator: ProgressIndicator,
        onProgress: (String) -> Unit = {},
        batchStrategy: PreviewGalleryBatchStrategy? = null,
        onOutcome: (PreviewRenderOutcome) -> Unit,
    ) {
        if (selections.isEmpty()) return
        val groupedTargets = linkedMapOf<PreviewGradleTarget, MutableList<PreviewSourceSelection>>()
        selections.distinct().forEach { selection ->
            runCatching { locateGradleTarget(selection) }
                .onSuccess { target ->
                    groupedTargets.getOrPut(target, ::mutableListOf) += selection
                }
                .onFailure { error ->
                    onOutcome(toolingFailure(selection, error))
                }
        }
        var completed = 0
        groupedTargets.forEach { (target, moduleSelections) ->
            indicator.checkCanceled()
            onProgress("Compiling preview descriptors for ${target.modulePath}…")
            indicator.text = "Compiling ViewCompose preview descriptors"
            val discovery = discoverPreviews(target, indicator)
            if (discovery.exitCode != 0) {
                moduleSelections.forEach { selection ->
                    onOutcome(
                        PreviewRenderOutcome.Failure(
                            selection = selection,
                            title = "Preview discovery failed",
                            diagnostics = emptyList(),
                            details = discovery.presentableFailure(),
                        ),
                    )
                }
                return@forEach
            }
            val selectionMatches = mutableListOf<PreviewSelectionMatch>()
            moduleSelections.forEach selectionLoop@ { selection ->
                indicator.checkCanceled()
                val matches = findMatchingPreviews(
                    target = target,
                    selection = selection,
                )
                if (matches.isEmpty()) {
                    onOutcome(
                        PreviewRenderOutcome.Failure(
                            selection = selection,
                            title = "No compiled preview matched this function",
                            diagnostics = readCatalogDiagnostics(target),
                            details = "The function may have an unsupported signature.",
                        ),
                    )
                    return@selectionLoop
                }
                matches.forEach { match ->
                    selectionMatches += PreviewSelectionMatch(selection, match)
                }
            }
            selectionMatches
                .groupBy { selectionMatch -> selectionMatch.match.catalog.buildVariant }
                .forEach { (_, variantMatches) ->
                    indicator.checkCanceled()
                    val batches = batchStrategy
                        ?.batches(variantMatches)
                        ?: listOf(variantMatches)
                    batches.forEach { batch ->
                        if (batch.isEmpty()) return@forEach
                        indicator.checkCanceled()
                        completed += batch.size
                        onProgress(
                            "Rendering ${batch.size} previews in one batch " +
                                "($completed total)…",
                        )
                        renderBatch(
                            target = target,
                            matches = batch,
                            forceRerender = forceRerender,
                            indicator = indicator,
                            onOutcome = { outcome ->
                                onOutcome(
                                    outcome.withPerformancePhase(
                                        phase = "gradle-discovery",
                                        durationMillis = discovery.durationMillis,
                                        shared = true,
                                    ),
                                )
                            },
                        )
                        batchStrategy?.onBatchCompleted(
                            batch.mapTo(linkedSetOf(), PreviewSelectionMatch::selection),
                        )
                    }
                }
        }
    }

    private fun renderBatch(
        target: PreviewGradleTarget,
        matches: List<PreviewSelectionMatch>,
        forceRerender: Boolean,
        indicator: ProgressIndicator,
        onOutcome: (PreviewRenderOutcome) -> Unit,
    ) {
        if (matches.isEmpty()) return
        val batchFile = Files.createTempFile("viewcompose-preview-targets-", ".tsv")
        try {
            Files.writeString(
                batchFile,
                matches.joinToString(separator = "\n", postfix = "\n") { selectionMatch ->
                    "${selectionMatch.match.descriptor.id}\t" +
                        selectionMatch.match.variant.id
                },
            )
            indicator.text = "Rendering ${matches.size} ViewCompose previews"
            val render = executor.execute(
                invocation = target.invocation(
                    taskName = renderTaskName(matches.first().match.catalog.buildVariant),
                    additionalBuildArguments = previewBatchBuildArguments(
                        batchFile = batchFile,
                        forceRerender = forceRerender,
                    ),
                ),
                indicator = indicator,
            )
            if (render.exitCode != 0) {
                matches.forEach { selectionMatch ->
                    onOutcome(
                        PreviewRenderOutcome.Failure(
                            selection = selectionMatch.selection,
                            title = "Preview batch render failed",
                            diagnostics = selectionMatch.match.readResponseOrNull()
                                ?.diagnostics
                                .orEmpty(),
                            details = render.presentableFailure(),
                        ),
                    )
                }
                return
            }
            matches.forEach { selectionMatch ->
                indicator.checkCanceled()
                val outcome = runCatching {
                    readMatchOutcome(
                        target = target,
                        selection = selectionMatch.selection,
                        match = selectionMatch.match,
                        render = render,
                    )
                }.getOrElse { error ->
                    if (error is ProcessCanceledException) throw error
                    toolingFailure(selectionMatch.selection, error)
                }
                onOutcome(outcome)
            }
        } finally {
            runCatching { Files.deleteIfExists(batchFile) }
        }
    }

    private fun renderMatch(
        target: PreviewGradleTarget,
        selection: PreviewSourceSelection,
        match: PreviewCatalogMatch,
        forceRerender: Boolean,
        indicator: ProgressIndicator,
        onProgress: (String) -> Unit,
    ): PreviewRenderOutcome {
        onProgress("Rendering ${match.descriptor.displayName}…")
        indicator.text = "Rendering ${match.descriptor.displayName}"
        val render = executor.execute(
            invocation = target.invocation(
                taskName = renderTaskName(match.catalog.buildVariant),
                additionalBuildArguments = previewSelectionBuildArguments(
                    descriptorId = match.descriptor.id,
                    variantId = match.variant.id,
                    forceRerender = forceRerender,
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
        return readMatchOutcome(
            target = target,
            selection = selection,
            match = match,
            render = render,
        )
    }

    private fun readMatchOutcome(
        target: PreviewGradleTarget,
        selection: PreviewSourceSelection,
        match: PreviewCatalogMatch,
        render: PreviewGradleResult,
    ): PreviewRenderOutcome {
        val response = match.readResponse()
        require(response.previewId == match.descriptor.id) {
            "Preview response '${response.previewId}' does not match '${match.descriptor.id}'."
        }
        require(response.variantId == match.variant.id) {
            "Preview response variant '${response.variantId}' does not match '${match.variant.id}'."
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
        val snapshotDecodeStartedAtNanos = System.nanoTime()
        val renderSnapshotResult = renderTreePath?.let { path ->
            runCatching { StudioPreviewProtocolReader.readRenderSnapshot(path) }
        }
        val snapshotDecodeMillis = elapsedMillis(snapshotDecodeStartedAtNanos)
        val snapshotDiagnostic = renderSnapshotResult
            ?.exceptionOrNull()
            ?.let { error ->
                StudioPreviewDiagnostic(
                    severity = StudioPreviewDiagnosticSeverity.Warning,
                    message = "The preview rendered, but its diagnostic snapshot could not be read.",
                    phase = "render-tree",
                    sourceLocation = match.descriptor.sourceLocation,
                    details = error.message,
                )
            }
        val imageDecodeStartedAtNanos = System.nanoTime()
        val image = loadBoundedPreviewImage(imagePath)
        val imageDecodeMillis = elapsedMillis(imageDecodeStartedAtNanos)
        val workerPhases = response.phaseTimings.map { timing ->
            PreviewPerformancePhase(
                phase = timing.phase,
                durationMillis = timing.durationMillis,
            )
        }
        return PreviewRenderOutcome.Success(
            selection = selection,
            descriptorId = match.descriptor.id,
            descriptorName = match.descriptor.displayName,
            variants = match.descriptor.variants,
            selectedVariantId = match.variant.id,
            variantName = match.variant.displayName,
            image = image,
            imagePath = imagePath,
            renderTreePath = renderTreePath,
            renderSnapshot = renderSnapshotResult?.getOrNull(),
            diagnostics = response.diagnostics + listOfNotNull(snapshotDiagnostic),
            durationMillis = response.durationMillis,
            cacheHit = match.wasCacheHit(render.standardOutput),
            performanceTrace = PreviewPerformanceTrace(workerPhases)
                .plus("gradle-render", render.durationMillis, shared = true)
                .plus("snapshot-decode", snapshotDecodeMillis)
                .plus("image-decode", imageDecodeMillis),
            inputScope = PreviewInputScope.create(
                projectRoot = target.projectRoot,
                moduleRoot = target.moduleRoot,
                manifestInputPaths = runCatching {
                    StudioPreviewProtocolReader.readBuildManifestInputPaths(
                        match.catalogPath.parent.resolve(BUILD_MANIFEST_FILE_NAME),
                    )
                }.getOrDefault(emptyList()),
            ),
        )
    }

    private fun toolingFailure(
        selection: PreviewSourceSelection,
        error: Throwable,
    ): PreviewRenderOutcome.Failure {
        return PreviewRenderOutcome.Failure(
            selection = selection,
            title = "Preview tooling failed",
            diagnostics = emptyList(),
            details = error.message ?: error::class.java.simpleName,
        )
    }

    private fun discoverPreviews(
        target: PreviewGradleTarget,
        indicator: ProgressIndicator,
    ): PreviewGradleResult {
        val preferred = executor.execute(
            invocation = target.invocation(PREFERRED_DISCOVERY_TASK_NAME),
            indicator = indicator,
        )
        if (!preferred.isMissingTaskFailure(PREFERRED_DISCOVERY_TASK_NAME)) return preferred
        indicator.checkCanceled()
        return executor.execute(
            invocation = target.invocation(AGGREGATE_DISCOVERY_TASK_NAME),
            indicator = indicator,
        )
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

    private fun findMatchingPreviewById(
        target: PreviewGradleTarget,
        descriptorId: String,
        requestedVariantId: String,
        buildVariant: String,
    ): PreviewCatalogMatch? {
        return readCatalogFiles(target)
            .asSequence()
            .map { catalogFile -> catalogFile to StudioPreviewProtocolReader.readCatalog(catalogFile) }
            .filter { (_, catalog) -> catalog.buildVariant == buildVariant }
            .mapNotNull { (catalogFile, catalog) ->
                val descriptor = catalog.descriptors
                    .firstOrNull { candidate -> candidate.id == descriptorId }
                    ?: return@mapNotNull null
                val variant = descriptor.variants
                    .firstOrNull { candidate -> candidate.id == requestedVariantId }
                    ?: return@mapNotNull null
                PreviewCatalogMatch(
                    catalogPath = catalogFile,
                    catalog = catalog,
                    descriptor = descriptor,
                    variant = variant,
                )
            }
            .firstOrNull()
    }

    private fun findMatchingPreviews(
        target: PreviewGradleTarget,
        selection: PreviewSourceSelection,
    ): List<PreviewCatalogMatch> {
        val first = findMatchingPreview(
            target = target,
            selection = selection,
            requestedVariantId = null,
        ) ?: return emptyList()
        return first.descriptor.variants.map { variant -> first.copy(variant = variant) }
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

internal class PreviewGalleryBatchStrategy(
    private val firstBatchSelectionCount: Int,
    private val priorityOrder: PreviewGalleryPriorityOrder,
    private val batchCompleted: (Set<PreviewSourceSelection>) -> Unit,
) {
    init {
        require(firstBatchSelectionCount > 0)
    }

    fun batches(matches: List<PreviewSelectionMatch>): List<List<PreviewSelectionMatch>> {
        if (matches.isEmpty()) return emptyList()
        val orderedSelections = priorityOrder.order(matches.map(PreviewSelectionMatch::selection))
        val firstSelections = orderedSelections.take(firstBatchSelectionCount).toHashSet()
        val first = matches.filter { match -> match.selection in firstSelections }
        val remaining = matches.filterNot { match -> match.selection in firstSelections }
        return listOf(first, remaining).filter(List<PreviewSelectionMatch>::isNotEmpty)
    }

    fun onBatchCompleted(selections: Set<PreviewSourceSelection>) {
        batchCompleted(selections)
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
        additionalBuildArguments: List<String> = emptyList(),
    ): PreviewGradleInvocation {
        val qualifiedTask = if (modulePath == ":") {
            ":$taskName"
        } else {
            "$modulePath:$taskName"
        }
        return PreviewGradleInvocation(
            executable = wrapper,
            workingDirectory = projectRoot,
            task = qualifiedTask,
            buildArguments = listOf(
                "--console=plain",
                "--stacktrace",
            ) + additionalBuildArguments,
        )
    }
}

private fun previewSelectionBuildArguments(
    descriptorId: String,
    variantId: String,
    forceRerender: Boolean,
): List<String> = buildList {
    add("-P$PREVIEW_ID_PROJECT_PROPERTY=$descriptorId")
    add("-P$PREVIEW_VARIANT_ID_PROJECT_PROPERTY=$variantId")
    if (forceRerender) add("-P$PREVIEW_RERENDER_PROJECT_PROPERTY=true")
}

private fun previewBatchBuildArguments(
    batchFile: Path,
    forceRerender: Boolean,
): List<String> = buildList {
    add("-P$PREVIEW_TARGETS_FILE_PROJECT_PROPERTY=${batchFile.toAbsolutePath().normalize()}")
    if (forceRerender) add("-P$PREVIEW_RERENDER_PROJECT_PROPERTY=true")
}

internal data class PreviewCatalogMatch(
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

    fun wasCacheHit(standardOutput: String): Boolean {
        return "$CACHE_HIT_MARKER ${responsePath.toAbsolutePath().normalize()}" in standardOutput
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

internal data class PreviewSelectionMatch(
    val selection: PreviewSourceSelection,
    val match: PreviewCatalogMatch,
)

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

private fun PreviewGradleResult.isMissingTaskFailure(taskName: String): Boolean {
    if (exitCode == 0) return false
    val output = "$errorOutput\n$standardOutput".lowercase()
    return taskName.lowercase() in output && "not found in project" in output
}

private fun PreviewGradleResult.isUnknownPreviewFailure(descriptorId: String): Boolean {
    if (exitCode == 0) return false
    val output = "$errorOutput\n$standardOutput"
    return "Unknown ViewCompose preview '$descriptorId'" in output
}

private fun PreviewRenderOutcome.withPerformancePhase(
    phase: String,
    durationMillis: Long,
    shared: Boolean = false,
): PreviewRenderOutcome {
    return when (this) {
        is PreviewRenderOutcome.Success -> copy(
            performanceTrace = performanceTrace.plus(
                phase = phase,
                durationMillis = durationMillis,
                shared = shared,
            ),
        )
        is PreviewRenderOutcome.Failure -> this
    }
}

private fun elapsedMillis(startedAtNanos: Long): Long {
    return ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND)
        .coerceAtLeast(0L)
}

internal fun loadBoundedPreviewImage(path: Path): BufferedImage {
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
private const val BUILD_MANIFEST_FILE_NAME = "build-manifest.json"
private const val RESPONSE_FILE_NAME = "response.json"
private const val CACHE_HIT_MARKER = "ViewCompose preview cache hit:"
private const val MAXIMUM_GRADLE_OUTPUT_LENGTH = 30_000
private const val MAXIMUM_PREVIEW_IMAGE_BYTES = 64L * 1024L * 1024L
// Keep Studio's decoded-image ceiling aligned with the renderer's auto-height capture budget.
private const val MAXIMUM_PREVIEW_IMAGE_PIXELS = 16_000_000L
private const val NANOS_PER_MILLISECOND = 1_000_000L
private const val DEFAULT_RENDER_LOGICAL_WIDTH_DP = 411
private const val PREFERRED_DISCOVERY_TASK_NAME = "discoverDebugViewComposePreviews"
private const val AGGREGATE_DISCOVERY_TASK_NAME = "viewComposePreviewDescriptors"
private const val PREFERRED_BUILD_VARIANT = "debug"
private const val PREVIEW_ID_PROJECT_PROPERTY = "viewComposePreviewId"
private const val PREVIEW_VARIANT_ID_PROJECT_PROPERTY = "viewComposePreviewVariantId"
private const val PREVIEW_TARGETS_FILE_PROJECT_PROPERTY = "viewComposePreviewTargetsFile"
private const val PREVIEW_RERENDER_PROJECT_PROPERTY = "viewComposePreviewRerender"
private val GRADLE_PROJECT_SEGMENT = Regex("[A-Za-z0-9_.-]+")
