package com.viewcompose.preview.runner

import android.app.Application
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.viewcompose.host.android.RenderSession
import com.viewcompose.host.android.renderInto
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.preview.PreviewThemeResolution
import com.viewcompose.preview.tooling.PreviewCompositionLocal
import com.viewcompose.preview.tooling.PreviewCompositionSnapshot
import com.viewcompose.preview.tooling.PreviewDiagnostic
import com.viewcompose.preview.tooling.PreviewDiagnosticSeverity
import com.viewcompose.preview.tooling.PreviewDefaults
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
import com.viewcompose.preview.tooling.isAutoHeight
import com.viewcompose.preview.tooling.viewportHeightDp
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.tooling.UiNodeToolingMetadata
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.material3.Material3DynamicColorPolicy
import com.viewcompose.material3.Material3ThemeBridge
import com.viewcompose.ui.foundation.ProvideSaveableStateRegistry
import com.viewcompose.ui.foundation.RenderFailure
import com.viewcompose.ui.foundation.RenderTreeResult
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import java.io.Closeable
import kotlin.math.roundToInt

/**
 * Mounts a single deterministic ViewCompose frame into a plain Android View hierarchy.
 *
 * This layer owns no screenshot engine. Layoutlib, a device host, or a test backend can capture
 * the resulting [StaticPreviewFrame] without changing composition behavior.
 */
