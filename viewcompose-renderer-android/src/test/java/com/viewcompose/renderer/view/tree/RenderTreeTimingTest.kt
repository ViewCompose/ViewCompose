package com.viewcompose.renderer.view.tree

import android.content.Context
import android.widget.FrameLayout
import com.viewcompose.text.TextDocument
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RenderTreeTimingTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `ordinary renderer allocates no timing identity`() {
        val node = textNode()

        ViewTreeRenderer.renderInto(
            container = FrameLayout(context),
            previous = emptyList(),
            nodes = listOf(node),
        )

        assertNull(UiNodeTooling.timingIdentityOf(node))
    }

    @Test
    fun `timed insertion separates reconciliation and direct binding`() {
        val events = mutableListOf<Event>()
        val node = UiNodeTooling.attachTimingIdentity(textNode(), identity = 41L)
        val collector = RenderTreeTimingCollector { subject, phase ->
            events += Event("begin", subject, phase)
            RenderTreeTimingSpan { events += Event("end", subject, phase) }
        }

        ViewTreeRenderer.renderIntoWithTiming(
            container = FrameLayout(context),
            previous = emptyList(),
            nodes = listOf(node),
            timingCollector = collector,
        )

        assertEquals(Event("begin", virtualRoot, RenderTreeTimingPhase.Reconciliation), events.first())
        assertEquals(Event("end", virtualRoot, RenderTreeTimingPhase.Reconciliation), events[1])
        assertTrue(events.any { event ->
            event.edge == "begin" &&
                event.subject.nodeIdentity == 41L &&
                event.phase == RenderTreeTimingPhase.Binding
        })
        val bindingBegin = events.indexOfFirst { event ->
            event.edge == "begin" && event.phase == RenderTreeTimingPhase.Binding
        }
        val bindingEnd = events.indexOfLast { event ->
            event.edge == "end" && event.phase == RenderTreeTimingPhase.Binding
        }
        assertTrue(bindingBegin > 1)
        assertTrue(bindingEnd > bindingBegin)
        assertEquals(1, events[bindingBegin].subject.depth)
    }

    @Test
    fun `exact subtree skip produces no binding interval`() {
        val container = FrameLayout(context)
        val node = textNode()
        val initial = ViewTreeRenderer.renderInto(
            container = container,
            previous = emptyList(),
            nodes = listOf(node),
        )
        val phases = mutableListOf<RenderTreeTimingPhase>()

        ViewTreeRenderer.renderIntoWithTiming(
            container = container,
            previous = initial.mountedNodes,
            nodes = listOf(node),
            timingCollector = RenderTreeTimingCollector { _, phase ->
                phases += phase
                RenderTreeTimingSpan { }
            },
        )

        assertTrue(RenderTreeTimingPhase.Reconciliation in phases)
        assertTrue(RenderTreeTimingPhase.Binding !in phases)
    }

    @Test
    fun `collector failures are isolated from renderer result`() {
        val beginResult = ViewTreeRenderer.renderIntoWithTiming(
            container = FrameLayout(context),
            previous = emptyList(),
            nodes = listOf(textNode()),
            timingCollector = RenderTreeTimingCollector { _, _ -> error("begin failure") },
        )
        assertEquals(1, beginResult.mountedNodes.size)

        val closeResult = ViewTreeRenderer.renderIntoWithTiming(
            container = FrameLayout(context),
            previous = emptyList(),
            nodes = listOf(textNode()),
            timingCollector = RenderTreeTimingCollector { _, _ ->
                RenderTreeTimingSpan { error("close failure") }
            },
        )
        assertEquals(1, closeResult.mountedNodes.size)
    }

    private fun textNode(): VNode = VNode(
        type = NodeType.Text,
        spec = TextNodeProps(
            document = TextDocument.plain("timing"),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Start,
            textColor = 0xFF000000.toInt(),
            textSizeSp = 14.sp,
        ),
    )

    private data class Event(
        val edge: String,
        val subject: RenderTreeTimingSubject,
        val phase: RenderTreeTimingPhase,
    )

    private companion object {
        val virtualRoot = RenderTreeTimingSubject(
            nodeIdentity = null,
            nodeType = null,
            depth = 0,
            synthetic = true,
        )
    }
}
