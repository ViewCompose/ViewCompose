package com.viewcompose.widget.core

/** Represents a platform popup instance that can be updated in place or dismissed. */
interface PopupOverlayHandle {
    /** Updates the active popup to [spec] and [content] without changing its entry identity. */
    fun update(
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    )

    /** Permanently dismisses this platform popup instance. */
    fun dismiss()
}

/**
 * Platform-provided popup presenter that receives declarative specs and content tokens.
 */
interface PopupOverlayPresenter {
    /** Creates and shows a platform popup for [entryId]. */
    fun show(
        entryId: OverlayEntryId,
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    ): PopupOverlayHandle
}

/**
 * Synchronizes popup overlay requests to the platform presenter and filters out non-popup requests.
 */
class PopupOverlayHost(
    private val presenter: PopupOverlayPresenter,
) : SessionBoundSurfaceOverlayHost<PopupOverlaySpec, PopupOverlayContent, PopupOverlayHandle>(
    overlayType = OverlayType.Popup,
    decode = { request ->
        val spec = request.payload as? PopupOverlaySpec
        val content = request.contentToken as? PopupOverlayContent
        if (spec == null || content == null) {
            null
        } else {
            spec to content
        }
    },
) {
    override fun onShow(
        entryId: OverlayEntryId,
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    ): PopupOverlayHandle = presenter.show(entryId, spec, content)

    override fun onUpdate(
        handle: PopupOverlayHandle,
        spec: PopupOverlaySpec,
        content: PopupOverlayContent,
    ) {
        handle.update(spec, content)
    }

    override fun onDismiss(handle: PopupOverlayHandle) {
        handle.dismiss()
    }
}
