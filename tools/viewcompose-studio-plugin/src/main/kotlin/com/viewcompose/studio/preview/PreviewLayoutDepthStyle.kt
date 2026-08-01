package com.viewcompose.studio.preview

import java.awt.Color

internal data class PreviewLayoutDepthStyle(
    val color: Color,
    val strokeAlpha: Int,
)

internal data class PreviewLayoutBoundLayer(
    val bounds: StudioPreviewLayoutBounds,
    val depth: Int,
)

/**
 * Maps declarative semantic depth to a stable overdraw-inspired warning scale.
 *
 * Platform hosts and renderer-created synthetic Views do not consume a warning tier. This keeps
 * normal DSL nesting cool while deeper declarative hierarchies become warmer and more opaque.
 */
internal fun previewLayoutDepthStyle(depth: Int): PreviewLayoutDepthStyle {
    require(depth > 0) { "Preview View depth must be positive." }
    val tier = (depth - 1).coerceAtMost(PREVIEW_DEPTH_COLORS.lastIndex)
    return PreviewLayoutDepthStyle(
        color = PREVIEW_DEPTH_COLORS[tier],
        strokeAlpha = PREVIEW_DEPTH_STROKE_ALPHAS[tier],
    )
}

/**
 * Builds source-aware bounds and collapses equal rectangles to their deepest semantic layer.
 *
 * Only non-synthetic Views backed by a captured DSL node are painted. Platform wrappers remain
 * inspectable in the Views tab but do not inflate layout-depth warnings.
 */
internal fun previewLayoutBoundLayers(
    roots: List<StudioPreviewNativeViewNode>,
): List<PreviewLayoutBoundLayer> {
    val deepestByBounds = linkedMapOf<StudioPreviewLayoutBounds, Int>()

    fun collect(
        view: StudioPreviewNativeViewNode,
        parentSemanticDepth: Int,
    ) {
        val isSemanticNode =
            view.nodeId != null &&
                view.sourceCallSites.isNotEmpty() &&
                !view.synthetic
        val semanticDepth = parentSemanticDepth + if (isSemanticNode) 1 else 0
        val bounds = view.bounds
        if (
            isSemanticNode &&
            view.visibility == "VISIBLE" &&
            bounds.width > 0 &&
            bounds.height > 0
        ) {
            deepestByBounds[bounds] = maxOf(
                deepestByBounds[bounds] ?: 0,
                semanticDepth,
            )
        }
        view.children.forEach { child ->
            collect(
                view = child,
                parentSemanticDepth = semanticDepth,
            )
        }
    }

    roots.forEach { root -> collect(root, parentSemanticDepth = 0) }
    return deepestByBounds
        .map { (bounds, depth) -> PreviewLayoutBoundLayer(bounds, depth) }
        .sortedBy(PreviewLayoutBoundLayer::depth)
}

private val PREVIEW_DEPTH_COLORS = listOf(
    Color(0x2F, 0x80, 0xED),
    Color(0x27, 0xAE, 0x60),
    Color(0xF2, 0xC9, 0x4C),
    Color(0xF2, 0x99, 0x4A),
    Color(0xEB, 0x57, 0x57),
    Color(0xC2, 0x18, 0x5B),
)
private val PREVIEW_DEPTH_STROKE_ALPHAS = listOf(125, 145, 165, 185, 205, 225)
