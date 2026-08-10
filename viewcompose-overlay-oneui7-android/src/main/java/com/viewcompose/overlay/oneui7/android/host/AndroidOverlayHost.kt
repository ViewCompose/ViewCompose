package com.viewcompose.overlay.oneui7.android.host

import android.view.View
import com.viewcompose.overlay.android.AndroidOverlayHost as PlatformAndroidOverlayHost
import com.viewcompose.overlay.oneui7.android.OneUi7OverlayStyle
import com.viewcompose.overlay.oneui7.android.defaultOneUi7Tokens
import com.viewcompose.overlay.oneui7.android.presenter.AndroidOneUi7ModalBottomSheetPresenter
import com.viewcompose.overlay.oneui7.android.presenter.AndroidOneUi7SnackbarPresenter
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayRequest
import com.viewcompose.ui.foundation.OverlaySessionId
import com.viewcompose.ui.foundation.UiDesignConformance
import com.viewcompose.ui.foundation.UiIntegrationAttribution
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.unit.dp

/**
 * Presents ViewCompose overlays with neutral Android transport and One UI 7 chrome.
 *
 * Dialog, popup, Toast, nested-render-session, and lifecycle behavior remain owned by the neutral
 * Android overlay transport. This adapter supplies only One UI Snackbar and modal-bottom-sheet
 * presenters and applies the public One UI minimum 24dp horizontal window margin. It has no
 * Material Components dependency and never registers a process-global service provider.
 *
 * One host belongs to the attached window containing [rootView]. All calls and callbacks must run
 * on the Android main thread, and the render session must call [clear] before that window is
 * destroyed.
 *
 * @sample com.viewcompose.overlay.oneui7.android.samples.oneUi7AndroidOverlayHostSample
 * @param rootView attached render root used for window ownership and overlay placement
 * @param tokens immutable One UI snapshot used for platform-owned Snackbar and sheet chrome; the
 * default chooses the static light or dark snapshot from the root configuration and does not
 * refresh in place
 */
class AndroidOverlayHost(
    rootView: View,
    tokens: UiThemeTokens = defaultOneUi7Tokens(rootView.context),
) : OverlayHost {
    private val style = OneUi7OverlayStyle.from(tokens)
    private val delegate = PlatformAndroidOverlayHost(
        rootView = rootView,
        dialogWindowInset = style.horizontalMarginDp.dp,
        snackbarPresenter = AndroidOneUi7SnackbarPresenter(rootView, style),
        modalBottomSheetPresenter = AndroidOneUi7ModalBottomSheetPresenter(rootView, style),
    )

    /** Immutable root-owned evidence for neutral transport and the explicit One UI presenters. */
    val integrationAttribution: List<UiIntegrationAttribution> =
        delegate.integrationAttribution.map { attribution ->
            when (attribution.capabilityId) {
                "overlay.dialog" -> attribution.copy(
                    presenterId = "viewcompose-oneui7/captured-dialog-content",
                )
                "overlay.popup" -> attribution.copy(
                    presenterId = "viewcompose-oneui7/captured-popup-content",
                )
                "overlay.snackbar" -> attribution.copy(
                    presenterId = "viewcompose-oneui7/native-snackbar",
                    conformance = UiDesignConformance.Equivalent,
                    fallback = "none",
                )
                "overlay.modal-bottom-sheet" -> attribution.copy(
                    presenterId = "viewcompose-oneui7/bottom-sheet-dialog",
                    conformance = UiDesignConformance.Equivalent,
                    fallback = "none",
                )
                else -> attribution
            }
        }

    /** Reconciles [requests] as the complete overlay snapshot owned by [sessionId]. */
    override fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    ) {
        delegate.commit(sessionId, requests)
    }

    /** Dismisses all One UI and neutral overlay entries owned by [sessionId]. */
    override fun clear(sessionId: OverlaySessionId) {
        delegate.clear(sessionId)
    }
}
