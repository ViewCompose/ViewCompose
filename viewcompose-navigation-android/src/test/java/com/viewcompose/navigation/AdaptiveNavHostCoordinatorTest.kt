package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Adaptive Nav Host Coordinator 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Adaptive Nav Host Coordinator behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import android.view.View
import android.view.MotionEvent
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
import com.viewcompose.navigation.core.NavResultKey
import com.viewcompose.navigation.core.NavSceneInteraction
import com.viewcompose.navigation.core.NavSceneLayerRole
import com.viewcompose.navigation.core.NavSceneStrategies
import com.viewcompose.navigation.core.NavSceneTransitionPhase
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
                "second-confirmation",
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
            initialPresentationRetentionPolicy = NavPresentationRetentionPolicy.RetainAll,
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
    fun `pane scenes cap active participants before every settled pane becomes interactive`() {
        val root = coordinator.snapshot.top

        val first = coordinator.navigate(
            NavCommand.Push(NavRoute("details")),
        ) as NavHostNavigationResult.Committed
        val details = coordinator.snapshot.top

        assertEquals(
            listOf(root.id),
            first.transition.beforeScene.contentPaneScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            listOf(root.id, details.id),
            first.transition.afterScene.contentPaneScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(setOf(root.id, details.id), first.transition.visibleEntryIds)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertTrue(
            first.transition.scene.entries.all { entry ->
                entry.interaction == NavSceneInteraction.NonInteractive
            },
        )

        transitionDriver.completeLatest()
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))

        val second = coordinator.navigate(
            NavCommand.Push(NavRoute("confirmation")),
        ) as NavHostNavigationResult.Committed
        val confirmation = coordinator.snapshot.top

        assertEquals(
            listOf(root.id, details.id),
            second.transition.beforeScene.contentPaneScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            listOf(details.id, confirmation.id),
            second.transition.afterScene.contentPaneScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            setOf(root.id, details.id, confirmation.id),
            second.transition.visibleEntryIds,
        )
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(confirmation))

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

        val triple = coordinator.updateSceneProjection(
            sceneStrategies = emptyList(),
            strategy = NavPaneStrategies.BackStack,
            maxPaneCount = 3,
        )

        assertEquals(
            listOf(NavPaneRole.Primary, NavPaneRole.Secondary, NavPaneRole.Tertiary),
            triple.contentPaneScene.panes.map { pane -> pane.role },
        )
        assertTrue(originalSessions.all { session -> session.container.visibility == View.VISIBLE })
        assertTrue(
            listOf(root, details, confirmation).all { entry ->
                lifecycle(entry) == NavEntryLifecycleState.Resumed
            },
        )
        assertEquals("dark", renderedThemes.getValue("home").last())

        val single = coordinator.updateSceneProjection(
            sceneStrategies = emptyList(),
            strategy = NavPaneStrategies.BackStack,
            maxPaneCount = 1,
        )

        assertEquals(
            listOf(confirmation.id),
            single.contentPaneScene.panes.map { pane -> pane.entryId },
        )
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

        val scene = coordinator.updateSceneProjection(
            sceneStrategies = emptyList(),
            strategy = NavPaneStrategies.BackStack,
            maxPaneCount = 3,
            onDestinationRefreshFailure = { failure -> refreshFailure = failure },
        )

        assertEquals(
            listOf(details.id, confirmation.id),
            scene.contentPaneScene.panes.map { pane -> pane.entryId },
        )
        assertSame(root, checkNotNull(refreshFailure).failedEntry)
        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertEquals(View.VISIBLE, session(confirmation).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavHostCoordinatorState.Attached, coordinator.state)
    }

    @Test
    fun `overlay scene keeps pane content mounted under one modal lifecycle and result boundary`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        val overlayStrategy = NavSceneStrategies.trailingOverlays { entry ->
            entry.route.name.startsWith("confirmation")
        }
        coordinator.updateSceneProjection(
            sceneStrategies = listOf(overlayStrategy),
            strategy = NavPaneStrategies.BackStack,
            maxPaneCount = 2,
        )

        val pushed = coordinator.navigate(
            NavCommand.Push(NavRoute("confirmation")),
        ) as NavHostNavigationResult.Committed
        val confirmation = coordinator.snapshot.top

        assertEquals(listOf(confirmation.id), pushed.transition.afterScene.overlayEntryIds)
        assertEquals(
            listOf(root.id, details.id),
            pushed.transition.afterScene.contentPaneScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(confirmation))
        assertEquals(NavSceneLayerRole.Overlay, pushed.transition.scene[confirmation.id]?.layerRole)
        assertEquals(setOf(confirmation.id), pushed.transition.scene.entries
            .filter { entry -> entry.visibility == com.viewcompose.navigation.core.NavSceneVisibility.Visible }
            .mapTo(linkedSetOf()) { entry -> entry.entryId })

        transitionDriver.completeLatest()
        measureAndLayout(sessionStore.hostView, width = 900, height = 600)

        assertEquals(450, session(root).container.width)
        assertEquals(450, session(details).container.width)
        assertEquals(900, session(confirmation).container.width)
        assertTrue(session(details).container.background != null)
        assertNull(session(confirmation).container.background)
        assertSame(
            session(confirmation).container,
            sessionStore.hostView.getChildAt(sessionStore.hostView.childCount - 1),
        )
        assertTrue(!session(root).container.acceptsNavigationInput)
        assertTrue(!session(details).container.acceptsNavigationInput)
        assertTrue(session(confirmation).container.acceptsNavigationInput)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, session(details)
            .container.importantForAccessibility)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, session(confirmation)
            .container.importantForAccessibility)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))
        assertEquals(
            NavSceneLayerRole.Overlay,
            checkNotNull(ownerStore.ownerOrNull(confirmation.id)).destinationContext
                .presentation.value.layerRole,
        )

        var coveredTouchCount = 0
        session(details).container.setOnTouchListener { _, _ ->
            coveredTouchCount += 1
            true
        }
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 700f, 300f, 0)
        try {
            assertTrue(sessionStore.hostView.dispatchTouchEvent(down))
        } finally {
            down.recycle()
        }
        assertEquals(0, coveredTouchCount)

        val secondPush = coordinator.navigate(
            NavCommand.Push(NavRoute("confirmation-secondary")),
        ) as NavHostNavigationResult.Committed
        val secondConfirmation = coordinator.snapshot.top
        assertEquals(
            listOf(confirmation.id, secondConfirmation.id),
            secondPush.transition.afterScene.overlayEntryIds,
        )
        transitionDriver.completeLatest()
        measureAndLayout(sessionStore.hostView, width = 900, height = 600)
        assertEquals(900, session(secondConfirmation).container.width)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(confirmation))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(secondConfirmation))
        assertTrue(!session(confirmation).container.acceptsNavigationInput)
        assertTrue(session(secondConfirmation).container.acceptsNavigationInput)
        assertSame(
            session(secondConfirmation).container,
            sessionStore.hostView.getChildAt(sessionStore.hostView.childCount - 1),
        )

        coordinator.navigate(NavCommand.Pop)
        transitionDriver.completeLatest()
        assertNull(sessionStore.sessionOrNull(secondConfirmation.id))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))

        val resultKey = NavResultKey.text("confirmation")
        val popped = coordinator.navigate(
            NavCommand.PopWithResult(resultKey.encode("accepted")),
        )
        assertTrue(popped is NavHostNavigationResult.Committed)
        assertEquals(
            "accepted",
            checkNotNull(ownerStore.ownerOrNull(details.id)).destinationContext.results
                .peek(resultKey),
        )
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Created, lifecycle(confirmation))

        transitionDriver.completeLatest()
        assertNull(sessionStore.sessionOrNull(confirmation.id))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))
    }

    @Test
    fun `modal result consumer rerenders after resume and queues reentrant navigation`() {
        val root = coordinator.snapshot.top
        val resultKey = NavResultKey.text("modal-result")
        val received = mutableListOf<String>()
        var nestedCommandResult: NavHostNavigationResult? = null
        val localSnapshot = captureUiLocalSnapshot()
        val content: NavDestinationContent = { entry ->
            if (entry.id == root.id) {
                NavResultEffect(resultKey) { value ->
                    received += value
                    nestedCommandResult = coordinator.navigate(
                        NavCommand.Push(NavRoute("after-result")),
                    )
                }
            }
            Text(entry.route.name)
        }
        coordinator.updateRenderEnvironment(localSnapshot, content)
        session(root).render(localSnapshot, content)
        coordinator.updateSceneProjection(
            sceneStrategies = listOf(
                NavSceneStrategies.trailingOverlays { entry ->
                    entry.route.name == "confirmation"
                },
            ),
            strategy = NavPaneStrategies.BackStack,
            maxPaneCount = 2,
        )

        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        transitionDriver.completeLatest()
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))

        coordinator.navigate(
            NavCommand.PopWithResult(resultKey.encode("accepted")),
        )
        assertTrue(received.isEmpty())
        assertEquals("accepted", ownerStore.ownerOrNull(root.id)?.destinationContext
            ?.results?.peek(resultKey))

        transitionDriver.completeLatest()

        assertTrue(nestedCommandResult is NavHostNavigationResult.Queued)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        transitionDriver.completeLatest()

        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))
        assertEquals(listOf("accepted"), received)
        assertEquals(0, ownerStore.ownerOrNull(root.id)?.destinationContext?.results?.pendingCount)
        assertEquals("after-result", coordinator.snapshot.top.route.name)
        assertEquals(
            "after-result",
            coordinator.lastTransitionResult?.transition?.incomingEntry?.route?.name,
        )
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
            preview.beforeScene.contentPaneScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            listOf(root.id, details.id),
            preview.afterScene.contentPaneScene.panes.map { pane -> pane.entryId },
        )
        assertEquals(
            setOf(root.id, details.id, confirmation.id),
            preview.visibleEntryIds,
        )
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(confirmation))
        assertTrue(
            preview.scene.entries
                .filter { entry -> entry.entryId in preview.visibleEntryIds }
                .all { entry ->
                    entry.transitionPhase == NavSceneTransitionPhase.PredictivePreview
                },
        )
        assertEquals("dark", renderedThemes.getValue("home").last())

        assertTrue(coordinator.cancelBackPreview(preview.id))
        assertEquals(View.GONE, session(root).container.visibility)
        assertEquals(NavEntryLifecycleState.Created, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))

        val committedPreview = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0.5f)),
        )
        val result = coordinator.commitBackPreview(committedPreview.id)

        assertTrue(result is NavHostNavigationResult.Committed)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Created, lifecycle(confirmation))
        transitionDriver.completeLatest()
        assertEquals(listOf("home", "details"), coordinator.snapshot.entries.map { it.route.name })
        assertEquals(View.VISIBLE, session(root).container.visibility)
        assertEquals(View.VISIBLE, session(details).container.visibility)
        assertNull(sessionStore.sessionOrNull(confirmation.id))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(details))
    }

    @Test
    fun `predictive back previews and restores an overlay through the same scene owners`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        transitionDriver.completeLatest()
        val details = coordinator.snapshot.top
        coordinator.updateSceneProjection(
            sceneStrategies = listOf(
                NavSceneStrategies.trailingOverlays { entry ->
                    entry.route.name == "confirmation"
                },
            ),
            strategy = NavPaneStrategies.BackStack,
            maxPaneCount = 2,
        )
        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        transitionDriver.completeLatest()
        val confirmation = coordinator.snapshot.top

        val cancelled = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0.4f)),
        )
        assertEquals(listOf(confirmation.id), cancelled.beforeScene.overlayEntryIds)
        assertTrue(cancelled.afterScene.overlayEntryIds.isEmpty())
        assertEquals(NavSceneLayerRole.Overlay, cancelled.scene[confirmation.id]?.layerRole)
        assertEquals(NavEntryLifecycleState.Started, lifecycle(root))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(confirmation))
        assertTrue(coordinator.cancelBackPreview(cancelled.id))
        assertEquals(NavEntryLifecycleState.Resumed, lifecycle(confirmation))
        assertEquals(NavEntryLifecycleState.Started, lifecycle(details))

        val committed = checkNotNull(
            coordinator.beginBackPreview(backEvent(progress = 0.7f)),
        )
        assertTrue(coordinator.commitBackPreview(committed.id) is NavHostNavigationResult.Committed)
        transitionDriver.completeLatest()

        assertNull(sessionStore.sessionOrNull(confirmation.id))
        assertEquals(listOf("home", "details"), coordinator.snapshot.entries.map { it.route.name })
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

    private fun measureAndLayout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
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
        val completing = checkNotNull(latestRun)
        completing.complete()
        if (latestRun === completing) {
            latestRun = null
        }
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
