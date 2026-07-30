package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.PreviewBuildInputKind
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.tooling.PreviewRenderStatus
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
 * Selects and renders one preview in a fresh JVM, backed by a content-addressed artifact cache.
 */
@DisableCachingByDefault(
    because = "The task owns a content-addressed render cache and launches an isolated JVM.",
)
abstract class RenderViewComposePreviewTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Input
    abstract val previewId: Property<String>

    @get:Input
    @get:Optional
    abstract val variantId: Property<String>

    @get:Input
    abstract val rerender: Property<Boolean>

    @get:Input
    abstract val workerMainClass: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildManifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val descriptorCatalogFile: RegularFileProperty

    @get:Classpath
    abstract val workerClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val layoutlibRuntimeArchive: ConfigurableFileCollection

    @get:Classpath
    abstract val layoutlibResourcesArchive: ConfigurableFileCollection

    init {
        rerender.convention(false)
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
        option = "rerender",
        description = "Ignore an existing successful content-addressed render.",
    )
    fun forceRerender(value: Boolean) {
        rerender.set(value)
    }

    @TaskAction
    fun renderPreview() {
        require(Runtime.version().feature() >= MINIMUM_JAVA_VERSION) {
            "ViewCompose static preview requires JDK $MINIMUM_JAVA_VERSION or newer."
        }
        val manifest = PreviewProtocolJson.decodeBuildManifest(
            buildManifestFile.get().asFile.readText(),
        )
        val catalog = PreviewProtocolJson.decodeDescriptorCatalog(
            descriptorCatalogFile.get().asFile.readText(),
        )
        val plan = planPreviewRender(
            manifest = manifest,
            catalog = catalog,
            previewId = previewId.get(),
            requestedVariantId = variantId.orNull,
        )
        val outputDirectory = File(
            manifest.artifactRootDirectory,
            plan.cacheRelativeDirectory,
        )
        val responseFile = outputDirectory.resolve(RESPONSE_FILE_NAME)
        if (!rerender.get() && responseFile.isSuccessfulCachedResponse()) {
            logger.lifecycle(
                "ViewCompose preview cache hit: ${responseFile.absolutePath}",
            )
            return
        }
        check(outputDirectory.isDirectory || outputDirectory.mkdirs()) {
            "Could not create ViewCompose preview output '${outputDirectory.absolutePath}'."
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
        val runtimeRoot = materializeArchive(
            archive = layoutlibRuntimeArchive.requireSingleFile("Layoutlib runtime"),
            label = "layoutlib-runtime",
        )
        val resourcesRoot = materializeArchive(
            archive = layoutlibResourcesArchive.requireSingleFile("Layoutlib resources"),
            label = "layoutlib-resources",
        )
        val commandFile = outputDirectory.resolve(COMMAND_FILE_NAME)
        commandFile.writeTextAtomically(
            PreviewProtocolJson.encodeWorkerCommand(
                PreviewWorkerCommand(
                    buildManifestPath = buildManifestFile.get().asFile.absolutePath,
                    renderRequestPath = requestFile.absolutePath,
                    renderResponsePath = responseFile.absolutePath,
                    layoutlibRuntimeRoot = runtimeRoot.absolutePath,
                    layoutlibResourcesRoot = resourcesRoot.absolutePath,
                ),
            ),
        )
        val hostFiles = workerClasspath.files
        require(hostFiles.isNotEmpty()) {
            "No ViewCompose preview worker is configured. Add the worker host and Android runner " +
                "distributions to the '$WORKER_HOST_CONFIGURATION_NAME' and " +
                "'$RUNNER_CONFIGURATION_NAME' configurations."
        }
        val applicationClasspath = manifest.inputs.asSequence()
            .filter { input ->
                input.kind == PreviewBuildInputKind.ProjectClassDirectory ||
                    input.kind == PreviewBuildInputKind.ProjectClassJar ||
                    input.kind == PreviewBuildInputKind.RuntimeClasspath
            }
            .flatMap { input -> input.paths.asSequence() }
            .map(::File)
            .toList()
        val execution = execOperations.javaexec { spec ->
            spec.classpath(hostFiles + applicationClasspath)
            spec.mainClass.set(workerMainClass)
            spec.args(commandFile.absolutePath)
            spec.jvmArgs("-Djava.awt.headless=true")
            spec.isIgnoreExitValue = true
        }
        if (execution.exitValue != 0 && !responseFile.isFile) {
            throw GradleException(
                "ViewCompose preview worker exited with code ${execution.exitValue} " +
                    "without a response.",
            )
        }
        require(responseFile.isFile) {
            "ViewCompose preview worker did not write '${responseFile.absolutePath}'."
        }
        val response = PreviewProtocolJson.decodeResponse(responseFile.readText())
        validateResponse(request, response)
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

private fun ConfigurableFileCollection.requireSingleFile(label: String): File {
    require(files.size == 1) {
        "$label must resolve exactly one archive, but resolved: " +
            files.joinToString { file -> file.absolutePath }
    }
    return singleFile
}

private fun File.isSuccessfulCachedResponse(): Boolean {
    if (!isFile) return false
    val response = runCatching {
        PreviewProtocolJson.decodeResponse(readText())
    }.getOrNull() ?: return false
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
