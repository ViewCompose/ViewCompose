package com.viewcompose.preview.worker

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Environment
import app.cash.paparazzi.PaparazziSdk
import com.android.resources.Density
import com.android.resources.LayoutDirection
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.viewcompose.preview.tooling.PreviewBuildInputKind
import com.viewcompose.preview.tooling.PreviewBuildManifest
import com.viewcompose.preview.tooling.PreviewDiagnostic
import com.viewcompose.preview.tooling.PreviewDiagnosticSeverity
import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewPhaseTiming
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.tooling.PreviewRenderStatus
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.PreviewWorkerCommand
import com.viewcompose.preview.tooling.viewportHeightDp
import java.io.File
import java.net.URLClassLoader
import kotlin.math.roundToInt
import kotlinx.serialization.json.jsonObject

/**
 * Standalone JVM boundary that owns Layoutlib and isolates reloadable preview bytecode.
 *
 * Each command validates its manifest/request identity, installs a fresh child class loader for
 * reloadable project classes, prepares and tears down one Paparazzi/Layoutlib SDK, writes one atomic
 * response, restores the thread context loader, and closes the child loader. Non-fatal render
 * failures become structured responses; fatal VM errors continue to escape.
 */
object PreviewWorkerHost {
    /**
     * Runs one-shot command/batch mode or the bounded loopback server.
     *
     * One-shot mode accepts exactly one command JSON path and distinguishes a batch by its top-level
     * `commands` field. Server mode accepts `--server`, an endpoint path, and a compatibility
     * fingerprint. Malformed protocol files or invalid argument counts fail the process; render
     * failures that reach request decoding are written as response data.
     *
     * @param args command-line arguments supplied by the Gradle bridge
     */
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.firstOrNull() == WORKER_SERVER_ARGUMENT) {
            require(args.size == 3) {
                "Expected --server, endpoint path, and compatibility fingerprint."
            }
            PreviewWorkerServer(
                endpointFile = File(args[1]),
                compatibilityFingerprint = args[2],
            ).run()
            return
        }
        require(args.size == 1) {
            "Expected one argument containing the PreviewWorkerCommand JSON path."
        }
        val commandFile = File(args.single())
        val commandJson = commandFile.readText()
        if (PreviewProtocolJson.format.parseToJsonElement(commandJson).jsonObject.containsKey("commands")) {
            executeBatch(commandJson)
        } else {
            execute(commandJson)
        }
    }

    /**
     * Executes one worker command from [commandFile] and returns the response also written to the
     * command's response path.
     *
     * The request, manifest, build fingerprint, module, variant, Layoutlib roots, and every exported
     * input are validated before rendering. Non-fatal failures after request decoding produce a
     * `RenderFailure` response with source-aware diagnostics. Response publication is atomic.
     *
     * @sample com.viewcompose.preview.worker.samples.executeWorkerCommandSample
     * @throws IllegalArgumentException for malformed/unsupported command or request JSON
     * @throws java.io.IOException when required protocol files cannot be read or the response cannot be published
     */
    fun execute(commandFile: File): PreviewRenderResponse {
        return execute(commandFile.readText())
    }

    internal fun executeBatch(commandFile: File): List<PreviewRenderResponse> {
        return executeBatch(commandFile.readText())
    }

    private fun executeBatch(commandJson: String): List<PreviewRenderResponse> {
        val batch = PreviewProtocolJson.decodeWorkerBatchCommand(commandJson)
        return batch.commands.map(::execute)
    }

    private fun execute(commandJson: String): PreviewRenderResponse {
        val command = PreviewProtocolJson.decodeWorkerCommand(commandJson)
        return execute(command)
    }

    private fun execute(command: PreviewWorkerCommand): PreviewRenderResponse {
        val requestFile = File(command.renderRequestPath)
        val request = PreviewProtocolJson.decodeRequest(requestFile.readText())
        val response = try {
            withPreviewRenderClassLoader(command.renderClasspath) {
                val manifest = PreviewProtocolJson.decodeBuildManifest(
                    File(command.buildManifestPath).readText(),
                )
                validateCommand(
                    command = command,
                    manifest = manifest,
                    request = request,
                )
                render(
                    command = command,
                    manifest = manifest,
                    request = request,
                )
            }
        } catch (error: Throwable) {
            error.throwIfFatal()
            request.failureResponse(error)
        }
        File(command.renderResponsePath).writeTextAtomically(
            PreviewProtocolJson.encodeResponse(response),
        )
        return response
    }

    internal fun environmentFor(manifest: PreviewBuildManifest): Environment {
        return Environment(
            appTestDir = manifest.artifactRootDirectory,
            packageName = manifest.namespace,
            compileSdkVersion = manifest.compileSdk,
            resourcePackageNames = manifest.resourcePackageNames,
            localResourceDirs = manifest.paths(PreviewBuildInputKind.LocalResourceDirectory),
            moduleResourceDirs = manifest.paths(PreviewBuildInputKind.ModuleResourceDirectory),
            libraryResourceDirs = manifest.paths(PreviewBuildInputKind.LibraryResourceDirectory),
            allModuleAssetDirs =
                manifest.paths(PreviewBuildInputKind.LocalAssetDirectory) +
                    manifest.paths(PreviewBuildInputKind.ModuleAssetDirectory),
            libraryAssetDirs = manifest.paths(PreviewBuildInputKind.LibraryAssetDirectory),
        )
    }

    internal fun deviceConfigFor(request: PreviewRenderRequest): DeviceConfig {
        val configuration = request.configuration
        val viewportHeightDp = configuration.viewportHeightDp
        val densityDpi = (configuration.density * DENSITY_DEFAULT)
            .roundToInt()
            .coerceAtLeast(1)
        return DeviceConfig.PIXEL_5.copy(
            screenWidth = (configuration.widthDp * configuration.density).roundToInt(),
            screenHeight = (viewportHeightDp * configuration.density).roundToInt(),
            xdpi = densityDpi,
            ydpi = densityDpi,
            orientation = if (configuration.widthDp > viewportHeightDp) {
                ScreenOrientation.LANDSCAPE
            } else {
                ScreenOrientation.PORTRAIT
            },
            nightMode = when (configuration.theme) {
                PreviewTheme.Light -> NightMode.NOTNIGHT
                PreviewTheme.Dark -> NightMode.NIGHT
            },
            density = Density.create(densityDpi),
            fontScale = configuration.fontScale,
            layoutDirection = when (configuration.layoutDirection) {
                PreviewLayoutDirection.Ltr -> LayoutDirection.LTR
                PreviewLayoutDirection.Rtl -> LayoutDirection.RTL
            },
            locale = configuration.localeTags.first().toAndroidResourceLocale(),
            softButtons = false,
        )
    }

    internal fun themeFor(
        manifest: PreviewBuildManifest,
        previewTheme: PreviewTheme,
    ): String {
        val fallback = when (previewTheme) {
            PreviewTheme.Light -> "android:Theme.Material.Light.NoActionBar"
            PreviewTheme.Dark -> "android:Theme.Material.NoActionBar"
        }
        val mergedManifest = File(manifest.mergedManifestPath)
        if (!mergedManifest.isFile) return fallback
        val applicationTag = APPLICATION_TAG_PATTERN
            .find(mergedManifest.readText())
            ?.value
            ?: return fallback
        val themeReference = ANDROID_THEME_PATTERN
            .find(applicationTag)
            ?.groupValues
            ?.get(1)
            ?: return fallback
        return when {
            themeReference.startsWith("@style/") ->
                themeReference.removePrefix("@style/")

            themeReference.startsWith("@android:style/") ->
                "android:${themeReference.removePrefix("@android:style/")}"

            else -> fallback
        }
    }

    private fun render(
        command: PreviewWorkerCommand,
        manifest: PreviewBuildManifest,
        request: PreviewRenderRequest,
    ): PreviewRenderResponse {
        System.setProperty(LAYOUTLIB_RUNTIME_PROPERTY, command.layoutlibRuntimeRoot)
        System.setProperty(LAYOUTLIB_RESOURCES_PROPERTY, command.layoutlibResourcesRoot)
        val sdk = PaparazziSdk(
            environment = environmentFor(manifest),
            deviceConfig = deviceConfigFor(request),
            theme = themeFor(manifest, request.configuration.theme),
            supportsRtl = true,
            onNewFrame = {},
        )
        val setupStartedAtNanos = System.nanoTime()
        sdk.setup()
        var prepared = false
        var response: PreviewRenderResponse? = null
        var teardownTiming: PreviewPhaseTiming? = null
        try {
            sdk.prepare()
            prepared = true
            val setupTiming = phaseTiming("layoutlib-setup", setupStartedAtNanos)
            val runnerResponse = invokeRunner(
                context = sdk.context,
                request = request,
            )
            response = runnerResponse.copy(
                phaseTimings = listOf(setupTiming) + runnerResponse.phaseTimings,
            )
        } finally {
            if (prepared) {
                val teardownStartedAtNanos = System.nanoTime()
                sdk.teardown()
                teardownTiming = phaseTiming("layoutlib-teardown", teardownStartedAtNanos)
            }
        }
        val rendered = checkNotNull(response)
        return rendered.copy(
            phaseTimings = rendered.phaseTimings + checkNotNull(teardownTiming),
        )
    }

    private fun invokeRunner(
        context: Any,
        request: PreviewRenderRequest,
    ): PreviewRenderResponse {
        val classLoader = checkNotNull(Thread.currentThread().contextClassLoader)
        val workerClass = classLoader.loadClass(RUNNER_CLASS_NAME)
        val worker = workerClass.getDeclaredConstructor().newInstance()
        val method = workerClass.methods.single { candidate ->
            candidate.name == "render" &&
                candidate.parameterTypes.size == 3 &&
                candidate.parameterTypes[1] == PreviewRenderRequest::class.java &&
                candidate.parameterTypes[2] == ClassLoader::class.java
        }
        return method.invoke(worker, context, request, classLoader) as PreviewRenderResponse
    }

    private fun validateCommand(
        command: PreviewWorkerCommand,
        manifest: PreviewBuildManifest,
        request: PreviewRenderRequest,
    ) {
        require(request.modulePath == manifest.modulePath) {
            "Render request module '${request.modulePath}' does not match " +
                "'${manifest.modulePath}'."
        }
        require(request.buildVariant == manifest.buildVariant) {
            "Render request variant '${request.buildVariant}' does not match " +
                "'${manifest.buildVariant}'."
        }
        require(request.buildFingerprint == manifest.inputFingerprint) {
            "Render request fingerprint '${request.buildFingerprint}' does not match " +
                "'${manifest.inputFingerprint}'."
        }
        require(File(command.layoutlibRuntimeRoot).isDirectory) {
            "Layoutlib runtime root does not exist: '${command.layoutlibRuntimeRoot}'."
        }
        require(File(command.layoutlibResourcesRoot).isDirectory) {
            "Layoutlib resources root does not exist: '${command.layoutlibResourcesRoot}'."
        }
        manifest.inputs.flatMap { input -> input.paths }.forEach { path ->
            require(File(path).exists()) {
                "Preview build input no longer exists: '$path'."
            }
        }
    }
}

