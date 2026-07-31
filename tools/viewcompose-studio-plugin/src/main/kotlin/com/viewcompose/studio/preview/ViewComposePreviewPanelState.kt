package com.viewcompose.studio.preview

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
}
