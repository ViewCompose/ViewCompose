package com.viewcompose

import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.AlertDialog
import com.viewcompose.ui.foundation.Box
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Dialog
import com.viewcompose.ui.foundation.DialogPosition
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.DropdownMenu
import com.viewcompose.ui.foundation.DropdownMenuItem
import com.viewcompose.ui.foundation.ModalBottomSheet
import com.viewcompose.ui.foundation.ModalBottomSheetDefaults
import com.viewcompose.ui.foundation.PlainTooltip
import com.viewcompose.ui.foundation.Popup
import com.viewcompose.ui.foundation.PopupAlignment
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Snackbar
import com.viewcompose.ui.foundation.SnackbarDuration
import com.viewcompose.ui.foundation.SurfaceDefaults
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.Toast
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

internal fun UiTreeBuilder.DeclareTransientFeedbackOverlays(
    anchors: FeedbackAnchors,
    state: TransientFeedbackState,
    scenario: DemoScenarioSpec?,
) {
    Dialog(
        visible = state.dialogVisible.value,
        requestKey = "feedback_transient_dialog",
        position = DialogPosition.Bottom,
        scrimOpacity = 0.48f,
        onDismissRequest = { state.dialogVisible.value = false },
    ) {
        Column(
            spacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .backgroundColor(SurfaceDefaults.backgroundColor())
                .shape(SurfaceDefaults.shape())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.demo_feedback_custom_dialog_title,
                    state.dialogCount.value,
                ),
                style = UiTextStyle(fontSizeSp = 18.sp),
                modifier = Modifier.feedbackScenarioTarget(scenario, DemoAutomationRole.Target),
            )
            Text(
                text = stringResource(R.string.demo_feedback_custom_dialog_body),
                color = TextDefaults.secondaryColor(),
            )
            Row(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    text = stringResource(R.string.demo_feedback_custom_dialog_confirm),
                    onClick = { state.dialogVisible.value = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    text = stringResource(R.string.demo_feedback_reset),
                    variant = ButtonVariant.Outlined,
                    onClick = { resetTransientFeedback(state) },
                    modifier = Modifier
                        .weight(1f)
                        .feedbackScenarioTarget(scenario, DemoAutomationRole.Reset),
                )
            }
        }
    }

    Popup(
        visible = state.popupVisible.value,
        anchorId = anchors.popup,
        requestKey = "feedback_transient_popup",
        alignment = PopupAlignment.AboveStart,
        offsetY = 8.dp,
        onDismissRequest = { state.popupVisible.value = false },
    ) {
        Column(
            spacing = 10.dp,
            modifier = Modifier
                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                .shape(SurfaceDefaults.shape())
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.demo_feedback_popup_title,
                    state.popupCount.value,
                ),
                style = UiTextStyle(fontSizeSp = 16.sp),
            )
            Text(
                text = stringResource(R.string.demo_feedback_popup_body),
                color = TextDefaults.secondaryColor(),
            )
            Button(
                text = stringResource(R.string.demo_feedback_popup_close),
                variant = ButtonVariant.Outlined,
                onClick = { state.popupVisible.value = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .feedbackScenarioTarget(scenario, DemoAutomationRole.SecondaryTarget),
            )
        }
    }

    Snackbar(
        visible = state.snackbarVisible.value,
        message = stringResource(
            R.string.demo_feedback_snackbar_message,
            state.snackbarCount.value,
        ),
        actionLabel = stringResource(R.string.demo_feedback_snackbar_action),
        duration = SnackbarDuration.Long,
        requestKey = "feedback_transient_snackbar",
        onAction = { state.snackbarVisible.value = false },
        onDismiss = { state.snackbarVisible.value = false },
    )

    Toast(
        visible = state.toastCount.value > 0,
        message = stringResource(R.string.demo_feedback_toast_message, state.toastCount.value),
        requestKey = "feedback_transient_toast_${state.toastCount.value}",
    )
}

