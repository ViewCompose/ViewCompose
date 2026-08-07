package com.viewcompose

import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.Chip
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.RadioButton
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp

/** Emits controls resolved only from the host or static Material 3 theme adapter. */
internal fun UiTreeBuilder.Material3DefaultThemePage() {
    val defaultButtonClicks = remember { mutableStateOf(0) }
    LazyColumn(
        items = listOf("intro", "buttons", "compact", "selection", "navigation"),
        key = { it },
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background)
            .systemBarsInsetsPadding()
            .padding(horizontal = 16.dp)
            .testTag(DemoTestTags.MATERIAL3_DEFAULT_ROOT),
    ) { section ->
        when (section) {
            "intro" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 16.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "默认 Material3 验证",
                    style = Theme.typography.headlineSmall,
                    color = Theme.colors.onBackground,
                )
                Text(
                    text = "本页不使用 DemoThemeTokens；圆角、色彩、字体和尺寸均来自 Material3 适配层。",
                    style = Theme.typography.bodyMedium,
                    color = Theme.colors.onSurfaceVariant,
                )
            }

            "buttons" -> Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 12.dp),
            ) {
                Text(text = "Buttons", style = Theme.typography.titleMedium)
                Row(spacing = 8.dp, verticalAlignment = VerticalAlignment.Center) {
                    Button(
                        text = "Default",
                        onClick = { defaultButtonClicks.value += 1 },
                        modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_BUTTON),
                    )
                    Button(text = "Outlined", variant = ButtonVariant.Outlined, onClick = {})
                    Button(text = "Disabled", enabled = false)
                }
                Text(
                    text = "Default clicks: ${defaultButtonClicks.value}",
                    style = Theme.typography.bodySmall,
                    color = Theme.colors.onSurfaceVariant,
                    modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_BUTTON_STATUS),
                )
            }

            "compact" -> Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
            ) {
                Text(text = "Compact controls", style = Theme.typography.titleMedium)
                Row(spacing = 12.dp, verticalAlignment = VerticalAlignment.Center) {
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = "Default Material3 icon button",
                        onClick = {},
                        modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_ICON_BUTTON),
                    )
                    Chip(
                        label = "Assist chip",
                        onClick = {},
                        modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_CHIP),
                    )
                }
            }

            "selection" -> Material3DefaultSelectionControls()

            else -> Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp, bottom = 24.dp),
            ) {
                Text(text = "Navigation", style = Theme.typography.titleMedium)
                NavigationBar(
                    selectedIndex = 0,
                    onItemSelected = {},
                    modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_NAVIGATION),
                ) {
                    Item(label = "Home", icon = ImageSource.Resource(R.drawable.demo_media_icon))
                    Item(label = "Search", icon = ImageSource.Resource(R.drawable.demo_media_icon))
                    Item(label = "Profile", icon = ImageSource.Resource(R.drawable.demo_media_icon))
                }
            }
        }
    }
}

private fun UiTreeBuilder.Material3DefaultSelectionControls() {
    val checked = remember { mutableStateOf(true) }
    val sliderValue = remember { mutableStateOf(50) }
    Column(
        spacing = 8.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
    ) {
        Text(text = "Selection controls", style = Theme.typography.titleMedium)
        Checkbox(
            text = "Checkbox",
            checked = checked.value,
            onCheckedChange = { checked.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_CHECKBOX),
        )
        RadioButton(
            text = "Radio button",
            checked = checked.value,
            onCheckedChange = { checked.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_RADIO),
        )
        Switch(
            text = "Switch",
            checked = checked.value,
            onCheckedChange = { checked.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_SWITCH),
        )
        Slider(
            value = sliderValue.value,
            onValueChange = { sliderValue.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.MATERIAL3_DEFAULT_SLIDER),
        )
    }
}
