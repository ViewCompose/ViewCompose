package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.MAX_PREVIEW_WORKER_BATCH_SIZE
import com.viewcompose.preview.tooling.PreviewBuildInputKind
import com.viewcompose.preview.tooling.PreviewBuildManifest
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.tooling.PreviewRenderStatus
import com.viewcompose.preview.tooling.PreviewWorkerBatchCommand
import com.viewcompose.preview.tooling.PreviewWorkerCommand
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault

/**
 * Renders one preview or a bounded batch in a short-lived JVM, backed by a content-addressed cache.
 */
@DisableCachingByDefault(
    because = "The task owns a content-addressed render cache and launches an isolated JVM.",
)
abstract class RenderViewComposePreviewTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val previewId: Property<String>

    @get:Input
    @get:Optional
    abstract val variantId: Property<String>

    @get:Input
    abstract val rerender: Property<Boolean>

    @get:Input
    abstract val verifyWorkerReuse: Property<Boolean>

    @get:Input
    abstract val workerMainClass: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildManifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val descriptorCatalogFile: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val batchTargetsFile: RegularFileProperty

    @get:Classpath
    abstract val workerHostClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val runnerClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val layoutlibRuntimeArchive: ConfigurableFileCollection

    @get:Classpath
    abstract val layoutlibResourcesArchive: ConfigurableFileCollection

    init {
        rerender.convention(false)
        verifyWorkerReuse.convention(false)
        workerMainClass.convention(DEFAULT_WORKER_MAIN_CLASS)
    }

    @Option(
        option = "preview-id",
        description = "Stable ViewCompose preview id from descriptors.json.",
    )
    fun selectPreview(value: String) {
        previewId.set(value)
    }

    @Option(
        option = "variant-id",
        description = "Optional preview variant id; omitted only when the preview has one variant.",
    )
    fun selectVariant(value: String) {
        variantId.set(value)
    }

    @Option(
        option = "preview-targets-file",
        description = "TSV file containing preview-id and variant-id pairs to render in one build.",
    )
    fun selectPreviewTargetsFile(value: String) {
        batchTargetsFile.set(File(value))
    }

    @Option(
        option = "rerender",
        description = "Ignore an existing successful content-addressed render.",
    )
    fun forceRerender(value: Boolean) {
        rerender.set(value)
    }

    @Option(
        option = "verify-worker-reuse",
        description = "Render warm and cold paths and fail unless pixels and structure match.",
    )
    fun verifyWorkerReuse(value: Boolean) {
        verifyWorkerReuse.set(value)
    }

    @TaskAction
    fun renderPreview() {
        require(Runtime.version().feature() >= MINIMUM_JAVA_VERSION) {
            "ViewCompose static preview requires JDK $MINIMUM_JAVA_VERSION or newer."
        }
        require(batchTargetsFile.isPresent.xor(previewId.isPresent)) {
            "Specify exactly one of --preview-id or --preview-targets-file."
        }
        require(!batchTargetsFile.isPresent || !variantId.isPresent) {
            "--variant-id cannot be combined with --preview-targets-file."
        }
        val manifest = PreviewProtocolJson.decodeBuildManifest(
            buildManifestFile.get().asFile.readText(),
        )
        val catalog = PreviewProtocolJson.decodeDescriptorCatalog(
            descriptorCatalogFile.get().asFile.readText(),
        )
        val renderRuntimeFingerprint = PreviewInputFingerprint.calculate(
            mapOf(
                "worker-host-classpath" to workerHostClasspath.files,
                "runner-classpath" to runnerClasspath.files,
                "layoutlib-runtime" to layoutlibRuntimeArchive.files,
                "layoutlib-resources" to layoutlibResourcesArchive.files,
            ),
        )
        val targets = if (batchTargetsFile.isPresent) {
            batchTargetsFile.get().asFile.readPreviewBatchTargets()
        } else {
            listOf(PreviewBatchTarget(previewId.get(), variantId.orNull ?: ""))
        }
        val plans = targets.map { target ->
            planPreviewRender(
                manifest = manifest,
                catalog = catalog,
                previewId = target.previewId,
                requestedVariantId = target.variantId.ifBlank { null },
                renderRuntimeFingerprint = renderRuntimeFingerprint,
            )
        }
        val pendingPlans = plans.filterNot { plan ->
            val responseFile = plan.responseFile(manifest)
            val cacheHit = !rerender.get() &&
                responseFile.isSuccessfulCachedResponse(expectedRequestId = plan.requestId)
            if (cacheHit) {
                logger.lifecycle("ViewCompose preview cache hit: ${responseFile.absolutePath}")
            }
            cacheHit
        }
        if (pendingPlans.isEmpty()) return

        val runtimeRoot = materializeArchive(
            archive = layoutlibRuntimeArchive.requireSingleFile("Layoutlib runtime"),
            label = "layoutlib-runtime",
        )
        val resourcesRoot = materializeArchive(
            archive = layoutlibResourcesArchive.requireSingleFile("Layoutlib resources"),
            label = "layoutlib-resources",
        )
        val hostFiles = workerHostClasspath.files
        val runnerFiles = runnerClasspath.files
        require(hostFiles.isNotEmpty()) {
            "No ViewCompose preview worker is configured. Add the worker host and Android runner " +
                "distributions to the '$WORKER_HOST_CONFIGURATION_NAME' and " +
                "'$RUNNER_CONFIGURATION_NAME' configurations."
        }
        val reloadableClasspath = manifest.paths(
            PreviewBuildInputKind.ProjectClassDirectory,
            PreviewBuildInputKind.ProjectClassJar,
        )
        val retainedRuntimeClasspath = manifest.paths(
            PreviewBuildInputKind.RuntimeClasspath,
            PreviewBuildInputKind.BootClasspath,
        )
        val resourceSymbols = PreviewResourceSymbolClasspath.materialize(
            projectClasspath = reloadableClasspath,
            artifactRoot = File(manifest.artifactRootDirectory),
            compatibilityFingerprint = manifest.layoutlibCompatibilityFingerprint,
        )
        val processClasspath = (
            hostFiles + runnerFiles + retainedRuntimeClasspath + resourceSymbols
            ).distinctBy { file -> file.absoluteFile.normalize().path }
        val workerCompatibilityFingerprint = PreviewInputFingerprint.combine(
            mapOf(
                "layoutlib-environment" to manifest.layoutlibCompatibilityFingerprint,
                "render-runtime" to renderRuntimeFingerprint,
            ),
        )
        val prepared = pendingPlans.map { plan ->
            preparePlan(
                plan = plan,
                manifest = manifest,
                runtimeRoot = runtimeRoot,
                resourcesRoot = resourcesRoot,
                renderClasspath = reloadableClasspath,
            )
        }
        val failures = if (batchTargetsFile.isPresent) {
            prepared.chunked(MAX_PREVIEW_WORKER_BATCH_SIZE)
                .flatMapIndexed { batchIndex, batch ->
                    executeBatch(
                        batch = batch,
                        batchIndex = batchIndex,
                        processClasspath = processClasspath,
                        compatibilityFingerprint = workerCompatibilityFingerprint,
                        artifactRoot = File(manifest.artifactRootDirectory),
                    )
                }
        } else {
            listOfNotNull(
                executeSingle(
                    prepared = prepared.single(),
                    processClasspath = processClasspath,
                    compatibilityFingerprint = workerCompatibilityFingerprint,
                    artifactRoot = File(manifest.artifactRootDirectory),
                ),
            )
        }
        if (failures.isNotEmpty() && !batchTargetsFile.isPresent) {
            throw failures.single().second
        }
        failures.forEach { (plan, error) ->
            logger.error(
                "ViewCompose preview '${plan.descriptor.id}/${plan.variant.id}' failed: " +
                    (error.message ?: error::class.java.simpleName),
            )
        }
    }

    private fun preparePlan(
        plan: PreviewRenderPlan,
        manifest: PreviewBuildManifest,
        runtimeRoot: File,
        resourcesRoot: File,
        renderClasspath: List<File>,
    ): PreparedPreviewExecution {
        val outputDirectory = File(
            manifest.artifactRootDirectory,
            plan.cacheRelativeDirectory,
        )
        val responseFile = outputDirectory.resolve(RESPONSE_FILE_NAME)
        check(outputDirectory.isDirectory || outputDirectory.mkdirs()) {
            "Could not create ViewCompose preview output '${outputDirectory.absolutePath}'."
        }
        if (responseFile.exists()) {
            check(responseFile.delete()) {
                "Could not invalidate stale ViewCompose preview response " +
                    "'${responseFile.absolutePath}'."
            }
        }
        val request = PreviewRenderRequest(
            requestId = plan.requestId,
            descriptor = plan.descriptor,
            variantId = plan.variant.id,
            modulePath = manifest.modulePath,
            buildVariant = manifest.buildVariant,
            buildFingerprint = manifest.inputFingerprint,
            outputDirectory = outputDirectory.absolutePath,
        )
        val requestFile = outputDirectory.resolve(REQUEST_FILE_NAME).apply {
            writeTextAtomically(PreviewProtocolJson.encodeRequest(request))
        }
        val commandFile = outputDirectory.resolve(COMMAND_FILE_NAME)
        val command = PreviewWorkerCommand(
            buildManifestPath = buildManifestFile.get().asFile.absolutePath,
            renderRequestPath = requestFile.absolutePath,
            renderResponsePath = responseFile.absolutePath,
            layoutlibRuntimeRoot = runtimeRoot.absolutePath,
            layoutlibResourcesRoot = resourcesRoot.absolutePath,
            renderClasspath = renderClasspath.map(File::getAbsolutePath),
        )
        commandFile.writeTextAtomically(PreviewProtocolJson.encodeWorkerCommand(command))
        return PreparedPreviewExecution(
            plan = plan,
            request = request,
            responseFile = responseFile,
            command = command,
            commandFile = commandFile,
        )
    }

    private fun executeSingle(
        prepared: PreparedPreviewExecution,
        processClasspath: List<File>,
        compatibilityFingerprint: String,
        artifactRoot: File,
    ): Pair<PreviewRenderPlan, Throwable>? {
        val firstAttempt = runCatching {
            val exitValue = executeWorker(
                commandFile = prepared.commandFile,
                processClasspath = processClasspath,
                renderClasspath = prepared.command.renderClasspath.map(::File),
                compatibilityFingerprint = compatibilityFingerprint,
                artifactRoot = artifactRoot,
            )
            validateExecution(prepared, exitValue)
            if (verifyWorkerReuse.get() && workerMainClass.get() == DEFAULT_WORKER_MAIN_CLASS) {
                verifyWorkerReuse(
                    commandFile = prepared.commandFile,
                    prepared = listOf(prepared),
                    processClasspath = processClasspath,
                    compatibilityFingerprint = compatibilityFingerprint,
                    artifactRoot = artifactRoot,
                )
            }
        }
        val firstError = firstAttempt.exceptionOrNull() ?: return null
        if (workerMainClass.get() != DEFAULT_WORKER_MAIN_CLASS) {
            return prepared.plan to firstError
        }
        logger.warn(
            "Warm ViewCompose preview failed; retrying once with a cold worker.",
            firstError,
        )
        return runCatching {
            val coldExit = executeIsolatedWorker(prepared.commandFile, processClasspath)
            validateExecution(prepared, coldExit)
        }.exceptionOrNull()?.let { coldError -> prepared.plan to coldError }
    }

    private fun executeBatch(
        batch: List<PreparedPreviewExecution>,
        batchIndex: Int,
        processClasspath: List<File>,
        compatibilityFingerprint: String,
        artifactRoot: File,
    ): List<Pair<PreviewRenderPlan, Throwable>> {
        val batchCommandFile = temporaryDir.resolve("batch-command-$batchIndex.json")
        batchCommandFile.writeTextAtomically(
            PreviewProtocolJson.encodeWorkerBatchCommand(
                PreviewWorkerBatchCommand(commands = batch.map(PreparedPreviewExecution::command)),
            ),
        )
        val executionResult = runCatching {
            executeWorker(
                commandFile = batchCommandFile,
                processClasspath = processClasspath,
                renderClasspath = batch.flatMap { prepared ->
                    prepared.command.renderClasspath.map(::File)
                }.distinctBy(File::getAbsolutePath),
                compatibilityFingerprint = compatibilityFingerprint,
                artifactRoot = artifactRoot,
            )
        }
        val executionError = executionResult.exceptionOrNull()
        var failures = batch.mapNotNull { prepared ->
            val error = executionError ?: runCatching {
                validateExecution(prepared, checkNotNull(executionResult.getOrNull()))
            }.exceptionOrNull()
            error?.let { prepared.plan to it }
        }
        if (failures.isNotEmpty() && workerMainClass.get() == DEFAULT_WORKER_MAIN_CLASS) {
            logger.warn(
                "Warm ViewCompose preview batch failed; retrying once with a cold worker.",
                failures.first().second,
            )
            val coldResult = runCatching {
                executeIsolatedWorker(batchCommandFile, processClasspath)
            }
            val coldError = coldResult.exceptionOrNull()
            failures = batch.mapNotNull { prepared ->
                val error = coldError ?: runCatching {
                    validateExecution(prepared, checkNotNull(coldResult.getOrNull()))
                }.exceptionOrNull()
                error?.let { prepared.plan to it }
            }
        }
        if (
            failures.isEmpty() &&
            verifyWorkerReuse.get() &&
            workerMainClass.get() == DEFAULT_WORKER_MAIN_CLASS
        ) {
            val verificationError = runCatching {
                verifyWorkerReuse(
                    commandFile = batchCommandFile,
                    prepared = batch,
                    processClasspath = processClasspath,
                    compatibilityFingerprint = compatibilityFingerprint,
                    artifactRoot = artifactRoot,
                )
            }.exceptionOrNull()
            if (verificationError != null) {
                return batch.map { prepared -> prepared.plan to verificationError }
            }
        }
        return failures
    }

    private fun executeWorker(
        commandFile: File,
        processClasspath: List<File>,
        renderClasspath: List<File>,
        compatibilityFingerprint: String,
        artifactRoot: File,
    ): Int {
        if (workerMainClass.get() == DEFAULT_WORKER_MAIN_CLASS) {
            val workerDirectory = artifactRoot.resolve("worker")
            val persistent = runCatching {
                PersistentPreviewWorkerClient(
                    endpointFile = workerDirectory.resolve("endpoint.properties"),
                    logFile = workerDirectory.resolve("worker.log"),
                    compatibilityFingerprint = compatibilityFingerprint,
                    processClasspath = processClasspath,
                    mainClass = workerMainClass.get(),
                ).execute(commandFile)
            }
            persistent.getOrNull()?.let { result ->
                logger.lifecycle(
                    "ViewCompose preview worker ${result.processId}: " +
                        "${result.message} processed=${result.processedCommands} " +
                        "retiring=${result.retiring}",
                )
                return 0
            }
            logger.warn(
                "Persistent ViewCompose preview worker failed; retrying in an isolated JVM.",
                persistent.exceptionOrNull(),
            )
        }
        val execution = execOperations.javaexec { spec ->
            val classpath = if (workerMainClass.get() == DEFAULT_WORKER_MAIN_CLASS) {
                processClasspath
            } else {
                processClasspath + renderClasspath
            }
            spec.classpath(classpath)
            spec.mainClass.set(workerMainClass)
            spec.args(commandFile.absolutePath)
            spec.jvmArgs("-Djava.awt.headless=true")
            spec.isIgnoreExitValue = true
        }
        return execution.exitValue
    }

    private fun verifyWorkerReuse(
        commandFile: File,
        prepared: List<PreparedPreviewExecution>,
        processClasspath: List<File>,
        compatibilityFingerprint: String,
        artifactRoot: File,
    ) {
        val workerDirectory = artifactRoot.resolve("worker")
        val client = PersistentPreviewWorkerClient(
            endpointFile = workerDirectory.resolve("endpoint.properties"),
            logFile = workerDirectory.resolve("worker.log"),
            compatibilityFingerprint = compatibilityFingerprint,
            processClasspath = processClasspath,
            mainClass = workerMainClass.get(),
        )
        var warmExecution = client.execute(commandFile)
        if (warmExecution.processedCommands <= prepared.size) {
            warmExecution = client.execute(commandFile)
        }
        check(warmExecution.processedCommands > prepared.size) {
            "Could not obtain a proven warm Layoutlib render for verification."
        }
        prepared.forEach { execution -> validateExecution(execution, 0) }
        val warmArtifacts = prepared.mapIndexed { index, execution ->
            val response = PreviewProtocolJson.decodeResponse(execution.responseFile.readText())
            val artifacts = checkNotNull(response.artifacts)
            val warmDirectory = temporaryDir.resolve("worker-reuse-verification/warm-$index")
            check(warmDirectory.isDirectory || warmDirectory.mkdirs()) {
                "Could not create warm verification directory '${warmDirectory.absolutePath}'."
            }
            WarmPreviewArtifacts(
                image = File(checkNotNull(artifacts.imagePath)).copyTo(
                    warmDirectory.resolve("preview.png"),
                    overwrite = true,
                ),
                tree = File(checkNotNull(artifacts.renderTreePath)).copyTo(
                    warmDirectory.resolve("render-tree.json"),
                    overwrite = true,
                ),
            )
        }
        val coldExit = executeIsolatedWorker(commandFile, processClasspath)
        prepared.forEach { execution -> validateExecution(execution, coldExit) }
        prepared.zip(warmArtifacts).forEach { (execution, warm) ->
            val response = PreviewProtocolJson.decodeResponse(execution.responseFile.readText())
            val artifacts = checkNotNull(response.artifacts)
            PreviewWorkerReuseVerifier.requireEquivalent(
                warmImage = warm.image,
                coldImage = File(checkNotNull(artifacts.imagePath)),
                warmTree = warm.tree,
                coldTree = File(checkNotNull(artifacts.renderTreePath)),
            )
        }
        logger.lifecycle(
            "ViewCompose preview worker reuse verified against ${prepared.size} cold render(s).",
        )
    }

    private fun executeIsolatedWorker(
        commandFile: File,
        processClasspath: List<File>,
    ): Int {
        val execution = execOperations.javaexec { spec ->
            spec.classpath(processClasspath)
            spec.mainClass.set(workerMainClass)
            spec.args(commandFile.absolutePath)
            spec.jvmArgs("-Djava.awt.headless=true")
            spec.isIgnoreExitValue = true
        }
        return execution.exitValue
    }

    private fun validateExecution(
        prepared: PreparedPreviewExecution,
        exitValue: Int,
    ) {
        val responseFile = prepared.responseFile
        if (exitValue != 0 && !responseFile.isFile) {
            throw GradleException(
                "ViewCompose preview worker exited with code $exitValue " +
                    "without a response.",
            )
        }
        require(responseFile.isFile) {
            "ViewCompose preview worker did not write '${responseFile.absolutePath}'."
        }
        val response = PreviewProtocolJson.decodeResponse(responseFile.readText())
        validateResponse(prepared.request, response)
        if (response.status != PreviewRenderStatus.Success) {
            throw GradleException(
                buildString {
                    appendLine("ViewCompose preview render failed:")
                    response.diagnostics.forEach { diagnostic ->
                        append("- [${diagnostic.phase}] ${diagnostic.message}")
                        diagnostic.details?.let { details -> append(": $details") }
                        appendLine()
                    }
                    append("Structured response: ${responseFile.absolutePath}")
                },
            )
        }
        logger.lifecycle("ViewCompose preview rendered: ${responseFile.absolutePath}")
    }

    private fun materializeArchive(
        archive: File,
        label: String,
    ): File {
        if (archive.isDirectory) {
            return archive
        }
        require(archive.isFile) { "$label archive does not exist: '${archive.absolutePath}'." }
        val destination = temporaryDir.resolve(label)
        if (destination.exists()) {
            check(destination.deleteRecursively()) {
                "Could not clear temporary $label directory '${destination.absolutePath}'."
            }
        }
        check(destination.mkdirs()) {
            "Could not create temporary $label directory '${destination.absolutePath}'."
        }
        val canonicalRoot = destination.canonicalFile
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val target = File(destination, entry.name).canonicalFile
                require(
                    target == canonicalRoot ||
                        target.path.startsWith(canonicalRoot.path + File.separator),
                ) {
                    "Unsafe entry '${entry.name}' in $label archive '${archive.absolutePath}'."
                }
                if (entry.isDirectory) {
                    check(target.isDirectory || target.mkdirs()) {
                        "Could not create $label directory '${target.absolutePath}'."
                    }
                } else {
                    target.parentFile?.let { parent ->
                        check(parent.isDirectory || parent.mkdirs()) {
                            "Could not create $label directory '${parent.absolutePath}'."
                        }
                    }
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use(input::copyTo)
                    }
                }
            }
        }
        return destination
    }
}

