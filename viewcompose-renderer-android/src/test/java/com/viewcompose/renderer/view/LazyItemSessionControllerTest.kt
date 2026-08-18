package com.viewcompose.renderer.view

/*
 * 测试职责：覆盖 renderer view 中的 Lazy Item Session Controller 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy Item Session Controller behavior in renderer view and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.lazyListItemSessionStrategy
import com.viewcompose.ui.node.ReusableItemPresentation
import com.viewcompose.renderer.view.lazy.session.LazyItemBindOutcome
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyItemSessionControllerTest {
    @Test
    fun `bind outcome separates new activation revision render and duplicate submission`() {
        val controller = createController(mutableListOf())

        assertEquals(
            LazyItemBindOutcome.ActivatedNewSession,
            controller.bind(item(key = "A", contentRevision = 1), submissionRevision = 1L),
        )
        assertEquals(
            LazyItemBindOutcome.AlreadyCommitted,
            controller.bind(item(key = "A", contentRevision = 1), submissionRevision = 1L),
        )
        assertEquals(
            LazyItemBindOutcome.AcceptedUnchanged,
            controller.bind(item(key = "A", contentRevision = 1), submissionRevision = 2L),
        )
        assertEquals(
            LazyItemBindOutcome.RenderedRevision,
            controller.bind(item(key = "A", contentRevision = 2), submissionRevision = 3L),
        )
    }

    @Test
    fun `exact committed check includes semantic revisions and exact submission`() {
        val controller = createController(mutableListOf())
        val committed = item(key = "A", contentRevision = 1)
        controller.bind(committed, submissionRevision = 2L)

        assertTrue(controller.hasCommittedExact(committed, submissionRevision = 2L))
        assertFalse(controller.hasCommittedExact(committed, submissionRevision = 1L))
        assertFalse(
            controller.hasCommittedExact(
                item(key = "A", contentRevision = 1),
                submissionRevision = 2L,
            ),
        )
        assertFalse(
            controller.hasCommittedExact(
                item(key = "A", contentRevision = 2),
                submissionRevision = 2L,
            ),
        )
        assertFalse(
            controller.hasCommittedExact(
                LazyListItem(
                    key = "A",
                    contentRevision = 1,
                    environmentRevision = "dark",
                    sessionStrategy = lazyListItemSessionStrategy(
                        create = { error("unused") },
                        update = {},
                    ),
                ),
                submissionRevision = 2L,
            ),
        )
    }

    @Test
    fun `bind outcome keeps speculative preparation separate from cheap activation`() {
        val controller = createLifecycleController(mutableListOf())
        val candidate = item(key = "A", contentRevision = 1)

        assertEquals(
            LazyItemBindOutcome.PreparedNewSession,
            controller.prepare(candidate, submissionRevision = 1L),
        )
        assertEquals(
            LazyItemBindOutcome.ActivatedPrepared,
            controller.commit(submissionRevision = 1L),
        )
        assertEquals(
            LazyItemBindOutcome.AlreadyCommitted,
            controller.commit(submissionRevision = 1L),
        )
    }

    @Test
    fun `different key creates a new logical session while transferring only presentation`() {
        val events = mutableListOf<String>()
        val presentation = RecordingPresentation(events)
        var created = 0
        val controller = LazyItemSessionController(
            createSession = { item ->
                ReusableRecordingSession(
                    label = item.key.toString(),
                    events = events,
                    presentation = presentation,
                ).also { created += 1 }
            },
            clearContainer = { events += "clear" },
        )

        controller.bind(item(key = "A", contentRevision = 1), submissionRevision = 1L)
        val detached = checkNotNull(controller.detachForReuse())
        controller.adoptForNextSession(detached)
        controller.bind(item(key = "B", contentRevision = 1), submissionRevision = 2L)

        assertEquals(2, created)
        assertEquals(
            listOf(
                "clear",
                "create:A",
                "render:A",
                "detach:A",
                "clear",
                "clear",
                "create:B",
                "adopt:B",
                "render:B",
            ),
            events,
        )
        assertFalse(presentation.released)
    }

    @Test
    fun `discarding an unadopted presentation releases it exactly once`() {
        val events = mutableListOf<String>()
        val presentation = RecordingPresentation(events)
        val controller = createController(events)

        controller.adoptForNextSession(presentation)
        controller.recycle()
        controller.recycle()

        assertEquals(listOf("clear", "release", "clear"), events)
        assertTrue(presentation.released)
    }

    @Test
    fun `presentation rejected by a new session is released immediately`() {
        val events = mutableListOf<String>()
        val presentation = RecordingPresentation(events)
        val controller = createController(events)

        controller.adoptForNextSession(presentation)
        controller.bind(item(key = "A", contentRevision = 1))
        controller.recycle()

        assertEquals(
            listOf("clear", "create:A:1", "release", "render:A:1", "dispose:A:1", "clear"),
            events,
        )
        assertTrue(presentation.released)
    }

    @Test
    fun `presentation is released when adoption throws`() {
        val events = mutableListOf<String>()
        val presentation = RecordingPresentation(events)
        val controller = LazyItemSessionController(
            createSession = {
                object : LazyListItemSession {
                    override fun render() = true

                    override fun adoptReusablePresentation(
                        presentation: ReusableItemPresentation,
                    ): Boolean = error("adoption failed")

                    override fun dispose() = Unit
                }
            },
            clearContainer = { events += "clear" },
        )

        controller.adoptForNextSession(presentation)
        val failure = runCatching {
            controller.bind(item(key = "A", contentRevision = 1))
        }.exceptionOrNull()

        assertEquals("adoption failed", failure?.message)
        assertEquals(listOf("clear", "release", "clear"), events)
        assertTrue(presentation.released)
        assertFalse(controller.hasPendingPresentation)
    }

    @Test
    fun `failed detach still clears logical ownership and pending presentation`() {
        val events = mutableListOf<String>()
        val presentation = RecordingPresentation(events)
        val controller = LazyItemSessionController(
            createSession = {
                object : LazyListItemSession {
                    override fun render() = true

                    override fun disposeForReuse(): ReusableItemPresentation? {
                        events += "detach"
                        error("detach failed")
                    }

                    override fun dispose() = Unit
                }
            },
            clearContainer = { events += "clear" },
        )
        controller.bind(item(key = "A", contentRevision = 1))
        controller.adoptForNextSession(presentation)

        val failure = runCatching(controller::detachForReuse).exceptionOrNull()

        assertEquals("detach failed", failure?.message)
        assertEquals(listOf("clear", "detach", "clear", "release"), events)
        assertFalse(controller.hasPendingPresentation)
    }

    @Test
    fun `failed container clear releases the detached presentation and clears ownership`() {
        val events = mutableListOf<String>()
        val detached = RecordingPresentation(events)
        var failClear = false
        val controller = LazyItemSessionController(
            createSession = {
                object : LazyListItemSession {
                    override fun render() = true

                    override fun disposeForReuse(): ReusableItemPresentation {
                        events += "detach"
                        return detached
                    }

                    override fun dispose() = Unit
                }
            },
            clearContainer = {
                events += "clear"
                if (failClear) error("clear failed")
            },
        )
        controller.bind(item(key = "A", contentRevision = 1))
        failClear = true

        val failure = runCatching(controller::detachForReuse).exceptionOrNull()

        assertEquals("clear failed", failure?.message)
        assertEquals(listOf("clear", "detach", "clear", "release"), events)
        assertTrue(detached.released)
        assertFalse(controller.hasPendingPresentation)
    }

    @Test
    fun `failed updater never publishes a partial session and a retry creates a fresh owner`() {
        val events = mutableListOf<String>()
        var created = 0
        var failUpdate = true
        val controller = LazyItemSessionController(
            createSession = {
                RecordingSession("candidate-${++created}", events)
            },
            clearContainer = { events += "clear" },
        )
        fun candidate() = item(
            key = "A",
            contentRevision = 1,
            sessionUpdater = { session ->
                events += "update-${(session as RecordingSession).label}"
                if (failUpdate) error("update failed")
            },
        )

        val failure = runCatching { controller.bind(candidate(), submissionRevision = 1L) }
            .exceptionOrNull()
        failUpdate = false
        controller.bind(candidate(), submissionRevision = 1L)

        assertEquals("update failed", failure?.message)
        assertEquals(2, created)
        assertEquals(
            listOf(
                "clear",
                "create:candidate-1",
                "update-candidate-1",
                "dispose:candidate-1",
                "clear",
                "clear",
                "create:candidate-2",
                "update-candidate-2",
                "render:candidate-2",
            ),
            events,
        )
    }

    @Test
    fun `failed revision render abandons the mutated session and retries with a fresh owner`() {
        val events = mutableListOf<String>()
        var created = 0
        var failRender = false
        val controller = LazyItemSessionController(
            createSession = { item ->
                FailingRenderSession(
                    label = "${item.key}:${item.contentRevision}:owner-${++created}",
                    events = events,
                    shouldFail = { failRender },
                )
            },
            clearContainer = { events += "clear" },
        )
        fun revision(revision: Int) = item(
            key = "A",
            contentRevision = revision,
            sessionUpdater = { session ->
                (session as FailingRenderSession).update("A:$revision")
            },
        )

        controller.bind(revision(1), submissionRevision = 1L)
        failRender = true
        val failure = runCatching { controller.bind(revision(2), submissionRevision = 2L) }
            .exceptionOrNull()
        failRender = false
        controller.bind(revision(2), submissionRevision = 2L)

        assertEquals("render failed", failure?.message)
        assertEquals(2, created)
        assertEquals(
            listOf(
                "clear",
                "create:A:1:owner-1",
                "update:A:1",
                "render:A:1",
                "update:A:2",
                "render:A:2",
                "dispose:A:2",
                "clear",
                "clear",
                "create:A:2:owner-2",
                "update:A:2",
                "render:A:2",
            ),
            events,
        )
    }

    @Test
    fun `failed prepared activation is not committed and can retry the candidate`() {
        val events = mutableListOf<String>()
        var created = 0
        var failActivation = true
        val controller = LazyItemSessionController(
            createSession = { item ->
                object : LazyListItemSession {
                    private val label = "${item.key}:owner-${++created}"

                    init {
                        events += "create:$label"
                    }

                    override fun prepare() {
                        events += "prepare:$label"
                    }

                    override fun activate(): Boolean {
                        events += "activate:$label"
                        if (failActivation) error("activation failed")
                        return true
                    }

                    override fun render() = true

                    override fun dispose() {
                        events += "dispose:$label"
                    }
                }
            },
            clearContainer = { events += "clear" },
        )
        val candidate = item(key = "A", contentRevision = 1)

        controller.prepare(candidate, submissionRevision = 4L)
        val failure = runCatching { controller.commit(4L) }.exceptionOrNull()
        assertFalse(controller.hasCommitted(4L))
        failActivation = false
        controller.commit(4L)

        assertEquals("activation failed", failure?.message)
        assertTrue(controller.hasCommitted(4L))
        assertEquals(2, created)
        assertEquals(
            listOf(
                "clear",
                "create:A:owner-1",
                "prepare:A:owner-1",
                "activate:A:owner-1",
                "dispose:A:owner-1",
                "clear",
                "clear",
                "create:A:owner-2",
                "activate:A:owner-2",
            ),
            events,
        )
    }

    @Test
    fun `rolled back render does not commit revision and retries the retained session`() {
        val events = mutableListOf<String>()
        var commitRender = false
        val controller = LazyItemSessionController(
            createSession = {
                object : LazyListItemSession {
                    override fun render(): Boolean {
                        events += "render:$commitRender"
                        return commitRender
                    }

                    override fun dispose() = Unit
                }
            },
            clearContainer = { events += "clear" },
        )
        val item = item(key = "A", contentRevision = 1)

        controller.bind(item, submissionRevision = 8L)
        assertFalse(controller.hasCommitted(8L))
        commitRender = true
        controller.bind(item, submissionRevision = 8L)

        assertTrue(controller.hasCommitted(8L))
        assertEquals(listOf("clear", "render:false", "render:true"), events)
    }

    @Test
    fun `prepare builds candidate and commit activates it without active render`() {
        val events = mutableListOf<String>()
        val controller = createLifecycleController(events)
        val item = item(
            key = "A",
            contentRevision = 1,
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
        val item = item(key = "A", contentRevision = 1)

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

        controller.prepare(item(key = "A", contentRevision = 1), submissionRevision = 1L)
        controller.prepare(item(key = "B", contentRevision = 2), submissionRevision = 2L)
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

        controller.prepare(item(key = "A", contentRevision = 1), submissionRevision = 1L)
        controller.recycle()

        assertEquals(
            listOf("clear", "create:A:1", "prepare:A:1", "dispose:A:1", "clear"),
            events,
        )
    }

    @Test
    fun `reuses session when key and revisions are unchanged`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        val item = item(key = "A", contentRevision = 1)

        controller.bind(item, submissionRevision = 1L)
        controller.bind(item, submissionRevision = 1L)

        assertEquals(
            listOf("clear", "create:A:1", "render:A:1"),
            events,
        )
    }

    @Test
    fun `skips a newer updater when key and revisions are unchanged`() {
        val events = mutableListOf<String>()
        val controller = createController(events)

        controller.bind(
            item(
                key = "A",
                contentRevision = 1,
                sessionUpdater = { session ->
                    (session as RecordingSession).updateLabel("A:1:first")
                },
            ),
        )
        controller.bind(
            item(
                key = "A",
                contentRevision = 1,
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
            ),
            events,
        )
    }

    @Test
    fun `submission revision alone never renders a stable logical item`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        val updater: (LazyListItemSession) -> Unit = { session ->
            (session as RecordingSession).updateLabel("A:1")
        }
        val item = item(
            key = "A",
            contentRevision = 1,
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
            ),
            events,
        )
        assertTrue(controller.hasCommitted(2L))
    }

    @Test
    fun `ignores a delayed duplicate bind from the same submission revision`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        val item = item(
            key = "A",
            contentRevision = 1,
            sessionUpdater = { session ->
                (session as RecordingSession).updateLabel("A:1")
            },
        )

        controller.bind(item, submissionRevision = 7L)
        controller.bind(
            item = item,
            payload = com.viewcompose.renderer.reconcile.LazyListChangePayload.RevisionChanged(
                previousContent = 0,
                nextContent = 1,
                previousEnvironment = null,
                nextEnvironment = null,
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
                contentRevision = 1,
                sessionUpdater = { session ->
                    (session as RecordingSession).updateLabel("old")
                },
            ),
            submissionRevision = 1L,
        )

        controller.stage(
            item = item(
                key = "A",
                contentRevision = 1,
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
        controller.stage(item(key = "A", contentRevision = 1), submissionRevision = 1L)
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
        controller.bind(item(key = "A", contentRevision = 1), submissionRevision = 1L)
        controller.stage(item(key = "B", contentRevision = 2), submissionRevision = 2L)

        controller.discard(submissionRevision = 2L)

        assertEquals(listOf("clear", "create:A:1", "render:A:1"), events)
    }

    @Test
    fun `ignores factory identity when semantic revisions are equal`() {
        val events = mutableListOf<String>()
        val controller = createController(events)
        val first = item(
            key = "A",
            contentRevision = 1,
            sessionFactory = { error("first") },
        )
        val second = item(
            key = "A",
            contentRevision = 1,
            sessionFactory = { error("second") },
        )

        controller.bind(first)
        controller.bind(second)

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "render:A:1",
            ),
            events,
        )
    }

    @Test
    fun `retains logical session when content revision changes`() {
        val events = mutableListOf<String>()
        val controller = createController(events)

        controller.bind(item(key = "A", contentRevision = 1))
        controller.bind(item(key = "A", contentRevision = 2))

        assertEquals(
            listOf(
                "clear",
                "create:A:1",
                "render:A:1",
                "render:A:1",
            ),
            events,
        )
    }

    @Test
    fun `same key with different content type fully replaces logical session`() {
        val events = mutableListOf<String>()
        val controller = createController(events)

        controller.bind(item(key = "A", contentRevision = 1, contentType = "compact"))
        controller.bind(item(key = "A", contentRevision = 1, contentType = "expanded"))

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
    fun `same key with different item kind fully replaces logical session`() {
        val events = mutableListOf<String>()
        val controller = createController(events)

        controller.bind(item(key = "A", contentRevision = 1))
        controller.bind(
            item(
                key = "A",
                contentRevision = 1,
                kind = com.viewcompose.ui.node.LazyListItemKind.StickyHeader,
            ),
        )

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
    fun `updates existing session when content revision changes but key is stable`() {
        val events = mutableListOf<String>()
        val controller = createController(events)

        controller.bind(
            item(
                key = "A",
                contentRevision = 1,
                sessionUpdater = { session ->
                    (session as RecordingSession).updateLabel("A:1")
                },
            ),
        )
        controller.bind(
            item(
                key = "A",
                contentRevision = 2,
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

        controller.bind(item(key = "A", contentRevision = 1))
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
                    label = "${item.key}:${item.contentRevision}",
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
                    label = "${item.key}:${item.contentRevision}",
                    events = events,
                )
            },
            clearContainer = { events += "clear" },
        )
    }

    private fun item(
        key: Any,
        contentRevision: Any?,
        contentType: Any? = null,
        kind: com.viewcompose.ui.node.LazyListItemKind =
            com.viewcompose.ui.node.LazyListItemKind.Item,
        sessionFactory: (RenderContainerHandle) -> LazyListItemSession = { _ ->
            error("sessionFactory should not be used in controller tests")
        },
        sessionUpdater: (LazyListItemSession) -> Unit = {},
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = contentRevision,
            contentType = contentType,
            kind = kind,
            sessionStrategy = lazyListItemSessionStrategy(
                create = sessionFactory,
                update = sessionUpdater,
            ),
        )
    }

    private class RecordingSession(
        var label: String,
        private val events: MutableList<String>,
    ) : LazyListItemSession {
        init {
            events += "create:$label"
        }

        override fun render(): Boolean {
            events += "render:$label"
            return true
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

    private class FailingRenderSession(
        private var label: String,
        private val events: MutableList<String>,
        private val shouldFail: () -> Boolean,
    ) : LazyListItemSession {
        init {
            events += "create:$label"
        }

        override fun render(): Boolean {
            events += "render:$label"
            if (shouldFail()) error("render failed")
            return true
        }

        override fun dispose() {
            events += "dispose:$label"
        }

        fun update(next: String) {
            label = next
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

        override fun activate(): Boolean {
            events += "activate:$label"
            return true
        }

        override fun render(): Boolean {
            events += "render:$label"
            return true
        }

        override fun dispose() {
            events += "dispose:$label"
        }

        fun updateLabel(label: String) {
            this.label = label
            events += "update:$label"
        }
    }

    private class ReusableRecordingSession(
        private val label: String,
        private val events: MutableList<String>,
        private val presentation: RecordingPresentation,
    ) : LazyListItemSession {
        init {
            events += "create:$label"
        }

        override fun render(): Boolean {
            events += "render:$label"
            return true
        }

        override fun disposeForReuse(): ReusableItemPresentation {
            events += "detach:$label"
            return presentation
        }

        override fun adoptReusablePresentation(presentation: ReusableItemPresentation): Boolean {
            events += "adopt:$label"
            return presentation === this.presentation
        }

        override fun dispose() {
            events += "dispose:$label"
        }
    }

    private class RecordingPresentation(
        private val events: MutableList<String>,
    ) : ReusableItemPresentation {
        var released = false
            private set

        override fun release() {
            if (released) return
            released = true
            events += "release"
        }
    }
}
