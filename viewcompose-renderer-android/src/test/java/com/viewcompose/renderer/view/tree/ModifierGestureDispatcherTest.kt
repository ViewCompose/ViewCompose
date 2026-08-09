package com.viewcompose.renderer.view.tree

/*
 * 测试职责：覆盖 renderer view/tree 中的 Modifier Gesture Dispatcher 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Modifier Gesture Dispatcher behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.ui.gesture.GestureCancellationReason
import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.modifier.AnchoredDraggableModifierElement
import com.viewcompose.ui.modifier.DraggableModifierElement
import com.viewcompose.ui.modifier.TransformableModifierElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ModifierGestureDispatcherTest {
    @Test
    fun `two pointer transform cancels active drag and does not resume it`() {
        val view = View(RuntimeEnvironment.getApplication())
        val dragEvents = mutableListOf<String>()
        val transformEvents = mutableListOf<String>()
        ModifierGestureApplier.applyGestureState(
            view = view,
            resolved = ResolvedModifiers(
                draggable = DraggableModifierElement(
                    enabled = true,
                    orientation = GestureOrientation.Horizontal,
                    onDragStarted = { dragEvents += "started" },
                    onDragStopped = { dragEvents += "stopped" },
                    onDelta = { dragEvents += "delta" },
                    onDragCancelled = { reason -> dragEvents += "cancelled:$reason" },
                ),
                transformable = TransformableModifierElement(
                    enabled = true,
                    onTransform = { transformEvents += "delta" },
                    onTransformStarted = { transformEvents += "started" },
                    onTransformStopped = { transformEvents += "stopped" },
                    onTransformCancelled = { reason ->
                        transformEvents += "cancelled:$reason"
                    },
                ),
            ),
        )

        view.dispatch(singlePointerEvent(MotionEvent.ACTION_DOWN, 0L, 0f, 0f))
        view.dispatch(singlePointerEvent(MotionEvent.ACTION_MOVE, 10L, 32f, 0f))
        view.dispatch(
            twoPointerEvent(
                action = MotionEvent.ACTION_POINTER_DOWN or
                    (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                eventTime = 20L,
                firstX = 32f,
                firstY = 0f,
                secondX = 32f,
                secondY = 40f,
            ),
        )
        view.dispatch(
            twoPointerEvent(
                action = MotionEvent.ACTION_MOVE,
                eventTime = 30L,
                firstX = 32f,
                firstY = 0f,
                secondX = 82f,
                secondY = 40f,
            ),
        )
        view.dispatch(
            twoPointerEvent(
                action = MotionEvent.ACTION_POINTER_UP or
                    (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                eventTime = 40L,
                firstX = 32f,
                firstY = 0f,
                secondX = 82f,
                secondY = 40f,
            ),
        )
        view.dispatch(singlePointerEvent(MotionEvent.ACTION_MOVE, 50L, 100f, 0f))
        view.dispatch(singlePointerEvent(MotionEvent.ACTION_UP, 60L, 100f, 0f))

        assertEquals(
            listOf(
                "started",
                "delta",
                "cancelled:${GestureCancellationReason.TransformTookOver}",
            ),
            dragEvents,
        )
        assertEquals(listOf("started", "delta", "stopped"), transformEvents)
    }

    @Test
    fun `system cancellation does not fling or settle anchored drag`() {
        val view = View(RuntimeEnvironment.getApplication())
        val cancellationReasons = mutableListOf<GestureCancellationReason>()
        var deltaCount = 0
        var settleCount = 0
        ModifierGestureApplier.applyGestureState(
            view = view,
            resolved = ResolvedModifiers(
                anchoredDraggable = AnchoredDraggableModifierElement(
                    enabled = true,
                    orientation = GestureOrientation.Horizontal,
                    anchorOffsetsPx = listOf(0f, 100f),
                    currentOffsetPx = 0f,
                    onDelta = { deltaCount += 1 },
                    onSettleToOffset = { settleCount += 1 },
                    onDragCancelled = { cancellationReasons += it },
                ),
            ),
        )

        view.dispatch(singlePointerEvent(MotionEvent.ACTION_DOWN, 0L, 0f, 0f))
        view.dispatch(singlePointerEvent(MotionEvent.ACTION_MOVE, 10L, 32f, 0f))
        view.dispatch(singlePointerEvent(MotionEvent.ACTION_CANCEL, 20L, 32f, 0f))

        assertTrue(deltaCount > 0)
        assertEquals(listOf(GestureCancellationReason.SystemCancelled), cancellationReasons)
        assertEquals(0, settleCount)
    }

    @Test
    fun `recognized anchored drag settles without also performing click`() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 240, 80)
        }
        var clickCount = 0
        var settleCount = 0
        view.setOnClickListener { clickCount += 1 }
        ModifierGestureApplier.applyGestureState(
            view = view,
            resolved = ResolvedModifiers(
                anchoredDraggable = AnchoredDraggableModifierElement(
                    enabled = true,
                    orientation = GestureOrientation.Horizontal,
                    anchorOffsetsPx = listOf(0f, 100f),
                    currentOffsetPx = 0f,
                    onDelta = {},
                    onSettleToOffset = { settleCount += 1 },
                ),
            ),
        )

        view.dispatch(singlePointerEvent(MotionEvent.ACTION_DOWN, 0L, 40f, 40f))
        view.dispatch(singlePointerEvent(MotionEvent.ACTION_MOVE, 40L, 120f, 40f))
        view.dispatch(singlePointerEvent(MotionEvent.ACTION_UP, 80L, 140f, 40f))

        assertEquals(1, settleCount)
        assertEquals(0, clickCount)
    }

    @Test
    fun `anchored drag preserves click when the tap never crosses touch slop`() {
        val view = View(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 240, 80)
        }
        var clickCount = 0
        var settleCount = 0
        view.setOnClickListener { clickCount += 1 }
        ModifierGestureApplier.applyGestureState(
            view = view,
            resolved = ResolvedModifiers(
                anchoredDraggable = AnchoredDraggableModifierElement(
                    enabled = true,
                    orientation = GestureOrientation.Horizontal,
                    anchorOffsetsPx = listOf(0f, 100f),
                    currentOffsetPx = 0f,
                    onDelta = {},
                    onSettleToOffset = { settleCount += 1 },
                ),
            ),
        )

        view.dispatch(singlePointerEvent(MotionEvent.ACTION_DOWN, 0L, 40f, 40f))
        view.dispatch(singlePointerEvent(MotionEvent.ACTION_UP, 40L, 40f, 40f))

        assertEquals(1, clickCount)
        assertEquals(0, settleCount)
    }

    @Test
    fun `removing active transform reports modifier cancellation`() {
        val view = View(RuntimeEnvironment.getApplication())
        val cancellationReasons = mutableListOf<GestureCancellationReason>()
        ModifierGestureApplier.applyGestureState(
            view = view,
            resolved = ResolvedModifiers(
                transformable = TransformableModifierElement(
                    enabled = true,
                    onTransform = {},
                    onTransformCancelled = { cancellationReasons += it },
                ),
            ),
        )
        view.dispatch(singlePointerEvent(MotionEvent.ACTION_DOWN, 0L, 0f, 0f))
        view.dispatch(
            twoPointerEvent(
                action = MotionEvent.ACTION_POINTER_DOWN or
                    (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                eventTime = 10L,
                firstX = 0f,
                firstY = 0f,
                secondX = 0f,
                secondY = 40f,
            ),
        )
        view.dispatch(
            twoPointerEvent(
                action = MotionEvent.ACTION_MOVE,
                eventTime = 20L,
                firstX = 0f,
                firstY = 0f,
                secondX = 50f,
                secondY = 40f,
            ),
        )

        ModifierGestureApplier.applyGestureState(view, ResolvedModifiers())

        assertEquals(listOf(GestureCancellationReason.Disposed), cancellationReasons)
    }

    private fun View.dispatch(event: MotionEvent) {
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun singlePointerEvent(
        action: Int,
        eventTime: Long,
        x: Float,
        y: Float,
    ): MotionEvent {
        return MotionEvent.obtain(
            0L,
            eventTime,
            action,
            x,
            y,
            0,
        )
    }

    private fun twoPointerEvent(
        action: Int,
        eventTime: Long,
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float,
    ): MotionEvent {
        val pointerProperties = arrayOf(
            pointerProperties(id = 0),
            pointerProperties(id = 1),
        )
        val pointerCoords = arrayOf(
            pointerCoords(x = firstX, y = firstY),
            pointerCoords(x = secondX, y = secondY),
        )
        return MotionEvent.obtain(
            0L,
            eventTime,
            action,
            pointerProperties.size,
            pointerProperties,
            pointerCoords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
    }

    private fun pointerProperties(id: Int): MotionEvent.PointerProperties {
        return MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
    }

    private fun pointerCoords(
        x: Float,
        y: Float,
    ): MotionEvent.PointerCoords {
        return MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
    }
}
