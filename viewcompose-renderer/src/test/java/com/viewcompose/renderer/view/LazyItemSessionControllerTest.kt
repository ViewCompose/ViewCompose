package com.viewcompose.renderer.view

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import org.junit.Assert.assertEquals
import org.junit.Test

class LazyItemSessionControllerTest {
    @Test
    fun `reuses session when key and content token are unchanged`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        val item = item(key = "A", contentToken = 1)

        controller.bind(item)
        controller.bind(item)

        assertEquals(
            listOf("clear", "create:A:1", "render:A:1"),
            events,
        )
    }

    @Test
    fun `refreshes existing session updater when key and content token are unchanged`() {
        val events = mutableListOf<String>()
        val controller = createController(events)

        controller.bind(
            item(
                key = "A",
                contentToken = 1,
                sessionUpdater = { session ->
                    (session as RecordingSession).updateLabel("A:1:first")
                },
            ),
        )
        controller.bind(
            item(
                key = "A",
                contentToken = 1,
                sessionUpdater = { session ->
                    (session as RecordingSession).updateLabel("A:1:second")
                },
            ),
        )

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "update:A:1:first",
                "render:A:1:first",
                "update:A:1:second",
            ),
            events,
        )
    }

    @Test
    fun `replaces session when content token changes`() {
        val events = mutableListOf<String>()
        val controller = createController(events)

        controller.bind(item(key = "A", contentToken = 1))
        controller.bind(item(key = "A", contentToken = 2))

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "render:A:1",
                "dispose:A:1",
                "clear",
                "create:A:2",
                "render:A:2",
            ),
            events,
        )
    }

    @Test
    fun `updates existing session when content token changes but key is stable`() {
        val events = mutableListOf<String>()
        val controller = createController(events)

        controller.bind(
            item(
                key = "A",
                contentToken = 1,
                sessionUpdater = { session ->
                    (session as RecordingSession).updateLabel("A:1")
                },
            ),
        )
        controller.bind(
            item(
                key = "A",
                contentToken = 2,
                sessionUpdater = { session ->
                    (session as RecordingSession).updateLabel("A:2")
                },
            ),
        )

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "update:A:1",
                "render:A:1",
                "update:A:2",
                "render:A:2",
            ),
            events,
        )
    }

    @Test
    fun `recycle disposes active session`() {
        val events = mutableListOf<String>()
        val controller = createController(events)

        controller.bind(item(key = "A", contentToken = 1))
        controller.recycle()

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "render:A:1",
                "dispose:A:1",
                "clear",
            ),
            events,
        )
    }

    private fun createController(
        events: MutableList<String>,
    ): LazyItemSessionController {
        return LazyItemSessionController(
            createSession = { item ->
                RecordingSession(
                    label = "${item.key}:${item.contentToken}",
                    events = events,
                )
            },
            clearContainer = {
                events += "clear"
            },
        )
    }

    private fun item(
        key: Any?,
        contentToken: Any?,
        sessionUpdater: ((LazyListItemSession) -> Unit)? = null,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = contentToken,
            sessionFactory = LazyListItemSessionFactory { _ ->
                error("sessionFactory should not be used in controller tests")
            },
            sessionUpdater = sessionUpdater,
        )
    }

    private class RecordingSession(
        private var label: String,
        private val events: MutableList<String>,
    ) : LazyListItemSession {
        init {
            events += "create:$label"
        }

        override fun render() {
            events += "render:$label"
        }

        override fun dispose() {
            events += "dispose:$label"
        }

        fun updateLabel(
            label: String,
        ) {
            this.label = label
            events += "update:$label"
        }
    }
}
