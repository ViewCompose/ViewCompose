package com.viewcompose.preview.runner

import android.content.Context
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.host.android.RenderSession
import com.viewcompose.host.android.renderInto
import com.viewcompose.preview.tooling.PreviewCompositionLocal
import com.viewcompose.preview.tooling.PreviewCompositionSnapshot
import com.viewcompose.preview.tooling.PreviewDiagnostic
import com.viewcompose.preview.tooling.PreviewDiagnosticSeverity
import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewNodeBindingStats
import com.viewcompose.preview.tooling.PreviewPatchRecord
import com.viewcompose.preview.tooling.PreviewRecomposeScope
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderSnapshot
import com.viewcompose.preview.tooling.PreviewRenderStats
import com.viewcompose.preview.tooling.PreviewRenderStructure
import com.viewcompose.preview.tooling.PreviewRenderTreeNode
import com.viewcompose.preview.tooling.PreviewSourceCallSite
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.tooling.UiNodeToolingMetadata
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.widget.core.AndroidDynamicColorPolicy
import com.viewcompose.widget.core.AndroidThemeBridge
import com.viewcompose.widget.core.RenderFailure
import com.viewcompose.widget.core.RenderTreeResult
import com.viewcompose.widget.core.UiEnvironment
import com.viewcompose.widget.core.UiTheme
import java.io.Closeable
import kotlin.math.roundToInt

/**
 * Mounts a single deterministic ViewCompose frame into a plain Android View hierarchy.
 *
 * This layer owns no screenshot engine. Layoutlib, a device host, or a test backend can capture
 * the resulting [StaticPreviewFrame] without changing composition behavior.
 */
object StaticPreviewRenderer {
    fun mount(
        context: Context,
        request: PreviewRenderRequest,
        entry: StaticPreviewEntry,
    ): StaticPreviewMountResult {
        if (request.descriptor != entry.descriptor) {
            return StaticPreviewMountResult.Failure(
                diagnostic = request.renderDiagnostic(
                    message = "Compiled preview entry does not match the requested descriptor.",
                    phase = "entry-resolution",
                    details = "Requested '${request.descriptor.id}', resolved " +
                        "'${entry.descriptor.id}'.",
                ),
            )
        }

        val configuration = request.configuration
        val requestedApiLevel = configuration.apiLevel
        if (requestedApiLevel != null && requestedApiLevel != Build.VERSION.SDK_INT) {
            return StaticPreviewMountResult.Failure(
                diagnostic = request.renderDiagnostic(
                    message = "Preview worker API level does not match the request.",
                    phase = "environment",
                    details = "Requested API $requestedApiLevel, worker API " +
                        "${Build.VERSION.SDK_INT}.",
                ),
            )
        }
        val previewContext = PreviewAndroidContextFactory.create(context, configuration)
        val widthPx = (configuration.widthDp * configuration.density).roundToInt()
            .coerceAtLeast(1)
        val heightPx = (configuration.heightDp * configuration.density).roundToInt()
            .coerceAtLeast(1)
        // Resolve the same Android application theme as a real host. Dynamic color is disabled so
        // one preview request remains reproducible across Studio, Gradle, and CI machines.
        val resolvedTheme = AndroidThemeBridge.resolveContext(
            context = previewContext,
            dynamicColorPolicy = AndroidDynamicColorPolicy.Disabled,
        )
        val themeTokens = AndroidThemeBridge.fromResolvedTheme(resolvedTheme)
        val root = FrameLayout(resolvedTheme.context).apply {
            setBackgroundColor(themeTokens.colors.background)
        }
        var renderResult: RenderTreeResult? = null
        var renderFailure: RenderFailure? = null
        var session: RenderSession? = null

        return UiNodeTooling.withSourceCapture {
            try {
                session = renderInto(
                container = root,
                debug = false,
                debugTag = "ViewComposePreview",
                onRenderResult = { result -> renderResult = result },
                onRenderFailure = { failure -> renderFailure = failure },
            ) {
                UiEnvironment(
                    values = UiEnvironmentValues(
                        density = UiDensity(
                            density = configuration.density,
                            fontScale = configuration.fontScale,
                        ),
                        locales = UiLocaleList.from(configuration.localeTags),
                        layoutDirection = when (configuration.layoutDirection) {
                            PreviewLayoutDirection.Ltr -> UiLayoutDirection.Ltr
                            PreviewLayoutDirection.Rtl -> UiLayoutDirection.Rtl
                        },
                    ),
                ) {
                    UiTheme(
                        tokens = themeTokens,
                    ) {
                        entry.content(this)
                    }
                }
            }
            session?.setRenderingActive(false)
            root.layoutDirection = when (configuration.layoutDirection) {
                PreviewLayoutDirection.Ltr -> View.LAYOUT_DIRECTION_LTR
                PreviewLayoutDirection.Rtl -> View.LAYOUT_DIRECTION_RTL
            }

            val failure = renderFailure
            val result = renderResult
            if (failure != null || result == null) {
                session?.dispose()
                StaticPreviewMountResult.Failure(
                    diagnostic = request.renderDiagnostic(
                        message = failure?.let {
                            "Preview render failed during ${it.phase}."
                        } ?: "Preview render completed without diagnostics.",
                        phase = failure?.phase?.name ?: "render",
                        details = failure?.cause?.stackTraceToString(),
                    ),
                )
            } else {
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, widthPx, heightPx)
                StaticPreviewMountResult.Success(
                    frame = StaticPreviewFrame(
                        rootView = root,
                        snapshot = result.toPreviewSnapshot(root),
                        session = checkNotNull(session),
                    ),
                )
            }
            } catch (error: Throwable) {
                error.throwIfFatalPreviewWorkerError()
                session?.dispose()
                StaticPreviewMountResult.Failure(
                    diagnostic = request.renderDiagnostic(
                        message = "Preview render threw ${error::class.java.simpleName}.",
                        phase = "render",
                        details = error.stackTraceToString(),
                    ),
                )
            }
        }
    }
}

