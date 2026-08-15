package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.material3.Material3Button
import com.viewcompose.material3.Material3Card
import com.viewcompose.material3.Material3NavigationBar
import com.viewcompose.material3.Material3Reference
import com.viewcompose.material3.Material3Surface
import com.viewcompose.material3.Material3SurfaceVariant
import com.viewcompose.material3.Material3Switch
import com.viewcompose.material3.Material3TextField
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonDefaults
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.Chip
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.DesignSystemDiagnostics
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
import com.viewcompose.ui.foundation.key
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.foundation.rememberTextFieldState
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
internal fun UiTreeBuilder.Material3DefaultThemePage(
    source: DemoThemeSource,
    scenario: DemoScenarioSpec? = null,
) {
    val sessionGeneration = rememberSaveable(key = "material3-theme-session-generation") {
        mutableStateOf(0)
    }
    key(sessionGeneration.value) {
        Material3DefaultThemeSession(
            source = source,
            scenario = scenario,
            sessionGeneration = sessionGeneration.value,
            onReset = { sessionGeneration.value += 1 },
        )
    }
}

private fun UiTreeBuilder.Material3DefaultThemeSession(
    source: DemoThemeSource,
    scenario: DemoScenarioSpec?,
    sessionGeneration: Int,
    onReset: () -> Unit,
) {
    val automationActions = remember { mutableStateOf(0) }
    val defaultButtonClicks = remember { mutableStateOf(0) }
    val namedSwitchChecked = remember { mutableStateOf(true) }
    val namedNavigationIndex = remember { mutableStateOf(0) }
    val namedTextField = rememberTextFieldState(stringResource(R.string.demo_material3_named_initial_value))
    val stateLayerFocusRequester = remember { FocusRequester() }
    val stateLayerSegmentedIndex = remember { mutableStateOf(0) }
    LazyColumn(
        items = listOf(
            "intro",
            "source",
            "namedPressure",
            "buttons",
            "stateLayers",
            "compact",
            "selection",
            "navigation",
            "targetProbes",
        ),
        // Lazy items own independent logical Sessions. A page-level key alone cannot reset their
        // remember/effect identity, so the generation must participate in each item key as well.
        key = { section -> "$sessionGeneration:$section" },
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background)
            .systemBarsInsetsPadding()
            .padding(horizontal = 16.dp)
            .material3ScenarioTarget(scenario, DemoAutomationRole.Root),
    ) { section ->
        when (section) {
            "intro" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 16.dp, bottom = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_material3_ready),
                    style = Theme.typography.labelMedium,
                    color = Theme.colors.onSurfaceVariant,
                    modifier = Modifier.material3ScenarioTarget(scenario, DemoAutomationRole.Ready),
                )
                Text(
                    text = stringResource(
                        R.string.demo_material3_automation_state,
                        source.id,
                        automationActions.value,
                    ),
                    style = Theme.typography.bodyMedium,
                    color = Theme.colors.onSurfaceVariant,
                    modifier = Modifier.material3ScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        text = stringResource(R.string.demo_material3_automation_action),
                        onClick = { automationActions.value += 1 },
                        modifier = Modifier.material3ScenarioTarget(
                            scenario,
                            DemoAutomationRole.PrimaryAction,
                        ),
                    )
                    Button(
                        text = stringResource(R.string.demo_material3_automation_reset),
                        variant = ButtonVariant.Outlined,
                        onClick = onReset,
                        modifier = Modifier.material3ScenarioTarget(
                            scenario,
                            DemoAutomationRole.Reset,
                        ),
                    )
                }
                Text(
                    text = stringResource(R.string.demo_material3_intro_title),
                    style = Theme.typography.headlineSmall,
                    color = Theme.colors.onBackground,
                )
                Text(
                    text = stringResource(R.string.demo_material3_intro_summary),
                    style = Theme.typography.bodyMedium,
                    color = Theme.colors.onSurfaceVariant,
                )
                ThemeFixtureBadge(source)
            }

            "source" -> ThemeSourceSnapshotSection(source)

            "namedPressure" -> Material3NamedPressureSlice(
                source = source,
                scenario = scenario,
                switchChecked = namedSwitchChecked.value,
                onSwitchCheckedChange = { checked -> namedSwitchChecked.value = checked },
                selectedNavigationIndex = namedNavigationIndex.value,
                onNavigationSelected = { index -> namedNavigationIndex.value = index },
                field = namedTextField,
            )

            "buttons" -> Column(
                spacing = 12.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 12.dp),
            ) {
                Text(text = stringResource(R.string.demo_material3_section_buttons), style = Theme.typography.titleMedium)
                ThemeFixtureBadge(source)
                Row(spacing = 8.dp, verticalAlignment = VerticalAlignment.Center) {
                    Button(
                        text = stringResource(R.string.demo_material3_button_default),
                        onClick = { defaultButtonClicks.value += 1 },
                        modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_BUTTON),
                    )
                    Button(
                        text = stringResource(R.string.demo_material3_button_outlined),
                        variant = ButtonVariant.Outlined,
                        onClick = {},
                    )
                    Button(text = stringResource(R.string.demo_material3_button_disabled), enabled = false)
                }
                Text(
                    text = stringResource(R.string.demo_material3_default_clicks, defaultButtonClicks.value),
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
                Text(text = stringResource(R.string.demo_material3_section_compact), style = Theme.typography.titleMedium)
                ThemeFixtureBadge(source)
                Row(spacing = 12.dp, verticalAlignment = VerticalAlignment.Center) {
                    IconButton(
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                        contentDescription = stringResource(R.string.demo_material3_default_icon_description),
                        onClick = {},
                        modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_ICON_BUTTON),
                    )
                    Chip(
                        label = stringResource(R.string.demo_material3_assist_chip),
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
                Text(text = stringResource(R.string.demo_material3_section_navigation), style = Theme.typography.titleMedium)
                ThemeFixtureBadge(source)
                NavigationBar(
                    selectedIndex = 0,
                    onItemSelected = {},
                    modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_NAVIGATION),
                ) {
                    Item(
                        key = "home",
                        label = stringResource(R.string.demo_material3_navigation_home),
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                    Item(
                        key = "search",
                        label = stringResource(R.string.demo_material3_navigation_search),
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
                    Item(
                        key = "profile",
                        label = stringResource(R.string.demo_material3_navigation_profile),
                        icon = ImageSource.Resource(R.drawable.demo_media_icon),
                    )
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
        Text(
            text = stringResource(R.string.demo_material3_state_layers_title),
            style = Theme.typography.titleMedium,
        )
        ThemeFixtureBadge(source)
        Text(
            text = stringResource(R.string.demo_material3_state_layers_instruction),
            style = Theme.typography.bodySmall,
            color = Theme.colors.onSurfaceVariant,
        )
        Row(spacing = 8.dp, verticalAlignment = VerticalAlignment.Center) {
            Button(
                text = stringResource(R.string.demo_material3_primary),
                onClick = {},
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable()
                    .testTag(DemoTestTags.MATERIAL3_STATE_LAYER_PRIMARY),
            )
            Button(
                text = stringResource(R.string.demo_material3_tonal),
                variant = ButtonVariant.Tonal,
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_TONAL),
            )
            Button(
                text = stringResource(R.string.demo_material3_button_outlined),
                variant = ButtonVariant.Outlined,
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_OUTLINED),
            )
        }
        Row(spacing = 8.dp, verticalAlignment = VerticalAlignment.Center) {
            IconButton(
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                contentDescription = stringResource(R.string.demo_material3_state_layer_icon_description),
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_ICON),
            )
            Button(
                text = stringResource(R.string.demo_material3_focus_primary),
                variant = ButtonVariant.Outlined,
                onClick = { focusRequester.requestFocus() },
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_FOCUS_ACTION),
            )
            Button(
                text = stringResource(R.string.demo_material3_clear_focus),
                variant = ButtonVariant.Text,
                onClick = { focusManager.clearFocus(force = true) },
            )
        }
        Text(
            text = stringResource(R.string.demo_material3_focus_instruction),
            style = Theme.typography.bodySmall,
            color = Theme.colors.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.demo_material3_composite_controls),
            style = Theme.typography.labelLarge,
        )
        Row(spacing = 12.dp, verticalAlignment = VerticalAlignment.Center) {
            Chip(
                label = stringResource(R.string.demo_material3_assist_chip),
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_CHIP),
            )
            FloatingActionButton(
                onClick = {},
                modifier = Modifier.testTag(DemoTestTags.MATERIAL3_STATE_LAYER_FAB),
            ) {
                Icon(
                    source = ImageSource.Resource(R.drawable.demo_media_icon),
                    contentDescription = stringResource(R.string.demo_material3_state_layer_fab_description),
                )
            }
        }
        SegmentedControl(
            items = demoSegmentedItems(
                "selected" to stringResource(R.string.demo_material3_segment_selected),
                "other" to stringResource(R.string.demo_material3_segment_other),
            ),
            selectedIndex = segmentedIndex,
            onSelectionChange = onSegmentSelected,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DemoTestTags.MATERIAL3_STATE_LAYER_SEGMENTED),
        )
        Text(
            text = stringResource(R.string.demo_material3_composite_instruction),
            style = Theme.typography.bodySmall,
            color = Theme.colors.onSurfaceVariant,
        )
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_material3_state_layer_contract),
            facts = listOf(
                DiagnosticFact(stringResource(R.string.demo_material3_fact_opacity), "10% / 10% / 8%"),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_primary_base),
                    Theme.colors.onPrimary.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_primary_states),
                    primary.asStateLayerHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_tonal_base),
                    Theme.colors.onSecondaryContainer.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_tonal_states),
                    tonal.asStateLayerHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_outlined_base),
                    Theme.colors.primary.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_outlined_states),
                    outlined.asStateLayerHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_icon_base),
                    Theme.colors.onSurfaceVariant.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_icon_states),
                    icon.asStateLayerHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_chip_base),
                    Theme.colors.onSurfaceVariant.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_fab_base),
                    Theme.colors.onPrimaryContainer.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_segment_base),
                    stringResource(
                        R.string.demo_material3_selected_value,
                        Theme.colors.onSecondaryContainer.asColorHex(),
                    ),
                ),
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
        text = stringResource(R.string.demo_material3_fixture_badge, source.id, mode),
        style = Theme.typography.labelSmall,
        color = Theme.colors.onSurfaceVariant,
    )
}

