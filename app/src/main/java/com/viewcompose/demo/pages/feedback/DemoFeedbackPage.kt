package com.viewcompose

import com.viewcompose.demo.contract.DemoScenarioId
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.demo.registry.DemoScenarioIds
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.ui.foundation.UiTreeBuilder

@ViewComposePreview(name = "Feedback · Transient", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewFeedbackTransient() {
    FeedbackPage(FeedbackFixture.Transient)
}

@ViewComposePreview(name = "Feedback · Dialog", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewFeedbackDialog() {
    FeedbackPage(FeedbackFixture.Dialog)
}

@ViewComposePreview(name = "Feedback · Menu", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewFeedbackMenu() {
    FeedbackPage(FeedbackFixture.Menu)
}

internal enum class FeedbackFixture(
    val scenarioId: DemoScenarioId,
) {
    Transient(DemoScenarioIds.OverlayTransient),
    Dialog(DemoScenarioIds.OverlayDialog),
    Menu(DemoScenarioIds.OverlayMenu),
    ;

    companion object {
        fun from(scenarioId: DemoScenarioId): FeedbackFixture =
            entries.singleOrNull { fixture -> fixture.scenarioId == scenarioId }
                ?: error("Unsupported feedback scenario: $scenarioId")
    }
}

internal fun UiTreeBuilder.FeedbackPage(
    fixture: FeedbackFixture,
    scenario: DemoScenarioSpec? = null,
) {
    when (fixture) {
        FeedbackFixture.Transient -> TransientFeedbackFixture(scenario)
        FeedbackFixture.Dialog -> DialogFeedbackFixture(scenario)
        FeedbackFixture.Menu -> MenuFeedbackFixture(scenario)
    }
}
