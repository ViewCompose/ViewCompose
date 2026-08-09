package com.viewcompose.overlay.android

import android.util.Log
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayContent
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayHandle
import com.viewcompose.ui.foundation.ModalBottomSheetOverlayPresenter
import com.viewcompose.ui.foundation.ModalBottomSheetOverlaySpec
import com.viewcompose.ui.foundation.OverlayEntryId
import com.viewcompose.ui.foundation.SnackbarOverlayPresenter
import com.viewcompose.ui.foundation.SnackbarOverlaySpec
import com.viewcompose.ui.foundation.TransientFeedbackDismissReason
import java.util.concurrent.atomic.AtomicBoolean

/** Reports the absence of a design-owned snackbar presenter and completes the queue entry. */
internal class UnsupportedSnackbarOverlayPresenter : SnackbarOverlayPresenter {
    private val reported = AtomicBoolean(false)

    override fun show(
        entryId: OverlayEntryId,
        spec: SnackbarOverlaySpec,
        onDismissed: (TransientFeedbackDismissReason) -> Unit,
    ) {
        if (reported.compareAndSet(false, true)) {
            Log.i(TAG, "Snackbar overlay is unsupported by the active Android overlay integration.")
        }
        onDismissed(TransientFeedbackDismissReason.Platform)
    }

    override fun dismiss(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
    ) = Unit
}

/** Keeps unsupported modal-sheet requests inert without substituting a Material implementation. */
internal class UnsupportedModalBottomSheetOverlayPresenter : ModalBottomSheetOverlayPresenter {
    private val reported = AtomicBoolean(false)

    override fun show(
        entryId: OverlayEntryId,
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ): ModalBottomSheetOverlayHandle {
        if (reported.compareAndSet(false, true)) {
            Log.i(TAG, "Modal bottom-sheet overlay is unsupported by the active Android overlay integration.")
        }
        return UnsupportedModalBottomSheetOverlayHandle
    }
}

private object UnsupportedModalBottomSheetOverlayHandle : ModalBottomSheetOverlayHandle {
    override fun update(
        spec: ModalBottomSheetOverlaySpec,
        content: ModalBottomSheetOverlayContent,
    ) = Unit

    override fun dismiss() = Unit
}

private const val TAG = "ViewCompose"
