package com.viewcompose.overlay.android

import android.view.View
import com.viewcompose.overlay.android.presenter.AndroidDialogOverlayPresenter
import com.viewcompose.overlay.android.presenter.AndroidPopupOverlayPresenter
import com.viewcompose.overlay.android.presenter.AndroidToastOverlayPresenter
import com.viewcompose.ui.foundation.DialogOverlayHost
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayHost
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayPresenter
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayRequest
import com.viewcompose.ui.foundation.OverlaySessionId
import com.viewcompose.ui.foundation.PopupOverlayHost
import com.viewcompose.ui.foundation.SnackbarOverlayPresenter
import com.viewcompose.ui.foundation.TransientFeedbackOverlayHost
import com.viewcompose.ui.unit.UiDp

/**
 * Presents platform-neutral ViewCompose overlay requests in the Android window owning [rootView].
 *
 * Dialogs, anchored popups, and Android toasts use the built-in Material-free presenters. Snackbar
 * and modal-bottom-sheet presenters are narrow integration slots because their visual and
 * behavioral policy may belong to a design system. When a slot is absent, that request is reported
 * as unsupported and no Material widget is substituted. One instance belongs to one attached root;
 * its caller must clear every render session before the root's window is destroyed.
 *
 * All calls, callbacks, and supplied presenters are confined to the Android main thread. The host
 * retains [rootView] until the host itself becomes unreachable.
 *
 * @sample com.viewcompose.overlay.android.samples.androidOverlayHostSample
 * @param rootView attached render root used for window ownership, popup anchors, and resources
 * @param dialogWindowInset inset applied around custom dialog content before platform placement
 * @param snackbarPresenter optional design-owned snackbar backend, or an attributable unsupported fallback
 * @param modalBottomSheetPresenter optional design-owned modal-sheet backend, or an attributable unsupported fallback
 */
class AndroidOverlayHost(
    rootView: View,
    dialogWindowInset: UiDp = UiDp.Zero,
    snackbarPresenter: SnackbarOverlayPresenter? = null,
    modalBottomSheetPresenter: ModalBottomSheetOverlayPresenter? = null,
) : OverlayHost {
    private val delegate = CompositeOverlayHost(
        DialogOverlayHost(
            AndroidDialogOverlayPresenter(
                rootView = rootView,
                windowInset = dialogWindowInset,
            ),
        ),
        PopupOverlayHost(AndroidPopupOverlayPresenter(rootView)),
        ModalBottomSheetOverlayHost(
            modalBottomSheetPresenter ?: UnsupportedModalBottomSheetOverlayPresenter(),
        ),
        TransientFeedbackOverlayHost(
            snackbarPresenter = snackbarPresenter ?: UnsupportedSnackbarOverlayPresenter(),
            toastPresenter = AndroidToastOverlayPresenter(rootView.context),
        ),
    )

    /**
     * Reconciles [requests] as the complete desired overlay set owned by [sessionId].
     *
     * Type-specific delegates ignore requests outside their capability. Repeating an unchanged
     * request is idempotent; omitting a previous key dismisses or clears only that session's entry.
     *
     * @param sessionId render-session owner whose entries are being replaced
     * @param requests complete desired request snapshot for that session
     */
    override fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    ) {
        delegate.commit(sessionId, requests)
    }

    /**
     * Dismisses every surface and transient request owned by [sessionId].
     *
     * @param sessionId render-session owner being permanently cleared
     */
    override fun clear(sessionId: OverlaySessionId) {
        delegate.clear(sessionId)
    }
}

/** Fans one desired request set out to type-specific hosts. */
private class CompositeOverlayHost(
    private vararg val delegates: OverlayHost,
) : OverlayHost {
    override fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    ) {
        delegates.forEach { host -> host.commit(sessionId, requests) }
    }

    override fun clear(sessionId: OverlaySessionId) {
        delegates.forEach { host -> host.clear(sessionId) }
    }
}
