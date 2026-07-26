package com.viewcompose.navigation

import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.host.android.renderInto
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.Text
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NavHostPublicApiTest {
    @Test
    fun `public host mounts stack and controller executes transactional navigation`() {
        val fixture = renderPublicHost()

        assertTrue(fixture.controller.isAttached)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.childCount)

        val pushed = fixture.controller.navigate(NavRoute("details"))

        assertTrue(pushed is NavResult.Committed)
        assertEquals(listOf("home", "details"), fixture.controller.routeNames())
        assertEquals(2, fixture.navHostView.childCount)

        val popped = fixture.controller.popBackStack()

        assertTrue(popped is NavResult.Committed)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.childCount)

        fixture.session.dispose()

        assertFalse(fixture.controller.isAttached)
        assertEquals(0, fixture.navHostView.childCount)
    }

    @Test
    fun `destination lifecycle follows public host lifecycle and destroys on owner death`() {
        var destinationOwner: LifecycleOwner? = null
        val fixture = renderPublicHost { entry ->
            destinationOwner = LocalLifecycleOwner.current
            Text(entry.route.name)
        }
        val owner = checkNotNull(destinationOwner)

        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        fixture.lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        assertEquals(Lifecycle.State.STARTED, owner.lifecycle.currentState)

        fixture.lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        fixture.lifecycleOwner.moveTo(Lifecycle.State.DESTROYED)

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertFalse(fixture.controller.isAttached)
        assertEquals(0, fixture.navHostView.childCount)
        fixture.session.dispose()
    }

    @Test
    fun `parent rerender refreshes destination content without replacing host`() {
        var renderCount = 0
        val fixture = renderPublicHost { entry ->
            renderCount += 1
            Text(entry.route.name)
        }
        val originalHost = fixture.navHostView
        val initialRenderCount = renderCount

        fixture.session.render()

        assertTrue(renderCount > initialRenderCount)
        assertTrue(fixture.root.getChildAt(0) === originalHost)
        assertTrue(fixture.controller.isAttached)
        fixture.session.dispose()
    }

    @Test
    fun `failed destination returns public failure and preserves committed page`() {
        var reportedFailure: NavFailure? = null
        val fixture = renderPublicHost(
            onFailure = { failure ->
                reportedFailure = failure
            },
        ) { entry ->
            if (entry.route.name == "broken") {
                error("broken destination")
            }
            Text(entry.route.name)
        }

        val result = fixture.controller.navigate(NavRoute("broken"))

        assertTrue(result is NavResult.Failed)
        result as NavResult.Failed
        assertEquals(NavFailurePhase.DestinationPreparation, result.failure.phase)
        assertFalse(result.failure.stackCommitted)
        assertEquals(result.failure, reportedFailure)
        assertEquals(listOf("home"), fixture.controller.routeNames())
        assertEquals(1, fixture.navHostView.childCount)
        fixture.session.dispose()
    }

    @Test
    fun `controller rejects commands before a host is attached`() {
        val controller = createNavHostController(NavRoute("home"))

        val failure = runCatching {
            controller.navigate(NavRoute("details"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("attached NavHost"))
        assertEquals(listOf("home"), controller.routeNames())
    }

    @Test
    fun `failed initial destination can retry on the next committed parent render`() {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val lifecycleOwner = TestLifecycleOwner().apply {
            moveTo(Lifecycle.State.RESUMED)
        }
        val controller = deterministicController()
        val failures = mutableListOf<NavFailure>()
        var failInitialDestination = true
        val session = renderInto(root) {
            ProvideLifecycleOwner(lifecycleOwner) {
                NavHost(
                    controller = controller,
                    transitionSpec = NavTransitionSpec.None,
                    overlayHostFactory = { OverlayHostDefaults.noOp },
                    onFailure = failures::add,
                ) { entry ->
                    if (failInitialDestination) {
                        error("initial destination failed")
                    }
                    Text(entry.route.name)
                }
            }
        }

        assertFalse(controller.isAttached)
        assertEquals(NavFailurePhase.DestinationPreparation, failures.single().phase)
        assertEquals(0, (root.getChildAt(0) as NavHostView).childCount)

        failInitialDestination = false
        session.render()

        assertTrue(controller.isAttached)
        assertEquals(1, (root.getChildAt(0) as NavHostView).childCount)
        session.dispose()
    }

    @Test
    fun `released controller can attach a new host with its existing stack`() {
        val controller = deterministicController()
        val first = renderPublicHost(controller = controller)
        controller.navigate(NavRoute("details"))
        first.session.dispose()

        val second = renderPublicHost(controller = controller)

        assertTrue(controller.isAttached)
        assertEquals(listOf("home", "details"), controller.routeNames())
        assertEquals(2, second.navHostView.childCount)
        second.session.dispose()
    }

    private fun renderPublicHost(
        controller: NavHostController = deterministicController(),
        onFailure: ((NavFailure) -> Unit)? = null,
        content: com.viewcompose.widget.core.UiTreeBuilder.(
            com.viewcompose.navigation.core.NavEntry,
        ) -> Unit = { entry -> Text(entry.route.name) },
    ): PublicHostFixture {
        val application = RuntimeEnvironment.getApplication()
        val root = FrameLayout(application)
        val lifecycleOwner = TestLifecycleOwner().apply {
            moveTo(Lifecycle.State.RESUMED)
        }
        val session = renderInto(root) {
            ProvideLifecycleOwner(lifecycleOwner) {
                NavHost(
                    controller = controller,
                    transitionSpec = NavTransitionSpec.None,
                    overlayHostFactory = { OverlayHostDefaults.noOp },
                    onFailure = onFailure,
                    content = content,
                )
            }
        }
        val navHostView = root.getChildAt(0) as NavHostView
        return PublicHostFixture(
            root = root,
            lifecycleOwner = lifecycleOwner,
            controller = controller,
            session = session,
            navHostView = navHostView,
        )
    }

    private fun deterministicController(): NavHostController {
        val entryIds = ArrayDeque(
            listOf(
                "root",
                "details",
                "broken",
                "next",
            ),
        )
        return createNavHostController(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
    }

    private fun NavHostController.routeNames(): List<String> {
        return snapshot.entries.map { entry -> entry.route.name }
    }
}

private data class PublicHostFixture(
    val root: FrameLayout,
    val lifecycleOwner: TestLifecycleOwner,
    val controller: NavHostController,
    val session: com.viewcompose.host.android.RenderSession,
    val navHostView: NavHostView,
)

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}
