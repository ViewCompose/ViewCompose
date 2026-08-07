package com.viewcompose

import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonDefaults
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.Chip
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.IconButtonDefaults
import com.viewcompose.ui.foundation.FloatingActionButton
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.LocalFocusManager
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.RadioButton
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.focusRequester
import com.viewcompose.ui.modifier.focusable
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp

/** Emits one stable component fixture under an explicitly identified theme source. */
internal fun UiTreeBuilder.Material3DefaultThemePage(source: DemoThemeSource) {
    val defaultButtonClicks = remember { mutableStateOf(0) }
    val stateLayerFocusRequester = remember { FocusRequester() }
    val stateLayerSegmentedIndex = remember { mutableStateOf(0) }
    LazyColumn(
        items = listOf(
            "intro",
            "source",
            "buttons",
            "stateLayers",
            "compact",
            "selection",
            "navigation",
            "targetProbes",
        ),
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
                    text = "主题与 Token 验证",
                    style = Theme.typography.headlineSmall,
                    color = Theme.colors.onBackground,
                )
                Text(
                    text = "同一组组件固定不变；仅切换主题来源。截图先核对来源和 token 数值，再检查组件消费结果。",
                    style = Theme.typography.bodyMedium,
                    color = Theme.colors.onSurfaceVariant,
                )
                ThemeFixtureBadge(source)
            }

            "source" -> ThemeSourceSnapshotSection(source)

            "buttons" -> Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 12.dp),
            ) {
                Text(text = "Buttons", style = Theme.typography.titleMedium)
                ThemeFixtureBadge(source)
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

            "stateLayers" -> Material3StateLayerVerification(
                source = source,
                focusRequester = stateLayerFocusRequester,
                segmentedIndex = stateLayerSegmentedIndex.value,
                onSegmentSelected = { index -> stateLayerSegmentedIndex.value = index },
            )

            "compact" -> Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
            ) {
                Text(text = "Compact controls", style = Theme.typography.titleMedium)
                ThemeFixtureBadge(source)
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

            "selection" -> Material3DefaultSelectionControls(source)

            "navigation" -> Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp, bottom = 24.dp),
            ) {
                Text(text = "Navigation", style = Theme.typography.titleMedium)
                ThemeFixtureBadge(source)
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

            else -> Material3TouchTargetProbes(source)
        }
    }
}

