package com.viewcompose.preview.runner

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.viewcompose.preview.tooling.PreviewClippingState
import com.viewcompose.preview.tooling.PreviewDiagnosticSeverity
import com.viewcompose.preview.tooling.PreviewLayoutBounds
import com.viewcompose.preview.tooling.PreviewLayoutDiagnostic
import com.viewcompose.preview.tooling.PreviewLayoutDiagnosticKind
import com.viewcompose.preview.tooling.PreviewNativeViewNode
import com.viewcompose.preview.tooling.PreviewSourceCallSite
import com.viewcompose.renderer.view.tree.ViewNodeToolingRegistry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PreviewNativeViewCapture(
    val roots: List<PreviewNativeViewNode>,
    val layoutDiagnostics: List<PreviewLayoutDiagnostic>,
)

/**
 * Captures post-layout Android View geometry without retaining runtime Views in the tooling model.
 */
internal object PreviewNativeViewSnapshotter {
    fun capture(rootView: View): PreviewNativeViewCapture {
        val diagnostics = mutableListOf<PreviewLayoutDiagnostic>()
        val root = captureNode(
            view = rootView,
            absoluteLeft = 0,
            absoluteTop = 0,
            inheritedClip = null,
            isRoot = true,
            diagnostics = diagnostics,
        )
        return PreviewNativeViewCapture(
            roots = listOf(root),
            layoutDiagnostics = diagnostics
                .distinctBy { diagnostic ->
                    listOf(
                        diagnostic.kind,
                        diagnostic.nodeId,
                        diagnostic.className,
                        diagnostic.bounds,
                    )
                }
                .sortedWith(
                    compareByDescending<PreviewLayoutDiagnostic> { diagnostic ->
                        diagnostic.severity == PreviewDiagnosticSeverity.Error
                    }
                        .thenByDescending { diagnostic ->
                            diagnostic.severity == PreviewDiagnosticSeverity.Warning
                        }
                        .thenBy { diagnostic -> diagnostic.bounds.top }
                        .thenBy { diagnostic -> diagnostic.bounds.left },
                ),
        )
    }

    private fun captureNode(
        view: View,
        absoluteLeft: Int,
        absoluteTop: Int,
        inheritedClip: ClipRegion?,
        isRoot: Boolean,
        diagnostics: MutableList<PreviewLayoutDiagnostic>,
    ): PreviewNativeViewNode {
        val tooling = ViewNodeToolingRegistry.metadataOf(view)
        val sourceCallSites = tooling.toPreviewSourceCallSites()
        val nodeLeft = absoluteLeft + view.translationX.roundToInt()
        val nodeTop = absoluteTop + view.translationY.roundToInt()
        val bounds = PreviewLayoutBounds(
            left = nodeLeft,
            top = nodeTop,
            right = nodeLeft + view.width,
            bottom = nodeTop + view.height,
        )
        val ownClip = view.clipBounds?.let { clip ->
            ClipRegion(
                bounds = PreviewLayoutBounds(
                    left = nodeLeft + clip.left,
                    top = nodeTop + clip.top,
                    right = nodeLeft + clip.right,
                    bottom = nodeTop + clip.bottom,
                ),
                sourceClassName = view.javaClass.name,
                sourceNodeId = tooling?.nodeId,
                expected = false,
            )
        }
        val visibleGeometry = visibleGeometry(
            bounds = bounds,
            inheritedClip = inheritedClip,
            ownClip = ownClip,
        )
        val clippingState = when {
            !bounds.hasArea() -> PreviewClippingState.NotClipped
            visibleGeometry.bounds == null -> PreviewClippingState.FullyClipped
            visibleGeometry.bounds != bounds -> PreviewClippingState.PartiallyClipped
            else -> PreviewClippingState.NotClipped
        }
        val visibleBounds = visibleGeometry.bounds.takeIf {
            view.visibility == View.VISIBLE && bounds.hasArea()
        }

        if (
            !isRoot &&
            view.visibility == View.VISIBLE &&
            sourceCallSites.isNotEmpty() &&
            tooling?.synthetic != true
        ) {
            collectGeometryDiagnostics(
                view = view,
                bounds = bounds,
                visibleBounds = visibleBounds,
                clippingState = clippingState,
                clippingSource = visibleGeometry.clippingSource,
                nodeId = tooling?.nodeId,
                sourceCallSites = sourceCallSites,
                diagnostics = diagnostics,
            )
            collectTextDiagnostics(
                view = view,
                bounds = bounds,
                visibleBounds = visibleBounds,
                nodeId = tooling?.nodeId,
                sourceCallSites = sourceCallSites,
                diagnostics = diagnostics,
            )
        }

        val childClip = childClipRegion(
            view = view,
            bounds = bounds,
            inheritedClip = inheritedClip,
            ownClip = ownClip,
            nodeId = tooling?.nodeId,
        )
        val childOriginLeft = nodeLeft - view.scrollX
        val childOriginTop = nodeTop - view.scrollY
        val childNodes = if (view is ViewGroup) {
            List(view.childCount) { index ->
                val child = view.getChildAt(index)
                captureNode(
                    view = child,
                    absoluteLeft = childOriginLeft + child.left,
                    absoluteTop = childOriginTop + child.top,
                    inheritedClip = childClip,
                    isRoot = false,
                    diagnostics = diagnostics,
                )
            }
        } else {
            emptyList()
        }
        return PreviewNativeViewNode(
            className = view.javaClass.name,
            bounds = bounds,
            measuredWidth = view.measuredWidth,
            measuredHeight = view.measuredHeight,
            visibility = view.visibilityName(),
            visibleBounds = visibleBounds,
            clippingState = clippingState,
            clippingAncestorClassName = visibleGeometry.clippingSource?.sourceClassName,
            clippingAncestorNodeId = visibleGeometry.clippingSource?.sourceNodeId,
            clippingExpected = visibleGeometry.clippingSource?.expected == true,
            nodeId = tooling?.nodeId,
            sourceCallSites = sourceCallSites,
            synthetic = tooling?.synthetic == true,
            children = childNodes,
        )
    }
}

