package com.viewcompose

import androidx.annotation.IdRes
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.nativeView
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Dialog
import com.viewcompose.ui.foundation.Environment
import com.viewcompose.ui.foundation.FlowRow
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
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
import com.viewcompose.ui.node.TextFieldAutofillHint
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

/** Renders one strict multi-design-system pressure fixture with screenshot-readable attribution. */
internal fun UiTreeBuilder.DemoDesignSystemVerificationPage(
    hostContext: DemoHostContextSnapshot,
    scenario: DemoScenarioSpec,
    onReplaceDesignSystem: (DemoDesignSystemKind) -> Unit,
) {
    val sessionGeneration = rememberSaveable(key = "design-system-session-generation") {
        mutableStateOf(0)
    }
    key(sessionGeneration.value) {
        DemoDesignSystemVerificationSession(
            hostContext = hostContext,
            scenario = scenario,
            sessionGeneration = sessionGeneration.value,
            onReplaceDesignSystem = onReplaceDesignSystem,
            onReset = { sessionGeneration.value += 1 },
        )
    }
}

private fun UiTreeBuilder.DemoDesignSystemVerificationSession(
    hostContext: DemoHostContextSnapshot,
    scenario: DemoScenarioSpec,
    sessionGeneration: Int,
    onReplaceDesignSystem: (DemoDesignSystemKind) -> Unit,
    onReset: () -> Unit,
) {
    val bundle = DemoDesignSystem
    val nextKind = when (bundle.kind) {
        DemoDesignSystemKind.RoundedReference -> DemoDesignSystemKind.CupertinoPressure
        DemoDesignSystemKind.CutContrast -> DemoDesignSystemKind.RoundedReference
        DemoDesignSystemKind.CupertinoPressure -> DemoDesignSystemKind.CutContrast
    }
    val checked = rememberSaveable(key = "design-system-switch") { mutableStateOf(true) }
    val selectedIndex = rememberSaveable(key = "design-system-navigation") { mutableStateOf(0) }
    val selectedSegment = rememberSaveable(key = "design-system-segmented") { mutableStateOf(0) }
    val buttonClicks = rememberSaveable(key = "design-system-button-clicks") { mutableStateOf(0) }
    val dialogVisible = rememberSaveable(key = "design-system-dialog-visible") { mutableStateOf(false) }
    val field = rememberTextFieldState("Ada")
    val errorField = rememberTextFieldState("")
    val segmentLabels = listOf(
        stringResource(R.string.demo_design_system_day),
        stringResource(R.string.demo_design_system_week),
        stringResource(R.string.demo_design_system_month),
    )
    val navigationItems = demoDesignNavigationItems(
        listOf(
            stringResource(R.string.demo_design_system_home),
            stringResource(R.string.demo_design_system_search),
            stringResource(R.string.demo_design_system_profile),
        ),
    )
    LazyColumn(
        items = listOf(
            "identity",
            "switching",
            "surface",
            "switch",
            "textfield",
            "segmented",
            "navigation",
        ),
        // Lazy items own independent logical Sessions; reset must replace those identities too.
        key = { section -> "$sessionGeneration:$section" },
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(Theme.colors.background)
            .systemBarsInsetsPadding()
            .padding(horizontal = 16.dp)
            .designSystemScenarioTarget(scenario, DemoAutomationRole.Root),
    ) { section ->
        when (section) {
            "identity" -> DemoDesignSystemIdentitySection(
                bundle = bundle,
                hostContext = hostContext,
                scenario = scenario,
                buttonClicks = buttonClicks.value,
                onConfirm = { buttonClicks.value += 1 },
                onReset = onReset,
            )

            "switching" -> Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle(stringResource(R.string.demo_design_system_root_coherence))
                Text(
                    text = stringResource(R.string.demo_design_system_lazy_identity, bundle.kind.id),
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodyMedium,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_LAZY_IDENTITY),
                )
                FlowRow(
                    horizontalSpacing = 10.dp,
                    verticalSpacing = 10.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DemoDesignButton(
                        text = stringResource(R.string.demo_design_system_switch_kind, nextKind.id),
                        onClick = { onReplaceDesignSystem(nextKind) },
                        modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_REPLACE_ROOT),
                    )
                    DemoDesignButton(
                        text = stringResource(R.string.demo_design_system_open_dialog),
                        onClick = { dialogVisible.value = true },
                        modifier = Modifier
                            .testTag(DemoTestTags.DESIGN_SYSTEM_OPEN_DIALOG)
                            .designSystemAndroidId(
                                R.id.demo_design_system_dialog_open,
                                "dialog-open",
                            ),
                    )
                }
            }

            "surface" -> Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle(stringResource(R.string.demo_design_system_surface_section))
                DemoDesignCard(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.DESIGN_SYSTEM_SURFACE),
                ) {
                    Column(spacing = 6.dp) {
                        Text(
                            text = stringResource(R.string.demo_design_system_resolved_surface),
                            color = Theme.colors.onSurface,
                            style = Theme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.demo_design_system_surface_summary),
                            color = Theme.colors.onSurfaceVariant,
                            style = Theme.typography.bodySmall,
                        )
                    }
                }
            }

            "switch" -> Column(
                spacing = 4.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle(stringResource(R.string.demo_design_system_switch_section))
                DemoDesignSwitch(
                    text = stringResource(R.string.demo_design_system_switch_label),
                    checked = checked.value,
                    onCheckedChange = { checked.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .designSystemScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                )
                DemoDesignSwitch(
                    text = stringResource(R.string.demo_design_system_switch_disabled),
                    checked = false,
                    onCheckedChange = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.DESIGN_SYSTEM_SWITCH_DISABLED),
                )
                Text(
                    text = stringResource(
                        R.string.demo_design_system_checked_status,
                        checked.value.toString(),
                    ),
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.designSystemScenarioTarget(
                        scenario,
                        DemoAutomationRole.SecondaryTarget,
                    ),
                )
            }

            "textfield" -> Column(
                spacing = 14.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle(stringResource(R.string.demo_design_system_text_field_section))
                DemoDesignTextField(
                    state = field,
                    label = stringResource(R.string.demo_design_system_account_name),
                    placeholder = stringResource(R.string.demo_design_system_name_placeholder),
                    supportingText = stringResource(R.string.demo_design_system_text_field_support),
                    isError = false,
                    autofillHints = setOf(TextFieldAutofillHint.Username),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.DESIGN_SYSTEM_TEXT_FIELD),
                )
                DemoDesignTextField(
                    state = errorField,
                    label = stringResource(R.string.demo_design_system_required_field),
                    placeholder = stringResource(R.string.demo_design_system_required_placeholder),
                    supportingText = stringResource(R.string.demo_design_system_required_support),
                    isError = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.DESIGN_SYSTEM_TEXT_FIELD_ERROR),
                )
            }

            "segmented" -> Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp),
            ) {
                DemoDesignSectionTitle(stringResource(R.string.demo_design_system_segmented_section))
                DemoDesignSegmentedControl(
                    labels = segmentLabels,
                    selectedIndex = selectedSegment.value,
                    onItemSelected = { selectedSegment.value = it },
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_SEGMENTED),
                )
                Text(
                    text = stringResource(
                        R.string.demo_design_system_segment_status,
                        segmentLabels[selectedSegment.value],
                    ),
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_SEGMENTED_STATUS),
                )
            }

            else -> Column(
                spacing = 10.dp,
                modifier = Modifier.fillMaxWidth().margin(top = 18.dp, bottom = 28.dp),
            ) {
                DemoDesignSectionTitle(stringResource(R.string.demo_design_system_navigation_section))
                DemoDesignNavigationBar(
                    items = navigationItems,
                    selectedIndex = selectedIndex.value,
                    onItemSelected = { selectedIndex.value = it },
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_NAVIGATION),
                )
                Text(
                    text = stringResource(
                        R.string.demo_design_system_selected_status,
                        navigationItems[selectedIndex.value].label,
                    ),
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodySmall,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_NAVIGATION_STATUS),
                )
            }
        }
    }
    Dialog(
        visible = dialogVisible.value,
        requestKey = "design-system-coherence-dialog",
        onDismissRequest = { dialogVisible.value = false },
    ) {
        DemoDesignCard(modifier = Modifier.fillMaxWidth()) {
            Column(spacing = 10.dp) {
                Text(
                    text = stringResource(
                        R.string.demo_design_system_overlay_identity,
                        bundle.kind.id,
                    ),
                    color = Theme.colors.onSurface,
                    style = Theme.typography.titleMedium,
                    modifier = Modifier
                        .testTag(DemoTestTags.DESIGN_SYSTEM_OVERLAY_IDENTITY)
                        .designSystemAndroidId(
                            R.id.demo_design_system_dialog_state,
                            "dialog-state",
                        ),
                )
                Text(
                    text = stringResource(R.string.demo_design_system_overlay_token, bundle.kind.id),
                    color = Theme.colors.onSurfaceVariant,
                    style = Theme.typography.bodyMedium,
                    modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_OVERLAY_TOKEN_SOURCE),
                )
                DemoDesignButton(
                    text = stringResource(R.string.demo_design_system_overlay_switch, nextKind.id),
                    onClick = { onReplaceDesignSystem(nextKind) },
                    modifier = Modifier.designSystemAndroidId(
                        R.id.demo_design_system_dialog_switch,
                        "dialog-switch",
                    ),
                )
                DemoDesignButton(
                    text = stringResource(R.string.demo_design_system_overlay_close),
                    onClick = { dialogVisible.value = false },
                    modifier = Modifier.designSystemAndroidId(
                        R.id.demo_design_system_dialog_close,
                        "dialog-close",
                    ),
                )
            }
        }
    }
}

