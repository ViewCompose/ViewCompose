package com.viewcompose

import com.viewcompose.preview.tooling.ViewComposePreview
import android.view.ViewGroup
import com.viewcompose.host.android.resources.stringResource
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
import com.viewcompose.ui.foundation.CardVariant
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

internal val DIAGNOSTICS_THEME_PAGE_ITEMS = listOf(
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
    section: String,
    root: ViewGroup?,
    firstModifier: Modifier = Modifier,
    lastModifier: Modifier = Modifier,
) {
    when (section) {
        "theme_snapshot_core" -> DiagnosticsThemeSnapshotCoreSection(root, firstModifier)
        "theme_snapshot_palette" -> DiagnosticsThemeSnapshotPaletteSection()
        "theme_snapshot_sizing" -> DiagnosticsThemeSnapshotSizingSection()
        "theme_surface" -> DiagnosticsThemeSurfaceSection()
        "theme_action" -> DiagnosticsThemeActionSection()
        "theme_input" -> DiagnosticsThemeInputSection()
        "theme_navigation" -> DiagnosticsThemeNavigationSection()
        "theme_shape_size" -> DiagnosticsThemeShapeSizeSection(lastModifier)
        else -> error("Unknown diagnostics theme section: $section")
    }
}

private fun UiTreeBuilder.DiagnosticsThemeSnapshotCoreSection(
    root: ViewGroup?,
    modifier: Modifier,
) {
    val modeFactLabel = stringResource(R.string.demo_diagnostics_theme_mode)
    val sourceFactLabel = stringResource(R.string.demo_diagnostics_theme_source)
    val secondaryContainerFactLabel = "SecondaryContainer"
    val modeLabel = root?.context?.let { context ->
        DemoThemeTokens.modeLabel(DemoThemeSession.mode, context)
    } ?: stringResource(
        DemoThemeTokens.modeLabelRes(
            mode = DemoThemeSession.mode,
            isSystemDark = false,
        ),
    )
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_diagnostics_theme_snapshot_title),
        subtitle = stringResource(R.string.demo_diagnostics_theme_snapshot_summary),
        modifier = modifier,
    ) {
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_diagnostics_theme_current_baseline),
            facts = listOf(
                DiagnosticFact(modeFactLabel, modeLabel),
                DiagnosticFact(
                    sourceFactLabel,
                    "${DemoThemeSource.DemoCustom.id} · " +
                        stringResource(DemoThemeSource.DemoCustom.labelRes),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_diagnostics_theme_metadata_origin),
                    Theme.current.metadata.origin.name,
                ),
                DiagnosticFact("Background", Theme.colors.background.asColorHex()),
                DiagnosticFact("Surface", Theme.colors.surface.asColorHex()),
                DiagnosticFact("SurfaceVariant", Theme.colors.surfaceVariant.asColorHex()),
                DiagnosticFact("OnSurface", Theme.colors.onSurface.asColorHex()),
                DiagnosticFact("OnSurfaceVariant", Theme.colors.onSurfaceVariant.asColorHex()),
                DiagnosticFact("Primary", Theme.colors.primary.asColorHex()),
                DiagnosticFact("OnPrimary", Theme.colors.onPrimary.asColorHex()),
                DiagnosticFact("Secondary", Theme.colors.secondary.asColorHex()),
                DiagnosticFact("OnSecondary", Theme.colors.onSecondary.asColorHex()),
                DiagnosticFact(secondaryContainerFactLabel, Theme.colors.secondaryContainer.asColorHex()),
                DiagnosticFact("OnSecondaryContainer", Theme.colors.onSecondaryContainer.asColorHex()),
                DiagnosticFact(
                    stringResource(R.string.demo_diagnostics_theme_role_check),
                    stringResource(
                        if (Theme.colors.secondary != Theme.colors.secondaryContainer) {
                            R.string.demo_diagnostics_theme_role_distinct
                        } else {
                            R.string.demo_diagnostics_theme_role_collision
                        },
                    ),
                ),
            ),
            valueTagsByLabel = mapOf(
                modeFactLabel to DemoTestTags.DIAGNOSTICS_THEME_MODE,
                sourceFactLabel to DemoTestTags.DIAGNOSTICS_THEME_SOURCE,
                secondaryContainerFactLabel to DemoTestTags.DIAGNOSTICS_THEME_SECONDARY_CONTAINER,
            ),
        )
    }
}

