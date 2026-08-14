package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.alpha
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.backgroundDrawableRes
import com.viewcompose.ui.modifier.border
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.offset
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Modifiers · Elevation", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierElevationSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_modifiers_elevation_title),
        subtitle = stringResource(R.string.demo_modifiers_elevation_summary),
    ) {
        Row(
            spacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(0, 4, 8, 16).forEach { elev ->
                Box(
                    contentAlignment = BoxAlignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .backgroundColor(SurfaceDefaults.backgroundColor())
                        .cornerRadius(12.dp)
                        .elevation(elev.dp),
                ) {
                    Text(
                        text = stringResource(R.string.demo_modifiers_dp_value, elev),
                        style = UiTextStyle(fontSizeSp = 13.sp),
                    )
                }
            }
        }
    }
}

@ViewComposePreview(name = "Modifiers · Border and clip", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierBorderClipSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_modifiers_border_title),
        subtitle = stringResource(R.string.demo_modifiers_border_summary),
    ) {
        Row(
            spacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .border(1.dp, Theme.colors.outlineVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(text = stringResource(R.string.demo_modifiers_border_one))
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .border(2.dp, Theme.colors.primary)
                    .cornerRadius(12.dp),
            ) {
                Text(text = stringResource(R.string.demo_modifiers_border_two))
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .border(3.dp, Theme.colors.secondary)
                    .cornerRadius(24.dp),
            ) {
                Text(text = stringResource(R.string.demo_modifiers_border_three))
            }
        }
        Text(
            text = stringResource(R.string.demo_modifiers_clip_heading),
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .cornerRadius(16.dp)
                .clip()
                .backgroundColor(Theme.colors.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp, 200.dp)
                    .backgroundColor(Theme.colors.primary)
                    .offset(x = (-20).dp, y = (-20).dp),
            ) {}
            Text(
                text = stringResource(R.string.demo_modifiers_clip_content),
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@ViewComposePreview(name = "Modifiers · Alpha and ripple", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierAlphaRippleSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_modifiers_alpha_title),
        subtitle = stringResource(R.string.demo_modifiers_alpha_summary),
    ) {
        Text(
            text = stringResource(R.string.demo_modifiers_alpha_heading),
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Row(
            spacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 16.dp),
        ) {
            listOf(1.0f, 0.5f, 0.3f, 0.0f).forEach { a ->
                Box(
                    contentAlignment = BoxAlignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .backgroundColor(Theme.colors.primary)
                        .cornerRadius(8.dp)
                        .alpha(a),
                ) {
                    Text(
                        text = stringResource(
                            R.string.demo_modifiers_percent_value,
                            (a * 100).toInt(),
                        ),
                        style = UiTextStyle(fontSizeSp = 13.sp),
                    )
                }
            }
        }
        Divider(modifier = Modifier.margin(bottom = 12.dp))
        Text(
            text = stringResource(R.string.demo_modifiers_ripple_heading),
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Row(
            spacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                text = stringResource(R.string.demo_modifiers_ripple_red),
                onClick = {},
                rippleColor = 0xFFFF0000.toInt(),
                modifier = Modifier
                    .weight(1f),
            )
            Button(
                text = stringResource(R.string.demo_modifiers_ripple_green),
                onClick = {},
                rippleColor = 0xFF00FF00.toInt(),
                modifier = Modifier
                    .weight(1f),
            )
            Button(
                text = stringResource(R.string.demo_modifiers_ripple_blue),
                onClick = {},
                rippleColor = 0xFF0000FF.toInt(),
                modifier = Modifier
                    .weight(1f),
            )
        }
    }
}

@ViewComposePreview(name = "Modifiers · Background drawable", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierBackgroundDrawableSection(
    scenario: DemoScenarioSpec? = null,
) {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_modifiers_drawable_title),
        subtitle = stringResource(R.string.demo_modifiers_drawable_summary),
    ) {
        Row(
            spacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .backgroundColor(Theme.colors.error)
                    .cornerRadius(12.dp)
                    .border(1.dp, Theme.colors.outlineVariant)
                    .modifierScenarioTarget(scenario, DemoAutomationRole.SecondaryTarget),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_drawable_color_only),
                    color = Theme.colors.onSurface,
                    style = UiTextStyle(fontSizeSp = 13.sp),
                )
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp)
                    .backgroundColor(Theme.colors.error)
                    .backgroundDrawableRes(R.drawable.demo_media_image)
                    .cornerRadius(12.dp)
                    .border(1.dp, Theme.colors.primary)
                    .modifierScenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_drawable_combined),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
        }
        Text(
            text = stringResource(R.string.demo_modifiers_drawable_note),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
    }
}

@ViewComposePreview(name = "Modifiers · Corners", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierCornerSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_modifiers_corner_title),
        subtitle = stringResource(R.string.demo_modifiers_corner_summary),
    ) {
        Row(
            spacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 12.dp),
        ) {
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(0.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_dp_value, 0),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_dp_value, 8),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_modifiers_dp_value, 24),
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
        }
        Text(
            text = stringResource(R.string.demo_modifiers_corner_grouped),
            style = UiTextStyle(fontSizeSp = 13.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Box(
            contentAlignment = BoxAlignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .backgroundColor(Theme.colors.surfaceVariant)
                .cornerRadius(top = 16.dp, bottom = 0.dp)
                .margin(bottom = 12.dp),
        ) {
            Text(text = stringResource(R.string.demo_modifiers_corner_top))
        }
        Text(
            text = stringResource(R.string.demo_modifiers_corner_independent),
            style = UiTextStyle(fontSizeSp = 13.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Box(
            contentAlignment = BoxAlignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .backgroundColor(Theme.colors.surfaceVariant)
                .cornerRadius(topStart = 24.dp, topEnd = 0.dp, bottomEnd = 24.dp, bottomStart = 0.dp),
        ) {
            Text(text = stringResource(R.string.demo_modifiers_corner_diagonal))
        }
    }
}
