package com.viewcompose

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.offset
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.modifier.zIndex
import com.viewcompose.widget.core.Box
import com.viewcompose.widget.core.SurfaceDefaults
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.TextDefaults
import com.viewcompose.widget.core.Theme
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

internal fun UiTreeBuilder.ModifierOffsetZIndexSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "offset + zIndex",
        subtitle = "offset 平移 View 位置，zIndex 控制绘制顺序。",
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                .cornerRadius(12.dp)
                .padding(12.dp),
        ) {
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .backgroundColor(Theme.colors.primary)
                    .cornerRadius(8.dp)
                    .zIndex(1f),
            ) {
                Text(text = "zIndex=1")
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .backgroundColor(Theme.colors.secondary)
                    .cornerRadius(8.dp)
                    .offset(x = 40.dp, y = 20.dp)
                    .zIndex(2f),
            ) {
                Text(text = "zIndex=2")
            }
            Box(
                contentAlignment = BoxAlignment.Center,
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .backgroundColor(Theme.colors.surfaceVariant)
                    .cornerRadius(8.dp)
                    .offset(x = 80.dp, y = 40.dp)
                    .zIndex(0f),
            ) {
                Text(text = "zIndex=0")
            }
        }
        Text(
            text = "三个方块分别设置不同的 offset 和 zIndex，高 zIndex 的方块绘制在上方。",
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 8.dp),
        )
    }
}
