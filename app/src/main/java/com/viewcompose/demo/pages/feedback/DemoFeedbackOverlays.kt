package com.viewcompose

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.shape
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.node.ImageSource
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
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

internal fun UiTreeBuilder.DeclareFeedbackOverlays(
    anchors: FeedbackAnchors,
    state: FeedbackPageState,
) {
    Dialog(
        visible = state.dialogVisibleState.value,
        requestKey = "feedback_dialog",
        position = DialogPosition.Bottom,
        scrimOpacity = 0.48f,
        onDismissRequest = {
            if (state.dialogVisibleState.value) {
                state.dialogVisibleState.value = false
                state.lastEventState.value = "Dialog 关闭 ${state.dialogCountState.value}"
            }
        },
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
                text = "自定义 Dialog ${state.dialogCountState.value}",
                style = UiTextStyle(fontSizeSp = 18.sp),
            )
            Text(
                text = "Dialog 内容通过 overlay host 渲染到同一个 render session 中。",
                color = TextDefaults.secondaryColor(),
            )
            Row(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    text = "确认",
                    onClick = {
                        state.dialogVisibleState.value = false
                        state.lastEventState.value = "Dialog 确认 ${state.dialogCountState.value}"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DemoTestTags.FEEDBACK_DIALOG_CONFIRM),
                )
                Button(
                    text = "关闭",
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        state.dialogVisibleState.value = false
                        state.lastEventState.value = "Dialog 关闭 ${state.dialogCountState.value}"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DemoTestTags.FEEDBACK_DIALOG_CLOSE),
                )
            }
        }
    }

    Popup(
        visible = state.popupVisibleState.value,
        anchorId = anchors.popupAnchorId,
        requestKey = "feedback_popup",
        alignment = PopupAlignment.AboveStart,
        offsetY = 8.dp,
        onDismissRequest = {
            if (state.popupVisibleState.value) {
                state.popupVisibleState.value = false
                state.lastEventState.value = "Popup 关闭 ${state.popupCountState.value}"
            }
        },
    ) {
        Column(
            spacing = 10.dp,
            modifier = Modifier
                .backgroundColor(SurfaceDefaults.variantBackgroundColor())
                .shape(SurfaceDefaults.shape())
                .padding(12.dp),
        ) {
            Text(
                text = "Popup ${state.popupCountState.value}",
                style = UiTextStyle(fontSizeSp = 16.sp),
            )
            Text(
                text = "锚定弹窗内容，通过 anchor target 定位。",
                color = TextDefaults.secondaryColor(),
            )
            Button(
                text = "关闭 Popup",
                variant = ButtonVariant.Outlined,
                onClick = {
                    state.popupVisibleState.value = false
                    state.lastEventState.value = "Popup 手动关闭 ${state.popupCountState.value}"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DemoTestTags.FEEDBACK_POPUP_DISMISS),
            )
        }
    }

    Snackbar(
        visible = state.snackbarVisibleState.value,
        message = "Snackbar 通知 ${state.snackbarCountState.value}",
        actionLabel = "知道了",
        duration = SnackbarDuration.Long,
        requestKey = "feedback_snackbar",
        onAction = {
            state.lastEventState.value = "Snackbar 操作 ${state.snackbarCountState.value}"
            state.snackbarVisibleState.value = false
        },
        onDismiss = {
            if (state.snackbarVisibleState.value) {
                state.lastEventState.value = "Snackbar 消失 ${state.snackbarCountState.value}"
                state.snackbarVisibleState.value = false
            }
        },
    )

    Toast(
        visible = state.toastCountState.value > 0,
        message = "Toast 提示 ${state.toastCountState.value}",
        requestKey = "feedback_toast_${state.toastCountState.value}",
    )

    AlertDialog(
        visible = state.alertDialogVisibleState.value,
        title = "确认删除？",
        text = "此操作不可撤销，删除后数据将无法恢复。",
        confirmButtonText = "确定",
        onConfirm = {
            state.alertDialogVisibleState.value = false
            state.lastEventState.value = "AlertDialog 确认"
        },
        dismissButtonText = "取消",
        onDismiss = {
            state.alertDialogVisibleState.value = false
            state.lastEventState.value = "AlertDialog 取消"
        },
        onDismissRequest = {
            state.alertDialogVisibleState.value = false
            state.lastEventState.value = "AlertDialog 外部关闭"
        },
        requestKey = "feedback_alert_dialog",
    )

    AlertDialog(
        visible = state.alertDialogIconVisibleState.value,
        title = "更新可用",
        text = "发现新版本，是否立即更新？",
        confirmButtonText = "更新",
        onConfirm = {
            state.alertDialogIconVisibleState.value = false
            state.lastEventState.value = "AlertDialog 带图标 确认"
        },
        dismissButtonText = "稍后",
        onDismiss = {
            state.alertDialogIconVisibleState.value = false
            state.lastEventState.value = "AlertDialog 带图标 取消"
        },
        onDismissRequest = {
            state.alertDialogIconVisibleState.value = false
            state.lastEventState.value = "AlertDialog 带图标 外部关闭"
        },
        icon = ImageSource.Resource(R.drawable.demo_media_icon),
        requestKey = "feedback_alert_dialog_icon",
    )

    DropdownMenu(
        expanded = state.menuExpandedState.value,
        anchorId = anchors.menuAnchorId,
        onDismissRequest = { state.menuExpandedState.value = false },
        requestKey = "feedback_dropdown_menu",
    ) {
        DropdownMenuItem(
            text = "编辑",
            onClick = {
                state.menuSelectedState.value = "编辑"
                state.menuExpandedState.value = false
            },
            leadingIcon = ImageSource.Resource(R.drawable.demo_media_icon),
        )
        DropdownMenuItem(
            text = "复制",
            onClick = {
                state.menuSelectedState.value = "复制"
                state.menuExpandedState.value = false
            },
        )
        DropdownMenuItem(
            text = "分享",
            onClick = {
                state.menuSelectedState.value = "分享"
                state.menuExpandedState.value = false
            },
            trailingText = "Ctrl+S",
        )
        DropdownMenuItem(
            text = "删除",
            onClick = {},
            enabled = false,
        )
    }

    PlainTooltip(
        text = "这是一个提示信息",
        visible = state.tooltipVisibleState.value,
        anchorId = anchors.tooltipAnchorId,
        onDismissRequest = { state.tooltipVisibleState.value = false },
        requestKey = "feedback_tooltip",
    )

    ModalBottomSheet(
        visible = state.bottomSheetVisibleState.value,
        requestKey = "feedback_bottom_sheet",
        onDismissRequest = {
            state.bottomSheetVisibleState.value = false
            state.lastEventState.value = "BottomSheet 关闭"
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shape(SurfaceDefaults.shape())
                .backgroundColor(ModalBottomSheetDefaults.containerColor())
        ) {
            Column(
                spacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "底部弹窗",
                    style = UiTextStyle(fontSizeSp = 18.sp),
                    modifier = Modifier.testTag(DemoTestTags.FEEDBACK_BOTTOM_SHEET_TITLE),
                )
                Text(
                    text = "ModalBottomSheet 通过 overlay 路径渲染，支持手势下滑关闭。",
                    color = TextDefaults.secondaryColor(),
                )
                Divider()
                Button(
                    text = "选项一：保存草稿",
                    onClick = {
                        state.bottomSheetVisibleState.value = false
                        state.lastEventState.value = "BottomSheet 保存草稿"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    text = "选项二：丢弃更改",
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        state.bottomSheetVisibleState.value = false
                        state.lastEventState.value = "BottomSheet 丢弃更改"
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    text = "关闭",
                    variant = ButtonVariant.Tonal,
                    onClick = {
                        state.bottomSheetVisibleState.value = false
                        state.lastEventState.value = "BottomSheet 关闭"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DemoTestTags.FEEDBACK_BOTTOM_SHEET_CLOSE),
                )
            }
        }
    }
}
