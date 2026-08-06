package com.viewcompose.host.android.runtime

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
                collectDiagnostics = false,
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
            collectDiagnostics = false,
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
            collectDiagnostics = false,
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
            collectDiagnostics = false,
        )

        val orderedFrame = engine.renderInto(
            container = externalContainer.renderContainerHandle(),
            previousMountedNodes = plainFrame.mountedNodes,
            nodes = listOf(spacerNode(modifier = Modifier.zIndex(1f))),
            collectDiagnostics = false,
        )

        assertEquals(1, externalContainer.childCount)
        val host = externalContainer.getChildAt(0)
        assertTrue(host is ViewDecorationHostLayout)
        assertEquals(1, (host as ViewDecorationHostLayout).childCount)

        val plainAgainFrame = engine.renderInto(
            container = externalContainer.renderContainerHandle(),
            previousMountedNodes = orderedFrame.mountedNodes,
            nodes = listOf(spacerNode()),
            collectDiagnostics = false,
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
            collectDiagnostics = false,
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
            collectDiagnostics = false,
        )

        assertEquals(1, externalContainer.childCount)
        assertTrue(externalContainer.getChildAt(0) is ViewDecorationHostLayout)
        engine.disposeMounted(externalContainer.renderContainerHandle(), frame.mountedNodes)
        assertEquals(0, externalContainer.childCount)
    }

    private fun spacerNode(modifier: Modifier = Modifier): VNode {
        return VNode(
            type = NodeType.Spacer,
            spec = EmptyNodeSpec,
            modifier = modifier,
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
}
