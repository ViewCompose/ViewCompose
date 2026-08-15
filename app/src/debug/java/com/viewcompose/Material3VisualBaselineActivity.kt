package com.viewcompose

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import com.viewcompose.material3.Material3ThemeDefaults
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Debug-only real Android Renderer host used by the Phase 1 Material 3 visual acceptance suite. */
class Material3VisualBaselineActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_material3_visual_baseline_title

    private val baselinePage: Int
        get() = intent.getIntExtra(EXTRA_PAGE, PAGE_ACTIONS).coerceIn(PAGE_ACTIONS, PAGE_SURFACES)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val useDarkSystemBars = when (DemoThemeSession.mode) {
            DemoThemeMode.Light -> false
            DemoThemeMode.Dark -> true
            DemoThemeMode.System -> {
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            }
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !useDarkSystemBars
            isAppearanceLightNavigationBars = !useDarkSystemBars
        }
    }

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) = Unit

    override fun UiTreeBuilder.buildRootScaffold(root: ViewGroup) {
        val pageContent: UiTreeBuilder.() -> Unit = {
            Material3VisualBaselinePage(page = baselinePage)
        }
        when (DemoThemeSession.mode) {
            DemoThemeMode.Light -> UiTheme(
                tokens = Material3ThemeDefaults.light(),
                content = pageContent,
            )

            DemoThemeMode.Dark -> UiTheme(
                tokens = Material3ThemeDefaults.dark(),
                content = pageContent,
            )

            // System intentionally keeps the host-provided Material3Theme so the same fixture also
            // exercises the Android theme bridge instead of another static token snapshot.
            DemoThemeMode.System -> pageContent()
        }
    }

    companion object {
        private const val EXTRA_PAGE = "material3_baseline_page"

        const val PAGE_ACTIONS = 0
        const val PAGE_INPUTS = 1
        const val PAGE_SURFACES = 2

        fun newIntent(
            context: Context,
            page: Int,
        ): Intent = Intent(context, Material3VisualBaselineActivity::class.java)
            .putExtra(EXTRA_PAGE, page)
    }
}
