package com.viewcompose.ui.foundation

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ProgressIndicatorNodeProps
import com.viewcompose.ui.unit.UiDp

/**
 * Emits a linear determinate or indeterminate progress indicator.
 *
 * A `null` [progress] selects indeterminate mode. Non-null values are mapped to the platform
 * progress range by the renderer. Appearance resolves once from instance, scoped, and semantic
 * defaults in that order.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder that receives the emitted indicator node
 * @param progress determinate fraction interpreted by the renderer, or `null` for indeterminate mode
 * @param overrides sparse instance appearance applied after scoped [ProvideLinearProgressIndicatorOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after fill-width and resolved thickness
 */
fun UiTreeBuilder.LinearProgressIndicator(
    progress: Float? = null,
    overrides: LinearProgressIndicatorOverrides = LinearProgressIndicatorOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = ProgressIndicatorDefaults.resolveLinear(overrides)
    emit(
        type = NodeType.LinearProgressIndicator,
        key = key,
        spec = ProgressIndicatorNodeProps(
            progress = progress,
            indicatorColor = appearance.indicatorColor,
            trackColor = appearance.trackColor,
            trackThickness = appearance.trackThickness,
            indicatorSize = UiDp.Zero,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(appearance.trackThickness)
            .then(modifier),
    )
}

/**
 * Emits a circular determinate or indeterminate progress indicator.
 *
 * A `null` [progress] selects indeterminate mode. Appearance resolves once from instance, scoped,
 * and semantic defaults in that order.
 *
 * @sample com.viewcompose.ui.foundation.samples.componentOverridesSample
 * @receiver active tree builder that receives the emitted indicator node
 * @param progress determinate fraction interpreted by the renderer, or `null` for indeterminate mode
 * @param overrides sparse instance appearance applied after scoped [ProvideCircularProgressIndicatorOverrides]
 * @param key optional stable sibling identity used during reconciliation
 * @param modifier ordered configuration appended after the resolved square size
 */
fun UiTreeBuilder.CircularProgressIndicator(
    progress: Float? = null,
    overrides: CircularProgressIndicatorOverrides = CircularProgressIndicatorOverrides.None,
    key: Any? = null,
    modifier: Modifier = Modifier,
) {
    val appearance = ProgressIndicatorDefaults.resolveCircular(overrides)
    emit(
        type = NodeType.CircularProgressIndicator,
        key = key,
        spec = ProgressIndicatorNodeProps(
            progress = progress,
            indicatorColor = appearance.indicatorColor,
            trackColor = appearance.trackColor,
            trackThickness = appearance.trackThickness,
            indicatorSize = appearance.size,
        ),
        modifier = Modifier
            .size(width = appearance.size, height = appearance.size)
            .then(modifier),
    )
}

/**
 * Submits a keyed snackbar request to the active overlay presenter.
 *
 * No request is submitted when [visible] is `false`. Duration and queueing are presenter-owned;
 * callbacks run on the presenter thread and do not mutate [visible] automatically.
 *
 * @sample com.viewcompose.ui.foundation.samples.feedbackDslSample
 * @receiver active tree builder submitting the transient request
 * @param visible whether this render includes the snackbar request
 * @param message message displayed by the presenter
 * @param actionLabel optional label for the snackbar action
 * @param duration presenter duration category
 * @param queuePolicy behavior when another transient item is active
 * @param requestKey stable identity used for deduplication and replacement
 * @param onAction optional callback invoked when the action is accepted
 * @param onDismiss optional callback receiving the terminal dismissal reason
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
 * Submits a keyed toast request to the active overlay presenter.
 *
 * No request is submitted when [visible] is `false`. Queue and duration policy are resolved by the
 * presenter, and dismissal does not mutate caller state.
 *
 * @sample com.viewcompose.ui.foundation.samples.feedbackDslSample
 * @receiver active tree builder submitting the transient request
 * @param visible whether this render includes the toast request
 * @param message message displayed by the presenter
 * @param duration presenter duration category
 * @param queuePolicy behavior when another transient item is active
 * @param requestKey stable identity used for deduplication and replacement
 * @param onDismiss optional callback receiving the terminal dismissal reason
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
 * Submits custom DSL content as a keyed modal dialog overlay.
 *
 * Content is captured during the current render and mounted by the presenter in a separate host.
 * The request remains caller-controlled: dismissal invokes [onDismissRequest], and the owner must
 * render [visible] as `false`. Saveable state is scoped to the request identity.
 *
 * @sample com.viewcompose.ui.foundation.samples.feedbackDslSample
 * @receiver active tree builder submitting the modal request
 * @param visible whether this render includes the dialog request
 * @param requestKey stable dialog and saveable-state identity within the session
 * @param dismissOnBackPress whether platform Back requests dismissal
 * @param dismissOnClickOutside whether a scrim click requests dismissal
 * @param position placement of dialog content within the host window
 * @param scrimOpacity scrim alpha in the inclusive `0..1` range
 * @param onDismissRequest optional callback invoked when the presenter requests removal
 * @param content DSL content captured synchronously for the overlay surface
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
 * Submits custom DSL content as a keyed popup anchored to a rendered semantics id.
 *
 * [anchorId] must resolve in the same host window. Placement is recomputed by the presenter from
 * the current anchor and environment; dismissal requests do not mutate [visible]. Saveable state
 * is scoped to [requestKey].
 *
 * @sample com.viewcompose.ui.foundation.samples.feedbackDslSample
 * @receiver active tree builder submitting the popup request
 * @param visible whether this render includes the popup request
 * @param anchorId semantics id of the rendered anchor view
 * @param requestKey stable popup and saveable-state identity within the session
 * @param alignment preferred placement relative to the anchor
 * @param overflowPolicy flip and clamp behavior near window edges
 * @param windowMargin minimum logical distance from the window edge
 * @param dismissOnClickOutside whether an outside click requests dismissal
 * @param focusable whether the popup may receive focus and keyboard input
 * @param offsetX horizontal logical offset applied after anchored placement
 * @param offsetY vertical logical offset applied after anchored placement
 * @param onDismissRequest optional callback invoked when the presenter requests removal
 * @param content DSL content captured synchronously for the overlay surface
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
 * Requests a modal bottom sheet with a stable session-scoped identity.
 *
 * [visible] controls whether the request exists. Behavior remains explicit while appearance
 * resolves from [ModalBottomSheetDefaults], nested [ProvideModalBottomSheetOverrides] scopes, and
 * instance [overrides]. The complete appearance and captured content are sent to the active
 * presenter on first show and every changed same-key commit. The owner must remove the request
 * after [onDismissRequest].
 *
 * @sample com.viewcompose.ui.foundation.samples.modalBottomSheetAppearanceSample
 * @receiver active tree builder submitting the overlay request
 * @param visible whether this render keeps the bottom-sheet request active
 * @param requestKey stable request identity within the current render session
 * @param dismissOnBackPress whether platform Back requests dismissal
 * @param dismissOnClickOutside whether a scrim click requests dismissal
 * @param skipPartiallyExpanded whether presenters with a partial state must omit it
 * @param overrides sparse instance appearance applied after scoped bottom-sheet overrides
 * @param onDismissRequest callback invoked by platform dismissal on the presenter thread
 * @param content subtree captured with the resolved content color for the overlay session
 */
fun UiTreeBuilder.ModalBottomSheet(
    visible: Boolean,
    requestKey: String = "bottom_sheet",
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    skipPartiallyExpanded: Boolean = false,
    overrides: ModalBottomSheetOverrides = ModalBottomSheetOverrides.None,
    onDismissRequest: (() -> Unit)? = null,
    content: UiTreeBuilder.() -> Unit,
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    if (!visible) {
        return
    }
    val appearance = ModalBottomSheetDefaults.resolve(overrides)
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
                appearance = appearance,
                onDismissRequest = onDismissRequest,
            ),
            contentToken = ModalBottomSheetOverlayContent(
                surface = captureOverlaySurfaceContent(
                    content = {
                        ProvideLocal(LocalContentColor, appearance.contentColor, content)
                    },
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
