package com.viewcompose.renderer.view.tree

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.util.Log
import com.viewcompose.gesture.core.AnchoredThresholdPolicy
import com.viewcompose.gesture.core.LockedAxis
import com.viewcompose.gesture.core.resolveLockAxis
import com.viewcompose.gesture.core.resolveAnchoredSettleTarget
import com.viewcompose.gesture.core.shouldActivateTransform
import com.viewcompose.renderer.R
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.GesturePriority
import com.viewcompose.ui.gesture.PointerChange
import com.viewcompose.ui.gesture.PointerEvent
import com.viewcompose.ui.gesture.PointerEventResult
import com.viewcompose.ui.gesture.PointerEventType
import com.viewcompose.ui.gesture.TransformDelta
import com.viewcompose.ui.gesture.NestedScrollSource
import com.viewcompose.ui.gesture.ScrollDelta
import com.viewcompose.ui.gesture.ScrollVelocity
import com.viewcompose.ui.modifier.CombinedClickableModifierElement
import com.viewcompose.renderer.view.container.DeclarativeNestedScrollHostLayout
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private const val GESTURE_LOG_TAG: String = "ViewComposeGesture"
private const val INVALID_POINTER_ID: Int = -1

internal object ModifierGestureApplier {
    fun applyGestureState(
        view: View,
        resolved: ResolvedModifiers,
    ) {
        val hasGesture = resolved.pointerInput != null ||
            resolved.combinedClickable != null ||
            resolved.draggable != null ||
            resolved.anchoredDraggable != null ||
            resolved.transformable != null
        if (!hasGesture) {
            (view.getTag(R.id.viewcompose_gesture_dispatcher) as? ViewGestureDispatcher)?.dispose()
            view.setTag(R.id.viewcompose_gesture_dispatcher, null)
            view.setOnTouchListener(null)
            return
        }
        val dispatcher = (view.getTag(R.id.viewcompose_gesture_dispatcher) as? ViewGestureDispatcher)
            ?: ViewGestureDispatcher(view).also {
                view.setTag(R.id.viewcompose_gesture_dispatcher, it)
            }
        dispatcher.update(resolved)
        view.setOnTouchListener(dispatcher)
    }
}

