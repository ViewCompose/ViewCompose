package com.viewcompose

import androidx.annotation.IdRes
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.nativeView
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.oneui7.OneUi7Button
import com.viewcompose.oneui7.OneUi7ButtonVariant
import com.viewcompose.oneui7.OneUi7NavigationBar
import com.viewcompose.oneui7.OneUi7NavigationItem
import com.viewcompose.oneui7.OneUi7Reference
import com.viewcompose.oneui7.OneUi7Surface
import com.viewcompose.oneui7.OneUi7Switch
import com.viewcompose.oneui7.OneUi7TextField
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.DesignSystemDiagnostics
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.ModalBottomSheet
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Snackbar
import com.viewcompose.ui.foundation.SnackbarDuration
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiDesignSystemAttribution
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.key
import com.viewcompose.ui.foundation.rememberSaveable
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.systemBarsInsetsPadding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.unit.dp

/** Emits one strict, resettable fixture for the public One UI 7 five-component alpha slice. */
internal fun UiTreeBuilder.DemoOneUi7VerificationPage(scenario: DemoScenarioSpec) {
    val sessionGeneration = rememberSaveable(key = "one-ui-7-session-generation") {
        mutableStateOf(0)
    }
    key(sessionGeneration.value) {
        DemoOneUi7VerificationSession(
            scenario = scenario,
            sessionGeneration = sessionGeneration.value,
            onReset = { sessionGeneration.value += 1 },
        )
    }
}

