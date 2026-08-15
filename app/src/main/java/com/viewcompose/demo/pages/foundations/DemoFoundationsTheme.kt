package com.viewcompose

import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonOverrides
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.ProvideButtonOverrides
import com.viewcompose.ui.foundation.ProvideSegmentedControlOverrides
import com.viewcompose.ui.foundation.ProvideTextFieldOverrides
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.SegmentedControlOverrides
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldOverrides
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
    ProvideButtonOverrides(
        ButtonOverrides(
            contentColor = Theme.colors.background,
            disabledContainerColor = Theme.colors.outlineVariant,
            disabledContentColor = Theme.colors.onSurfaceVariant,
            disabledBorderColor = Theme.colors.onSurfaceVariant,
        ),
    ) {
        ProvideTextFieldOverrides(
            TextFieldOverrides(
                disabledContainerColor = Theme.colors.surfaceVariant,
                errorContainerColor = Theme.colors.surfaceVariant,
                errorBorderColor = Theme.colors.secondary,
            ),
        ) {
            ProvideSegmentedControlOverrides(
                SegmentedControlOverrides(
                    indicatorColor = Theme.colors.secondary,
                    disabledIndicatorColor = Theme.colors.outlineVariant,
                    selectedContentColor = Theme.colors.background,
                    disabledSelectedContentColor = Theme.colors.onSurfaceVariant,
                ),
            ) {
                ProvideButtonOverrides(
                    ButtonOverrides(
                        borderColor = Theme.colors.secondary,
                        borderWidth = 2.dp,
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
                            items = demoSegmentedItems(
                                "alpha" to stringResource(R.string.demo_foundations_theme_alpha),
                                "beta" to stringResource(R.string.demo_foundations_theme_beta),
                                "gamma" to stringResource(R.string.demo_foundations_theme_gamma),
                            ),
                            selectedIndex = 1,
                            onSelectionChange = {},
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            text = stringResource(R.string.demo_foundations_theme_primary_token),
                            leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                            overrides = ButtonOverrides(containerColor = Theme.colors.onSurface),
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
