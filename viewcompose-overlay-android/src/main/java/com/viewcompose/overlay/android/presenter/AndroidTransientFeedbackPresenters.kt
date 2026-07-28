package com.viewcompose.overlay.android.presenter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.viewcompose.widget.core.OverlayEntryId
import com.viewcompose.widget.core.SnackbarDuration
import com.viewcompose.widget.core.SnackbarOverlayPresenter
import com.viewcompose.widget.core.SnackbarOverlaySpec
import com.viewcompose.widget.core.ToastDuration
import com.viewcompose.widget.core.ToastOverlayPresenter
import com.viewcompose.widget.core.ToastOverlaySpec
import com.viewcompose.widget.core.TransientFeedbackDismissReason

/**
 * 使用 Material Snackbar 展示 declarative snackbar 请求。
 * Presents declarative snackbar requests with Material Snackbar.
 *
 * presenter 按 OverlayEntryId 记录当前平台 snackbar，dismiss 时把平台事件映射回框架 dismiss reason。
 * The presenter tracks platform snackbars by OverlayEntryId and maps platform dismissal events back to framework reasons.
 */
class AndroidSnackbarOverlayPresenter(
    private val anchorView: View,
) : SnackbarOverlayPresenter {
    private val activeSnackbars = mutableMapOf<OverlayEntryId, ActiveSnackbar>()

    /**
     * 创建并展示一个平台 Snackbar。
     * Creates and shows one platform Snackbar.
     */
    override fun show(
        entryId: OverlayEntryId,
        spec: SnackbarOverlaySpec,
        onDismissed: (TransientFeedbackDismissReason) -> Unit,
    ) {
        val snackbar = Snackbar.make(
            anchorView,
            spec.message,
            spec.duration.toPlatformDuration(),
        ).apply {
            if (!spec.actionLabel.isNullOrBlank()) {
                setAction(spec.actionLabel) {
                    // action 回调只通知业务，真正的 dismiss reason 由 Snackbar callback 给出。
                    // The action callback only notifies business code; the Snackbar callback supplies the dismiss reason.
                    spec.onAction?.invoke()
                }
            }
            addCallback(
                object : Snackbar.Callback() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        val active = activeSnackbars[entryId]
                            ?.takeIf { it.snackbar === transientBottomBar }
                            ?: return
                        activeSnackbars.remove(entryId)
                        active.onDismissed(
                            active.requestedDismissReason ?: event.toDismissReason(),
                        )
                    }
                },
            )
        }
        activeSnackbars[entryId] = ActiveSnackbar(
            snackbar = snackbar,
            onDismissed = onDismissed,
        )
        snackbar.show()
    }

    /**
     * 主动关闭指定 snackbar，并保留框架侧传入的关闭原因。
     * Proactively dismisses the snackbar and preserves the framework-supplied reason.
     */
    override fun dismiss(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
    ) {
        activeSnackbars[entryId]?.let { active ->
            active.requestedDismissReason = reason
            active.snackbar.dismiss()
        }
    }

    /**
     * 当前展示的 Snackbar 及其完成回调。
     * Currently visible Snackbar and its completion callback.
     */
    private data class ActiveSnackbar(
        val snackbar: Snackbar,
        val onDismissed: (TransientFeedbackDismissReason) -> Unit,
        var requestedDismissReason: TransientFeedbackDismissReason? = null,
    )
}

/**
 * 使用 Android Toast 展示 declarative toast 请求。
 * Presents declarative toast requests with Android Toast.
 *
 * Android Toast 没有可靠的完成回调，因此用主线程定时器模拟展示完成。
 * Android Toast has no reliable completion callback, so a main-thread timer simulates display completion.
 */
