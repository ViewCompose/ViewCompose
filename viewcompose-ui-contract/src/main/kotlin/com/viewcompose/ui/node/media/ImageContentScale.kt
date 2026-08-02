package com.viewcompose.ui.node

/**
 * Selects how image intrinsic bounds map into the rendered target bounds.
 *
 * `Fit` preserves aspect ratio and fits both dimensions, `Crop` preserves aspect ratio while
 * filling and clipping overflow, `FillBounds` stretches both dimensions independently, and
 * `Inside` avoids upscaling content that already fits.
 */
enum class ImageContentScale {
    Fit,
    Crop,
    FillBounds,
    Inside,
}
