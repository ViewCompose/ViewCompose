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
 * Android 平台 overlay host 聚合入口。
 * Android platform overlay host aggregation entry point.
 *
 * 一个 rootView 同时承载 dialog、popup、bottom sheet 和 transient feedback 的平台 presenter。
 * A single rootView owns platform presenters for dialogs, popups, bottom sheets, and transient feedback.
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
     * 将同一 render session 的 overlay 请求广播给所有子 host。
     * Broadcasts overlay requests from one render session to all child hosts.
     */
    override fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    ) {
        delegate.commit(sessionId, requests)
    }

    /**
     * 清理指定 render session 创建的全部 overlay。
     * Clears all overlays created by the given render session.
     */
    override fun clear(sessionId: OverlaySessionId) {
        delegate.clear(sessionId)
    }
}

/**
 * 简单的 OverlayHost fan-out，用于按 overlay type 分发到多个专职 host。
 * Simple OverlayHost fan-out used to route overlay types to dedicated hosts.
 */
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
