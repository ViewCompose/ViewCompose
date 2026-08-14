package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.host.android.resources.stringResource
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
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Modifiers · Offset and z-index", group = "Demo/Sections")
internal fun UiTreeBuilder.ModifierOffsetZIndexSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_modifiers_layering_title),
        subtitle = stringResource(R.string.demo_modifiers_layering_summary),
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
                Text(text = stringResource(R.string.demo_modifiers_layering_value, 1))
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
                Text(text = stringResource(R.string.demo_modifiers_layering_value, 2))
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
                Text(text = stringResource(R.string.demo_modifiers_layering_value, 0))
            }
        }
        Text(
            text = stringResource(R.string.demo_modifiers_layering_note),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.margin(top = 8.dp),
        )
    }
}
