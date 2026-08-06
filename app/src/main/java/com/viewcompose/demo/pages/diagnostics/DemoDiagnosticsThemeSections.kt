package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import android.view.ViewGroup
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.clip
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.elevation
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.size
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.foundation.Badge
import com.viewcompose.ui.foundation.BadgedBox
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonSize
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Card
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.Chip
import com.viewcompose.ui.foundation.ChipVariant
import com.viewcompose.ui.foundation.CircularProgressIndicator
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.DropdownMenuDefaults
import com.viewcompose.ui.foundation.DropdownMenuItem
import com.viewcompose.ui.foundation.ExtendedFloatingActionButton
import com.viewcompose.ui.foundation.FabSize
import com.viewcompose.ui.foundation.FloatingActionButton
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.LinearProgressIndicator
import com.viewcompose.ui.foundation.ListItem
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.OutlinedCard
import com.viewcompose.ui.foundation.RadioButton
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SearchBar
import com.viewcompose.ui.foundation.SegmentedControl
import com.viewcompose.ui.foundation.SegmentedControlSize
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.TabRow
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldSize
import com.viewcompose.ui.foundation.TextFieldVariant
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.TooltipDefaults
import com.viewcompose.ui.foundation.TopAppBar
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.unit.sp
import com.viewcompose.runtime.mutableStateOf

internal val DIAGNOSTICS_THEME_SECTION_KEYS = listOf(
    "theme_snapshot_core",
    "theme_snapshot_palette",
    "theme_snapshot_sizing",
    "theme_surface",
    "theme_action",
    "theme_input",
    "theme_navigation",
    "theme_shape_size",
)

internal fun UiTreeBuilder.DiagnosticsThemeSection(
    root: ViewGroup?,
    section: String,
) {
    when (section) {
        "theme_snapshot_core" -> DiagnosticsThemeSnapshotCoreSection(root)
        "theme_snapshot_palette" -> DiagnosticsThemeSnapshotPaletteSection()
        "theme_snapshot_sizing" -> DiagnosticsThemeSnapshotSizingSection()
        "theme_surface" -> DiagnosticsThemeSurfaceSection()
        "theme_action" -> DiagnosticsThemeActionSection()
        "theme_input" -> DiagnosticsThemeInputSection()
        "theme_navigation" -> DiagnosticsThemeNavigationSection()
        "theme_shape_size" -> DiagnosticsThemeShapeSizeSection()
        else -> error("Unknown diagnostics theme section: $section")
    }
}

private fun UiTreeBuilder.DiagnosticsThemeSnapshotCoreSection(root: ViewGroup?) {
    val modeLabel = root?.context?.let { context ->
        DemoThemeTokens.modeLabel(DemoThemeSession.mode, context)
    } ?: DemoThemeTokens.modeLabel(
        mode = DemoThemeSession.mode,
        isSystemDark = false,
    )
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "Theme Snapshot",
        subtitle = "集中查看当前模式和最常用的语义色，作为后续组件视觉诊断的基线。",
    ) {
        DiagnosticFactGroup(
            title = "当前主题基线",
            facts = listOf(
                DiagnosticFact("Mode", modeLabel),
                DiagnosticFact("Background", Theme.colors.background.asColorHex()),
                DiagnosticFact("Surface", Theme.colors.surface.asColorHex()),
                DiagnosticFact("SurfaceVariant", Theme.colors.surfaceVariant.asColorHex()),
                DiagnosticFact("OnSurface", Theme.colors.onSurface.asColorHex()),
                DiagnosticFact("OnSurfaceVariant", Theme.colors.onSurfaceVariant.asColorHex()),
                DiagnosticFact("Primary", Theme.colors.primary.asColorHex()),
                DiagnosticFact("OnPrimary", Theme.colors.onPrimary.asColorHex()),
                DiagnosticFact("Secondary", Theme.colors.secondary.asColorHex()),
                DiagnosticFact("OnSecondary", Theme.colors.onSecondary.asColorHex()),
            ),
            valueTagsByLabel = mapOf(
                "Mode" to DemoTestTags.DIAGNOSTICS_THEME_MODE,
            ),
        )
    }
}

