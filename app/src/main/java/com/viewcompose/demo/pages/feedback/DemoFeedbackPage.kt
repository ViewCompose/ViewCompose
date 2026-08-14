package com.viewcompose

import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember

@ViewComposePreview(name = "Feedback · Transient", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewFeedbackTransient() {
    FeedbackPage(initialPageIndex = 0)
}

@ViewComposePreview(name = "Feedback · Dialog", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewFeedbackDialog() {
    FeedbackPage(initialPageIndex = 1)
}

@ViewComposePreview(name = "Feedback · Menu", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewFeedbackMenu() {
    FeedbackPage(initialPageIndex = 2)
}

internal fun UiTreeBuilder.FeedbackPage(
    initialPageIndex: Int = 0,
    scenario: DemoScenarioSpec? = null,
) {
    val anchors = FeedbackAnchors(
        popupAnchorId = "feedback_popup_anchor",
        menuAnchorId = "feedback_menu_anchor",
        tooltipAnchorId = "feedback_tooltip_anchor",
    )
    val state = FeedbackPageState(
        selectedPageState = remember { mutableStateOf(initialPageIndex.coerceIn(0, 2)) },
        dialogVisibleState = remember { mutableStateOf(false) },
        dialogCountState = remember { mutableStateOf(0) },
        popupVisibleState = remember { mutableStateOf(false) },
        popupCountState = remember { mutableStateOf(0) },
        snackbarVisibleState = remember { mutableStateOf(false) },
        snackbarCountState = remember { mutableStateOf(0) },
        toastCountState = remember { mutableStateOf(0) },
        lastEventState = remember { mutableStateOf("空闲") },
        alertDialogVisibleState = remember { mutableStateOf(false) },
        alertDialogIconVisibleState = remember { mutableStateOf(false) },
        menuExpandedState = remember { mutableStateOf(false) },
        menuSelectedState = remember { mutableStateOf("未选择") },
        tooltipVisibleState = remember { mutableStateOf(false) },
        bottomSheetVisibleState = remember { mutableStateOf(false) },
    )

    DeclareFeedbackOverlays(
        anchors = anchors,
        state = state,
    )

    LazyColumn(
        items = feedbackPageItems(state.selectedPageState.value),
        key = { it },
        modifier = Modifier.fillMaxSize(),
    ) { section ->
        RenderFeedbackSection(
            section = section,
            anchors = anchors,
            state = state,
            scenario = scenario,
        )
    }
}
