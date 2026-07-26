package com.viewcompose.navigation

import android.view.View
import androidx.lifecycle.Lifecycle
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavNoChangeReason
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
class TransactionalNavHostCoordinatorTest {
    private lateinit var controller: NavBackStackController
    private lateinit var ownerStore: NavEntryOwnerStore
    private lateinit var sessionStore: NavDestinationSessionStore
    private lateinit var coordinator: TransactionalNavHostCoordinator
    private lateinit var entryIds: ArrayDeque<String>

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        entryIds = ArrayDeque(
            listOf(
                "root",
                "details",
                "confirmation",
                "settings",
                "login",
                "broken",
                "good",
            ),
        )
        controller = NavBackStackController.create(
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
        coordinator = TransactionalNavHostCoordinator(
            controller = controller,
            ownerStore = ownerStore,
            sessionStore = sessionStore,
            initialHostLifecycleState = NavHostLifecycleState.Resumed,
        )
    }

    @After
    fun tearDown() {
        coordinator.destroy()
    }

    @Test
    fun `attach renders current stack and publishes one resumed visible page`() {
        val result = attach()
        val root = coordinator.snapshot.top
        val session = checkNotNull(sessionStore.sessionOrNull(root.id))

        assertTrue(result is NavHostAttachmentResult.Attached)
        assertEquals(NavHostCoordinatorState.Attached, coordinator.state)
        assertEquals(1, coordinator.hostView.childCount)
        assertEquals(View.VISIBLE, session.container.visibility)
        assertEquals(
            NavEntryLifecycleState.Resumed,
            checkNotNull(ownerStore.ownerOrNull(root.id)).entryLifecycleState,
        )
    }

    @Test
    fun `failed initial page leaves host detached and can attach again`() {
        val failed = coordinator.attach(
            localSnapshot = captureUiLocalSnapshot(),
        ) {
            error("initial page failed")
        }

        assertTrue(failed is NavHostAttachmentResult.Failed)
        assertEquals(NavHostCoordinatorState.Detached, coordinator.state)
        assertEquals(0, coordinator.hostView.childCount)
        assertNull(ownerStore.ownerOrNull(coordinator.snapshot.top.id))

        val attached = attach()

        assertTrue(attached is NavHostAttachmentResult.Attached)
        assertEquals(NavHostCoordinatorState.Attached, coordinator.state)
        assertEquals(1, coordinator.hostView.childCount)
    }

