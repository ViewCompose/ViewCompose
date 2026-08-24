package com.viewcompose.renderer.view.tree

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.viewcompose.renderer.view.container.DeclarativeAnimatedVisibilityHostLayout
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.TransformOrigin
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.node.spec.AndroidViewOperationException
import com.viewcompose.ui.node.spec.AnimatedVisibilityHostNodeProps
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AnimatedVisibilityRenderTransactionTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `later bind failure restores visibility visuals alignment and interaction ownership`() {
        val container = FrameLayout(context)
        val initialHost = visibilityNode(
            alpha = 1f,
            scale = 1f,
            translation = 0f,
            alignment = BoxAlignment.TopStart,
            active = true,
        )
        val initialFailureProbe = androidNode(value = "old", failUpdate = false)
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(initialHost, initialFailureProbe),
        )
        val mountedHost = initial.mountedNodes[0]
        val hostView = mountedHost.view as DeclarativeAnimatedVisibilityHostLayout

        val error = runCatching {
            ViewTreeRenderer.renderInto(
                container = container,
                previous = initial.mountedNodes,
                nodes = listOf(
                    visibilityNode(
                        alpha = 0.2f,
                        scale = 0.8f,
                        translation = -0.5f,
                        alignment = BoxAlignment.BottomEnd,
                        active = false,
                    ),
                    androidNode(value = "broken", failUpdate = true),
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is AndroidViewOperationException)
        assertSame(initialHost, mountedHost.vnode)
        assertEquals(1f, hostView.alpha, 0f)
        assertEquals(1f, hostView.scaleX, 0f)
        assertEquals(0f, hostView.translationXFraction, 0f)
        assertEquals(true, hostView.contentActive)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, hostView.importantForAccessibility)
        assertEquals("old", initial.mountedNodes[1].view.tag)
    }

    private fun visibilityNode(
        alpha: Float,
        scale: Float,
        translation: Float,
        alignment: BoxAlignment,
        active: Boolean,
    ): VNode {
        return VNode(
            type = NodeType.AnimatedVisibilityHost,
            key = "visibility-host",
            spec = AnimatedVisibilityHostNodeProps(
                alpha = alpha,
                widthScale = 1f,
                heightScale = 1f,
                scaleX = scale,
                scaleY = scale,
                translationXFraction = translation,
                translationYFraction = 0f,
                transformOrigin = TransformOrigin.Center,
                contentAlignment = alignment,
                clipToBounds = false,
                active = active,
            ),
            children = listOf(
                VNode(
                    type = NodeType.Spacer,
                    key = "content",
                    spec = EmptyNodeSpec,
                ),
            ),
        )
    }

    private fun androidNode(value: String, failUpdate: Boolean): VNode {
        return VNode(
            type = NodeType.AndroidView,
            key = "failure-probe",
            spec = AndroidViewNodeProps(
                factory = { rawContext, _ -> View(rawContext as Context) },
                update = { rawView, _ ->
                    if (failUpdate) error("injected update failure")
                    (rawView as View).tag = value
                },
            ),
        )
    }
}
