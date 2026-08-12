package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core overlay 中的 Modal Bottom Sheet Overlay Host 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Modal Bottom Sheet Overlay Host behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.spec.TextNodeProps
import org.junit.Assert.assertEquals
import org.junit.Test

class ModalBottomSheetOverlayHostTest {
    @Test
    fun `shows bottom sheet request on first commit`() {
        val presenter = RecordingBottomSheetPresenter()
        val host = ModalBottomSheetOverlayHost(presenter)

        host.commit(
            sessionId = OverlaySessionId("session-1"),
            requests = listOf(
                bottomSheetRequest(
                    key = "actions",
                    message = "Action sheet",
                ),
            ),
        )

        assertEquals(
            listOf("show:session-1:actions:Action sheet"),
            presenter.events,
        )
    }

    @Test
    fun `updates bottom sheet when content changes`() {
        val presenter = RecordingBottomSheetPresenter()
        val host = ModalBottomSheetOverlayHost(presenter)
        val sessionId = OverlaySessionId("session-1")

        host.commit(
            sessionId = sessionId,
            requests = listOf(
                bottomSheetRequest(
                    key = "actions",
                    message = "Action sheet",
                ),
            ),
        )
        host.commit(
            sessionId = sessionId,
            requests = listOf(
                bottomSheetRequest(
                    key = "actions",
                    message = "Updated sheet",
                ),
            ),
        )

        assertEquals(
            listOf(
                "show:session-1:actions:Action sheet",
                "update:session-1:actions:Updated sheet",
            ),
            presenter.events,
        )
    }

    @Test
    fun `updates bottom sheet when behavior changes`() {
        val presenter = RecordingBottomSheetPresenter()
        val host = ModalBottomSheetOverlayHost(presenter)
        val sessionId = OverlaySessionId("session-1")

        host.commit(
            sessionId = sessionId,
            requests = listOf(
                bottomSheetRequest(
                    key = "actions",
                    message = "Action sheet",
                    spec = ModalBottomSheetOverlaySpec(
                        skipPartiallyExpanded = false,
                        scrimOpacity = 0.32f,
                    ),
                ),
            ),
        )
        host.commit(
            sessionId = sessionId,
            requests = listOf(
                bottomSheetRequest(
                    key = "actions",
                    message = "Action sheet",
                    spec = ModalBottomSheetOverlaySpec(
                        skipPartiallyExpanded = true,
                        scrimOpacity = 0.56f,
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "show:session-1:actions:Action sheet",
                "update:session-1:actions:Action sheet",
            ),
            presenter.events,
        )
    }

    @Test
    fun `dismisses bottom sheet removed on next commit`() {
        val presenter = RecordingBottomSheetPresenter()
        val host = ModalBottomSheetOverlayHost(presenter)
        val sessionId = OverlaySessionId("session-1")

        host.commit(
            sessionId = sessionId,
            requests = listOf(
                bottomSheetRequest(
                    key = "actions",
                    message = "Action sheet",
                ),
            ),
        )
        host.commit(
            sessionId = sessionId,
            requests = emptyList(),
        )

        assertEquals(
            listOf(
                "show:session-1:actions:Action sheet",
                "dismiss:session-1:actions",
            ),
            presenter.events,
        )
    }

    @Test
    fun `clear only dismisses matching session bottom sheets`() {
        val presenter = RecordingBottomSheetPresenter()
        val host = ModalBottomSheetOverlayHost(presenter)

        host.commit(
            sessionId = OverlaySessionId("session-1"),
            requests = listOf(
                bottomSheetRequest(
                    key = "actions",
                    message = "Action sheet",
                ),
            ),
        )
        host.commit(
            sessionId = OverlaySessionId("session-2"),
            requests = listOf(
                bottomSheetRequest(
                    key = "profile",
                    message = "Profile sheet",
                ),
            ),
        )

        host.clear(OverlaySessionId("session-1"))

        assertEquals(
            listOf(
                "show:session-1:actions:Action sheet",
                "show:session-2:profile:Profile sheet",
                "dismiss:session-1:actions",
            ),
            presenter.events,
        )
    }

    private fun bottomSheetRequest(
        key: String,
        message: String,
        spec: ModalBottomSheetOverlaySpec = ModalBottomSheetOverlaySpec(),
    ): OverlayRequest {
        return OverlayRequest(
            key = key,
            type = OverlayType.ModalBottomSheet,
            payload = spec,
            contentToken = ModalBottomSheetOverlayContent(
                surface = captureOverlaySurfaceContent {
                    Text(text = message)
                },
            ),
        )
    }

    private class RecordingBottomSheetPresenter : ModalBottomSheetOverlayPresenter {
        val events = mutableListOf<String>()

        override fun show(
            entryId: OverlayEntryId,
            spec: ModalBottomSheetOverlaySpec,
            content: ModalBottomSheetOverlayContent,
        ): ModalBottomSheetOverlayHandle {
            val textNode = content.surface.buildNodes().single()
            val message = (textNode.spec as? TextNodeProps)?.document?.text ?: ""
            events += "show:${entryId.sessionId.value}:${entryId.requestKey}:$message"
            return RecordingBottomSheetHandle(entryId, events)
        }
    }

    private class RecordingBottomSheetHandle(
        private val entryId: OverlayEntryId,
        private val events: MutableList<String>,
    ) : ModalBottomSheetOverlayHandle {
        override fun update(
            spec: ModalBottomSheetOverlaySpec,
            content: ModalBottomSheetOverlayContent,
        ) {
            val textNode = content.surface.buildNodes().single()
            val message = (textNode.spec as? TextNodeProps)?.document?.text ?: ""
            events += "update:${entryId.sessionId.value}:${entryId.requestKey}:$message"
        }

        override fun dismiss() {
            events += "dismiss:${entryId.sessionId.value}:${entryId.requestKey}"
        }
    }
}
