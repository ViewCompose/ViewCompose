package com.viewcompose.overlay.android.presenter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.viewcompose.ui.foundation.OverlayEntryId
import com.viewcompose.ui.foundation.ToastDuration
import com.viewcompose.ui.foundation.ToastOverlayPresenter
import com.viewcompose.ui.foundation.ToastOverlaySpec
import com.viewcompose.ui.foundation.TransientFeedbackDismissReason

/** Presents Android Toast requests while keeping their approximate completion session-scoped. */
internal class AndroidToastOverlayPresenter(
    appContext: Context,
) : ToastOverlayPresenter {
    private val context = appContext.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val activeToasts = mutableMapOf<OverlayEntryId, ActiveToast>()

    override fun show(
        entryId: OverlayEntryId,
        spec: ToastOverlaySpec,
        onDismissed: (TransientFeedbackDismissReason) -> Unit,
    ) {
        val toast = Toast.makeText(
            context,
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
