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
 * Maps structural View depth to a stable overdraw-inspired warning scale.
 *
 * This visualizes hierarchy pressure rather than measured GPU overdraw: deeper nodes become warmer
 * and more opaque without covering the preview content.
 */
internal fun previewLayoutDepthStyle(depth: Int): PreviewLayoutDepthStyle {
    require(depth > 0) { "Preview View depth must be positive." }
    val tier = (depth - 1).coerceAtMost(PREVIEW_DEPTH_COLORS.lastIndex)
    return PreviewLayoutDepthStyle(
        color = PREVIEW_DEPTH_COLORS[tier],
        strokeAlpha = PREVIEW_DEPTH_STROKE_ALPHAS[tier],
    )
}

/** Collapses equal parent/child rectangles and keeps the deepest structural warning for each. */
internal fun previewLayoutBoundLayers(
    roots: List<StudioPreviewNativeViewNode>,
): List<PreviewLayoutBoundLayer> {
    val deepestByBounds = linkedMapOf<StudioPreviewLayoutBounds, Int>()

    fun collect(
        view: StudioPreviewNativeViewNode,
        depth: Int,
    ) {
        val bounds = view.bounds
        if (
            depth > 0 &&
            view.visibility == "VISIBLE" &&
            bounds.width > 0 &&
            bounds.height > 0
        ) {
            deepestByBounds[bounds] = maxOf(deepestByBounds[bounds] ?: 0, depth)
        }
        view.children.forEach { child -> collect(child, depth + 1) }
    }

    roots.forEach { root -> collect(root, depth = 0) }
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
