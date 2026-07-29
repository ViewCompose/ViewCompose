package com.viewcompose.widget.core

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import com.viewcompose.ui.unit.UiDp

/**
 * 发射线性进度指示器节点。
 * Emits a linear progress indicator node.
 *
 * progress 为 null 时表示不确定进度；非空时由 renderer 约束到平台进度范围。
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
 * 发射圆形进度指示器节点。
 * Emits a circular progress indicator node.
 *
 * size 与 trackThickness 一起描述测量和绘制参数，颜色默认来自当前主题 token。
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
 * 请求展示 snackbar 类型的瞬时反馈。
 * Requests a snackbar transient feedback overlay.
 *
 * visible 为 false 时不提交 overlay request，避免下一次 render 继续保留过期反馈。
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
    // snackbar 通过 overlay 通道交给宿主队列处理，而不是作为普通 VNode 留在树中。
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
 * 请求展示 toast 类型的瞬时反馈。
 * Requests a toast transient feedback overlay.
 *
 * queuePolicy 决定宿主在已有反馈展示时如何合并、替换或排队。
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
    // toast 没有 action，仅保留 dismiss 回调用于统计或同步业务状态。
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
 * 请求展示承载自定义内容的对话框 overlay。
 * Requests a dialog overlay that hosts custom UI content.
 *
 * content 会被捕获成 OverlaySurfaceContent，使宿主可以在独立窗口/容器中渲染同一套 DSL 节点。
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
    if (!visible) {
        return
    }
    // overlay 内容在当前 render session 中捕获，避免回调之后再访问失效的 builder。
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
                surface = captureOverlaySurfaceContent(content),
            ),
        ),
    )
}

/**
 * 请求展示锚定到指定 anchorId 的弹出层。
 * Requests a popup overlay anchored to the given anchorId.
 *
 * anchorId 应对应 renderer 可定位的宿主视图；overflowPolicy 决定越界时翻转或夹紧策略。
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
    if (!visible) {
        return
    }
    // Popup 使用 requestKey 去重，同一 key 的请求会被 overlay host 视为同一个表面更新。
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
                surface = captureOverlaySurfaceContent(content),
            ),
        ),
    )
}

/**
 * 请求展示模态底部弹层。
 * Requests a modal bottom sheet overlay.
 *
 * 底部弹层同样捕获 DSL 内容，具体展开状态和系统栏处理交由 Android overlay presenter。
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
