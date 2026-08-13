package com.viewcompose.renderer.view

/*
 * 测试职责：覆盖 renderer view 中的 Lazy Item Session Controller 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy Item Session Controller behavior in renderer view and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyItemSessionControllerTest {
    @Test
    fun `prepare builds candidate and commit activates it without active render`() {
        val events = mutableListOf<String>()
        val controller = createLifecycleController(events)
        val item = item(
            key = "A",
            contentToken = 1,
            sessionUpdater = { session ->
                (session as LifecycleRecordingSession).updateLabel("prepared")
            },
        )

        controller.prepare(item, submissionRevision = 4L)

        assertEquals(
            listOf("clear", "create:A:1", "update:prepared", "prepare:prepared"),
            events,
        )
        assertFalse(controller.hasCommitted(4L))

        controller.commit(4L)

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "update:prepared",
                "prepare:prepared",
                "activate:prepared",
            ),
            events,
        )
        assertTrue(controller.hasCommitted(4L))
    }

    @Test
    fun `duplicate detached bind prepares and activates one candidate`() {
        val events = mutableListOf<String>()
        val controller = createLifecycleController(events)
        val item = item(key = "A", contentToken = 1)

        controller.prepare(item, submissionRevision = 3L)
        controller.prepare(item, submissionRevision = 3L)
        controller.commit(3L)
        controller.commit(3L)

        assertEquals(
            listOf("clear", "create:A:1", "prepare:A:1", "activate:A:1"),
            events,
        )
    }

    @Test
    fun `newer detached revision replaces and disposes prepared candidate`() {
        val events = mutableListOf<String>()
        val controller = createLifecycleController(events)

        controller.prepare(item(key = "A", contentToken = 1), submissionRevision = 1L)
        controller.prepare(item(key = "B", contentToken = 2), submissionRevision = 2L)
        controller.commit(2L)

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "prepare:A:1",
                "dispose:A:1",
                "clear",
                "create:B:2",
                "prepare:B:2",
                "activate:B:2",
            ),
            events,
        )
    }

    @Test
    fun `recycle before attach disposes prepared candidate without activation`() {
        val events = mutableListOf<String>()
        val controller = createLifecycleController(events)

        controller.prepare(item(key = "A", contentToken = 1), submissionRevision = 1L)
        controller.recycle()

        assertEquals(
            listOf("clear", "create:A:1", "prepare:A:1", "dispose:A:1", "clear"),
            events,
        )
    }

    @Test
    fun `reuses session when key and content token are unchanged`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        val item = item(key = "A", contentToken = 1)

        controller.bind(item, submissionRevision = 1L)
        controller.bind(item, submissionRevision = 1L)

        assertEquals(
            listOf("clear", "create:A:1", "render:A:1"),
            events,
        )
    }

    @Test
    fun `refreshes and renders existing session when key and content token are unchanged`() {
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
                "render:A:1:second",
            ),
            events,
        )
    }

    @Test
    fun `renders a reused updater once for each submission revision`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        val updater: (LazyListItemSession) -> Unit = { session ->
            (session as RecordingSession).updateLabel("A:1")
        }
        val item = item(
            key = "A",
            contentToken = 1,
            sessionUpdater = updater,
        )

        controller.bind(item, submissionRevision = 1L)
        controller.bind(item, submissionRevision = 2L)

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "update:A:1",
                "render:A:1",
                "update:A:1",
                "render:A:1",
            ),
            events,
        )
    }

    @Test
    fun `ignores a delayed duplicate bind from the same submission revision`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        val item = item(
            key = "A",
            contentToken = 1,
            sessionUpdater = { session ->
                (session as RecordingSession).updateLabel("A:1")
            },
        )

        controller.bind(item, submissionRevision = 7L)
        controller.bind(
            item = item,
            payload = com.viewcompose.renderer.reconcile.LazyListChangePayload.ContentTokenChanged(
                previous = 0,
                next = 1,
            ),
            submissionRevision = 7L,
        )

        assertEquals(
            listOf("clear", "create:A:1", "update:A:1", "render:A:1"),
            events,
        )
    }

    @Test
    fun `staged submission does not update or render until committed`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        controller.bind(
            item = item(
                key = "A",
                contentToken = 1,
                sessionUpdater = { session ->
                    (session as RecordingSession).updateLabel("old")
                },
            ),
            submissionRevision = 1L,
        )

        controller.stage(
            item = item(
                key = "A",
                contentToken = 1,
                sessionUpdater = { session ->
                    (session as RecordingSession).updateLabel("new")
                },
            ),
            submissionRevision = 2L,
        )

        assertEquals(
            listOf("clear", "create:A:1", "update:old", "render:old"),
            events,
        )
        controller.commit(submissionRevision = 2L)
        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "update:old",
                "render:old",
                "update:new",
                "render:new",
            ),
            events,
        )
    }

    @Test
    fun `reports whether the requested revision is committed`() {
        val controller = createController(mutableListOf())

        assertFalse(controller.hasCommitted(submissionRevision = 1L))
        controller.commit(submissionRevision = 1L)
        assertFalse(controller.hasCommitted(submissionRevision = 1L))
        controller.stage(item(key = "A", contentToken = 1), submissionRevision = 1L)
        controller.commit(submissionRevision = 2L)
        assertFalse(controller.hasCommitted(submissionRevision = 1L))
        controller.commit(submissionRevision = 1L)
        assertTrue(controller.hasCommitted(submissionRevision = 1L))
        assertFalse(controller.hasCommitted(submissionRevision = 2L))
    }

    @Test
    fun `discard removes staged submission without touching committed child`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        controller.bind(item(key = "A", contentToken = 1), submissionRevision = 1L)
        controller.stage(item(key = "B", contentToken = 2), submissionRevision = 2L)

        controller.discard(submissionRevision = 2L)

        assertEquals(listOf("clear", "create:A:1", "render:A:1"), events)
    }

    @Test
    fun `replaces equal token session when updater is absent and factory changes`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        val first = item(
            key = "A",
            contentToken = 1,
            sessionFactory = LazyListItemSessionFactory { error("first") },
        )
        val second = item(
            key = "A",
            contentToken = 1,
            sessionFactory = LazyListItemSessionFactory { error("second") },
        )

        controller.bind(first)
        controller.bind(second)

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "render:A:1",
                "dispose:A:1",
                "clear",
                "create:A:1",
                "render:A:1",
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

    private fun createLifecycleController(
        events: MutableList<String>,
    ): LazyItemSessionController {
        return LazyItemSessionController(
            createSession = { item ->
                LifecycleRecordingSession(
                    label = "${item.key}:${item.contentToken}",
                    events = events,
                )
            },
            clearContainer = { events += "clear" },
        )
    }

    private fun item(
        key: Any?,
        contentToken: Any?,
        sessionFactory: LazyListItemSessionFactory = LazyListItemSessionFactory { _ ->
            error("sessionFactory should not be used in controller tests")
        },
        sessionUpdater: ((LazyListItemSession) -> Unit)? = null,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentToken = contentToken,
            sessionFactory = sessionFactory,
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

    private class LifecycleRecordingSession(
        private var label: String,
        private val events: MutableList<String>,
    ) : LazyListItemSession {
        init {
            events += "create:$label"
        }

        override fun prepare() {
            events += "prepare:$label"
        }

        override fun activate() {
            events += "activate:$label"
        }

        override fun render() {
            events += "render:$label"
        }

        override fun dispose() {
            events += "dispose:$label"
        }

        fun updateLabel(label: String) {
            this.label = label
            events += "update:$label"
        }
    }
}
