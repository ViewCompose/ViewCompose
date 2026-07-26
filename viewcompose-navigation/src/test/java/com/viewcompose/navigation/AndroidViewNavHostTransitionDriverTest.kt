package com.viewcompose.navigation

import android.app.Activity
import android.os.Looper
import android.view.View
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.captureUiLocalSnapshot
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class AndroidViewNavHostTransitionDriverTest {
    private lateinit var sessionStore: NavDestinationSessionStore
    private lateinit var coordinator: TransactionalNavHostCoordinator
    private lateinit var specHolder: TransitionSpecHolder
    private var activityController: ActivityController<Activity>? = null

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        val entryIds = ArrayDeque(listOf("root", "details"))
        val controller = NavBackStackController.create(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        val ownerStore = NavEntryOwnerStore(application)
        sessionStore = NavDestinationSessionStore(
            hostView = NavHostView(application),
            ownerStore = ownerStore,
        )
        specHolder = TransitionSpecHolder(NavTransitionSpec.Default)
        coordinator = TransactionalNavHostCoordinator(
            controller = controller,
            ownerStore = ownerStore,
            sessionStore = sessionStore,
            initialHostLifecycleState = NavHostLifecycleState.Resumed,
            transitionDriver = AndroidViewNavHostTransitionDriver(
                sessionStore = sessionStore,
                specProvider = { specHolder.value },
            ),
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
        activityController?.pause()?.stop()?.destroy()
    }

    @Test
    fun `unlaid out host completes transition synchronously`() {
        val root = coordinator.snapshot.top

        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top

        assertNull(coordinator.activeTransition)
        assertEquals(View.GONE, session(root.id).container.visibility)
        assertEquals(View.VISIBLE, session(details.id).container.visibility)
        assertEquals(
            NavHostTransitionOutcome.Completed,
            coordinator.lastTransitionResult?.outcome,
        )
    }

    @Test
    fun `laid out host starts native motion and cancellation resets properties`() {
        attachAndLayoutHost()
        val root = coordinator.snapshot.top

        val result = coordinator.navigate(
            NavCommand.Push(NavRoute("details")),
        ) as NavHostNavigationResult.Committed
        val details = coordinator.snapshot.top
        val incoming = session(details.id).container

        assertEquals(120f, incoming.translationX)
        assertEquals(0f, incoming.alpha)
        assertTrue(coordinator.activeTransition != null)

        coordinator.cancelTransition(result.transition.id)

        assertNull(coordinator.activeTransition)
        assertEquals(0f, incoming.translationX)
        assertEquals(1f, incoming.alpha)
        assertEquals(0f, session(root.id).container.translationX)
        assertEquals(1f, session(root.id).container.alpha)
        assertEquals(View.GONE, session(root.id).container.visibility)
        assertEquals(View.VISIBLE, incoming.visibility)
    }

    @Test
    fun `none spec completes even when host is laid out`() {
        attachAndLayoutHost()
        specHolder.value = NavTransitionSpec.None

        coordinator.navigate(NavCommand.Push(NavRoute("details")))

        assertNull(coordinator.activeTransition)
        assertEquals(
            NavHostTransitionOutcome.Completed,
            coordinator.lastTransitionResult?.outcome,
        )
    }

    @Test
    fun `native motion completion settles transition`() {
        attachAndLayoutHost()

        coordinator.navigate(NavCommand.Push(NavRoute("details")))

        assertTrue(coordinator.activeTransition != null)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        assertNull(coordinator.activeTransition)
        assertEquals(
            NavHostTransitionOutcome.Completed,
            coordinator.lastTransitionResult?.outcome,
        )
    }

    @Test
    fun `zero travel still supports fade only motion`() {
        attachAndLayoutHost()
        specHolder.value = NavTransitionSpec(
            durationMillis = 260L,
            travelFraction = 0f,
            fadeEnabled = true,
        )

        val result = coordinator.navigate(
            NavCommand.Push(NavRoute("details")),
        ) as NavHostNavigationResult.Committed
        val incoming = session(coordinator.snapshot.top.id).container

        assertEquals(0f, incoming.translationX)
        assertEquals(0f, incoming.alpha)
        assertTrue(coordinator.activeTransition != null)

        coordinator.cancelTransition(result.transition.id)
        assertEquals(1f, incoming.alpha)
    }

    @Test
    fun `motion direction follows command and host layout direction`() {
        assertEquals(
            1f,
            navTransitionDirection(
                command = NavCommand.Push(NavRoute("details")),
                layoutDirection = View.LAYOUT_DIRECTION_LTR,
            ),
        )
        assertEquals(
            -1f,
            navTransitionDirection(
                command = NavCommand.Pop,
                layoutDirection = View.LAYOUT_DIRECTION_LTR,
            ),
        )
        assertEquals(
            -1f,
            navTransitionDirection(
                command = NavCommand.Push(NavRoute("details")),
                layoutDirection = View.LAYOUT_DIRECTION_RTL,
            ),
        )
        assertEquals(
            1f,
            navTransitionDirection(
                command = NavCommand.Pop,
                layoutDirection = View.LAYOUT_DIRECTION_RTL,
            ),
        )
    }

    private fun session(entryId: NavEntryId): NavDestinationSession {
        return checkNotNull(sessionStore.sessionOrNull(entryId))
    }

    private fun attachAndLayoutHost() {
        val controller = Robolectric.buildActivity(Activity::class.java)
            .setup()
            .visible()
        activityController = controller
        controller.get().setContentView(sessionStore.hostView)
        val exactSize = View.MeasureSpec.makeMeasureSpec(
            1_000,
            View.MeasureSpec.EXACTLY,
        )
        sessionStore.hostView.measure(exactSize, exactSize)
        sessionStore.hostView.layout(0, 0, 1_000, 1_000)
        assertTrue(sessionStore.hostView.isAttachedToWindow)
    }
}