private fun visibleGeometry(
    bounds: PreviewLayoutBounds,
    inheritedClip: ClipRegion?,
    ownClip: ClipRegion?,
): VisibleGeometry {
    var visibleBounds: PreviewLayoutBounds? = bounds
    var clippingSource: ClipRegion? = null
    if (inheritedClip != null) {
        val clipped = visibleBounds?.intersection(inheritedClip.bounds)
        if (clipped != visibleBounds) {
            clippingSource = inheritedClip
        }
        visibleBounds = clipped
    }
    if (ownClip != null) {
        val clipped = visibleBounds?.intersection(ownClip.bounds)
        if (clipped != visibleBounds) {
            clippingSource = ownClip
        }
        visibleBounds = clipped
    }
    return VisibleGeometry(
        bounds = visibleBounds,
        clippingSource = clippingSource,
    )
}

private fun childClipRegion(
    view: View,
    bounds: PreviewLayoutBounds,
    inheritedClip: ClipRegion?,
    ownClip: ClipRegion?,
    nodeId: String?,
): ClipRegion? {
    var clip = inheritedClip
    if (ownClip != null) {
        clip = clip.intersect(ownClip)
    }
    if (view is ViewGroup && view.clipChildren) {
        val childBounds = if (view.clipToPadding) {
            PreviewLayoutBounds(
                left = bounds.left + view.paddingLeft,
                top = bounds.top + view.paddingTop,
                right = bounds.right - view.paddingRight,
                bottom = bounds.bottom - view.paddingBottom,
            )
        } else {
            bounds
        }
        clip = clip.intersect(
            ClipRegion(
                bounds = childBounds,
                sourceClassName = view.javaClass.name,
                sourceNodeId = nodeId,
                expected = view.isExpectedClippingContainer(),
            ),
        )
    }
    return clip
}

private fun collectGeometryDiagnostics(
    view: View,
    bounds: PreviewLayoutBounds,
    visibleBounds: PreviewLayoutBounds?,
    clippingState: PreviewClippingState,
    clippingSource: ClipRegion?,
    nodeId: String?,
    sourceCallSites: List<PreviewSourceCallSite>,
    diagnostics: MutableList<PreviewLayoutDiagnostic>,
) {
    if (!bounds.hasArea() && view.hasMeaningfulLayoutContent()) {
        diagnostics += PreviewLayoutDiagnostic(
            kind = PreviewLayoutDiagnosticKind.ZeroLayoutSize,
            severity = PreviewDiagnosticSeverity.Warning,
            className = view.javaClass.name,
            bounds = bounds,
            visibleBounds = visibleBounds,
            metrics = mapOf(
                "measuredWidth" to view.measuredWidth,
                "measuredHeight" to view.measuredHeight,
            ),
            nodeId = nodeId,
            sourceCallSites = sourceCallSites,
        )
        return
    }
    val kind = when (clippingState) {
        PreviewClippingState.PartiallyClipped -> PreviewLayoutDiagnosticKind.PartiallyClipped
        PreviewClippingState.FullyClipped -> PreviewLayoutDiagnosticKind.FullyClipped
        PreviewClippingState.NotClipped -> return
    }
    diagnostics += PreviewLayoutDiagnostic(
        kind = kind,
        severity = if (clippingSource?.expected == true) {
            PreviewDiagnosticSeverity.Info
        } else {
            PreviewDiagnosticSeverity.Warning
        },
        className = view.javaClass.name,
        bounds = bounds,
        visibleBounds = visibleBounds,
        clippingAncestorClassName = clippingSource?.sourceClassName,
        clippingAncestorNodeId = clippingSource?.sourceNodeId,
        clippingExpected = clippingSource?.expected == true,
        nodeId = nodeId,
        sourceCallSites = sourceCallSites,
    )
}

