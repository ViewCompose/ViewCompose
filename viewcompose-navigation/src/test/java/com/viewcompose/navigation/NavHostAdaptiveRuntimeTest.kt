package com.viewcompose.navigation

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryIdFactory
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.captureUiLocalSnapshot
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
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
            transitionSpec = NavTransitionSpec.None,
            panePolicy = NavPanePolicy(
                minPaneWidthDp = 100f,
                maxPaneCount = 3,
                paneSpacingDp = 8f,
            ),
            systemBackEnabled = false,
            onFailure = null,
            content = { entry -> Text(entry.route.name) },
        )
        val runtime = NavHostRuntime.create(
            context = context,
            controller = controller,
            initialConfig = config,
            overlayHostFactory = { root: ViewGroup ->
                OverlayHostDefaults.androidOrNoOp(root)
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
}

private class ResumedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle
        get() = registry
}