private data class PreparedPreviewExecution(
    val plan: PreviewRenderPlan,
    val request: PreviewRenderRequest,
    val responseFile: File,
    val command: PreviewWorkerCommand,
    val commandFile: File,
)

private data class WarmPreviewArtifacts(
    val image: File,
    val tree: File,
)

private fun PreviewRenderPlan.responseFile(manifest: PreviewBuildManifest): File {
    return File(manifest.artifactRootDirectory, cacheRelativeDirectory).resolve(RESPONSE_FILE_NAME)
}

private fun PreviewBuildManifest.paths(
    vararg kinds: PreviewBuildInputKind,
): List<File> {
    val selected = kinds.toSet()
    return inputs.asSequence()
        .filter { input -> input.kind in selected }
        .flatMap { input -> input.paths.asSequence() }
        .map(::File)
        .filter(File::exists)
        .toList()
}

private fun ConfigurableFileCollection.requireSingleFile(label: String): File {
    require(files.size == 1) {
        "$label must resolve exactly one archive, but resolved: " +
            files.joinToString { file -> file.absolutePath }
    }
    return singleFile
}

private fun File.isSuccessfulCachedResponse(expectedRequestId: String): Boolean {
    if (!isFile) return false
    val response = runCatching {
        PreviewProtocolJson.decodeResponse(readText())
    }.getOrNull() ?: return false
    if (response.requestId != expectedRequestId) return false
    if (response.status != PreviewRenderStatus.Success) return false
    val artifacts = response.artifacts ?: return false
    return listOfNotNull(artifacts.imagePath, artifacts.renderTreePath)
        .all { path -> File(path).isFile }
}

