package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.renderer.R
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.clickable
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ModifierInteractionApplierTest {
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