private fun UiTreeBuilder.ThemeSourceSnapshotSection(source: DemoThemeSource) {
    val colors = Theme.colors
    val rolesDistinct = colors.secondary != colors.secondaryContainer
    val provenance = Theme.current.metadata.provenance
    val attribution = DesignSystemDiagnostics.current
    val componentEvidence = attribution?.components?.joinToString(separator = " · ") { component ->
        "${component.familyId}:${component.backend.name}/${component.conformance.name}"
    } ?: "unattributed"
    val overlayEvidence = attribution?.integrations?.joinToString(separator = " · ") { integration ->
        "${integration.capabilityId}:${integration.presenterId}/${integration.conformance.name}" +
            if (integration.fallback == "none") "" else "→${integration.fallback}"
    } ?: "unattributed"
    val sourceLabel = stringResource(R.string.demo_material3_fact_source)
    val metadataOriginLabel = stringResource(R.string.demo_material3_fact_metadata_origin)
    val tokenProducerLabel = stringResource(R.string.demo_material3_fact_token_producer)
    val primarySourceLabel = stringResource(R.string.demo_material3_fact_primary_source)
    val shapeSourceLabel = stringResource(R.string.demo_material3_fact_shape_source)
    val designSystemLabel = stringResource(R.string.demo_material3_fact_design_system)
    val recipeSetLabel = stringResource(R.string.demo_material3_fact_recipe_set)
    val componentBackendsLabel = stringResource(R.string.demo_material3_fact_component_backends)
    val overlayTransportLabel = stringResource(R.string.demo_material3_fact_overlay_transport)
    val overlayPresentersLabel = stringResource(R.string.demo_material3_fact_overlay_presenters)
    val modeLabel = stringResource(R.string.demo_material3_fact_mode)
    val secondaryLabel = stringResource(R.string.demo_material3_fact_secondary)
    val secondaryContainerLabel = stringResource(R.string.demo_material3_fact_secondary_container)
    val roleCheckLabel = stringResource(R.string.demo_material3_fact_role_check)
    Column(
        spacing = 12.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.demo_material3_snapshot_title),
            style = Theme.typography.titleMedium,
        )
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_material3_screenshot_identity),
            facts = listOf(
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_fixture),
                    "theme-token-matrix-v2",
                ),
                DiagnosticFact(
                    sourceLabel,
                    "${source.id} · ${stringResource(source.labelRes)}",
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_definition),
                    stringResource(source.descriptionRes),
                ),
                DiagnosticFact(metadataOriginLabel, Theme.current.metadata.origin.name),
                DiagnosticFact(tokenProducerLabel, provenance.sourceId),
                DiagnosticFact(primarySourceLabel, provenance.originOf("colors.primary").name),
                DiagnosticFact(shapeSourceLabel, provenance.originOf("shapes.full").name),
                DiagnosticFact(designSystemLabel, attribution?.designSystemId ?: "unattributed"),
                DiagnosticFact(recipeSetLabel, attribution?.recipeSetId ?: "unattributed"),
                DiagnosticFact(componentBackendsLabel, componentEvidence),
                DiagnosticFact(
                    overlayTransportLabel,
                    attribution?.integration("overlay.dialog")?.transportId ?: "unattributed",
                ),
                DiagnosticFact(overlayPresentersLabel, overlayEvidence),
                DiagnosticFact(
                    modeLabel,
                    stringResource(
                        if (Theme.current.metadata.isDark == true) {
                            R.string.demo_material3_mode_dark
                        } else {
                            R.string.demo_material3_mode_light
                        },
                    ),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_primary),
                    colors.primary.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_primary_container),
                    colors.primaryContainer.asColorHex(),
                ),
                DiagnosticFact(secondaryLabel, colors.secondary.asColorHex()),
                DiagnosticFact(secondaryContainerLabel, colors.secondaryContainer.asColorHex()),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_on_secondary_container),
                    colors.onSecondaryContainer.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_surface),
                    colors.surface.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_material3_fact_surface_container),
                    colors.surfaceContainer.asColorHex(),
                ),
                DiagnosticFact(roleCheckLabel, if (rolesDistinct) "DISTINCT" else "COLLISION"),
            ),
            valueTagsByLabel = mapOf(
                sourceLabel to DemoTestTags.MATERIAL3_THEME_SOURCE,
                metadataOriginLabel to DemoTestTags.MATERIAL3_THEME_ORIGIN,
                tokenProducerLabel to DemoTestTags.MATERIAL3_TOKEN_PRODUCER,
                primarySourceLabel to DemoTestTags.MATERIAL3_PRIMARY_ORIGIN,
                shapeSourceLabel to DemoTestTags.MATERIAL3_SHAPE_ORIGIN,
                designSystemLabel to DemoTestTags.MATERIAL3_DESIGN_SYSTEM,
                recipeSetLabel to DemoTestTags.MATERIAL3_RECIPE_SET,
                componentBackendsLabel to DemoTestTags.MATERIAL3_COMPONENT_BACKENDS,
                overlayTransportLabel to DemoTestTags.MATERIAL3_OVERLAY_TRANSPORT,
                overlayPresentersLabel to DemoTestTags.MATERIAL3_OVERLAY_PRESENTERS,
                modeLabel to DemoTestTags.MATERIAL3_THEME_MODE,
                secondaryLabel to DemoTestTags.MATERIAL3_THEME_SECONDARY,
                secondaryContainerLabel to DemoTestTags.MATERIAL3_THEME_SECONDARY_CONTAINER,
                roleCheckLabel to DemoTestTags.MATERIAL3_THEME_ROLE_COLLISION,
            ),
        )
        ThemeSwatchRow(
            label = stringResource(R.string.demo_material3_primary_roles),
            swatches = listOf(
                ThemeSwatch("P", colors.primary),
                ThemeSwatch("PC", colors.primaryContainer),
                ThemeSwatch("OnP", colors.onPrimaryContainer),
            ),
        )
        ThemeSwatchRow(
            label = stringResource(R.string.demo_material3_secondary_roles),
            swatches = listOf(
                ThemeSwatch("S", colors.secondary),
                ThemeSwatch("SC", colors.secondaryContainer),
                ThemeSwatch("OnS", colors.onSecondaryContainer),
            ),
        )
    }
}

