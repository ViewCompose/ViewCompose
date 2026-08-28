package com.viewcompose.navigation

/*
 * 测试职责：覆盖 Android navigation runtime 中的 Nav Host Adaptive Runtime 行为，防止导航契约在后续重构中回退。
 * Test responsibility: covers Nav Host Adaptive Runtime behavior in Android navigation runtime and guards navigation contracts against regressions.
 */

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.host.android.overlay.AndroidOverlayHostDefaults
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.ui.foundation.ProvideLocal
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiLocalSnapshot
import com.viewcompose.ui.foundation.UiLocals
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.captureUiLocalSnapshot
import com.viewcompose.ui.foundation.uiLocalOf
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NavHostAdaptiveRuntimeTest {
    @Test
    fun `native width changes publish adaptive panes through the public controller`() {
        val context = RuntimeEnvironment.getApplication()
        val owner = ResumedLifecycleOwner()
        val entryIds = ArrayDeque(listOf("root", "details", "confirmation"))
        val controller = createNavHostController(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        val config = NavHostRuntimeConfig(
            localSnapshot = captureUiLocalSnapshot(),
            lifecycleOwner = owner,
            parentViewModelStoreOwner = NavigationTestParentViewModelStoreOwner(),
            transitionSpec = NavTransitionSpec.None,
            panePolicy = NavPanePolicy(
                minPaneWidthDp = 100f,
                maxPaneCount = 3,
                paneSpacingDp = 8f,
            ),
            systemBackEnabled = false,
            onFailure = null,
            contentKey = Unit,
            content = { entry -> Text(entry.route.name) },
        )
        val runtime = NavHostRuntime.create(
            context = context,
            controller = controller,
            initialConfig = config,
            overlayHostFactory = { root: ViewGroup ->
                AndroidOverlayHostDefaults.androidOrNoOp(root)
            },
            debug = false,
            debugTag = "adaptive-runtime-test",
        )

        try {
            runtime.commitStaged()
            measureAndLayout(runtime.hostView, width = 1_200, height = 600)
            assertTrue(controller.navigate(NavRoute("details")) is NavResult.Committed)
            assertTrue(controller.navigate(NavRoute("confirmation")) is NavResult.Committed)
            measureAndLayout(runtime.hostView, width = 1_200, height = 600)

            val visibleChildren = (0 until runtime.hostView.childCount)
                .map(runtime.hostView::getChildAt)
                .filter { child -> child.visibility == View.VISIBLE }
                .sortedBy(View::getLeft)
            val spacing = config.panePolicy.resolveSpacingPixels(
                runtime.hostView.resources.displayMetrics.density,
            )
            val basePaneWidth = (1_200 - (spacing * 2)) / 3
            val remainder = (1_200 - (spacing * 2)) % 3
            val firstPaneWidth = basePaneWidth + if (remainder > 0) 1 else 0
            val secondPaneWidth = basePaneWidth + if (remainder > 1) 1 else 0

            assertEquals(3, visibleChildren.size)
            assertEquals(firstPaneWidth, visibleChildren[0].width)
            assertEquals(firstPaneWidth + spacing, visibleChildren[1].left)
            assertEquals(
                firstPaneWidth + spacing + secondPaneWidth + spacing,
                visibleChildren[2].left,
            )

            measureAndLayout(runtime.hostView, width = 180, height = 600)

            assertEquals(
                1,
                (0 until runtime.hostView.childCount)
                    .map(runtime.hostView::getChildAt)
                    .count { child -> child.visibility == View.VISIBLE },
            )
        } finally {
            runtime.destroy()
        }
    }

    @Test
    fun `committed pane policy refreshes newly visible pages with latest staged environment`() {
        val fixture = createSinglePaneRuntime()
        try {
            measureAndLayout(fixture.runtime.hostView, width = 1_200, height = 600)
            assertTrue(fixture.controller.navigate(NavRoute("details")) is NavResult.Committed)
            assertTrue(fixture.controller.navigate(NavRoute("confirmation")) is NavResult.Committed)
            val renderedThemes = mutableMapOf<String, MutableList<String>>()
            val expanded = fixture.config.copy(
                localSnapshot = themeSnapshot("dark"),
                panePolicy = fixture.config.panePolicy.copy(maxPaneCount = 3),
                content = { entry ->
                    renderedThemes.getOrPut(entry.route.name) { mutableListOf() } +=
                        UiLocals.current(TestThemeLocal)
                    Text(entry.route.name)
                },
            )

            fixture.runtime.stage(expanded)
            fixture.runtime.commitStaged()

            assertEquals("dark", renderedThemes.getValue("home").last())
            assertEquals("dark", renderedThemes.getValue("details").last())
            assertEquals(3, visibleChildCount(fixture.runtime.hostView))
        } finally {
            fixture.runtime.destroy()
        }
    }

    @Test
    fun `pane refresh failure reaches runtime handler and keeps previous scene`() {
        var reportedFailure: NavFailure? = null
        val fixture = createSinglePaneRuntime(
            onFailure = { failure -> reportedFailure = failure },
        )
        try {
            measureAndLayout(fixture.runtime.hostView, width = 1_200, height = 600)
            assertTrue(fixture.controller.navigate(NavRoute("details")) is NavResult.Committed)
            assertTrue(fixture.controller.navigate(NavRoute("confirmation")) is NavResult.Committed)
            val expanded = fixture.config.copy(
                panePolicy = fixture.config.panePolicy.copy(maxPaneCount = 3),
                content = { entry ->
                    if (entry.route.name == "home") {
                        error("home pane refresh failed")
                    }
                    Text(entry.route.name)
                },
            )

            fixture.runtime.stage(expanded)
            fixture.runtime.commitStaged()

            assertNotNull(reportedFailure)
            assertEquals(
                NavFailurePhase.DestinationRefresh,
                checkNotNull(reportedFailure).phase,
            )
            assertEquals(false, checkNotNull(reportedFailure).stackCommitted)
            assertEquals(1, visibleChildCount(fixture.runtime.hostView))
            assertEquals(
                listOf("home", "details", "confirmation"),
                fixture.controller.snapshot.entries.map { entry -> entry.route.name },
            )
        } finally {
            fixture.runtime.destroy()
        }
    }

    private fun measureAndLayout(
        view: View,
        width: Int,
        height: Int,
    ) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun createSinglePaneRuntime(
        onFailure: ((NavFailure) -> Unit)? = null,
    ): AdaptiveRuntimeFixture {
        val context = RuntimeEnvironment.getApplication()
        val entryIds = ArrayDeque(listOf("root", "details", "confirmation"))
        val controller = createNavHostController(
            startDestination = NavRoute("home"),
            entryIdFactory = NavEntryIdFactory {
                NavEntryId(entryIds.removeFirst())
            },
        )
        val config = NavHostRuntimeConfig(
            localSnapshot = captureUiLocalSnapshot(),
            lifecycleOwner = ResumedLifecycleOwner(),
            parentViewModelStoreOwner = NavigationTestParentViewModelStoreOwner(),
            transitionSpec = NavTransitionSpec.None,
            panePolicy = NavPanePolicy(
                minPaneWidthDp = 100f,
                maxPaneCount = 1,
                paneSpacingDp = 8f,
            ),
            systemBackEnabled = false,
            onFailure = onFailure,
            contentKey = Unit,
            content = { entry -> Text(entry.route.name) },
        )
        val runtime = NavHostRuntime.create(
            context = context,
            controller = controller,
            initialConfig = config,
            overlayHostFactory = { root: ViewGroup ->
                AndroidOverlayHostDefaults.androidOrNoOp(root)
            },
            debug = false,
            debugTag = "adaptive-runtime-refresh-test",
        )
        runtime.commitStaged()
        return AdaptiveRuntimeFixture(runtime, controller, config)
    }

    private fun visibleChildCount(hostView: NavHostView): Int {
        return (0 until hostView.childCount)
            .map(hostView::getChildAt)
            .count { child -> child.visibility == View.VISIBLE }
    }

    private fun themeSnapshot(theme: String): UiLocalSnapshot {
        var snapshot: UiLocalSnapshot? = null
        UiTreeBuilder().ProvideLocal(TestThemeLocal, theme) {
            snapshot = captureUiLocalSnapshot()
        }
        return checkNotNull(snapshot)
    }

    private companion object {
        val TestThemeLocal = uiLocalOf(debugName = "AdaptiveRuntimeTestTheme") { "system" }
    }
}

private data class AdaptiveRuntimeFixture(
    val runtime: NavHostRuntime,
    val controller: NavHostController,
    val config: NavHostRuntimeConfig,
)

private class ResumedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle
        get() = registry
}