private fun UiTreeBuilder.DiagnosticsThemeSnapshotPaletteSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "Theme Palette",
        subtitle = "检查扩展语义色和关键色板组合。",
    ) {
        DiagnosticFactGroup(
            title = "扩展语义色",
            facts = listOf(
                DiagnosticFact("ErrorContainer", Theme.colors.errorContainer.asColorHex()),
                DiagnosticFact("OnErrorContainer", Theme.colors.onErrorContainer.asColorHex()),
                DiagnosticFact("Outline", Theme.colors.outline.asColorHex()),
                DiagnosticFact("OutlineVariant", Theme.colors.outlineVariant.asColorHex()),
                DiagnosticFact("InverseSurface", Theme.colors.inverseSurface.asColorHex()),
                DiagnosticFact("InverseOnSurface", Theme.colors.inverseOnSurface.asColorHex()),
                DiagnosticFact("Ripple", Theme.colors.ripple.asColorHex()),
            ),
        )
        ThemeSwatchRow(
            label = "Surface / Inverse",
            swatches = listOf(
                ThemeSwatch("Background", Theme.colors.background),
                ThemeSwatch("Surface", Theme.colors.surface),
                ThemeSwatch("Variant", Theme.colors.surfaceVariant),
                ThemeSwatch("Inverse", Theme.colors.inverseSurface),
            ),
        )
        ThemeSwatchRow(
            label = "Accent / Error / Outline",
            swatches = listOf(
                ThemeSwatch("Primary", Theme.colors.primary),
                ThemeSwatch("Secondary", Theme.colors.secondary),
                ThemeSwatch("Error", Theme.colors.error),
                ThemeSwatch("Outline", Theme.colors.outline),
            ),
        )
    }
}

private fun UiTreeBuilder.DiagnosticsThemeSnapshotSizingSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = "Theme Shape + Sizing",
        subtitle = "检查 shape tier 和关键 control sizing。",
    ) {
        DiagnosticFactGroup(
            title = "Shape + Control Sizing",
            facts = listOf(
                DiagnosticFact("small / medium / large", "${Theme.shapes.small.demoLabel()} / ${Theme.shapes.medium.demoLabel()} / ${Theme.shapes.large.demoLabel()}"),
                DiagnosticFact("Button", "${Theme.controls.button.compactHeight}/${Theme.controls.button.mediumHeight}/${Theme.controls.button.largeHeight}px"),
                DiagnosticFact("TextField", "${Theme.controls.textField.compactHeight}/${Theme.controls.textField.mediumHeight}/${Theme.controls.textField.largeHeight}px"),
                DiagnosticFact("SearchBar", "${Theme.controls.searchBar.height}px"),
                DiagnosticFact("NavigationBar", "${Theme.controls.navigationBar.height}px"),
                DiagnosticFact("TopAppBar", "${Theme.controls.appBar.topHeight}px"),
                DiagnosticFact("ListItem", "${Theme.controls.listItem.minHeight}px"),
                DiagnosticFact("Menu", "${Theme.controls.menu.minWidth}px / ${Theme.controls.menu.itemHeight}px"),
                DiagnosticFact("Tooltip", "${Theme.controls.tooltip.horizontalPadding}px / ${Theme.controls.tooltip.verticalPadding}px"),
                DiagnosticFact("Badge", "${Theme.controls.badge.dotSize}px / ${Theme.controls.badge.pillHeight}px"),
            ),
        )
    }
}

private fun UiTreeBuilder.DiagnosticsThemeSurfaceSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "Surface 家族诊断",
        subtitle = "验证 surface/container/content 语义、outline 语义和 inverse 语义是否真正进入组件默认值。",
    ) {
        TopAppBar(
            title = "Theme Top App Bar",
            navigationIcon = {
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = "导航图标",
                    onClick = {},
                )
            },
            actions = {
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = "操作",
                    onClick = {},
                )
            },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.DIAGNOSTICS_THEME_SURFACE_SAMPLE)
                    .padding(12.dp),
            ) {
                Column(spacing = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Default Surface")
                    Text(
                        text = "onSurface 文本应可读。",
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                    )
                }
            }
            Surface(
                variant = SurfaceVariant.Variant,
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
            ) {
                Column(spacing = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Variant Surface")
                    Text(
                        text = "onSurfaceVariant 辅助文本。",
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                    )
                }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Column(
                spacing = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(text = "Card")
                Text(
                    text = "medium shape tier 和 onSurface 默认值应同时生效。",
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }
        }
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = "OutlinedCard 使用 outline 边框。",
                modifier = Modifier.padding(12.dp),
            )
        }
        ListItem(
            overlineText = "ListItem",
            headlineText = "Surface + text semantic",
            supportingText = "headline/supporting 应分别跟随 onSurface 与 onSurfaceVariant。",
            trailingContent = {
                Text(
                    text = "A1",
                    style = UiTextStyle(fontSizeSp = 12.sp),
                    color = TextDefaults.secondaryColor(),
                )
            },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        MenuVisualSample()
        TooltipVisualSample()
    }
}

