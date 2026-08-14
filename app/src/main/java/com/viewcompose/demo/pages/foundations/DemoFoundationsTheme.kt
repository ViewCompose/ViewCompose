package com.viewcompose

import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonColorOverride
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.ProvideButtonColors
import com.viewcompose.ui.foundation.ProvideSegmentedControlColors
import com.viewcompose.ui.foundation.ProvideTextFieldColors
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.SegmentedControlColorOverride
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldColorOverride
import com.viewcompose.ui.foundation.TextFieldVariant
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiThemeOverride
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

internal fun UiTreeBuilder.FoundationsThemeFixture(scenario: DemoScenarioSpec?) {
    FoundationsFixtureList(
        sections = listOf("override", "component_defaults", "tokens", "shape"),
    ) { section ->
        when (section) {
            "override" -> FoundationsColorOverride(scenario)
            "component_defaults" -> FoundationsComponentDefaults(scenario)
            "tokens" -> FoundationsTokenSnapshot()
            "shape" -> FoundationsShapeOverride()
            else -> error("Unsupported foundations theme section: $section")
        }
    }
}

private fun UiTreeBuilder.FoundationsColorOverride(scenario: DemoScenarioSpec?) {
    Column(spacing = 10.dp, modifier = Modifier.fillMaxWidth()) {
        FoundationsSummary(scenario)
        Text(
            text = stringResource(R.string.demo_foundations_theme_color_override),
            style = Theme.typography.titleMedium,
        )
        UiThemeOverride(colors = { copy(primary = secondary, surfaceVariant = primary) }) {
            Column(
                spacing = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                    .shape(SurfaceDefaults.shape())
                    .padding(12.dp),
            ) {
                ThemeSwatchRow(
                    label = stringResource(R.string.demo_foundations_theme_local_colors),
                    swatches = listOf(
                        ThemeSwatch(
                            stringResource(R.string.demo_foundations_theme_primary),
                            Theme.colors.primary,
                        ),
                        ThemeSwatch(
                            stringResource(R.string.demo_foundations_theme_surface),
                            Theme.colors.surfaceVariant,
                        ),
                    ),
                )
                Button(
                    text = stringResource(R.string.demo_foundations_theme_accent_primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .foundationsScenarioTarget(scenario, DemoAutomationRole.Target),
                )
            }
        }
    }
}

private fun UiTreeBuilder.FoundationsComponentDefaults(scenario: DemoScenarioSpec?) {
    ProvideButtonColors(
        ButtonColorOverride(
            primaryContainer = Theme.colors.onSurface,
            primaryContent = Theme.colors.background,
            primaryDisabledContainer = Theme.colors.outlineVariant,
            primaryDisabledContent = Theme.colors.onSurfaceVariant,
            outlinedBorder = Theme.colors.secondary,
            outlinedDisabledBorder = Theme.colors.onSurfaceVariant,
        ),
    ) {
        ProvideTextFieldColors(
            TextFieldColorOverride(
                filledDisabledContainer = Theme.colors.surfaceVariant,
                outlinedErrorBorder = Theme.colors.secondary,
            ),
        ) {
            ProvideSegmentedControlColors(
                SegmentedControlColorOverride(
                    indicator = Theme.colors.secondary,
                    indicatorDisabled = Theme.colors.outlineVariant,
                    selectedText = Theme.colors.background,
                    selectedTextDisabled = Theme.colors.onSurfaceVariant,
                ),
            ) {
                Column(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .shape(SurfaceDefaults.shape())
                        .padding(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.demo_foundations_theme_component_defaults),
                        style = Theme.typography.titleMedium,
                    )
                    SegmentedControl(
                        items = listOf(
                            stringResource(R.string.demo_foundations_theme_alpha),
                            stringResource(R.string.demo_foundations_theme_beta),
                            stringResource(R.string.demo_foundations_theme_gamma),
                        ),
                        selectedIndex = 1,
                        onSelectionChange = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        text = stringResource(R.string.demo_foundations_theme_primary_token),
                        leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                        modifier = Modifier
                            .fillMaxWidth()
                            .foundationsScenarioTarget(
                                scenario,
                                DemoAutomationRole.SecondaryTarget,
                            ),
                    )
                    TextField(
                        state = rememberTextFieldState(
                            stringResource(R.string.demo_foundations_theme_error_value),
                        ),
                        variant = TextFieldVariant.Outlined,
                        isError = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun UiTreeBuilder.FoundationsTokenSnapshot() {
    Column(spacing = 8.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.demo_foundations_theme_token_snapshot),
            style = Theme.typography.titleMedium,
        )
        ThemeSwatchRow(
            stringResource(R.string.demo_foundations_theme_core_colors),
            listOf(
                ThemeSwatch(stringResource(R.string.demo_foundations_theme_background), Theme.colors.background),
                ThemeSwatch(stringResource(R.string.demo_foundations_theme_surface), Theme.colors.surface),
                ThemeSwatch(stringResource(R.string.demo_foundations_theme_primary), Theme.colors.primary),
                ThemeSwatch(stringResource(R.string.demo_foundations_theme_secondary), Theme.colors.secondary),
            ),
        )
        Text(
            text = stringResource(
                R.string.demo_foundations_theme_shape_state,
                Theme.shapes.medium.demoLabel(),
                Theme.shapes.small.demoLabel(),
            ),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
    }
}

private fun UiTreeBuilder.FoundationsShapeOverride() {
    UiThemeOverride(
        shapes = { copy(medium = UiShape.rounded(32.dp), small = UiShape.rounded(24.dp)) },
    ) {
        Column(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .backgroundColor(SurfaceDefaults.backgroundColor())
                .shape(SurfaceDefaults.shape())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.demo_foundations_theme_shape_override),
                style = Theme.typography.titleMedium,
            )
            Row(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .backgroundColor(Theme.colors.surfaceVariant)
                        .shape(SurfaceDefaults.shape()),
                ) {}
                Button(
                    text = stringResource(R.string.demo_foundations_theme_rounded_button),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