private fun UiTreeBuilder.DemoOneUi7VerificationSession(
    scenario: DemoScenarioSpec,
    sessionGeneration: Int,
    onReset: () -> Unit,
) {
    val clicks = rememberSaveable(key = "one-ui-7-clicks") { mutableStateOf(0) }
    val checked = rememberSaveable(key = "one-ui-7-switch") { mutableStateOf(true) }
    val selected = rememberSaveable(key = "one-ui-7-navigation") { mutableStateOf(0) }
    val snackbarVisible = rememberSaveable(key = "one-ui-7-snackbar") { mutableStateOf(false) }
    val sheetVisible = rememberSaveable(key = "one-ui-7-sheet") { mutableStateOf(false) }
    val account = rememberTextFieldState(stringResource(R.string.demo_one_ui7_account_initial_value))
    val destinations = listOf(
        OneUi7NavigationItem("home", stringResource(R.string.demo_one_ui7_home)),
        OneUi7NavigationItem("search", stringResource(R.string.demo_one_ui7_search)),
        OneUi7NavigationItem("profile", stringResource(R.string.demo_one_ui7_profile)),
    )
    val attribution = DesignSystemDiagnostics.current
    val unattributed = stringResource(R.string.demo_one_ui7_unattributed)
    val componentEvidence = attribution?.components?.joinToString(separator = " · ") { component ->
        "${component.familyId}:${component.recipeId}:" +
            "${component.backend.name}/${component.conformance.name}"
    } ?: unattributed
    val overlayEvidence = attribution?.integrations?.joinToString(separator = " · ") { integration ->
        "${integration.capabilityId}:${integration.presenterId}/${integration.conformance.name}" +
            if (integration.fallback == "none") "" else "→${integration.fallback}"
    } ?: unattributed
    LazyColumn(
        items = listOf("identity", "surface", "switch", "textfield", "navigation", "overlay"),
        // Lazy items own independent Sessions, so reset must replace their identities as well.
        key = { section -> "$sessionGeneration:$section" },
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background)
            .systemBarsInsetsPadding()
            .padding(horizontal = 24.dp)
            .oneUi7ScenarioTarget(scenario, DemoAutomationRole.Root),
    ) { section ->
        when (section) {
            "identity" -> DemoOneUi7IdentitySection(
                scenario = scenario,
                clicks = clicks.value,
                componentEvidence = componentEvidence,
                overlayEvidence = overlayEvidence,
                attribution = attribution,
                unattributed = unattributed,
                onContinue = { clicks.value += 1 },
                onReset = onReset,
            )

            "surface" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
            ) {
                Text(
                    stringResource(R.string.demo_one_ui7_surface_section),
                    color = Theme.colors.onSurface,
                    style = Theme.typography.titleMedium,
                )
                OneUi7Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .oneUi7ScenarioTarget(scenario, DemoAutomationRole.Target),
                ) {
                    Column(spacing = 5.dp) {
                        Text(
                            stringResource(R.string.demo_one_ui7_connected_devices),
                            color = Theme.colors.onSurface,
                            style = Theme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.demo_one_ui7_surface_summary),
                            color = Theme.colors.onSurfaceVariant,
                            style = Theme.typography.bodySmall,
                        )
                    }
                }
            }

            "switch" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
            ) {
                Text(
                    stringResource(R.string.demo_one_ui7_switch_section),
                    color = Theme.colors.onSurface,
                    style = Theme.typography.titleMedium,
                )
                OneUi7Switch(
                    text = stringResource(R.string.demo_one_ui7_sync_devices),
                    checked = checked.value,
                    onCheckedChange = { checked.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .oneUi7ScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                )
                Text(
                    stringResource(R.string.demo_one_ui7_checked_status, checked.value.toString()),
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.oneUi7ScenarioTarget(
                        scenario,
                        DemoAutomationRole.SecondaryTarget,
                    ),
                )
            }

            "textfield" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp),
            ) {
                Text(
                    stringResource(R.string.demo_one_ui7_text_field_section),
                    color = Theme.colors.onSurface,
                    style = Theme.typography.titleMedium,
                )
                OneUi7TextField(
                    state = account,
                    label = stringResource(R.string.demo_one_ui7_account_name),
                    placeholder = stringResource(R.string.demo_one_ui7_name_placeholder),
                    supportingText = stringResource(R.string.demo_one_ui7_text_field_support),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoOneUi7TestTags.ONE_UI_7_TEXT_FIELD),
                )
            }

            "navigation" -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp, bottom = 28.dp),
            ) {
                Text(
                    stringResource(R.string.demo_one_ui7_navigation_section),
                    color = Theme.colors.onSurface,
                    style = Theme.typography.titleMedium,
                )
                OneUi7NavigationBar(
                    items = destinations,
                    selectedIndex = selected.value,
                    onItemSelected = { selected.value = it },
                    modifier = Modifier.testTag(DemoOneUi7TestTags.ONE_UI_7_NAVIGATION),
                )
                Text(
                    stringResource(
                        R.string.demo_one_ui7_selected_status,
                        destinations[selected.value].label,
                    ),
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.testTag(DemoOneUi7TestTags.ONE_UI_7_NAVIGATION_STATUS),
                )
            }

            else -> Column(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 20.dp, bottom = 28.dp),
            ) {
                Text(
                    stringResource(R.string.demo_one_ui7_overlays),
                    color = Theme.colors.onSurface,
                    style = Theme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.demo_one_ui7_overlay_summary),
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                )
                Row(spacing = 10.dp, verticalAlignment = VerticalAlignment.Center) {
                    OneUi7Button(
                        text = stringResource(R.string.demo_one_ui7_show_snackbar),
                        onClick = { snackbarVisible.value = true },
                        modifier = Modifier
                            .testTag(DemoOneUi7TestTags.ONE_UI_7_SNACKBAR_ACTION)
                            .oneUi7AndroidId(R.id.demo_oneui7_snackbar_show, "snackbar-show"),
                    )
                    OneUi7Button(
                        text = stringResource(R.string.demo_one_ui7_show_sheet),
                        onClick = { sheetVisible.value = true },
                        variant = OneUi7ButtonVariant.Neutral,
                        modifier = Modifier
                            .testTag(DemoOneUi7TestTags.ONE_UI_7_BOTTOM_SHEET_ACTION)
                            .oneUi7AndroidId(R.id.demo_oneui7_sheet_show, "sheet-show"),
                    )
                }
            }
        }
    }

    Snackbar(
        visible = snackbarVisible.value,
        message = stringResource(R.string.demo_one_ui7_snackbar_message),
        actionLabel = stringResource(R.string.demo_one_ui7_snackbar_done),
        duration = SnackbarDuration.Indefinite,
        requestKey = "one-ui-7-snackbar",
        onAction = { snackbarVisible.value = false },
        onDismiss = { snackbarVisible.value = false },
    )
    ModalBottomSheet(
        visible = sheetVisible.value,
        requestKey = "one-ui-7-bottom-sheet",
        skipPartiallyExpanded = true,
        onDismissRequest = { sheetVisible.value = false },
    ) {
        Column(
            spacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .testTag(DemoOneUi7TestTags.ONE_UI_7_BOTTOM_SHEET_CONTENT)
                .oneUi7AndroidId(R.id.demo_oneui7_sheet_content, "sheet-content"),
        ) {
            Text(
                stringResource(R.string.demo_one_ui7_connected_devices),
                color = Theme.colors.onSurface,
                style = Theme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.demo_one_ui7_sheet_summary),
                color = Theme.colors.onSurfaceVariant,
                style = Theme.typography.bodyMedium,
            )
            OneUi7Button(
                text = stringResource(R.string.demo_one_ui7_close),
                onClick = { sheetVisible.value = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DemoOneUi7TestTags.ONE_UI_7_BOTTOM_SHEET_DISMISS)
                    .oneUi7AndroidId(R.id.demo_oneui7_sheet_dismiss, "sheet-dismiss"),
            )
        }
    }
}

