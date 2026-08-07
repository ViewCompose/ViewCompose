package com.viewcompose

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts an isolated theme-source fixture for manual and screenshot-based token verification. */
class Material3DefaultThemeActivity : DemoRenderActivity() {
    override val demoTitle: String = "Theme and token verification"

    private val themeSource: DemoThemeSource
        get() = DemoThemeSource.fromId(intent?.getStringExtra(EXTRA_THEME_SOURCE))

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
        val source = themeSource
        val isDark = DemoThemeTokens.isSystemDark(root.context)
        val pageContent: UiTreeBuilder.() -> Unit = {
            Material3DefaultThemePage(source = source)
        }
        val tokens = source.tokens(isDark)
        if (tokens == null) {
            pageContent()
        } else {
            UiTheme(tokens, pageContent)
        }
    }

    companion object {
        private const val EXTRA_FONT_SCALE = "material3_default_font_scale"
        private const val EXTRA_THEME_SOURCE = "material3_theme_source"

        internal fun newIntent(
            context: Context,
            fontScale: Float = 0f,
            source: DemoThemeSource = DemoThemeSource.Material3Defaults,
        ): Intent = Intent(context, Material3DefaultThemeActivity::class.java)
            .putExtra(EXTRA_FONT_SCALE, fontScale)
            .putExtra(EXTRA_THEME_SOURCE, source.id)
    }
}
