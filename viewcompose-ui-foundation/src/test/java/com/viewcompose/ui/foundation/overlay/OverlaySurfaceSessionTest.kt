package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core overlay 中的 Overlay Surface Session 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Overlay Surface Session behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.TextNodeProps
import com.viewcompose.ui.environment.UiEnvironmentValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OverlaySurfaceSessionTest {
    @Test
    fun `capture overlay surface content keeps overlay host and local snapshot`() {
        val overlayHost = RecordingOverlayHost()
        val localText = uiLocalOf { "unset" }
        lateinit var captured: OverlaySurfaceContent

        buildVNodeTree {
            ProvideOverlayHost(overlayHost) {
                ProvideLocal(localText, "captured-value") {
                    captured = captureOverlaySurfaceContent {
                        Text(UiLocals.current(localText))
                    }
                }
            }
        }

        assertSame(overlayHost, captured.overlayHost())
        val nodes = captured.buildNodes()
        assertEquals(1, nodes.size)
        assertEquals(NodeType.Text, nodes.single().type)
        val textSpec = nodes.single().spec as TextNodeProps
        assertEquals("captured-value", textSpec.document.text)
    }

    @Test
    fun `captured overlay content keeps the current resource revision`() {
        lateinit var captured: OverlaySurfaceContent

        buildVNodeTree {
            UiEnvironment(UiEnvironmentValues(resourceRevision = 23L)) {
                captured = captureOverlaySurfaceContent {
                    Text("Overlay resource")
                }
            }
        }

        assertEquals(23L, captured.buildNodes().single().environment.resourceRevision)
    }

    private class RecordingOverlayHost : OverlayHost {
        override fun commit(
            sessionId: OverlaySessionId,
            requests: List<OverlayRequest>,
        ) = Unit

        override fun clear(sessionId: OverlaySessionId) = Unit
    }
}
