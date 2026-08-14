package com.viewcompose

import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.overlayAnchor
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

internal fun UiTreeBuilder.TransientFeedbackFixture(scenario: DemoScenarioSpec?) {
    val anchors = FeedbackAnchors()
    val state = TransientFeedbackState(
        dialogVisible = remember { mutableStateOf(false) },
        dialogCount = remember { mutableStateOf(0) },
        popupVisible = remember { mutableStateOf(false) },
        popupCount = remember { mutableStateOf(0) },
        snackbarVisible = remember { mutableStateOf(false) },
        snackbarCount = remember { mutableStateOf(0) },
        toastCount = remember { mutableStateOf(0) },
    )
    DeclareTransientFeedbackOverlays(anchors, state, scenario)

    LazyColumn(
        items = listOf("transient"),
        key = { section -> section },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "transient" -> ScenarioSection(
                kind = ScenarioKind.Core,
                title = stringResource(R.string.demo_feedback_transient_title),
                subtitle = stringResource(R.string.demo_feedback_transient_summary),
            ) {
                Text(
                    text = stringResource(
                        R.string.demo_feedback_transient_state,
                        state.dialogCount.value,
                        state.popupCount.value,
                        state.snackbarCount.value,
                        state.toastCount.value,
                    ),
                    style = UiTextStyle(fontSizeSp = 13.sp),
                    color = TextDefaults.secondaryColor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .feedbackScenarioTarget(scenario, DemoAutomationRole.State),
                )
                Button(
                    text = stringResource(R.string.demo_feedback_transient_primary_action),
                    onClick = {
                        state.dialogCount.value += 1
                        state.dialogVisible.value = true
                        state.snackbarCount.value += 1
                        state.snackbarVisible.value = true
                        state.toastCount.value += 1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(bottom = 8.dp)
                        .feedbackScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                )
                Button(
                    text = stringResource(R.string.demo_feedback_transient_popup_action),
                    variant = ButtonVariant.Tonal,
                    onClick = {
                        state.popupCount.value += 1
                        state.popupVisible.value = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .overlayAnchor(anchors.popup)
                        .feedbackScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                )
            }

            else -> error("Unsupported transient feedback section: $section")
        }
    }
}

internal fun UiTreeBuilder.DialogFeedbackFixture(scenario: DemoScenarioSpec?) {
    val state = DialogFeedbackState(
        alertVisible = remember { mutableStateOf(false) },
        iconAlertVisible = remember { mutableStateOf(false) },
        bottomSheetVisible = remember { mutableStateOf(false) },
        outcome = remember { mutableStateOf(DialogFeedbackOutcome.Idle) },
    )
    DeclareDialogFeedbackOverlays(state, scenario)

    LazyColumn(
        items = listOf("dialog"),
        key = { section -> section },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "dialog" -> {
                val visibleLabel = stringResource(R.string.demo_feedback_visible)
                val hiddenLabel = stringResource(R.string.demo_feedback_hidden)
                ScenarioSection(
                    kind = ScenarioKind.Core,
                    title = stringResource(R.string.demo_feedback_dialog_title),
                    subtitle = stringResource(R.string.demo_feedback_dialog_summary),
                ) {
                    Text(
                        text = stringResource(
                            R.string.demo_feedback_dialog_state,
                            dialogOutcomeLabel(state.outcome.value),
                            if (state.bottomSheetVisible.value) visibleLabel else hiddenLabel,
                            if (state.alertVisible.value || state.iconAlertVisible.value) {
                                visibleLabel
                            } else {
                                hiddenLabel
                            },
                        ),
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(bottom = 8.dp)
                            .feedbackScenarioTarget(scenario, DemoAutomationRole.State),
                    )
                    Button(
                        text = stringResource(R.string.demo_feedback_show_bottom_sheet),
                        onClick = { state.bottomSheetVisible.value = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(bottom = 8.dp)
                            .feedbackScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    )
                    Button(
                        text = stringResource(R.string.demo_feedback_show_alert),
                        variant = ButtonVariant.Tonal,
                        onClick = { state.alertVisible.value = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(bottom = 8.dp)
                            .feedbackScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                    )
                    Button(
                        text = stringResource(R.string.demo_feedback_show_icon_alert),
                        variant = ButtonVariant.Outlined,
                        onClick = { state.iconAlertVisible.value = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> error("Unsupported dialog feedback section: $section")
        }
    }
}

internal fun UiTreeBuilder.MenuFeedbackFixture(scenario: DemoScenarioSpec?) {
    val anchors = FeedbackAnchors()
    val state = MenuFeedbackState(
        expanded = remember { mutableStateOf(false) },
        selection = remember { mutableStateOf(MenuFeedbackSelection.None) },
        tooltipVisible = remember { mutableStateOf(false) },
    )
    DeclareMenuFeedbackOverlays(anchors, state, scenario)

    LazyColumn(
        items = listOf("menu"),
        key = { section -> section },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        when (section) {
            "menu" -> {
                val visibleLabel = stringResource(R.string.demo_feedback_visible)
                val hiddenLabel = stringResource(R.string.demo_feedback_hidden)
                ScenarioSection(
                    kind = ScenarioKind.Core,
                    title = stringResource(R.string.demo_feedback_menu_title),
                    subtitle = stringResource(R.string.demo_feedback_menu_summary),
                ) {
                    Text(
                        text = stringResource(
                            R.string.demo_feedback_menu_state,
                            menuSelectionLabel(state.selection.value),
                            if (state.expanded.value) visibleLabel else hiddenLabel,
                            if (state.tooltipVisible.value) visibleLabel else hiddenLabel,
                        ),
                        style = UiTextStyle(fontSizeSp = 13.sp),
                        color = TextDefaults.secondaryColor(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(bottom = 8.dp)
                            .feedbackScenarioTarget(scenario, DemoAutomationRole.State),
                    )
                    Button(
                        text = stringResource(
                            if (state.tooltipVisible.value) {
                                R.string.demo_feedback_hide_tooltip
                            } else {
                                R.string.demo_feedback_show_tooltip
                            },
                        ),
                        onClick = {
                            state.tooltipVisible.value = !state.tooltipVisible.value
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(bottom = 8.dp)
                            .overlayAnchor(anchors.tooltip)
                            .feedbackScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
                    )
                    Button(
                        text = stringResource(R.string.demo_feedback_open_menu),
                        variant = ButtonVariant.Tonal,
                        onClick = { state.expanded.value = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .margin(bottom = 8.dp)
                            .overlayAnchor(anchors.menu)
                            .feedbackScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
                    )
                    Button(
                        text = stringResource(R.string.demo_feedback_menu_reset),
                        variant = ButtonVariant.Outlined,
                        onClick = { resetMenuFeedback(state) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .feedbackScenarioTarget(scenario, DemoAutomationRole.Reset),
                    )
                }
            }

            else -> error("Unsupported menu feedback section: $section")
        }
    }
}

private fun UiTreeBuilder.dialogOutcomeLabel(outcome: DialogFeedbackOutcome): String =
    stringResource(
        when (outcome) {
            DialogFeedbackOutcome.Idle -> R.string.demo_feedback_outcome_idle
            DialogFeedbackOutcome.AlertConfirmed -> R.string.demo_feedback_outcome_alert_confirmed
            DialogFeedbackOutcome.AlertDismissed -> R.string.demo_feedback_outcome_alert_dismissed
            DialogFeedbackOutcome.IconConfirmed -> R.string.demo_feedback_outcome_icon_confirmed
            DialogFeedbackOutcome.IconDismissed -> R.string.demo_feedback_outcome_icon_dismissed
            DialogFeedbackOutcome.SheetSaved -> R.string.demo_feedback_outcome_sheet_saved
            DialogFeedbackOutcome.SheetDiscarded -> R.string.demo_feedback_outcome_sheet_discarded
        },
    )

private fun UiTreeBuilder.menuSelectionLabel(selection: MenuFeedbackSelection): String =
    stringResource(
        when (selection) {
            MenuFeedbackSelection.None -> R.string.demo_feedback_menu_none
            MenuFeedbackSelection.Edit -> R.string.demo_feedback_menu_edit
            MenuFeedbackSelection.Copy -> R.string.demo_feedback_menu_copy
            MenuFeedbackSelection.Share -> R.string.demo_feedback_menu_share
        },
    )

internal fun resetTransientFeedback(state: TransientFeedbackState) {
    state.dialogVisible.value = false
    state.dialogCount.value = 0
    state.popupVisible.value = false
    state.popupCount.value = 0
    state.snackbarVisible.value = false
    state.snackbarCount.value = 0
    state.toastCount.value = 0
}

internal fun resetDialogFeedback(state: DialogFeedbackState) {
    state.alertVisible.value = false
    state.iconAlertVisible.value = false
    state.bottomSheetVisible.value = false
    state.outcome.value = DialogFeedbackOutcome.Idle
}

internal fun resetMenuFeedback(state: MenuFeedbackState) {
    state.expanded.value = false
    state.selection.value = MenuFeedbackSelection.None
    state.tooltipVisible.value = false
}

internal fun Modifier.feedbackScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this
