package com.viewcompose.host.android.runtime

import android.widget.FrameLayout
import com.viewcompose.shadow.android.ShadowDecorationHostLayout
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
class AndroidCoreRenderEngineDecorationHostTest {
    @Test
    fun `render session installs one root decoration host and removes it on dispose`() {
        val externalContainer = FrameLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()

        val frame = engine.renderInto(
            container = externalContainer,
            previousMountedNodes = emptyList(),
            nodes = listOf(spacerNode()),
            collectDiagnostics = false,
        )

        assertEquals(1, externalContainer.childCount)
        val decorationHost = externalContainer.getChildAt(0)
        assertTrue(decorationHost is ShadowDecorationHostLayout)
        assertEquals(1, (decorationHost as ShadowDecorationHostLayout).childCount)

        engine.disposeMounted(
            container = externalContainer,
            mountedNodes = frame.mountedNodes,
        )

        assertEquals(0, externalContainer.childCount)
    }

    @Test
    fun `existing decoration host is reused without nesting another host`() {
        val host = ShadowDecorationHostLayout(RuntimeEnvironment.getApplication())
        val engine = AndroidCoreRenderEngine()

        val frame = engine.renderInto(
            container = host,
            previousMountedNodes = emptyList(),
            nodes = listOf(spacerNode()),
            collectDiagnostics = false,
        )

        assertEquals(1, host.childCount)
        assertFalse(host.getChildAt(0) is ShadowDecorationHostLayout)

        engine.disposeMounted(
            container = host,
            mountedNodes = frame.mountedNodes,
        )

        assertEquals(0, host.childCount)
    }

    private fun spacerNode(): VNode {
        return VNode(
            type = NodeType.Spacer,
            spec = EmptyNodeSpec,
        )
    }
}
