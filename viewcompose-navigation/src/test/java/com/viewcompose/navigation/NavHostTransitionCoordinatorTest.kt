package com.viewcompose.navigation

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
            listOf(
                "root",
                "details",
                "confirmation",
                "login",
            ),
        )
        val controller = NavBackStackController.create(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        ownerStore = NavEntryOwnerStore(application)
        sessionStore = NavDestinationSessionStore(
            context = application,
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

    val latest: RecordingTransitionRun
        get() = runs.last()

    override fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        val run = RecordingTransitionRun(
            transition = transition,
            onCompleted = onCompleted,
        )
        runs += run
        return NavHostTransitionHandle {
            run.cancelled = true
        }
    }

    fun completeLatest() {
        latest.complete()
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
