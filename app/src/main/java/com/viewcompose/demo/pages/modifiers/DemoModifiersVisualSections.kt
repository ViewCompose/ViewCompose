package com.viewcompose

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
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Divider
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.SurfaceDefaults
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

internal fun UiTreeBuilder.ModifierElevationSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "elevation 高程对比",
        subtitle = "不同 elevation 值的阴影效果对比。",
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
                        text = "${elev}dp",
                        style = UiTextStyle(fontSizeSp = 13.sp),
                    )
                }
            }
        }
    }
}

internal fun UiTreeBuilder.ModifierBorderClipSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "border 边框 + clip 裁切",
        subtitle = "border 设置边框宽度和颜色，clip 裁切溢出内容。",
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
                Text(text = "1dp 边框")
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .border(2.dp, Theme.colors.primary)
                    .cornerRadius(12.dp),
            ) {
                Text(text = "2dp 主色")
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .border(3.dp, Theme.colors.secondary)
                    .cornerRadius(24.dp),
            ) {
                Text(text = "3dp 圆角")
            }
        }
        Text(
            text = "clip 裁切溢出内容",
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
                text = "溢出的蓝色方块被裁切",
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

internal fun UiTreeBuilder.ModifierAlphaRippleSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "alpha 透明度 + rippleColor 按压颜色",
        subtitle = "alpha 控制 View 透明度，rippleColor 自定义按压反馈颜色。",
    ) {
        Text(
            text = "透明度梯度",
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
                        text = "${(a * 100).toInt()}%",
                        style = UiTextStyle(fontSizeSp = 13.sp),
                    )
                }
            }
        }
        Divider(modifier = Modifier.margin(bottom = 12.dp))
        Text(
            text = "自定义按压颜色（点击查看效果）",
            style = UiTextStyle(fontSizeSp = 14.sp),
            modifier = Modifier.margin(bottom = 8.dp),
        )
        Row(
            spacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                text = "红色波纹",
                onClick = {},
                rippleColor = 0xFFFF0000.toInt(),
                modifier = Modifier
                    .weight(1f),
            )
            Button(
                text = "绿色波纹",
                onClick = {},
                rippleColor = 0xFF00FF00.toInt(),
                modifier = Modifier
                    .weight(1f),
            )
            Button(
                text = "蓝色波纹",
                onClick = {},
                rippleColor = 0xFF0000FF.toInt(),
                modifier = Modifier
                    .weight(1f),
            )
        }
    }
}

internal fun UiTreeBuilder.ModifierBackgroundDrawableSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "backgroundDrawableRes 资源背景",
        subtitle = "当同时设置 backgroundColor 与 backgroundDrawableRes 时，drawable 优先；配合 cornerRadius 自动裁剪。",
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
                    .testTag(DemoTestTags.MODIFIERS_DRAWABLE_BACKGROUND_COLOR_ONLY),
            ) {
                Text(
                    text = "仅颜色",
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
                    .testTag(DemoTestTags.MODIFIERS_DRAWABLE_BACKGROUND_SAMPLE),
            ) {
                Text(
                    text = "颜色 + Drawable",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                )
            }
        }
        Text(
            text = "左侧只应用颜色（不自动裁剪）；右侧同时配置 drawable + cornerRadius，将自动裁剪。",
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
    }
}

internal fun UiTreeBuilder.ModifierCornerSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "cornerRadius 多级圆角",
        subtitle = "cornerRadius 支持统一、上下分组和四角独立设置。",
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
                Text(text = "0dp", style = UiTextStyle(fontSizeSp = 12.sp))
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(text = "8dp", style = UiTextStyle(fontSizeSp = 12.sp))
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(24.dp),
            ) {
                Text(text = "24dp", style = UiTextStyle(fontSizeSp = 12.sp))
            }
        }
        Text(
            text = "上下分组: top=16, bottom=0",
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
            Text(text = "顶部圆角")
        }
        Text(
            text = "四角独立: topStart=24, topEnd=0, bottomEnd=24, bottomStart=0",
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
            Text(text = "对角圆角")
        }
    }
}
