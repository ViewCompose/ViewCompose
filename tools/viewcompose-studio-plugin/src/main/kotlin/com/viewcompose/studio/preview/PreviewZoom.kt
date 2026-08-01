package com.viewcompose.studio.preview

import kotlin.math.pow
import kotlin.math.roundToInt

internal enum class PreviewZoomOption {
    Fit,
    ActualSize,
}

internal fun calculatePreviewScale(
    option: PreviewZoomOption,
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    actualSizeScale: Double = 1.0,
): Double {
    require(imageWidth > 0 && imageHeight > 0) {
        "Preview image dimensions must be positive."
    }
    require(actualSizeScale.isFinite() && actualSizeScale > 0.0) {
        "Actual preview scale must be finite and positive."
    }
    if (option == PreviewZoomOption.ActualSize) {
        return clampPreviewScale(actualSizeScale)
    }
    val availableWidth = (viewportWidth - PREVIEW_FIT_PADDING_PIXELS).coerceAtLeast(1)
    val availableHeight = (viewportHeight - PREVIEW_FIT_PADDING_PIXELS).coerceAtLeast(1)
    return minOf(
        availableWidth.toDouble() / imageWidth,
        availableHeight.toDouble() / imageHeight,
        MAXIMUM_FIT_SCALE,
    ).coerceAtLeast(MINIMUM_PREVIEW_SCALE)
}

internal fun calculateButtonPreviewScale(
    currentScale: Double,
    direction: Int,
): Double {
    require(currentScale.isFinite() && currentScale > 0.0) {
        "Current preview scale must be finite and positive."
    }
    require(direction == -1 || direction == 1) {
        "Preview zoom direction must be -1 or 1."
    }
    val factor = if (direction > 0) BUTTON_ZOOM_FACTOR else 1.0 / BUTTON_ZOOM_FACTOR
    return clampPreviewScale(currentScale * factor)
}

internal fun calculateMagnifiedPreviewScale(
    currentScale: Double,
    magnification: Double,
): Double {
    require(currentScale.isFinite() && currentScale > 0.0) {
        "Current preview scale must be finite and positive."
    }
    require(magnification.isFinite()) { "Preview magnification must be finite." }
    return clampPreviewScale(
        currentScale * (1.0 + magnification).coerceAtLeast(MINIMUM_MAGNIFICATION_FACTOR),
    )
}

internal fun calculateWheelPreviewScale(
    currentScale: Double,
    preciseWheelRotation: Double,
): Double {
    require(currentScale.isFinite() && currentScale > 0.0) {
        "Current preview scale must be finite and positive."
    }
    require(preciseWheelRotation.isFinite()) { "Preview wheel rotation must be finite." }
    return clampPreviewScale(
        currentScale * WHEEL_ZOOM_BASE.pow(-preciseWheelRotation),
    )
}

internal fun clampPreviewScale(scale: Double): Double {
    require(scale.isFinite()) { "Preview scale must be finite." }
    return scale.coerceIn(MINIMUM_PREVIEW_SCALE, MAXIMUM_PREVIEW_SCALE)
}

internal fun calculatePreviewScrollPosition(
    currentPosition: Int,
    maximumPosition: Int,
    preciseWheelRotation: Double,
): Int {
    require(maximumPosition >= 0) { "Maximum preview scroll position must be non-negative." }
    require(preciseWheelRotation.isFinite()) { "Preview wheel rotation must be finite." }
    return (currentPosition + preciseWheelRotation * PREVIEW_TRACKPAD_SCROLL_UNIT)
        .roundToInt()
        .coerceIn(0, maximumPosition)
}

internal fun calculateAnchoredPreviewPosition(
    imageOffset: Int,
    imageAnchor: Double,
    scale: Double,
    anchorViewportOffset: Int,
    maximumPosition: Int,
): Int {
    require(imageAnchor.isFinite()) { "Preview image anchor must be finite." }
    require(scale.isFinite() && scale > 0.0) { "Preview scale must be finite and positive." }
    require(maximumPosition >= 0) { "Maximum preview position must be non-negative." }
    return (imageOffset + imageAnchor * scale - anchorViewportOffset)
        .roundToInt()
        .coerceIn(0, maximumPosition)
}

private const val PREVIEW_FIT_PADDING_PIXELS = 16
private const val MAXIMUM_FIT_SCALE = 1.0
private const val MINIMUM_PREVIEW_SCALE = 0.05
private const val MAXIMUM_PREVIEW_SCALE = 4.0
private const val MINIMUM_MAGNIFICATION_FACTOR = 0.01
private const val WHEEL_ZOOM_BASE = 1.1
private const val BUTTON_ZOOM_FACTOR = 1.25
private const val PREVIEW_TRACKPAD_SCROLL_UNIT = 48.0
