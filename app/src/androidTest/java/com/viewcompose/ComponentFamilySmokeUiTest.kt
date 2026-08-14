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
        launchDemoActivity<ActionsActivity>(
            Intent(
                ApplicationProvider.getApplicationContext(),
                ActionsActivity::class.java,
            ).putExtra(EXTRA_ACTIONS_PAGE_INDEX, 2),
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.ACTIONS_CHIP_FILTER))
            }
        }

        launchDemoActivity<InputActivity>(
            Intent(
                ApplicationProvider.getApplicationContext(),
                InputActivity::class.java,
            ).putExtra(EXTRA_INPUT_PAGE_INDEX, 3),
        ).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertViewFullyVisible(activity.requireViewByTestTagVisible(DemoTestTags.INPUT_SEARCH_PRIMARY))
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

        launchDemoActivity<LayoutsActivity>(
            Intent(
                ApplicationProvider.getApplicationContext(),
                LayoutsActivity::class.java,
            ).putExtra(EXTRA_LAYOUTS_PAGE_INDEX, 3),
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
