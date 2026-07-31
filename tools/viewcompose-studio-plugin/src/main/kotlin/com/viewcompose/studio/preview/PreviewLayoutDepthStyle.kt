package com.viewcompose.studio.preview

import java.awt.Color

internal data class PreviewLayoutDepthStyle(
    val color: Color,
    val fillAlpha: Int,
    val strokeAlpha: Int,
    val strokeWidth: Int,
)

/**
 * Maps structural View depth to a stable overdraw-inspired warning scale.
 *
 * This visualizes hierarchy pressure rather than measured GPU overdraw: deeper nodes become warmer,
 * more opaque, and eventually thicker without changing color between renders.
 */
internal fun previewLayoutDepthStyle(depth: Int): PreviewLayoutDepthStyle {
    require(depth > 0) { "Preview View depth must be positive." }
    val tier = (depth - 1).coerceAtMost(PREVIEW_DEPTH_COLORS.lastIndex)
    return PreviewLayoutDepthStyle(
        color = PREVIEW_DEPTH_COLORS[tier],
        fillAlpha = PREVIEW_DEPTH_FILL_ALPHAS[tier],
        strokeAlpha = PREVIEW_DEPTH_STROKE_ALPHAS[tier],
        strokeWidth = if (tier >= 4) 2 else 1,
    )
}

private val PREVIEW_DEPTH_COLORS = listOf(
    Color(0x2F, 0x80, 0xED),
    Color(0x27, 0xAE, 0x60),
    Color(0xF2, 0xC9, 0x4C),
    Color(0xF2, 0x99, 0x4A),
    Color(0xEB, 0x57, 0x57),
    Color(0xC2, 0x18, 0x5B),
)
private val PREVIEW_DEPTH_FILL_ALPHAS = listOf(14, 22, 32, 44, 58, 76)
private val PREVIEW_DEPTH_STROKE_ALPHAS = listOf(150, 165, 180, 200, 220, 238)