private class ViewGestureDispatcher(
    private val hostView: View,
) : View.OnTouchListener {
    private var resolved: ResolvedModifiers = ResolvedModifiers()
    private val touchSlop: Float = ViewConfiguration.get(hostView.context).scaledTouchSlop.toFloat()
    private val minimumFlingVelocity: Float =
        ViewConfiguration.get(hostView.context).scaledMinimumFlingVelocity.toFloat()
    private val anchoredThresholdPolicy = AnchoredThresholdPolicy()

    private var velocityTracker: VelocityTracker? = null
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lockAxis: LockedAxis? = null
    private var dragStarted = false
    private var localDragTotal = 0f
    private var swipeStartAnchorPx = 0f
    private var combinedTapConsumed = false
    private var pointerStreamActive = false
    private var transformStreamActive = false
    private var transformPastTouchSlop = false
    private var transformMidX = Float.NaN
    private var transformMidY = Float.NaN
    private var transformAngle = Float.NaN
    private var transformSpan = Float.NaN
    private var transformPanMotion = 0f
    private var transformZoomMotion = 0f
    private var transformRotationMotion = 0f
    private var primaryTransformPointerId = INVALID_POINTER_ID
    private var secondaryTransformPointerId = INVALID_POINTER_ID

    private val combinedDetector = GestureDetector(
        hostView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return resolved.combinedClickable?.isParticipating() == true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val element = resolved.combinedClickable ?: return false
                val onClick = element.onClick ?: return false
                if (!element.enabled) return false
                onClick()
                combinedTapConsumed = true
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val element = resolved.combinedClickable ?: return false
                val onDoubleClick = element.onDoubleClick ?: return false
                if (!element.enabled) return false
                onDoubleClick()
                combinedTapConsumed = true
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val element = resolved.combinedClickable ?: return
                val onLongClick = element.onLongClick ?: return
                if (!element.enabled) return
                onLongClick()
                combinedTapConsumed = true
            }
        },
    )

    fun update(resolved: ResolvedModifiers) {
        this.resolved = resolved
    }

    fun dispose() {
        velocityTracker?.recycle()
        velocityTracker = null
        pointerStreamActive = false
    }

    override fun onTouch(
        v: View,
        event: MotionEvent,
    ): Boolean {
        val action = event.actionMasked
        val hasPointerInput = resolved.pointerInput != null
        if (action == MotionEvent.ACTION_DOWN && hasPointerInput) {
            pointerStreamActive = true
        }
        val pointerConsumed = dispatchPointerInput(event)
        if (pointerConsumed) {
            if (action == MotionEvent.ACTION_DOWN &&
                resolved.gesturePriority?.priority == GesturePriority.High
            ) {
                hostView.parent?.requestDisallowInterceptTouchEvent(true)
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                combinedTapConsumed = false
                resetTrackingState()
            }
            return true
        }

        val combinedParticipating = resolved.combinedClickable?.isParticipating() == true
        val requiresContinuousStream =
            hasPointerInput ||
            resolved.draggable?.enabled == true ||
                resolved.anchoredDraggable?.enabled == true ||
                resolved.transformable?.enabled == true
        if (combinedParticipating) {
            combinedDetector.onTouchEvent(event)
        }
        val transformConsumed = dispatchTransform(event)
        val dragSwipeConsumed = dispatchDragAndSwipe(event)
        if (action == MotionEvent.ACTION_DOWN &&
            resolved.gesturePriority?.priority == GesturePriority.High
        ) {
            hostView.parent?.requestDisallowInterceptTouchEvent(true)
        }
        val consumed = pointerConsumed || transformConsumed || dragSwipeConsumed || combinedTapConsumed
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            combinedTapConsumed = false
            pointerStreamActive = false
        }
        // combinedClickable owns tap gesture arbitration only when it has an active callback.
        if (combinedParticipating) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                resetTrackingState()
            }
            return true
        }
        if (transformStreamActive) {
            // Keep 2-finger transform stream attached to this node to avoid parent scroll interception.
            return true
        }
        if (pointerStreamActive) {
            return true
        }
        if (action == MotionEvent.ACTION_DOWN && requiresContinuousStream) {
            // Keep the pointer stream on this view so drag/swipe/transform can receive MOVE/UP events.
            return true
        }
        return consumed
    }

    private fun dispatchPointerInput(event: MotionEvent): Boolean {
        val pointer = resolved.pointerInput ?: return false
        val result = pointer.onEvent(
            event.toPointerEvent(),
        )
        return result == PointerEventResult.Consumed
    }

    private fun dispatchTransform(event: MotionEvent): Boolean {
        val transform = resolved.transformable ?: return false
        if (!transform.enabled) return false
        var dispatched = false
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                if (initializeTransformPointers(event) && captureTransformBaseline(event)) {
                    transformStreamActive = true
                    debugLog {
                        "transform-start pointers=${event.pointerCount} " +
                            "mid=(${transformMidX.format(1)}, ${transformMidY.format(1)}) " +
                            "angle=${transformAngle.format(2)} span=${transformSpan.format(2)}"
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val snapshot = resolveTransformSnapshot(event) ?: return false
                if (!transformStreamActive || transformMidX.isNaN() || transformMidY.isNaN()) {
                    captureTransformBaseline(event)
                    return false
                }
                val panX = snapshot.midX - transformMidX
                val panY = snapshot.midY - transformMidY
                val rotation = normalizeAngle(snapshot.angle - transformAngle)
                val zoom = if (transformSpan.isNaN() || transformSpan == 0f) {
                    1f
                } else {
                    snapshot.span / transformSpan
                }
                if (!transformPastTouchSlop) {
                    val centroidSize = snapshot.span * 0.5f
                    val panMotion = vectorMagnitude(panX, panY)
                    val zoomMotion = abs(1f - zoom) * centroidSize
                    val rotationMotion =
                        abs(Math.toRadians(rotation.toDouble()).toFloat()) * centroidSize
                    transformPanMotion += panMotion
                    transformZoomMotion += zoomMotion
                    transformRotationMotion += rotationMotion
                    if (shouldActivateTransform(
                            panMotion = transformPanMotion,
                            zoomMotion = transformZoomMotion,
                            rotationMotion = transformRotationMotion,
                            touchSlop = touchSlop,
                        )
                    ) {
                        transformPastTouchSlop = true
                        hostView.parent?.requestDisallowInterceptTouchEvent(true)
                        debugLog {
                            "transform-active panMotion=${transformPanMotion.format(2)} " +
                                "zoomMotion=${transformZoomMotion.format(2)} " +
                                "rotationMotion=${transformRotationMotion.format(2)} " +
                                "touchSlop=${touchSlop.format(2)}"
                        }
                    }
                }
                val hasTransformDelta = zoom != 1f || panX != 0f || panY != 0f || rotation != 0f
                if (transformPastTouchSlop && hasTransformDelta) {
                    val nestedParent = hostView.findNestedScrollHost()
                    val pan = ScrollDelta(panX, panY)
                    val preConsumed = nestedParent?.dispatchPreScrollFromDescendant(
                        available = pan,
                        source = NestedScrollSource.UserInput,
                    ) ?: ScrollDelta.Zero
                    val remainingPan = pan - preConsumed
                    transform.onTransform(
                        TransformDelta(
                            zoom = zoom,
                            panX = remainingPan.x,
                            panY = remainingPan.y,
                            rotation = rotation,
                        ),
                    )
                    nestedParent?.dispatchPostScrollFromDescendant(
                        consumed = remainingPan,
                        available = ScrollDelta.Zero,
                        source = NestedScrollSource.UserInput,
                    )
                    dispatched = true
                    debugLog {
                        "transform-move zoom=${zoom.format(3)} " +
                            "pan=(${panX.format(2)}, ${panY.format(2)}) " +
                            "rotation=${rotation.format(2)} pointers=${event.pointerCount}"
                    }
                }
                transformMidX = snapshot.midX
                transformMidY = snapshot.midY
                transformAngle = snapshot.angle
                transformSpan = snapshot.span
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val hasRemainingPair = initializeTransformPointers(
                    event = event,
                    excludeActionIndex = event.actionIndex,
                ) && captureTransformBaseline(
                    event = event,
                    excludeActionIndex = event.actionIndex,
                )
                if (!hasRemainingPair) {
                    resetTransformTracking()
                    debugLog { "transform-end action=${event.actionMasked} pointers=${event.pointerCount}" }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetTransformTracking()
                debugLog { "transform-end action=${event.actionMasked} pointers=${event.pointerCount}" }
            }
        }
        return dispatched
    }

    private fun dispatchDragAndSwipe(event: MotionEvent): Boolean {
        val draggable = resolved.draggable?.takeIf { it.enabled }
        val anchoredDraggable = resolved.anchoredDraggable?.takeIf { it.enabled }
        if (draggable == null && anchoredDraggable == null) {
            // Do not clear transform tracking here; transformable may coexist without drag/swipe.
            if (transformStreamActive) {
                debugLog { "drag-swipe bypassed while transform stream is active" }
            }
            resetDragSwipeTracking()
            return false
        }
        if (transformStreamActive && event.pointerCount > 1) {
            return false
        }
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = downX
                lastY = downY
                lockAxis = null
                dragStarted = false
                localDragTotal = 0f
                swipeStartAnchorPx = anchoredDraggable?.currentOffsetPx
                    ?: anchoredDraggable?.anchorOffsetsPx?.firstOrNull()
                    ?: 0f
                tracker.clear()
                tracker.addMovement(event)
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                tracker.addMovement(event)
                val rawX = event.rawX
                val rawY = event.rawY
                val dx = rawX - downX
                val dy = rawY - downY
                if (lockAxis == null) {
                    lockAxis = resolveLockAxis(
                        dx = dx,
                        dy = dy,
                        orientation = resolveGestureOrientation(draggable, anchoredDraggable),
                        touchSlop = touchSlop,
                    )
                    if (lockAxis != null) {
                        hostView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                val axis = lockAxis ?: return false
                val delta = resolveAxisDelta(
                    axis = axis,
                    rawX = rawX,
                    rawY = rawY,
                    lastX = lastX,
                    lastY = lastY,
                )
                lastX = rawX
                lastY = rawY
                if (abs(delta) <= 0f) {
                    return false
                }
                val nestedParent = hostView.findNestedScrollHost()
                val available = axis.toScrollDelta(delta)
                val preConsumed = nestedParent?.dispatchPreScrollFromDescendant(
                    available = available,
                    source = NestedScrollSource.UserInput,
                ) ?: ScrollDelta.Zero
                val remaining = available - preConsumed
                val localDelta = remaining.valueFor(axis)
                if (abs(localDelta) <= 0f) {
                    return !preConsumed.isZero
                }
                if (!dragStarted) {
                    dragStarted = true
                    draggable?.onDragStarted?.invoke()
                }
                draggable?.onDelta?.invoke(localDelta)
                anchoredDraggable?.onDelta?.invoke(localDelta)
                localDragTotal += localDelta
                nestedParent?.dispatchPostScrollFromDescendant(
                    consumed = remaining,
                    available = ScrollDelta.Zero,
                    source = NestedScrollSource.UserInput,
                )
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker.addMovement(event)
                val axis = lockAxis
                tracker.computeCurrentVelocity(1000)
                val rawVelocity = resolveAxisVelocity(axis = axis, tracker = tracker)
                val nestedParent = hostView.findNestedScrollHost()
                val availableVelocity = axis.toScrollVelocity(rawVelocity)
                val preConsumedVelocity = if (axis != null) {
                    nestedParent?.dispatchPreFlingFromDescendant(availableVelocity)
                        ?: ScrollVelocity.Zero
                } else {
                    ScrollVelocity.Zero
                }
                val remainingVelocity = availableVelocity - preConsumedVelocity
                val velocity = remainingVelocity.valueFor(axis)
                if (dragStarted) {
                    draggable?.onDragStopped?.invoke(remainingVelocity.valueFor(axis))
                }
                val swipeConsumed = if (axis != null && anchoredDraggable != null) {
                    runCatching {
                        val startOffset = anchoredDraggable.currentOffsetPx
                            ?: swipeStartAnchorPx
                        val settleResult = resolveAnchoredSettleTarget(
                            anchorsPx = anchoredDraggable.anchorOffsetsPx,
                            startOffsetPx = startOffset,
                            currentOffsetPx = startOffset + localDragTotal,
                            velocityPxPerSecond = velocity,
                            touchSlopPx = touchSlop,
                            minFlingVelocityPxPerSecond = minimumFlingVelocity,
                            thresholdPolicy = anchoredThresholdPolicy,
                        )
                        anchoredDraggable.onSettleToOffset(settleResult.targetOffsetPx)
                        true
                    }.getOrElse { error ->
                        debugLog { "anchored-settle-failed reason=${error.message}" }
                        false
                    }
                } else {
                    false
                }
                if (dragStarted || swipeConsumed) {
                    nestedParent?.dispatchPostFlingFromDescendant(
                        consumed = remainingVelocity,
                        available = ScrollVelocity.Zero,
                    )
                }
                val dragConsumed = dragStarted
                resetDragSwipeTracking()
                return dragConsumed || swipeConsumed
            }
        }
        return false
    }

    private fun resetTrackingState() {
        resetDragSwipeTracking()
        resetTransformTracking()
    }

    private fun resetDragSwipeTracking() {
        velocityTracker?.recycle()
        velocityTracker = null
        lockAxis = null
        dragStarted = false
        localDragTotal = 0f
        swipeStartAnchorPx = 0f
    }

    private fun resetTransformTracking() {
        transformMidX = Float.NaN
        transformMidY = Float.NaN
        transformAngle = Float.NaN
        transformSpan = Float.NaN
        transformPastTouchSlop = false
        transformPanMotion = 0f
        transformZoomMotion = 0f
        transformRotationMotion = 0f
        transformStreamActive = false
        primaryTransformPointerId = INVALID_POINTER_ID
        secondaryTransformPointerId = INVALID_POINTER_ID
        pointerStreamActive = false
    }

    private fun resolveGestureOrientation(
        draggable: com.viewcompose.ui.modifier.DraggableModifierElement?,
        anchoredDraggable: com.viewcompose.ui.modifier.AnchoredDraggableModifierElement?,
    ): GestureOrientation {
        return draggable?.orientation ?: anchoredDraggable?.orientation ?: GestureOrientation.Free
    }

    private fun resolveAxisDelta(
        axis: LockedAxis,
        rawX: Float,
        rawY: Float,
        lastX: Float,
        lastY: Float,
    ): Float {
        return when (axis) {
            LockedAxis.Horizontal -> rawX - lastX
            LockedAxis.Vertical -> rawY - lastY
        }
    }

    private fun resolveAxisVelocity(
        axis: LockedAxis?,
        tracker: VelocityTracker,
    ): Float {
        return when (axis) {
            LockedAxis.Horizontal -> tracker.xVelocity
            LockedAxis.Vertical -> tracker.yVelocity
            null -> 0f
        }
    }

    private data class TransformSnapshot(
        val midX: Float,
        val midY: Float,
        val angle: Float,
        val span: Float,
    )

    private fun initializeTransformPointers(
        event: MotionEvent,
        excludeActionIndex: Int? = null,
    ): Boolean {
        val availablePointerIds = collectAvailablePointerIds(event, excludeActionIndex)
        if (availablePointerIds.size < 2) {
            primaryTransformPointerId = INVALID_POINTER_ID
            secondaryTransformPointerId = INVALID_POINTER_ID
            return false
        }
        val keepCurrentPair = availablePointerIds.contains(primaryTransformPointerId) &&
            availablePointerIds.contains(secondaryTransformPointerId) &&
            primaryTransformPointerId != secondaryTransformPointerId
        if (keepCurrentPair) {
            return true
        }
        primaryTransformPointerId = availablePointerIds[0]
        secondaryTransformPointerId = availablePointerIds[1]
        return true
    }

    private fun captureTransformBaseline(
        event: MotionEvent,
        excludeActionIndex: Int? = null,
    ): Boolean {
        val snapshot = resolveTransformSnapshot(
            event = event,
            excludeActionIndex = excludeActionIndex,
        ) ?: return false
        transformPastTouchSlop = false
        transformPanMotion = 0f
        transformZoomMotion = 0f
        transformRotationMotion = 0f
        transformMidX = snapshot.midX
        transformMidY = snapshot.midY
        transformAngle = snapshot.angle
        transformSpan = snapshot.span
        return true
    }

    private fun resolveTransformSnapshot(
        event: MotionEvent,
        excludeActionIndex: Int? = null,
    ): TransformSnapshot? {
        if (!initializeTransformPointers(event, excludeActionIndex)) {
            return null
        }
        var primaryIndex = event.findPointerIndex(primaryTransformPointerId)
        var secondaryIndex = event.findPointerIndex(secondaryTransformPointerId)
        if (!isPointerUsable(primaryIndex, excludeActionIndex) ||
            !isPointerUsable(secondaryIndex, excludeActionIndex) ||
            primaryIndex == secondaryIndex
        ) {
            if (!initializeTransformPointers(event, excludeActionIndex)) {
                return null
            }
            primaryIndex = event.findPointerIndex(primaryTransformPointerId)
            secondaryIndex = event.findPointerIndex(secondaryTransformPointerId)
            if (!isPointerUsable(primaryIndex, excludeActionIndex) ||
                !isPointerUsable(secondaryIndex, excludeActionIndex) ||
                primaryIndex == secondaryIndex
            ) {
                return null
            }
        }
        val x1 = event.getX(primaryIndex)
        val y1 = event.getY(primaryIndex)
        val x2 = event.getX(secondaryIndex)
        val y2 = event.getY(secondaryIndex)
        val midX = (x1 + x2) * 0.5f
        val midY = (y1 + y2) * 0.5f
        return TransformSnapshot(
            midX = midX,
            midY = midY,
            angle = calculateAngle(x1, y1, x2, y2),
            span = vectorMagnitude(x2 - x1, y2 - y1),
        )
    }

    private fun collectAvailablePointerIds(
        event: MotionEvent,
        excludeActionIndex: Int? = null,
    ): IntArray {
        val ids = IntArray(event.pointerCount)
        var count = 0
        for (index in 0 until event.pointerCount) {
            if (excludeActionIndex != null && index == excludeActionIndex) {
                continue
            }
            ids[count++] = event.getPointerId(index)
        }
        return ids.copyOf(count)
    }

    private fun isPointerUsable(
        pointerIndex: Int,
        excludeActionIndex: Int?,
    ): Boolean {
        return pointerIndex >= 0 && (excludeActionIndex == null || pointerIndex != excludeActionIndex)
    }

    private fun MotionEvent.toPointerEvent(): PointerEvent {
        return PointerEvent(
            type = toPointerEventType(actionMasked),
            uptimeMillis = eventTime,
            changes = List(pointerCount) { index ->
                val pressed = when (actionMasked) {
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> false
                    MotionEvent.ACTION_POINTER_UP -> index != actionIndex
                    else -> true
                }
                PointerChange(
                    id = getPointerId(index).toLong(),
                    x = getX(index),
                    y = getY(index),
                    pressed = pressed,
                )
            },
        )
    }

    private fun toPointerEventType(actionMasked: Int): PointerEventType {
        return when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> PointerEventType.Down
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> PointerEventType.Up
            MotionEvent.ACTION_CANCEL -> PointerEventType.Cancel
            else -> PointerEventType.Move
        }
    }

    private fun calculateAngle(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        return Math.toDegrees(
            atan2(
                (y2 - y1).toDouble(),
                (x2 - x1).toDouble(),
            ),
        ).toFloat()
    }

    private fun normalizeAngle(delta: Float): Float {
        var normalized = delta
        while (normalized > 180f) {
            normalized -= 360f
        }
        while (normalized < -180f) {
            normalized += 360f
        }
        return normalized
    }

    private fun vectorMagnitude(x: Float, y: Float): Float {
        return sqrt((x * x) + (y * y))
    }

    private fun CombinedClickableModifierElement.isParticipating(): Boolean {
        if (!enabled) {
            return false
        }
        return onClick != null || onDoubleClick != null || onLongClick != null
    }

    private inline fun debugLog(message: () -> String) {
        if (!Log.isLoggable(GESTURE_LOG_TAG, Log.DEBUG)) {
            return
        }
        Log.d(GESTURE_LOG_TAG, message())
    }

    private fun Float.format(digits: Int): String {
        return "%.${digits}f".format(this)
    }
}

private fun View.findNestedScrollHost(): DeclarativeNestedScrollHostLayout? {
    var current = parent
    while (current is View) {
        if (current is DeclarativeNestedScrollHostLayout) {
            return current
        }
        current = current.parent
    }
    return null
}

private fun LockedAxis.toScrollDelta(value: Float): ScrollDelta {
    return when (this) {
        LockedAxis.Horizontal -> ScrollDelta(x = value, y = 0f)
        LockedAxis.Vertical -> ScrollDelta(x = 0f, y = value)
    }
}

private fun LockedAxis?.toScrollVelocity(value: Float): ScrollVelocity {
    return when (this) {
        LockedAxis.Horizontal -> ScrollVelocity(x = value, y = 0f)
        LockedAxis.Vertical -> ScrollVelocity(x = 0f, y = value)
        null -> ScrollVelocity.Zero
    }
}

private fun ScrollDelta.valueFor(axis: LockedAxis): Float {
    return when (axis) {
        LockedAxis.Horizontal -> x
        LockedAxis.Vertical -> y
    }
}

private fun ScrollVelocity.valueFor(axis: LockedAxis?): Float {
    return when (axis) {
        LockedAxis.Horizontal -> x
        LockedAxis.Vertical -> y
        null -> 0f
    }
}
