package com.viewcompose

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.EXTRA_DEMO_SCENARIO_ID
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.demo.registry.DemoScenarioRegistry
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
    override val demoTitleRes: Int = R.string.demo_activity_design_system_title

    private val requestedFixture: DemoDesignSystemFixture
        get() {
            val scenario = checkNotNull(currentScenario()) {
                "DemoDesignSystemVerificationActivity requires a registered design-system scenario"
            }
            return DemoDesignSystemFixture.from(scenario.id)
        }

    private val requestedKind: DemoDesignSystemKind
        get() = requestedFixture.resolveVariant(
            intent?.getStringExtra(EXTRA_DEMO_DESIGN_SYSTEM_KIND),
        )

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
                    DemoDesignSystemVerificationPage(
                        hostContext = DemoHostContextSnapshot.from(root),
                        scenario = checkNotNull(currentScenario()),
                        onReplaceDesignSystem = ::replaceDesignSystem,
                    )
                }
            }
        }
    }

    private fun replaceDesignSystem(kind: DemoDesignSystemKind) {
        if (kind == requestedKind) return
        val fixture = DemoDesignSystemFixture.from(kind)
        intent.putExtra(EXTRA_DEMO_SCENARIO_ID, fixture.scenarioId.value)
        intent.putExtra(EXTRA_DEMO_DESIGN_SYSTEM_KIND, kind.id)
        // Recreating the Activity replaces the root RenderSession and every session-bound overlay
        // together while Android's saved-state owner preserves caller-owned rememberSaveable state.
        recreate()
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
        ): Intent {
            val fixture = DemoDesignSystemFixture.from(kind)
            val scenario = DemoScenarioRegistry.require(fixture.scenarioId.value)
            return DemoScenarioRegistry.createLaunchIntent(context, scenario)
                .putExtra(EXTRA_DEMO_DESIGN_SYSTEM_KIND, kind.id)
                .putExtra(EXTRA_DARK, dark)
                .putExtra(EXTRA_RTL, rtl)
                .putExtra(EXTRA_FONT_SCALE, fontScale)
                .putExtra(EXTRA_REDUCED_MOTION, reducedMotionEnabled)
        }
    }
}

internal enum class DemoDesignSystemFixture(
    val scenarioId: DemoScenarioId,
    val defaultKind: DemoDesignSystemKind,
    val allowedKinds: Set<DemoDesignSystemKind>,
) {
    Material3(
        scenarioId = DemoScenarioIds.DesignBundleMaterial3,
        defaultKind = DemoDesignSystemKind.RoundedReference,
        allowedKinds = setOf(DemoDesignSystemKind.RoundedReference),
    ),
    Contrast(
        scenarioId = DemoScenarioIds.DesignBundleContrast,
        defaultKind = DemoDesignSystemKind.CutContrast,
        allowedKinds = setOf(
            DemoDesignSystemKind.CutContrast,
            DemoDesignSystemKind.CupertinoPressure,
        ),
    ),
    ;

    fun resolveVariant(id: String?): DemoDesignSystemKind {
        val kind = id?.let(DemoDesignSystemKind::fromId) ?: defaultKind
        require(kind in allowedKinds) {
            "Variant ${kind.id} does not belong to ${scenarioId.value}"
        }
        return kind
    }

    companion object {
        fun from(scenarioId: DemoScenarioId): DemoDesignSystemFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported design-system scenario: $scenarioId")

        fun from(kind: DemoDesignSystemKind): DemoDesignSystemFixture =
            entries.single { fixture -> kind in fixture.allowedKinds }
    }
}

/** Actual Android root-context evidence shown next to token and recipe attribution. */
internal data class DemoHostContextSnapshot(
    val chain: String,
    val androidPrimary: Int,
) {
    companion object {
        fun from(root: ViewGroup): DemoHostContextSnapshot {
            return DemoHostContextSnapshot(
                chain = root.context.contextChain(),
                androidPrimary = root.context.resolveColorAttribute(androidx.appcompat.R.attr.colorPrimary),
            )
        }
    }
}

private fun Context.contextChain(): String {
    val visited = mutableSetOf<Context>()
    val names = mutableListOf<String>()
    var current: Context? = this
    while (current != null && visited.add(current) && names.size < 8) {
        names += current.javaClass.simpleName.ifBlank { current.javaClass.name.substringAfterLast('.') }
        current = (current as? ContextWrapper)?.baseContext
    }
    return names.joinToString(" > ")
}

private fun Context.resolveColorAttribute(attribute: Int): Int {
    val value = TypedValue()
    check(theme.resolveAttribute(attribute, value, true)) {
        "Expected Android theme attribute 0x${attribute.toString(16)}"
    }
    return value.data
}