private fun UiTreeBuilder.DiagnosticsThemeActionSection() {
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "Action 家族诊断",
        subtitle = "验证按钮 variant、FAB、Chip 和 badge 类样本是否匹配当前语义色与小圆角 tier。",
    ) {
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Button(
                text = "Primary",
                onClick = {},
                variant = ButtonVariant.Primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.DIAGNOSTICS_THEME_BUTTON_PRIMARY),
            )
            Button(text = "Secondary", onClick = {}, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f))
        }
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Button(text = "Tonal", onClick = {}, variant = ButtonVariant.Tonal, modifier = Modifier.weight(1f))
            Button(text = "Outlined", onClick = {}, variant = ButtonVariant.Outlined, modifier = Modifier.weight(1f))
            Button(text = "Text", onClick = {}, variant = ButtonVariant.Text, modifier = Modifier.weight(1f))
        }
        Row(
            spacing = 12.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            FloatingActionButton(onClick = {}, size = FabSize.Small) {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = "Fab",
                )
            }
            FloatingActionButton(
                onClick = {},
                size = FabSize.Medium,
                modifier = Modifier.testTag(DemoTestTags.DIAGNOSTICS_THEME_FAB),
            ) {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = "Fab",
                )
            }
            FloatingActionButton(onClick = {}, size = FabSize.Large) {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = "Fab",
                )
            }
            ExtendedFloatingActionButton(
                text = "Extended FAB",
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                onClick = {},
            )
        }
        Row(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Chip(
                label = "Assist Chip",
                onClick = {},
                variant = ChipVariant.Assist,
                modifier = Modifier.weight(1f),
            )
            Chip(
                label = "Filter Chip",
                onClick = {},
                variant = ChipVariant.Filter,
                selected = true,
                modifier = Modifier.weight(1f),
            )
            BadgedBox(
                badge = { Badge(count = 8) },
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Button(text = "Badge", onClick = {}, variant = ButtonVariant.Tonal)
            }
        }
    }
}

