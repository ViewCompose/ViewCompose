package com.viewcompose.gesture

/*
 * 测试职责：覆盖 gesture DSL 中的 Gesture Modifiers 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Gesture Modifiers behavior in gesture DSL and guards the contract against regressions.
 */

import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.gesture.GesturePriority
import com.viewcompose.ui.gesture.PointerEvent
import com.viewcompose.ui.gesture.PointerEventResult
import com.viewcompose.ui.gesture.PointerEventType
import com.viewcompose.ui.gesture.TransformDelta
import com.viewcompose.ui.modifier.AnchoredDraggableModifierElement
import com.viewcompose.ui.modifier.CombinedClickableModifierElement
import com.viewcompose.ui.modifier.DraggableModifierElement
import com.viewcompose.ui.modifier.GesturePriorityModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.PointerInputModifierElement
import com.viewcompose.ui.modifier.TransformableModifierElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GestureModifiersTest {
    @Test
    fun `pointerInput appends pointer element`() {
        val modifier = Modifier.pointerInput(key = "p") {
            PointerEventResult.Consumed
        }
        val element = modifier.elements.single() as PointerInputModifierElement
        val result = element.onEvent(
            PointerEvent(
                type = PointerEventType.Down,
                uptimeMillis = 1L,
                changes = emptyList(),
            ),
        )
        assertEquals("p", element.key)
        assertEquals(PointerEventResult.Consumed, result)
    }

    @Test
    fun `combinedClickable appends expected element`() {
        val modifier = Modifier.combinedClickable(
            enabled = true,
            onClick = {},
            onDoubleClick = {},
            onLongClick = {},
        )
        val element = modifier.elements.single() as CombinedClickableModifierElement
        assertTrue(element.enabled)
        assertTrue(element.onClick != null)
        assertTrue(element.onDoubleClick != null)
        assertTrue(element.onLongClick != null)
    }

    @Test
    fun `combinedClickable without callbacks is no-op`() {
        val base = Modifier
        val modifier = base.combinedClickable(
            enabled = true,
            onClick = null,
            onDoubleClick = null,
            onLongClick = null,
        )
        assertSame(base, modifier)
    }

    @Test
    fun `draggable appends draggable element and forwards delta to state`() {
        var total = 0f
        val state = DraggableState(
            onDeltaState = com.viewcompose.runtime.mutableStateOf<(Float) -> Unit>(
                { delta -> total += delta },
            ),
        )
        val modifier = Modifier.draggable(
            state = state,
            orientation = GestureOrientation.Horizontal,
        )
        val element = modifier.elements.single() as DraggableModifierElement
        element.onDelta(5f)
        assertEquals(5f, total)
    }

    @Test
    fun `anchoredDraggable updates state using nearest anchor on settle`() {
        val state = AnchoredDraggableState(initialValue = "A")
        val modifier = Modifier.anchoredDraggable(
            state = state,
            anchors = draggableAnchorsOf(0f to "A", 100f to "B"),
            orientation = GestureOrientation.Horizontal,
        )
        val element = modifier.elements.single() as AnchoredDraggableModifierElement
        element.onSettleToOffset(80f)
        assertEquals("B", state.currentValue.value)
        element.onSettleToOffset(10f)
        assertEquals("A", state.currentValue.value)
    }

    @Test
    fun `anchoredDraggable rejects free orientation`() {
        try {
            Modifier.anchoredDraggable(
                state = AnchoredDraggableState(initialValue = "A"),
                anchors = draggableAnchorsOf(0f to "A", 100f to "B"),
                orientation = GestureOrientation.Free,
            )
            fail("Expected IllegalArgumentException for free orientation")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message?.contains("Horizontal or Vertical") == true)
        }
    }

    @Test
    fun `transformable appends transform element and forwards delta`() {
        var last = TransformDelta()
        val state = TransformableState(
            onTransformState = com.viewcompose.runtime.mutableStateOf<(TransformDelta) -> Unit>(
                { delta -> last = delta },
            ),
        )
        val modifier = Modifier.transformable(state = state)
        val element = modifier.elements.single() as TransformableModifierElement
        element.onTransform(
            TransformDelta(
                panX = 2f,
                panY = 3f,
                zoom = 1.2f,
                rotation = 10f,
            ),
        )
        assertEquals(2f, last.panX)
        assertEquals(3f, last.panY)
        assertEquals(1.2f, last.zoom)
        assertEquals(10f, last.rotation)
    }

    @Test
    fun `gesturePriority appends expected priority element`() {
        val modifier = Modifier.gesturePriority(GesturePriority.High)
        val element = modifier.elements.single() as GesturePriorityModifierElement
        assertEquals(GesturePriority.High, element.priority)
    }
}
