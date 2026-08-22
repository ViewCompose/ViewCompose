package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.focusRequester
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.TextFieldImeAction
import com.viewcompose.ui.node.TextFieldKeyboardOptions
import com.viewcompose.ui.node.policy.GridCells
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.node.policy.LazyContentPadding
import com.viewcompose.runtime.derivedStateOf
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonSize
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Checkbox
import com.viewcompose.ui.foundation.CheckboxOverrides
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Icon
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.LazyVerticalGrid
import com.viewcompose.ui.foundation.LocalFocusManager
import com.viewcompose.ui.foundation.ProvideCheckboxOverrides
import com.viewcompose.ui.foundation.ProvideRadioButtonOverrides
import com.viewcompose.ui.foundation.ProvideSliderOverrides
import com.viewcompose.ui.foundation.ProvideSwitchOverrides
import com.viewcompose.ui.foundation.PullToRefresh
import com.viewcompose.ui.foundation.RadioButton
import com.viewcompose.ui.foundation.RadioButtonOverrides
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.SearchBar
import com.viewcompose.ui.foundation.ScrollableColumn
import com.viewcompose.ui.foundation.Slider
import com.viewcompose.ui.foundation.SliderOverrides
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.Switch
import com.viewcompose.ui.foundation.SwitchOverrides
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextFieldInputProfile
import com.viewcompose.ui.foundation.TextFieldLinePolicy
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.TextField
import com.viewcompose.ui.foundation.TextFieldSize
import com.viewcompose.ui.foundation.TextFieldVariant
import com.viewcompose.ui.foundation.Theme
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiThemeOverride
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.VerticalPager
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberTextFieldState
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Input · Fields", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputFields() {
    InputPage(InputFixture.Fields)
}

