package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import android.graphics.Typeface
import android.widget.TextView
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.contentDescription
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxHeight
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.minHeight
import com.viewcompose.ui.modifier.minWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.width
import com.viewcompose.host.android.nativeView
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Modifiers · Size constraints", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierSizeConstraintsSection(
    scenario: DemoScenarioSpec? = null,
) {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_modifiers_size_title),
        subtitle = stringResource(R.string.demo_modifiers_size_summary),
    ) {
        Text(
            text = stringResource(R.string.demo_modifiers_min_width_heading),
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .minWidth(100.dp)
                    .height(40.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_min_width_100),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .minWidth(60.dp)
                    .height(40.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_min_width_60),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
        }
        Text(
            text = stringResource(R.string.demo_modifiers_min_height_heading),
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .minHeight(80.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_min_height_80),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .minHeight(40.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_min_height_40),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
        }
        Text(
            text = stringResource(R.string.demo_modifiers_fill_height_heading),
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .backgroundColor(Theme.colors.primary)
                    .cornerRadius(8.dp)
                    .modifierScenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_fill_height_full),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .width(80.dp)
                    .height(60.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_fixed_height_60),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
            Text(
                text = stringResource(R.string.demo_modifiers_fill_height_note),
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
            )
        }
        Divider(modifier = Modifier.margin(vertical = 12.dp))
        Text(
            text = stringResource(R.string.demo_modifiers_spacing_heading),
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .backgroundColor(0x220000FF)
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .backgroundColor(0x2200FF00)
                    .margin(8.dp)
                    .padding(12.dp),
            ) {
                Box(
                    contentAlignment = BoxAlignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .backgroundColor(0x22FF0000)
                        .margin(4.dp)
                        .padding(8.dp),
                ) {
                    Text(text = stringResource(R.string.demo_modifiers_spacing_inner))
                }
            }
        }
        Text(
            text = stringResource(R.string.demo_modifiers_spacing_note),
            style = UiTextStyle(fontSizeSp = 12.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 4.dp),
        )
    }
}

@ViewComposePreview(name = "Modifiers · Accessibility", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierAccessibilitySection(
    scenario: DemoScenarioSpec? = null,
) {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_modifiers_accessibility_title),
        subtitle = stringResource(R.string.demo_modifiers_accessibility_summary),
    ) {
        Row(
            spacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp)
                    .contentDescription(
                        stringResource(R.string.demo_modifiers_accessibility_description),
                    )
                    .modifierScenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                Text(text = stringResource(R.string.demo_modifiers_accessibility_present))
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(text = stringResource(R.string.demo_modifiers_accessibility_absent))
            }
        }
        Text(
            text = stringResource(R.string.demo_modifiers_accessibility_note),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 8.dp),
        )
    }
}

@ViewComposePreview(name = "Modifiers · Native view", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierNativeViewSection(
    scenario: DemoScenarioSpec? = null,
) {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_modifiers_native_title),
        subtitle = stringResource(R.string.demo_modifiers_native_summary),
    ) {
        Box(
            contentAlignment = BoxAlignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .backgroundColor(Theme.colors.surfaceVariant)
                .cornerRadius(8.dp),
        ) {
            Text(
                text = stringResource(R.string.demo_modifiers_native_sample),
                modifier = Modifier
                    .nativeView(NATIVE_BOLD_TEXT_KEY) { view ->
                        if (view is TextView) {
                            view.typeface = Typeface.DEFAULT_BOLD
                            view.letterSpacing = 0.1f
                        }
                    }
                    .modifierScenarioTarget(scenario, DemoAutomationRole.SecondaryTarget),
            )
        }
        Text(
            text = stringResource(R.string.demo_modifiers_native_note),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 8.dp),
        )
    }
}

private const val NATIVE_BOLD_TEXT_KEY = "bold_text"
