package com.viewcompose.widget.core

/*
 * 测试职责：覆盖 widget-core overlay 中的 Dialog Overlay Host 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Dialog Overlay Host behavior in widget-core overlay and guards DSL, state, or theme contracts against regressions.
 */

import com.viewcompose.ui.node.spec.TextNodeProps
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogOverlayHostTest {
    @Test
    fun `shows dialog request on first commit`() {
        val presenter = RecordingDialogPresenter()
        val host = DialogOverlayHost(presenter)

        host.commit(
            sessionId = OverlaySessionId("session-1"),
            requests = listOf(
                dialogRequest(
                    key = "settings",
                    message = "Settings dialog",
                ),
            ),
        )

        assertEquals(
            listOf("show:session-1:settings:Settings dialog"),
            presenter.events,
        )
    }

    @Test
    fun `updates dialog when content changes`() {
        val presenter = RecordingDialogPresenter()
        val host = DialogOverlayHost(presenter)
        val sessionId = OverlaySessionId("session-1")

        host.commit(
            sessionId = sessionId,
            requests = listOf(
                dialogRequest(
                    key = "settings",
                    message = "Settings dialog",
                ),
            ),
        )
        host.commit(
            sessionId = sessionId,
            requests = listOf(
                dialogRequest(
                    key = "settings",
                    message = "Updated dialog",
                ),
            ),
        )

        assertEquals(
            listOf(
                "show:session-1:settings:Settings dialog",
                "update:session-1:settings:Updated dialog",
            ),
            presenter.events,
        )
    }

    @Test
    fun `updates dialog when position or scrim changes`() {
        val presenter = RecordingDialogPresenter()
        val host = DialogOverlayHost(presenter)
        val sessionId = OverlaySessionId("session-1")

        host.commit(
            sessionId = sessionId,
            requests = listOf(
                dialogRequest(
                    key = "settings",
                    message = "Settings dialog",
                    spec = DialogOverlaySpec(
                        position = DialogPosition.Center,
                        scrimOpacity = 0.32f,
                    ),
                ),
            ),
        )
        host.commit(
            sessionId = sessionId,
            requests = listOf(
                dialogRequest(
                    key = "settings",
                    message = "Settings dialog",
                    spec = DialogOverlaySpec(
                        position = DialogPosition.Bottom,
                        scrimOpacity = 0.56f,
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "show:session-1:settings:Settings dialog",
                "update:session-1:settings:Settings dialog",
            ),
            presenter.events,
        )
    }

    @Test
    fun `dismisses dialog removed on next commit`() {
        val presenter = RecordingDialogPresenter()
        val host = DialogOverlayHost(presenter)
        val sessionId = OverlaySessionId("session-1")

        host.commit(
            sessionId = sessionId,
            requests = listOf(
                dialogRequest(
                    key = "settings",
                    message = "Settings dialog",
                ),
            ),
        )
        host.commit(
            sessionId = sessionId,
            requests = emptyList(),
        )

        assertEquals(
            listOf(
                "show:session-1:settings:Settings dialog",
                "dismiss:session-1:settings",
            ),
            presenter.events,
        )
    }

    @Test
    fun `clear only dismisses matching session dialogs`() {
        val presenter = RecordingDialogPresenter()
        val host = DialogOverlayHost(presenter)

        host.commit(
            sessionId = OverlaySessionId("session-1"),
            requests = listOf(
                dialogRequest(
                    key = "settings",
                    message = "Settings dialog",
                ),
            ),
        )
        host.commit(
            sessionId = OverlaySessionId("session-2"),
            requests = listOf(
                dialogRequest(
                    key = "profile",
                    message = "Profile dialog",
                ),
            ),
        )

        host.clear(OverlaySessionId("session-1"))

        assertEquals(
            listOf(
                "show:session-1:settings:Settings dialog",
                "show:session-2:profile:Profile dialog",
                "dismiss:session-1:settings",
            ),
            presenter.events,
        )
    }

    private fun dialogRequest(
        key: String,
        message: String,
        spec: DialogOverlaySpec = DialogOverlaySpec(),
    ): OverlayRequest {
        return OverlayRequest(
            key = key,
            type = OverlayType.Dialog,
            payload = spec,
            contentToken = DialogOverlayContent(
                surface = captureOverlaySurfaceContent {
                    Text(text = message)
                },
            ),
        )
    }

    private class RecordingDialogPresenter : DialogOverlayPresenter {
        val events = mutableListOf<String>()

        override fun show(
            entryId: OverlayEntryId,
            spec: DialogOverlaySpec,
            content: DialogOverlayContent,
        ): DialogOverlayHandle {
            val textNode = content.surface.buildNodes().single()
            val message = (textNode.spec as? TextNodeProps)?.text?.toString() ?: ""
            events += "show:${entryId.sessionId.value}:${entryId.requestKey}:$message"
            return RecordingDialogHandle(
                entryId = entryId,
                events = events,
            )
        }
    }

    private class RecordingDialogHandle(
        private val entryId: OverlayEntryId,
        private val events: MutableList<String>,
    ) : DialogOverlayHandle {
        override fun update(
            spec: DialogOverlaySpec,
            content: DialogOverlayContent,
        ) {
            val textNode = content.surface.buildNodes().single()
            val message = (textNode.spec as? TextNodeProps)?.text?.toString() ?: ""
            events += "update:${entryId.sessionId.value}:${entryId.requestKey}:$message"
        }

        override fun dismiss() {
            events += "dismiss:${entryId.sessionId.value}:${entryId.requestKey}"
        }
    }
}
