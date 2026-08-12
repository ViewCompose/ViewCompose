package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core overlay 中的 Popup Overlay Host 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Popup Overlay Host behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.spec.TextNodeProps
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupOverlayHostTest {
    @Test
    fun `shows popup request on first commit`() {
        val presenter = RecordingPopupPresenter()
        val host = PopupOverlayHost(presenter)

        host.commit(
            sessionId = OverlaySessionId("session-1"),
            requests = listOf(
                popupRequest(
                    key = "menu",
                    message = "Menu popup",
                ),
            ),
        )

        assertEquals(
            listOf("show:session-1:menu:Menu popup"),
            presenter.events,
        )
    }

    @Test
    fun `updates popup when content changes`() {
        val presenter = RecordingPopupPresenter()
        val host = PopupOverlayHost(presenter)
        val sessionId = OverlaySessionId("session-1")

        host.commit(
            sessionId = sessionId,
            requests = listOf(
                popupRequest(
                    key = "menu",
                    message = "Menu popup",
                ),
            ),
        )
        host.commit(
            sessionId = sessionId,
            requests = listOf(
                popupRequest(
                    key = "menu",
                    message = "Updated popup",
                ),
            ),
        )

        assertEquals(
            listOf(
                "show:session-1:menu:Menu popup",
                "update:session-1:menu:Updated popup",
            ),
            presenter.events,
        )
    }

    @Test
    fun `updates popup when alignment changes`() {
        val presenter = RecordingPopupPresenter()
        val host = PopupOverlayHost(presenter)
        val sessionId = OverlaySessionId("session-1")

        host.commit(
            sessionId = sessionId,
            requests = listOf(
                popupRequest(
                    key = "menu",
                    message = "Menu popup",
                    spec = PopupOverlaySpec(
                        anchorId = "popup_anchor",
                        alignment = PopupAlignment.BelowStart,
                    ),
                ),
            ),
        )
        host.commit(
            sessionId = sessionId,
            requests = listOf(
                popupRequest(
                    key = "menu",
                    message = "Menu popup",
                    spec = PopupOverlaySpec(
                        anchorId = "popup_anchor",
                        alignment = PopupAlignment.AboveEnd,
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "show:session-1:menu:Menu popup",
                "update:session-1:menu:Menu popup",
            ),
            presenter.events,
        )
    }

    @Test
    fun `dismisses popup removed on next commit`() {
        val presenter = RecordingPopupPresenter()
        val host = PopupOverlayHost(presenter)
        val sessionId = OverlaySessionId("session-1")

        host.commit(
            sessionId = sessionId,
            requests = listOf(
                popupRequest(
                    key = "menu",
                    message = "Menu popup",
                ),
            ),
        )
        host.commit(
            sessionId = sessionId,
            requests = emptyList(),
        )

        assertEquals(
            listOf(
                "show:session-1:menu:Menu popup",
                "dismiss:session-1:menu",
            ),
            presenter.events,
        )
    }

    private fun popupRequest(
        key: String,
        message: String,
        spec: PopupOverlaySpec = PopupOverlaySpec(anchorId = "popup_anchor"),
    ): OverlayRequest {
        return OverlayRequest(
            key = key,
            type = OverlayType.Popup,
            payload = spec,
            contentToken = PopupOverlayContent(
                surface = captureOverlaySurfaceContent {
                    Text(text = message)
                },
            ),
        )
    }

    private class RecordingPopupPresenter : PopupOverlayPresenter {
        val events = mutableListOf<String>()

        override fun show(
            entryId: OverlayEntryId,
            spec: PopupOverlaySpec,
            content: PopupOverlayContent,
        ): PopupOverlayHandle {
            val textNode = content.surface.buildNodes().single()
            val message = (textNode.spec as? TextNodeProps)?.document?.text ?: ""
            events += "show:${entryId.sessionId.value}:${entryId.requestKey}:$message"
            return RecordingPopupHandle(
                entryId = entryId,
                events = events,
            )
        }
    }

    private class RecordingPopupHandle(
        private val entryId: OverlayEntryId,
        private val events: MutableList<String>,
    ) : PopupOverlayHandle {
        override fun update(
            spec: PopupOverlaySpec,
            content: PopupOverlayContent,
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
