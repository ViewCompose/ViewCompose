package com.viewcompose.renderer.view.tree

/*
 * 测试职责：覆盖 renderer view/tree 中的 Nested Scroll Rendering 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Nested Scroll Rendering behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.widget.FrameLayout
import com.viewcompose.renderer.view.container.DeclarativeNestedScrollHostLayout
import com.viewcompose.ui.gesture.NestedScrollConnection
import com.viewcompose.ui.gesture.NestedScrollDispatcher
import com.viewcompose.ui.gesture.NestedScrollSource
import com.viewcompose.ui.gesture.ScrollDelta
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NestedScrollModifierElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NestedScrollRenderingTest {
    @Test
    fun `render session mounts transparent host and detaches dispatcher on disposal`() {
        val container = FrameLayout(RuntimeEnvironment.getApplication())
        val dispatcher = NestedScrollDispatcher()
        val node = VNode(
            type = NodeType.Spacer,
            key = "nested-spacer",
            spec = EmptyNodeSpec,
            modifier = Modifier.then(
                NestedScrollModifierElement(
                    connection = object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: ScrollDelta,
                            source: NestedScrollSource,
                        ): ScrollDelta = ScrollDelta(
                            x = available.x / 2f,
                            y = available.y / 2f,
                        )
                    },
                    dispatcher = dispatcher,
                ),
            ),
        )

        val result = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(node),
        )

        assertTrue(result.mountedNodes.single().view is DeclarativeNestedScrollHostLayout)
        assertEquals(NodeType.Spacer, result.mountedNodes.single().children.single().vnode.type)
        assertEquals(
            ScrollDelta(4f, 6f),
            dispatcher.dispatchPreScroll(
                available = ScrollDelta(8f, 12f),
                source = NestedScrollSource.UserInput,
            ),
        )

        ViewTreeRenderer.disposeMounted(
            container = container,
            mountedNodes = result.mountedNodes,
        )

        assertEquals(
            ScrollDelta.Zero,
            dispatcher.dispatchPreScroll(ScrollDelta(8f, 12f)),
        )
    }
}
