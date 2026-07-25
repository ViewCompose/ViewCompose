package com.viewcompose

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaveableStateRestorationUiTest {
    @Before
    fun wakeDevice() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.waitForIdle()
    }

    @Test
    fun rememberSaveable_restoresMutableStateAfterActivityRecreation() {
        ActivityScenario.launch(SaveableStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("0", activity.countText().text.toString())
                activity.clickByTestTag(SaveableStateTestActivity.INCREMENT_TAG)
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertEquals("1", activity.countText().text.toString())
            }

            scenario.recreate()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals("1", activity.countText().text.toString())
            }
        }
    }

    @Test
    fun rememberLazyListState_restoresVisibleItemAfterActivityRecreation() {
        ActivityScenario.launch(SaveableLazyListStateTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.listState.scrollToPosition(
                    SaveableLazyListStateTestActivity.RESTORE_TARGET,
                )
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                assertEquals(
                    SaveableLazyListStateTestActivity.RESTORE_TARGET,
                    activity.listState.firstVisibleItemIndex,
                )
                activity.taggedView(
                    SaveableLazyListStateTestActivity.itemTag(
                        SaveableLazyListStateTestActivity.RESTORE_TARGET,
                    ),
                )
            }

            scenario.recreate()
            waitForUiIdle()

            scenario.onActivity { activity ->
                assertEquals(
                    SaveableLazyListStateTestActivity.RESTORE_TARGET,
                    activity.listState.firstVisibleItemIndex,
                )
                activity.taggedView(
                    SaveableLazyListStateTestActivity.itemTag(
                        SaveableLazyListStateTestActivity.RESTORE_TARGET,
                    ),
                )
            }
        }
    }

    private fun SaveableStateTestActivity.countText(): TextView {
        return taggedView(SaveableStateTestActivity.COUNT_TAG) as TextView
    }

    private fun androidx.activity.ComponentActivity.taggedView(tag: String): View {
        val root = findViewById<ViewGroup>(android.R.id.content)
        return requireNotNull(findViewByTestTag(root, tag)) {
            "No view found for test tag '$tag'."
        }
    }
}