private fun UiTreeBuilder.DemoDesignSystemIdentitySection(
    bundle: DemoDesignSystemBundle,
    hostContext: DemoHostContextSnapshot,
    scenario: DemoScenarioSpec,
    buttonClicks: Int,
    onConfirm: () -> Unit,
    onReset: () -> Unit,
) {
    val designSystemLabel = stringResource(R.string.demo_design_system_fact_design_system)
    val tokenSourceLabel = stringResource(R.string.demo_design_system_fact_token_source)
    val recipeIdentityLabel = stringResource(R.string.demo_design_system_fact_recipe_identity)
    val rootContextLabel = stringResource(R.string.demo_design_system_fact_root_context)
    val androidPrimaryLabel = stringResource(R.string.demo_design_system_fact_android_primary)
    val componentBackendsLabel = stringResource(R.string.demo_design_system_fact_component_backends)
    val modeLabel = stringResource(R.string.demo_design_system_fact_mode)
    val reducedMotionLabel = stringResource(R.string.demo_design_system_fact_reduced_motion)
    val fontScaleLabel = stringResource(R.string.demo_design_system_fact_font_scale)
    val capabilityLabel = stringResource(R.string.demo_design_system_fact_capability)
    Column(
        spacing = 10.dp,
        modifier = Modifier.fillMaxWidth().margin(top = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.demo_design_system_ready),
            color = Theme.colors.onSurfaceVariant,
            style = Theme.typography.labelMedium,
            modifier = Modifier.designSystemScenarioTarget(scenario, DemoAutomationRole.Ready),
        )
        Text(
            text = stringResource(R.string.demo_design_system_title),
            color = Theme.colors.onBackground,
            style = Theme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.demo_design_system_summary),
            color = Theme.colors.onSurfaceVariant,
            style = Theme.typography.bodyMedium,
            modifier = Modifier.designSystemScenarioTarget(scenario, DemoAutomationRole.Target),
        )
        Row(spacing = 10.dp, verticalAlignment = VerticalAlignment.Center) {
            DemoDesignButton(
                text = stringResource(R.string.demo_design_system_button_confirm),
                onClick = onConfirm,
                modifier = Modifier.designSystemScenarioTarget(
                    scenario,
                    DemoAutomationRole.PrimaryAction,
                ),
            )
            DemoDesignButton(
                text = stringResource(R.string.demo_design_system_button_disabled),
                enabled = false,
                modifier = Modifier.testTag(DemoTestTags.DESIGN_SYSTEM_BUTTON_DISABLED),
            )
        }
        Text(
            text = stringResource(R.string.demo_design_system_button_status, buttonClicks),
            color = Theme.colors.onSurfaceVariant,
            style = Theme.typography.bodySmall,
            modifier = Modifier.designSystemScenarioTarget(scenario, DemoAutomationRole.State),
        )
        DemoDesignButton(
            text = stringResource(R.string.demo_design_system_reset),
            onClick = onReset,
            modifier = Modifier.designSystemScenarioTarget(scenario, DemoAutomationRole.Reset),
        )
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_design_system_screenshot_identity),
            facts = listOf(
                DiagnosticFact(
                    stringResource(R.string.demo_design_system_fact_fixture),
                    "multi-design-system-pressure-v2",
                ),
                DiagnosticFact(
                    designSystemLabel,
                    "${bundle.kind.id} · ${stringResource(bundle.kind.labelRes)}",
                ),
                DiagnosticFact(tokenSourceLabel, "demo-design-system/${bundle.kind.id}"),
                DiagnosticFact(recipeIdentityLabel, "${bundle.kind.id}/pressure-v2"),
                DiagnosticFact(rootContextLabel, hostContext.chain),
                DiagnosticFact(androidPrimaryLabel, hostContext.androidPrimary.asColorHex()),
                DiagnosticFact(
                    componentBackendsLabel,
                    stringResource(R.string.demo_design_system_component_backends_value),
                ),
                DiagnosticFact(
                    modeLabel,
                    stringResource(
                        if (bundle.tokens.metadata.isDark == true) {
                            R.string.demo_design_system_mode_dark
                        } else {
                            R.string.demo_design_system_mode_light
                        },
                    ),
                ),
                DiagnosticFact(reducedMotionLabel, bundle.reducedMotionEnabled.toString()),
                DiagnosticFact(fontScaleLabel, Environment.density.fontScale.toString()),
                DiagnosticFact(
                    stringResource(R.string.demo_design_system_fact_layout_direction),
                    Environment.layoutDirection.name,
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_design_system_fact_shape),
                    bundle.tokens.shapes.medium.demoLabel(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_design_system_fact_primary),
                    bundle.tokens.colors.primary.asColorHex(),
                ),
                DiagnosticFact(
                    stringResource(R.string.demo_design_system_fact_surface),
                    bundle.tokens.colors.surface.asColorHex(),
                ),
                DiagnosticFact(capabilityLabel, bundle.capabilitySummary()),
            ),
            valueTagsByLabel = mapOf(
                designSystemLabel to DemoTestTags.DESIGN_SYSTEM_IDENTITY,
                tokenSourceLabel to DemoTestTags.DESIGN_SYSTEM_TOKEN_SOURCE,
                recipeIdentityLabel to DemoTestTags.DESIGN_SYSTEM_RECIPE_IDENTITY,
                rootContextLabel to DemoTestTags.DESIGN_SYSTEM_ROOT_CONTEXT,
                androidPrimaryLabel to DemoTestTags.DESIGN_SYSTEM_ANDROID_PRIMARY,
                componentBackendsLabel to DemoTestTags.DESIGN_SYSTEM_COMPONENT_BACKENDS,
                modeLabel to DemoTestTags.DESIGN_SYSTEM_MODE,
                reducedMotionLabel to DemoTestTags.DESIGN_SYSTEM_REDUCED_MOTION,
                fontScaleLabel to DemoTestTags.DESIGN_SYSTEM_FONT_SCALE,
                capabilityLabel to DemoTestTags.DESIGN_SYSTEM_CAPABILITY,
            ),
        )
        DiagnosticFactGroup(
            title = stringResource(R.string.demo_design_system_conformance),
            facts = bundle.conformance.map { item ->
                DiagnosticFact(
                    item.component,
                    "${item.outcome.name} · ${item.implementation} · fallback=${item.fallback}",
                )
            },
        )
    }
}

private fun DemoDesignSystemBundle.capabilitySummary(): String {
    return if (kind == DemoDesignSystemKind.CupertinoPressure) {
        "continuous-path=exact; shape-morph=discrete-endpoint; " +
            "backdrop-blur=tinted-translucent-surface"
    } else {
        "continuous-path=yes; backdrop-blur=tinted-surface"
    }
}

private fun UiTreeBuilder.DemoDesignSectionTitle(text: String) {
    Text(
        text = text,
        color = Theme.colors.onSurface,
        style = UiTextStyle(fontSizeSp = 18.sp, fontWeight = 650, lineHeightSp = 24.sp),
    )
}

private fun Modifier.designSystemScenarioTarget(
    scenario: DemoScenarioSpec,
    role: DemoAutomationRole,
): Modifier = demoAutomationTarget(scenario.automation.require(role))

private fun Modifier.designSystemAndroidId(
    @IdRes id: Int,
    name: String,
): Modifier = nativeView(key = "demo-design-system:$name") { view ->
    if (view.id != id) {
        view.id = id
    }
}
