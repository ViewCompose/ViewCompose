package com.viewcompose.overlay.material3.android.host

import android.view.View
import com.viewcompose.overlay.material3.android.presenter.AndroidDialogOverlayPresenter
import com.viewcompose.overlay.material3.android.presenter.AndroidModalBottomSheetPresenter
import com.viewcompose.overlay.material3.android.presenter.AndroidPopupOverlayPresenter
import com.viewcompose.ui.foundation.DialogOverlayHost
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayHost
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayRequest
import com.viewcompose.ui.foundation.OverlaySessionId
import com.viewcompose.ui.foundation.PopupOverlayHost

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
 * @sample com.viewcompose.overlay.material3.android.samples.androidOverlayHostSample
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
