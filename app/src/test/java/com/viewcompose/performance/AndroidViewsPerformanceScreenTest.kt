package com.viewcompose.performance

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.registry.DemoScenarioRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AndroidViewsPerformanceScreenTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `list control exposes stable ids and commits mutation before state`() {
        val scenario = DemoScenarioRegistry.require("performance.list")
        val fixtures = PerformanceFixtures(context)
        val root = createAndroidViewsListPerformanceScreen(
            context = context,
            scenario = scenario,
            fixtures = fixtures,
        )
        val ready = root.findViewById<TextView>(
            scenario.automation.require(DemoAutomationRole.Ready).androidViewId,
        )
        val state = root.findViewById<TextView>(
            scenario.automation.require(DemoAutomationRole.State).androidViewId,
        )
        val action = root.findViewById<TextView>(
            scenario.automation.require(DemoAutomationRole.PrimaryAction).androidViewId,
        )
        val reset = root.findViewById<TextView>(
            scenario.automation.require(DemoAutomationRole.Reset).androidViewId,
        )
        val list = root.findViewById<RecyclerView>(
            scenario.automation.require(DemoAutomationRole.Target).androidViewId,
        )
        val adapter = list.adapter as AndroidViewsPerformanceListAdapter
        val initialState = state.text.toString()

        assertTrue(ready.text.contains("Android Views"))
        assertTrue(adapter.hasStableIds())
        assertNull(list.itemAnimator)
        assertEquals(PERFORMANCE_LIST_ITEM_COUNT, adapter.itemCount)
        assertEquals(0L, adapter.getItemId(0))

        action.performClick()

        assertNotEquals(initialState, state.text.toString())
        assertEquals(PERFORMANCE_LIST_ROTATION.toLong(), adapter.getItemId(0))

        reset.performClick()

        assertEquals(initialState, state.text.toString())
        assertEquals(0L, adapter.getItemId(0))
    }

    @Test
    fun `complex control separates property binding from structural binding`() {
        val scenario = DemoScenarioRegistry.require("performance.complex-layout")
        val fixtures = PerformanceFixtures(context)
        val root = createAndroidViewsComplexLayoutPerformanceScreen(
            context = context,
            scenario = scenario,
            fixtures = fixtures,
        )
        val state = root.findViewById<TextView>(
            scenario.automation.require(DemoAutomationRole.State).androidViewId,
        )
        val action = root.findViewById<TextView>(
            scenario.automation.require(DemoAutomationRole.PrimaryAction).androidViewId,
        )
        val structureAction = root.findViewById<TextView>(
            scenario.automation.require(DemoAutomationRole.SecondaryAction).androidViewId,
        )
        val reset = root.findViewById<TextView>(
            scenario.automation.require(DemoAutomationRole.Reset).androidViewId,
        )
        val scroll = root.findViewById<ScrollView>(
            scenario.automation.require(DemoAutomationRole.Target).androidViewId,
        )
        val content = scroll.getChildAt(0) as LinearLayout
        val cards = (0 until content.childCount).map { index ->
            content.getChildAt(index) as AndroidViewsDashboardCardView
        }
        val initialState = state.text.toString()

        assertEquals(PERFORMANCE_DASHBOARD_CARD_COUNT, cards.size)
        assertEquals(5, cards.count { card -> card.childCount == 4 })

        action.performClick()

        assertNotEquals(initialState, state.text.toString())
        assertEquals(5, cards.count { card -> card.childCount == 4 })

        val propertyState = state.text.toString()
        structureAction.performClick()

        assertNotEquals(propertyState, state.text.toString())
        assertEquals(4, cards.count { card -> card.childCount == 4 })

        reset.performClick()

        assertEquals(initialState, state.text.toString())
        assertEquals(5, cards.count { card -> card.childCount == 4 })
    }
}
