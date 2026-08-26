package com.viewcompose.samples.tutorials

import com.viewcompose.ui.foundation.DropdownMenu
import com.viewcompose.ui.foundation.DropdownMenuItem
import com.viewcompose.ui.foundation.ModalBottomSheet
import com.viewcompose.ui.foundation.PopupAlignment
import com.viewcompose.ui.foundation.Snackbar
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TransientFeedbackQueuePolicy
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.overlayAnchor

// DOCS_REGION_START(overlay-bottom-sheet)
fun UiTreeBuilder.AccountActionsSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        visible = visible,
        requestKey = "account-actions",
        skipPartiallyExpanded = true,
        onDismissRequest = onDismissRequest,
    ) {
        Text("Account actions")
    }
}
// DOCS_REGION_END(overlay-bottom-sheet)

// DOCS_REGION_START(overlay-dropdown-menu)
fun UiTreeBuilder.ProfileMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    Text(
        text = "Profile",
        modifier = Modifier.overlayAnchor("profile-menu-anchor"),
    )
    DropdownMenu(
        expanded = expanded,
        anchorId = "profile-menu-anchor",
        requestKey = "profile-menu",
        alignment = PopupAlignment.BelowEnd,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem("Settings", onClick = onDismissRequest)
        DropdownMenuItem("Sign out", onClick = onDismissRequest)
    }
}
// DOCS_REGION_END(overlay-dropdown-menu)

// DOCS_REGION_START(overlay-snackbar)
fun UiTreeBuilder.SaveResultSnackbar(
    visible: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    Snackbar(
        visible = visible,
        requestKey = "save-result",
        message = "Saved",
        actionLabel = "Undo",
        queuePolicy = TransientFeedbackQueuePolicy.ReplaceSameKey,
        onAction = onUndo,
        onDismiss = { onDismiss() },
    )
}
// DOCS_REGION_END(overlay-snackbar)
