package com.viewcompose.overlay.android.host

import android.view.View
import com.viewcompose.overlay.android.presenter.AndroidSnackbarOverlayPresenter
import com.viewcompose.overlay.android.presenter.AndroidToastOverlayPresenter
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.TransientFeedbackOverlayHost

/**
 * Android 瞬时反馈 host，绑定 snackbar 与 toast 的平台 presenter。
 * Android transient feedback host binding snackbar and toast platform presenters.
 *
 * snackbar 锚定到 root/anchor view，toast 使用 application context 避免泄漏界面 Context。
 * Snackbar is anchored to the root/anchor view, while Toast uses application context to avoid leaking UI Context.
 */
class AndroidTransientFeedbackOverlayHost(
    anchorView: View,
) : OverlayHost by TransientFeedbackOverlayHost(
    snackbarPresenter = AndroidSnackbarOverlayPresenter(anchorView),
    toastPresenter = AndroidToastOverlayPresenter(anchorView.context.applicationContext),
)
