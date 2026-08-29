package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Android View Nav Host Transition Driver 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Android View Nav Host Transition Driver behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import android.app.Activity
import android.graphics.drawable.ColorDrawable
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
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.captureUiLocalSnapshot
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
        val ownerStore = navigationTestOwnerStore(application)
        sessionStore = NavDestinationSessionStore(
            hostView = NavHostView(application),
            ownerStore = ownerStore,
            initialPresentationRetentionPolicy = NavPresentationRetentionPolicy.RetainAll,
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
    fun `laid out host starts opaque native motion and cancellation resets properties`() {
        attachAndLayoutHost()
        val root = coordinator.snapshot.top

        val result = coordinator.navigate(
            NavCommand.Push(NavRoute("details")),
        ) as NavHostNavigationResult.Committed
        val details = coordinator.snapshot.top
        val incoming = session(details.id).container
        val density = incoming.resources.displayMetrics.density

        assertEquals(96f * density, incoming.translationX)
        assertEquals(0f, incoming.alpha)
        assertEquals(1f, incoming.scaleX)
        assertEquals(1f, incoming.scaleY)
        assertEquals(View.LAYER_TYPE_HARDWARE, incoming.layerType)
        assertEquals(View.LAYER_TYPE_HARDWARE, session(root.id).container.layerType)
        assertTrue(session(details.id).isRenderingActive)
        assertTrue(!session(root.id).isRenderingActive)
        assertTrue(coordinator.activeTransition != null)

        coordinator.cancelTransition(result.transition.id)

        assertNull(coordinator.activeTransition)
        assertEquals(0f, incoming.translationX)
        assertEquals(1f, incoming.alpha)
        assertEquals(1f, incoming.scaleX)
        assertEquals(1f, incoming.scaleY)
        assertEquals(0f, session(root.id).container.translationX)
        assertEquals(1f, session(root.id).container.alpha)
        assertEquals(View.LAYER_TYPE_NONE, incoming.layerType)
        assertEquals(View.LAYER_TYPE_NONE, session(root.id).container.layerType)
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
        val expectedStartOffset = 96f * detailsView.resources.displayMetrics.density
        assertEquals(expectedStartOffset, detailsView.translationX)
        assertEquals(0f, detailsView.alpha)
        assertEquals(1f, detailsView.scaleX)

        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        val confirmationView = session(coordinator.snapshot.top.id).container

        assertEquals(
            NavHostTransitionOutcome.Redirected,
            coordinator.lastTransitionResult?.outcome,
        )
        assertEquals(expectedStartOffset, detailsView.translationX)
        assertEquals(0f, detailsView.alpha)
        assertEquals(1f, detailsView.scaleX)
        assertEquals(expectedStartOffset, confirmationView.translationX)
        assertEquals(0f, confirmationView.alpha)
        assertTrue(coordinator.activeTransition != null)
    }

    @Test
    fun `redirected visual state settles when the next command is a no-op`() {
        attachAndLayoutHost()

        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val detailsView = session(coordinator.snapshot.top.id).container
        assertEquals(
            96f * detailsView.resources.displayMetrics.density,
            detailsView.translationX,
        )

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
            View.LAYER_TYPE_NONE,
            session(coordinator.snapshot.top.id).container.layerType,
        )
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
        val density = incoming.resources.displayMetrics.density
        val outgoingTravel = 1_000f * 0.05f - 8f * density

        assertTrue(visualProgress > 0.5f)
        assertEquals(-96f * density, incoming.translationX, 0.05f)
        assertEquals(1f, incoming.alpha, 0f)
        assertEquals(outgoingTravel * visualProgress, outgoing.translationX, 0.05f)
        assertEquals(1f, outgoing.alpha, 0f)
        assertEquals(1f - 0.1f * visualProgress, outgoing.scaleX, 0.001f)
        assertEquals(1f - 0.1f * visualProgress, incoming.scaleX, 0.001f)
        assertTrue(incoming.foreground is ColorDrawable)
        assertEquals(View.LAYER_TYPE_HARDWARE, incoming.layerType)
        assertEquals(View.LAYER_TYPE_HARDWARE, outgoing.layerType)

        coordinator.updateBackPreview(
            previewId = preview.id,
            event = backEvent(
                progress = 0.5f,
                swipeEdge = NavHostBackSwipeEdge.Left,
                touchY = 400f,
                frameTimeMillis = 48L,
            ),
        )
        assertTrue(outgoing.translationY > 0f)
        assertTrue(incoming.translationY > 0f)

        coordinator.cancelBackPreview(preview.id)

        assertEquals(View.GONE, incoming.visibility)
        assertEquals(View.VISIBLE, outgoing.visibility)
        assertTrue(outgoing.translationX > 0f)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertEquals(0f, incoming.translationX)
        assertEquals(1f, incoming.alpha)
        assertEquals(0f, outgoing.translationX)
        assertEquals(0f, outgoing.translationY)
        assertEquals(1f, outgoing.alpha)
        assertEquals(1f, outgoing.scaleX)
        assertNull(incoming.foreground)
        assertEquals(View.LAYER_TYPE_NONE, incoming.layerType)
        assertEquals(View.LAYER_TYPE_NONE, outgoing.layerType)
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
    fun `navigation during cancel spring redirects from the rebound frame`() {
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        attachAndLayoutHost()
        val preview = checkNotNull(
            coordinator.beginBackPreview(
                backEvent(
                    progress = 0.2f,
                    swipeEdge = NavHostBackSwipeEdge.Left,
                    frameTimeMillis = 100L,
                ),
            ),
        )
        coordinator.updateBackPreview(
            previewId = preview.id,
            event = backEvent(
                progress = 0.65f,
                swipeEdge = NavHostBackSwipeEdge.Left,
                frameTimeMillis = 150L,
            ),
        )
        val detailsView = session(details.id).container

        coordinator.cancelBackPreview(preview.id)
        val reboundTranslation = detailsView.translationX
        assertTrue(reboundTranslation > 0f)

        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        val confirmationView = session(coordinator.snapshot.top.id).container

        assertEquals(reboundTranslation, detailsView.translationX)
        assertTrue(coordinator.activeTransition != null)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertEquals(0f, detailsView.translationX)
        assertEquals(1f, detailsView.alpha)
        assertEquals(0f, confirmationView.translationX)
        assertEquals(1f, confirmationView.alpha)
        assertEquals(View.VISIBLE, confirmationView.visibility)
    }

    @Test
    fun `host destruction cancels a predictive back settle spring`() {
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        attachAndLayoutHost()
        val detailsView = session(coordinator.snapshot.top.id).container
        val preview = checkNotNull(
            coordinator.beginBackPreview(
                backEvent(
                    progress = 0.65f,
                    swipeEdge = NavHostBackSwipeEdge.Left,
                ),
            ),
        )

        coordinator.cancelBackPreview(preview.id)
        assertTrue(detailsView.translationX > 0f)

        coordinator.destroy()

        assertEquals(0f, detailsView.translationX)
        assertEquals(1f, detailsView.alpha)
        assertEquals(1f, detailsView.scaleX)
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
        assertEquals(View.LAYER_TYPE_HARDWARE, session(root.id).container.layerType)
        assertEquals(View.LAYER_TYPE_HARDWARE, session(details.id).container.layerType)
        assertEquals(listOf("home"), coordinator.snapshot.entries.map { it.route.name })

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertNull(coordinator.activeTransition)
        assertNull(sessionStore.sessionOrNull(details.id))
        assertEquals(View.VISIBLE, session(root.id).container.visibility)
        assertEquals(View.LAYER_TYPE_NONE, session(root.id).container.layerType)
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
    fun `default push pop and predictive back mirror current Android system motion`() {
        val spec = NavTransitionSpec.Default

        assertEquals(450L, spec.push.durationMillis)
        assertEquals(96f, spec.push.incomingStart.travelDp)
        assertEquals(0f, spec.push.incomingStart.alpha)
        assertEquals(96f, spec.push.outgoingEnd.travelDp)
        assertEquals(50L, spec.push.incomingAlphaTiming.startDelayMillis)
        assertEquals(83L, spec.push.incomingAlphaTiming.durationMillis)

        assertEquals(450L, spec.pop.durationMillis)
        assertEquals(96f, spec.pop.incomingStart.travelDp)
        assertEquals(96f, spec.pop.outgoingEnd.travelDp)
        assertEquals(0f, spec.pop.outgoingEnd.alpha)
        assertEquals(35L, spec.pop.outgoingAlphaTiming.startDelayMillis)
        assertEquals(83L, spec.pop.outgoingAlphaTiming.durationMillis)
        assertEquals(
            0.4f,
            NavMotionEasing.Emphasized.transform(0.166666f),
            0.001f,
        )

        assertEquals(0.9f, spec.predictiveBack.outgoingEnd.scale)
        assertEquals(0.05f, spec.predictiveBack.outgoingEnd.travelFraction)
        assertEquals(-8f, spec.predictiveBack.outgoingEnd.travelDp)
        assertEquals(96f, spec.predictiveBack.incomingStart.travelDp)
        assertEquals(96f, spec.predictiveBack.incomingEnd.travelDp)
        assertEquals(0.9f, spec.predictiveBack.incomingEnd.scale)
        assertEquals(450L, spec.predictiveBack.commitMotion.durationMillis)
        assertEquals(90L, spec.predictiveBack.commitMotion.outgoingAlphaTiming.durationMillis)
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
        touchY: Float = 16f,
        frameTimeMillis: Long = 24L,
    ): NavHostBackEvent {
        return NavHostBackEvent(
            touchX = 8f,
            touchY = touchY,
            progress = progress,
            swipeEdge = swipeEdge,
            frameTimeMillis = frameTimeMillis,
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
