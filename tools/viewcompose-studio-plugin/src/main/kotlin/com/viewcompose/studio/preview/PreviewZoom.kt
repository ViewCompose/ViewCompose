package com.viewcompose.studio.preview

import kotlin.math.min

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
    val availableHeight = (viewportHeight - PREVIEW_FIT_PADDING_PIXELS).coerceAtLeast(1)
    return min(
        availableWidth.toDouble() / imageWidth,
        availableHeight.toDouble() / imageHeight,
    ).coerceIn(MINIMUM_FIT_SCALE, MAXIMUM_FIT_SCALE)
}

private const val PREVIEW_FIT_PADDING_PIXELS = 16
private const val MINIMUM_FIT_SCALE = 0.1
private const val MAXIMUM_FIT_SCALE = 1.0