@ViewComposePreview(name = "Input · Selection", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputSelection() {
    InputPage(InputFixture.Selection)
}

@ViewComposePreview(name = "Input · Stress", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputStress() {
    InputPage(InputFixture.Stress)
}

@ViewComposePreview(name = "Input · Search", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputSearch() {
    InputPage(InputFixture.Search)
}

@ViewComposePreview(name = "Input · Focus follow · Lazy column", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputFocusFollowLazyColumn() {
    InputPage(InputFixture.FocusFollowLazyColumn)
}

@ViewComposePreview(name = "Input · Focus follow · Lazy grid", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputFocusFollowLazyGrid() {
    InputPage(InputFixture.FocusFollowLazyGrid)
}

@ViewComposePreview(name = "Input · Focus follow · Scrollable column", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputFocusFollowScrollableColumn() {
    InputPage(InputFixture.FocusFollowScrollableColumn)
}

@ViewComposePreview(name = "Input · Focus follow · Vertical pager", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputFocusFollowVerticalPager() {
    InputPage(InputFixture.FocusFollowVerticalPager)
}

@ViewComposePreview(name = "Input · Focus follow · Pull refresh", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputFocusFollowPullRefresh() {
    InputPage(InputFixture.FocusFollowPullRefresh)
}

@ViewComposePreview(name = "Input · Summary", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewInputSummary() {
    InputPage(InputFixture.DerivedSummary)
}

internal enum class InputFixture(
    val scenarioId: DemoScenarioId,
) {
    Fields(DemoScenarioIds.InputFields),
    Selection(DemoScenarioIds.InputSelection),
    Stress(DemoScenarioIds.InputStress),
    Search(DemoScenarioIds.InputSearch),
    FocusFollowLazyColumn(DemoScenarioIds.InputFocusFollowLazyColumn),
    FocusFollowLazyGrid(DemoScenarioIds.InputFocusFollowLazyGrid),
    FocusFollowScrollableColumn(DemoScenarioIds.InputFocusFollowScrollableColumn),
    FocusFollowVerticalPager(DemoScenarioIds.InputFocusFollowVerticalPager),
    FocusFollowPullRefresh(DemoScenarioIds.InputFocusFollowPullRefresh),
    DerivedSummary(DemoScenarioIds.InputDerivedSummary),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): InputFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported input scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.InputPage(
    fixture: InputFixture,
    scenario: DemoScenarioSpec? = null,
) {
    when (fixture) {
        InputFixture.FocusFollowLazyColumn -> {
            InputFocusFollowLazyColumnPage(scenario)
            return
        }

        InputFixture.FocusFollowLazyGrid -> {
            InputFocusFollowLazyGridPage(scenario)
            return
        }

        InputFixture.FocusFollowScrollableColumn -> {
            InputFocusFollowScrollableColumnPage(scenario)
            return
        }

        InputFixture.FocusFollowVerticalPager -> {
            InputFocusFollowVerticalPagerPage(scenario)
            return
        }

        InputFixture.FocusFollowPullRefresh -> {
            InputFocusFollowPullRefreshPage(scenario)
            return
        }

        else -> Unit
    }
    val fieldsActive = fixture == InputFixture.Fields
    val selectionActive = fixture == InputFixture.Selection
    val stressActive = fixture == InputFixture.Stress
    val searchActive = fixture == InputFixture.Search
    val summaryActive = fixture == InputFixture.DerivedSummary

    // A strict fixture owns only the state it renders. Inactive scenario state must not enter the
    // composition observer graph or contaminate benchmark allocation and invalidation counts.
    val bioInitial = if (fieldsActive) stringResource(R.string.demo_input_bio_initial) else ""
    val benchmarkCompactData = if (fieldsActive) {
        stringResource(R.string.demo_input_benchmark_compact_data)
    } else {
        ""
    }
    val benchmarkExpandedData = if (fieldsActive) {
        stringResource(R.string.demo_input_benchmark_expanded_data)
    } else {
        ""
    }
    val benchmarkExpandedState = if (fieldsActive) remember { mutableStateOf(false) } else null
    val nameState = if (fieldsActive) rememberTextFieldState("GZQ") else null
    val emailState = if (fieldsActive) rememberTextFieldState("demo@viewcompose.dev") else null
    val passwordState = if (fieldsActive) rememberTextFieldState() else null
    val ageState = if (fieldsActive) rememberTextFieldState("3") else null
    val bioState = if (fieldsActive) rememberTextFieldState(bioInitial) else null
    val benchmarkFieldState = if (fieldsActive) rememberTextFieldState(benchmarkCompactData) else null
    val disabledEmailState = if (fieldsActive) {
        rememberTextFieldState("disabled@viewcompose.dev")
    } else {
        null
    }

    val notificationsEnabledState = if (selectionActive) remember { mutableStateOf(true) } else null
    val analyticsEnabledState = if (selectionActive) remember { mutableStateOf(false) } else null
    val selectedTierState = if (selectionActive) remember { mutableStateOf("Alpha") } else null
    val intensityState = if (selectionActive) remember { mutableStateOf(32) } else null

    val stressExpandedState = if (stressActive) remember { mutableStateOf(false) } else null
    val stressReadonlyState = if (stressActive) remember { mutableStateOf(true) } else null
    val stressErrorState = if (stressActive) remember { mutableStateOf(true) } else null
    val stressCompactTitle = if (stressActive) {
        stringResource(R.string.demo_input_stress_compact_title)
    } else {
        ""
    }
    val stressReadonlyNote = if (stressActive) {
        stringResource(R.string.demo_input_stress_readonly_note)
    } else {
        ""
    }
    val stressExpandedTitle = if (stressActive) {
        stringResource(R.string.demo_input_stress_expanded_title)
    } else {
        ""
    }
    val stressExpandedNotes = if (stressActive) {
        stringResource(R.string.demo_input_stress_expanded_notes)
    } else {
        ""
    }
    val stressTitleFieldState = if (stressActive) rememberTextFieldState(stressCompactTitle) else null
    val stressNotesFieldState = if (stressActive) rememberTextFieldState(stressReadonlyNote) else null
    val stressPasswordFieldState = if (stressActive) rememberTextFieldState() else null

    val searchQueryState = if (searchActive) rememberTextFieldState() else null
    val searchHistoryState = if (searchActive) rememberTextFieldState() else null
    val disabledSearchState = if (searchActive) rememberTextFieldState() else null
    val searchResultState = if (searchActive) remember { mutableStateOf("") } else null
    val summaryAlternateState = if (summaryActive) remember { mutableStateOf(false) } else null
    val summaryState = if (summaryActive) {
        val activeSummaryAlternateState = requireNotNull(summaryAlternateState)
        val defaultSummary = stringResource(R.string.demo_input_summary_default)
        val alternateSummary = stringResource(R.string.demo_input_summary_alternate)
        remember(defaultSummary, alternateSummary) {
            derivedStateOf {
                if (activeSummaryAlternateState.value) {
                    alternateSummary
                } else {
                    defaultSummary
                }
            }
        }
    } else {
        null
    }
    val pageItems = when (fixture) {
        InputFixture.Fields -> listOf("benchmark", "form")
        InputFixture.Selection -> listOf("controls")
        InputFixture.Stress -> listOf("stress")
        InputFixture.Search -> listOf("search")
        InputFixture.FocusFollowLazyColumn,
        InputFixture.FocusFollowLazyGrid,
        InputFixture.FocusFollowScrollableColumn,
        InputFixture.FocusFollowVerticalPager,
        InputFixture.FocusFollowPullRefresh,
        -> error("Focus-follow fixtures are rendered by dedicated roots")
        InputFixture.DerivedSummary -> listOf("summary")
    }

    LazyColumn(
        items = pageItems,
        key = { it },
        modifier = Modifier
            .fillMaxSize(),
    ) { section ->
        when (section) {
            "benchmark" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_input_benchmark_title),
                subtitle = stringResource(R.string.demo_input_benchmark_summary),
            ) {
                val benchmarkExpandedState = requireNotNull(benchmarkExpandedState)
                val benchmarkFieldState = requireNotNull(benchmarkFieldState)
                val nameState = requireNotNull(nameState)
                val emailState = requireNotNull(emailState)
                val passwordState = requireNotNull(passwordState)
                val ageState = requireNotNull(ageState)
                val bioState = requireNotNull(bioState)
                Text(
                    text = stringResource(
                        if (benchmarkExpandedState.value) {
                            R.string.demo_input_benchmark_state_expanded
                        } else {
                            R.string.demo_input_benchmark_state_collapsed
                        },
                    ),
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(
                        if (benchmarkExpandedState.value) {
                            R.string.demo_input_benchmark_action_expanded
                        } else {
                            R.string.demo_input_benchmark_action_collapsed
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    onClick = {
                        benchmarkExpandedState.value = !benchmarkExpandedState.value
                        benchmarkFieldState.setTextAndPlaceCursorAtEnd(
                            if (benchmarkExpandedState.value) {
                                benchmarkExpandedData
                            } else {
                                benchmarkCompactData
                            },
                        )
                    },
                )
                Button(
                    text = stringResource(R.string.demo_input_benchmark_reset),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = {
                        benchmarkExpandedState.value = false
                        benchmarkFieldState.setTextAndPlaceCursorAtEnd(benchmarkCompactData)
                        nameState.setTextAndPlaceCursorAtEnd("GZQ")
                        emailState.setTextAndPlaceCursorAtEnd("demo@viewcompose.dev")
                        passwordState.clearText()
                        ageState.setTextAndPlaceCursorAtEnd("3")
                        bioState.setTextAndPlaceCursorAtEnd(bioInitial)
                    },
                )
                TextField(
                    state = benchmarkFieldState,
                    label = stringResource(R.string.demo_input_benchmark_field_label),
                    supportingText = if (benchmarkExpandedState.value) {
                        stringResource(R.string.demo_input_benchmark_support_expanded)
                    } else {
                        stringResource(R.string.demo_input_benchmark_support_compact)
                    },
                    readOnly = true,
                    variant = TextFieldVariant.Outlined,
                    size = TextFieldSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
            }

            "form" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_input_form_title),
                subtitle = stringResource(R.string.demo_input_form_summary),
            ) {
                val nameState = requireNotNull(nameState)
                val emailState = requireNotNull(emailState)
                val passwordState = requireNotNull(passwordState)
                val ageState = requireNotNull(ageState)
                val bioState = requireNotNull(bioState)
                val disabledEmailState = requireNotNull(disabledEmailState)
                TextField(
                    state = nameState,
                    hint = stringResource(R.string.demo_input_name_hint),
                    label = stringResource(R.string.demo_input_name_label),
                    supportingText = stringResource(R.string.demo_input_name_support),
                    inputProfile = TextFieldInputProfile(
                        keyboardOptions = TextFieldKeyboardOptions(
                            imeAction = TextFieldImeAction.Next,
                        ),
                    ),
                    variant = TextFieldVariant.Filled,
                    size = TextFieldSize.Large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                TextField(
                    state = emailState,
                    hint = stringResource(R.string.demo_input_email_hint),
                    label = stringResource(R.string.demo_input_email_label),
                    supportingText = stringResource(R.string.demo_input_email_support),
                    inputProfile = TextFieldInputProfile(
                        keyboardOptions = TextFieldKeyboardOptions(
                            keyboardType = com.viewcompose.ui.node.TextFieldType.Email,
                            imeAction = TextFieldImeAction.Next,
                        ),
                        autofillHints = TextFieldInputProfile.Email.autofillHints,
                    ),
                    variant = TextFieldVariant.Tonal,
                    size = TextFieldSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                TextField(
                    state = passwordState,
                    hint = stringResource(R.string.demo_input_password_hint),
                    label = stringResource(R.string.demo_input_password_label),
                    supportingText = stringResource(R.string.demo_input_password_support),
                    inputProfile = TextFieldInputProfile(
                        keyboardOptions = TextFieldKeyboardOptions(
                            keyboardType = com.viewcompose.ui.node.TextFieldType.Password,
                            imeAction = TextFieldImeAction.Done,
                            autoCorrectEnabled = false,
                        ),
                        autofillHints = TextFieldInputProfile.Password.autofillHints,
                    ),
                    variant = TextFieldVariant.Outlined,
                    size = TextFieldSize.Medium,
                    isError = passwordState.text.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                TextField(
                    state = ageState,
                    hint = stringResource(R.string.demo_input_age_hint),
                    label = stringResource(R.string.demo_input_age_label),
                    supportingText = stringResource(R.string.demo_input_age_support),
                    inputProfile = TextFieldInputProfile.Number,
                    variant = TextFieldVariant.Outlined,
                    size = TextFieldSize.Compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                TextField(
                    state = disabledEmailState,
                    hint = stringResource(R.string.demo_input_disabled_email_hint),
                    label = stringResource(R.string.demo_input_disabled_email_label),
                    supportingText = stringResource(R.string.demo_input_disabled_email_support),
                    inputProfile = TextFieldInputProfile.Email,
                    variant = TextFieldVariant.Tonal,
                    size = TextFieldSize.Medium,
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                TextField(
                    state = bioState,
                    hint = stringResource(R.string.demo_input_bio_hint),
                    label = stringResource(R.string.demo_input_bio_label),
                    supportingText = stringResource(R.string.demo_input_bio_support),
                    linePolicy = TextFieldLinePolicy.MultiLine(maxLines = 6),
                    inputProfile = TextFieldInputProfile(
                        keyboardOptions = TextFieldKeyboardOptions(
                            imeAction = TextFieldImeAction.Done,
                        ),
                    ),
                    variant = TextFieldVariant.Filled,
                    size = TextFieldSize.Large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.INPUT_BIO_FIELD)
                        .margin(bottom = 12.dp),
                )
                Button(
                    text = stringResource(R.string.demo_input_form_reset),
                    leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                    trailingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                    size = ButtonSize.Large,
                    onClick = {
                        nameState.setTextAndPlaceCursorAtEnd("GZQ")
                        emailState.setTextAndPlaceCursorAtEnd("demo@viewcompose.dev")
                        passwordState.clearText()
                        ageState.setTextAndPlaceCursorAtEnd("3")
                        bioState.setTextAndPlaceCursorAtEnd(bioInitial)
                    },
                )
            }

            "controls" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_input_selection_title),
                subtitle = stringResource(R.string.demo_input_selection_summary),
            ) {
                val notificationsEnabledState = requireNotNull(notificationsEnabledState)
                val analyticsEnabledState = requireNotNull(analyticsEnabledState)
                val selectedTierState = requireNotNull(selectedTierState)
                val intensityState = requireNotNull(intensityState)
                Text(
                    text = stringResource(
                        R.string.demo_input_selection_state,
                        notificationsEnabledState.value,
                        analyticsEnabledState.value,
                        selectedTierState.value,
                        intensityState.value,
                    ),
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.margin(bottom = 12.dp),
                ) {
                    Button(
                        text = stringResource(R.string.demo_input_selection_toggle),
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = {
                            val alternate = notificationsEnabledState.value
                            notificationsEnabledState.value = !alternate
                            analyticsEnabledState.value = alternate
                            selectedTierState.value = if (alternate) "Beta" else "Alpha"
                            intensityState.value = if (alternate) 68 else 32
                        },
                    )
                    Button(
                        text = stringResource(R.string.demo_input_selection_reset),
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                        onClick = {
                            notificationsEnabledState.value = true
                            analyticsEnabledState.value = false
                            selectedTierState.value = "Alpha"
                            intensityState.value = 32
                        },
                    )
                }
                Checkbox(
                    text = stringResource(R.string.demo_input_notifications),
                    checked = notificationsEnabledState.value,
                    onCheckedChange = { notificationsEnabledState.value = it },
                    modifier = Modifier
                        .margin(bottom = 8.dp)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
                Switch(
                    text = stringResource(R.string.demo_input_analytics),
                    checked = analyticsEnabledState.value,
                    onCheckedChange = { analyticsEnabledState.value = it },
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                RadioButton(
                    text = stringResource(R.string.demo_input_tier_alpha),
                    checked = selectedTierState.value == "Alpha",
                    onCheckedChange = { checked -> if (checked) selectedTierState.value = "Alpha" },
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                RadioButton(
                    text = stringResource(R.string.demo_input_tier_beta),
                    checked = selectedTierState.value == "Beta",
                    onCheckedChange = { checked -> if (checked) selectedTierState.value = "Beta" },
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                Text(
                    text = stringResource(R.string.demo_input_intensity, intensityState.value),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Slider(
                    value = intensityState.value,
                    min = 0,
                    max = 100,
                    onValueChange = { intensityState.value = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                ProvideCheckboxOverrides(
                    CheckboxOverrides(
                        checkedColor = Theme.colors.secondary,
                        uncheckedColor = Theme.colors.outline,
                        disabledCheckedColor = Theme.colors.outlineVariant,
                        disabledUncheckedColor = Theme.colors.outlineVariant,
                        labelColor = Theme.colors.onSurface,
                        disabledLabelColor = Theme.colors.onSurfaceVariant,
                    ),
                ) {
                    ProvideSwitchOverrides(
                        SwitchOverrides(
                            checkedThumbColor = Theme.colors.onSecondary,
                            uncheckedThumbColor = Theme.colors.outline,
                            checkedTrackColor = Theme.colors.secondary,
                            uncheckedTrackColor = Theme.colors.surfaceVariant,
                            disabledCheckedThumbColor = Theme.colors.outlineVariant,
                            disabledUncheckedThumbColor = Theme.colors.outlineVariant,
                            disabledCheckedTrackColor = Theme.colors.surfaceVariant,
                            disabledUncheckedTrackColor = Theme.colors.surfaceVariant,
                            labelColor = Theme.colors.onSurface,
                            disabledLabelColor = Theme.colors.onSurfaceVariant,
                        ),
                    ) {
                        ProvideRadioButtonOverrides(
                            RadioButtonOverrides(
                                checkedColor = Theme.colors.secondary,
                                uncheckedColor = Theme.colors.outline,
                                disabledCheckedColor = Theme.colors.outlineVariant,
                                disabledUncheckedColor = Theme.colors.outlineVariant,
                                labelColor = Theme.colors.onSurface,
                                disabledLabelColor = Theme.colors.onSurfaceVariant,
                            ),
                        ) {
                            ProvideSliderOverrides(
                                SliderOverrides(
                                    thumbColor = Theme.colors.secondary,
                                    activeTrackColor = Theme.colors.secondary,
                                    inactiveTrackColor = Theme.colors.surfaceVariant,
                                    disabledThumbColor = Theme.colors.outlineVariant,
                                    disabledActiveTrackColor = Theme.colors.outlineVariant,
                                    disabledInactiveTrackColor = Theme.colors.surfaceVariant,
                                ),
                            ) {
                                Column(
                                    spacing = 8.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .backgroundColor(SurfaceDefaults.backgroundColor())
                                        .shape(SurfaceDefaults.shape())
                                        .padding(12.dp),
                                ) {
                                    Text(text = stringResource(R.string.demo_input_color_override))
                                    Checkbox(
                                        text = stringResource(R.string.demo_input_local_checkbox),
                                        checked = false,
                                        onCheckedChange = {},
                                    )
                                    Switch(
                                        text = stringResource(R.string.demo_input_disabled_switch),
                                        checked = false,
                                        enabled = false,
                                        onCheckedChange = {},
                                    )
                                    RadioButton(
                                        text = stringResource(R.string.demo_input_local_radio),
                                        checked = true,
                                        onCheckedChange = {},
                                    )
                                    Slider(
                                        value = 56,
                                        min = 0,
                                        max = 100,
                                        enabled = false,
                                        onValueChange = {},
                                        overrides = SliderOverrides(
                                            disabledThumbColor = Theme.colors.secondary,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "stress" -> ScenarioSection(
                kind = ScenarioKind.Stress,
                title = stringResource(R.string.demo_input_stress_title),
                subtitle = stringResource(R.string.demo_input_stress_summary),
            ) {
                val stressExpandedState = requireNotNull(stressExpandedState)
                val stressReadonlyState = requireNotNull(stressReadonlyState)
                val stressErrorState = requireNotNull(stressErrorState)
                val stressTitleFieldState = requireNotNull(stressTitleFieldState)
                val stressNotesFieldState = requireNotNull(stressNotesFieldState)
                val stressPasswordFieldState = requireNotNull(stressPasswordFieldState)
                Text(
                    text = stringResource(
                        R.string.demo_input_stress_state,
                        stressExpandedState.value,
                        stressReadonlyState.value,
                        stressErrorState.value,
                    ),
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.margin(bottom = 12.dp),
                ) {
                    Button(
                        text = stringResource(
                            if (stressExpandedState.value) {
                                R.string.demo_input_stress_use_compact_copy
                            } else {
                                R.string.demo_input_stress_expand_copy
                            },
                        ),
                        size = ButtonSize.Compact,
                        modifier = Modifier
                            .inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = {
                            stressExpandedState.value = !stressExpandedState.value
                            stressTitleFieldState.setTextAndPlaceCursorAtEnd(
                                if (stressExpandedState.value) {
                                    stressExpandedTitle
                                } else {
                                    stressCompactTitle
                                },
                            )
                            stressNotesFieldState.setTextAndPlaceCursorAtEnd(
                                if (stressExpandedState.value) {
                                    stressExpandedNotes
                                } else {
                                    stressReadonlyNote
                                },
                            )
                        },
                    )
                    Button(
                        text = stringResource(
                            if (stressReadonlyState.value) {
                                R.string.demo_input_stress_editable
                            } else {
                                R.string.demo_input_stress_read_only
                            },
                        ),
                        size = ButtonSize.Compact,
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier
                            .inputScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                        onClick = { stressReadonlyState.value = !stressReadonlyState.value },
                    )
                    Button(
                        text = stringResource(
                            if (stressErrorState.value) {
                                R.string.demo_input_stress_clear_error
                            } else {
                                R.string.demo_input_stress_show_error
                            },
                        ),
                        size = ButtonSize.Compact,
                        variant = ButtonVariant.Tonal,
                        modifier = Modifier.testTag(DemoTestTags.INPUT_STRESS_ERROR),
                        onClick = {
                            stressErrorState.value = !stressErrorState.value
                            if (stressErrorState.value) {
                                stressPasswordFieldState.clearText()
                            } else {
                                stressPasswordFieldState.setTextAndPlaceCursorAtEnd("stable-password")
                            }
                        },
                    )
                }
                Button(
                    text = stringResource(R.string.demo_input_stress_reset),
                    variant = ButtonVariant.Outlined,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                    onClick = {
                        stressExpandedState.value = false
                        stressReadonlyState.value = true
                        stressErrorState.value = true
                        stressTitleFieldState.setTextAndPlaceCursorAtEnd(stressCompactTitle)
                        stressNotesFieldState.setTextAndPlaceCursorAtEnd(stressReadonlyNote)
                        stressPasswordFieldState.clearText()
                    },
                )
                TextField(
                    state = stressTitleFieldState,
                    readOnly = true,
                    label = stringResource(R.string.demo_input_stress_channel_label),
                    supportingText = if (stressExpandedState.value) {
                        stringResource(R.string.demo_input_stress_support_long)
                    } else {
                        stringResource(R.string.demo_input_stress_support_short)
                    },
                    variant = TextFieldVariant.Outlined,
                    size = TextFieldSize.Large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                TextField(
                    state = stressNotesFieldState,
                    label = stringResource(R.string.demo_input_stress_notes_label),
                    supportingText = stringResource(R.string.demo_input_stress_notes_support),
                    readOnly = stressReadonlyState.value,
                    linePolicy = TextFieldLinePolicy.MultiLine(maxLines = 6),
                    variant = TextFieldVariant.Tonal,
                    size = TextFieldSize.Large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.INPUT_STRESS_NOTES_FIELD)
                        .margin(bottom = 12.dp),
                )
                TextField(
                    state = stressPasswordFieldState,
                    label = stringResource(R.string.demo_input_stress_protected_label),
                    supportingText = if (stressErrorState.value) {
                        stringResource(R.string.demo_input_stress_error_support)
                    } else {
                        stringResource(R.string.demo_input_stress_resolved_support)
                    },
                    inputProfile = TextFieldInputProfile.Password,
                    isError = stressErrorState.value,
                    variant = TextFieldVariant.Filled,
                    size = TextFieldSize.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
            }

            "search" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_input_search_title),
                subtitle = stringResource(R.string.demo_input_search_summary),
            ) {
                val searchQueryState = requireNotNull(searchQueryState)
                val searchHistoryState = requireNotNull(searchHistoryState)
                val disabledSearchState = requireNotNull(disabledSearchState)
                val searchResultState = requireNotNull(searchResultState)
                val searchResultFormat = stringResource(R.string.demo_input_search_result)
                Text(
                    text = if (searchQueryState.text.isBlank()) {
                        stringResource(R.string.demo_input_search_state_idle)
                    } else {
                        stringResource(R.string.demo_input_search_state_query, searchQueryState.text)
                    },
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.margin(bottom = 12.dp),
                ) {
                    Button(
                        text = stringResource(R.string.demo_input_search_fill),
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = {
                            searchQueryState.setTextAndPlaceCursorAtEnd("ViewCompose")
                            searchResultState.value = searchResultFormat.format("ViewCompose")
                        },
                    )
                    Button(
                        text = stringResource(R.string.demo_input_search_reset),
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                        onClick = {
                            searchQueryState.clearText()
                            searchHistoryState.clearText()
                            disabledSearchState.clearText()
                            searchResultState.value = ""
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.demo_input_search_basic),
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                SearchBar(
                    state = searchQueryState,
                    onSearch = { query -> searchResultState.value = searchResultFormat.format(query) },
                    placeholder = stringResource(R.string.demo_input_search_products_placeholder),
                    leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
                if (searchResultState.value.isNotEmpty()) {
                    Text(
                        text = searchResultState.value,
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier.margin(bottom = 12.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.demo_input_search_clearable),
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                SearchBar(
                    state = searchHistoryState,
                    onSearch = { query -> searchResultState.value = searchResultFormat.format(query) },
                    placeholder = stringResource(R.string.demo_input_search_history_placeholder),
                    leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
                    trailingIcon = {
                        if (searchHistoryState.text.isNotEmpty()) {
                            IconButton(
                                icon = ImageSource.Resource(R.drawable.demo_media_icon),
                                contentDescription = stringResource(
                                    R.string.demo_input_search_clear_description,
                                ),
                                onClick = searchHistoryState::clearText,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 12.dp),
                )
                Text(
                    text = stringResource(R.string.demo_input_search_disabled),
                    style = UiTextStyle(fontSizeSp = 14.sp),
                    modifier = Modifier.margin(bottom = 8.dp),
                )
                SearchBar(
                    state = disabledSearchState,
                    placeholder = stringResource(R.string.demo_input_search_unavailable_placeholder),
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            "summary" -> ScenarioSection(
                kind = ScenarioKind.Benchmark,
                title = stringResource(R.string.demo_input_summary_title),
                subtitle = stringResource(R.string.demo_input_summary_summary),
            ) {
                val summaryAlternateState = requireNotNull(summaryAlternateState)
                val summaryState = requireNotNull(summaryState)
                Text(
                    text = stringResource(
                        if (summaryAlternateState.value) {
                            R.string.demo_input_summary_state_alternate
                        } else {
                            R.string.demo_input_summary_state_default
                        },
                    ),
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Text(
                    text = summaryState.value,
                    modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
                Row(
                    spacing = 8.dp,
                    modifier = Modifier.margin(top = 12.dp),
                ) {
                    Button(
                        text = stringResource(R.string.demo_input_summary_toggle),
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                        onClick = { summaryAlternateState.value = !summaryAlternateState.value },
                    )
                    Button(
                        text = stringResource(R.string.demo_input_summary_reset),
                        variant = ButtonVariant.Outlined,
                        modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.Reset),
                        onClick = { summaryAlternateState.value = false },
                    )
                }
            }

            else -> error("Unsupported input section: $section")
        }
    }
}

private fun UiTreeBuilder.InputFocusFollowLazyColumnPage(scenario: DemoScenarioSpec?) {
    val queryState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    val focusRequestCount = remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current
    LazyColumn(
        spacing = 8.dp,
        contentPadding = LazyContentPadding.all(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header", contentRevision = "header") {
            FocusFollowHeader(
                scenario = scenario,
                fallbackTitle = R.string.demo_scenario_input_focus_follow_lazy_column_title,
                fallbackSummary = R.string.demo_scenario_input_focus_follow_lazy_column_summary,
            )
        }
        item(key = "controls", contentRevision = focusRequestCount.value) {
            FocusFollowControls(
                scenario = scenario,
                focusRequestCount = focusRequestCount.value,
                onFocus = {
                    if (focusRequester.requestFocus()) focusRequestCount.value += 1
                },
                onReset = {
                    focusManager.clearFocus(force = true)
                    queryState.clearText()
                    focusRequestCount.value = 0
                },
            )
        }
        items((1..3).toList(), key = { "before-$it" }) { index ->
            FocusFollowPlaceholder(index)
        }
        item(key = "search", contentRevision = "search") {
            SearchBar(
                state = queryState,
                placeholder = stringResource(R.string.demo_input_search_products_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(DemoTestTags.INPUT_FOCUS_LAZY_COLUMN_SEARCH)
                    .inputScenarioTarget(scenario, DemoAutomationRole.Target),
            )
        }
        items((4..11).toList(), key = { "after-$it" }) { index ->
            FocusFollowPlaceholder(index)
        }
    }
}

private fun UiTreeBuilder.InputFocusFollowLazyGridPage(scenario: DemoScenarioSpec?) {
    val queryState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    val focusRequestCount = remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current
    LazyVerticalGrid(
        cells = GridCells.Fixed(2),
        horizontalSpacing = 8.dp,
        verticalSpacing = 8.dp,
        contentPadding = LazyContentPadding.all(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(
            key = "header",
            contentRevision = "header",
            span = GridItemSpan.FullLine,
        ) {
            FocusFollowHeader(
                scenario = scenario,
                fallbackTitle = R.string.demo_scenario_input_focus_follow_lazy_grid_title,
                fallbackSummary = R.string.demo_scenario_input_focus_follow_lazy_grid_summary,
            )
        }
        item(
            key = "controls",
            contentRevision = focusRequestCount.value,
            span = GridItemSpan.FullLine,
        ) {
            FocusFollowControls(
                scenario = scenario,
                focusRequestCount = focusRequestCount.value,
                onFocus = {
                    if (focusRequester.requestFocus()) focusRequestCount.value += 1
                },
                onReset = {
                    focusManager.clearFocus(force = true)
                    queryState.clearText()
                    focusRequestCount.value = 0
                },
            )
        }
        items((1..8).toList(), key = { "before-$it" }) { index ->
            FocusFollowPlaceholder(index)
        }
        item(
            key = "search",
            contentRevision = "search",
            span = GridItemSpan.FullLine,
        ) {
            SearchBar(
                state = queryState,
                placeholder = stringResource(R.string.demo_input_search_products_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .inputScenarioTarget(scenario, DemoAutomationRole.Target),
            )
        }
        items((9..20).toList(), key = { "after-$it" }) { index ->
            FocusFollowPlaceholder(index)
        }
    }
}

private fun UiTreeBuilder.InputFocusFollowScrollableColumnPage(scenario: DemoScenarioSpec?) {
    val queryState = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    val focusRequestCount = remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current
    ScrollableColumn(
        spacing = 8.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        FocusFollowHeader(
            scenario = scenario,
            fallbackTitle = R.string.demo_scenario_input_focus_follow_scrollable_column_title,
            fallbackSummary = R.string.demo_scenario_input_focus_follow_scrollable_column_summary,
        )
        FocusFollowControls(
            scenario = scenario,
            focusRequestCount = focusRequestCount.value,
            onFocus = {
                if (focusRequester.requestFocus()) focusRequestCount.value += 1
            },
            onReset = {
                focusManager.clearFocus(force = true)
                queryState.clearText()
                focusRequestCount.value = 0
            },
        )
        (1..5).forEach { index -> FocusFollowPlaceholder(index) }
        SearchBar(
            state = queryState,
            placeholder = stringResource(R.string.demo_input_search_scrollable_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .testTag(DemoTestTags.INPUT_FOCUS_SCROLLABLE_SEARCH)
                .inputScenarioTarget(scenario, DemoAutomationRole.Target),
        )
        (8..19).forEach { index -> FocusFollowPlaceholder(index) }
    }
}

private fun UiTreeBuilder.InputFocusFollowVerticalPagerPage(scenario: DemoScenarioSpec?) {
    val queryState = rememberTextFieldState()
    val pageState = remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val focusRequestCount = remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current
    VerticalPager(
        currentPage = pageState.value,
        onPageChanged = { pageState.value = it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Page(key = "search", contentRevision = "search") {
            ScrollableColumn(
                spacing = 8.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                FocusFollowHeader(
                    scenario = scenario,
                    fallbackTitle = R.string.demo_scenario_input_focus_follow_vertical_pager_title,
                    fallbackSummary = R.string.demo_scenario_input_focus_follow_vertical_pager_summary,
                )
                FocusFollowControls(
                    scenario = scenario,
                    focusRequestCount = focusRequestCount.value,
                    onFocus = {
                        if (focusRequester.requestFocus()) focusRequestCount.value += 1
                    },
                    onReset = {
                        focusManager.clearFocus(force = true)
                        queryState.clearText()
                        focusRequestCount.value = 0
                        pageState.value = 0
                    },
                )
                Text(
                    text = stringResource(R.string.demo_input_search_pager_first_note),
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
                (1..4).forEach { index -> FocusFollowPlaceholder(index) }
                SearchBar(
                    state = queryState,
                    placeholder = stringResource(R.string.demo_input_search_pager_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag(DemoTestTags.INPUT_FOCUS_VERTICAL_PAGER_SEARCH)
                        .inputScenarioTarget(scenario, DemoAutomationRole.Target),
                )
                (5..12).forEach { index -> FocusFollowPlaceholder(index) }
            }
        }
        Page(key = "instructions", contentRevision = "instructions") {
            Column(
                spacing = 8.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                Text(text = stringResource(R.string.demo_input_search_pager_second_title))
                Text(
                    text = stringResource(R.string.demo_input_search_pager_second_note),
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                )
            }
        }
    }
}

private fun UiTreeBuilder.InputFocusFollowPullRefreshPage(scenario: DemoScenarioSpec?) {
    val queryState = rememberTextFieldState()
    val refreshingState = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusRequestCount = remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current
    PullToRefresh(
        isRefreshing = refreshingState.value,
        onRefresh = { refreshingState.value = true },
        modifier = Modifier.fillMaxSize(),
    ) {
        ScrollableColumn(
            spacing = 8.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            FocusFollowHeader(
                scenario = scenario,
                fallbackTitle = R.string.demo_scenario_input_focus_follow_pull_refresh_title,
                fallbackSummary = R.string.demo_scenario_input_focus_follow_pull_refresh_summary,
            )
            FocusFollowControls(
                scenario = scenario,
                focusRequestCount = focusRequestCount.value,
                onFocus = {
                    if (focusRequester.requestFocus()) focusRequestCount.value += 1
                },
                onReset = {
                    focusManager.clearFocus(force = true)
                    queryState.clearText()
                    refreshingState.value = false
                    focusRequestCount.value = 0
                },
            )
            Button(
                text = stringResource(
                    if (refreshingState.value) {
                        R.string.demo_input_search_stop_refresh
                    } else {
                        R.string.demo_input_search_simulate_refresh
                    },
                ),
                variant = ButtonVariant.Outlined,
                modifier = Modifier.fillMaxWidth(),
                onClick = { refreshingState.value = !refreshingState.value },
            )
            (1..7).forEach { index -> FocusFollowPlaceholder(index) }
            SearchBar(
                state = queryState,
                placeholder = stringResource(R.string.demo_input_search_refresh_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(DemoTestTags.INPUT_FOCUS_PULL_REFRESH_SEARCH)
                    .inputScenarioTarget(scenario, DemoAutomationRole.Target),
            )
            (8..19).forEach { index -> FocusFollowPlaceholder(index) }
        }
    }
}

private fun UiTreeBuilder.FocusFollowHeader(
    scenario: DemoScenarioSpec?,
    fallbackTitle: Int,
    fallbackSummary: Int,
) {
    Column(
        spacing = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DemoTestTags.INPUT_FOCUS_FOLLOW_HEADER),
    ) {
        Text(
            text = stringResource(scenario?.titleRes ?: fallbackTitle),
            style = Theme.typography.titleLarge,
        )
        Text(
            text = stringResource(scenario?.summaryRes ?: fallbackSummary),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
        )
    }
}

private fun UiTreeBuilder.FocusFollowControls(
    scenario: DemoScenarioSpec?,
    focusRequestCount: Int,
    onFocus: () -> Unit,
    onReset: () -> Unit,
) {
    Column(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.demo_input_focus_follow_state, focusRequestCount),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.inputScenarioTarget(scenario, DemoAutomationRole.State),
        )
        Row(spacing = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Button(
                text = stringResource(R.string.demo_input_focus_follow_action),
                onClick = onFocus,
                modifier = Modifier
                    .weight(1f)
                    .inputScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_input_focus_follow_reset),
                variant = ButtonVariant.Outlined,
                onClick = onReset,
                modifier = Modifier
                    .weight(1f)
                    .inputScenarioTarget(scenario, DemoAutomationRole.Reset),
            )
        }
    }
}

private fun UiTreeBuilder.FocusFollowPlaceholder(index: Int) {
    Text(
        text = stringResource(R.string.demo_input_search_placeholder_row, index),
        style = UiTextStyle(fontSizeSp = 13.sp),
        color = TextDefaults.secondaryColor(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    )
}

private fun Modifier.inputScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier {
    val target = scenario?.automation?.get(role) ?: return this
    return demoAutomationTarget(target)
}