private fun UiTreeBuilder.DiagnosticsThemeInputSection() {
    val searchQueryState = rememberTextFieldState("Theme token")
    val normalFieldState = rememberTextFieldState("theme@viewcompose.dev")
    val errorFieldState = rememberTextFieldState("error@viewcompose.dev")
    val disabledFieldState = rememberTextFieldState("Disabled field")
    val checkboxState = remember { mutableStateOf(true) }
    val switchState = remember { mutableStateOf(true) }
    val radioState = remember { mutableStateOf(true) }
    val sliderState = remember { mutableStateOf(68) }
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "Input / Selection 家族诊断",
        subtitle = "验证 field container、error container、outline variant 和 selection controls 的默认语义。",
    ) {
        TextField(
            state = normalFieldState,
            variant = TextFieldVariant.Outlined,
            size = TextFieldSize.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        TextField(
            state = errorFieldState,
            variant = TextFieldVariant.Tonal,
            size = TextFieldSize.Medium,
            isError = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.DIAGNOSTICS_THEME_TEXTFIELD_ERROR)
                .padding(bottom = 8.dp),
        )
        TextField(
            state = disabledFieldState,
            enabled = false,
            size = TextFieldSize.Compact,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        SearchBar(
            state = searchQueryState,
            onSearch = {},
            placeholder = "Search token",
            leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.DIAGNOSTICS_THEME_SEARCHBAR)
                .padding(bottom = 8.dp),
        )
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Checkbox(
                text = "Checkbox",
                checked = checkboxState.value,
                onCheckedChange = { checkboxState.value = it },
                modifier = Modifier.weight(1f),
            )
            Switch(
                text = "Switch",
                checked = switchState.value,
                onCheckedChange = { switchState.value = it },
                modifier = Modifier.weight(1f),
            )
        }
        RadioButton(
            text = "RadioButton",
            checked = radioState.value,
            onCheckedChange = { radioState.value = it },
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Slider(
            value = sliderState.value,
            onValueChange = { sliderState.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        LinearProgressIndicator(
            progress = sliderState.value / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        Row(
            spacing = 12.dp,
            verticalAlignment = VerticalAlignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CircularProgressIndicator(progress = sliderState.value / 100f)
            Text(
                text = "Selection controls 应沿用 primary / outlineVariant / surfaceVariant 语义。",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun UiTreeBuilder.DiagnosticsThemeNavigationSection() {
    val navIndexState = remember { mutableStateOf(1) }
    val segmentedIndexState = remember { mutableStateOf(0) }
    val tabIndexState = remember { mutableStateOf(0) }
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "Navigation / Collection 家族诊断",
        subtitle = "验证 selected/unselected、indicator、badge 和标签排版是否跟随当前主题默认值。",
    ) {
        NavigationBar(
            selectedIndex = navIndexState.value,
            onItemSelected = { navIndexState.value = it },
            modifier = Modifier
                .margin(bottom = 8.dp)
                .testTag(DemoTestTags.DIAGNOSTICS_THEME_NAVIGATION),
        ) {
            Item(label = "Home", icon = ImageSource.Resource(R.drawable.demo_media_icon))
            Item(label = "Search", icon = ImageSource.Resource(R.drawable.demo_media_icon), badgeCount = 3)
            Item(label = "Profile", icon = ImageSource.Resource(R.drawable.demo_media_icon))
        }
        SegmentedControl(
            items = listOf("Alpha", "Beta", "Gamma"),
            selectedIndex = segmentedIndexState.value,
            onSelectionChange = { segmentedIndexState.value = it },
            size = SegmentedControlSize.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.DIAGNOSTICS_THEME_SEGMENTED)
                .padding(bottom = 8.dp),
        )
        TabRow(
            selectedIndex = tabIndexState.value,
            onTabSelected = { tabIndexState.value = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Tab { selected ->
                Text(
                    text = if (selected) "Overview" else "概览",
                    modifier = Modifier.padding(12.dp),
                )
            }
            Tab { selected ->
                Text(
                    text = if (selected) "Theme" else "主题",
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

private fun UiTreeBuilder.DiagnosticsThemeShapeSizeSection() {
    val compactFieldState = rememberTextFieldState(
        "Compact / Medium / Large use Theme.controls.textField.*",
    )
    val mediumFieldState = rememberTextFieldState("Medium TextField")
    val largeFieldState = rememberTextFieldState("Large TextField")
    val searchState = rememberTextFieldState("Large shape sample")
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = "Shape / Size 诊断",
        subtitle = "通过同组件不同尺寸和不同 radius tier，对照当前 theme 的 shape / control sizing 是否真的进入默认值。",
    ) {
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            ShapeProbe(
                label = "Small",
                shape = Theme.shapes.small,
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.DIAGNOSTICS_THEME_SHAPE_SMALL),
            )
            ShapeProbe("Medium", Theme.shapes.medium, Modifier.weight(1f))
            ShapeProbe(
                label = "Large",
                shape = Theme.shapes.large,
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.DIAGNOSTICS_THEME_SHAPE_LARGE),
            )
        }
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Button(text = "Compact", onClick = {}, size = ButtonSize.Compact, modifier = Modifier.weight(1f))
            Button(text = "Medium", onClick = {}, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
            Button(text = "Large", onClick = {}, size = ButtonSize.Large, modifier = Modifier.weight(1f))
        }
        TextField(
            state = compactFieldState,
            size = TextFieldSize.Compact,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        TextField(
            state = mediumFieldState,
            size = TextFieldSize.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        TextField(
            state = largeFieldState,
            size = TextFieldSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        SegmentedControl(
            items = listOf("S", "M", "L"),
            selectedIndex = 1,
            onSelectionChange = {},
            size = SegmentedControlSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        SearchBar(
            state = searchState,
            placeholder = "Search shape",
            leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@ViewComposePreview(name = "Diagnostics · Theme verification", group = "Demo/Sections")
internal fun UiTreeBuilder.DiagnosticsThemeVerificationSection() {
    VerificationNotesSection(
        what = "该页是 Theme token 实际消费的权威人工回归入口，目标不是看数值对不对，而是确认 token 最终确实驱动了关键组件默认值。",
        howToVerify = listOf(
            "依次切换 Light / Dark / System，确认 Theme Snapshot 的颜色值和下方组件视觉一起变化，而不是只有数值变化。",
            "先看 Surface 家族，确认 Default/Variant surface、ListItem、TopAppBar 的前景文字都保持可读，OutlinedCard 边框跟随 outline。",
            "看 Action 家族，确认 Primary/Secondary/Tonal/Outlined/Text 五种按钮的强调层级明显不同，FAB 和 Extended FAB 跟随当前主题。",
            "看 Input / Selection 家族，确认错误态 TextField 与普通 TextField 的 container、text、hint 有明显语义差异，SearchBar 使用较大圆角与较高 control sizing。",
            "看 Navigation / Collection 家族，确认 NavigationBar 与 SegmentedControl 的 selected/unselected 对比稳定，badge 与 indicator 不会和背景融在一起。",
            "看 Shape / Size 诊断，确认 small/medium/large radius 探针和 Button/TextField/SegmentedControl/SearchBar 的尺寸都与当前 theme token 一致。",
            "再到 Feedback / Input / Navigation 页面抽查真实功能页，确认这里定义的主题语义没有在 live 页面里回退。",
        ),
        expected = listOf(
            "Theme Snapshot 是诊断基线，组件视觉与 token 变化保持同向。",
            "surface/content、outline、inverse、errorContainer 等语义都能从样本中直接看出来，而不是只能靠读代码确认。",
            "shape tier 和 control sizing 不再停留在 token 定义层，而是能从真实组件高度、圆角、间距中直接观察到。",
            "真实功能页中的 Dialog / Popup / BottomSheet、SearchBar、NavigationBar / SegmentedControl 与此页口径一致。",
        ),
        relatedGaps = listOf(
            "本轮不新增主题专项 instrumentation，稳定 testTag 仅为后续自动化预留。",
            "overlay 真实主题验证继续依赖既有功能页，不在本页重复堆叠完整交互。",
        ),
    )
}

private fun UiTreeBuilder.MenuVisualSample() {
    Column(
        spacing = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(DropdownMenuDefaults.containerColor())
            .shape(DropdownMenuDefaults.shape())
            .clip()
            .elevation(DropdownMenuDefaults.elevation())
            .padding(vertical = DropdownMenuDefaults.verticalPadding())
            .padding(bottom = 8.dp),
    ) {
        DropdownMenuItem(
            text = "Menu Item",
            onClick = {},
            leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
        )
        DropdownMenuItem(
            text = "Disabled Item",
            onClick = {},
            trailingText = "OFF",
            enabled = false,
        )
    }
}

private fun UiTreeBuilder.TooltipVisualSample() {
    Row(
        spacing = 12.dp,
        verticalAlignment = VerticalAlignment.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .backgroundColor(TooltipDefaults.containerColor())
                .shape(TooltipDefaults.shape())
                .clip()
                .padding(
                    horizontal = TooltipDefaults.horizontalPadding(),
                    vertical = TooltipDefaults.verticalPadding(),
                ),
        ) {
            Text(
                text = "Inverse Tooltip",
                style = TooltipDefaults.textStyle(),
                color = TooltipDefaults.contentColor(),
            )
        }
        Text(
            text = "Tooltip 应使用 inverseSurface / inverseOnSurface。",
            modifier = Modifier.weight(1f),
        )
    }
}

private fun UiTreeBuilder.ShapeProbe(
    label: String,
    shape: UiShape,
    modifier: Modifier = Modifier,
) {
    Column(
        spacing = 6.dp,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .backgroundColor(Theme.colors.surfaceVariant)
                .shape(shape)
                .clip(),
        ) {}
        Text(
            text = "$label (${shape.demoLabel()})",
            style = UiTextStyle(fontSizeSp = 12.sp),
            color = TextDefaults.secondaryColor(),
        )
    }
}
