package com.viewcompose.gesture

import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.gesture.TransformDelta
import com.viewcompose.widget.core.remember
import com.viewcompose.widget.core.rememberUpdatedState
import kotlin.math.abs

/**
 * 业务侧持有的 drag 状态，renderer 通过它回传原始位移。
 * App-held drag state; renderers dispatch raw drag deltas through it.
 */
class DraggableState internal constructor(
    private val onDeltaState: State<(Float) -> Unit>,
) {
    fun dispatchRawDelta(delta: Float) {
        onDeltaState.value(delta)
    }
}

/**
 * anchored draggable 的一个离散 anchor。
 * One discrete anchor for anchored draggable.
 */
data class DraggableAnchor<T>(
    val offsetPx: Float,
    val value: T,
)

/**
 * 已按像素位置排序的 anchor 集合。
 * Anchor collection sorted by pixel offset.
 */
class DraggableAnchors<T> private constructor(
    private val sortedAnchors: List<DraggableAnchor<T>>,
) {
    val offsetsPx: List<Float> = sortedAnchors.map { it.offsetPx }
    val firstOffsetPx: Float = sortedAnchors.first().offsetPx
    val lastOffsetPx: Float = sortedAnchors.last().offsetPx
    val size: Int = sortedAnchors.size

    fun offsetOf(value: T): Float? {
        return sortedAnchors.firstOrNull { it.value == value }?.offsetPx
    }

    fun valueAt(offsetPx: Float): T? {
        return sortedAnchors.firstOrNull { it.offsetPx == offsetPx }?.value
    }

    fun nearest(offsetPx: Float): DraggableAnchor<T> {
        var nearest = sortedAnchors.first()
        var minDistance = abs(nearest.offsetPx - offsetPx)
        for (index in 1 until sortedAnchors.size) {
            val candidate = sortedAnchors[index]
            val distance = abs(candidate.offsetPx - offsetPx)
            if (distance < minDistance) {
                minDistance = distance
                nearest = candidate
            }
        }
        return nearest
    }

    companion object {
        fun <T> of(vararg anchors: Pair<Float, T>): DraggableAnchors<T> {
            return from(anchors.toList())
        }

        fun <T> from(anchors: List<Pair<Float, T>>): DraggableAnchors<T> {
            require(anchors.isNotEmpty()) { "DraggableAnchors must not be empty." }
            val sorted = anchors
                .map { (offsetPx, value) ->
                    require(offsetPx.isFinite()) {
                        "Anchor offset must be finite, but was $offsetPx."
                    }
                    DraggableAnchor(offsetPx = offsetPx, value = value)
                }
                .sortedBy { it.offsetPx }
            for (index in 1 until sorted.size) {
                require(sorted[index].offsetPx > sorted[index - 1].offsetPx) {
                    "Anchor offsets must be strictly increasing."
                }
            }
            return DraggableAnchors(sortedAnchors = sorted)
        }
    }
}

/**
 * 用 DSL 构建 [DraggableAnchors] 的 builder。
 * Builder used by the DSL to create [DraggableAnchors].
 */
class DraggableAnchorsBuilder<T> {
    private val anchors = mutableListOf<Pair<Float, T>>()

    fun anchor(
        offsetPx: Float,
        value: T,
    ) {
        anchors += offsetPx to value
    }

    internal fun build(): DraggableAnchors<T> {
        return DraggableAnchors.from(anchors)
    }
}

fun <T> draggableAnchors(
    builder: DraggableAnchorsBuilder<T>.() -> Unit,
): DraggableAnchors<T> {
    return DraggableAnchorsBuilder<T>()
        .apply(builder)
        .build()
}

fun <T> draggableAnchorsOf(vararg anchors: Pair<Float, T>): DraggableAnchors<T> {
    return DraggableAnchors.of(*anchors)
}

/**
 * anchored draggable 的当前值、目标值和当前 offset 状态。
 * Current value, target value, and current offset state for anchored draggable.
 */
class AnchoredDraggableState<T> internal constructor(
    initialValue: T,
) {
    private val currentState = mutableStateOf(initialValue)
    private val targetState = mutableStateOf(initialValue)
    private val currentOffsetState = mutableStateOf<Float?>(null)
    private var anchors: DraggableAnchors<T>? = null

    val currentValue: State<T>
        get() = currentState

    val targetValue: State<T>
        get() = targetState

    val currentOffsetPx: State<Float?>
        get() = currentOffsetState

    fun snapTo(target: T) {
        currentState.value = target
        targetState.value = target
        currentOffsetState.value = anchors?.offsetOf(target)
    }

    internal fun updateAnchors(newAnchors: DraggableAnchors<T>) {
        anchors = newAnchors
        val currentMappedOffset = newAnchors.offsetOf(currentState.value)
        if (currentMappedOffset != null) {
            currentOffsetState.value = currentMappedOffset
            targetState.value = currentState.value
            return
        }
        // 当前值不再存在于新 anchors 时，按现有 offset 贴近最近 anchor，避免状态悬空。
        // If the current value disappears from anchors, snap to the nearest offset to avoid dangling state.
        val nearest = newAnchors.nearest(
            offsetPx = currentOffsetState.value ?: newAnchors.firstOffsetPx,
        )
        currentState.value = nearest.value
        targetState.value = nearest.value
        currentOffsetState.value = nearest.offsetPx
    }

    internal fun dispatchRawDelta(delta: Float) {
        val activeAnchors = anchors ?: return
        val base = currentOffsetState.value
            ?: activeAnchors.offsetOf(currentState.value)
            ?: activeAnchors.firstOffsetPx
        currentOffsetState.value = base + delta
    }

    internal fun settleToOffset(offsetPx: Float) {
        val activeAnchors = anchors ?: return
        val nearest = activeAnchors.nearest(offsetPx)
        currentState.value = nearest.value
        targetState.value = nearest.value
        currentOffsetState.value = nearest.offsetPx
    }
}

/**
 * transformable 手势的业务状态入口。
 * App state entry point for transformable gestures.
 */
class TransformableState internal constructor(
    private val onTransformState: State<(TransformDelta) -> Unit>,
) {
    fun dispatchTransform(delta: TransformDelta) {
        onTransformState.value(delta)
    }
}

/**
 * 记忆 [DraggableState]，同时让回调始终指向最新 lambda。
 * Remembers [DraggableState] while keeping its callback pointed at the latest lambda.
 */
fun rememberDraggableState(
    onDelta: (Float) -> Unit,
): DraggableState {
    val latest = rememberUpdatedState(onDelta)
    return remember {
        DraggableState(onDeltaState = latest)
    }
}

/**
 * 记忆 anchored draggable 状态。
 * Remembers anchored draggable state.
 */
fun <T> rememberAnchoredDraggableState(
    initialValue: T,
): AnchoredDraggableState<T> {
    return remember {
        AnchoredDraggableState(initialValue)
    }
}

/**
 * 记忆 transformable 状态，并把 renderer 的 [TransformDelta] 展开为业务回调参数。
 * Remembers transformable state and expands renderer [TransformDelta] into app callback arguments.
 */
fun rememberTransformableState(
    onTransformation: (
        zoomChange: Float,
        panChangeX: Float,
        panChangeY: Float,
        rotationChange: Float,
    ) -> Unit,
): TransformableState {
    val latest = rememberUpdatedState(onTransformation)
    val callback = rememberUpdatedState<(TransformDelta) -> Unit> { delta ->
        latest.value(
            delta.zoom,
            delta.panX,
            delta.panY,
            delta.rotation,
        )
    }
    return remember {
        TransformableState(onTransformState = callback)
    }
}
