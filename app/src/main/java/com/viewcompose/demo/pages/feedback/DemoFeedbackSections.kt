package com.viewcompose

import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.overlayAnchor
import com.viewcompose.ui.modifier.testTag
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.ButtonVariant
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Divider
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

internal fun feedbackPageItems(
    selectedIndex: Int,
): List<String> {
    return when (selectedIndex) {
        0 -> listOf("benchmark", "page", "page_filter", "transient", "verify")
        1 -> listOf("page", "page_filter", "alert_sheet", "verify")
        else -> listOf("page", "page_filter", "menu_tooltip", "verify")
    }
}

internal fun UiTreeBuilder.RenderFeedbackSection(
    section: String,
    anchors: FeedbackAnchors,
    state: FeedbackPageState,
) {
    when (section) {
        "page" -> ChapterPageOverviewSection(
            title = "反馈组件",
            goal = "验证瞬态 overlay（Snackbar、Toast、Dialog、Popup）和结构化弹窗（AlertDialog、DropdownMenu、Tooltip、ModalBottomSheet）的生命周期和交互。",
            modules = listOf("overlay host", "render session lifecycle", "transient presenters", "AlertDialog", "DropdownMenu", "PlainTooltip", "ModalBottomSheet"),
        )

        "page_filter" -> ChapterPageFilterSection(
            pages = listOf("瞬态反馈", "弹窗", "菜单"),
            selectedIndex = state.selectedPageState.value,
            onSelectionChange = { state.selectedPageState.value = it },
        )

        "benchmark" -> ScenarioSection(
            kind = ScenarioKind.Benchmark,
            title = "反馈组件 Benchmark 锚点",
            subtitle = "AlertDialog show/dismiss 和 DropdownMenu 展开/收起的稳定路径。",
        ) {
            BenchmarkRouteCallout(
                route = "Catalog -> Feedback -> 瞬态反馈页 -> Benchmark 锚点",
                stableTargets = listOf(
                    "Show Snackbar / Show Toast / Show Dialog / Show Popup",
                    "AlertDialog show/dismiss",
                    "DropdownMenu expand/collapse",
                ),
            )
        }

        "transient" -> ScenarioSection(
            kind = ScenarioKind.Core,
            title = "瞬态反馈",
            subtitle = "Snackbar、Toast、Dialog、Popup 的触发、展示和关闭。",
        ) {
            Text(
                text = "最后事件: ${state.lastEventState.value}",
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier
                    .margin(bottom = 8.dp)
                    .testTag(DemoTestTags.FEEDBACK_LAST_EVENT),
            )
            Row(
                spacing = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(bottom = 8.dp),
            ) {
                Button(
                    text = "显示 Snackbar",
                    onClick = {
                        state.snackbarCountState.value += 1
                        state.snackbarVisibleState.value = true
                        state.lastEventState.value = "Snackbar 请求 ${state.snackbarCountState.value}"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DemoTestTags.FEEDBACK_SHOW_SNACKBAR),
                )
                Button(
                    text = "隐藏 Snackbar",
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        state.snackbarVisibleState.value = false
                        state.lastEventState.value = "Snackbar 隐藏"
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    text = "显示 Toast",
                    variant = ButtonVariant.Tonal,
                    onClick = {
                        state.toastCountState.value += 1
                        state.lastEventState.value = "Toast 请求 ${state.toastCountState.value}"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DemoTestTags.FEEDBACK_SHOW_TOAST),
                )
                Button(
                    text = "显示 Dialog",
                    onClick = {
                        state.dialogCountState.value += 1
                        state.dialogVisibleState.value = true
                        state.lastEventState.value = "Dialog 请求 ${state.dialogCountState.value}"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DemoTestTags.FEEDBACK_SHOW_DIALOG),
                )
            }
            Row(
                spacing = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(top = 8.dp),
            ) {
                Button(
                    text = "显示 Popup",
                    variant = ButtonVariant.Tonal,
                    onClick = {
                        state.popupCountState.value += 1
                        state.popupVisibleState.value = true
                        state.lastEventState.value = "Popup 请求 ${state.popupCountState.value}"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .overlayAnchor(anchors.popupAnchorId)
                        .testTag(DemoTestTags.FEEDBACK_SHOW_POPUP),
                )
                Button(
                    text = "重置",
                    variant = ButtonVariant.Outlined,
                    onClick = {
                        state.dialogVisibleState.value = false
                        state.dialogCountState.value = 0
                        state.popupVisibleState.value = false
                        state.popupCountState.value = 0
                        state.snackbarVisibleState.value = false
                        state.snackbarCountState.value = 0
                        state.toastCountState.value = 0
                        state.lastEventState.value = "空闲"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DemoTestTags.FEEDBACK_RESET),
                )
            }
            Column(
                spacing = 6.dp,
                modifier = Modifier.margin(top = 12.dp),
            ) {
                Text(
                    text = "Dialog 次数: ${state.dialogCountState.value}",
                    modifier = Modifier.testTag(DemoTestTags.FEEDBACK_DIALOG_COUNT),
                )
                Text(
                    text = "Popup 次数: ${state.popupCountState.value}",
                    modifier = Modifier.testTag(DemoTestTags.FEEDBACK_POPUP_COUNT),
                )
                Text(text = "Snackbar 次数: ${state.snackbarCountState.value}")
                Text(
                    text = "Toast 次数: ${state.toastCountState.value}",
                    modifier = Modifier.testTag(DemoTestTags.FEEDBACK_TOAST_COUNT),
                )
            }
        }

        "alert_sheet" -> ScenarioSection(
            kind = ScenarioKind.Core,
            title = "AlertDialog + ModalBottomSheet",
            subtitle = "AlertDialog 提供标准化确认弹窗。ModalBottomSheet 提供底部弹出面板。",
        ) {
            Text(
                text = "最后事件: ${state.lastEventState.value}",
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier
                    .margin(bottom = 8.dp)
                    .testTag(DemoTestTags.FEEDBACK_LAST_EVENT),
            )
            Button(
                text = "显示 AlertDialog（标准）",
                onClick = { state.alertDialogVisibleState.value = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(bottom = 8.dp),
            )
            Button(
                text = "显示 AlertDialog（带图标）",
                variant = ButtonVariant.Tonal,
                onClick = { state.alertDialogIconVisibleState.value = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .margin(bottom = 12.dp),
            )
            Divider(modifier = Modifier.margin(bottom = 12.dp))
            Button(
                text = "显示 ModalBottomSheet",
                onClick = { state.bottomSheetVisibleState.value = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DemoTestTags.FEEDBACK_SHOW_BOTTOM_SHEET),
            )
        }

        "menu_tooltip" -> ScenarioSection(
            kind = ScenarioKind.Core,
            title = "DropdownMenu + PlainTooltip",
            subtitle = "DropdownMenu 提供锚定下拉菜单。PlainTooltip 提供简单的提示气泡。",
        ) {
            Text(
                text = "菜单选中项: ${state.menuSelectedState.value}",
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier.margin(bottom = 8.dp),
            )
            Button(
                text = "打开菜单",
                onClick = { state.menuExpandedState.value = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .overlayAnchor(anchors.menuAnchorId),
            )
            Text(
                text = "菜单包含 4 个选项：编辑（带图标）、复制、分享（带快捷键）、删除（禁用）",
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier.margin(top = 8.dp, bottom = 16.dp),
            )
            Divider(modifier = Modifier.margin(bottom = 12.dp))
            Button(
                text = if (state.tooltipVisibleState.value) "隐藏 Tooltip" else "显示 Tooltip",
                variant = ButtonVariant.Tonal,
                onClick = { state.tooltipVisibleState.value = !state.tooltipVisibleState.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .overlayAnchor(anchors.tooltipAnchorId),
            )
            Text(
                text = "PlainTooltip 锚定在上方按钮，显示纯文字提示。",
                style = UiTextStyle(fontSizeSp = 13.sp),
                color = TextDefaults.secondaryColor(),
                modifier = Modifier.margin(top = 8.dp),
            )
        }

        else -> VerificationNotesSection(
            what = "反馈组件应验证 overlay 驱动的瞬态反馈和结构化弹窗的生命周期和交互。",
            howToVerify = listOf(
                "点击显示 Snackbar，确认底部出现带操作按钮的通知。",
                "连续点击显示 Toast，确认短暂提示重复触发。",
                "点击显示 Dialog，确认弹窗出现并可通过按钮关闭。",
                "点击显示 AlertDialog，确认标准确认弹窗出现，点击确定/取消关闭。",
                "点击显示 AlertDialog 带图标，确认图标显示在标题上方。",
                "点击打开菜单，确认 4 个菜单项正常显示，禁用项不可点击。",
                "点击显示 Tooltip，确认提示气泡出现在锚点附近。",
                "点击显示 ModalBottomSheet，确认底部面板弹出并可选择选项或关闭。",
            ),
            expected = listOf(
                "所有 overlay 通过 host 渲染，关闭后不残留。",
                "AlertDialog 和 ModalBottomSheet 的交互事件正确更新 lastEvent。",
                "DropdownMenu 的禁用项视觉降低透明度，不响应点击。",
            ),
        )
    }
}
