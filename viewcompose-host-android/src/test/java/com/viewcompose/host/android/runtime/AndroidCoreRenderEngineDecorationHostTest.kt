package com.viewcompose.host.android.runtime

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.viewcompose.renderer.decoration.AndroidViewDecorationBackend
import com.viewcompose.renderer.decoration.AndroidViewDecorationPresence
import com.viewcompose.renderer.decoration.AndroidViewDecorationRequest
import com.viewcompose.renderer.decoration.AndroidViewDecorationRuntime
import com.viewcompose.renderer.decoration.ViewDecorationHostLayout
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.dropShadow
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel
import com.viewcompose.text.TextDocument
import com.viewcompose.ui.node.TextAlign
import com.viewcompose.ui.node.TextOverflow
import com.viewcompose.ui.unit.sp
import com.viewcompose.ui.foundation.CoreObservedPropertyPatch
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidCoreRenderEngineDecorationHostTest {
    @Test
    fun `android engine rejects a render handle without a ViewGroup`() {
        val engine = AndroidCoreRenderEngine()
        val error = runCatching {
            engine.renderInto(
                container = object : PlatformRenderContainerHandle {
                    override val container: Any = Any()
                },
                previousMountedNodes = emptyList(),
                nodes = emptyList(),
                diagnosticLevel = RenderFrameDiagnosticLevel.None,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("ViewGroup-backed render container"))
    }

    @Test
    fun `plain render session mounts directly without an extra decoration host`() {
        val externalContainer = FrameLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()

        val frame = engine.renderInto(
            container = externalContainer.renderContainerHandle(),
            previousMountedNodes = emptyList(),
            nodes = listOf(spacerNode()),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )

        assertEquals(1, externalContainer.childCount)
        assertFalse(externalContainer.getChildAt(0) is ViewDecorationHostLayout)

        engine.disposeMounted(
            container = externalContainer.renderContainerHandle(),
            mountedNodes = frame.mountedNodes,
        )

        assertEquals(0, externalContainer.childCount)
    }

    @Test
    fun `existing decoration host is reused without nesting another host`() {
        val host = ViewDecorationHostLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()

        val frame = engine.renderInto(
            container = host.renderContainerHandle(),
            previousMountedNodes = emptyList(),
            nodes = listOf(spacerNode()),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )

        assertEquals(1, host.childCount)
        assertFalse(host.getChildAt(0) is ViewDecorationHostLayout)

        engine.disposeMounted(
            container = host.renderContainerHandle(),
            mountedNodes = frame.mountedNodes,
        )

        assertEquals(0, host.childCount)
    }

    @Test
    fun `top level zIndex adds and later removes a host by remounting safely`() {
        val externalContainer = FrameLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()
        val plainFrame = engine.renderInto(
            container = externalContainer.renderContainerHandle(),
            previousMountedNodes = emptyList(),
            nodes = listOf(spacerNode()),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )

        val orderedFrame = engine.renderInto(
            container = externalContainer.renderContainerHandle(),
            previousMountedNodes = plainFrame.mountedNodes,
            nodes = listOf(spacerNode(modifier = Modifier.zIndex(1f))),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )

        assertEquals(1, externalContainer.childCount)
        val host = externalContainer.getChildAt(0)
        assertTrue(host is ViewDecorationHostLayout)
        assertEquals(1, (host as ViewDecorationHostLayout).childCount)

        val plainAgainFrame = engine.renderInto(
            container = externalContainer.renderContainerHandle(),
            previousMountedNodes = orderedFrame.mountedNodes,
            nodes = listOf(spacerNode()),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )

        assertEquals(1, externalContainer.childCount)
        assertFalse(externalContainer.getChildAt(0) is ViewDecorationHostLayout)

        engine.disposeMounted(externalContainer.renderContainerHandle(), plainAgainFrame.mountedNodes)
        assertEquals(0, externalContainer.childCount)
    }

    @Test
    fun `zero top level zIndex stays on the direct render path`() {
        val externalContainer = FrameLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()

        val frame = engine.renderInto(
            container = externalContainer.renderContainerHandle(),
            previousMountedNodes = emptyList(),
            nodes = listOf(spacerNode(modifier = Modifier.zIndex(0f))),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )

        assertEquals(1, externalContainer.childCount)
        assertFalse(externalContainer.getChildAt(0) is ViewDecorationHostLayout)

        engine.disposeMounted(externalContainer.renderContainerHandle(), frame.mountedNodes)
    }

    @Test
    fun `optional backend adds a root host only for an actual top level decoration`() {
        AndroidViewDecorationRuntime.install(TestDecorationBackend)
        val externalContainer = FrameLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()

        val frame = engine.renderInto(
            container = externalContainer.renderContainerHandle(),
            previousMountedNodes = emptyList(),
            nodes = listOf(
                spacerNode(
                    modifier = Modifier.dropShadow(UiShadow(blurRadius = 4.dp)),
                ),
            ),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )

        assertEquals(1, externalContainer.childCount)
        assertTrue(externalContainer.getChildAt(0) is ViewDecorationHostLayout)
        engine.disposeMounted(externalContainer.renderContainerHandle(), frame.mountedNodes)
        assertEquals(0, externalContainer.childCount)
    }

    @Test
    fun `reusable decorated root attaches through a decoration host before cross owner rebind`() {
        val firstContainer = FrameLayout(RuntimeEnvironment.getApplication())
        val secondContainer = FrameLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()
        val decoratedNode = spacerNode(modifier = Modifier.zIndex(1f))
        val firstFrame = engine.renderInto(
            container = firstContainer.renderContainerHandle(),
            previousMountedNodes = emptyList(),
            nodes = listOf(decoratedNode),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )
        val firstHost = firstContainer.getChildAt(0) as ViewDecorationHostLayout
        val physicalView = firstHost.getChildAt(0)

        val reusable = requireNotNull(
            engine.detachMountedForReuse(
                container = firstContainer.renderContainerHandle(),
                mountedNodes = firstFrame.mountedNodes,
            ),
        )
        val adopted = engine.attachReusableMounted(
            container = secondContainer.renderContainerHandle(),
            tree = reusable,
        )

        assertEquals(0, firstContainer.childCount)
        val secondHost = secondContainer.getChildAt(0) as ViewDecorationHostLayout
        assertTrue(physicalView === secondHost.getChildAt(0))

        val rebound = engine.renderInto(
            container = secondContainer.renderContainerHandle(),
            previousMountedNodes = adopted,
            nodes = listOf(decoratedNode),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )

        assertTrue(physicalView === secondHost.getChildAt(0))
        engine.disposeMounted(secondContainer.renderContainerHandle(), rebound.mountedNodes)
        assertEquals(0, secondContainer.childCount)
    }

    @Test
    fun `failed reusable attach retains ownership for retry`() {
        val firstContainer = FrameLayout(RuntimeEnvironment.getApplication())
        val secondContainer = FrameLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()
        val firstFrame = engine.renderInto(
            container = firstContainer.renderContainerHandle(),
            previousMountedNodes = emptyList(),
            nodes = listOf(spacerNode()),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )
        val physicalView = firstContainer.getChildAt(0)
        val reusable = requireNotNull(
            engine.detachMountedForReuse(
                container = firstContainer.renderContainerHandle(),
                mountedNodes = firstFrame.mountedNodes,
            ),
        )

        val failure = runCatching {
            engine.attachReusableMounted(
                container = RejectingFrameLayout(RuntimeEnvironment.getApplication()).renderContainerHandle(),
                tree = reusable,
            )
        }.exceptionOrNull()
        val adopted = engine.attachReusableMounted(
            container = secondContainer.renderContainerHandle(),
            tree = reusable,
        )

        assertTrue(failure is IllegalStateException)
        assertTrue(adopted.isNotEmpty())
        assertTrue(physicalView === secondContainer.getChildAt(0))
        engine.disposeMounted(secondContainer.renderContainerHandle(), adopted)
    }

    @Test
    fun `android engine patches exact observed property target without replacing roots`() {
        val container = FrameLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()
        val previousNode = textNode("before").copy(observedPropertyId = 7L)
        val first = engine.renderInto(
            container = container.renderContainerHandle(),
            previousMountedNodes = emptyList(),
            nodes = listOf(previousNode),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )
        val target = requireNotNull(first.observedPropertyTargets[7L])
        val nextNode = target.node.copy(spec = textSpec("after"))

        engine.patchObservedProperties(
            container = container.renderContainerHandle(),
            mountedNodes = first.mountedNodes,
            patches = listOf(
                CoreObservedPropertyPatch(7L, target, target.node, nextNode),
            ),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
        )

        assertEquals("after", (container.getChildAt(0) as TextView).text.toString())
        assertTrue(target.node === previousNode)
        engine.disposeMounted(container.renderContainerHandle(), first.mountedNodes)
    }

    private fun spacerNode(modifier: Modifier = Modifier): VNode {
        return VNode(
            type = NodeType.Spacer,
            spec = EmptyNodeSpec,
            modifier = modifier,
        )
    }

    private fun textNode(text: String): VNode {
        return VNode(type = NodeType.Text, spec = textSpec(text))
    }

    private fun textSpec(text: String): TextNodeProps {
        return TextNodeProps(
            document = TextDocument.plain(text),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Start,
            textColor = 0xFF000000.toInt(),
            textSizeSp = 16.sp,
        )
    }

    private fun ViewGroup.renderContainerHandle(): RenderContainerHandle {
        return object : PlatformRenderContainerHandle {
            override val container: Any = this@renderContainerHandle
        }
    }

    private object TestDecorationBackend : AndroidViewDecorationBackend {
        override fun update(
            view: View,
            request: AndroidViewDecorationRequest,
        ): AndroidViewDecorationPresence {
            return AndroidViewDecorationPresence(
                behindChild = request.dropShadows.isNotEmpty(),
                overChild = request.innerShadows.isNotEmpty(),
            )
        }

        override fun clear(view: View) = Unit

        override fun drawBehindChild(canvas: Canvas, parent: ViewGroup, child: View) = Unit

        override fun drawOverChild(canvas: Canvas, parent: ViewGroup, child: View) = Unit
    }

    private class RejectingFrameLayout(context: Context) : FrameLayout(context) {
        override fun onViewAdded(child: View) {
            super.onViewAdded(child)
            error("Rejected reusable child")
        }
    }
}