private fun UiTreeBuilder.DemoOneUi7IdentitySection(
    scenario: DemoScenarioSpec,
    clicks: Int,
    componentEvidence: String,
    overlayEvidence: String,
    attribution: UiDesignSystemAttribution?,
    unattributed: String,
    onContinue: () -> Unit,
    onReset: () -> Unit,
) {
    val componentSetLabel = stringResource(R.string.demo_one_ui7_fact_component_set)
    val tokenSourceLabel = stringResource(R.string.demo_one_ui7_fact_token_source)
    val primarySourceLabel = stringResource(R.string.demo_one_ui7_fact_primary_source)
    val designSystemLabel = stringResource(R.string.demo_one_ui7_fact_design_system)
    val recipeSetLabel = stringResource(R.string.demo_one_ui7_fact_recipe_set)
    val componentBackendsLabel = stringResource(R.string.demo_one_ui7_fact_component_backends)
    val overlayTransportLabel = stringResource(R.string.demo_one_ui7_fact_overlay_transport)
    val overlayPresentersLabel = stringResource(R.string.demo_one_ui7_fact_overlay_presenters)
    Column(
        spacing = 10.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.demo_one_ui7_ready),
            color = Theme.colors.onSurfaceVariant,
            style = Theme.typography.labelMedium,
            modifier = Modifier.oneUi7ScenarioTarget(scenario, DemoAutomationRole.Ready),
        )
        Text(
            text = stringResource(R.string.demo_one_ui7_title),
            color = Theme.colors.onBackground,
            style = Theme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.demo_one_ui7_summary),
            color = Theme.colors.onSurfaceVariant,
            style = Theme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.demo_one_ui7_buttons),
            color = Theme.colors.onSurface,
            style = Theme.typography.titleMedium,
        )
        Row(spacing = 10.dp, verticalAlignment = VerticalAlignment.Center) {
            OneUi7Button(
                text = stringResource(R.string.demo_one_ui7_continue),
                onClick = onContinue,
                modifier = Modifier.oneUi7ScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            OneUi7Button(
                text = stringResource(R.string.demo_one_ui7_later),
                onClick = {},
                variant = OneUi7ButtonVariant.Neutral,
            )
        }
        OneUi7Button(
            text = stringResource(R.string.demo_one_ui7_flat_action),
            onClick = {},
            variant = OneUi7ButtonVariant.Flat,
        )
        Text(
            text = stringResource(R.string.demo_one_ui7_button_status, clicks),
            color = Theme.colors.onSurfaceVariant,
            style = Theme.typography.bodySmall,
            modifier = Modifier.oneUi7ScenarioTarget(scenario, DemoAutomationRole.State),
        )
        OneUi7Button(
            text = stringResource(R.string.demo_one_ui7_reset),
            onClick = onReset,
            modifier = Modifier.oneUi7ScenarioTarget(scenario, DemoAutomationRole.Reset),
        )
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_one_ui7_screenshot_identity),
            facts = listOf(
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_fact_reference),
                    OneUi7Reference.targetVersion,
                ),
                DiagnosticFact(componentSetLabel, OneUi7Reference.componentSet),
                DiagnosticFact(tokenSourceLabel, Theme.current.metadata.provenance.sourceId),
                DiagnosticFact(
                    primarySourceLabel,
                    Theme.current.metadata.provenance.originOf("colors.primary").name,
                ),
                DiagnosticFact(designSystemLabel, attribution?.designSystemId ?: unattributed),
                DiagnosticFact(recipeSetLabel, attribution?.recipeSetId ?: unattributed),
                DiagnosticFact(componentBackendsLabel, componentEvidence),
                DiagnosticFact(
                    overlayTransportLabel,
                    attribution?.integration("overlay.dialog")?.transportId ?: unattributed,
                ),
                DiagnosticFact(overlayPresentersLabel, overlayEvidence),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_fact_mode),
                    stringResource(
                        if (Theme.current.metadata.isDark == true) {
                            R.string.demo_one_ui7_mode_dark
                        } else {
                            R.string.demo_one_ui7_mode_light
                        },
                    ),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_fact_font_scale),
                    Environment.density.fontScale.toString(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_fact_direction),
                    Environment.layoutDirection.name,
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_fact_primary),
                    Theme.colors.primary.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_fact_surface),
                    Theme.colors.surface.asColorHex(),
                ),
            ),
            valueTagsByLabel = mapOf(
                componentSetLabel to DemoOneUi7TestTags.ONE_UI_7_IDENTITY,
                tokenSourceLabel to DemoOneUi7TestTags.ONE_UI_7_TOKEN_PRODUCER,
                primarySourceLabel to DemoOneUi7TestTags.ONE_UI_7_PRIMARY_ORIGIN,
                designSystemLabel to DemoOneUi7TestTags.ONE_UI_7_DESIGN_SYSTEM,
                recipeSetLabel to DemoOneUi7TestTags.ONE_UI_7_RECIPE_SET,
                componentBackendsLabel to DemoOneUi7TestTags.ONE_UI_7_COMPONENT_BACKENDS,
                overlayTransportLabel to DemoOneUi7TestTags.ONE_UI_7_OVERLAY_TRANSPORT,
                overlayPresentersLabel to DemoOneUi7TestTags.ONE_UI_7_OVERLAY_PRESENTERS,
            ),
        )
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_one_ui7_conformance),
            facts = listOf(
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_component_button),
                    stringResource(R.string.demo_one_ui7_conformance_button),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_component_surface),
                    stringResource(R.string.demo_one_ui7_conformance_surface),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_component_switch),
                    stringResource(R.string.demo_one_ui7_conformance_switch),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_component_text_field),
                    stringResource(R.string.demo_one_ui7_conformance_text_field),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_component_navigation),
                    stringResource(R.string.demo_one_ui7_conformance_navigation),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_one_ui7_component_backdrop_blur),
                    stringResource(R.string.demo_one_ui7_conformance_backdrop_blur),
                ),
            ),
        )
    }
}

private fun Modifier.oneUi7ScenarioTarget(
    scenario: DemoScenarioSpec,
    role: DemoAutomationRole,
): Modifier = demoAutomationTarget(scenario.automation.require(role))

private fun Modifier.oneUi7AndroidId(
    @IdRes id: Int,
    name: String,
): Modifier = nativeView(key = "demo-one-ui7:$name") { view ->
    if (view.id != id) {
        view.id = id
    }
}
