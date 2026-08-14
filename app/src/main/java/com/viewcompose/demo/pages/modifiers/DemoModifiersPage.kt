package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize

@ViewComposePreview(name = "Modifiers · Visual", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewModifiersVisual() {
    ModifiersPage(ModifiersFixture.Visual)
}

@ViewComposePreview(name = "Modifiers · Size", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewModifiersSize() {
    ModifiersPage(ModifiersFixture.Sizing)
}

@ViewComposePreview(name = "Modifiers · Accessibility", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewModifiersAccessibility() {
    ModifiersPage(ModifiersFixture.Accessibility)
}

internal enum class ModifiersFixture(
    val scenarioId: DemoScenarioId,
) {
    Visual(DemoScenarioIds.ModifierVisual),
    Sizing(DemoScenarioIds.ModifierSizing),
    Accessibility(DemoScenarioIds.ModifierAccessibility),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): ModifiersFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported modifiers scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.ModifiersPage(
    fixture: ModifiersFixture,
    scenario: DemoScenarioSpec? = null,
) {
    val sections = when (fixture) {
        ModifiersFixture.Visual -> listOf(
            "elevation",
            "border_clip",
            "background_drawable",
            "alpha_ripple",
            "corner",
        )

        ModifiersFixture.Sizing -> listOf("size_constraints")
        ModifiersFixture.Accessibility -> listOf("accessibility", "native_view", "offset_zindex")
    }

    LazyColumn(
        items = sections,
        key = { section -> section },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "elevation" -> ModifierElevationSection()
            "border_clip" -> ModifierBorderClipSection()
            "background_drawable" -> ModifierBackgroundDrawableSection(scenario)
            "alpha_ripple" -> ModifierAlphaRippleSection()
            "corner" -> ModifierCornerSection()
            "size_constraints" -> ModifierSizeConstraintsSection(scenario)
            "accessibility" -> ModifierAccessibilitySection(scenario)
            "native_view" -> ModifierNativeViewSection(scenario)
            "offset_zindex" -> ModifierOffsetZIndexSection()
            else -> error("Unsupported modifiers section: $section")
        }
    }
}

internal fun Modifier.modifierScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}