private fun UiTreeBuilder.DiagnosticsThemeSnapshotPaletteSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_diagnostics_theme_palette_title),
        subtitle = stringResource(R.string.demo_diagnostics_theme_palette_summary),
    ) {
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_diagnostics_theme_extended_colors),
            facts = listOf(
                DiagnosticFact("ErrorContainer", Theme.colors.errorContainer.asColorHex()),
                DiagnosticFact("OnErrorContainer", Theme.colors.onErrorContainer.asColorHex()),
                DiagnosticFact("Outline", Theme.colors.outline.asColorHex()),
                DiagnosticFact("OutlineVariant", Theme.colors.outlineVariant.asColorHex()),
                DiagnosticFact("InverseSurface", Theme.colors.inverseSurface.asColorHex()),
                DiagnosticFact("InverseOnSurface", Theme.colors.inverseOnSurface.asColorHex()),
                DiagnosticFact(
                    "State layers",
                    "pressed=${Theme.interactions.pressedStateLayerOpacity}; " +
                        "focused=${Theme.interactions.focusedStateLayerOpacity}; " +
                        "hovered=${Theme.interactions.hoveredStateLayerOpacity}",
                ),
            ),
        )
        ThemeSwatchRow(
            label = stringResource(R.string.demo_diagnostics_theme_surface_inverse),
            swatches = listOf(
                ThemeSwatch("Background", Theme.colors.background),
                ThemeSwatch("Surface", Theme.colors.surface),
                ThemeSwatch("Variant", Theme.colors.surfaceVariant),
                ThemeSwatch("Inverse", Theme.colors.inverseSurface),
            ),
        )
        ThemeSwatchRow(
            label = stringResource(R.string.demo_diagnostics_theme_primary_secondary_roles),
            swatches = listOf(
                ThemeSwatch("P", Theme.colors.primary),
                ThemeSwatch("S", Theme.colors.secondary),
                ThemeSwatch("SC", Theme.colors.secondaryContainer),
                ThemeSwatch("OnS", Theme.colors.onSecondaryContainer),
            ),
        )
        ThemeSwatchRow(
            label = stringResource(R.string.demo_diagnostics_theme_error_outline),
            swatches = listOf(
                ThemeSwatch("Error", Theme.colors.error),
                ThemeSwatch("Outline", Theme.colors.outline),
            ),
        )
    }
}