private fun UiTreeBuilder.Material3StateLayerVerification(
    source: DemoThemeSource,
    focusRequester: FocusRequester,
    segmentedIndex: Int,
    onSegmentSelected: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val primary = ButtonDefaults.stateLayerColors(ButtonVariant.Primary)
    val tonal = ButtonDefaults.stateLayerColors(ButtonVariant.Tonal)
    val outlined = ButtonDefaults.stateLayerColors(ButtonVariant.Outlined)
    val icon = IconButtonDefaults.stateLayerColors()

    Column(
        spacing = 12.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
    ) {
        Text(text = "Interaction state layers", style = Theme.typography.titleMedium)
        ThemeFixtureBadge(source)
        Text(
            text = "长按控件检查 pressed；点击“聚焦 Primary”固定 focused；连接鼠标或触控笔悬停检查 hovered。状态层只能覆盖可见胶囊，不应染色外侧触控区域。",
            style = Theme.typography.bodySmall,
            color = Theme.colors.onSurfaceVariant,
        )
        Row(spacing = 8.dp, verticalAlignment = VerticalAlignment.Center) {
            Button(
                text = "Primary",
                onClick = {},
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable()
                    .testTag(DemoTestTags.MATERIAL3_STATE_LAYER_PRIMARY),
            )
            Button(
                text = "Tonal",
                variant = ButtonVariant.Tonal,
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_TONAL),
            )
            Button(
                text = "Outlined",
                variant = ButtonVariant.Outlined,
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_OUTLINED),
            )
        }
        Row(spacing = 8.dp, verticalAlignment = VerticalAlignment.Center) {
            IconButton(
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                contentDescription = "State-layer icon button",
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_ICON),
            )
            Button(
                text = "聚焦 Primary",
                variant = ButtonVariant.Outlined,
                onClick = { focusRequester.requestFocus() },
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_FOCUS_ACTION),
            )
            Button(
                text = "清除焦点",
                variant = ButtonVariant.Text,
                onClick = { focusManager.clearFocus(force = true) },
            )
        }
        Text(
            text = "聚焦后直接观察 Primary 的 focused 状态层；清除焦点后应恢复默认外观。",
            style = Theme.typography.bodySmall,
            color = Theme.colors.onSurfaceVariant,
        )
        Text(text = "Composite controls", style = Theme.typography.labelLarge)
        Row(spacing = 12.dp, verticalAlignment = VerticalAlignment.Center) {
            Chip(
                label = "Assist chip",
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_CHIP),
            )
            FloatingActionButton(
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_FAB),
            ) {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = "State-layer FAB",
                )
            }
        }
        SegmentedControl(
            items = listOf("Selected", "Other"),
            selectedIndex = segmentedIndex,
            onSelectionChange = onSegmentSelected,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.MATERIAL3_STATE_LAYER_SEGMENTED),
        )
        Text(
            text = "长按或悬停 Chip、FAB 和两个分段；切换分段后，状态层应跟随 selected / unselected 内容角色。",
            style = Theme.typography.bodySmall,
            color = Theme.colors.onSurfaceVariant,
        )
        DiagnosticFactGroup(
            title = "Resolved state-layer contract",
            facts = listOf(
                DiagnosticFact("Opacity P/F/H", "10% / 10% / 8%"),
                DiagnosticFact("Primary base", Theme.colors.onPrimary.asColorHex()),
                DiagnosticFact("Primary P/F/H", primary.asStateLayerHex()),
                DiagnosticFact("Tonal base", Theme.colors.onSecondaryContainer.asColorHex()),
                DiagnosticFact("Tonal P/F/H", tonal.asStateLayerHex()),
                DiagnosticFact("Outlined base", Theme.colors.primary.asColorHex()),
                DiagnosticFact("Outlined P/F/H", outlined.asStateLayerHex()),
                DiagnosticFact("Icon base", Theme.colors.onSurfaceVariant.asColorHex()),
                DiagnosticFact("Icon P/F/H", icon.asStateLayerHex()),
                DiagnosticFact("Chip base", Theme.colors.onSurfaceVariant.asColorHex()),
                DiagnosticFact("FAB base", Theme.colors.onPrimaryContainer.asColorHex()),
                DiagnosticFact("Segment base", "${Theme.colors.onSecondaryContainer.asColorHex()} selected"),
            ),
        )
    }
}

private fun com.viewcompose.ui.node.UiStateLayerColors.asStateLayerHex(): String {
    return listOf(pressedColor, focusedColor, hoveredColor)
        .joinToString(separator = " / ") { color -> color.asColorHex() }
}

private fun UiTreeBuilder.ThemeFixtureBadge(source: DemoThemeSource) {
    val mode = if (Theme.current.metadata.isDark == true) "dark" else "light"
    Text(
        text = "theme-token-matrix-v1 · ${source.id} · $mode",
        style = Theme.typography.labelSmall,
        color = Theme.colors.onSurfaceVariant,
    )
}

