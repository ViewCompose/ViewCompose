package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.sp

import com.viewcompose.ui.unit.dp

/*
 * 测试职责：覆盖 renderer view/tree 中的 Modifier Interaction Applier 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Modifier Interaction Applier behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.view.View
import com.viewcompose.renderer.R
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.shadow.android.DecorationChildDrawingOrder
import com.viewcompose.shadow.android.ShadowDecorationLayer
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.dropShadow
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.innerShadow
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ModifierInteractionApplierTest {
    @Test
    fun `zIndex uses decoration order without changing platform shadow height`() {
        val view = View(RuntimeEnvironment.getApplication())
        val node = vnode(
            Modifier
                .elevation(6.dp)
                .zIndex(4f),
        )

        ViewModifierApplier.applyModifier(view, node, defaultRippleColor = 0)

        assertEquals(4f, DecorationChildDrawingOrder.zIndex(view))
        assertEquals(0f, view.translationZ)
        assertEquals(6f, view.elevation)

        ViewModifierApplier.applyModifier(view, vnode(Modifier), defaultRippleColor = 0)

        assertEquals(0f, DecorationChildDrawingOrder.zIndex(view))
        assertEquals(0f, view.translationZ)
        assertEquals(0f, view.elevation)
    }

    @Test
    fun `incremental modifier application still applies changed layout domain`() {
        val view = View(RuntimeEnvironment.getApplication())
        val firstNode = vnode(Modifier.padding(4.dp))
        val secondNode = vnode(Modifier.padding(12.dp))

        ViewModifierApplier.applyModifier(view, firstNode, defaultRippleColor = 0)
        ViewModifierApplier.applyModifier(view, secondNode, defaultRippleColor = 0)

        assertEquals(12, view.paddingLeft)
        assertEquals(12, view.paddingTop)
        assertEquals(12, view.paddingRight)
        assertEquals(12, view.paddingBottom)
    }

    @Test
    fun `callback-only modifier update does not rebuild the native surface`() {
        val view = View(RuntimeEnvironment.getApplication())
        var latestCalls = 0
        val firstNode = vnode(
            Modifier
                .backgroundColor(0xFF112233.toInt())
                .clickable {},
        )
        val secondNode = vnode(
            Modifier
                .backgroundColor(0xFF112233.toInt())
                .clickable { latestCalls += 1 },
        )

        ViewModifierApplier.applyModifier(view, firstNode, defaultRippleColor = 0)
        val firstBackground = view.background
        ViewModifierApplier.applyModifier(view, secondNode, defaultRippleColor = 0)

        assertSame(firstBackground, view.background)
        view.performClick()
        assertEquals(1, latestCalls)

        val changedSurfaceNode = vnode(
            Modifier
                .backgroundColor(0xFF445566.toInt())
                .clickable { latestCalls += 1 },
        )
        ViewModifierApplier.applyModifier(view, changedSurfaceNode, defaultRippleColor = 0)

        assertNotSame(firstBackground, view.background)
    }

    @Test
    fun `clickable modifier reuses listener while refreshing callback`() {
        val view = View(RuntimeEnvironment.getApplication())
        var firstCalls = 0
        var secondCalls = 0
        val firstNode = vnode(Modifier.clickable { firstCalls += 1 })
        val secondNode = vnode(Modifier.clickable { secondCalls += 1 })

        ModifierInteractionApplier.applyClickAndFocusState(
            view = view,
            node = firstNode,
            resolved = firstNode.modifier.resolve(),
        )
        val firstBinding = view.getTag(R.id.viewcompose_modifier_click_listener)

        ModifierInteractionApplier.applyClickAndFocusState(
            view = view,
            node = secondNode,
            resolved = secondNode.modifier.resolve(),
        )
        val secondBinding = view.getTag(R.id.viewcompose_modifier_click_listener)
        view.performClick()

        assertSame(firstBinding, secondBinding)
        assertEquals(0, firstCalls)
        assertEquals(1, secondCalls)
    }

    @Test
    fun `drop shadow follows resolved node shape and is removed incrementally`() {
        val view = View(RuntimeEnvironment.getApplication())
        val shadowNode = vnode(
            Modifier
                .cornerRadius(6.dp)
                .dropShadow(
                    UiShadow(
                        color = 0x33000000,
                        blurRadius = 8.dp,
                        offsetY = 2.dp,
                    ),
                ),
        )

        ViewModifierApplier.applyModifier(view, shadowNode, defaultRippleColor = 0)

        val installed = requireNotNull(ShadowDecorationLayer.specOrNull(view))
        assertEquals(1, installed.layerCount)
        assertEquals(6.dp, installed.groups.single().shape.uniformAbsoluteSizeOrNull)

        ViewModifierApplier.applyModifier(view, vnode(Modifier), defaultRippleColor = 0)

        assertNull(ShadowDecorationLayer.specOrNull(view))
    }

    @Test
    fun `inner shadow follows shape without rebuilding ripple or click binding`() {
        val view = View(RuntimeEnvironment.getApplication())
        var clicks = 0
        val onClick = { clicks += 1 }
        val baseNode = vnode(
            Modifier
                .backgroundColor(0xFF112233.toInt())
                .cornerRadius(6.dp)
                .clickable(onClick),
        )
        val shadowNode = vnode(
            Modifier
                .backgroundColor(0xFF112233.toInt())
                .cornerRadius(6.dp)
                .innerShadow(
                    UiShadow(
                        color = 0x33000000,
                        blurRadius = 8.dp,
                        offsetY = 2.dp,
                    ),
                )
                .clickable(onClick),
        )

        ViewModifierApplier.applyModifier(view, baseNode, defaultRippleColor = 0x22000000)
        val initialBackground = view.background
        val initialClickBinding = view.getTag(R.id.viewcompose_modifier_click_listener)
        ViewModifierApplier.applyModifier(view, shadowNode, defaultRippleColor = 0x22000000)

        val installed = requireNotNull(ShadowDecorationLayer.innerSpecOrNull(view))
        assertEquals(1, installed.layerCount)
        assertEquals(6.dp, installed.groups.single().shape.uniformAbsoluteSizeOrNull)
        assertSame(initialBackground, view.background)
        assertSame(initialClickBinding, view.getTag(R.id.viewcompose_modifier_click_listener))
        view.performClick()
        assertEquals(1, clicks)

        ViewModifierApplier.applyModifier(view, vnode(Modifier), defaultRippleColor = 0)

        assertNull(ShadowDecorationLayer.innerSpecOrNull(view))
    }

    private fun vnode(modifier: Modifier): VNode {
        return VNode(
            type = NodeType.Box,
            spec = EmptyNodeSpec,
            modifier = modifier,
        )
    }
}
