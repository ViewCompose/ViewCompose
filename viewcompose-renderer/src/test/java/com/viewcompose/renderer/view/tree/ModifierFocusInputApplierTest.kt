package com.viewcompose.renderer.view.tree

/*
 * 测试职责：覆盖 renderer view/tree 中的 Modifier Focus Input Applier 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Modifier Focus Input Applier behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.renderer.modifier.resolve
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.focusGroup
import com.viewcompose.ui.modifier.focusProperties
import com.viewcompose.ui.modifier.focusRequester
import com.viewcompose.ui.modifier.focusable
import com.viewcompose.ui.modifier.onKeyEvent
import com.viewcompose.ui.modifier.onPreviewKeyEvent
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ModifierFocusInputApplierTest {
    @Test
    fun `focus requester follows binding and is detached on disposal`() {
        val view = View(RuntimeEnvironment.getApplication())
        val requester = FocusRequester()
        val node = vnode(
            key = "field",
            modifier = Modifier
                .focusRequester(requester)
                .focusable(),
        )

        ModifierFocusInputApplier.apply(view, node, node.modifier.resolve())

        assertTrue(requester.requestFocus())
        assertTrue(view.isFocused)

        ModifierFocusInputApplier.dispose(view)

        assertTrue(runCatching { requester.requestFocus() }.isFailure)
    }

    @Test
    fun `key events dispatch preview root to target then bubble target to root`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val child = View(root.context)
        root.addView(child)
        val calls = mutableListOf<String>()
        val rootNode = vnode(
            modifier = Modifier
                .onPreviewKeyEvent {
                    calls += "root-preview"
                    false
                }.onKeyEvent {
                    calls += "root-bubble"
                    false
                },
        )
        val childNode = vnode(
            modifier = Modifier
                .focusable()
                .onPreviewKeyEvent {
                    calls += "child-preview"
                    false
                }.onKeyEvent {
                    calls += "child-bubble"
                    false
                },
        )
        ModifierFocusInputApplier.apply(root, rootNode, rootNode.modifier.resolve())
        ModifierFocusInputApplier.apply(child, childNode, childNode.modifier.resolve())
        child.requestFocus()

        val consumed = child.dispatchKeyEvent(
            AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_A),
        )

        assertFalse(consumed)
        assertEquals(
            listOf(
                "root-preview",
                "child-preview",
                "child-bubble",
                "root-bubble",
            ),
            calls,
        )
    }

    @Test
    fun `explicit tab destination requests declared target`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val first = View(root.context)
        val second = View(root.context)
        root.addView(first)
        root.addView(second)
        val secondRequester = FocusRequester()
        val firstNode = vnode(
            key = "first",
            modifier = Modifier
                .focusable()
                .focusProperties {
                    next = secondRequester
                },
        )
        val secondNode = vnode(
            key = "second",
            modifier = Modifier
                .focusRequester(secondRequester)
                .focusable(),
        )
        ModifierFocusInputApplier.apply(first, firstNode, firstNode.modifier.resolve())
        ModifierFocusInputApplier.apply(second, secondNode, secondNode.modifier.resolve())
        first.requestFocus()

        val consumed = first.dispatchKeyEvent(
            AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_TAB),
        )

        assertTrue(consumed)
        assertTrue(second.isFocused)
    }

    @Test
    fun `removing focus group restores native descendant policy`() {
        val group = FrameLayout(RuntimeEnvironment.getApplication())
        group.descendantFocusability = ViewGroupFocusPolicy
        val grouped = vnode(modifier = Modifier.focusGroup())
        ModifierFocusInputApplier.apply(group, grouped, grouped.modifier.resolve())

        assertEquals(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS, group.descendantFocusability)

        val plain = vnode()
        ModifierFocusInputApplier.apply(group, plain, plain.modifier.resolve())

        assertEquals(ViewGroupFocusPolicy, group.descendantFocusability)
    }

    private fun vnode(
        key: Any? = null,
        modifier: Modifier = Modifier,
    ): VNode {
        return VNode(
            type = NodeType.Box,
            key = key,
            spec = EmptyNodeSpec,
            modifier = modifier,
        )
    }

    private companion object {
        const val ViewGroupFocusPolicy = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
    }
}