private fun UiTreeBuilder.DiagnosticsThemeSnapshotSizingSection() {
    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_diagnostics_theme_sizing_title),
        subtitle = stringResource(R.string.demo_diagnostics_theme_sizing_summary),
    ) {
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_diagnostics_theme_shape_control_sizing),
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
        title = stringResource(R.string.demo_diagnostics_theme_surface_title),
        subtitle = stringResource(R.string.demo_diagnostics_theme_surface_summary),
    ) {
        TopAppBar(
            title = stringResource(R.string.demo_diagnostics_theme_top_app_bar),
            navigationIcon = {
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(R.string.demo_diagnostics_theme_navigation_icon),
                    onClick = {},
                )
            },
            actions = {
                IconButton(
                    icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(R.string.demo_diagnostics_theme_action_icon),
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
                    Text(text = stringResource(R.string.demo_diagnostics_theme_default_surface))
                    Text(
                        text = stringResource(R.string.demo_diagnostics_theme_default_surface_note),
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
                    Text(text = stringResource(R.string.demo_diagnostics_theme_variant_surface))
                    Text(
                        text = stringResource(R.string.demo_diagnostics_theme_variant_surface_note),
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
                Text(text = stringResource(R.string.demo_diagnostics_theme_card))
                Text(
                    text = stringResource(R.string.demo_diagnostics_theme_card_note),
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }
        }
        Card(
            variant = CardVariant.Outlined,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.demo_diagnostics_theme_outlined_card_note),
                modifier = Modifier.padding(12.dp),
            )
        }
        ListItem(
            overlineText = stringResource(R.string.demo_diagnostics_theme_list_item),
            headlineText = stringResource(R.string.demo_diagnostics_theme_list_item_headline),
            supportingText = stringResource(R.string.demo_diagnostics_theme_list_item_note),
            trailingContent = {
                Text(
                    text = stringResource(R.string.demo_diagnostics_theme_list_item_badge),
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
        title = stringResource(R.string.demo_diagnostics_theme_action_title),
        subtitle = stringResource(R.string.demo_diagnostics_theme_action_summary),
    ) {
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Button(
                text = stringResource(R.string.demo_diagnostics_theme_primary),
                onClick = {},
                variant = ButtonVariant.Primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.DIAGNOSTICS_THEME_BUTTON_PRIMARY),
            )
            Button(
                text = stringResource(R.string.demo_diagnostics_theme_secondary),
                onClick = {},
                variant = ButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Button(
                text = stringResource(R.string.demo_diagnostics_theme_tonal),
                onClick = {},
                variant = ButtonVariant.Tonal,
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.demo_diagnostics_theme_outlined),
                onClick = {},
                variant = ButtonVariant.Outlined,
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.demo_diagnostics_theme_text),
                onClick = {},
                variant = ButtonVariant.Text,
                modifier = Modifier.weight(1f),
            )
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
                    contentDescription = stringResource(R.string.demo_diagnostics_theme_fab_icon),
                )
            }
            FloatingActionButton(
                onClick = {},
                size = FabSize.Medium,
                modifier = Modifier.testTag(DemoTestTags.DIAGNOSTICS_THEME_FAB),
            ) {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(R.string.demo_diagnostics_theme_fab_icon),
                )
            }
            FloatingActionButton(onClick = {}, size = FabSize.Large) {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(R.string.demo_diagnostics_theme_fab_icon),
                )
            }
            ExtendedFloatingActionButton(
                text = stringResource(R.string.demo_diagnostics_theme_extended_fab),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                onClick = {},
            )
        }
        Row(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Chip(
                label = stringResource(R.string.demo_diagnostics_theme_assist_chip),
                onClick = {},
                variant = ChipVariant.Assist,
                modifier = Modifier.weight(1f),
            )
            Chip(
                label = stringResource(R.string.demo_diagnostics_theme_filter_chip),
                onClick = {},
                variant = ChipVariant.Filter,
                selected = true,
                modifier = Modifier.weight(1f),
            )
            BadgedBox(
                badge = { Badge(count = 8) },
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Button(
                    text = stringResource(R.string.demo_diagnostics_theme_badge),
                    onClick = {},
                    variant = ButtonVariant.Tonal,
                )
            }
        }
    }
}

