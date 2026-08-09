package com.viewcompose.overlay.material3.android.presenter

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.viewcompose.ui.foundation.OverlayEntryId
import com.viewcompose.ui.foundation.SnackbarDuration
import com.viewcompose.ui.foundation.SnackbarOverlayPresenter
import com.viewcompose.ui.foundation.SnackbarOverlaySpec
import com.viewcompose.ui.foundation.TransientFeedbackDismissReason

/**
 * Presents declarative transient-feedback requests as Material [Snackbar] instances.
 *
 * Active bars are keyed by [OverlayEntryId], allowing the core queue to dismiss exactly one
 * session-owned request. Material dismissal events are translated to framework reasons; an explicit
 * queue dismissal reason takes precedence over the callback's platform event.
 *
 * @param anchorView attached view used by Material to find a suitable snackbar parent
 */
class AndroidSnackbarOverlayPresenter(
    private val anchorView: View,
) : SnackbarOverlayPresenter {
    private val activeSnackbars = mutableMapOf<OverlayEntryId, ActiveSnackbar>()

    /**
     * Creates and shows one snackbar, reporting its terminal dismissal exactly once.
     *
     * The action callback invokes [SnackbarOverlaySpec.onAction]. Completion is reported separately
     * through [onDismissed], after Material supplies the final dismissal event.
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
                    // Material's dismissal callback remains the single terminal queue signal.
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

    /** Dismisses [entryId] and preserves [reason] for the eventual Material callback. */
    override fun dismiss(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
    ) {
        activeSnackbars[entryId]?.let { active ->
            active.requestedDismissReason = reason
            active.snackbar.dismiss()
        }
    }

    /** Platform bar plus the queue callback and any explicit dismissal reason. */
    private data class ActiveSnackbar(
        val snackbar: Snackbar,
        val onDismissed: (TransientFeedbackDismissReason) -> Unit,
        var requestedDismissReason: TransientFeedbackDismissReason? = null,
    )
}

/** Converts framework snackbar duration to the Material integer contract. */
private fun SnackbarDuration.toPlatformDuration(): Int {
    return when (this) {
        SnackbarDuration.Short -> Snackbar.LENGTH_SHORT
        SnackbarDuration.Long -> Snackbar.LENGTH_LONG
        SnackbarDuration.Indefinite -> Snackbar.LENGTH_INDEFINITE
    }
}

/** Maps Material dismissal events to stable framework reasons. */
private fun Int.toDismissReason(): TransientFeedbackDismissReason {
    return when (this) {
        Snackbar.Callback.DISMISS_EVENT_TIMEOUT -> TransientFeedbackDismissReason.Timeout
        Snackbar.Callback.DISMISS_EVENT_ACTION -> TransientFeedbackDismissReason.Action
        Snackbar.Callback.DISMISS_EVENT_SWIPE -> TransientFeedbackDismissReason.Gesture
        Snackbar.Callback.DISMISS_EVENT_CONSECUTIVE -> TransientFeedbackDismissReason.Replaced
        else -> TransientFeedbackDismissReason.Platform
    }
}
