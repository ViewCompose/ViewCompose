package com.viewcompose.renderer.view.tree

/*
 * 测试职责：覆盖 renderer view/tree 中的 Modifier Interaction Applier 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Modifier Interaction Applier behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.view.View
import com.viewcompose.renderer.R
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ModifierInteractionApplierTest {
    @Test
    fun `incremental modifier application still applies changed layout domain`() {
        val view = View(RuntimeEnvironment.getApplication())
        val firstNode = vnode(Modifier.padding(4))
        val secondNode = vnode(Modifier.padding(12))

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

    private fun vnode(modifier: Modifier): VNode {
        return VNode(
            type = NodeType.Box,
            spec = EmptyNodeSpec,
            modifier = modifier,
        )
    }
}
