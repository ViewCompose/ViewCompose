package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Android Nav Host Back Adapter 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Android Nav Host Back Adapter behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import android.view.View
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.ExperimentalActivityApi
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.host.android.RenderSession
import com.viewcompose.host.android.renderInto
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.Text
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalActivityApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidNavHostBackAdapterTest {
    @Test
    fun `platform back pops navigation then delegates at root`() {
        val fixture = renderHost()

        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, fixture.owner.fallbackCount)
        assertEquals(listOf("home"), fixture.controller.routeNames())

        fixture.controller.navigate(NavRoute("details"))
        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, fixture.owner.fallbackCount)
        assertEquals(listOf("home"), fixture.controller.routeNames())

        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(2, fixture.owner.fallbackCount)
        fixture.session.dispose()
    }

    @Test
    fun `disabled system back delegates without changing navigation`() {
        val fixture = renderHost(systemBackEnabled = false)
        fixture.controller.navigate(NavRoute("details"))

        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, fixture.owner.fallbackCount)
        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        fixture.session.dispose()
    }

    @Test
    fun `platform back delegates while host lifecycle is below started`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))
        fixture.owner.moveTo(Lifecycle.State.CREATED)

        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, fixture.owner.fallbackCount)
        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        fixture.session.dispose()
    }

    @Test
    fun `predictive back cancellation keeps stack and restores settled visibility`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))

        fixture.owner.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(progress = 0f))
        fixture.owner.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(progress = 0.5f))

        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        assertEquals(2, fixture.navHostView.visiblePageCount())

        fixture.owner.onBackPressedDispatcher.dispatchOnBackCancelled()

        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.visiblePageCount())
        assertEquals(View.VISIBLE, fixture.navHostView.getChildAt(1).visibility)
        fixture.session.dispose()
    }

    @Test
    fun `predictive back commit pops once and next back delegates`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))

        fixture.owner.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(progress = 0f))
        fixture.owner.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(progress = 0.7f))
        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(0, fixture.owner.fallbackCount)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.childCount)

        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, fixture.owner.fallbackCount)
        fixture.session.dispose()
    }

    @Test
    fun `disposing host removes platform callback`() {
        val fixture = renderHost()
        fixture.controller.navigate(NavRoute("details"))

        fixture.session.dispose()
        fixture.owner.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, fixture.owner.fallbackCount)
        assertTrue(!fixture.controller.isAttached)
    }

    private fun renderHost(
        systemBackEnabled: Boolean = true,
    ): BackHostFixture {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val owner = TestBackOwner().apply {
            moveTo(Lifecycle.State.RESUMED)
        }
        root.setViewTreeOnBackPressedDispatcherOwner(owner)
        val entryIds = ArrayDeque(listOf("root", "details", "next"))
        val controller = createNavHostController(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                NavHost(
                    controller = controller,
                    transitionSpec = NavTransitionSpec.None,
                    systemBackEnabled = systemBackEnabled,
                    overlayHostFactory = { OverlayHostDefaults.noOp },
                ) { entry ->
                    Text(entry.route.name)
                }
            }
        }
        return BackHostFixture(
            owner = owner,
            controller = controller,
            session = session,
            navHostView = root.getChildAt(0) as NavHostView,
        )
    }

    private fun backEvent(progress: Float): BackEventCompat {
        return BackEventCompat(
            touchX = 10f,
            touchY = 20f,
            progress = progress,
            swipeEdge = BackEventCompat.EDGE_LEFT,
            frameTimeMillis = 30L,
        )
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

private data class BackHostFixture(
    val owner: TestBackOwner,
    val controller: NavHostController,
    val session: RenderSession,
    val navHostView: NavHostView,
)

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
