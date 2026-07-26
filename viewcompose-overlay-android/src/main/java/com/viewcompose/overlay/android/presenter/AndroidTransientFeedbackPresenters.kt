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

class AndroidSnackbarOverlayPresenter(
    private val anchorView: View,
) : SnackbarOverlayPresenter {
    private val activeSnackbars = mutableMapOf<OverlayEntryId, ActiveSnackbar>()

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

    override fun dismiss(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
    ) {
        activeSnackbars[entryId]?.let { active ->
            active.requestedDismissReason = reason
            active.snackbar.dismiss()
        }
    }

    private data class ActiveSnackbar(
        val snackbar: Snackbar,
        val onDismissed: (TransientFeedbackDismissReason) -> Unit,
        var requestedDismissReason: TransientFeedbackDismissReason? = null,
    )
}

class AndroidToastOverlayPresenter(
    private val appContext: Context,
) : ToastOverlayPresenter {
    private val handler = Handler(Looper.getMainLooper())
    private val activeToasts = mutableMapOf<OverlayEntryId, ActiveToast>()

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

    private data class ActiveToast(
        val toast: Toast,
        val timeout: Runnable,
        val onDismissed: (TransientFeedbackDismissReason) -> Unit,
    )
}

private fun SnackbarDuration.toPlatformDuration(): Int {
    return when (this) {
        SnackbarDuration.Short -> Snackbar.LENGTH_SHORT
        SnackbarDuration.Long -> Snackbar.LENGTH_LONG
        SnackbarDuration.Indefinite -> Snackbar.LENGTH_INDEFINITE
    }
}

private fun ToastDuration.toPlatformDuration(): Int {
    return when (this) {
        ToastDuration.Short -> Toast.LENGTH_SHORT
        ToastDuration.Long -> Toast.LENGTH_LONG
    }
}

private fun ToastDuration.toDisplayMillis(): Long {
    return when (this) {
        ToastDuration.Short -> 2_000L
        ToastDuration.Long -> 3_500L
    }
}

private fun Int.toDismissReason(): TransientFeedbackDismissReason {
    return when (this) {
        Snackbar.Callback.DISMISS_EVENT_TIMEOUT -> TransientFeedbackDismissReason.Timeout
        Snackbar.Callback.DISMISS_EVENT_ACTION -> TransientFeedbackDismissReason.Action
        Snackbar.Callback.DISMISS_EVENT_SWIPE -> TransientFeedbackDismissReason.Gesture
        Snackbar.Callback.DISMISS_EVENT_CONSECUTIVE -> TransientFeedbackDismissReason.Replaced
        else -> TransientFeedbackDismissReason.Platform
    }
}
