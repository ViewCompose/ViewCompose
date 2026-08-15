package com.viewcompose

import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonSize
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.IconButtonOverrides
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextButton
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

internal fun UiTreeBuilder.ComponentButtonFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val actionCount = rememberSaveable(key = "component-button-action-count") {
        mutableStateOf(0)
    }
    ComponentFixtureList(generation, listOf("control", "matrix", "extras")) { section ->
        when (section) {
            "control" -> ComponentFixtureHeader(
                scenario = scenario,
                state = {
                    stringResource(R.string.demo_component_action_count, actionCount.value)
                },
                onReset = onReset,
            ) {
                Button(
                    text = stringResource(R.string.demo_component_button_primary),
                    onClick = { actionCount.value += 1 },
                    modifier = Modifier.componentScenarioTarget(
                        scenario,
                        DemoAutomationRole.PrimaryAction,
                    ),
                )
            }

            "matrix" -> ComponentButtonMatrix(
                scenario = scenario,
                onClick = { actionCount.value += 1 },
            )

            else -> ComponentButtonExtras(onClick = { actionCount.value += 1 })
        }
    }
}

private fun UiTreeBuilder.ComponentButtonMatrix(
    scenario: DemoScenarioSpec?,
    onClick: () -> Unit,
) {
    Column(
        spacing = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .margin(top = 16.dp)
            .componentScenarioTarget(scenario, DemoAutomationRole.Target),
    ) {
        ComponentFixtureTitle(R.string.demo_component_button_matrix)
        ButtonVariant.entries.forEach { variant ->
            Text(
                text = variant.name,
                style = Theme.typography.bodyMedium,
                color = TextDefaults.secondaryColor(),
            )
            Row(spacing = 8.dp, verticalAlignment = VerticalAlignment.Center) {
                ButtonSize.entries.forEach { size ->
                    Button(
                        text = size.name,
                        variant = variant,
                        size = size,
                        onClick = onClick,
                    )
                }
            }
        }
    }
}

private fun UiTreeBuilder.ComponentButtonExtras(onClick: () -> Unit) {
    val icon = ImageSource.Resource(R.drawable.demo_media_icon)
    Column(
        spacing = 8.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 16.dp, bottom = 24.dp),
    ) {
        ComponentFixtureTitle(R.string.demo_component_button_extras)
        Row(spacing = 8.dp) {
            Button(
                text = stringResource(R.string.demo_component_enabled),
                onClick = onClick,
            )
            Button(
                text = stringResource(R.string.demo_component_disabled),
                enabled = false,
                onClick = {},
            )
        }
        Button(
            text = stringResource(R.string.demo_component_button_leading_icon),
            leadingIcon = icon,
            onClick = onClick,
        )
        Button(
            text = stringResource(R.string.demo_component_button_trailing_icon),
            trailingIcon = icon,
            onClick = onClick,
        )
        TextButton(
            text = stringResource(R.string.demo_component_text_button),
            onClick = onClick,
        )
    }
}

internal fun UiTreeBuilder.ComponentIconButtonFixture(
    scenario: DemoScenarioSpec?,
    generation: Int,
    onReset: () -> Unit,
) {
    val actionCount = rememberSaveable(key = "component-icon-button-action-count") {
        mutableStateOf(0)
    }
    ComponentFixtureList(generation, listOf("control", "variants")) { section ->
        when (section) {
            "control" -> ComponentFixtureHeader(
                scenario = scenario,
                state = {
                    stringResource(R.string.demo_component_action_count, actionCount.value)
                },
                onReset = onReset,
            ) {
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(
                        R.string.demo_component_icon_button_primary_description,
                    ),
                    onClick = { actionCount.value += 1 },
                    modifier = Modifier.componentScenarioTarget(
                        scenario,
                        DemoAutomationRole.PrimaryAction,
                    ),
                )
            }

            else -> ComponentIconButtonVariants(
                scenario = scenario,
                onClick = { actionCount.value += 1 },
            )
        }
    }
}

private fun UiTreeBuilder.ComponentIconButtonVariants(
    scenario: DemoScenarioSpec?,
    onClick: () -> Unit,
) {
    Column(
        spacing = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .margin(top = 16.dp, bottom = 24.dp)
            .componentScenarioTarget(scenario, DemoAutomationRole.Target),
    ) {
        ComponentFixtureTitle(R.string.demo_component_icon_button_variants)
        Row(spacing = 8.dp, verticalAlignment = VerticalAlignment.Center) {
            ButtonVariant.entries.forEach { variant ->
                Column(spacing = 2.dp) {
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = stringResource(
                            R.string.demo_component_icon_button_variant_description,
                            variant.name,
                        ),
                        variant = variant,
                        onClick = onClick,
                    )
                    Text(text = variant.name, style = UiTextStyle(fontSizeSp = 10.sp))
                }
            }
        }
        ComponentFixtureTitle(R.string.demo_component_icon_button_tints)
        Row(spacing = 12.dp) {
            listOf(
                Theme.colors.primary,
                Theme.colors.secondary,
                Theme.colors.onSurfaceVariant,
            ).forEachIndexed { index, tint ->
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(
                        R.string.demo_component_icon_button_tint_description,
                        index + 1,
                    ),
                    overrides = IconButtonOverrides(contentColor = tint),
                    onClick = onClick,
                )
            }
            IconButton(
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                contentDescription = stringResource(
                    R.string.demo_component_icon_button_disabled_description,
                ),
                enabled = false,
                onClick = {},
            )
        }
    }
}
