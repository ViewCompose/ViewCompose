package com.viewcompose.overlay.material3.android.host

import android.view.View
import com.viewcompose.overlay.material3.android.presenter.AndroidSnackbarOverlayPresenter
import com.viewcompose.overlay.material3.android.presenter.AndroidToastOverlayPresenter
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.TransientFeedbackOverlayHost

/**
 * Presents queued snackbar and toast requests for one Android render root.
 *
 * Snackbars use [anchorView] for placement. Toasts deliberately use its application context so a
 * queued timeout cannot retain an Activity after the render root is destroyed. Queue ordering,
 * replacement, and dismissal reasons are implemented by the platform-independent transient host.
 *
 * @param anchorView attached view used to place Material snackbars and obtain application context
 */
class AndroidTransientFeedbackOverlayHost(
    anchorView: View,
) : OverlayHost by TransientFeedbackOverlayHost(
    snackbarPresenter = AndroidSnackbarOverlayPresenter(anchorView),
    toastPresenter = AndroidToastOverlayPresenter(anchorView.context.applicationContext),
)
