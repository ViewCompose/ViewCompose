package com.viewcompose

import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentShowcaseUiTest {
    @Test
    fun strictComponentFixturesExposeTheirRealVisualTargets() {
        listOf(
            "component.button" to R.id.demo_component_button_target,
            "component.icon-button" to R.id.demo_component_icon_button_target,
            "component.segmented-control" to R.id.demo_component_segmented_control_target,
            "component.divider" to R.id.demo_component_divider_target,
            "component.progress" to R.id.demo_component_progress_target,
        ).forEach { (scenarioId, targetId) ->
            launchDemoScenarioActivity(
                activityClass = ComponentShowcaseActivity::class.java,
                scenarioId = scenarioId,
            ).use { scenario ->
                waitForUiIdle()
                scenario.onActivity { activity ->
                    assertViewFullyVisible(
                        activity.requireScenarioViewByIdVisible<View>(targetId),
                    )
                }
            }
        }
    }

    @Test
    fun segmentedControlSelectionAndInputCheckboxUseOwningScenarioTargets() {
        launchDemoScenarioActivity(
            activityClass = ComponentShowcaseActivity::class.java,
            scenarioId = "component.segmented-control",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val state = activity.requireScenarioViewById<TextView>(
                    R.id.demo_component_segmented_control_state,
                )
                assertTrue(state.text.toString().contains("0"))
                val control = activity.requireScenarioViewById<ViewGroup>(
                    R.id.demo_component_segmented_control_primary_action,
                )
                assertTrue(control.getChildAt(1).performClick())
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val state = activity.requireScenarioViewById<TextView>(
                    R.id.demo_component_segmented_control_state,
                )
                assertTrue(state.text.toString().contains("1"))
            }
        }

        launchDemoScenarioActivity(
            activityClass = InputActivity::class.java,
            scenarioId = "input.selection",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                val checkbox = activity.requireScenarioViewById<CheckBox>(
                    R.id.demo_input_selection_target,
                )
                assertTrue(checkbox.isClickable)
                assertTrue(checkbox.isChecked)
                checkbox.performClick()
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertFalse(
                    activity.requireScenarioViewById<CheckBox>(
                        R.id.demo_input_selection_target,
                    ).isChecked,
                )
            }
        }
    }
}
