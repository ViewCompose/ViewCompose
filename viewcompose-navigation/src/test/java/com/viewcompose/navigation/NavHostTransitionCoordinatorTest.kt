package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Nav Host Transition Coordinator 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Host Transition Coordinator behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import android.view.View
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.captureUiLocalSnapshot
import java.util.ArrayDeque
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NavHostTransitionCoordinatorTest {
    private lateinit var ownerStore: NavEntryOwnerStore
    private lateinit var sessionStore: NavDestinationSessionStore
    private lateinit var transitionDriver: RecordingTransitionDriver
    private lateinit var coordinator: TransactionalNavHostCoordinator

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        val entryIds = ArrayDeque(
            buildList {
                add("root")
                add("details")
                add("confirmation")
                add("login")
                repeat(128) { index ->
                    add("stress-$index")
                }
            },
        )
        val controller = NavBackStackController.create(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        ownerStore = NavEntryOwnerStore(application)
        sessionStore = NavDestinationSessionStore(
            hostView = NavHostView(application),
            ownerStore = ownerStore,
        )
        transitionDriver = RecordingTransitionDriver()
        coordinator = TransactionalNavHostCoordinator(
            controller = controller,
            ownerStore = ownerStore,
            sessionStore = sessionStore,
            initialHostLifecycleState = NavHostLifecycleState.Resumed,
            transitionDriver = transitionDriver,
        )
        coordinator.attach(
            localSnapshot = captureUiLocalSnapshot(),
        ) { entry ->
            Text(entry.route.name)
        }
    }

    @After
    fun tearDown() {
        coordinator.destroy()
    }

    @Test
    fun `push retains outgoing page until transition completes`() {
        val root = coordinator.snapshot.top

        val result = coordinator.navigate(
            NavCommand.Push(NavRoute("details")),
        ) as NavHostNavigationResult.Committed
        val details = coordinator.snapshot.top

        assertSame(result.transition, coordinator.activeTransition)
        assertEquals(
            listOf(root.id, details.id),
            result.transition.layerOrder,
        )
        assertEquals(
            setOf(root.id, details.id),
            result.transition.visibleEntryIds,
        )
        assertEquals(View.VISIBLE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))

        transitionDriver.completeLatest()

        assertNull(coordinator.activeTransition)
        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))
        assertEquals(
            NavHostTransitionOutcome.Completed,
            coordinator.lastTransitionResult?.outcome,
        )
    }

    @Test
    fun `pop keeps removed outgoing page alive until transition completes`() {
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top

        val result = coordinator.navigate(NavCommand.Pop) as NavHostNavigationResult.Committed
        val root = coordinator.snapshot.top

        assertEquals(details, result.transition.outgoingEntry)
        assertEquals(root, result.transition.incomingEntry)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(View.VISIBLE, session(root).container.visibility)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))

        transitionDriver.completeLatest()

        assertNull(sessionStore.sessionOrNull(details.id))
        assertNull(ownerStore.ownerOrNull(details.id))
        assertEquals(View.VISIBLE, session(root).container.visibility)
    }

    @Test
    fun `cancel settles the already committed target and cancels visual work`() {
        val root = coordinator.snapshot.top
        val result = coordinator.navigate(
            NavCommand.Push(NavRoute("details")),
        ) as NavHostNavigationResult.Committed
        val details = coordinator.snapshot.top

        assertTrue(coordinator.cancelTransition(result.transition.id))

        assertTrue(transitionDriver.latest.cancelled)
        assertNull(coordinator.activeTransition)
        assertEquals(listOf("home", "details"), coordinator.routeNames())
        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(
            NavHostTransitionOutcome.Cancelled,
            coordinator.lastTransitionResult?.outcome,
        )
        assertFalse(coordinator.cancelTransition(result.transition.id))
    }

    @Test
    fun `new command redirects active transition before starting the next one`() {
        val root = coordinator.snapshot.top
        val firstResult = coordinator.navigate(
            NavCommand.Push(NavRoute("details")),
        ) as NavHostNavigationResult.Committed
        val details = coordinator.snapshot.top
        val firstRun = transitionDriver.latest

        val secondResult = coordinator.navigate(
            NavCommand.Push(NavRoute("confirmation")),
        ) as NavHostNavigationResult.Committed
        val confirmation = coordinator.snapshot.top

        assertTrue(firstRun.cancelled)
        assertEquals(
            NavHostTransitionOutcome.Redirected,
            coordinator.lastTransitionResult?.outcome,
        )
        assertEquals(firstResult.transition, coordinator.lastTransitionResult?.transition)
        assertSame(secondResult.transition, coordinator.activeTransition)
        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(View.VISIBLE, session(confirmation).container.visibility)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))

        firstRun.complete()

        assertSame(secondResult.transition, coordinator.activeTransition)
        transitionDriver.completeLatest()
        assertNull(coordinator.activeTransition)
        assertEquals(View.GONE, session(details).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(details))
    }

    @Test
    fun `host lifecycle caps both pages during a transition`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top

        coordinator.moveHostTo(NavHostLifecycleState.Created)

        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Created, lifecycle(details))

        coordinator.moveHostTo(NavHostLifecycleState.Started)

        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))

        transitionDriver.completeLatest()

        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
    }

    @Test
    fun `reset retains old stack but exposes only outgoing and incoming pages`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        transitionDriver.completeLatest()
        val confirmation = coordinator.snapshot.top

        coordinator.navigate(NavCommand.Reset(NavRoute("login")))
        val login = coordinator.snapshot.top

        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(View.GONE, session(details).container.visibility)
        assertEquals(View.VISIBLE, session(confirmation).container.visibility)
        assertEquals(View.VISIBLE, session(login).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Created, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(confirmation))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(login))

        transitionDriver.completeLatest()

        listOf(root, details, confirmation).forEach { removed ->
            assertNull(sessionStore.sessionOrNull(removed.id))
            assertNull(ownerStore.ownerOrNull(removed.id))
        }
        assertEquals(1, coordinator.hostView.childCount)
    }

    @Test
    fun `predictive back preview exposes previous page without mutating stack`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top

        val preview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0f)),
        )

        assertSame(preview, coordinator.activeBackPreview)
        assertEquals(listOf("home", "details"), coordinator.routeNames())
        assertEquals(root, preview.incomingEntry)
        assertEquals(details, preview.outgoingEntry)
        assertEquals(setOf(root.id, details.id), preview.visibleEntryIds)
        assertEquals(View.VISIBLE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))

        assertTrue(
            coordinator.updateBackPreview(
                previewId = preview.id,
                event = backEvent(progress = 0.4f),
            ),
        )
        assertEquals(0.4f, transitionDriver.latestBackPreview.events.last().progress)
        assertEquals(listOf("home", "details"), coordinator.routeNames())
    }

    @Test
    fun `predictive back cancellation restores settled page`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        val preview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0.3f)),
        )
        val previewRun = transitionDriver.latestBackPreview

        assertTrue(coordinator.cancelBackPreview(preview.id))

        assertTrue(previewRun.cancelled)
        assertNull(coordinator.activeBackPreview)
        assertEquals(listOf("home", "details"), coordinator.routeNames())
        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))
        assertFalse(coordinator.cancelBackPreview(preview.id))
    }

    @Test
    fun `predictive back commit mutates stack only on commit and reuses preview motion`() {
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        val preview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0f)),
        )
        val previewRun = transitionDriver.latestBackPreview
        coordinator.updateBackPreview(
            previewId = preview.id,
            event = backEvent(progress = 0.6f),
        )

        assertEquals(listOf("home", "details"), coordinator.routeNames())

        val result = coordinator.commitBackPreview(
            preview.id,
        ) as NavHostNavigationResult.Committed
        val root = coordinator.snapshot.top

        assertEquals(listOf("home"), coordinator.routeNames())
        assertEquals(NavCommand.Pop, result.command)
        assertSame(result.transition, coordinator.activeTransition)
        assertSame(result.transition, previewRun.committedTransition)
        assertEquals(View.VISIBLE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))

        transitionDriver.completeLatest()

        assertNull(coordinator.activeTransition)
        assertNull(sessionStore.sessionOrNull(details.id))
        assertNull(ownerStore.ownerOrNull(details.id))
        assertEquals(View.VISIBLE, session(root).container.visibility)
    }

    @Test
    fun `root cannot begin predictive back`() {
        assertNull(coordinator.beginBackPreview(backEvent(progress = 0f)))
        assertNull(coordinator.activeBackPreview)
        assertTrue(transitionDriver.backPreviews.isEmpty())
        assertEquals(listOf("home"), coordinator.routeNames())
    }

    @Test
    fun `application navigation cancels active predictive preview before next transaction`() {
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val preview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0.2f)),
        )
        val previewRun = transitionDriver.latestBackPreview

        val result = coordinator.navigate(
            NavCommand.Push(NavRoute("confirmation")),
        )

        assertTrue(result is NavHostNavigationResult.Committed)
        assertTrue(previewRun.cancelled)
        assertNull(coordinator.activeBackPreview)
        assertFalse(coordinator.updateBackPreview(preview.id, backEvent(progress = 0.8f)))
        assertEquals(listOf("home", "details", "confirmation"), coordinator.routeNames())
    }

    @Test
    fun `host lifecycle caps both pages during predictive preview`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        coordinator.beginBackPreview(backEvent(progress = 0.1f))

        coordinator.moveHostTo(NavHostLifecycleState.Created)

        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Created, lifecycle(details))

        coordinator.moveHostTo(NavHostLifecycleState.Started)

        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
    }

    @Test
    fun `lifecycle change and navigation redirect predictive preview transactionally`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        val preview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0.4f)),
        )
        val previewRun = transitionDriver.latestBackPreview

        coordinator.moveHostTo(NavHostLifecycleState.Created)
        val result = coordinator.navigate(
            NavCommand.Push(NavRoute("confirmation")),
        ) as NavHostNavigationResult.Committed
        val confirmation = coordinator.snapshot.top

        assertTrue(previewRun.cancelled)
        assertNull(coordinator.activeBackPreview)
        assertFalse(
            coordinator.updateBackPreview(
                previewId = preview.id,
                event = backEvent(progress = 0.9f),
            ),
        )
        assertSame(result.transition, coordinator.activeTransition)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Created, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Created, lifecycle(confirmation))

        coordinator.moveHostTo(NavHostLifecycleState.Resumed)

        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))

        transitionDriver.completeLatest()

        assertNull(coordinator.activeTransition)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))
        assertEquals(
            listOf("home", "details", "confirmation"),
            coordinator.routeNames(),
        )
    }

    @Test
    fun `repeated lifecycle changes and redirected transactions ignore stale completions`() {
        val root = coordinator.snapshot.top

        repeat(100) { index ->
            coordinator.moveHostTo(NavHostLifecycleState.Created)
            val push = coordinator.navigate(
                NavCommand.Push(NavRoute("stress-route-$index")),
            ) as NavHostNavigationResult.Committed
            val pushed = coordinator.snapshot.top
            val stalePushRun = transitionDriver.latest

            coordinator.moveHostTo(NavHostLifecycleState.Started)
            val pop = coordinator.navigate(
                NavCommand.Pop,
            ) as NavHostNavigationResult.Committed

            assertTrue("iteration=$index", stalePushRun.cancelled)
            assertEquals(
                "iteration=$index",
                NavHostTransitionOutcome.Redirected,
                coordinator.lastTransitionResult?.outcome,
            )
            assertSame(pop.transition, coordinator.activeTransition)
            assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
            assertEquals(NavEntryLifecycleState.Started, lifecycle(pushed))

            coordinator.moveHostTo(NavHostLifecycleState.Created)
            assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
            assertEquals(NavEntryLifecycleState.Created, lifecycle(pushed))

            coordinator.moveHostTo(NavHostLifecycleState.Resumed)
            assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))
            assertEquals(NavEntryLifecycleState.Started, lifecycle(pushed))

            stalePushRun.complete()
            assertSame(
                "iteration=$index",
                pop.transition,
                coordinator.activeTransition,
            )

            transitionDriver.completeLatest()

            assertNull("iteration=$index", coordinator.activeTransition)
            assertEquals(
                "iteration=$index",
                listOf("home"),
                coordinator.routeNames(),
            )
            assertNull(sessionStore.sessionOrNull(pushed.id))
            assertNull(ownerStore.ownerOrNull(pushed.id))
            assertEquals(1, coordinator.hostView.childCount)
            assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))
            assertEquals(
                "iteration=$index",
                NavHostTransitionOutcome.Completed,
                coordinator.lastTransitionResult?.outcome,
            )
            assertSame(push.transition, stalePushRun.transition)
        }
    }

    @Test
    fun `destroy cancels active transition and tears down every retained page`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top

        coordinator.destroy()

        assertTrue(transitionDriver.latest.cancelled)
        assertEquals(NavHostCoordinatorState.Destroyed, coordinator.state)
        assertEquals(
            NavHostTransitionOutcome.HostDestroyed,
            coordinator.lastTransitionResult?.outcome,
        )
        assertNull(coordinator.activeTransition)
        assertNull(sessionStore.sessionOrNull(root.id))
        assertNull(sessionStore.sessionOrNull(details.id))
        assertNull(ownerStore.ownerOrNull(root.id))
        assertNull(ownerStore.ownerOrNull(details.id))
        assertEquals(0, coordinator.hostView.childCount)
    }

    @Test
    fun `destroy cancels predictive preview and tears down every page`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        coordinator.beginBackPreview(backEvent(progress = 0.5f))
        val previewRun = transitionDriver.latestBackPreview

        coordinator.destroy()

        assertTrue(previewRun.cancelled)
        assertEquals(NavHostCoordinatorState.Destroyed, coordinator.state)
        assertNull(coordinator.activeBackPreview)
        assertNull(sessionStore.sessionOrNull(root.id))
        assertNull(sessionStore.sessionOrNull(details.id))
        assertNull(ownerStore.ownerOrNull(root.id))
        assertNull(ownerStore.ownerOrNull(details.id))
        assertEquals(0, coordinator.hostView.childCount)
    }

    private fun backEvent(
        progress: Float,
        swipeEdge: NavHostBackSwipeEdge = NavHostBackSwipeEdge.Left,
    ): NavHostBackEvent {
        return NavHostBackEvent(
            touchX = 12f,
            touchY = 24f,
            progress = progress,
            swipeEdge = swipeEdge,
            frameTimeMillis = 32L,
        )
    }

    private fun session(entry: NavEntry): NavDestinationSession {
        return checkNotNull(sessionStore.sessionOrNull(entry.id))
    }

    private fun lifecycle(entry: NavEntry): NavEntryLifecycleState {
        return checkNotNull(ownerStore.ownerOrNull(entry.id)).entryLifecycleState
    }

    private fun TransactionalNavHostCoordinator.routeNames(): List<String> {
        return snapshot.entries.map { entry -> entry.route.name }
    }
}

