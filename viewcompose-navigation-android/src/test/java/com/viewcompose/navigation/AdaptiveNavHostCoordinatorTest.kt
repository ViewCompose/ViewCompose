package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Adaptive Nav Host Coordinator 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Adaptive Nav Host Coordinator behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import android.view.View
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavPaneRole
import com.viewcompose.navigation.core.NavPaneStrategies
import com.viewcompose.navigation.core.NavRoute
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AdaptiveNavHostCoordinatorTest {
    private lateinit var ownerStore: NavEntryOwnerStore
    private lateinit var sessionStore: NavDestinationSessionStore
    private lateinit var transitionDriver: ControlledTransitionDriver
    private lateinit var coordinator: TransactionalNavHostCoordinator

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        val entryIds = ArrayDeque(
            listOf(
                "root",
                "details",
                "confirmation",
            ),
        )
        val controller = NavBackStackController.create(
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
        transitionDriver = ControlledTransitionDriver()
        coordinator = TransactionalNavHostCoordinator(
            controller = controller,
            ownerStore = ownerStore,
            sessionStore = sessionStore,
            initialHostLifecycleState = NavHostLifecycleState.Resumed,
            transitionDriver = transitionDriver,
            initialPaneStrategy = NavPaneStrategies.BackStack,
            initialMaxPaneCount = 2,
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
    fun `pane scenes drive settled visibility and every pane is interactive`() {
        val root = coordinator.snapshot.top

        val first = coordinator.navigate(
            NavCommand.Push(NavRoute("details")),
        ) as NavHostNavigationResult.Committed
        val details = coordinator.snapshot.top

        assertEquals(
            listOf(root.id),
            first.transition.beforeScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            listOf(root.id, details.id),
            first.transition.afterScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(setOf(root.id, details.id), first.transition.visibleEntryIds)
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))

        transitionDriver.completeLatest()

        val second = coordinator.navigate(
            NavCommand.Push(NavRoute("confirmation")),
        ) as NavHostNavigationResult.Committed
        val confirmation = coordinator.snapshot.top

        assertEquals(
            listOf(root.id, details.id),
            second.transition.beforeScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            listOf(details.id, confirmation.id),
            second.transition.afterScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            setOf(root.id, details.id, confirmation.id),
            second.transition.visibleEntryIds,
        )
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))

        transitionDriver.completeLatest()

        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(View.VISIBLE, session(confirmation).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
    }

    @Test
    fun `pane count changes republish one committed stack without recreating pages`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        transitionDriver.completeLatest()
        val confirmation = coordinator.snapshot.top
        val originalSessions = listOf(
            session(root),
            session(details),
            session(confirmation),
        )
        val renderedThemes = mutableMapOf<String, MutableList<String>>()
        coordinator.updateRenderEnvironment(
            localSnapshot = themeSnapshot("dark"),
        ) { entry ->
            renderedThemes.getOrPut(entry.route.name) { mutableListOf() } +=
                UiLocals.current(TestThemeLocal)
            Text(entry.route.name)
        }

        val triple = coordinator.updatePaneStrategy(
            strategy = NavPaneStrategies.BackStack,
            maxPaneCount = 3,
        )

        assertEquals(
            listOf(NavPaneRole.Primary, NavPaneRole.Secondary, NavPaneRole.Tertiary),
            triple.panes.map { pane -> pane.role },
        )
        assertTrue(originalSessions.all { session -> session.container.visibility == View.VISIBLE })
        assertTrue(
            listOf(root, details, confirmation).all { entry ->
                lifecycle(entry) == NavEntryLifecycleState.Resumed
            },
        )
        assertEquals("dark", renderedThemes.getValue("home").last())

        val single = coordinator.updatePaneStrategy(
            strategy = NavPaneStrategies.BackStack,
            maxPaneCount = 1,
        )

        assertEquals(listOf(confirmation.id), single.panes.map { pane -> pane.entryId })
        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(View.GONE, session(details).container.visibility)
        assertEquals(View.VISIBLE, session(confirmation).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Created, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))
        assertEquals(originalSessions, listOf(session(root), session(details), session(confirmation)))
    }

    @Test
    fun `pane expansion refresh failure preserves the previous scene`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        transitionDriver.completeLatest()
        val confirmation = coordinator.snapshot.top
        coordinator.updateRenderEnvironment(
            localSnapshot = captureUiLocalSnapshot(),
        ) { entry ->
            if (entry.id == root.id) {
                error("root refresh failed")
            }
            Text(entry.route.name)
        }
        var refreshFailure: NavHostDestinationRefreshFailure? = null

        val scene = coordinator.updatePaneStrategy(
            strategy = NavPaneStrategies.BackStack,
            maxPaneCount = 3,
            onDestinationRefreshFailure = { failure -> refreshFailure = failure },
        )

        assertEquals(listOf(details.id, confirmation.id), scene.panes.map { pane -> pane.entryId })
        assertSame(root, checkNotNull(refreshFailure).failedEntry)
        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(View.VISIBLE, session(confirmation).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavHostCoordinatorState.Attached, coordinator.state)
    }

    @Test
    fun `predictive back previews the before and after pane scenes atomically`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        transitionDriver.completeLatest()
        val confirmation = coordinator.snapshot.top
        val renderedThemes = mutableMapOf<String, MutableList<String>>()
        coordinator.updateRenderEnvironment(
            localSnapshot = themeSnapshot("dark"),
        ) { entry ->
            renderedThemes.getOrPut(entry.route.name) { mutableListOf() } +=
                UiLocals.current(TestThemeLocal)
            Text(entry.route.name)
        }

        val preview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0f)),
        )

        assertEquals(
            listOf(details.id, confirmation.id),
            preview.beforeScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            listOf(root.id, details.id),
            preview.afterScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            setOf(root.id, details.id, confirmation.id),
            preview.visibleEntryIds,
        )
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))
        assertEquals("dark", renderedThemes.getValue("home").last())

        assertTrue(coordinator.cancelBackPreview(preview.id))
        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))

        val committedPreview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0.5f)),
        )
        val result = coordinator.commitBackPreview(committedPreview.id)

        assertTrue(result is NavHostNavigationResult.Committed)
        transitionDriver.completeLatest()
        assertEquals(listOf("home", "details"), coordinator.snapshot.entries.map { it.route.name })
        assertEquals(View.VISIBLE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertNull(sessionStore.sessionOrNull(confirmation.id))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))
    }

    private fun session(entry: NavEntry): NavDestinationSession {
        return checkNotNull(sessionStore.sessionOrNull(entry.id))
    }

    private fun lifecycle(entry: NavEntry): NavEntryLifecycleState {
        return checkNotNull(ownerStore.ownerOrNull(entry.id)).entryLifecycleState
    }

    private fun themeSnapshot(theme: String): UiLocalSnapshot {
        var snapshot: UiLocalSnapshot? = null
        UiTreeBuilder().ProvideLocal(TestThemeLocal, theme) {
            snapshot = captureUiLocalSnapshot()
        }
        return checkNotNull(snapshot)
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

    private companion object {
        val TestThemeLocal = uiLocalOf(debugName = "AdaptiveNavigationTestTheme") { "system" }
    }
}

private class ControlledTransitionDriver : NavHostTransitionDriver {
    private var latestRun: ControlledTransitionRun? = null

    override fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        return ControlledTransitionRun(onCompleted).also { run ->
            latestRun = run
        }
    }

    override fun startBackPreview(
        preview: NavHostBackPreview,
        initialEvent: NavHostBackEvent,
    ): NavHostBackPreviewHandle {
        return object : NavHostBackPreviewHandle {
            override fun update(event: NavHostBackEvent) = Unit

            override fun cancel() = Unit

            override fun commit(
                transition: NavHostTransition,
                onCompleted: () -> Unit,
            ): NavHostTransitionHandle {
                return ControlledTransitionRun(onCompleted).also { run ->
                    latestRun = run
                }
            }
        }
    }

    fun completeLatest() {
        checkNotNull(latestRun).complete()
        latestRun = null
    }
}

private class ControlledTransitionRun(
    private val onCompleted: () -> Unit,
) : NavHostTransitionHandle {
    private var terminal = false

    override fun cancel() {
        terminal = true
    }

    fun complete() {
        check(!terminal)
        terminal = true
        onCompleted()
    }
}
