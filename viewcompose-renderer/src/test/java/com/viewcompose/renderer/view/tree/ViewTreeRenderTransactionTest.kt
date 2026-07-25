package com.viewcompose.renderer.view.tree

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.MainAxisArrangement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.ColumnNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ViewTreeRenderTransactionTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `failed rebind restores previous vnode values and view order`() {
        val container = FrameLayout(context)
        val previousNodes = listOf(
            androidNode(key = "first", value = "old-first"),
            androidNode(key = "second", value = "old-second"),
        )
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = previousNodes,
        ).mountedNodes
        val previousViews = previous.map(MountedNode::view)
        val previousVNodes = previous.map(MountedNode::vnode)

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = previous,
                nodes = listOf(
                    androidNode(key = "first", value = "new-first"),
                    androidNode(
                        key = "second",
                        value = "new-second",
                        failUpdate = true,
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(2, container.childCount)
        previousViews.forEachIndexed { index, view ->
            assertSame(view, container.getChildAt(index))
        }
        assertEquals("old-first", previous[0].view.tag)
        assertEquals("old-second", previous[1].view.tag)
        assertSame(previousVNodes[0], previous[0].vnode)
        assertSame(previousVNodes[1], previous[1].vnode)
    }

    @Test
    fun `failed frame releases inserted nodes and restores previous children`() {
        val container = FrameLayout(context)
        var insertedReleases = 0
        val stableNode = androidNode(key = "stable", value = "stable")
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(stableNode),
        ).mountedNodes
        val stableView = previous.single().view
        val previousVNode = previous.single().vnode

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = previous,
                nodes = listOf(
                    androidNode(
                        key = "inserted",
                        value = "inserted",
                        onRelease = { insertedReleases += 1 },
                    ),
                    androidNode(
                        key = "stable",
                        value = "broken",
                        failUpdate = true,
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(1, insertedReleases)
        assertEquals(1, container.childCount)
        assertSame(stableView, container.getChildAt(0))
        assertSame(previousVNode, previous.single().vnode)
        assertEquals("stable", stableView.tag)
    }

    @Test
    fun `nested failure rolls back earlier child updates in the same frame`() {
        val container = FrameLayout(context)
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                columnNode(
                    androidNode(key = "first", value = "old-first"),
                    androidNode(key = "second", value = "old-second"),
                ),
            ),
        ).mountedNodes
        val previousRootVNode = previous.single().vnode
        val previousChildren = previous.single().children.toList()

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = previous,
                nodes = listOf(
                    columnNode(
                        androidNode(key = "first", value = "new-first"),
                        androidNode(
                            key = "second",
                            value = "new-second",
                            failUpdate = true,
                        ),
                    ),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertSame(previousRootVNode, previous.single().vnode)
        assertEquals(previousChildren, previous.single().children)
        assertEquals("old-first", previousChildren[0].view.tag)
        assertEquals("old-second", previousChildren[1].view.tag)
    }

    @Test
    fun `removed nodes release only after a successful frame`() {
        val container = FrameLayout(context)
        var releases = 0
        val previous = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(
                androidNode(
                    key = "removed",
                    value = "value",
                    onRelease = { releases += 1 },
                ),
            ),
        ).mountedNodes

        val result = ViewTreeRenderer.renderInto(
            container = container,
            previous = previous,
            nodes = emptyList(),
        )

        assertTrue(result.mountedNodes.isEmpty())
        assertEquals(0, container.childCount)
        assertEquals(1, releases)
    }

    private fun androidNode(
        key: Any,
        value: String,
        failUpdate: Boolean = false,
        onRelease: (() -> Unit)? = null,
    ): VNode {
        return VNode(
            type = NodeType.AndroidView,
            key = key,
            spec = AndroidViewNodeProps(
                factory = { rawContext ->
                    View(rawContext as Context)
                },
                update = { rawView ->
                    if (failUpdate) {
                        error("update failed")
                    }
                    (rawView as View).tag = value
                },
                onRelease = if (onRelease == null) {
                    null
                } else {
                    { onRelease() }
                },
            ),
        )
    }

    private fun columnNode(vararg children: VNode): VNode {
        return VNode(
            type = NodeType.Column,
            key = "column",
            spec = ColumnNodeProps(
                spacing = 0,
                arrangement = MainAxisArrangement.Start,
                horizontalAlignment = HorizontalAlignment.Start,
            ),
            children = children.toList(),
        )
    }
}