private class RecordingTransitionDriver : NavHostTransitionDriver {
    val runs = mutableListOf<RecordingTransitionRun>()
    val backPreviews = mutableListOf<RecordingBackPreviewRun>()

    val latest: RecordingTransitionRun
        get() = runs.last()

    val latestBackPreview: RecordingBackPreviewRun
        get() = backPreviews.last()

    override fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        return recordTransition(
            transition = transition,
            onCompleted = onCompleted,
        )
    }

    override fun startBackPreview(
        preview: NavHostBackPreview,
        initialEvent: NavHostBackEvent,
    ): NavHostBackPreviewHandle {
        return RecordingBackPreviewRun(
            preview = preview,
            initialEvent = initialEvent,
            commitTransition = ::recordTransition,
        ).also(backPreviews::add)
    }

    private fun recordTransition(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        val run = RecordingTransitionRun(transition, onCompleted)
        runs += run
        return NavHostTransitionHandle {
            run.cancelled = true
        }
    }

    fun completeLatest() {
        latest.complete()
    }
}

private class RecordingBackPreviewRun(
    val preview: NavHostBackPreview,
    initialEvent: NavHostBackEvent,
    private val commitTransition: (
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ) -> NavHostTransitionHandle,
) : NavHostBackPreviewHandle {
    val events = mutableListOf(initialEvent)
    var cancelled: Boolean = false
    var committedTransition: NavHostTransition? = null

    override fun update(event: NavHostBackEvent) {
        events += event
    }

    override fun cancel() {
        cancelled = true
    }

    override fun commit(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        committedTransition = transition
        return commitTransition(transition, onCompleted)
    }
}

private class RecordingTransitionRun(
    val transition: NavHostTransition,
    private val onCompleted: () -> Unit,
) {
    var cancelled: Boolean = false

    fun complete() {
        onCompleted()
    }
}
