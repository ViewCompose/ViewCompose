package com.viewcompose.gesture

import com.viewcompose.runtime.State
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberUpdatedState
import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.modifier.Modifier

/**
 * Owns follow-finger progress for a caller-controlled two-state control.
 *
 * Instances are created by [rememberToggleDragState]. [progress] is logical rather than physical:
 * zero always represents unchecked and one represents checked, even when the checked anchor is
 * negative for right-to-left layout. Pointer movement updates only visual progress. A normal settle
 * requests a replacement value through the latest callback, while cancellation restores the last
 * caller-supplied [checked][rememberToggleDragState] value.
 *
 * The state does not draw a track or thumb, install click behavior, or animate the settled value.
 * Components should keep a normal click action beside [toggleDraggable] and may use [isDragging]
 * to switch between follow-finger rendering and their design-system motion specification.
 *
 * @sample com.viewcompose.gesture.samples.toggleDragState
 */
class ToggleDragState internal constructor(
    checked: Boolean,
    private val onCheckedChangeState: State<(Boolean) -> Unit>,
) {
    private val anchoredState = AnchoredDraggableState(initialValue = checked)
    private var externalChecked: Boolean = checked
    private var checkedAnchorOffsetPx: Float = 1f
    private var anchors: DraggableAnchors<Boolean> = toggleAnchors(checkedAnchorOffsetPx)

    /** Logical unchecked-to-checked visual progress clamped to the inclusive `0f..1f` range. */
    val progress: State<Float> = object : State<Float> {
        override val value: Float
            get() {
                val offset = anchoredState.currentOffsetPx.value
                    ?: if (externalChecked) checkedAnchorOffsetPx else 0f
                if (offset == 0f) return 0f
                return (offset / checkedAnchorOffsetPx).coerceIn(0f, 1f)
            }
    }

    /** Whether accepted pointer movement is currently controlling [progress]. */
    val isDragging: State<Boolean>
        get() = anchoredState.isDragging

    internal fun update(
        checked: Boolean,
        checkedAnchorOffsetPx: Float,
    ) {
        require(checkedAnchorOffsetPx.isFinite() && checkedAnchorOffsetPx != 0f) {
            "Toggle checked anchor offset must be finite and non-zero."
        }
        externalChecked = checked
        if (this.checkedAnchorOffsetPx != checkedAnchorOffsetPx) {
            this.checkedAnchorOffsetPx = checkedAnchorOffsetPx
            anchors = toggleAnchors(checkedAnchorOffsetPx)
        }
        anchoredState.updateAnchors(anchors)
        if (!anchoredState.isDragging.value && anchoredState.currentValue.value != checked) {
            anchoredState.snapTo(checked)
        }
    }

    internal fun onValueSettled(value: Boolean) {
        if (value != externalChecked) {
            onCheckedChangeState.value(value)
        }
    }

    internal fun modifier(enabled: Boolean): Modifier {
        return Modifier.anchoredDraggable(
            state = anchoredState,
            anchors = anchors,
            orientation = GestureOrientation.Horizontal,
            enabled = enabled,
            onValueSettled = ::onValueSettled,
        )
    }
}

/**
 * Remembers controlled two-state drag progress with an explicit physical checked anchor.
 *
 * [checkedAnchorOffsetPx] is measured from the unchecked anchor at zero. Pass a positive value
 * when checked is physically toward the right and a negative value when it is toward the left.
 * Recomposition with an equivalent value preserves an active drag; a changed caller-owned
 * [checked] value synchronizes the idle state. The latest [onCheckedChange] callback is invoked
 * synchronously on the renderer input thread only when normal settling requests a different value.
 *
 * This is a Q3 stateful interaction API. The caller owns [checked], stable composition position,
 * density conversion, layout-direction resolution, drawing, settled animation, and persistence.
 *
 * @sample com.viewcompose.gesture.samples.toggleDragState
 * @param checked current caller-owned value
 * @param checkedAnchorOffsetPx signed finite non-zero physical offset for the checked position
 * @param onCheckedChange callback receiving a requested replacement value after normal settling
 * @return stable drag state for the current composition position
 * @throws IllegalArgumentException when [checkedAnchorOffsetPx] is zero or non-finite
 */
fun rememberToggleDragState(
    checked: Boolean,
    checkedAnchorOffsetPx: Float,
    onCheckedChange: (Boolean) -> Unit,
): ToggleDragState {
    val latestCallback = rememberUpdatedState(onCheckedChange)
    val state = remember {
        ToggleDragState(
            checked = checked,
            onCheckedChangeState = latestCallback,
        )
    }
    state.update(
        checked = checked,
        checkedAnchorOffsetPx = checkedAnchorOffsetPx,
    )
    return state
}

/**
 * Adds horizontal anchored dragging for a state created by [rememberToggleDragState].
 *
 * The renderer owns touch slop, velocity-based target selection, nested-scroll arbitration, and
 * cancellation. Taps that do not become drags remain available to a click modifier on the same
 * node. Accepted drags consume completion so the click callback is not also invoked.
 *
 * @sample com.viewcompose.gesture.samples.toggleDragState
 * @receiver modifier chain for the complete interactive control target
 * @param state remembered controlled toggle drag state
 * @param enabled whether drag recognition participates in pointer input
 * @return the unchanged receiver when disabled, otherwise a chain containing anchored drag input
 */
fun Modifier.toggleDraggable(
    state: ToggleDragState,
    enabled: Boolean = true,
): Modifier {
    return if (enabled) then(state.modifier(enabled = true)) else this
}

private fun toggleAnchors(checkedAnchorOffsetPx: Float): DraggableAnchors<Boolean> {
    return draggableAnchorsOf(
        0f to false,
        checkedAnchorOffsetPx to true,
    )
}
