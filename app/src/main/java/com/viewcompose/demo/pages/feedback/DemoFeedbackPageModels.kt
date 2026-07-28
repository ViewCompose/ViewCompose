package com.viewcompose

import com.viewcompose.runtime.MutableState

/**
 * 保存 feedback 页面中 overlay 锚点的节点 key，确保 popup/bottom sheet 示例能绑定稳定目标。
 * Stores overlay anchor node keys for the feedback page so popup and bottom-sheet samples bind to stable targets.
 */
internal data class FeedbackAnchors(
    val popupAnchorId: String,
    val menuAnchorId: String,
    val tooltipAnchorId: String,
)

/**
 * 聚合 feedback 页面运行时计数与最近事件，用于同时驱动 UI 文案和测试断言。
 * Aggregates feedback-page runtime counts and latest events, driving both visible UI copy and test assertions.
 */
internal data class FeedbackPageState(
    val selectedPageState: MutableState<Int>,
    val dialogVisibleState: MutableState<Boolean>,
    val dialogCountState: MutableState<Int>,
    val popupVisibleState: MutableState<Boolean>,
    val popupCountState: MutableState<Int>,
    val snackbarVisibleState: MutableState<Boolean>,
    val snackbarCountState: MutableState<Int>,
    val toastCountState: MutableState<Int>,
    val lastEventState: MutableState<String>,
    val alertDialogVisibleState: MutableState<Boolean>,
    val alertDialogIconVisibleState: MutableState<Boolean>,
    val menuExpandedState: MutableState<Boolean>,
    val menuSelectedState: MutableState<String>,
    val tooltipVisibleState: MutableState<Boolean>,
    val bottomSheetVisibleState: MutableState<Boolean>,
)