internal inline fun <T> withPreviewRenderClassLoader(
    renderClasspath: List<String>,
    parent: ClassLoader = PreviewWorkerHost::class.java.classLoader,
    block: () -> T,
): T {
    if (renderClasspath.isEmpty()) return block()
    val thread = Thread.currentThread()
    val previous = thread.contextClassLoader
    val loader = URLClassLoader(
        renderClasspath.map { path -> File(path).toURI().toURL() }.toTypedArray(),
        parent,
    )
    thread.contextClassLoader = loader
    return try {
        block()
    } finally {
        thread.contextClassLoader = previous
        loader.close()
    }
}

private fun PreviewBuildManifest.paths(kind: PreviewBuildInputKind): List<String> {
    return inputs.firstOrNull { input -> input.kind == kind }?.paths.orEmpty()
}

private fun phaseTiming(
    phase: String,
    startedAtNanos: Long,
): PreviewPhaseTiming {
    val durationMillis = ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND)
        .coerceAtLeast(0L)
    return PreviewPhaseTiming(phase = phase, durationMillis = durationMillis)
}

private fun PreviewRenderRequest.failureResponse(error: Throwable): PreviewRenderResponse {
    val cause = generateSequence(error) { current -> current.cause }.last()
    return PreviewRenderResponse(
        requestId = requestId,
        previewId = descriptor.id,
        variantId = variantId,
        status = PreviewRenderStatus.RenderFailure,
        diagnostics = listOf(
            PreviewDiagnostic(
                severity = PreviewDiagnosticSeverity.Error,
                message = "Preview worker host failed: ${cause::class.java.simpleName}.",
                phase = "worker-host",
                sourceLocation = descriptor.sourceLocation,
                details = error.stackTraceToString(),
            ),
        ),
    )
}

