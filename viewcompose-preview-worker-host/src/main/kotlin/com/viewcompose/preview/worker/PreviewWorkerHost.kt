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
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.tooling.PreviewRenderStatus
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.PreviewWorkerCommand
import java.io.File
import kotlin.math.roundToInt

/**
 * Standalone JVM entry point that owns one Layoutlib lifecycle and one render request.
 */
object PreviewWorkerHost {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) {
            "Expected one argument containing the PreviewWorkerCommand JSON path."
        }
        execute(File(args.single()))
    }

    fun execute(commandFile: File): PreviewRenderResponse {
        val command = PreviewProtocolJson.decodeWorkerCommand(commandFile.readText())
        val requestFile = File(command.renderRequestPath)
        val request = PreviewProtocolJson.decodeRequest(requestFile.readText())
        val response = try {
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
        val densityDpi = (configuration.density * DENSITY_DEFAULT)
            .roundToInt()
            .coerceAtLeast(1)
        return DeviceConfig.PIXEL_5.copy(
            screenWidth = (configuration.widthDp * configuration.density).roundToInt(),
            screenHeight = (configuration.heightDp * configuration.density).roundToInt(),
            xdpi = densityDpi,
            ydpi = densityDpi,
            orientation = if (configuration.widthDp > configuration.heightDp) {
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
            theme = when (request.configuration.theme) {
                PreviewTheme.Light -> "android:Theme.Material.Light.NoActionBar"
                PreviewTheme.Dark -> "android:Theme.Material.NoActionBar"
            },
            supportsRtl = true,
            onNewFrame = {},
        )
        sdk.setup()
        var prepared = false
        return try {
            sdk.prepare()
            prepared = true
            invokeRunner(
                context = sdk.context,
                request = request,
            )
        } finally {
            if (prepared) {
                sdk.teardown()
            }
        }
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

private fun PreviewBuildManifest.paths(kind: PreviewBuildInputKind): List<String> {
    return inputs.firstOrNull { input -> input.kind == kind }?.paths.orEmpty()
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
private const val LAYOUTLIB_RUNTIME_PROPERTY = "paparazzi.layoutlib.runtime.root"
private const val LAYOUTLIB_RESOURCES_PROPERTY = "paparazzi.layoutlib.resources.root"
private const val DENSITY_DEFAULT = 160
