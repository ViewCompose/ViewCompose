package com.viewcompose.gesture

import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.gesture.TransformDelta
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberUpdatedState
import kotlin.math.abs

/**
 * Forwards one-dimensional drag deltas to application state.
 *
 * Instances are normally created by [rememberDraggableState]. This object does not accumulate an
 * offset, clamp values, serialize mutations, or launch work; every delta is delivered synchronously
 * to the latest callback on the dispatching thread.
 */
class DraggableState internal constructor(
    private val onDeltaState: State<(Float) -> Unit>,
) {
    /**
     * Delivers one renderer-local drag delta without conversion or validation.
     *
     * @param delta signed incremental movement, normally in physical pixels
     */
    fun dispatchRawDelta(delta: Float) {
        onDeltaState.value(delta)
    }
}

/**
 * Associates a semantic value with one physical anchored-drag position.
 *
 * @property offsetPx finite renderer-local offset in physical pixels
 * @property value application value represented by [offsetPx]
 */
data class DraggableAnchor<T>(
    val offsetPx: Float,
    val value: T,
)

/**
 * Immutable, non-empty collection of anchored-drag positions sorted by pixel offset.
 *
 * Create instances with [of], [from], [draggableAnchors], or [draggableAnchorsOf]. Offsets are
 * finite, unique, and strictly increasing after construction. Semantic values are not required to
 * be unique; [offsetOf] returns the first matching value.
 *
 * @sample com.viewcompose.gesture.samples.anchoredDragState
 */