private fun UiTreeBuilder.Material3NamedPressureSlice(
    source: DemoThemeSource,
    scenario: DemoScenarioSpec?,
    switchChecked: Boolean,
    onSwitchCheckedChange: (Boolean) -> Unit,
    selectedNavigationIndex: Int,
    onNavigationSelected: (Int) -> Unit,
    field: com.viewcompose.text.TextFieldState,
) {
    Column(
        spacing = 12.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.demo_material3_named_title),
            style = Theme.typography.titleMedium,
            modifier = Modifier.material3ScenarioTarget(scenario, DemoAutomationRole.Target),
        )
        Text(
            text = stringResource(
                R.string.demo_material3_named_summary,
                Material3Reference.recipeSet,
            ),
            style = Theme.typography.bodySmall,
            color = Theme.colors.onSurfaceVariant,
        )
        ThemeFixtureBadge(source)
        Material3Surface(
            variant = Material3SurfaceVariant.Container,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
                .testTag(DemoTestTags.MATERIAL3_NAMED_SURFACE),
        ) {
            Text(text = stringResource(R.string.demo_material3_named_surface))
        }
        Material3Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp)
                .testTag(DemoTestTags.MATERIAL3_NAMED_CARD),
        ) {
            Text(text = stringResource(R.string.demo_material3_named_card))
        }
        Material3Button(
            text = stringResource(R.string.demo_material3_named_button),
            onClick = {},
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_NAMED_BUTTON),
        )
        Material3Switch(
            text = stringResource(R.string.demo_material3_named_switch),
            checked = switchChecked,
            onCheckedChange = onSwitchCheckedChange,
            modifier = Modifier.fillMaxWidth().testTag(DemoTestTags.MATERIAL3_NAMED_SWITCH),
        )
        Material3TextField(
            state = field,
            label = stringResource(R.string.demo_material3_named_text_field),
            supportingText = stringResource(R.string.demo_material3_named_text_field_support),
            modifier = Modifier.fillMaxWidth().testTag(DemoTestTags.MATERIAL3_NAMED_TEXT_FIELD),
        )
        Material3NavigationBar(
            selectedIndex = selectedNavigationIndex,
            onItemSelected = onNavigationSelected,
            modifier = Modifier.fillMaxWidth().testTag(DemoTestTags.MATERIAL3_NAMED_NAVIGATION),
        ) {
            Item(
                key = "home",
                label = stringResource(R.string.demo_material3_navigation_home),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
            )
            Item(
                key = "search",
                label = stringResource(R.string.demo_material3_navigation_search),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
            )
            Item(
                key = "profile",
                label = stringResource(R.string.demo_material3_navigation_profile),
                icon = ImageSource.Resource(R.drawable.demo_media_icon),
            )
        }
    }
}