private fun collectTextDiagnostics(
    view: View,
    bounds: PreviewLayoutBounds,
    visibleBounds: PreviewLayoutBounds?,
    nodeId: String?,
    sourceCallSites: List<PreviewSourceCallSite>,
    diagnostics: MutableList<PreviewLayoutDiagnostic>,
) {
    if (view !is TextView || view.text.isNullOrEmpty()) return
    val textLayout = view.layout ?: return
    val lineCount = textLayout.lineCount
    val ellipsizedLineCount = (0 until lineCount).count { line ->
        textLayout.getEllipsisCount(line) > 0
    }
    val renderedTextEnd = if (lineCount == 0) {
        0
    } else {
        textLayout.getLineEnd(lineCount - 1).coerceAtMost(view.text.length)
    }
    val hiddenCharacterCount = (view.text.length - renderedTextEnd).coerceAtLeast(0)
    val availableContentHeight = (
        view.height - view.compoundPaddingTop - view.compoundPaddingBottom
        ).coerceAtLeast(0)
    val clippedTextHeight = (textLayout.height - availableContentHeight).coerceAtLeast(0)

    if (ellipsizedLineCount > 0) {
        diagnostics += PreviewLayoutDiagnostic(
            kind = PreviewLayoutDiagnosticKind.TextEllipsized,
            severity = PreviewDiagnosticSeverity.Info,
            className = view.javaClass.name,
            bounds = bounds,
            visibleBounds = visibleBounds,
            metrics = mapOf(
                "lineCount" to lineCount,
                "ellipsizedLineCount" to ellipsizedLineCount,
                "hiddenCharacterCount" to hiddenCharacterCount,
            ),
            nodeId = nodeId,
            sourceCallSites = sourceCallSites,
        )
    }
    if (clippedTextHeight > 0 || (hiddenCharacterCount > 0 && ellipsizedLineCount == 0)) {
        diagnostics += PreviewLayoutDiagnostic(
            kind = PreviewLayoutDiagnosticKind.TextContentClipped,
            severity = PreviewDiagnosticSeverity.Warning,
            className = view.javaClass.name,
            bounds = bounds,
            visibleBounds = visibleBounds,
            metrics = mapOf(
                "lineCount" to lineCount,
                "hiddenCharacterCount" to hiddenCharacterCount,
                "layoutHeight" to textLayout.height,
                "availableContentHeight" to availableContentHeight,
                "clippedTextHeight" to clippedTextHeight,
            ),
            nodeId = nodeId,
            sourceCallSites = sourceCallSites,
        )
    }
}

private fun View.visibilityName(): String {
    return when (visibility) {
        View.VISIBLE -> "VISIBLE"
        View.INVISIBLE -> "INVISIBLE"
        View.GONE -> "GONE"
        else -> visibility.toString()
    }
}

private fun View.hasMeaningfulLayoutContent(): Boolean {
    return measuredWidth > 0 ||
        measuredHeight > 0 ||
        (this is TextView && text.isNotEmpty()) ||
        (this is ViewGroup && childCount > 0)
}

private fun ViewGroup.isExpectedClippingContainer(): Boolean {
    if (isScrollContainer) return true
    val name = javaClass.name
    return name.contains("Scroll", ignoreCase = true) ||
        name.contains("RecyclerView", ignoreCase = true) ||
        name.contains("Pager", ignoreCase = true)
}

private fun PreviewLayoutBounds.hasArea(): Boolean = right > left && bottom > top

private fun PreviewLayoutBounds.intersection(other: PreviewLayoutBounds): PreviewLayoutBounds? {
    val intersection = PreviewLayoutBounds(
        left = max(left, other.left),
        top = max(top, other.top),
        right = min(right, other.right),
        bottom = min(bottom, other.bottom),
    )
    return intersection.takeIf(PreviewLayoutBounds::hasArea)
}

private fun ClipRegion?.intersect(other: ClipRegion): ClipRegion {
    if (this == null) return other
    val nextBounds = bounds.intersectionOrEmpty(other.bounds)
    val source = if (nextBounds != bounds) other else this
    return source.copy(bounds = nextBounds)
}

private fun PreviewLayoutBounds.intersectionOrEmpty(
    other: PreviewLayoutBounds,
): PreviewLayoutBounds {
    val nextLeft = max(left, other.left)
    val nextTop = max(top, other.top)
    return PreviewLayoutBounds(
        left = nextLeft,
        top = nextTop,
        right = max(nextLeft, min(right, other.right)),
        bottom = max(nextTop, min(bottom, other.bottom)),
    )
}

private data class ClipRegion(
    val bounds: PreviewLayoutBounds,
    val sourceClassName: String,
    val sourceNodeId: String?,
    val expected: Boolean,
)

private data class VisibleGeometry(
    val bounds: PreviewLayoutBounds?,
    val clippingSource: ClipRegion?,
)
