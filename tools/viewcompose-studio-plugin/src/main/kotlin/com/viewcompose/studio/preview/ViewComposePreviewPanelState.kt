package com.viewcompose.studio.preview

import java.awt.image.BufferedImage
import java.nio.file.Path

internal sealed interface ViewComposePreviewPanelState {
    data object Empty : ViewComposePreviewPanelState

    data class Loading(
        val selection: PreviewSourceSelection,
        val message: String,
        val previousResult: PreviewRenderOutcome.Success? = null,
    ) : ViewComposePreviewPanelState

    data class Rendered(
        val result: PreviewRenderOutcome.Success,
    ) : ViewComposePreviewPanelState

    data class Failed(
        val result: PreviewRenderOutcome.Failure,
    ) : ViewComposePreviewPanelState

    data class GalleryLoading(
        val message: String,
        val previousResult: PreviewGalleryResult? = null,
    ) : ViewComposePreviewPanelState

    data class Gallery(
        val result: PreviewGalleryResult,
    ) : ViewComposePreviewPanelState

    data class GalleryFailed(
        val details: String,
    ) : ViewComposePreviewPanelState
}

internal data class PreviewGalleryResult(
    val items: List<PreviewGalleryItem>,
    val failures: List<PreviewRenderOutcome.Failure>,
)

internal data class PreviewGalleryItem(
    val selection: PreviewSourceSelection,
    val descriptorName: String,
    val variantName: String,
    val image: BufferedImage,
    val imagePath: Path,
    val cacheHit: Boolean,
)