sealed interface StaticPreviewMountResult {
    data class Success(
        val frame: StaticPreviewFrame,
    ) : StaticPreviewMountResult

    data class Failure(
        val diagnostic: PreviewDiagnostic,
    ) : StaticPreviewMountResult
}

class StaticPreviewFrame internal constructor(
    val rootView: View,
    val snapshot: PreviewRenderSnapshot,
    private val session: RenderSession,
) : Closeable {
    override fun close() {
        session.dispose()
    }
}

private fun PreviewRenderRequest.renderDiagnostic(
    message: String,
    phase: String,
    details: String? = null,
): PreviewDiagnostic {
    return PreviewDiagnostic(
        severity = PreviewDiagnosticSeverity.Error,
        message = message,
        phase = phase,
        sourceLocation = descriptor.sourceLocation,
        details = details,
    )
}

private fun RenderTreeResult.toPreviewSnapshot(rootView: View): PreviewRenderSnapshot {
    val nativeViewCapture = PreviewNativeViewSnapshotter.capture(rootView)
    return PreviewRenderSnapshot(
        stats = PreviewRenderStats(
            inserts = stats.inserts,
            reuses = stats.reuses,
            removals = stats.removals,
            reboundNodes = stats.reboundNodes,
            patchedNodes = stats.patchedNodes,
            skippedBindings = stats.skippedBindings,
            skippedSubtrees = stats.skippedSubtrees,
            bindingsByType = stats.bindingsByType.entries
                .sortedBy { (type, _) -> type.toString() }
                .associate { (type, value) ->
                    type.toString() to PreviewNodeBindingStats(
                        rebound = value.rebound,
                        patched = value.patched,
                        skipped = value.skipped,
                    )
                },
        ),
        structure = PreviewRenderStructure(
            vnodeCount = structure.vnodeCount,
            mountedNodeCount = structure.mountedNodeCount,
            maxVNodeDepth = structure.maxVNodeDepth,
            maxMountedDepth = structure.maxMountedDepth,
        ),
        warnings = warnings,
        tree = tree.map { node ->
            val tooling = node.toolingMetadata
            PreviewRenderTreeNode(
                type = node.type.toString(),
                key = node.key?.toString(),
                nodeId = tooling?.nodeId,
                sourceCallSites = tooling.toPreviewSourceCallSites(),
                synthetic = tooling?.synthetic == true,
                children = node.children.toPreviewTreeNodes(),
            )
        },
        nativeViewTree = nativeViewCapture.roots,
        layoutDiagnostics = nativeViewCapture.layoutDiagnostics,
        patches = patches.map { patch ->
            val tooling = patch.toolingMetadata
            PreviewPatchRecord(
                operation = patch.operation.name,
                type = patch.type.toString(),
                key = patch.key?.toString(),
                parentKey = patch.parentKey?.toString(),
                index = patch.index,
                moved = patch.moved,
                detail = patch.detail,
                nodeId = tooling?.nodeId,
                sourceCallSites = tooling.toPreviewSourceCallSites(),
                synthetic = tooling?.synthetic == true,
            )
        },
        composition = PreviewCompositionSnapshot(
            invalidatedScopeCount = composition.invalidatedScopeCount,
            recomposedScopeCount = composition.recomposedScopeCount,
            skippedScopeCount = composition.skippedScopeCount,
            scopes = composition.scopes.map { scope ->
                PreviewRecomposeScope(
                    path = scope.path,
                    signature = scope.signature,
                    depth = scope.depth,
                    reasons = scope.reasons.map { reason -> reason.name }.sorted(),
                    recomposed = scope.recomposed,
                    skipped = scope.skipped,
                    locals = scope.locals.map { local ->
                        PreviewCompositionLocal(
                            name = local.name,
                            value = local.value,
                        )
                    },
                    sourceCallSites = scope.sourceCallSites.map { source ->
                        PreviewSourceCallSite(
                            className = source.className,
                            methodName = source.methodName,
                            fileName = source.fileName,
                            lineNumber = source.lineNumber,
                        )
                    },
                )
            },
        ),
    )
}

private fun List<com.viewcompose.widget.core.RenderTreeNode>.toPreviewTreeNodes():
    List<PreviewRenderTreeNode> {
    return map { node ->
        val tooling = node.toolingMetadata
        PreviewRenderTreeNode(
            type = node.type.toString(),
            key = node.key?.toString(),
            nodeId = tooling?.nodeId,
            sourceCallSites = tooling.toPreviewSourceCallSites(),
            synthetic = tooling?.synthetic == true,
            children = node.children.toPreviewTreeNodes(),
        )
    }
}

internal fun UiNodeToolingMetadata?.toPreviewSourceCallSites(): List<PreviewSourceCallSite> {
    return this?.callSites.orEmpty().map { source ->
        PreviewSourceCallSite(
            className = source.className,
            methodName = source.methodName,
            fileName = source.fileName,
            lineNumber = source.lineNumber,
        )
    }
}
