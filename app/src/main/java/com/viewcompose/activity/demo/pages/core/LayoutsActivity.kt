package com.viewcompose

import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.demo.registry.DemoScenarioRegistry
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.environment.UiLocaleList
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.UiEnvironment
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.UiDensity

/**
 * Hosts one strict layout fixture selected by immutable scenario identity.
 */
class LayoutsActivity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_layouts_title

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        val scenario = checkNotNull(currentScenario()) {
            "LayoutsActivity requires a registered layout scenario"
        }
        builder.LayoutPage(
            fixture = LayoutFixture.from(scenario.id),
            scenario = scenario,
        )
    }

    override fun UiTreeBuilder.buildRootScaffold(root: ViewGroup) {
        if (!intent.hasExtra(EXTRA_VERIFICATION_FONT_SCALE)) {
            DemoSubPageScaffold(
                root = root,
                titleRes = demoTitleRes,
                scenario = currentScenario(),
            ) { builder ->
                buildDemoContent(root, builder)
            }
            return
        }
        val platform = Environment.values
        val rtl = intent.getBooleanExtra(EXTRA_VERIFICATION_RTL, false)
        UiEnvironment(
            UiEnvironmentValues(
                density = UiDensity(
                    density = platform.density.density,
                    fontScale = intent.getFloatExtra(EXTRA_VERIFICATION_FONT_SCALE, 1f),
                ),
                locales = UiLocaleList.of(if (rtl) "ar" else "en"),
                layoutDirection = if (rtl) UiLayoutDirection.Rtl else UiLayoutDirection.Ltr,
            ),
        ) {
            DemoSubPageScaffold(
                root = root,
                titleRes = demoTitleRes,
                scenario = currentScenario(),
            ) { builder ->
                buildDemoContent(root, builder)
            }
        }
    }

    companion object {
        private const val EXTRA_VERIFICATION_RTL = "layouts_verification_rtl"
        private const val EXTRA_VERIFICATION_FONT_SCALE = "layouts_verification_font_scale"

        internal fun newConstraintVerificationIntent(
            context: Context,
            rtl: Boolean,
            fontScale: Float,
        ): Intent = DemoScenarioRegistry.createLaunchIntent(
            context,
            DemoScenarioRegistry.require(DemoScenarioIds.LayoutConstraint.value),
        )
            .putExtra(EXTRA_VERIFICATION_RTL, rtl)
            .putExtra(EXTRA_VERIFICATION_FONT_SCALE, fontScale)
    }
}
