package com.viewcompose.host.android.runtime

import android.content.Context
import android.widget.FrameLayout
import com.viewcompose.ui.foundation.CoreRenderTimingPhase
import com.viewcompose.ui.foundation.CoreRenderTimingSpan
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.PlatformRenderContainerHandle
import com.viewcompose.ui.node.spec.EmptyNodeSpec
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.tooling.UiNodeTooling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidCoreRenderEngineTimingTest {
    @Test
    fun `timed render preserves identity and maps both renderer phases`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val container = FrameLayout(context)
        val handle = object : PlatformRenderContainerHandle {
            override val container: Any = container
        }
        val node = UiNodeTooling.attachTimingIdentity(
            VNode(
                type = NodeType.Spacer,
                spec = EmptyNodeSpec,
            ),
            identity = 41L,
        )
        val events = mutableListOf<Pair<Long?, CoreRenderTimingPhase>>()

        val frame = AndroidCoreRenderEngine().renderIntoWithTiming(
            container = handle,
            previousMountedNodes = emptyList(),
            nodes = listOf(node),
            diagnosticLevel = RenderFrameDiagnosticLevel.None,
            timingCollector = { subject, phase ->
                events += subject.nodeIdentity to phase
                CoreRenderTimingSpan { }
            },
        )
        frame.commitEffects.forEach { effect -> effect.commit() }

        assertEquals(1, frame.mountedNodes.size)
        assertTrue(events.any { event ->
            event.first == 41L && event.second == CoreRenderTimingPhase.Reconciliation
        })
        assertTrue(events.any { event ->
            event.first == 41L && event.second == CoreRenderTimingPhase.Binding
        })
    }
}
