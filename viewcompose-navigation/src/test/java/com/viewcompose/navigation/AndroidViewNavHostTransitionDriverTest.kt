package com.viewcompose.navigation

import android.app.Activity
import android.os.Looper
import android.view.View
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavLaunchMode
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackId
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.captureUiLocalSnapshot
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        val entryIds = ArrayDeque(listOf("root", "details", "confirmation"))
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

        assertEquals(80f, incoming.translationX)
        assertEquals(0.9f, incoming.alpha)
        assertEquals(0.985f, incoming.scaleX)
        assertEquals(0.985f, incoming.scaleY)
        assertTrue(coordinator.activeTransition != null)

        coordinator.cancelTransition(result.transition.id)

        assertNull(coordinator.activeTransition)
        assertEquals(0f, incoming.translationX)
        assertEquals(1f, incoming.alpha)
        assertEquals(1f, incoming.scaleX)
        assertEquals(1f, incoming.scaleY)
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
    fun `redirected navigation continues from the current visual state`() {
        attachAndLayoutHost()

        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        val detailsView = session(details.id).container
        assertEquals(80f, detailsView.translationX)
        assertEquals(0.9f, detailsView.alpha)
        assertEquals(0.985f, detailsView.scaleX)

        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        val confirmationView = session(coordinator.snapshot.top.id).container

        assertEquals(
            NavHostTransitionOutcome.Redirected,
            coordinator.lastTransitionResult?.outcome,
        )
        assertEquals(80f, detailsView.translationX)
        assertEquals(0.9f, detailsView.alpha)
        assertEquals(0.985f, detailsView.scaleX)
        assertEquals(80f, confirmationView.translationX)
        assertEquals(0.9f, confirmationView.alpha)
        assertTrue(coordinator.activeTransition != null)
    }

    @Test
    fun `redirected visual state settles when the next command is a no-op`() {
        attachAndLayoutHost()

        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val detailsView = session(coordinator.snapshot.top.id).container
        assertEquals(80f, detailsView.translationX)

        val result = coordinator.navigate(
            NavCommand.Push(
                route = NavRoute("details"),
                launchMode = NavLaunchMode.SingleTop,
            ),
        )
        assertTrue(result is NavHostNavigationResult.NoChange)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)

        assertNull(coordinator.activeTransition)
        assertEquals(0f, detailsView.translationX)
        assertEquals(1f, detailsView.alpha)
        assertEquals(1f, detailsView.scaleX)
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
        specHolder.value = NavTransitionSpec.Default.copy(
            push = NavDestinationMotionSpec(
                durationMillis = 260L,
                incomingStart = NavDestinationTransform(alpha = 0f),
                outgoingEnd = NavDestinationTransform(alpha = 0f),
            ),
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
    fun `predictive back progress uses eased transforms and cancellation rebounds`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        attachAndLayoutHost()

        val preview = checkNotNull(
            coordinator.beginBackPreview(
                backEvent(
                    progress = 0.5f,
                    swipeEdge = NavHostBackSwipeEdge.Left,
                ),
            ),
        )
        val incoming = session(root.id).container
        val outgoing = session(details.id).container
        val visualProgress = specHolder.value.predictiveBack.progressEasing.transform(0.5f)

        assertTrue(visualProgress > 0.5f)
        assertEquals(-40f * (1f - visualProgress), incoming.translationX, 0.05f)
        assertEquals(0.92f + 0.08f * visualProgress, incoming.alpha, 0.001f)
        assertEquals(100f * visualProgress, outgoing.translationX, 0.05f)
        assertEquals(1f - 0.04f * visualProgress, outgoing.alpha, 0.001f)
        assertEquals(1f - 0.015f * visualProgress, outgoing.scaleX, 0.001f)

        coordinator.cancelBackPreview(preview.id)

        assertEquals(View.GONE, incoming.visibility)
        assertEquals(View.VISIBLE, outgoing.visibility)
        assertTrue(outgoing.translationX > 0f)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertEquals(0f, incoming.translationX)
        assertEquals(1f, incoming.alpha)
        assertEquals(0f, outgoing.translationX)
        assertEquals(1f, outgoing.alpha)
        assertEquals(1f, outgoing.scaleX)
    }

    @Test
    fun `navigation during predictive back preserves the gesture visual state`() {
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        attachAndLayoutHost()
        coordinator.beginBackPreview(
            backEvent(
                progress = 0.5f,
                swipeEdge = NavHostBackSwipeEdge.Left,
            ),
        )
        val detailsView = session(details.id).container
        val gestureTranslation = detailsView.translationX
        assertTrue(gestureTranslation > 0f)

        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))

        assertNull(coordinator.activeBackPreview)
        assertEquals(gestureTranslation, detailsView.translationX)
        assertEquals(
            NavCommand.Push(NavRoute("confirmation")),
            coordinator.activeTransition?.command,
        )
    }

    @Test
    fun `predictive back commit continues native motion and settles pop`() {
        val root = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        attachAndLayoutHost()
        val preview = checkNotNull(
            coordinator.beginBackPreview(
                backEvent(
                    progress = 0.5f,
                    swipeEdge = NavHostBackSwipeEdge.Left,
                ),
            ),
        )

        val result = coordinator.commitBackPreview(
            preview.id,
        ) as NavHostNavigationResult.Committed

        assertEquals(NavCommand.Pop, result.command)
        assertTrue(coordinator.activeTransition != null)
        assertEquals(listOf("home"), coordinator.snapshot.entries.map { it.route.name })

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertNull(coordinator.activeTransition)
        assertNull(sessionStore.sessionOrNull(details.id))
        assertEquals(View.VISIBLE, session(root.id).container.visibility)
        assertEquals(0f, session(root.id).container.translationX)
        assertEquals(1f, session(root.id).container.alpha)
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

    @Test
    fun `motion policy resolves independently for every command family`() {
        val spec = NavTransitionSpec.Default

        assertSame(spec.push, spec.motionFor(NavCommand.Push(NavRoute("details"))))
        assertSame(spec.pop, spec.motionFor(NavCommand.Pop))
        assertSame(spec.replace, spec.motionFor(NavCommand.ReplaceTop(NavRoute("details"))))
        assertSame(spec.reset, spec.motionFor(NavCommand.Reset(NavRoute("home"))))
        assertSame(
            spec.stackSelection,
            spec.motionFor(NavCommand.SelectStack(NavStackId("search"))),
        )
        assertSame(spec.stackSelection, spec.motionFor(NavCommand.PopStackHistory))
        assertSame(
            spec.deepLink,
            spec.motionFor(NavCommand.OpenDeepLink(NavRoute("details"))),
        )
    }

    @Test
    fun `predictive back motion follows swipe edge with layout fallback`() {
        assertEquals(
            1f,
            backPreviewOutgoingDirection(
                swipeEdge = NavHostBackSwipeEdge.Left,
                layoutDirection = View.LAYOUT_DIRECTION_LTR,
            ),
        )
        assertEquals(
            -1f,
            backPreviewOutgoingDirection(
                swipeEdge = NavHostBackSwipeEdge.Right,
                layoutDirection = View.LAYOUT_DIRECTION_LTR,
            ),
        )
        assertEquals(
            1f,
            backPreviewOutgoingDirection(
                swipeEdge = NavHostBackSwipeEdge.None,
                layoutDirection = View.LAYOUT_DIRECTION_LTR,
            ),
        )
        assertEquals(
            -1f,
            backPreviewOutgoingDirection(
                swipeEdge = NavHostBackSwipeEdge.None,
                layoutDirection = View.LAYOUT_DIRECTION_RTL,
            ),
        )
    }

    private fun backEvent(
        progress: Float,
        swipeEdge: NavHostBackSwipeEdge,
    ): NavHostBackEvent {
        return NavHostBackEvent(
            touchX = 8f,
            touchY = 16f,
            progress = progress,
            swipeEdge = swipeEdge,
            frameTimeMillis = 24L,
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
