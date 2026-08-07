package com.viewcompose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiTheme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.UiDensity

internal const val EXTRA_DEMO_DESIGN_SYSTEM_KIND = "demo_design_system_kind"

/** Hosts the internal multi-design-system fixture under a deterministic configuration. */
class DemoDesignSystemVerificationActivity : DemoRenderActivity() {
    override val demoTitle: String = "Multi-design-system verification"

    private val requestedKind: DemoDesignSystemKind
        get() = DemoDesignSystemKind.fromId(intent?.getStringExtra(EXTRA_DEMO_DESIGN_SYSTEM_KIND))

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

    override fun UiTreeBuilder.buildRootScaffold(root: ViewGroup) {
        val requestedFontScale = intent?.getFloatExtra(EXTRA_FONT_SCALE, 1f) ?: 1f
        val rtl = intent?.getBooleanExtra(EXTRA_RTL, false) ?: false
        val bundle = DemoDesignSystemBundles.resolve(
            kind = requestedKind,
            dark = intent?.getBooleanExtra(EXTRA_DARK, false) ?: false,
            reducedMotionEnabled = intent?.getBooleanExtra(EXTRA_REDUCED_MOTION, false) ?: false,
        )
        val platformEnvironment = Environment.values
        val fixtureEnvironment = UiEnvironmentValues(
            density = UiDensity(
                density = platformEnvironment.density.density,
                fontScale = requestedFontScale,
            ),
            locales = UiLocaleList.of(if (rtl) "ar" else "en"),
            layoutDirection = if (rtl) UiLayoutDirection.Rtl else UiLayoutDirection.Ltr,
        )
        UiEnvironment(fixtureEnvironment) {
            UiTheme(bundle.tokens) {
                ProvideDemoDesignSystem(bundle) {
                    DemoDesignSystemVerificationPage()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_DARK = "demo_design_system_dark"
        private const val EXTRA_RTL = "demo_design_system_rtl"
        private const val EXTRA_FONT_SCALE = "demo_design_system_font_scale"
        private const val EXTRA_REDUCED_MOTION = "demo_design_system_reduced_motion"

        internal fun newIntent(
            context: Context,
            kind: DemoDesignSystemKind = DemoDesignSystemKind.CutContrast,
            dark: Boolean = false,
            rtl: Boolean = false,
            fontScale: Float = 1f,
            reducedMotionEnabled: Boolean = false,
        ): Intent = Intent(context, DemoDesignSystemVerificationActivity::class.java)
            .putExtra(EXTRA_DEMO_DESIGN_SYSTEM_KIND, kind.id)
            .putExtra(EXTRA_DARK, dark)
            .putExtra(EXTRA_RTL, rtl)
            .putExtra(EXTRA_FONT_SCALE, fontScale)
            .putExtra(EXTRA_REDUCED_MOTION, reducedMotionEnabled)
    }
}
