package com.viewcompose

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 核心组件家族的设备级 smoke test。
 * Device-level smoke test for core component families.
 *
 * 每段只断言稳定锚点可见，避免把大量视觉细节复制到 smoke 层。
 * Each section only asserts a stable anchor is visible, keeping visual details out of smoke coverage.
 */
@RunWith(AndroidJUnit4::class)
class ComponentFamilySmokeUiTest {
    @Test
    fun keyComponentFamilies_haveVisibleSmokeAnchors() {
        launchDemoScenarioActivity(
            activityClass = ActionsActivity::class.java,
            scenarioId = "component.chip",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(
                    activity.requireScenarioViewByIdVisible<android.view.View>(
                        R.id.demo_component_chip_primary_action,
                    ),
                )
            }
        }

        launchDemoScenarioActivity(
            activityClass = InputActivity::class.java,
            scenarioId = "input.search",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(
                    activity.requireScenarioViewById<android.view.View>(R.id.demo_input_search_target),
                )
            }
        }

        launchDemoActivity<NavigationActivity>(
            Intent(
                ApplicationProvider.getApplicationContext(),
                NavigationActivity::class.java,
            ).putExtra(EXTRA_NAVIGATION_PAGE_INDEX, 1),
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.NAVIGATION_BAR_PRIMARY))
            }
        }

        launchDemoActivity<NavigationActivity>(
            Intent(
                ApplicationProvider.getApplicationContext(),
                NavigationActivity::class.java,
            ).putExtra(EXTRA_NAVIGATION_PAGE_INDEX, 2),
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.NAVIGATION_SCAFFOLD))
            }
        }

        launchDemoScenarioActivity(
            activityClass = CollectionsActivity::class.java,
            scenarioId = "collection.lazy-row",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.COLLECTIONS_LAZY_ROW_PRIMARY))
            }
        }

        launchDemoScenarioActivity(
            activityClass = LayoutsActivity::class.java,
            scenarioId = "layout.flow",
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.LAYOUTS_FLOW_ROW))
            }
        }

        launchDemoActivity<PreviewActivity>(
            Intent(
                ApplicationProvider.getApplicationContext(),
                PreviewActivity::class.java,
            ).putExtra(EXTRA_PREVIEW_PAGE_INDEX, 0),
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.PREVIEW_HOST_SAMPLE))
            }
        }

    }
}
