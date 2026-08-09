package com.viewcompose.overlay.material3.android.host

import android.view.View
import com.viewcompose.overlay.material3.android.presenter.AndroidModalBottomSheetPresenter
import com.viewcompose.overlay.material3.android.presenter.AndroidSnackbarOverlayPresenter
import com.viewcompose.overlay.android.AndroidOverlayHost as PlatformAndroidOverlayHost
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayRequest
import com.viewcompose.ui.foundation.OverlaySessionId
import com.viewcompose.ui.foundation.UiDesignConformance
import com.viewcompose.ui.foundation.UiIntegrationAttribution
import com.viewcompose.ui.unit.dp

/**
 * Presents every ViewCompose overlay type in the Android window that owns [rootView].
 *
 * Neutral Android dialog, popup, toast, and nested-session behavior comes from the platform
 * transport. This adapter supplies only Material Snackbar and modal-bottom-sheet presenters and
 * retains the accepted 24dp dialog window inset. The host does not own [rootView] and must not
 * outlive its window; render-session teardown calls [clear] to dismiss only that session's entries.
 *
 * All operations and platform callbacks must run on the Android main thread.
 *
 * @param rootView attached render root used for window context, popup anchors, and snackbar placement
 * @sample com.viewcompose.overlay.material3.android.samples.androidOverlayHostSample
 */
class AndroidOverlayHost(
    rootView: View,
) : OverlayHost {
    private val delegate = PlatformAndroidOverlayHost(
        rootView = rootView,
        dialogWindowInset = 24.dp,
        snackbarPresenter = AndroidSnackbarOverlayPresenter(rootView),
        modalBottomSheetPresenter = AndroidModalBottomSheetPresenter(rootView),
    )

    /** Reports the neutral transport and Material presenter selected for every overlay type. */
    val integrationAttribution: List<UiIntegrationAttribution> =
        delegate.integrationAttribution.map { attribution ->
            when (attribution.capabilityId) {
                "overlay.dialog" -> attribution.copy(
                    presenterId = "viewcompose-material3/captured-dialog-content",
                )
                "overlay.popup" -> attribution.copy(
                    presenterId = "viewcompose-material3/captured-popup-content",
                )
                "overlay.snackbar" -> attribution.copy(
                    presenterId = "material-components/snackbar",
                    conformance = UiDesignConformance.Equivalent,
                    fallback = "none",
                )
                "overlay.modal-bottom-sheet" -> attribution.copy(
                    presenterId = "material-components/bottom-sheet-dialog",
                    conformance = UiDesignConformance.Equivalent,
                    fallback = "none",
                )
                else -> attribution
            }
        }

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