private fun String.toAndroidResourceLocale(): String {
    val parts = split('-')
    return if (parts.size >= 2) {
        "${parts[0]}-r${parts[1].uppercase()}"
    } else {
        this
    }
}

private fun File.writeTextAtomically(value: String) {
    parentFile?.let { parent ->
        check(parent.isDirectory || parent.mkdirs()) {
            "Could not create preview worker output directory '${parent.absolutePath}'."
        }
    }
    val temporary = File(checkNotNull(parentFile), "$name.tmp")
    temporary.writeText(value)
    if (exists()) {
        check(delete()) { "Could not replace preview worker response '$absolutePath'." }
    }
    check(temporary.renameTo(this)) {
        "Could not publish preview worker response '$absolutePath'."
    }
}

private fun Throwable.throwIfFatal() {
    if (this is ThreadDeath || this is OutOfMemoryError) {
        throw this
    }
}

private const val RUNNER_CLASS_NAME = "com.viewcompose.preview.runner.StaticPreviewWorker"
private const val WORKER_SERVER_ARGUMENT = "--server"
private const val LAYOUTLIB_RUNTIME_PROPERTY = "paparazzi.layoutlib.runtime.root"
private const val LAYOUTLIB_RESOURCES_PROPERTY = "paparazzi.layoutlib.resources.root"
private const val DENSITY_DEFAULT = 160
private const val NANOS_PER_MILLISECOND = 1_000_000L
private val APPLICATION_TAG_PATTERN = Regex(
    pattern = """<application\b[^>]*>""",
    option = RegexOption.DOT_MATCHES_ALL,
)
private val ANDROID_THEME_PATTERN = Regex(
    pattern = """\bandroid:theme\s*=\s*["']([^"']+)["']""",
)
