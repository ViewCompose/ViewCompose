package com.viewcompose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import com.viewcompose.android.setUiContent
import com.viewcompose.oneui7.OneUi7Theme
import com.viewcompose.oneui7.OneUi7ThemeDefaults
import com.viewcompose.overlay.oneui7.android.host.AndroidOverlayHost
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.UiIntegrationAttribution
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.UiDensity

/** Hosts deterministic light, dark, LTR, and RTL evidence for the public One UI 7 alpha slice. */
class OneUi7VerificationActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_one_ui_title
    private lateinit var resolvedTokens: UiThemeTokens
    private var overlayIntegrations: List<UiIntegrationAttribution> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = intent?.getBooleanExtra(EXTRA_DARK, false) ?: false
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) = Unit

    override fun installDemoContent() {
        val dark = intent?.getBooleanExtra(EXTRA_DARK, false) ?: false
        resolvedTokens = if (dark) OneUi7ThemeDefaults.dark() else OneUi7ThemeDefaults.light()
        setUiContent(
            debug = true,
            debugTag = "ViewComposeOneUi7",
            overlayHostFactory = { root ->
                AndroidOverlayHost(root, resolvedTokens).also { host ->
                    overlayIntegrations = host.integrationAttribution
                }
            },
            onRenderResult = DemoRenderDiagnosticsStore::record,
        ) { root ->
            buildRootScaffold(root)
        }
    }

    override fun UiTreeBuilder.buildRootScaffold(root: ViewGroup) {
        val rtl = intent?.getBooleanExtra(EXTRA_RTL, false) ?: false
        val fontScale = intent?.getFloatExtra(EXTRA_FONT_SCALE, 1f) ?: 1f
        val platform = Environment.values
        UiEnvironment(
            UiEnvironmentValues(
                density = UiDensity(platform.density.density, fontScale),
                locales = UiLocaleList.of(if (rtl) "ar" else "en"),
                layoutDirection = if (rtl) UiLayoutDirection.Rtl else UiLayoutDirection.Ltr,
            ),
        ) {
            OneUi7Theme(
                tokens = resolvedTokens,
                integrations = overlayIntegrations,
            ) {
                DemoOneUi7VerificationPage()
            }
        }
    }

    companion object {
        private const val EXTRA_DARK = "one_ui_7_dark"
        private const val EXTRA_RTL = "one_ui_7_rtl"
        private const val EXTRA_FONT_SCALE = "one_ui_7_font_scale"

        internal fun newIntent(
            context: Context,
            dark: Boolean = false,
            rtl: Boolean = false,
            fontScale: Float = 1f,
        ): Intent = Intent(context, OneUi7VerificationActivity::class.java)
            .putExtra(EXTRA_DARK, dark)
            .putExtra(EXTRA_RTL, rtl)
            .putExtra(EXTRA_FONT_SCALE, fontScale)
    }
}
