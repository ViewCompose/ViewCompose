package com.viewcompose.renderer.modifier

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer modifier 中的 Resolved Modifiers 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Resolved Modifiers behavior in renderer modifier and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.modifier.AnchoredDraggableModifierElement
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.backgroundDrawableRes
import com.viewcompose.ui.modifier.graphicsLayer
import com.viewcompose.ui.modifier.dropShadow
import com.viewcompose.ui.modifier.dropShadows
import com.viewcompose.ui.modifier.layoutId
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.ui.modifier.CombinedClickableModifierElement
import com.viewcompose.ui.modifier.ConstraintModifierElement
import com.viewcompose.ui.modifier.GesturePriorityModifierElement
import com.viewcompose.ui.modifier.SemanticsRole
import com.viewcompose.ui.modifier.semantics
import com.viewcompose.ui.gesture.GesturePriority
import com.viewcompose.ui.gesture.GestureOrientation
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ResolvedModifiersTest {
    @Test
    fun `resolve sums repeated zIndex modifiers`() {
        val resolved = Modifier
            .zIndex(2f)
            .zIndex(-0.5f)
            .resolve()

        assertEquals(1.5f, resolved.zIndex?.zIndex)
    }

    @Test
    fun `resolve captures background drawable resource modifier`() {
        val resolved = Modifier
            .backgroundColor(0xFF112233.toInt())
            .backgroundDrawableRes(42)
            .resolve()

        assertEquals(0xFF112233.toInt(), resolved.backgroundColor?.color)
        assertNotNull(resolved.backgroundDrawableRes)
        assertEquals(42, resolved.backgroundDrawableRes?.resId)
    }

    @Test
    fun `resolve captures graphics layer modifier payload`() {
        val resolved = Modifier
            .graphicsLayer(
                scaleX = 1.1f,
                translationX = 20f,
                alpha = 0.6f,
                transformOrigin = TransformOrigin.Center,
                clip = true,
            )
            .resolve()

        assertNotNull(resolved.graphicsLayer)
        assertEquals(1.1f, resolved.graphicsLayer?.scaleX)
        assertEquals(20f, resolved.graphicsLayer?.translationX)
        assertEquals(0.6f, resolved.graphicsLayer?.alpha)
        assertEquals(0.5f, resolved.graphicsLayer?.transformOrigin?.pivotFractionX)
        assertEquals(0.5f, resolved.graphicsLayer?.transformOrigin?.pivotFractionY)
        assertEquals(true, resolved.graphicsLayer?.clip)
    }

    @Test
    fun `resolve preserves every drop shadow group in declaration order`() {
        val first = UiShadow(
            color = 0x22000000,
            blurRadius = 4.dp,
        )
        val second = UiShadow(
            color = 0x33000000,
            blurRadius = 12.dp,
            offsetY = 6.dp,
        )
        val third = UiShadow(
            color = 0x44000000,
            blurRadius = 20.dp,
            spreadRadius = 2.dp,
        )

        val resolved = Modifier
            .dropShadows(listOf(first, second))
            .dropShadow(third)
            .resolve()

        assertEquals(2, resolved.dropShadows.size)
        assertEquals(listOf(first, second), resolved.dropShadows[0].shadows)
        assertEquals(listOf(third), resolved.dropShadows[1].shadows)
    }

    @Test
    fun `resolve captures gesture modifier payloads`() {
        val resolved = Modifier
            .then(
                CombinedClickableModifierElement(
                    enabled = true,
                    onClick = {},
                    onDoubleClick = null,
                    onLongClick = null,
                ),
            )
            .then(
                AnchoredDraggableModifierElement(
                    enabled = true,
                    orientation = GestureOrientation.Horizontal,
                    anchorOffsetsPx = listOf(0f, 80f, 160f),
                    currentOffsetPx = 80f,
                    onDelta = {},
                    onSettleToOffset = {},
                ),
            )
            .then(
                GesturePriorityModifierElement(
                    priority = GesturePriority.High,
                ),
            )
            .resolve()

        assertNotNull(resolved.combinedClickable)
        assertNotNull(resolved.anchoredDraggable)
        assertEquals(true, resolved.combinedClickable?.enabled)
        assertEquals(GesturePriority.High, resolved.gesturePriority?.priority)
    }

    @Test
    fun `resolve captures constraint parent-data modifiers`() {
        val resolved = Modifier
            .layoutId("card")
            .then(
                ConstraintModifierElement(
                    referenceId = "card",
                    constraint = ConstraintItemSpec(
                        start = ConstraintAnchorLink(
                            target = ConstraintAnchorTarget.parent(ConstraintAnchor.Start),
                            margin = 16.dp,
                        ),
                    ),
                ),
            )
            .resolve()

        assertEquals("card", resolved.layoutId?.layoutId)
        assertEquals("card", resolved.constraint?.referenceId)
        assertEquals(16.dp, resolved.constraint?.constraint?.start?.margin)
    }

    @Test
    fun `resolve merges structured semantics modifiers`() {
        val resolved = Modifier
            .semantics {
                contentDescription = "Account"
                role = SemanticsRole.Button
                selected = false
            }
            .semantics {
                stateDescription = "Signed in"
                selected = true
            }
            .resolve()

        assertEquals("Account", resolved.semantics.contentDescription)
        assertEquals("Signed in", resolved.semantics.stateDescription)
        assertEquals(SemanticsRole.Button, resolved.semantics.role)
        assertEquals(true, resolved.semantics.selected)
    }
}
