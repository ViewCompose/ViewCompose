package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Android Nav Host Back Adapter 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Android Nav Host Back Adapter behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import androidx.navigationevent.testing.TestNavigationEventDispatcherOwner
import com.viewcompose.host.android.RenderSession
import com.viewcompose.host.android.renderInto
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.Text
import com.viewcompose.viewmodel.ProvideViewModelStoreOwner
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidNavHostBackAdapterTest {
    @Test
    fun `platform back pops navigation then delegates at root`() {
        val fixture = renderHost()

        fixture.navigationEventInput.backCompleted()

        assertEquals(1, fixture.navigationFallbackCount)
        assertEquals(listOf("home"), fixture.controller.routeNames())

        fixture.controller.navigate(NavRoute("details"))
        fixture.navigationEventInput.backCompleted()

        assertEquals(1, fixture.navigationFallbackCount)
        assertEquals(listOf("home"), fixture.controller.routeNames())

        fixture.navigationEventInput.backCompleted()

        assertEquals(2, fixture.navigationFallbackCount)
        fixture.session.dispose()
    }

    @Test
    fun `disabled system back delegates without changing navigation`() {
        val fixture = renderHost(systemBackEnabled = false)
        fixture.controller.navigate(NavRoute("details"))

        fixture.navigationEventInput.backCompleted()

        assertEquals(1, fixture.navigationFallbackCount)
        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        fixture.session.dispose()
    }

    @Test
    fun `changing system back enablement keeps host identity and restores plan ownership`() {
        val enabled = mutableStateOf(true)
        val fixture = renderHost(systemBackEnabledState = enabled)
        fixture.controller.navigate(NavRoute("details"))

        enabled.value = false
        fixture.session.render()
        fixture.navigationEventInput.backCompleted()

        assertEquals(1, fixture.navigationFallbackCount)
        assertEquals(listOf("home", "details"), fixture.controller.routeNames())

        enabled.value = true
        fixture.session.render()
        fixture.navigationEventInput.backCompleted()

        assertEquals(1, fixture.navigationFallbackCount)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        fixture.session.dispose()
    }

    @Test
    fun `changing explicit host key rebuilds runtime without losing controller ownership`() {
        val hostKey = mutableStateOf("first")
        val fixture = renderHost(hostKeyState = hostKey)
        fixture.controller.navigate(NavRoute("details"))
        val originalHost = fixture.navHostView

        hostKey.value = "second"
        fixture.session.render()

        val replacementHost = fixture.root.requireNavHostView()
        assertTrue(replacementHost !== originalHost)
        fixture.navigationEventInput.backCompleted()
        assertEquals(0, fixture.navigationFallbackCount)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        fixture.session.dispose()
    }

    @Test
    fun `platform back delegates while host lifecycle is below started`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))
        fixture.owner.moveTo(Lifecycle.State.CREATED)

        fixture.navigationEventInput.backCompleted()

        assertEquals(1, fixture.navigationFallbackCount)
        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        fixture.session.dispose()
    }

    @Test
    fun `lifecycle stop cancels direct preview and restart restores ownership`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))
        fixture.navigationEventInput.backStarted(navigationEvent(progress = 0f))
        fixture.navigationEventInput.backProgressed(navigationEvent(progress = 0.5f))
        assertEquals(2, fixture.navHostView.visiblePageCount())

        fixture.owner.moveTo(Lifecycle.State.CREATED)

        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.visiblePageCount())
        fixture.navigationEventInput.backCancelled()
        fixture.navigationEventInput.backCompleted()
        assertEquals(1, fixture.navigationFallbackCount)

        fixture.owner.moveTo(Lifecycle.State.RESUMED)
        fixture.navigationEventInput.backCompleted()

        assertEquals(listOf("home"), fixture.controller.routeNames())
        fixture.session.dispose()
    }

    @Test
    fun `disabling system back cancels direct predictive preview`() {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val hostView = NavHostView(application)
        root.addView(hostView)
        val owner = TestBackOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val navigationEvents = navigationEventFixture()
        root.setViewTreeNavigationEventDispatcherOwner(navigationEvents.owner)
        var previewActive = false
        var cancellations = 0
        var ordinaryBacks = 0
        val previewId = NavHostBackPreviewId(1L)
        val adapter = AndroidNavHostBackAdapter(
            hostView = hostView,
            canHandleBack = { true },
            isPreviewActive = { previewActive },
            onBackPressed = { ordinaryBacks += 1 },
            onBackStarted = {
                previewActive = true
                previewId
            },
            onBackProgressed = { _, _ -> },
            onBackCancelled = {
                previewActive = false
                cancellations += 1
            },
            onBackCommitted = {},
        )
        adapter.attach(owner)
        navigationEvents.input.backStarted(navigationEvent(progress = 0f))

        adapter.updateEnabled(false)

        assertEquals(1, cancellations)
        assertTrue(!previewActive)
        navigationEvents.input.backCompleted()
        assertEquals(0, ordinaryBacks)
        assertEquals(0, navigationEvents.owner.onBackCompletedFallbackInvocations)
        navigationEvents.input.backCompleted()
        assertEquals(1, navigationEvents.owner.onBackCompletedFallbackInvocations)
        adapter.destroy()
    }

    @Test
    fun `predictive back cancellation keeps stack and restores settled visibility`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))

        fixture.navigationEventInput.backStarted(navigationEvent(progress = 0f))
        fixture.navigationEventInput.backProgressed(navigationEvent(progress = 0.5f))

        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        assertEquals(2, fixture.navHostView.visiblePageCount())

        fixture.navigationEventInput.backCancelled()

        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.visiblePageCount())
        assertEquals(View.VISIBLE, fixture.navHostView.getChildAt(1).visibility)
        fixture.session.dispose()
    }

    @Test
    fun `predictive back commit pops once and next back delegates`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))

        fixture.navigationEventInput.backStarted(navigationEvent(progress = 0f))
        fixture.navigationEventInput.backProgressed(navigationEvent(progress = 0.7f))
        fixture.navigationEventInput.backCompleted()

        assertEquals(0, fixture.navigationFallbackCount)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.childCount)

        fixture.navigationEventInput.backCompleted()

        assertEquals(1, fixture.navigationFallbackCount)
        fixture.session.dispose()
    }

    @Test
    fun `disposing host removes platform callback`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))

        fixture.session.dispose()
        fixture.navigationEventInput.backCompleted()

        assertEquals(1, fixture.navigationFallbackCount)
        assertTrue(!fixture.controller.isAttached)
    }

    @Test
    fun `NavigationEvent owner takes precedence over legacy view tree owner`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))

        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, fixture.owner.fallbackCount)
        assertEquals(listOf("home", "details"), fixture.controller.routeNames())

        fixture.navigationEventInput.backCompleted()

        assertEquals(listOf("home"), fixture.controller.routeNames())
        fixture.session.dispose()
    }

    @Test
    fun `explicit host replacement re-queries a changed NavigationEvent owner`() {
        val hostKey = mutableStateOf("first")
        val fixture = renderHost(hostKeyState = hostKey)
        fixture.controller.navigate(NavRoute("details"))
        val replacement = navigationEventFixture()
        fixture.root.setViewTreeNavigationEventDispatcherOwner(replacement.owner)

        hostKey.value = "second"
        fixture.session.render()
        fixture.navigationEventInput.backCompleted()

        assertEquals(1, fixture.navigationFallbackCount)
        assertEquals(listOf("home", "details"), fixture.controller.routeNames())

        replacement.input.backCompleted()

        assertEquals(listOf("home"), fixture.controller.routeNames())
        fixture.session.dispose()
    }

    @Test
    fun `legacy view tree owner remains a fallback without NavigationEvent ownership`() {
        val fixture = renderHost(installNavigationEventOwner = false)
        fixture.controller.navigate(NavRoute("details"))

        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(0, fixture.owner.fallbackCount)
        assertEquals(listOf("home"), fixture.controller.routeNames())

        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, fixture.owner.fallbackCount)
        fixture.session.dispose()
    }

    private fun renderHost(
        systemBackEnabled: Boolean = true,
        systemBackEnabledState: State<Boolean>? = null,
        hostKeyState: State<String>? = null,
        installNavigationEventOwner: Boolean = true,
    ): BackHostFixture {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val owner = TestBackOwner().apply {
            moveTo(Lifecycle.State.RESUMED)
        }
        root.setViewTreeOnBackPressedDispatcherOwner(owner)
        val navigationEventFixture = navigationEventFixture()
        if (installNavigationEventOwner) {
            root.setViewTreeNavigationEventDispatcherOwner(navigationEventFixture.owner)
        }
        val entryIds = ArrayDeque(listOf("root", "details", "next"))
        val controller = createNavHostController(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                ProvideViewModelStoreOwner(NavigationTestParentViewModelStoreOwner()) {
                    NavHost(
                        controller = controller,
                        transitionSpec = NavTransitionSpec.None,
                        presentationRetentionPolicy = NavPresentationRetentionPolicy.RetainAll,
                        systemBackEnabled = systemBackEnabledState?.value ?: systemBackEnabled,
                        overlayHostFactory = { OverlayHostDefaults.noOp },
                        key = hostKeyState?.value,
                    ) { entry ->
                        Text(entry.route.name)
                    }
                }
            }
        }
        return BackHostFixture(
            root = root,
            owner = owner,
            navigationEventOwner = navigationEventFixture.owner,
            navigationEventInput = navigationEventFixture.input,
            controller = controller,
            session = session,
            navHostView = root.requireNavHostView(),
        )
    }

    private fun navigationEvent(progress: Float): NavigationEvent {
        return NavigationEvent(
            touchX = 10f,
            touchY = 20f,
            progress = progress,
            swipeEdge = NavigationEvent.EDGE_LEFT,
            frameTimeMillis = 30L,
        )
    }

    private fun navigationEventFixture(): NavigationEventFixture {
        val owner = TestNavigationEventDispatcherOwner()
        val input = DirectNavigationEventInput()
        owner.navigationEventDispatcher.addInput(input)
        return NavigationEventFixture(owner, input)
    }

    private fun NavHostController.routeNames(): List<String> {
        return snapshot.entries.map { entry -> entry.route.name }
    }

    private fun NavHostView.visiblePageCount(): Int {
        return (0 until childCount).count { index ->
            getChildAt(index).visibility == View.VISIBLE
        }
    }

}

private data class NavigationEventFixture(
    val owner: TestNavigationEventDispatcherOwner,
    val input: DirectNavigationEventInput,
)

private data class BackHostFixture(
    val root: FrameLayout,
    val owner: TestBackOwner,
    val navigationEventOwner: TestNavigationEventDispatcherOwner,
    val navigationEventInput: DirectNavigationEventInput,
    val controller: NavHostController,
    val session: RenderSession,
    val navHostView: NavHostView,
) {
    val navigationFallbackCount: Int
        get() = navigationEventOwner.onBackCompletedFallbackInvocations
}

private class TestBackOwner : OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)

    var fallbackCount: Int = 0
        private set

    override val lifecycle: Lifecycle
        get() = registry

    override val onBackPressedDispatcher = OnBackPressedDispatcher {
        fallbackCount += 1
    }

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}
