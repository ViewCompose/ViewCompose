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

/** Hosts the default Material 3 verification page without applying [DemoThemeTokens]. */
class Material3DefaultThemeActivity : DemoRenderActivity() {
    override val demoTitle: String = "Default Material 3 theme"

    override fun attachBaseContext(newBase: Context) {
        val requestedFontScale = intent?.getFloatExtra(EXTRA_FONT_SCALE, 0f) ?: 0f
        if (requestedFontScale > 0f) {
            val configuration = Configuration(newBase.resources.configuration).apply {
                fontScale = requestedFontScale
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
        val pageContent: UiTreeBuilder.() -> Unit = {
            Material3DefaultThemePage()
        }
        when (DemoThemeSession.mode) {
            DemoThemeMode.Light -> UiTheme(Material3ThemeDefaults.light(), pageContent)
            DemoThemeMode.Dark -> UiTheme(Material3ThemeDefaults.dark(), pageContent)
            DemoThemeMode.System -> pageContent()
        }
    }

    companion object {
        private const val EXTRA_FONT_SCALE = "material3_default_font_scale"

        internal fun newIntent(
            context: Context,
            fontScale: Float,
        ): Intent = Intent(context, Material3DefaultThemeActivity::class.java)
            .putExtra(EXTRA_FONT_SCALE, fontScale)
    }
}