object StaticPreviewRenderer {
    /**
     * Mounts [entry] using the deterministic environment described by [request].
     *
     * The descriptor must exactly match the request. The renderer resolves application or Android
     * theme tokens, installs frame-scoped lifecycle, ViewModel, saveable-state, environment, and
     * theme owners, performs one synchronous render, and lays out the native tree. Auto-height
     * requests expand eligible scrollable roots only up to the runner's density and pixel budgets.
     *
     * A successful frame owns a live render session and host owners and therefore must be closed.
     * Expected entry, environment, theme, render, and layout failures are returned as diagnostics;
     * thread death and out-of-memory errors escape.
     *
     * @return a closeable mounted frame or a source-aware failure
     * @sample com.viewcompose.preview.runner.samples.mountStaticPreviewSample
     */
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
        val viewportHeightPx = (configuration.viewportHeightDp * configuration.density).roundToInt()
            .coerceAtLeast(1)
        val resolvedPreviewTheme = entry.themeProvider?.let { provider ->
            try {
                provider.resolve(
                    context = previewContext,
                    theme = configuration.theme,
                )
            } catch (error: Throwable) {
                error.throwIfFatalPreviewWorkerError()
                return StaticPreviewMountResult.Failure(
                    diagnostic = request.renderDiagnostic(
                        message = "Application preview theme provider failed.",
                        phase = "theme-provider",
                        details = error.stackTraceToString(),
                    ),
                )
            }
        } ?: run {
            // The default path mirrors the native Android host. Dynamic color stays disabled so a
            // request remains reproducible across Studio, Gradle, and CI machines.
            val resolvedAndroidTheme = Material3ThemeBridge.resolveContext(
                context = previewContext,
                dynamicColorPolicy = Material3DynamicColorPolicy.Disabled,
            )
            PreviewThemeResolution(
                context = resolvedAndroidTheme.context,
                tokens = Material3ThemeBridge.fromResolvedTheme(resolvedAndroidTheme),
            )
        }
        val themeTokens = resolvedPreviewTheme.tokens
        val root = FrameLayout(resolvedPreviewTheme.context).apply {
            setBackgroundColor(themeTokens.colors.background)
        }
        val previewOwner = StaticPreviewHostOwner(
            resolvedPreviewTheme.context.applicationContext as? Application,
        )
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
                ProvideLifecycleOwner(previewOwner) {
                    ProvideViewModelStoreOwner(previewOwner) {
                        ProvideSaveableStateRegistry(previewOwner.compositionSaveableStateRegistry) {
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
                previewOwner.close()
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
                val layoutResult = root.layoutForPreview(
                    widthPx = widthPx,
                    viewportHeightPx = viewportHeightPx,
                    autoHeight = configuration.isAutoHeight,
                    maxHeightPx = autoHeightLimitPx(
                        widthPx = widthPx,
                        density = configuration.density,
                        viewportHeightPx = viewportHeightPx,
                    ),
                )
                StaticPreviewMountResult.Success(
                    frame = StaticPreviewFrame(
                        rootView = root,
                        snapshot = result.toPreviewSnapshot(
                            rootView = root,
                            additionalWarnings = layoutResult.warnings,
                        ),
                        session = checkNotNull(session),
                        previewOwner = previewOwner,
                    ),
                )
            }
            } catch (error: Throwable) {
                error.throwIfFatalPreviewWorkerError()
                session?.dispose()
                previewOwner.close()
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

private data class PreviewRootLayoutResult(
    val heightPx: Int,
    val warnings: List<String> = emptyList(),
)

/**
 * Measures one concrete viewport first, then expands only when a scrollable descendant actually
 * grows with the root. This keeps intentionally fixed nested scrollers fixed while allowing
 * fill-parent page containers such as LazyColumn/RecyclerView to expose their complete content.
 */
private fun View.layoutForPreview(
    widthPx: Int,
    viewportHeightPx: Int,
    autoHeight: Boolean,
    maxHeightPx: Int,
): PreviewRootLayoutResult {
    layoutExactly(widthPx, viewportHeightPx)
    if (!autoHeight) {
        return PreviewRootLayoutResult(heightPx = viewportHeightPx)
    }

    var currentHeightPx = viewportHeightPx
    var scrollableHeights = forwardScrollableDescendantHeights()
    if (scrollableHeights.isEmpty()) {
        return PreviewRootLayoutResult(heightPx = currentHeightPx)
    }

    var expandableScrollers: Set<View>? = null
    while (currentHeightPx < maxHeightPx) {
        val nextHeightPx = minOf(
            maxHeightPx,
            maxOf(currentHeightPx * 2, currentHeightPx + viewportHeightPx),
        )
        layoutExactly(widthPx, nextHeightPx)
        val nextScrollableHeights = forwardScrollableDescendantHeights()
        val growingScrollers = scrollableHeights.mapNotNullTo(linkedSetOf()) { (view, height) ->
            view.takeIf { nextScrollableHeights.getOrDefault(view, view.height) > height }
        }
        if (expandableScrollers == null) {
            expandableScrollers = growingScrollers
        }
        val targets = checkNotNull(expandableScrollers)
        if (targets.isEmpty()) {
            layoutExactly(widthPx, currentHeightPx)
            return PreviewRootLayoutResult(heightPx = currentHeightPx)
        }
        if (targets.none { view -> view.canScrollVertically(1) }) {
            val resolvedHeightPx = findMinimumExpandedHeight(
                widthPx = widthPx,
                lowerScrollableHeightPx = currentHeightPx,
                upperCompleteHeightPx = nextHeightPx,
                scrollTargets = targets,
            )
            return PreviewRootLayoutResult(heightPx = resolvedHeightPx)
        }
        if (growingScrollers.none(targets::contains)) {
            layoutExactly(widthPx, currentHeightPx)
            return PreviewRootLayoutResult(
                heightPx = currentHeightPx,
                warnings = listOf(
                    "Auto-height preview could not expand a vertically scrollable container " +
                        "beyond ${currentHeightPx}px.",
                ),
            )
        }

        currentHeightPx = nextHeightPx
        scrollableHeights = nextScrollableHeights
    }

    return PreviewRootLayoutResult(
        heightPx = currentHeightPx,
        warnings = if (expandableScrollers.orEmpty().none { view ->
                view.canScrollVertically(1)
            }
        ) {
            emptyList()
        } else {
            listOf(
                "Auto-height preview reached its bounded capture limit at ${currentHeightPx}px " +
                    "while vertical content could still scroll.",
            )
        },
    )
}

private fun View.findMinimumExpandedHeight(
    widthPx: Int,
    lowerScrollableHeightPx: Int,
    upperCompleteHeightPx: Int,
    scrollTargets: Set<View>,
): Int {
    var lower = lowerScrollableHeightPx
    var upper = upperCompleteHeightPx
    while (upper - lower > 1) {
        val candidate = lower + ((upper - lower) / 2)
        layoutExactly(widthPx, candidate)
        if (scrollTargets.any { view -> view.canScrollVertically(1) }) {
            lower = candidate
        } else {
            upper = candidate
        }
    }
    // RecyclerView can report that forward scrolling has ended one physical pixel before its
    // final child is fully inside the viewport. Keep the result inside the already-proven complete
    // upper bound while stepping past that boolean transition boundary.
    val guardedHeightPx = minOf(
        upperCompleteHeightPx,
        upper + AUTO_HEIGHT_COMPLETION_GUARD_PX,
    )
    layoutExactly(widthPx, guardedHeightPx)
    return guardedHeightPx
}

private fun View.layoutExactly(
    widthPx: Int,
    heightPx: Int,
) {
    measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
    )
    layout(0, 0, widthPx, heightPx)
}

private fun View.forwardScrollableDescendantHeights(
    destination: MutableMap<View, Int> = linkedMapOf(),
): Map<View, Int> {
    if (visibility == View.VISIBLE && canScrollVertically(1)) {
        destination[this] = height
    }
    if (this is ViewGroup) {
        repeat(childCount) { index ->
            getChildAt(index).forwardScrollableDescendantHeights(destination)
        }
    }
    return destination
}

private fun autoHeightLimitPx(
    widthPx: Int,
    density: Float,
    viewportHeightPx: Int,
): Int {
    val densityLimit = (PreviewDefaults.MAX_AUTO_HEIGHT_DP * density).roundToInt()
    val pixelBudgetLimit = (MAX_AUTO_CAPTURE_PIXELS / widthPx.coerceAtLeast(1))
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    return maxOf(
        viewportHeightPx,
        minOf(densityLimit, pixelBudgetLimit),
    )
}

private const val AUTO_HEIGHT_COMPLETION_GUARD_PX = 1

/** Result of mounting and laying out one static preview frame. */
sealed interface StaticPreviewMountResult {
    /**
     * Indicates that [frame] is ready for capture and inspection.
     *
     * @property frame mounted frame whose ownership transfers to the caller
     */
    data class Success(
        val frame: StaticPreviewFrame,
    ) : StaticPreviewMountResult

    /**
     * Indicates that mounting failed before a frame could be transferred.
     *
     * @property diagnostic source-aware description of the failed render phase
     */
    data class Failure(
        val diagnostic: PreviewDiagnostic,
    ) : StaticPreviewMountResult
}

/**
 * A mounted Android View hierarchy and immutable tooling snapshot for one preview request.
 *
 * The frame owns its render session plus lifecycle, ViewModel, and saveable-state host. Call
 * [close] exactly once after capture or inspection. Closing disposes the render session first and
 * always releases the host owner, even when session disposal fails.
 *
 * @property rootView measured and laid-out native root ready for synchronous capture
 * @property snapshot immutable render, composition, patch, native View, source, and layout data
 */
class StaticPreviewFrame internal constructor(
    val rootView: View,
    val snapshot: PreviewRenderSnapshot,
    private val session: RenderSession,
    private val previewOwner: StaticPreviewHostOwner,
) : Closeable {
    /** Releases the render session and all frame-scoped Android owners. */
    override fun close() {
        try {
            session.dispose()
        } finally {
            previewOwner.close()
        }
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

private fun RenderTreeResult.toPreviewSnapshot(
    rootView: View,
    additionalWarnings: List<String> = emptyList(),
): PreviewRenderSnapshot {
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
        warnings = (warnings + additionalWarnings).distinct(),
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

private const val MAX_AUTO_CAPTURE_PIXELS: Long = 16_000_000L

private fun List<com.viewcompose.ui.foundation.RenderTreeNode>.toPreviewTreeNodes():
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
