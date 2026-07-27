package com.viewcompose

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.viewcompose.navigation.core.NavRoute
import com.viewcompose.navigation.core.NavStackSelectionMode
import com.viewcompose.navigation.core.NavValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemNavigationDemoDeviceTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun independentStacksAndPreviousStackBackAreVisibleThroughTheDemo() {
        launchDemoActivity(SystemNavigationActivity::class.java).use { scenario ->
            awaitNavigation()

            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.SYSTEM_NAV_PUSH)
            }
            awaitNavigation()

            scenario.onActivity { activity ->
                val controller = activity.controllerForTest()
                controller.selectStack(
                    stackId = SystemNavigationDemoModel.DiscoverStack,
                    selectionMode = NavStackSelectionMode.Preserve,
                )
                controller.navigate(NavRoute(SystemNavigationDemoModel.SearchResultRoute))
                controller.selectStack(
                    stackId = SystemNavigationDemoModel.AccountStack,
                    selectionMode = NavStackSelectionMode.Preserve,
                )
                controller.navigate(NavRoute(SystemNavigationDemoModel.SettingsRoute))
                controller.selectStack(
                    stackId = SystemNavigationDemoModel.HomeStack,
                    selectionMode = NavStackSelectionMode.Preserve,
                )

                val state = activity.navigationSnapshot()
                assertEquals(
                    listOf(
                        SystemNavigationDemoModel.HomeRoute,
                        SystemNavigationDemoModel.HomeDetailRoute,
                    ),
                    state[SystemNavigationDemoModel.HomeStack]!!.entries.map { it.route.name },
                )
                assertEquals(
                    listOf(
                        SystemNavigationDemoModel.DiscoverRoute,
                        SystemNavigationDemoModel.SearchResultRoute,
                    ),
                    state[SystemNavigationDemoModel.DiscoverStack]!!.entries.map { it.route.name },
                )
                assertEquals(
                    listOf(
                        SystemNavigationDemoModel.ProfileRoute,
                        SystemNavigationDemoModel.SettingsRoute,
                    ),
                    state[SystemNavigationDemoModel.AccountStack]!!.entries.map { it.route.name },
                )
            }
            awaitNavigation()

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            awaitNavigation()
            scenario.onActivity { activity ->
                assertEquals(
                    SystemNavigationDemoModel.HomeRoute,
                    activity.navigationSnapshot().activeStack.top.route.name,
                )
                activity.onBackPressedDispatcher.onBackPressed()
            }
            awaitNavigation()
            scenario.onActivity { activity ->
                assertEquals(
                    SystemNavigationDemoModel.AccountStack,
                    activity.navigationSnapshot().activeStackId,
                )
                assertEquals(
                    SystemNavigationDemoModel.SettingsRoute,
                    activity.navigationSnapshot().activeStack.top.route.name,
                )
            }

            scrollTextIntoView("准备三窗格样例（当前 Tab）")
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.SYSTEM_NAV_SEED_ADAPTIVE)
            }
            awaitNavigation()
            scenario.onActivity { activity ->
                assertEquals(
                    listOf(
                        SystemNavigationDemoModel.ProfileRoute,
                        SystemNavigationDemoModel.SecurityRoute,
                        SystemNavigationDemoModel.SettingsRoute,
                    ),
                    activity.navigationSnapshot().activeStack.entries.map { it.route.name },
                )
            }
        }
    }

    @Test
    fun strictDeepLinksCoverExternalIntentAcceptedRejectedAndNoMatchPaths() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(SystemNavigationDemoModel.SecurityDeepLink),
            context,
            SystemNavigationActivity::class.java,
        )
        launchDemoActivity<SystemNavigationActivity>(intent).use { scenario ->
            awaitNavigation()

            scenario.onActivity { activity ->
                val state = activity.navigationSnapshot()
                assertEquals(SystemNavigationDemoModel.AccountStack, state.activeStackId)
                assertEquals(SystemNavigationDemoModel.SecurityRoute, state.activeStack.top.route.name)
                assertEquals(
                    NavValue.LongValue(42L),
                    state.activeStack.top.route[SystemNavigationDemoModel.UserIdArgument],
                )
                assertTrue(activity.externalDeepLinkOutcomeForTest().contains("外部 Deep Link"))

                activity.controllerForTest().selectStack(
                    stackId = SystemNavigationDemoModel.DiscoverStack,
                    selectionMode = NavStackSelectionMode.Preserve,
                )
            }
            awaitNavigation()

            scrollTextIntoView("非法 Int 参数（应拒绝）")
            scenario.onActivity { activity ->
                val before = activity.navigationSnapshot()
                    .activeStack
                    .entries
                    .map { it.id }
                activity.clickByTestTag(DemoTestTags.SYSTEM_NAV_DEEP_LINK_INVALID)
                assertEquals(
                    before,
                    activity.navigationSnapshot().activeStack.entries.map { it.id },
                )
            }
            awaitNavigation()
            scenario.onActivity { activity ->
                val before = activity.navigationSnapshot()
                    .activeStack
                    .entries
                    .map { it.id }
                activity.clickByTestTag(DemoTestTags.SYSTEM_NAV_DEEP_LINK_NO_MATCH)
                assertEquals(
                    before,
                    activity.navigationSnapshot().activeStack.entries.map { it.id },
                )
            }
            awaitNavigation()
            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.SYSTEM_NAV_DEEP_LINK_VALID)
            }
            awaitNavigation()
            scenario.onActivity { activity ->
                val state = activity.navigationSnapshot()
                assertEquals(SystemNavigationDemoModel.DiscoverStack, state.activeStackId)
                assertEquals(
                    SystemNavigationDemoModel.SearchResultRoute,
                    state.activeStack.top.route.name,
                )
                assertEquals(
                    NavValue.IntValue(2),
                    state.activeStack.top.route[SystemNavigationDemoModel.PageArgument],
                )
            }
        }
    }

    @Test
    fun entryAndGraphOwnedStateSurviveActivityRecreation() {
        launchDemoActivity(SystemNavigationActivity::class.java).use { scenario ->
            awaitNavigation()

            scenario.onActivity { activity ->
                activity.clickByTestTag(DemoTestTags.SYSTEM_NAV_SAVEABLE_INCREMENT)
                activity.clickByTestTag(DemoTestTags.SYSTEM_NAV_HANDLE_INCREMENT)
                activity.clickByTestTag(DemoTestTags.SYSTEM_NAV_VIEW_MODEL_INCREMENT)
                activity.clickByTestTag(DemoTestTags.SYSTEM_NAV_GRAPH_INCREMENT)
            }
            awaitNavigation()

            var entryId = ""
            var graphIds = emptyList<String>()
            var counterStatus = ""
            var graphStatus = ""
            scenario.onActivity { activity ->
                val entry = activity.navigationSnapshot().activeStack.top
                entryId = entry.id.value
                graphIds = entry.graphEntries.map { it.id.value }
                counterStatus = activity
                    .requireTextViewByTestTagVisible(DemoTestTags.SYSTEM_NAV_COUNTER_STATUS)
                    .text
                    .toString()
                graphStatus = activity
                    .requireTextViewByTestTagVisible(DemoTestTags.SYSTEM_NAV_GRAPH_STATUS)
                    .text
                    .toString()
                assertTrue(counterStatus.contains("rememberSaveable=1"))
                assertTrue(counterStatus.contains("SavedStateHandle=1"))
                assertTrue(counterStatus.endsWith("=1"))
                assertTrue(graphStatus.contains("counter=1"))
            }

            scenario.recreate()
            awaitNavigation()

            scenario.onActivity { activity ->
                val entry = activity.navigationSnapshot().activeStack.top
                assertEquals(entryId, entry.id.value)
                assertEquals(graphIds, entry.graphEntries.map { it.id.value })
                val restoredCounterStatus = activity
                    .requireTextViewByTestTagVisible(DemoTestTags.SYSTEM_NAV_COUNTER_STATUS)
                    .text
                    .toString()
                assertTrue(restoredCounterStatus.contains("rememberSaveable=1"))
                assertTrue(restoredCounterStatus.contains("SavedStateHandle=1"))
                assertTrue(restoredCounterStatus.endsWith("=1"))
                assertTrue(restoredCounterStatus != counterStatus)
                assertEquals(
                    graphStatus,
                    activity
                        .requireTextViewByTestTagVisible(DemoTestTags.SYSTEM_NAV_GRAPH_STATUS)
                        .text
                        .toString(),
                )
            }
        }
    }

    private fun awaitNavigation() {
        SystemClock.sleep(450L)
        waitForUiIdle()
    }

    private fun scrollTextIntoView(text: String) {
        assertTrue(
            "Expected to scroll to '$text'",
            UiScrollable(UiSelector().scrollable(true))
                .setAsVerticalList()
                .scrollTextIntoView(text),
        )
        waitForUiIdle()
    }
}
