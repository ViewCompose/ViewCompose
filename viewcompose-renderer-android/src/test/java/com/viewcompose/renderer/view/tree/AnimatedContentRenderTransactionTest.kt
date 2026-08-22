package com.viewcompose.renderer.view.tree

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedContentHostLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedContentItemLayout
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.AndroidViewOperationException
import com.viewcompose.ui.node.spec.AnimatedContentHostNodeProps
import com.viewcompose.ui.node.spec.AnimatedContentItemNodeProps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AnimatedContentRenderTransactionTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `later bind failure restores content host visuals and interaction owner`() {
        val container = FrameLayout(context)
        val initialHost = contentHostNode(
            segmentId = 0L,
            sizeProgress = 1f,
            clip = false,
            itemAlpha = 1f,
            itemActive = true,
        )
        val initialFailureProbe = androidNode(value = "old", failUpdate = false)
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(initialHost, initialFailureProbe),
        )
        val mountedHost = initial.mountedNodes[0]
        val hostView = mountedHost.view as DeclarativeAnimatedContentHostLayout
        val itemView = mountedHost.children.single().view as DeclarativeAnimatedContentItemLayout

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = initial.mountedNodes,
                nodes = listOf(
                    contentHostNode(
                        segmentId = 1L,
                        sizeProgress = 0.25f,
                        clip = true,
                        itemAlpha = 0.2f,
                        itemActive = false,
                    ),
                    androidNode(value = "broken", failUpdate = true),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is AndroidViewOperationException)
        assertSame(initialHost, mountedHost.vnode)
        assertSame(initialHost.children.single(), mountedHost.children.single().vnode)
        assertEquals(0L, hostView.segmentId)
        assertEquals(1f, hostView.sizeProgress, 0f)
        assertEquals(false, hostView.clipToBounds)
        assertEquals(1f, itemView.alpha, 0f)
        assertEquals(true, itemView.contentActive)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, itemView.importantForAccessibility)
        assertEquals("old", initial.mountedNodes[1].view.tag)
    }

    @Test
    fun `host rejects a third full content subtree before publication`() {
        val container = FrameLayout(context)
        val invalidHost = VNode(
            type = NodeType.AnimatedContentHost,
            key = "content-host",
            spec = AnimatedContentHostNodeProps(
                segmentId = 1L,
                sizeProgress = 0f,
                sizeTransformEnabled = true,
                clipToBounds = true,
                contentAlignment = BoxAlignment.TopStart,
            ),
            children = List(3) { index -> contentItemNode("item-$index", 1f, active = index == 2) },
        )

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = emptyList(),
                nodes = listOf(invalidHost),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, container.childCount)
    }

    private fun contentHostNode(
        segmentId: Long,
        sizeProgress: Float,
        clip: Boolean,
        itemAlpha: Float,
        itemActive: Boolean,
    ): VNode {
        return VNode(
            type = NodeType.AnimatedContentHost,
            key = "content-host",
            spec = AnimatedContentHostNodeProps(
                segmentId = segmentId,
                sizeProgress = sizeProgress,
                sizeTransformEnabled = true,
                clipToBounds = clip,
                contentAlignment = BoxAlignment.Center,
            ),
            children = listOf(contentItemNode("item", itemAlpha, itemActive)),
        )
    }

    private fun contentItemNode(key: String, alpha: Float, active: Boolean): VNode {
        return VNode(
            type = NodeType.AnimatedContentItemHost,
            key = key,
            spec = AnimatedContentItemNodeProps(
                alpha = alpha,
                scaleX = 1f,
                scaleY = 1f,
                translationXFraction = 0f,
                translationYFraction = 0f,
                revealWidthFraction = 1f,
                revealHeightFraction = 1f,
                transformOrigin = TransformOrigin.Center,
                active = active,
            ),
        )
    }

    private fun androidNode(value: String, failUpdate: Boolean): VNode {
        return VNode(
            type = NodeType.AndroidView,
            key = "failure-probe",
            spec = AndroidViewNodeProps(
                factory = { rawContext -> View(rawContext as Context) },
                update = { rawView ->
                    if (failUpdate) error("injected update failure")
                    (rawView as View).tag = value
                },
            ),
        )
    }
}
