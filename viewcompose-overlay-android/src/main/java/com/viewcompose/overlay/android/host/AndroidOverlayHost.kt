package com.viewcompose.overlay.android.host

import android.view.View
import com.viewcompose.overlay.android.presenter.AndroidDialogOverlayPresenter
import com.viewcompose.overlay.android.presenter.AndroidModalBottomSheetPresenter
import com.viewcompose.overlay.android.presenter.AndroidPopupOverlayPresenter
import com.viewcompose.widget.core.DialogOverlayHost
import com.viewcompose.widget.core.ModalBottomSheetOverlayHost
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayRequest
import com.viewcompose.widget.core.OverlaySessionId
import com.viewcompose.widget.core.PopupOverlayHost

/**
 * Presents every ViewCompose overlay type in the Android window that owns [rootView].
 *
 * One host fans each session-scoped request set out to dedicated dialog, popup, modal bottom-sheet,
 * snackbar, and toast presenters. The host does not own [rootView] and must not outlive its window;
 * render-session teardown calls [clear] to dismiss only the overlays owned by that session.
 *
 * All operations and platform callbacks must run on the Android main thread.
 *
 * @param rootView attached render root used for window context, popup anchors, and snackbar placement
 * @sample com.viewcompose.overlay.android.samples.androidOverlayHostSample
 */
class AndroidOverlayHost(
    rootView: View,
) : OverlayHost {
    private val delegate = CompositeOverlayHost(
        DialogOverlayHost(AndroidDialogOverlayPresenter(rootView)),
        PopupOverlayHost(AndroidPopupOverlayPresenter(rootView)),
        ModalBottomSheetOverlayHost(AndroidModalBottomSheetPresenter(rootView)),
        AndroidTransientFeedbackOverlayHost(rootView),
    )

    /**
     * Reconciles [requests] as the complete desired overlay set for [sessionId].
     *
     * Requests are broadcast to type-specific hosts; each host ignores unsupported types. Repeating
     * an unchanged request is idempotent, while omitting a previously committed request dismisses it.
     */
    override fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    ) {
        delegate.commit(sessionId, requests)
    }

    /** Dismisses every surface and transient-feedback request owned by [sessionId]. */
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
        delegates.forEach { host ->
            host.commit(sessionId, requests)
        }
    }

    override fun clear(sessionId: OverlaySessionId) {
        delegates.forEach { host ->
            host.clear(sessionId)
        }
    }
}