    @Test
    fun `host operations reject background threads`() {
        val snapshot = captureUiLocalSnapshot()
        var failure: Throwable? = null
        val worker = Thread {
            failure = runCatching {
                coordinator.attach(snapshot) { entry ->
                    Text(entry.route.name)
                }
            }.exceptionOrNull()
        }

        worker.start()
        worker.join()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("main thread"))
        assertEquals(NavHostCoordinatorState.Detached, coordinator.state)
    }

    @Test
    fun `push publishes stack only after candidate page commits`() {
        attach()
        val root = coordinator.snapshot.top

        val result = coordinator.navigate(NavCommand.Push(NavRoute("details")))

        assertTrue(result is NavHostNavigationResult.Committed)
        assertEquals(listOf("home", "details"), coordinator.routeNames())
        val details = coordinator.snapshot.top
        assertEquals(View.GONE, checkNotNull(sessionStore.sessionOrNull(root.id)).container.visibility)
        assertEquals(
            View.VISIBLE,
            checkNotNull(sessionStore.sessionOrNull(details.id)).container.visibility,
        )
        assertEquals(
            NavEntryLifecycleState.Created,
            checkNotNull(ownerStore.ownerOrNull(root.id)).entryLifecycleState,
        )
        assertEquals(
            NavEntryLifecycleState.Resumed,
            checkNotNull(ownerStore.ownerOrNull(details.id)).entryLifecycleState,
        )
    }

    @Test
    fun `failed push keeps old stack page and lifecycle then next command can commit`() {
        attach { entry ->
            if (entry.route.name == "broken") {
                error("broken page")
            }
            Text(entry.route.name)
        }
        val root = coordinator.snapshot.top

        val failed = coordinator.navigate(NavCommand.Push(NavRoute("broken")))

        assertTrue(failed is NavHostNavigationResult.Failed)
        failed as NavHostNavigationResult.Failed
        assertFalse(failed.stackCommitted)
        assertEquals(NavHostFailurePhase.DestinationPreparation, failed.phase)
        assertEquals(listOf("home"), coordinator.routeNames())
        assertEquals(View.VISIBLE, checkNotNull(sessionStore.sessionOrNull(root.id)).container.visibility)
        assertEquals(
            NavEntryLifecycleState.Resumed,
            checkNotNull(ownerStore.ownerOrNull(root.id)).entryLifecycleState,
        )
        assertNull(ownerStore.ownerOrNull(checkNotNull(failed.failedEntry).id))

        val committed = coordinator.navigate(NavCommand.Push(NavRoute("good")))

        assertTrue(committed is NavHostNavigationResult.Committed)
        assertEquals(listOf("home", "good"), coordinator.routeNames())
        assertEquals("confirmation", coordinator.snapshot.top.id.value)
    }

    @Test
    fun `command queued by failed candidate is discarded with that candidate`() {
        var queuedResult: NavHostNavigationResult? = null
        attach { entry ->
            if (entry.route.name == "broken") {
                queuedResult = coordinator.navigate(
                    NavCommand.Push(NavRoute("good")),
                )
                error("broken after queue")
            }
            Text(entry.route.name)
        }

        val failed = coordinator.navigate(NavCommand.Push(NavRoute("broken")))

        assertTrue(failed is NavHostNavigationResult.Failed)
        assertTrue(queuedResult is NavHostNavigationResult.Queued)
        assertEquals(listOf("home"), coordinator.routeNames())
        assertEquals(NavHostCoordinatorState.Attached, coordinator.state)
    }

    @Test
    fun `pop refresh failure preserves current page and can be retried`() {
        var failHomeRefresh = false
        attach { entry ->
            if (entry.route.name == "home" && failHomeRefresh) {
                error("home refresh failed")
            }
            Text(entry.route.name)
        }
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        failHomeRefresh = true

        val failed = coordinator.navigate(NavCommand.Pop)

        assertTrue(failed is NavHostNavigationResult.Failed)
        failed as NavHostNavigationResult.Failed
        assertEquals(NavHostFailurePhase.DestinationRefresh, failed.phase)
        assertFalse(failed.stackCommitted)
        assertEquals(listOf("home", "details"), coordinator.routeNames())
        assertEquals(
            View.VISIBLE,
            checkNotNull(sessionStore.sessionOrNull(details.id)).container.visibility,
        )
        assertEquals(
            NavEntryLifecycleState.Resumed,
            checkNotNull(ownerStore.ownerOrNull(details.id)).entryLifecycleState,
        )

        failHomeRefresh = false
        val committed = coordinator.navigate(NavCommand.Pop)

        assertTrue(committed is NavHostNavigationResult.Committed)
        assertEquals(listOf("home"), coordinator.routeNames())
        assertNull(sessionStore.sessionOrNull(details.id))
        assertNull(ownerStore.ownerOrNull(details.id))
    }

    @Test
    fun `replace top removes old page but retains lower stack entries`() {
        attach()
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top

        val result = coordinator.navigate(NavCommand.ReplaceTop(NavRoute("settings")))

        assertTrue(result is NavHostNavigationResult.Committed)
        assertEquals(listOf("home", "settings"), coordinator.routeNames())
        assertNull(sessionStore.sessionOrNull(details.id))
        assertNull(ownerStore.ownerOrNull(details.id))
        assertEquals(
            NavEntryLifecycleState.Resumed,
            checkNotNull(ownerStore.ownerOrNull(coordinator.snapshot.top.id)).entryLifecycleState,
        )
    }

    @Test
    fun `reset disposes every previous page in top first order`() {
        attach()
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        val confirmation = coordinator.snapshot.top

        val result = coordinator.navigate(NavCommand.Reset(NavRoute("login")))

        assertTrue(result is NavHostNavigationResult.Committed)
        assertEquals(listOf("login"), coordinator.routeNames())
        listOf(root, details, confirmation).forEach { removed ->
            assertNull(sessionStore.sessionOrNull(removed.id))
            assertNull(ownerStore.ownerOrNull(removed.id))
        }
        assertEquals(1, coordinator.hostView.childCount)
    }

    @Test
    fun `root pop returns no change without touching page ownership`() {
        attach()
        val root = coordinator.snapshot.top
        val session = checkNotNull(sessionStore.sessionOrNull(root.id))

        val result = coordinator.navigate(NavCommand.Pop)

        assertTrue(result is NavHostNavigationResult.NoChange)
        result as NavHostNavigationResult.NoChange
        assertEquals(NavNoChangeReason.CannotPopRoot, result.reason)
        assertSame(session, sessionStore.sessionOrNull(root.id))
        assertEquals(listOf("home"), coordinator.routeNames())
    }

    @Test
    fun `navigation requested while candidate renders is queued and serialized`() {
        var requestedConfirmation = false
        var reentrantResult: NavHostNavigationResult? = null
        attach { entry ->
            if (entry.route.name == "details" && !requestedConfirmation) {
                requestedConfirmation = true
                reentrantResult = coordinator.navigate(
                    NavCommand.Push(NavRoute("confirmation")),
                )
            }
            Text(entry.route.name)
        }

        val result = coordinator.navigate(NavCommand.Push(NavRoute("details")))

        assertTrue(result is NavHostNavigationResult.Committed)
        assertTrue(reentrantResult is NavHostNavigationResult.Queued)
        assertEquals(listOf("home", "details", "confirmation"), coordinator.routeNames())
        assertEquals(
            NavEntryLifecycleState.Resumed,
            checkNotNull(ownerStore.ownerOrNull(coordinator.snapshot.top.id)).entryLifecycleState,
        )
    }

    @Test
    fun `platform lifecycle caps page and host destruction clears all resources`() {
        coordinator.moveHostTo(NavHostLifecycleState.Created)
        attach()
        val root = coordinator.snapshot.top
        val owner = checkNotNull(ownerStore.ownerOrNull(root.id))
        assertEquals(NavEntryLifecycleState.Created, owner.entryLifecycleState)

        coordinator.moveHostTo(NavHostLifecycleState.Started)
        assertEquals(NavEntryLifecycleState.Started, owner.entryLifecycleState)

        coordinator.moveHostTo(NavHostLifecycleState.Resumed)
        assertEquals(NavEntryLifecycleState.Resumed, owner.entryLifecycleState)

        coordinator.moveHostTo(NavHostLifecycleState.Destroyed)

        assertEquals(NavHostCoordinatorState.Destroyed, coordinator.state)
        assertEquals(0, coordinator.hostView.childCount)
        assertNull(ownerStore.ownerOrNull(root.id))
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    private fun attach(
        content: NavDestinationContent = { entry -> Text(entry.route.name) },
    ): NavHostAttachmentResult {
        return coordinator.attach(
            localSnapshot = captureUiLocalSnapshot(),
            content = content,
        )
    }

    private fun TransactionalNavHostCoordinator.routeNames(): List<String> {
        return snapshot.entries.map { entry -> entry.route.name }
    }
}