private fun UiTreeBuilder.ThemeSourceSnapshotSection(source: DemoThemeSource) {
    val colors = Theme.colors
    val rolesDistinct = colors.secondary != colors.secondaryContainer
    Column(
        spacing = 12.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 12.dp),
    ) {
        Text(text = "Source + Token Snapshot", style = Theme.typography.titleMedium)
        DiagnosticFactGroup(
            title = "Screenshot identity",
            facts = listOf(
                DiagnosticFact("Fixture", "theme-token-matrix-v1"),
                DiagnosticFact("Source", "${source.id} · ${source.label}"),
                DiagnosticFact("Definition", source.description),
                DiagnosticFact("Metadata origin", Theme.current.metadata.origin.name),
                DiagnosticFact("Mode", if (Theme.current.metadata.isDark == true) "Dark" else "Light"),
                DiagnosticFact("Primary", colors.primary.asColorHex()),
                DiagnosticFact("PrimaryContainer", colors.primaryContainer.asColorHex()),
                DiagnosticFact("Secondary", colors.secondary.asColorHex()),
                DiagnosticFact("SecondaryContainer", colors.secondaryContainer.asColorHex()),
                DiagnosticFact("OnSecondaryContainer", colors.onSecondaryContainer.asColorHex()),
                DiagnosticFact("Surface", colors.surface.asColorHex()),
                DiagnosticFact("SurfaceContainer", colors.surfaceContainer.asColorHex()),
                DiagnosticFact("Role check", if (rolesDistinct) "DISTINCT" else "COLLISION"),
            ),
            valueTagsByLabel = mapOf(
                "Source" to DemoTestTags.MATERIAL3_THEME_SOURCE,
                "Metadata origin" to DemoTestTags.MATERIAL3_THEME_ORIGIN,
                "Mode" to DemoTestTags.MATERIAL3_THEME_MODE,
                "Secondary" to DemoTestTags.MATERIAL3_THEME_SECONDARY,
                "SecondaryContainer" to DemoTestTags.MATERIAL3_THEME_SECONDARY_CONTAINER,
                "Role check" to DemoTestTags.MATERIAL3_THEME_ROLE_COLLISION,
            ),
        )
        ThemeSwatchRow(
            label = "Primary roles",
            swatches = listOf(
                ThemeSwatch("P", colors.primary),
                ThemeSwatch("PC", colors.primaryContainer),
                ThemeSwatch("OnP", colors.onPrimaryContainer),
            ),
        )
        ThemeSwatchRow(
            label = "Secondary roles",
            swatches = listOf(
                ThemeSwatch("S", colors.secondary),
                ThemeSwatch("SC", colors.secondaryContainer),
                ThemeSwatch("OnS", colors.onSecondaryContainer),
            ),
        )
    }
}

private fun UiTreeBuilder.Material3TouchTargetProbes(source: DemoThemeSource) {
    val firstChecked = remember { mutableStateOf(false) }
    val secondChecked = remember { mutableStateOf(false) }
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 20.dp, bottom = 24.dp),
    ) {
        Text(text = "Touch target probes", style = Theme.typography.titleMedium)
        ThemeFixtureBadge(source)
        Checkbox(
            text = "Adjacent first",
            checked = firstChecked.value,
            onCheckedChange = { firstChecked.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_TARGET_ADJACENT_FIRST),
        )
        Checkbox(
            text = "Adjacent second",
            checked = secondChecked.value,
            onCheckedChange = { secondChecked.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_TARGET_ADJACENT_SECOND),
        )
        Text(
            text = "Adjacent: ${firstChecked.value}/${secondChecked.value}",
            style = Theme.typography.bodySmall,
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_TARGET_ADJACENT_STATUS),
        )
        Checkbox(
            text = "Explicit 32dp target",
            checked = false,
            onCheckedChange = {},
            modifier = Modifier
                .height(32.dp)
                .testTag(DemoTestTags.MATERIAL3_TARGET_EXPLICIT_COMPACT),
        )
        Box(
            contentAlignment = BoxAlignment.CenterStart,
            modifier = Modifier
                .height(32.dp)
                .clip()
                .testTag(DemoTestTags.MATERIAL3_TARGET_CLIPPED_PARENT),
        ) {
            Checkbox(
                text = "Clipped 32dp parent",
                checked = false,
                onCheckedChange = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_TARGET_CLIPPED_CHILD),
            )
        }
    }
}

private fun UiTreeBuilder.Material3DefaultSelectionControls(source: DemoThemeSource) {
    val checked = remember { mutableStateOf(true) }
    val sliderValue = remember { mutableStateOf(50) }
    Column(
        spacing = 8.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
    ) {
        Text(text = "Selection controls", style = Theme.typography.titleMedium)
        ThemeFixtureBadge(source)
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
