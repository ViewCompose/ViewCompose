package com.viewcompose.navigation

/*
 * Test responsibility: covers shared-content ownership through committed navigation, predictive
 * Back cancellation/completion, and redirect cleanup using real destination render sessions.
 */

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.viewcompose.navigation.core.NavBackStackController
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.captureUiLocalSnapshot
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.sharedBounds
import com.viewcompose.ui.shared.SHARED_CONTENT_TAG_KEY
import com.viewcompose.ui.shared.SharedContentKey
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
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class AndroidSharedNavTransitionIntegrationTest {
    private lateinit var sessionStore: NavDestinationSessionStore
    private lateinit var coordinator: TransactionalNavHostCoordinator
    private lateinit var activityController: ActivityController<Activity>
    private val sharedKey = SharedContentKey("shared-title")
    private var transitionSpec = NavTransitionSpec.Default

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java)
            .setup()
            .visible()
        activityController = activity
        val entryIds = ArrayDeque(listOf("home", "details", "confirmation"))
        val controller = NavBackStackController.create(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory { NavEntryId(entryIds.removeFirst()) },
        )
        val ownerStore = NavEntryOwnerStore(activity.get().application)
        sessionStore = NavDestinationSessionStore(
            hostView = NavHostView(activity.get()),
            ownerStore = ownerStore,
        )
        coordinator = TransactionalNavHostCoordinator(
            controller = controller,
            ownerStore = ownerStore,
            sessionStore = sessionStore,
            initialHostLifecycleState = NavHostLifecycleState.Resumed,
            transitionDriver = AndroidViewNavHostTransitionDriver(
                sessionStore = sessionStore,
                specProvider = { transitionSpec },
            ),
        )
        coordinator.attach(localSnapshot = captureUiLocalSnapshot()) { entry ->
            Text(
                text = entry.route.name,
                modifier = Modifier.sharedBounds(sharedKey),
            )
        }
        activity.get().setContentView(sessionStore.hostView)
        layoutHost()
    }

    @After
    fun tearDown() {
        coordinator.destroy()
        activityController.pause().stop().destroy()
    }

    @Test
    fun `committed push suppresses the matched endpoints and restores the final target`() {
        val home = coordinator.snapshot.top

        val result = coordinator.navigate(
            NavCommand.Push(NavRoute("details")),
        ) as NavHostNavigationResult.Committed
        val details = coordinator.snapshot.top
        layoutHost()
        sessionStore.hostView.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(View.INVISIBLE, sharedEndpoint(home.id).visibility)
        assertEquals(0f, sharedEndpoint(details.id).alpha)

        coordinator.cancelTransition(result.transition.id)

        assertEquals(1f, sharedEndpoint(home.id).alpha)
        assertEquals(View.VISIBLE, sharedEndpoint(home.id).visibility)
        assertEquals(1f, sharedEndpoint(details.id).alpha)
        assertEquals(View.GONE, session(home.id).container.visibility)
        assertEquals(View.VISIBLE, session(details.id).container.visibility)
    }

    @Test
    fun `committed pop uses the reversed pair and releases the removed session`() {
        val home = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        val details = coordinator.snapshot.top
        layoutHost()

        coordinator.navigate(NavCommand.Pop)
        layoutHost()
        sessionStore.hostView.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(View.INVISIBLE, sharedEndpoint(details.id).visibility)
        assertEquals(0f, sharedEndpoint(home.id).alpha)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertNull(sessionStore.sessionOrNull(details.id))
        assertEquals(View.VISIBLE, sharedEndpoint(home.id).visibility)
        assertEquals(1f, sharedEndpoint(home.id).alpha)
    }

    @Test
    fun `replace pairs the old and new top then releases the replaced session`() {
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        val details = coordinator.snapshot.top
        layoutHost()

        coordinator.navigate(NavCommand.ReplaceTop(NavRoute("settings")))
        val settings = coordinator.snapshot.top
        layoutHost()
        sessionStore.hostView.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(View.INVISIBLE, sharedEndpoint(details.id).visibility)
        assertEquals(0f, sharedEndpoint(settings.id).alpha)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertNull(sessionStore.sessionOrNull(details.id))
        assertEquals(View.VISIBLE, sharedEndpoint(settings.id).visibility)
        assertEquals(1f, sharedEndpoint(settings.id).alpha)
    }

    @Test
    fun `predictive Back cancellation restores source and commit releases the removed session`() {
        val home = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        val details = coordinator.snapshot.top
        layoutHost()

        val cancelPreview = checkNotNull(
            coordinator.beginBackPreview(
                backEvent(progress = 0.55f, frameTimeMillis = 32L),
            ),
        )
        layoutHost()
        sessionStore.hostView.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(View.INVISIBLE, sharedEndpoint(details.id).visibility)
        assertEquals(0f, sharedEndpoint(home.id).alpha)

        coordinator.cancelBackPreview(cancelPreview.id)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertEquals(1f, sharedEndpoint(details.id).alpha)
        assertEquals(View.VISIBLE, session(details.id).container.visibility)
        assertEquals(View.GONE, session(home.id).container.visibility)

        val commitPreview = checkNotNull(
            coordinator.beginBackPreview(
                backEvent(progress = 0.65f, frameTimeMillis = 64L),
            ),
        )
        layoutHost()
        sessionStore.hostView.viewTreeObserver.dispatchOnPreDraw()
        coordinator.commitBackPreview(commitPreview.id)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)

        assertNull(sessionStore.sessionOrNull(details.id))
        assertEquals(1f, sharedEndpoint(home.id).alpha)
        assertEquals(View.VISIBLE, session(home.id).container.visibility)
    }

    @Test
    fun `redirect disposes old snapshots before the next destination pair is prepared`() {
        val home = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        layoutHost()
        sessionStore.hostView.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(View.INVISIBLE, sharedEndpoint(home.id).visibility)
        assertEquals(0f, sharedEndpoint(details.id).alpha)

        coordinator.navigate(NavCommand.Push(NavRoute("confirmation")))
        val confirmation = coordinator.snapshot.top

        assertEquals(1f, sharedEndpoint(details.id).alpha)
        layoutHost()
        sessionStore.hostView.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(
            "details(alpha=${sharedEndpoint(details.id).alpha},visibility=${sharedEndpoint(details.id).visibility}), " +
                "home(alpha=${sharedEndpoint(home.id).alpha},visibility=${sharedEndpoint(home.id).visibility})",
            View.INVISIBLE,
            sharedEndpoint(details.id).visibility,
        )
        assertEquals(0f, sharedEndpoint(confirmation.id).alpha)
        assertTrue(coordinator.activeTransition != null)
    }

    @Test
    fun `disabled motion completes without capturing or mutating shared endpoints`() {
        val home = coordinator.snapshot.top
        transitionSpec = NavTransitionSpec.None

        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        layoutHost()
        sessionStore.hostView.viewTreeObserver.dispatchOnPreDraw()

        assertEquals(View.VISIBLE, sharedEndpoint(home.id).visibility)
        assertEquals(1f, sharedEndpoint(home.id).alpha)
        assertEquals(View.VISIBLE, sharedEndpoint(details.id).visibility)
        assertEquals(1f, sharedEndpoint(details.id).alpha)
        assertNull(coordinator.activeTransition)
    }

    @Test
    fun `host destruction restores coordinator-owned endpoint properties`() {
        val home = coordinator.snapshot.top
        coordinator.navigate(NavCommand.Push(NavRoute("details")))
        val details = coordinator.snapshot.top
        layoutHost()
        sessionStore.hostView.viewTreeObserver.dispatchOnPreDraw()
        val source = sharedEndpoint(home.id)
        val target = sharedEndpoint(details.id)
        assertEquals(View.INVISIBLE, source.visibility)
        assertEquals(0f, target.alpha)

        coordinator.destroy()

        assertEquals(View.VISIBLE, source.visibility)
        assertEquals(1f, source.alpha)
        assertEquals(View.VISIBLE, target.visibility)
        assertEquals(1f, target.alpha)
    }

    private fun backEvent(
        progress: Float,
        frameTimeMillis: Long,
    ): NavHostBackEvent {
        return NavHostBackEvent(
            touchX = 8f,
            touchY = 16f,
            progress = progress,
            swipeEdge = NavHostBackSwipeEdge.Left,
            frameTimeMillis = frameTimeMillis,
        )
    }

    private fun sharedEndpoint(entryId: NavEntryId): View {
        var match: View? = null
        session(entryId).container.forEachDepthFirst { view ->
            if (view.getTag(SHARED_CONTENT_TAG_KEY) != null) match = view
        }
        return checkNotNull(match)
    }

    private fun session(entryId: NavEntryId): NavDestinationSession {
        return checkNotNull(sessionStore.sessionOrNull(entryId))
    }

    private fun layoutHost() {
        val exactSize = View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY)
        sessionStore.hostView.measure(exactSize, exactSize)
        sessionStore.hostView.layout(0, 0, 1_000, 1_000)
        assertTrue(sessionStore.hostView.isAttachedToWindow)
    }
}

private fun View.forEachDepthFirst(block: (View) -> Unit) {
    block(this)
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).forEachDepthFirst(block)
        }
    }
}
