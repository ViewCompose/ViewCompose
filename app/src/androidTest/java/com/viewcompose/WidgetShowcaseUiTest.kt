package com.viewcompose

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetShowcaseUiTest {
    @Test
    fun clickingListItemNavigatesToWidgetDetail() {
        launchDemoActivity(WidgetShowcaseActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.clickTextView("Text")
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.requireTextView("← 返回控件列表")
            }
        }
    }

    @Test
    fun checkboxDetail_checkboxCanToggleState() {
        launchDemoActivity(WidgetShowcaseActivity::class.java).use { scenario ->
            waitForUiIdle()
            scenario.onActivity { activity ->
                activity.clickTextViewVisible("Checkbox")
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val checkbox = activity.requireTextView("选中状态")
                assertTrue(
                    "Expected Checkbox view for text=选中状态, but was ${checkbox.javaClass.simpleName}",
                    checkbox is android.widget.CheckBox,
                )
                checkbox as android.widget.CheckBox
                assertTrue("Expected Checkbox to stay clickable for touch interaction", checkbox.isClickable)
                assertTrue("Expected initial checked=true for 选中状态", checkbox.isChecked)
                checkbox.performClick()
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val checkboxChecked = activity.requireTextView("选中状态") as android.widget.CheckBox
                val checkboxUnchecked = activity.requireTextView("未选中状态") as android.widget.CheckBox
                assertFalse("Expected 选中状态 to toggle to unchecked after click", checkboxChecked.isChecked)
                assertFalse("Expected initial 未选中状态=false", checkboxUnchecked.isChecked)
                checkboxUnchecked.performClick()
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val checkboxChecked = activity.requireTextView("选中状态") as android.widget.CheckBox
                val checkboxUnchecked = activity.requireTextView("未选中状态") as android.widget.CheckBox
                assertFalse("Expected 选中状态 to remain unchecked", checkboxChecked.isChecked)
                assertTrue("Expected 未选中状态 to toggle to checked after click", checkboxUnchecked.isChecked)
                checkboxChecked.performClick()
            }
            waitForUiIdle()
            scenario.onActivity { activity ->
                val checkbox = activity.requireTextView("选中状态") as android.widget.CheckBox
                assertTrue("Expected 选中状态 to toggle back to checked", checkbox.isChecked)
            }
        }
    }
}
