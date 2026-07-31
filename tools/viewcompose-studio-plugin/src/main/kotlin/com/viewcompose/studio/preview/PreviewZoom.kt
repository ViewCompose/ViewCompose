package com.viewcompose.studio.preview

import kotlin.math.pow
import kotlin.math.roundToInt

internal enum class PreviewZoomOption(
    val fixedScale: Double?,
) {
    Fit(fixedScale = null),
    Percent50(fixedScale = 0.5),
    Percent75(fixedScale = 0.75),
    Percent100(fixedScale = 1.0),
    Percent125(fixedScale = 1.25),
    Percent150(fixedScale = 1.5),
    Percent200(fixedScale = 2.0),
}

internal fun calculatePreviewScale(
    option: PreviewZoomOption,
    imageWidth: Int,
    imageHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
): Double {
    require(imageWidth > 0 && imageHeight > 0) {
        "Preview image dimensions must be positive."
    }
    option.fixedScale?.let { scale -> return scale }
    val availableWidth = (viewportWidth - PREVIEW_FIT_PADDING_PIXELS).coerceAtLeast(1)
    return (availableWidth.toDouble() / imageWidth)
        .coerceIn(MINIMUM_FIT_SCALE, MAXIMUM_FIT_SCALE)
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

private const val PREVIEW_FIT_PADDING_PIXELS = 16
private const val MINIMUM_FIT_SCALE = 0.1
private const val MAXIMUM_FIT_SCALE = 1.0
private const val MINIMUM_PREVIEW_SCALE = 0.1
private const val MAXIMUM_PREVIEW_SCALE = 4.0
private const val MINIMUM_MAGNIFICATION_FACTOR = 0.01
private const val WHEEL_ZOOM_BASE = 1.1
private const val PREVIEW_TRACKPAD_SCROLL_UNIT = 48.0
