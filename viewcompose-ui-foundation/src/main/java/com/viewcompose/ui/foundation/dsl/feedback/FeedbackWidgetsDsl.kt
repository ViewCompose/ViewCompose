package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import com.viewcompose.ui.unit.UiDp

/**
 * Emits a linear progress indicator node.
 *
 * A null progress represents indeterminate mode; non-null values are clamped/mapped by the renderer to platform progress.
 */
fun UiTreeBuilder.LinearProgressIndicator(
    progress: Float? = null,
    indicatorColor: Int = ProgressIndicatorDefaults.linearIndicatorColor(),
    trackColor: Int = ProgressIndicatorDefaults.linearTrackColor(),
    trackThickness: UiDp = ProgressIndicatorDefaults.linearTrackThickness(),
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
            indicatorSize = UiDp.Zero,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(trackThickness)
            .then(modifier),
    )
}

/**
 * Emits a circular progress indicator node.
 *
 * size and trackThickness describe measurement/drawing parameters, while colors default from current theme tokens.
 */
fun UiTreeBuilder.CircularProgressIndicator(
    progress: Float? = null,
    indicatorColor: Int = ProgressIndicatorDefaults.circularIndicatorColor(),
    trackColor: Int = ProgressIndicatorDefaults.circularTrackColor(),
    size: UiDp = ProgressIndicatorDefaults.circularSize(),
    trackThickness: UiDp = ProgressIndicatorDefaults.circularTrackThickness(),
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

/**
 * Requests a snackbar transient feedback overlay.
 *
 * When visible is false no overlay request is submitted, preventing stale feedback from surviving the next render.
 */
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
    // Snackbar goes through the overlay channel for host-side queueing instead of staying in the VNode tree.
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

/**
 * Requests a toast transient feedback overlay.
 *
 * queuePolicy tells the host how to merge, replace, or queue while another feedback item is visible.
 */
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
    // Toast has no action and only keeps the dismiss callback for analytics or business state sync.
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

/**
 * Requests a dialog overlay that hosts custom UI content.
 *
 * content is captured as OverlaySurfaceContent so the host can render the same DSL nodes in a separate window/container.
 */
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
    val saveableStateHolder = rememberSaveableStateHolder()
    if (!visible) {
        return
    }
    val saveableStateKey = overlaySaveableStateKey(
        type = OverlayType.Dialog,
        requestKey = requestKey,
    )
    retainOverlaySaveableStateKey(
        holder = saveableStateHolder,
        key = saveableStateKey,
    )
    // Overlay content is captured during the current render session to avoid using an invalid builder later.
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
                surface = captureOverlaySurfaceContent(
                    content = content,
                    saveableStateHolder = saveableStateHolder,
                    saveableStateKey = saveableStateKey.takeIf { saveableStateHolder != null },
                ),
            ),
        ),
    )
}

/**
 * Requests a popup overlay anchored to the given anchorId.
 *
 * anchorId should match a host view the renderer can locate; overflowPolicy decides flip/clamp behavior near edges.
 */
fun UiTreeBuilder.Popup(
    visible: Boolean,
    anchorId: String,
    requestKey: String = "popup",
    alignment: PopupAlignment = PopupAlignment.BelowStart,
    overflowPolicy: PopupOverflowPolicy = PopupOverflowPolicy.FlipThenClamp,
    windowMargin: UiDp = 8.dp,
    dismissOnClickOutside: Boolean = true,
    focusable: Boolean = true,
    offsetX: UiDp = UiDp.Zero,
    offsetY: UiDp = UiDp.Zero,
    onDismissRequest: (() -> Unit)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    if (!visible) {
        return
    }
    val saveableStateKey = overlaySaveableStateKey(
        type = OverlayType.Popup,
        requestKey = requestKey,
    )
    retainOverlaySaveableStateKey(
        holder = saveableStateHolder,
        key = saveableStateKey,
    )
    // Popup uses requestKey for identity so the overlay host can update the same surface request.
    submitOverlayRequest(
        OverlayRequest(
            key = requestKey,
            type = OverlayType.Popup,
            payload = PopupOverlaySpec(
                anchorId = anchorId,
                alignment = alignment,
                overflowPolicy = overflowPolicy,
                windowMargin = Environment.density.roundToPx(windowMargin),
                dismissOnClickOutside = dismissOnClickOutside,
                focusable = focusable,
                offsetX = Environment.density.roundToPx(offsetX),
                offsetY = Environment.density.roundToPx(offsetY),
                onDismissRequest = onDismissRequest,
            ),
            contentToken = PopupOverlayContent(
                surface = captureOverlaySurfaceContent(
                    content = content,
                    saveableStateHolder = saveableStateHolder,
                    saveableStateKey = saveableStateKey.takeIf { saveableStateHolder != null },
                ),
            ),
        ),
    )
}

/**
 * Requests a modal bottom sheet overlay.
 *
 * The sheet also captures DSL content, while expansion state and system-bar handling are delegated to Android presenters.
 */
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
    val saveableStateHolder = rememberSaveableStateHolder()
    if (!visible) {
        return
    }
    val saveableStateKey = overlaySaveableStateKey(
        type = OverlayType.ModalBottomSheet,
        requestKey = requestKey,
    )
    retainOverlaySaveableStateKey(
        holder = saveableStateHolder,
        key = saveableStateKey,
    )
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
                surface = captureOverlaySurfaceContent(
                    content = content,
                    saveableStateHolder = saveableStateHolder,
                    saveableStateKey = saveableStateKey.takeIf { saveableStateHolder != null },
                ),
            ),
        ),
    )
}

private fun overlaySaveableStateKey(
    type: OverlayType,
    requestKey: String,
): String {
    return "${type.name}:${requestKey.length}:$requestKey"
}

private fun retainOverlaySaveableStateKey(
    holder: SaveableStateHolder?,
    key: String,
) {
    holder ?: return
    SideEffect {
        holder.retainKeys(setOf(key))
    }
}