private fun validateResponse(
    request: PreviewRenderRequest,
    response: PreviewRenderResponse,
) {
    require(response.requestId == request.requestId) {
        "Preview worker response request id '${response.requestId}' does not match " +
            "'${request.requestId}'."
    }
    require(response.previewId == request.descriptor.id) {
        "Preview worker response preview id '${response.previewId}' does not match " +
            "'${request.descriptor.id}'."
    }
    require(response.variantId == request.variantId) {
        "Preview worker response variant id '${response.variantId}' does not match " +
            "'${request.variantId}'."
    }
}

private fun File.writeTextAtomically(value: String) {
    parentFile?.let { parent ->
        check(parent.isDirectory || parent.mkdirs()) {
            "Could not create ViewCompose preview directory '${parent.absolutePath}'."
        }
    }
    val temporary = File(checkNotNull(parentFile), "$name.tmp")
    temporary.writeText(value)
    if (exists()) {
        check(delete()) { "Could not replace ViewCompose preview file '$absolutePath'." }
    }
    check(temporary.renameTo(this)) {
        "Could not publish ViewCompose preview file '$absolutePath'."
    }
}

internal const val WORKER_HOST_CONFIGURATION_NAME = "viewComposePreviewWorkerHost"
internal const val RUNNER_CONFIGURATION_NAME = "viewComposePreviewRunner"
private const val DEFAULT_WORKER_MAIN_CLASS =
    "com.viewcompose.preview.worker.PreviewWorkerHost"
private const val MINIMUM_JAVA_VERSION = 17
private const val REQUEST_FILE_NAME = "request.json"
private const val COMMAND_FILE_NAME = "command.json"
private const val RESPONSE_FILE_NAME = "response.json"
