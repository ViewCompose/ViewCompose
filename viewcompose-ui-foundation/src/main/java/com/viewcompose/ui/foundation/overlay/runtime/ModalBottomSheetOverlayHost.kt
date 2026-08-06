package com.viewcompose.ui.foundation

/** Represents a platform modal bottom-sheet instance currently shown. */
interface ModalBottomSheetOverlayHandle {
    /** Updates the active sheet to [spec] and [content] without changing its entry identity. */
    fun update(
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    )

    /** Permanently dismisses this platform bottom-sheet instance. */
    fun dismiss()
}

/**
 * Platform-provided bottom sheet presenter responsible for creating the real UI container.
 */
interface ModalBottomSheetOverlayPresenter {
    /** Creates and shows a platform bottom sheet for [entryId]. */
    fun show(
        entryId: OverlayEntryId,
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ): ModalBottomSheetOverlayHandle
}

/**
 * Synchronizes declarative bottom sheet overlay requests to the platform presenter and reuses handles for the same entry.
 */
class ModalBottomSheetOverlayHost(
    private val presenter: ModalBottomSheetOverlayPresenter,
) : SessionBoundSurfaceOverlayHost<
    ModalBottomSheetOverlaySpec,
    ModalBottomSheetOverlayContent,
    ModalBottomSheetOverlayHandle,
>(
    overlayType = OverlayType.ModalBottomSheet,
    decode = { request ->
        val spec = request.payload as? ModalBottomSheetOverlaySpec
        val content = request.contentToken as? ModalBottomSheetOverlayContent
        if (spec == null || content == null) {
            null
        } else {
            spec to content
        }
    },
) {
    override fun onShow(
        entryId: OverlayEntryId,
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ): ModalBottomSheetOverlayHandle = presenter.show(entryId, spec, content)

    override fun onUpdate(
        handle: ModalBottomSheetOverlayHandle,
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ) {
        handle.update(spec, content)
    }

    override fun onDismiss(handle: ModalBottomSheetOverlayHandle) {
        handle.dismiss()
    }
}
