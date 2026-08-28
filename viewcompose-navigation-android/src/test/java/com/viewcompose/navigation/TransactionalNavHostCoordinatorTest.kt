package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Transactional Nav Host Coordinator 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Transactional Nav Host Coordinator behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

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
import com.viewcompose.navigation.core.NavRootBackBehavior
import com.viewcompose.navigation.core.NavStackConfiguration
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.navigation.core.NavStackSpec
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiLocalSnapshot
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.captureUiLocalSnapshot
import com.viewcompose.ui.foundation.uiLocalOf
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
        ownerStore = navigationTestOwnerStore(application)
        sessionStore = NavDestinationSessionStore(
            hostView = NavHostView(application),
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
    fun `attach at initialized keeps page initialized until host is created`() {
        coordinator.moveHostTo(NavHostLifecycleState.Initialized)

        val result = attach()
        val root = coordinator.snapshot.top
        val owner = checkNotNull(ownerStore.ownerOrNull(root.id))

        assertTrue(result is NavHostAttachmentResult.Attached)
        assertEquals(NavHostCoordinatorState.Attached, coordinator.state)
        assertEquals(NavEntryLifecycleState.Initialized, owner.entryLifecycleState)
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)

        coordinator.moveHostTo(NavHostLifecycleState.Created)

        assertEquals(NavEntryLifecycleState.Created, owner.entryLifecycleState)
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)
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
    fun `pop refreshes retained page with the latest local snapshot before reveal`() {
        val renderedThemes = mutableMapOf<String, MutableList<String>>()
        val content: NavDestinationContent = { entry ->
            renderedThemes.getOrPut(entry.route.name, ::mutableListOf) +=
                UiLocals.current(TestThemeLocal)
            Text(entry.route.name)
        }
        attach(
            localSnapshot = themeSnapshot("light"),
            content = content,
        )
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        coordinator.updateRenderEnvironment(
            localSnapshot = themeSnapshot("dark"),
            content = content,
        )

        val committed = coordinator.navigate(NavCommand.Pop)

        assertTrue(committed is NavHostNavigationResult.Committed)
        assertEquals("dark", renderedThemes.getValue("home").last())
        assertEquals(listOf("home"), coordinator.routeNames())
    }

    @Test
    fun `pop refresh failure preserves the committed stack and visible page`() {
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

        val home = coordinator.snapshot.entries.first()
        val homeSession = checkNotNull(sessionStore.sessionOrNull(home.id))
        val detailsSession = checkNotNull(sessionStore.sessionOrNull(details.id))

        val failed = coordinator.navigate(NavCommand.Pop)

        assertTrue(failed is NavHostNavigationResult.Failed)
        failed as NavHostNavigationResult.Failed
        assertEquals(NavHostFailurePhase.DestinationRefresh, failed.phase)
        assertSame(home, failed.failedEntry)
        assertFalse(failed.stackCommitted)
        assertEquals(listOf("home", "details"), coordinator.routeNames())
        assertSame(homeSession, sessionStore.sessionOrNull(home.id))
        assertSame(detailsSession, sessionStore.sessionOrNull(details.id))
        assertEquals(View.GONE, homeSession.container.visibility)
        assertEquals(View.VISIBLE, detailsSession.container.visibility)
        assertEquals(NavHostCoordinatorState.Attached, coordinator.state)
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

    @Test
    fun `tab stacks retain independent sessions state and lifecycle`() {
        configureMultiStackCoordinator()
        attach()
        val homeRoot = controller.stackSnapshot(HomeStack).top
        val searchRoot = controller.stackSnapshot(SearchStack).top
        val homeSession = checkNotNull(sessionStore.sessionOrNull(homeRoot.id))
        val searchSession = checkNotNull(sessionStore.sessionOrNull(searchRoot.id))

        assertEquals(2, coordinator.hostView.childCount)
        assertEquals(View.VISIBLE, homeSession.container.visibility)
        assertEquals(View.GONE, searchSession.container.visibility)
        assertEquals(
            NavEntryLifecycleState.Resumed,
            checkNotNull(ownerStore.ownerOrNull(homeRoot.id)).entryLifecycleState,
        )
        assertEquals(
            NavEntryLifecycleState.Created,
            checkNotNull(ownerStore.ownerOrNull(searchRoot.id)).entryLifecycleState,
        )

        coordinator.navigate(NavCommand.Push(NavRoute("home-details")))
        val homeDetails = controller.snapshot().top
        val homeDetailsSession = checkNotNull(sessionStore.sessionOrNull(homeDetails.id))
        coordinator.navigate(NavCommand.SelectStack(SearchStack))

        assertEquals(SearchStack, controller.stackStateSnapshot().activeStackId)
        assertEquals(View.VISIBLE, searchSession.container.visibility)
        assertEquals(View.GONE, homeDetailsSession.container.visibility)
        assertSame(homeSession, sessionStore.sessionOrNull(homeRoot.id))
        assertEquals(
            NavEntryLifecycleState.Created,
            checkNotNull(ownerStore.ownerOrNull(homeDetails.id)).entryLifecycleState,
        )
        assertEquals(
            NavEntryLifecycleState.Resumed,
            checkNotNull(ownerStore.ownerOrNull(searchRoot.id)).entryLifecycleState,
        )

        coordinator.navigate(NavCommand.Push(NavRoute("search-result")))
        val searchResult = controller.snapshot().top
        coordinator.navigate(NavCommand.SelectStack(HomeStack))

        assertSame(homeDetails, coordinator.snapshot.top)
        assertEquals(View.VISIBLE, homeDetailsSession.container.visibility)
        assertEquals(
            listOf("search", "search-result"),
            controller.stackSnapshot(SearchStack).entries.map { entry -> entry.route.name },
        )
        assertEquals(
            NavEntryLifecycleState.Created,
            checkNotNull(ownerStore.ownerOrNull(searchResult.id)).entryLifecycleState,
        )
    }

    @Test
    fun `tab switch refresh failure preserves active stack and retained sessions`() {
        configureMultiStackCoordinator()
        var failSearch = false
        attach { entry ->
            if (failSearch && entry.route.name == "search") {
                error("search refresh failed")
            }
            Text(entry.route.name)
        }
        val homeRoot = controller.snapshot().top
        val searchRoot = controller.stackSnapshot(SearchStack).top
        val searchSession = checkNotNull(sessionStore.sessionOrNull(searchRoot.id))
        failSearch = true

        val result = coordinator.navigate(NavCommand.SelectStack(SearchStack))

        assertTrue(result is NavHostNavigationResult.Failed)
        result as NavHostNavigationResult.Failed
        assertEquals(NavHostFailurePhase.DestinationRefresh, result.phase)
        assertSame(searchRoot, result.failedEntry)
        assertFalse(result.stackCommitted)
        assertEquals(HomeStack, controller.stackStateSnapshot().activeStackId)
        assertSame(homeRoot, coordinator.snapshot.top)
        assertSame(searchSession, sessionStore.sessionOrNull(searchRoot.id))
        assertEquals(View.VISIBLE, checkNotNull(sessionStore.sessionOrNull(homeRoot.id)).container.visibility)
        assertEquals(View.GONE, searchSession.container.visibility)
        assertEquals(
            NavEntryLifecycleState.Created,
            checkNotNull(ownerStore.ownerOrNull(searchRoot.id)).entryLifecycleState,
        )
    }

    @Test
    fun `tab switch refreshes retained target with latest local snapshot`() {
        configureMultiStackCoordinator()
        val renderedThemes = mutableMapOf<String, MutableList<String>>()
        val content: NavDestinationContent = { entry ->
            renderedThemes.getOrPut(entry.route.name, ::mutableListOf) +=
                UiLocals.current(TestThemeLocal)
            Text(entry.route.name)
        }
        attach(
            localSnapshot = themeSnapshot("light"),
            content = content,
        )
        coordinator.updateRenderEnvironment(
            localSnapshot = themeSnapshot("dark"),
            content = content,
        )

        val result = coordinator.navigate(NavCommand.SelectStack(SearchStack))

        assertTrue(result is NavHostNavigationResult.Committed)
        assertEquals(SearchStack, controller.stackStateSnapshot().activeStackId)
        assertEquals("dark", renderedThemes.getValue("search").last())
    }

    @Test
    fun `predictive system back can cancel or return to previous tab`() {
        configureMultiStackCoordinator(
            rootBackBehavior = NavRootBackBehavior.PreviousStack,
        )
        attach()
        coordinator.navigate(NavCommand.SelectStack(SearchStack))
        val homeRoot = controller.stackSnapshot(HomeStack).top
        val searchRoot = controller.snapshot().top

        val cancelledPreview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0f)),
        )
        assertEquals(NavCommand.PopStackHistory, cancelledPreview.command)
        assertSame(searchRoot, cancelledPreview.outgoingEntry)
        assertSame(homeRoot, cancelledPreview.incomingEntry)
        coordinator.cancelBackPreview(cancelledPreview.id)

        assertEquals(SearchStack, controller.stackStateSnapshot().activeStackId)
        assertEquals(listOf(HomeStack), controller.stackStateSnapshot().selectionHistory)

        val committedPreview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0.5f)),
        )
        val result = coordinator.commitBackPreview(committedPreview.id)

        assertTrue(result is NavHostNavigationResult.Committed)
        assertEquals(HomeStack, controller.stackStateSnapshot().activeStackId)
        assertTrue(controller.stackStateSnapshot().selectionHistory.isEmpty())
        assertEquals(View.VISIBLE, checkNotNull(sessionStore.sessionOrNull(homeRoot.id)).container.visibility)
        assertEquals(View.GONE, checkNotNull(sessionStore.sessionOrNull(searchRoot.id)).container.visibility)
    }

    @Test
    fun `predictive back refresh failure keeps committed scene and reports the destination`() {
        configureMultiStackCoordinator(
            rootBackBehavior = NavRootBackBehavior.PreviousStack,
        )
        var failHomeRefresh = false
        attach { entry ->
            if (failHomeRefresh && entry.route.name == "home") {
                error("home refresh failed")
            }
            Text(entry.route.name)
        }
        coordinator.navigate(NavCommand.SelectStack(SearchStack))
        val homeRoot = controller.stackSnapshot(HomeStack).top
        val searchRoot = controller.snapshot().top
        var refreshFailure: NavHostDestinationRefreshFailure? = null
        failHomeRefresh = true

        val preview = coordinator.beginBackPreview(
            event = backEvent(progress = 0f),
            onDestinationRefreshFailure = { failure -> refreshFailure = failure },
        )

        assertNull(preview)
        assertNull(coordinator.activeBackPreview)
        assertSame(homeRoot, checkNotNull(refreshFailure).failedEntry)
        assertEquals(SearchStack, controller.stackStateSnapshot().activeStackId)
        assertEquals(View.VISIBLE, checkNotNull(sessionStore.sessionOrNull(searchRoot.id)).container.visibility)
        assertEquals(View.GONE, checkNotNull(sessionStore.sessionOrNull(homeRoot.id)).container.visibility)
        assertEquals(NavHostCoordinatorState.Attached, coordinator.state)
    }

    private fun configureMultiStackCoordinator(
        rootBackBehavior: NavRootBackBehavior = NavRootBackBehavior.Delegate,
    ) {
        coordinator.destroy()
        val application = RuntimeEnvironment.getApplication()
        val ids = ArrayDeque(
            listOf(
                "home-root",
                "search-root",
                "home-details",
                "search-result",
            ),
        )
        controller = NavBackStackController.create(
            configuration = NavStackConfiguration(
                initialStackId = HomeStack,
                stacks = listOf(
                    NavStackSpec(HomeStack, NavRoute("home")),
                    NavStackSpec(SearchStack, NavRoute("search")),
                ),
                rootBackBehavior = rootBackBehavior,
            ),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(ids.removeFirst())
            },
        )
        ownerStore = navigationTestOwnerStore(application)
        sessionStore = NavDestinationSessionStore(
            hostView = NavHostView(application),
            ownerStore = ownerStore,
        )
        coordinator = TransactionalNavHostCoordinator(
            controller = controller,
            ownerStore = ownerStore,
            sessionStore = sessionStore,
            initialHostLifecycleState = NavHostLifecycleState.Resumed,
        )
    }

    private fun backEvent(progress: Float): NavHostBackEvent {
        return NavHostBackEvent(
            touchX = 0f,
            touchY = 0f,
            progress = progress,
            swipeEdge = NavHostBackSwipeEdge.Left,
            frameTimeMillis = 0L,
        )
    }

    private fun attach(
        localSnapshot: UiLocalSnapshot = captureUiLocalSnapshot(),
        content: NavDestinationContent = { entry -> Text(entry.route.name) },
    ): NavHostAttachmentResult {
        return coordinator.attach(
            localSnapshot = localSnapshot,
            content = content,
        )
    }

    private fun themeSnapshot(theme: String): UiLocalSnapshot {
        var snapshot: UiLocalSnapshot? = null
        UiTreeBuilder().ProvideLocal(TestThemeLocal, theme) {
            snapshot = captureUiLocalSnapshot()
        }
        return checkNotNull(snapshot)
    }

    private fun TransactionalNavHostCoordinator.routeNames(): List<String> {
        return snapshot.entries.map { entry -> entry.route.name }
    }

    private companion object {
        val TestThemeLocal = uiLocalOf(debugName = "NavigationTestTheme") { "system" }
        val HomeStack = NavStackId("home")
        val SearchStack = NavStackId("search")
    }
}
