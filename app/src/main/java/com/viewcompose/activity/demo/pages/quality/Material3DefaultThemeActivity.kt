package com.viewcompose

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.material3.Material3Theme
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts an isolated theme-source fixture for manual and screenshot-based token verification. */
class Material3DefaultThemeActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_material3_theme_title

    override fun attachBaseContext(newBase: Context) {
        val requestedFontScale = intent?.getFloatExtra(EXTRA_FONT_SCALE, 0f) ?: 0f
        val requestedMode = DemoThemeSession.mode
        if (requestedFontScale > 0f || requestedMode != DemoThemeMode.System) {
            val configuration = Configuration(newBase.resources.configuration).apply {
                if (requestedFontScale > 0f) {
                    fontScale = requestedFontScale
                }
                if (requestedMode != DemoThemeMode.System) {
                    val requestedNightMode = if (requestedMode == DemoThemeMode.Dark) {
                        Configuration.UI_MODE_NIGHT_YES
                    } else {
                        Configuration.UI_MODE_NIGHT_NO
                    }
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or requestedNightMode
                }
            }
            super.attachBaseContext(newBase.createConfigurationContext(configuration))
        } else {
            super.attachBaseContext(newBase)
        }
    }

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
        val scenario = checkNotNull(currentScenario()) {
            "Material3DefaultThemeActivity requires a registered Material 3 design scenario"
        }
        val source = Material3ThemeFixture.from(scenario.id).source
        val isDark = DemoThemeTokens.isSystemDark(root.context)
        val pageContent: UiTreeBuilder.() -> Unit = {
            Material3DefaultThemePage(
                source = source,
                scenario = scenario,
            )
        }
        val tokens = source.tokens(isDark)
        if (tokens == null) {
            pageContent()
        } else {
            Material3Theme(tokens = tokens, content = pageContent)
        }
    }

    companion object {
        private const val EXTRA_FONT_SCALE = "material3_default_font_scale"

        internal fun newIntent(
            context: Context,
            fontScale: Float = 0f,
            source: DemoThemeSource = DemoThemeSource.Material3Defaults,
        ): Intent {
            val fixture = Material3ThemeFixture.from(source)
            val scenario = DemoScenarioRegistry.require(fixture.scenarioId.value)
            return DemoScenarioRegistry.createLaunchIntent(context, scenario)
                .putExtra(EXTRA_FONT_SCALE, fontScale)
        }
    }
}

internal enum class Material3ThemeFixture(
    val scenarioId: DemoScenarioId,
    val source: DemoThemeSource,
) {
    AndroidXml(DemoScenarioIds.DesignMaterial3Xml, DemoThemeSource.AndroidXml),
    Static(DemoScenarioIds.DesignMaterial3Static, DemoThemeSource.Material3Defaults),
    Custom(DemoScenarioIds.DesignMaterial3Custom, DemoThemeSource.DemoCustom),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): Material3ThemeFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported Material 3 scenario: $scenarioId")

        fun from(source: DemoThemeSource): Material3ThemeFixture =
            entries.single { fixture -> fixture.source == source }
    }
}
