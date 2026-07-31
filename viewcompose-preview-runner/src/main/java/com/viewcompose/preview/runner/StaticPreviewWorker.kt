package com.viewcompose.preview.runner

import android.content.Context
import com.viewcompose.preview.tooling.PreviewArtifacts
import com.viewcompose.preview.tooling.PreviewDiagnostic
import com.viewcompose.preview.tooling.PreviewDiagnosticSeverity
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewPhaseTiming
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderResponse
import com.viewcompose.preview.tooling.PreviewRenderStatus
import java.io.File

/**
 * Executes one isolated static-preview request and materializes immutable artifacts.
 */
class StaticPreviewWorker(
    private val captureBackend: StaticPreviewCaptureBackend = AndroidBitmapCaptureBackend,
) {
    fun render(
        context: Context,
        request: PreviewRenderRequest,
        classLoader: ClassLoader,
    ): PreviewRenderResponse {
        val startedAtNanos = System.nanoTime()
        val resolutionStartedAtNanos = System.nanoTime()
        return when (
            val resolution = PreviewJvmEntryPointResolver.resolve(
                descriptor = request.descriptor,
                classLoader = classLoader,
            )
        ) {
            is PreviewEntryResolutionResult.Success -> renderResolved(
                context = context,
                request = request,
                entry = resolution.entry,
                initialTimings = listOf(
                    phaseTiming("entry-resolution", resolutionStartedAtNanos),
                ),
            )

            is PreviewEntryResolutionResult.Failure -> failureResponse(
                request = request,
                startedAtNanos = startedAtNanos,
                diagnostic = resolution.diagnostic,
                phaseTimings = listOf(
                    phaseTiming("entry-resolution", resolutionStartedAtNanos),
                ),
            )
        }
    }

    fun render(
        context: Context,
        request: PreviewRenderRequest,
        entry: StaticPreviewEntry,
    ): PreviewRenderResponse {
        return renderResolved(
            context = context,
            request = request,
            entry = entry,
            initialTimings = emptyList(),
        )
    }

    private fun renderResolved(
        context: Context,
        request: PreviewRenderRequest,
        entry: StaticPreviewEntry,
        initialTimings: List<PreviewPhaseTiming>,
    ): PreviewRenderResponse {
        val startedAtNanos = System.nanoTime()
        val mountStartedAtNanos = System.nanoTime()
        return when (val mount = StaticPreviewRenderer.mount(context, request, entry)) {
            is StaticPreviewMountResult.Failure -> failureResponse(
                request = request,
                startedAtNanos = startedAtNanos,
                diagnostic = mount.diagnostic,
                phaseTimings = initialTimings + phaseTiming("mount-layout", mountStartedAtNanos),
            )

            is StaticPreviewMountResult.Success -> mount.frame.use { frame ->
                try {
                    val phaseTimings = initialTimings.toMutableList().apply {
                        add(phaseTiming("mount-layout", mountStartedAtNanos))
                    }
                    val outputDirectory = File(request.outputDirectory)
                    ensureOutputDirectory(outputDirectory)
                    val imageFile = outputDirectory.resolve("preview.png")
                    val treeFile = outputDirectory.resolve("render-tree.json")
                    val imageExportStartedAtNanos = System.nanoTime()
                    writeAtomically(imageFile) { temporary ->
                        captureBackend.capture(frame.rootView, temporary)
                    }
                    phaseTimings += phaseTiming("image-export", imageExportStartedAtNanos)
                    val snapshotExportStartedAtNanos = System.nanoTime()
                    writeAtomically(treeFile) { temporary ->
                        temporary.writeText(
                            PreviewProtocolJson.encodeRenderSnapshot(frame.snapshot),
                        )
                    }
                    phaseTimings += phaseTiming("snapshot-export", snapshotExportStartedAtNanos)
                    PreviewRenderResponse(
                        requestId = request.requestId,
                        previewId = request.descriptor.id,
                        variantId = request.variantId,
                        status = PreviewRenderStatus.Success,
                        artifacts = PreviewArtifacts(
                            imagePath = imageFile.absolutePath,
                            renderTreePath = treeFile.absolutePath,
                        ),
                        diagnostics = frame.snapshot.warnings.map { warning ->
                            PreviewDiagnostic(
                                severity = PreviewDiagnosticSeverity.Warning,
                                message = warning,
                                phase = "render-diagnostics",
                                sourceLocation = request.descriptor.sourceLocation,
                            )
                        },
                        durationMillis = elapsedMillis(startedAtNanos),
                        phaseTimings = phaseTimings,
                    )
                } catch (error: Throwable) {
                    error.throwIfFatalPreviewWorkerError()
                    failureResponse(
                        request = request,
                        startedAtNanos = startedAtNanos,
                        diagnostic = PreviewDiagnostic(
                            severity = PreviewDiagnosticSeverity.Error,
                            message = "Failed to export static preview artifacts.",
                            phase = "artifact-export",
                            sourceLocation = request.descriptor.sourceLocation,
                            details = error.stackTraceToString(),
                        ),
                        phaseTimings = initialTimings,
                    )
                }
            }
        }
    }

    private fun failureResponse(
        request: PreviewRenderRequest,
        startedAtNanos: Long,
        diagnostic: PreviewDiagnostic,
        phaseTimings: List<PreviewPhaseTiming> = emptyList(),
    ): PreviewRenderResponse {
        return PreviewRenderResponse(
            requestId = request.requestId,
            previewId = request.descriptor.id,
            variantId = request.variantId,
            status = PreviewRenderStatus.RenderFailure,
            diagnostics = listOf(diagnostic),
            durationMillis = elapsedMillis(startedAtNanos),
            phaseTimings = phaseTimings,
        )
    }

    private fun ensureOutputDirectory(directory: File) {
        if (!directory.exists()) {
            check(directory.mkdirs()) {
                "Could not create preview output directory '${directory.absolutePath}'."
            }
        }
        require(directory.isDirectory) {
            "Preview output path '${directory.absolutePath}' is not a directory."
        }
    }

    private inline fun writeAtomically(
        target: File,
        write: (File) -> Unit,
    ) {
        val temporary = File(checkNotNull(target.parentFile), "${target.name}.tmp")
        if (temporary.exists()) {
            check(temporary.delete()) {
                "Could not replace temporary preview artifact '${temporary.absolutePath}'."
            }
        }
        try {
            write(temporary)
            if (target.exists()) {
                check(target.delete()) {
                    "Could not replace preview artifact '${target.absolutePath}'."
                }
            }
            check(temporary.renameTo(target)) {
                "Could not publish preview artifact '${target.absolutePath}'."
            }
        } finally {
            temporary.delete()
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Long {
        return ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND)
            .coerceAtLeast(0L)
    }

    private fun phaseTiming(
        phase: String,
        startedAtNanos: Long,
    ): PreviewPhaseTiming {
        return PreviewPhaseTiming(phase = phase, durationMillis = elapsedMillis(startedAtNanos))
    }

    private companion object {
        const val NANOS_PER_MILLISECOND: Long = 1_000_000L
    }
}

internal fun Throwable.throwIfFatalPreviewWorkerError() {
    if (this is ThreadDeath || this is OutOfMemoryError) {
        throw this
    }
}
