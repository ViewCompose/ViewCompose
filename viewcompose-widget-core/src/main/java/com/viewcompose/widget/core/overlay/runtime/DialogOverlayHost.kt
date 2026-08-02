package com.viewcompose.widget.core

/** Represents a platform dialog instance that can be updated in place or dismissed. */
interface DialogOverlayHandle {
    /** Updates the active dialog to [spec] and [content] without changing its entry identity. */
    fun update(
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    )

    /** Permanently dismisses this platform dialog instance. */
    fun dismiss()
}

/**
 * Platform-provided dialog presentation entry point; widget-core depends only on this minimal contract.
 */
interface DialogOverlayPresenter {
    /** Creates and shows a platform dialog for [entryId]. */
    fun show(
        entryId: OverlayEntryId,
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    ): DialogOverlayHandle
}

/**
 * Synchronizes declarative overlay requests to a dialog presenter and owns lifecycle by session/key.
 */
class DialogOverlayHost(
    private val presenter: DialogOverlayPresenter,
) : SessionBoundSurfaceOverlayHost<DialogOverlaySpec, DialogOverlayContent, DialogOverlayHandle>(
    overlayType = OverlayType.Dialog,
    decode = { request ->
        val spec = request.payload as? DialogOverlaySpec
        val content = request.contentToken as? DialogOverlayContent
        if (spec == null || content == null) {
            null
        } else {
            spec to content
        }
    },
) {
    override fun onShow(
        entryId: OverlayEntryId,
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    ): DialogOverlayHandle = presenter.show(entryId, spec, content)

    override fun onUpdate(
        handle: DialogOverlayHandle,
        spec: DialogOverlaySpec,
        content: DialogOverlayContent,
    ) {
        handle.update(spec, content)
    }

    override fun onDismiss(handle: DialogOverlayHandle) {
        handle.dismiss()
    }
}
