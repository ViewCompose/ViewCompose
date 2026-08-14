package com.viewcompose

import com.viewcompose.runtime.MutableState

internal data class FeedbackAnchors(
    val popup: String = "feedback_popup_anchor",
    val menu: String = "feedback_menu_anchor",
    val tooltip: String = "feedback_tooltip_anchor",
)

internal data class TransientFeedbackState(
    val dialogVisible: MutableState<Boolean>,
    val dialogCount: MutableState<Int>,
    val popupVisible: MutableState<Boolean>,
    val popupCount: MutableState<Int>,
    val snackbarVisible: MutableState<Boolean>,
    val snackbarCount: MutableState<Int>,
    val toastCount: MutableState<Int>,
)

internal enum class DialogFeedbackOutcome {
    Idle,
    AlertConfirmed,
    AlertDismissed,
    IconConfirmed,
    IconDismissed,
    SheetSaved,
    SheetDiscarded,
}

internal data class DialogFeedbackState(
    val alertVisible: MutableState<Boolean>,
    val iconAlertVisible: MutableState<Boolean>,
    val bottomSheetVisible: MutableState<Boolean>,
    val outcome: MutableState<DialogFeedbackOutcome>,
)

internal enum class MenuFeedbackSelection {
    None,
    Edit,
    Copy,
    Share,
}

internal data class MenuFeedbackState(
    val expanded: MutableState<Boolean>,
    val selection: MutableState<MenuFeedbackSelection>,
    val tooltipVisible: MutableState<Boolean>,
)