private fun UiTreeBuilder.Material3TouchTargetProbes(source: DemoThemeSource) {
    val firstChecked = remember { mutableStateOf(false) }
    val secondChecked = remember { mutableStateOf(false) }
    Column(
        spacing = 0.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 20.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.demo_material3_touch_targets_title),
            style = Theme.typography.titleMedium,
        )
        ThemeFixtureBadge(source)
        Checkbox(
            text = stringResource(R.string.demo_material3_adjacent_first),
            checked = firstChecked.value,
            onCheckedChange = { firstChecked.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_TARGET_ADJACENT_FIRST),
        )
        Checkbox(
            text = stringResource(R.string.demo_material3_adjacent_second),
            checked = secondChecked.value,
            onCheckedChange = { secondChecked.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_TARGET_ADJACENT_SECOND),
        )
        Text(
            text = stringResource(
                R.string.demo_material3_adjacent_state,
                firstChecked.value.toString(),
                secondChecked.value.toString(),
            ),
            style = Theme.typography.bodySmall,
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_TARGET_ADJACENT_STATUS),
        )
        Checkbox(
            text = stringResource(R.string.demo_material3_explicit_compact),
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
                text = stringResource(R.string.demo_material3_clipped_parent),
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
        Text(
            text = stringResource(R.string.demo_material3_selection_controls_title),
            style = Theme.typography.titleMedium,
        )
        ThemeFixtureBadge(source)
        Checkbox(
            text = stringResource(R.string.demo_material3_checkbox),
            checked = checked.value,
            onCheckedChange = { checked.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_CHECKBOX),
        )
        RadioButton(
            text = stringResource(R.string.demo_material3_radio_button),
            checked = checked.value,
            onCheckedChange = { checked.value = it },
            modifier = Modifier.testTag(DemoTestTags.MATERIAL3_DEFAULT_RADIO),
        )
        Switch(
            text = stringResource(R.string.demo_material3_switch),
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

private fun Modifier.material3ScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}