private fun UiTreeBuilder.DiagnosticsThemeInputSection() {
    val searchQueryState = rememberTextFieldState(
        stringResource(R.string.demo_diagnostics_theme_search_token_value),
    )
    val normalFieldState = rememberTextFieldState("theme@viewcompose.dev")
    val errorFieldState = rememberTextFieldState("error@viewcompose.dev")
    val disabledFieldState = rememberTextFieldState(
        stringResource(R.string.demo_diagnostics_theme_disabled_field_value),
    )
    val checkboxState = remember { mutableStateOf(true) }
    val switchState = remember { mutableStateOf(true) }
    val radioState = remember { mutableStateOf(true) }
    val sliderState = remember { mutableStateOf(68) }
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_diagnostics_theme_input_title),
        subtitle = stringResource(R.string.demo_diagnostics_theme_input_summary),
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
            placeholder = stringResource(R.string.demo_diagnostics_theme_search_token_placeholder),
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
                text = stringResource(R.string.demo_diagnostics_theme_checkbox),
                checked = checkboxState.value,
                onCheckedChange = { checkboxState.value = it },
                modifier = Modifier.weight(1f),
            )
            Switch(
                text = stringResource(R.string.demo_diagnostics_theme_switch),
                checked = switchState.value,
                onCheckedChange = { switchState.value = it },
                modifier = Modifier.weight(1f),
            )
        }
        RadioButton(
            text = stringResource(R.string.demo_diagnostics_theme_radio_button),
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
                text = stringResource(R.string.demo_diagnostics_theme_selection_note),
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
        title = stringResource(R.string.demo_diagnostics_theme_navigation_title),
        subtitle = stringResource(R.string.demo_diagnostics_theme_navigation_summary),
    ) {
        NavigationBar(
            selectedIndex = navIndexState.value,
            onItemSelected = { navIndexState.value = it },
            modifier = Modifier
                .margin(bottom = 8.dp)
                .testTag(DemoTestTags.DIAGNOSTICS_THEME_NAVIGATION),
        ) {
            Item(
                key = "home",
                label = stringResource(R.string.demo_diagnostics_theme_home),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
            )
            Item(
                key = "search",
                label = stringResource(R.string.demo_diagnostics_theme_search),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                badgeCount = 3,
            )
            Item(
                key = "profile",
                label = stringResource(R.string.demo_diagnostics_theme_profile),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
            )
        }
        SegmentedControl(
            items = demoSegmentedItems(
                "alpha" to stringResource(R.string.demo_diagnostics_theme_alpha),
                "beta" to stringResource(R.string.demo_diagnostics_theme_beta),
                "gamma" to stringResource(R.string.demo_diagnostics_theme_gamma),
            ),
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
            Tab(key = "overview") { _ ->
                Text(
                    text = stringResource(R.string.demo_diagnostics_theme_overview),
                    modifier = Modifier.padding(12.dp),
                )
            }
            Tab(key = "theme") { _ ->
                Text(
                    text = stringResource(R.string.demo_diagnostics_theme_theme),
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

private fun UiTreeBuilder.DiagnosticsThemeShapeSizeSection(modifier: Modifier) {
    val compactFieldState = rememberTextFieldState(
        stringResource(R.string.demo_diagnostics_theme_compact_field_value),
    )
    val mediumFieldState = rememberTextFieldState(
        stringResource(R.string.demo_diagnostics_theme_medium_field_value),
    )
    val largeFieldState = rememberTextFieldState(
        stringResource(R.string.demo_diagnostics_theme_large_field_value),
    )
    val searchState = rememberTextFieldState(
        stringResource(R.string.demo_diagnostics_theme_large_shape_value),
    )
    ScenarioSection(
        kind = ScenarioKind.Visual,
        title = stringResource(R.string.demo_diagnostics_theme_shape_size_title),
        subtitle = stringResource(R.string.demo_diagnostics_theme_shape_size_summary),
        modifier = modifier,
    ) {
        Row(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            ShapeProbe(
                label = stringResource(R.string.demo_diagnostics_theme_small),
                shape = Theme.shapes.small,
                modifier = Modifier
                    .weight(1f)
                    .testTag(DemoTestTags.DIAGNOSTICS_THEME_SHAPE_SMALL),
            )
            ShapeProbe(
                stringResource(R.string.demo_diagnostics_theme_medium),
                Theme.shapes.medium,
                Modifier.weight(1f),
            )
            ShapeProbe(
                label = stringResource(R.string.demo_diagnostics_theme_large),
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
            Button(
                text = stringResource(R.string.demo_diagnostics_theme_compact),
                onClick = {},
                size = ButtonSize.Compact,
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.demo_diagnostics_theme_medium),
                onClick = {},
                size = ButtonSize.Medium,
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.demo_diagnostics_theme_large),
                onClick = {},
                size = ButtonSize.Large,
                modifier = Modifier.weight(1f),
            )
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
            items = demoSegmentedItems("small" to "S", "medium" to "M", "large" to "L"),
            selectedIndex = 1,
            onSelectionChange = {},
            size = SegmentedControlSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        SearchBar(
            state = searchState,
            placeholder = stringResource(R.string.demo_diagnostics_theme_search_shape_placeholder),
            leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
            text = stringResource(R.string.demo_diagnostics_theme_menu_item),
            onClick = {},
            leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
        )
        DropdownMenuItem(
            text = stringResource(R.string.demo_diagnostics_theme_disabled_menu_item),
            onClick = {},
            trailingText = stringResource(R.string.demo_diagnostics_theme_off),
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
                text = stringResource(R.string.demo_diagnostics_theme_inverse_tooltip),
                style = TooltipDefaults.textStyle(),
                color = TooltipDefaults.contentColor(),
            )
        }
        Text(
            text = stringResource(R.string.demo_diagnostics_theme_tooltip_note),
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
            text = stringResource(
                R.string.demo_diagnostics_theme_shape_probe,
                label,
                shape.demoLabel(),
            ),
            style = UiTextStyle(fontSizeSp = 12.sp),
            color = TextDefaults.secondaryColor(),
        )
    }
}