class AndroidToastOverlayPresenter(
    private val appContext: Context,
) : ToastOverlayPresenter {
    private val handler = Handler(Looper.getMainLooper())
    private val activeToasts = mutableMapOf<OverlayEntryId, ActiveToast>()

    /**
     * 创建并展示一个平台 Toast。
     * Creates and shows one platform Toast.
     */
    override fun show(
        entryId: OverlayEntryId,
        spec: ToastOverlaySpec,
        onDismissed: (TransientFeedbackDismissReason) -> Unit,
    ) {
        val toast = Toast.makeText(
            appContext,
            spec.message,
            spec.duration.toPlatformDuration(),
        )
        // Toast 没有 onDismissed callback，使用平台时长近似完成时间并通知队列继续 drain。
        // Toast has no onDismissed callback; approximate its duration so the queue can keep draining.
        val timeout = Runnable {
            complete(
                entryId = entryId,
                reason = TransientFeedbackDismissReason.Timeout,
                cancelToast = false,
            )
        }
        activeToasts[entryId] = ActiveToast(
            toast = toast,
            timeout = timeout,
            onDismissed = onDismissed,
        )
        toast.show()
        handler.postDelayed(timeout, spec.duration.toDisplayMillis())
    }

    /**
     * 主动关闭 toast，并立即完成对应队列项。
     * Proactively cancels the toast and completes the matching queue item immediately.
     */
    override fun dismiss(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
    ) {
        complete(
            entryId = entryId,
            reason = reason,
            cancelToast = true,
        )
    }

    /**
     * 完成 toast 展示并清理定时器。
     * Completes toast display and clears the timeout callback.
     */
    private fun complete(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
        cancelToast: Boolean,
    ) {
        val active = activeToasts.remove(entryId) ?: return
        handler.removeCallbacks(active.timeout)
        if (cancelToast) {
            active.toast.cancel()
        }
        active.onDismissed(reason)
    }

    /**
     * 当前展示的 Toast、模拟完成定时器和完成回调。
     * Currently visible Toast, simulated completion timer, and completion callback.
     */
    private data class ActiveToast(
        val toast: Toast,
        val timeout: Runnable,
        val onDismissed: (TransientFeedbackDismissReason) -> Unit,
    )
}

/**
 * 转换框架 snackbar duration 到 Material Snackbar duration。
 * Converts framework snackbar duration to Material Snackbar duration.
 */
private fun SnackbarDuration.toPlatformDuration(): Int {
    return when (this) {
        SnackbarDuration.Short -> Snackbar.LENGTH_SHORT
        SnackbarDuration.Long -> Snackbar.LENGTH_LONG
        SnackbarDuration.Indefinite -> Snackbar.LENGTH_INDEFINITE
    }
}

/**
 * 转换框架 toast duration 到 Android Toast duration。
 * Converts framework toast duration to Android Toast duration.
 */
private fun ToastDuration.toPlatformDuration(): Int {
    return when (this) {
        ToastDuration.Short -> Toast.LENGTH_SHORT
        ToastDuration.Long -> Toast.LENGTH_LONG
    }
}

/**
 * 估算 Android Toast 的展示时长，用于补足缺失的完成回调。
 * Estimates Android Toast display duration to compensate for the missing completion callback.
 */
private fun ToastDuration.toDisplayMillis(): Long {
    return when (this) {
        ToastDuration.Short -> 2_000L
        ToastDuration.Long -> 3_500L
    }
}

/**
 * 将 Material Snackbar dismiss 事件映射为框架 dismiss reason。
 * Maps Material Snackbar dismiss events to framework dismiss reasons.
 */
private fun Int.toDismissReason(): TransientFeedbackDismissReason {
    return when (this) {
        Snackbar.Callback.DISMISS_EVENT_TIMEOUT -> TransientFeedbackDismissReason.Timeout
        Snackbar.Callback.DISMISS_EVENT_ACTION -> TransientFeedbackDismissReason.Action
        Snackbar.Callback.DISMISS_EVENT_SWIPE -> TransientFeedbackDismissReason.Gesture
        Snackbar.Callback.DISMISS_EVENT_CONSECUTIVE -> TransientFeedbackDismissReason.Replaced
        else -> TransientFeedbackDismissReason.Platform
    }
}
