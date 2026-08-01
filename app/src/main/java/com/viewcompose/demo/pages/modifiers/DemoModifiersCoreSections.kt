package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import android.graphics.Typeface
import android.widget.TextView
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
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.Button
import com.viewcompose.widget.core.Column
import com.viewcompose.widget.core.Divider
import com.viewcompose.widget.core.Row
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Modifiers · Size constraints", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierSizeConstraintsSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "尺寸约束",
        subtitle = "minWidth、minHeight、fillMaxHeight 和 size/width/height 的效果。",
    ) {
        Text(
            text = "minWidth 最小宽度约束",
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
                Text(text = "min 100dp", style = UiTextStyle(fontSizeSp = 12.sp))
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .minWidth(60.dp)
                    .height(40.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(text = "min 60dp", style = UiTextStyle(fontSizeSp = 12.sp))
            }
        }
        Text(
            text = "minHeight 最小高度约束",
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
                Text(text = "min 80dp 高", style = UiTextStyle(fontSizeSp = 12.sp))
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .minHeight(40.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(text = "min 40dp 高", style = UiTextStyle(fontSizeSp = 12.sp))
            }
        }
        Text(
            text = "fillMaxHeight 单独使用",
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
                    .cornerRadius(8.dp),
            ) {
                Text(text = "满高", style = UiTextStyle(fontSizeSp = 12.sp))
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .width(80.dp)
                    .height(60.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(text = "60dp", style = UiTextStyle(fontSizeSp = 12.sp))
            }
            Text(
                text = "左侧 fillMaxHeight 撑满父容器高度，右侧固定 60dp。",
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
            )
        }
        Divider(modifier = Modifier.margin(vertical = 12.dp))
        Text(
            text = "padding + margin 3 级级联",
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
                    Text(text = "最内层内容")
                }
            }
        }
        Text(
            text = "蓝色=外层 padding 16, 绿色=中层 margin 8 + padding 12, 红色=内层 margin 4 + padding 8",
            style = UiTextStyle(fontSizeSp = 12.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 4.dp),
        )
    }
}

@ViewComposePreview(name = "Modifiers · Accessibility", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierAccessibilitySection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "contentDescription 无障碍描述",
        subtitle = "contentDescription 为 View 设置无障碍描述，可被 TalkBack 读取。",
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
                    .contentDescription("这是一个示例区块，用于展示无障碍描述"),
            ) {
                Text(text = "有无障碍描述")
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp),
            ) {
                Text(text = "无描述")
            }
        }
        Text(
            text = "左侧 Box 设置了 contentDescription，可被 TalkBack 朗读。开启 TalkBack 验证。",
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 8.dp),
        )
    }
}

@ViewComposePreview(name = "Modifiers · Native view", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierNativeViewSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "nativeView 原生属性逃生通道",
        subtitle = "nativeView 直接配置原生 Android View 属性，突破框架 Modifier 覆盖范围。",
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
                text = "nativeView 设置粗体+字间距", modifier = Modifier
                    .nativeView("bold_text") { view ->
                        if (view is TextView) {
                            view.typeface = Typeface.DEFAULT_BOLD
                            view.letterSpacing = 0.1f
                        }
                    })
        }
        Text(
            text = "通过 nativeView 直接修改 TextView 的 typeface 和 letterSpacing。",
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 8.dp),
        )
    }
}

@ViewComposePreview(name = "Modifiers · Verification", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierVerificationSection() {
    VerificationNotesSection(
        what = "Modifier 展示应覆盖全部未展示的 Modifier 函数的视觉效果和行为。",
        howToVerify = listOf(
            "观察 elevation 对比行，确认阴影随值增大而明显。",
            "确认 border 边框宽度和颜色正确显示。",
            "确认 clip 裁切掉了溢出的蓝色方块。",
            "确认 alpha 透明度梯度从 100% 到 0% 递减。",
            "点击带颜色波纹的按钮，确认按压颜色正确。",
            "确认 minWidth/minHeight 约束生效。",
            "确认 fillMaxHeight 撑满父容器高度。",
            "开启 TalkBack 验证 contentDescription。",
            "确认 nativeView 的粗体效果。",
            "确认 offset + zIndex 的叠放顺序正确。",
        ),
        expected = listOf(
            "所有 Modifier 效果在亮色/暗色主题下均可见。",
            "cornerRadius 三级（统一/上下/四角）均正确渲染。",
            "padding/margin 三级级联视觉层次清晰。",
        ),
    )
}
