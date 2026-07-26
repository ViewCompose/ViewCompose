package com.viewcompose.widget.core

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps

fun UiTreeBuilder.LinearProgressIndicator(
    progress: Float? = null,
    indicatorColor: Int = ProgressIndicatorDefaults.linearIndicatorColor(),
    trackColor: Int = ProgressIndicatorDefaults.linearTrackColor(),
    trackThickness: Int = ProgressIndicatorDefaults.linearTrackThickness(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emit(
        type = NodeType.LinearProgressIndicator,
        key = key,
        spec = ProgressIndicatorNodeProps(
            enabled = true,
            progress = progress,
            indicatorColor = indicatorColor,
            trackColor = trackColor,
            trackThickness = trackThickness,
            indicatorSize = 0,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(trackThickness)
            .then(modifier),
    )
}

fun UiTreeBuilder.CircularProgressIndicator(
    progress: Float? = null,
    indicatorColor: Int = ProgressIndicatorDefaults.circularIndicatorColor(),
    trackColor: Int = ProgressIndicatorDefaults.circularTrackColor(),
    size: Int = ProgressIndicatorDefaults.circularSize(),
    trackThickness: Int = ProgressIndicatorDefaults.circularTrackThickness(),
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    emit(
        type = NodeType.CircularProgressIndicator,
        key = key,
        spec = ProgressIndicatorNodeProps(
            enabled = true,
            progress = progress,
            indicatorColor = indicatorColor,
            trackColor = trackColor,
            trackThickness = trackThickness,
            indicatorSize = size,
        ),
        modifier = Modifier
            .size(width = size, height = size)
            .then(modifier),
    )
}

fun UiTreeBuilder.Snackbar(
    visible: Boolean,
    message: String,
    actionLabel: String? = null,
    duration: SnackbarDuration = SnackbarDuration.Short,
    queuePolicy: TransientFeedbackQueuePolicy = TransientFeedbackQueuePolicy.Enqueue,
    requestKey: String = "snackbar",
    onAction: (() -> Unit)? = null,
    onDismiss: ((TransientFeedbackDismissReason) -> Unit)? = null,
) {
    if (!visible) {
        return
    }
    submitOverlayRequest(
        OverlayRequest(
            key = requestKey,
            type = OverlayType.Snackbar,
            payload = SnackbarOverlaySpec(
                message = message,
                actionLabel = actionLabel,
                duration = duration,
                queuePolicy = queuePolicy,
                onAction = onAction,
                onDismiss = onDismiss,
            ),
        ),
    )
}

fun UiTreeBuilder.Toast(
    visible: Boolean,
    message: String,
    duration: ToastDuration = ToastDuration.Short,
    queuePolicy: TransientFeedbackQueuePolicy = TransientFeedbackQueuePolicy.Enqueue,
    requestKey: String = "toast",
    onDismiss: ((TransientFeedbackDismissReason) -> Unit)? = null,
) {
    if (!visible) {
        return
    }
    submitOverlayRequest(
        OverlayRequest(
            key = requestKey,
            type = OverlayType.Toast,
            payload = ToastOverlaySpec(
                message = message,
                duration = duration,
                queuePolicy = queuePolicy,
                onDismiss = onDismiss,
            ),
        ),
    )
}

fun UiTreeBuilder.Dialog(
    visible: Boolean,
    requestKey: String = "dialog",
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    position: DialogPosition = DialogPosition.Center,
    scrimOpacity: Float = Theme.overlays.scrimOpacity,
    onDismissRequest: (() -> Unit)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    if (!visible) {
        return
    }
    submitOverlayRequest(
        OverlayRequest(
            key = requestKey,
            type = OverlayType.Dialog,
            payload = DialogOverlaySpec(
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
                position = position,
                scrimOpacity = scrimOpacity,
                onDismissRequest = onDismissRequest,
            ),
            contentToken = DialogOverlayContent(
                surface = captureOverlaySurfaceContent(content),
            ),
        ),
    )
}

fun UiTreeBuilder.Popup(
    visible: Boolean,
    anchorId: String,
    requestKey: String = "popup",
    alignment: PopupAlignment = PopupAlignment.BelowStart,
    overflowPolicy: PopupOverflowPolicy = PopupOverflowPolicy.FlipThenClamp,
    windowMargin: Int = 8.dp,
    dismissOnClickOutside: Boolean = true,
    focusable: Boolean = true,
    offsetX: Int = 0,
    offsetY: Int = 0,
    onDismissRequest: (() -> Unit)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    if (!visible) {
        return
    }
    submitOverlayRequest(
        OverlayRequest(
            key = requestKey,
            type = OverlayType.Popup,
            payload = PopupOverlaySpec(
                anchorId = anchorId,
                alignment = alignment,
                overflowPolicy = overflowPolicy,
                windowMargin = windowMargin,
                dismissOnClickOutside = dismissOnClickOutside,
                focusable = focusable,
                offsetX = offsetX,
                offsetY = offsetY,
                onDismissRequest = onDismissRequest,
            ),
            contentToken = PopupOverlayContent(
                surface = captureOverlaySurfaceContent(content),
            ),
        ),
    )
}

fun UiTreeBuilder.ModalBottomSheet(
    visible: Boolean,
    requestKey: String = "bottom_sheet",
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    skipPartiallyExpanded: Boolean = false,
    scrimOpacity: Float = ModalBottomSheetDefaults.scrimOpacity(),
    navigationBarColor: Int? = ModalBottomSheetDefaults.navigationBarColor(),
    onDismissRequest: (() -> Unit)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    if (!visible) {
        return
    }
    submitOverlayRequest(
        OverlayRequest(
            key = requestKey,
            type = OverlayType.ModalBottomSheet,
            payload = ModalBottomSheetOverlaySpec(
                dismissOnBackPress = dismissOnBackPress,
                dismissOnClickOutside = dismissOnClickOutside,
                skipPartiallyExpanded = skipPartiallyExpanded,
                scrimOpacity = scrimOpacity,
                navigationBarColor = navigationBarColor,
                onDismissRequest = onDismissRequest,
            ),
            contentToken = ModalBottomSheetOverlayContent(
                surface = captureOverlaySurfaceContent(content),
            ),
        ),
    )
}