class DraggableAnchors<T> private constructor(
    private val sortedAnchors: List<DraggableAnchor<T>>,
) {
    /** Sorted immutable snapshot of all physical-pixel offsets. */
    val offsetsPx: List<Float> = sortedAnchors.map { it.offsetPx }

    /** Smallest physical-pixel offset in this collection. */
    val firstOffsetPx: Float = sortedAnchors.first().offsetPx

    /** Largest physical-pixel offset in this collection. */
    val lastOffsetPx: Float = sortedAnchors.last().offsetPx

    /** Number of anchors in this collection. */
    val size: Int = sortedAnchors.size

    /**
     * Returns the first offset associated with [value], or `null` when it is absent.
     *
     * Equality uses the semantic value's `equals` implementation.
     */
    fun offsetOf(value: T): Float? {
        return sortedAnchors.firstOrNull { it.value == value }?.offsetPx
    }

    /** Returns the value at exactly [offsetPx], or `null` when no exact floating-point match exists. */
    fun valueAt(offsetPx: Float): T? {
        return sortedAnchors.firstOrNull { it.offsetPx == offsetPx }?.value
    }

    /**
     * Returns the anchor nearest [offsetPx].
     *
     * Equal-distance ties choose the lower offset because anchors are traversed in sorted order.
     * Non-finite inputs are not rejected and resolve to the first anchor when no comparison wins.
     */
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

    internal fun hasSameAnchors(other: DraggableAnchors<T>): Boolean {
        return sortedAnchors == other.sortedAnchors
    }

    /** Creates validated anchor collections without the builder DSL. */
    companion object {
        /**
         * Creates anchors from `(offsetPx to value)` pairs.
         *
         * Input order is irrelevant; offsets are sorted during construction.
         *
         * @throws IllegalArgumentException if no pair is supplied, an offset is non-finite, or
         * duplicate offsets are present
         */
        fun <T> of(vararg anchors: Pair<Float, T>): DraggableAnchors<T> {
            return from(anchors.toList())
        }

        /**
         * Creates anchors from a list of `(offsetPx to value)` pairs.
         *
         * Input order is irrelevant; offsets are sorted during construction.
         *
         * @throws IllegalArgumentException if [anchors] is empty, an offset is non-finite, or
         * duplicate offsets are present
         */
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

/** Mutable receiver used by [draggableAnchors] to collect anchored-drag positions. */
class DraggableAnchorsBuilder<T> {
    private val anchors = mutableListOf<Pair<Float, T>>()

    /**
     * Adds one semantic [value] at [offsetPx].
     *
     * Validation is deferred until the builder finishes so anchors may be declared in any order.
     */
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

/**
 * Builds an immutable [DraggableAnchors] collection with a receiver DSL.
 *
 * @param builder block that declares one or more anchors in any order
 * @throws IllegalArgumentException if the resulting set is empty, contains a non-finite offset,
 * or contains duplicate offsets
 * @sample com.viewcompose.gesture.samples.anchoredDragState
 */
fun <T> draggableAnchors(
    builder: DraggableAnchorsBuilder<T>.() -> Unit,
): DraggableAnchors<T> {
    return DraggableAnchorsBuilder<T>()
        .apply(builder)
        .build()
}

/**
 * Creates immutable anchors from `(offsetPx to value)` pairs.
 *
 * @throws IllegalArgumentException if no pair is supplied, an offset is non-finite, or duplicate
 * offsets are present
 */
fun <T> draggableAnchorsOf(vararg anchors: Pair<Float, T>): DraggableAnchors<T> {
    return DraggableAnchors.of(*anchors)
}

/**
 * Owns the semantic value and visual offset of an [anchoredDraggable] modifier.
 *
 * This alpha implementation updates synchronously and does not animate between anchors. A renderer
 * sends raw deltas into the visual offset and commits the renderer-selected anchor when settling.
 * Movement is clamped to the installed anchor range. Recomposition with an equivalent anchor set
 * preserves an active drag, while cancellation restores the last committed value. Until the first
 * modifier supplies anchors, [currentOffsetPx] is `null`. The exposed [State] objects are stable
 * and invalidate readers when their values change.
 *
 * @sample com.viewcompose.gesture.samples.anchoredDragState
 */
class AnchoredDraggableState<T> internal constructor(
    initialValue: T,
) {
    private val currentState = mutableStateOf(initialValue)
    private val targetState = mutableStateOf(initialValue)
    private val currentOffsetState = mutableStateOf<Float?>(null)
    private val draggingState = mutableStateOf(false)
    private var anchors: DraggableAnchors<T>? = null

    /** Semantic value committed at the current anchor. */
    val currentValue: State<T>
        get() = currentState

    /**
     * Semantic settle target.
     *
     * In this release settling is synchronous, so this normally changes with [currentValue].
     */
    val targetValue: State<T>
        get() = targetState

    /** Current anchor-range-clamped visual offset in physical pixels, or `null` before installation. */
    val currentOffsetPx: State<Float?>
        get() = currentOffsetState

    /** Whether accepted pointer movement is currently changing [currentOffsetPx]. */
    val isDragging: State<Boolean>
        get() = draggingState

    /**
     * Commits [target] immediately and maps its offset through the current anchors.
     *
     * If [target] is absent from the current anchor set, the semantic value is still stored and the
     * offset becomes `null`; a later anchor update reconciles it to an exact or nearest anchor.
     */
    fun snapTo(target: T) {
        draggingState.value = false
        currentState.value = target
        targetState.value = target
        currentOffsetState.value = anchors?.offsetOf(target)
    }

    internal fun updateAnchors(newAnchors: DraggableAnchors<T>) {
        val previousAnchors = anchors
        if (previousAnchors?.hasSameAnchors(newAnchors) == true) {
            anchors = newAnchors
            return
        }
        anchors = newAnchors
        val currentMappedOffset = newAnchors.offsetOf(currentState.value)
        if (currentMappedOffset != null) {
            draggingState.value = false
            currentOffsetState.value = currentMappedOffset
            targetState.value = currentState.value
            return
        }
        // If the current value disappears from anchors, snap to the nearest offset to avoid dangling state.
        val nearest = newAnchors.nearest(
            offsetPx = currentOffsetState.value ?: newAnchors.firstOffsetPx,
        )
        currentState.value = nearest.value
        targetState.value = nearest.value
        currentOffsetState.value = nearest.offsetPx
        draggingState.value = false
    }

    internal fun dispatchRawDelta(delta: Float) {
        val activeAnchors = anchors ?: return
        if (!delta.isFinite() || delta == 0f) return
        val base = currentOffsetState.value
            ?: activeAnchors.offsetOf(currentState.value)
            ?: activeAnchors.firstOffsetPx
        draggingState.value = true
        currentOffsetState.value = (base + delta).coerceIn(
            activeAnchors.firstOffsetPx,
            activeAnchors.lastOffsetPx,
        )
    }

    internal fun settleToOffset(offsetPx: Float) {
        val activeAnchors = anchors ?: return
        val nearest = activeAnchors.nearest(offsetPx)
        draggingState.value = false
        currentState.value = nearest.value
        targetState.value = nearest.value
        currentOffsetState.value = nearest.offsetPx
    }

    internal fun cancelDrag() {
        draggingState.value = false
        targetState.value = currentState.value
        currentOffsetState.value = anchors?.offsetOf(currentState.value)
    }
}

/**
 * Forwards incremental pan, zoom, and rotation deltas to application state.
 *
 * Instances are normally created by [rememberTransformableState]. This object does not accumulate
 * a transform, enforce bounds, serialize mutations, or launch work. Dispatch is synchronous.
 */
class TransformableState internal constructor(
    private val onTransformState: State<(TransformDelta) -> Unit>,
) {
    /** Delivers one incremental renderer transform to the latest application callback. */
    fun dispatchTransform(delta: TransformDelta) {
        onTransformState.value(delta)
    }
}

/**
 * Remembers a stable [DraggableState] that invokes the latest [onDelta] lambda.
 *
 * Recomposition replaces the callback without replacing the state object. The callback executes
 * synchronously on the renderer's dispatch thread and receives incremental renderer-local movement.
 *
 * @sample com.viewcompose.gesture.samples.dragState
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
 * Remembers one [AnchoredDraggableState] in the current composition position.
 *
 * [initialValue] is read only when the state is first created; changing it during recomposition does
 * not reset existing state. Use [AnchoredDraggableState.snapTo] for an explicit reset.
 *
 * @sample com.viewcompose.gesture.samples.anchoredDragState
 */
fun <T> rememberAnchoredDraggableState(
    initialValue: T,
): AnchoredDraggableState<T> {
    return remember {
        AnchoredDraggableState(initialValue)
    }
}

/**
 * Remembers a stable [TransformableState] that invokes the latest transform callback.
 *
 * Each renderer [TransformDelta] is expanded into multiplicative zoom, incremental pan in physical
 * pixels, and incremental clockwise rotation in degrees. This function does not accumulate or
 * constrain those values.
 *
 * @sample com.viewcompose.gesture.samples.transformState
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