internal fun UiTreeBuilder.DeclareDialogFeedbackOverlays(
    state: DialogFeedbackState,
    scenario: DemoScenarioSpec?,
) {
    AlertDialog(
        visible = state.alertVisible.value,
        title = stringResource(R.string.demo_feedback_alert_title),
        text = stringResource(R.string.demo_feedback_alert_body),
        confirmButtonText = stringResource(R.string.demo_feedback_alert_confirm),
        onConfirm = {
            state.alertVisible.value = false
            state.outcome.value = DialogFeedbackOutcome.AlertConfirmed
        },
        dismissButtonText = stringResource(R.string.demo_feedback_alert_cancel),
        onDismiss = {
            state.alertVisible.value = false
            state.outcome.value = DialogFeedbackOutcome.AlertDismissed
        },
        onDismissRequest = {
            state.alertVisible.value = false
            state.outcome.value = DialogFeedbackOutcome.AlertDismissed
        },
        requestKey = "feedback_alert_dialog",
    )

    AlertDialog(
        visible = state.iconAlertVisible.value,
        title = stringResource(R.string.demo_feedback_icon_alert_title),
        text = stringResource(R.string.demo_feedback_icon_alert_body),
        confirmButtonText = stringResource(R.string.demo_feedback_icon_alert_confirm),
        onConfirm = {
            state.iconAlertVisible.value = false
            state.outcome.value = DialogFeedbackOutcome.IconConfirmed
        },
        dismissButtonText = stringResource(R.string.demo_feedback_icon_alert_cancel),
        onDismiss = {
            state.iconAlertVisible.value = false
            state.outcome.value = DialogFeedbackOutcome.IconDismissed
        },
        onDismissRequest = {
            state.iconAlertVisible.value = false
            state.outcome.value = DialogFeedbackOutcome.IconDismissed
        },
        icon = ImageSource.Resource(R.drawable.demo_media_icon),
        requestKey = "feedback_icon_alert_dialog",
    )

    ModalBottomSheet(
        visible = state.bottomSheetVisible.value,
        requestKey = "feedback_bottom_sheet",
        onDismissRequest = { resetDialogFeedback(state) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shape(SurfaceDefaults.shape())
                .backgroundColor(ModalBottomSheetDefaults.containerColor()),
        ) {
            Column(
                spacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.demo_feedback_bottom_sheet_title),
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.feedbackScenarioTarget(
                        scenario,
                        DemoAutomationRole.Target,
                    ),
                )
                Text(
                    text = stringResource(R.string.demo_feedback_bottom_sheet_body),
                    color = TextDefaults.secondaryColor(),
                )
                Divider()
                Button(
                    text = stringResource(R.string.demo_feedback_bottom_sheet_save),
                    onClick = {
                        state.bottomSheetVisible.value = false
                        state.outcome.value = DialogFeedbackOutcome.SheetSaved
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    text = stringResource(R.string.demo_feedback_bottom_sheet_discard),
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        state.bottomSheetVisible.value = false
                        state.outcome.value = DialogFeedbackOutcome.SheetDiscarded
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    text = stringResource(R.string.demo_feedback_reset),
                    variant = ButtonVariant.Tonal,
                    onClick = { resetDialogFeedback(state) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .feedbackScenarioTarget(scenario, DemoAutomationRole.Reset),
                )
            }
        }
    }
}

internal fun UiTreeBuilder.DeclareMenuFeedbackOverlays(
    anchors: FeedbackAnchors,
    state: MenuFeedbackState,
    scenario: DemoScenarioSpec?,
) {
    DropdownMenu(
        expanded = state.expanded.value,
        anchorId = anchors.menu,
        onDismissRequest = { state.expanded.value = false },
        requestKey = "feedback_dropdown_menu",
    ) {
        DropdownMenuItem(
            text = stringResource(R.string.demo_feedback_menu_edit),
            onClick = {
                state.selection.value = MenuFeedbackSelection.Edit
                state.expanded.value = false
            },
            leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
            modifier = Modifier.feedbackScenarioTarget(scenario, DemoAutomationRole.Target),
        )
        DropdownMenuItem(
            text = stringResource(R.string.demo_feedback_menu_copy),
            onClick = {
                state.selection.value = MenuFeedbackSelection.Copy
                state.expanded.value = false
            },
        )
        DropdownMenuItem(
            text = stringResource(R.string.demo_feedback_menu_share),
            onClick = {
                state.selection.value = MenuFeedbackSelection.Share
                state.expanded.value = false
            },
            trailingText = stringResource(R.string.demo_feedback_menu_shortcut),
        )
        DropdownMenuItem(
            text = stringResource(R.string.demo_feedback_menu_delete),
            onClick = {},
            enabled = false,
        )
    }

    PlainTooltip(
        text = stringResource(R.string.demo_feedback_tooltip_text),
        visible = state.tooltipVisible.value,
        anchorId = anchors.tooltip,
        onDismissRequest = { state.tooltipVisible.value = false },
        requestKey = "feedback_tooltip",
    )
}
