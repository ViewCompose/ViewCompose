package com.viewcompose.performance

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec

/** Exposes the same fully qualified Android resource selector as the ViewCompose fixture. */
@Composable
internal fun Modifier.performanceScenarioTarget(
    scenario: DemoScenarioSpec,
    role: DemoAutomationRole,
    enableResourceIds: Boolean = false,
): Modifier {
    val target = scenario.automation.require(role)
    val qualifiedResourceName = LocalContext.current.resources.getResourceName(target.androidViewId)
    val tagged = testTag(qualifiedResourceName)
    return if (enableResourceIds) {
        tagged.semantics { testTagsAsResourceId = true }
    } else {
        tagged
    }
}
