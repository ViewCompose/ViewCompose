package com.viewcompose.renderer.view.tree

import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Switch
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.viewcompose.renderer.R
import com.viewcompose.renderer.view.container.DeclarativePullToRefreshLayout
import com.viewcompose.renderer.view.tree.patch.ContainerNodePatchApplier
import com.viewcompose.renderer.view.tree.patch.InputNodePatchApplier
import com.viewcompose.ui.node.spec.PullToRefreshNodeProps
import com.viewcompose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeListenerBindingTest {
    @Test
    fun `accepted native switch state is not written back during its running transition`() {
        val view = CountingSwitch().apply {
            isChecked = true
            checkedAssignments = 0
        }
        val previous = toggleNodeProps(checked = false)
        val next = toggleNodeProps(checked = true)

        InputNodePatchApplier.applyTogglePatch(
            view = view,
            patch = ToggleNodePatch(previous = previous, next = next),
        )

        assertEquals(0, view.checkedAssignments)
        assertEquals(true, view.isChecked)
    }

    @Test
    fun `toggle keeps one native listener and updates its callback`() {
        val view = CheckBox(RuntimeEnvironment.getApplication())
        var firstCalls = 0
        var secondValue: Boolean? = null

        InputViewBinder.bindCheckbox(
            view,
            toggleSpec(checked = false) { firstCalls += 1 },
        )
        val firstBinding = view.getTag(R.id.viewcompose_toggle_listener)

        InputViewBinder.bindCheckbox(
            view,
            toggleSpec(checked = true) { secondValue = it },
        )
        val secondBinding = view.getTag(R.id.viewcompose_toggle_listener)
        (secondBinding as android.widget.CompoundButton.OnCheckedChangeListener)
            .onCheckedChanged(view, false)

        assertSame(firstBinding, secondBinding)
        assertEquals(0, firstCalls)
        assertEquals(false, secondValue)
    }

    @Test
    fun `slider keeps one native listener and updates its value mapping`() {
        val view = SeekBar(RuntimeEnvironment.getApplication())
        var firstCalls = 0
        var secondValue: Int? = null

        InputViewBinder.bindSlider(
            view,
            sliderSpec(min = 0, value = 4) { firstCalls += 1 },
        )
        val firstBinding = view.getTag(R.id.viewcompose_seek_listener)

        InputViewBinder.bindSlider(
            view,
            sliderSpec(min = 10, value = 14) { secondValue = it },
        )
        val secondBinding = view.getTag(R.id.viewcompose_seek_listener)
        (secondBinding as SeekBar.OnSeekBarChangeListener)
            .onProgressChanged(view, 6, true)

        assertSame(firstBinding, secondBinding)
        assertEquals(0, firstCalls)
        assertEquals(16, secondValue)
    }

    @Test
    fun `stepped slider reports touch interaction in start change finish order`() {
        val view = ViewComposeSeekBar(RuntimeEnvironment.getApplication())
        val events = mutableListOf<String>()
        InputViewBinder.bindSlider(
            view,
            sliderSpec(
                min = 10,
                value = 10,
                step = 5,
                onValueChangeStarted = { events += "start" },
                onValueChangeFinished = { events += "finish" },
            ) { value -> events += "change:$value" },
        )
        val listener = view.getTag(R.id.viewcompose_seek_listener) as SeekBar.OnSeekBarChangeListener

        listener.onStartTrackingTouch(view)
        listener.onProgressChanged(view, 2, true)
        listener.onStopTrackingTouch(view)

        assertEquals(listOf("start", "change:20", "finish"), events)
        assertEquals(4, view.max)
    }

    @Test
    fun `slider reports a user returning to the controlled value`() {
        val view = ViewComposeSeekBar(RuntimeEnvironment.getApplication())
        val values = mutableListOf<Int>()
        InputViewBinder.bindSlider(
            view,
            sliderSpec(min = 10, value = 10, step = 5, onValueChange = values::add),
        )
        val listener = view.getTag(R.id.viewcompose_seek_listener) as SeekBar.OnSeekBarChangeListener

        listener.onProgressChanged(view, 2, true)
        listener.onProgressChanged(view, 0, true)

        assertEquals(listOf(20, 10), values)
    }

    @Test
    fun `slider brackets keyboard and accessibility progress actions`() {
        val view = ViewComposeSeekBar(RuntimeEnvironment.getApplication())
        val events = mutableListOf<String>()
        InputViewBinder.bindSlider(
            view,
            sliderSpec(
                min = 0,
                value = 0,
                onValueChangeStarted = { events += "start" },
                onValueChangeFinished = { events += "finish" },
            ) {},
        )

        view.onKeyDown(
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT),
        )
        assertEquals("start", events.first())
        assertEquals("finish", events.last())

        events.clear()
        view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)
        assertEquals("start", events.first())
        assertEquals("finish", events.last())
    }

    @Test
    fun `disabled slider does not publish keyboard or accessibility interaction callbacks`() {
        val view = ViewComposeSeekBar(RuntimeEnvironment.getApplication())
        val events = mutableListOf<String>()
        InputViewBinder.bindSlider(
            view,
            sliderSpec(
                min = 0,
                value = 0,
                enabled = false,
                onValueChangeStarted = { events += "start" },
                onValueChangeFinished = { events += "finish" },
            ) {},
        )

        view.onKeyDown(
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT),
        )
        view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `button keeps one native listener and invokes the latest callback`() {
        val view = Button(RuntimeEnvironment.getApplication())
        var firstCalls = 0
        var secondCalls = 0

        ContentViewBinder.bindButton(view, buttonSpec { firstCalls += 1 })
        val firstBinding = view.getTag(R.id.viewcompose_button_click_listener)

        ContentViewBinder.bindButton(view, buttonSpec { secondCalls += 1 })
        val secondBinding = view.getTag(R.id.viewcompose_button_click_listener)
        view.performClick()

        assertSame(firstBinding, secondBinding)
        assertEquals(0, firstCalls)
        assertEquals(1, secondCalls)
    }

    @Test
    fun `disabled pull refresh keeps descendant input and reuses latest callback binding`() {
        val view = DeclarativePullToRefreshLayout(RuntimeEnvironment.getApplication())
        val child = Button(view.context)
        view.addView(child)
        var firstCalls = 0
        var secondCalls = 0
        ScrollableViewBinder.bindPullToRefresh(
            view,
            ScrollableViewBinder.PullToRefreshSpec(false, { firstCalls += 1 }, true, 0),
        )
        val firstBinding = view.getTag(R.id.viewcompose_pull_refresh_listener)
        ScrollableViewBinder.bindPullToRefresh(
            view,
            ScrollableViewBinder.PullToRefreshSpec(false, { secondCalls += 1 }, false, 0),
        )
        val secondBinding = view.getTag(R.id.viewcompose_pull_refresh_listener)

        (secondBinding as SwipeRefreshLayout.OnRefreshListener).onRefresh()

        assertSame(firstBinding, secondBinding)
        assertEquals(0, firstCalls)
        assertEquals(0, secondCalls)
        assertEquals(false, view.isEnabled)
        assertEquals(true, child.isEnabled)

        ScrollableViewBinder.bindPullToRefresh(
            view,
            ScrollableViewBinder.PullToRefreshSpec(false, { secondCalls += 1 }, true, 0),
        )
        secondBinding.onRefresh()

        assertSame(secondBinding, view.getTag(R.id.viewcompose_pull_refresh_listener))
        assertEquals(1, secondCalls)
    }

    @Test
    fun `pull refresh patch preserves the reusable guarded listener`() {
        val view = DeclarativePullToRefreshLayout(RuntimeEnvironment.getApplication())
        var firstCalls = 0
        var secondCalls = 0
        val previous = pullToRefreshProps(enabled = true) { firstCalls += 1 }
        ScrollableViewBinder.bindPullToRefresh(
            view,
            ScrollableViewBinder.PullToRefreshSpec(
                isRefreshing = previous.isRefreshing,
                onRefresh = previous.onRefresh,
                enabled = previous.enabled,
                indicatorColor = previous.indicatorColor,
            ),
        )
        val binding = view.getTag(R.id.viewcompose_pull_refresh_listener)
            as SwipeRefreshLayout.OnRefreshListener
        val disabled = pullToRefreshProps(enabled = false) { secondCalls += 1 }

        ContainerNodePatchApplier.applyPullToRefreshPatch(
            view = view,
            patch = PullToRefreshNodePatch(previous = previous, next = disabled),
        )
        binding.onRefresh()

        assertSame(binding, view.getTag(R.id.viewcompose_pull_refresh_listener))
        assertEquals(0, firstCalls)
        assertEquals(0, secondCalls)

        ContainerNodePatchApplier.applyPullToRefreshPatch(
            view = view,
            patch = PullToRefreshNodePatch(
                previous = disabled,
                next = pullToRefreshProps(enabled = true) { secondCalls += 1 },
            ),
        )
        binding.onRefresh()

        assertEquals(1, secondCalls)
    }

    private fun toggleSpec(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) = InputViewBinder.ToggleSpec(
        text = null,
        enabled = true,
        checked = checked,
        controlColor = 0xFF000000.toInt(),
        onCheckedChange = onCheckedChange,
    )

    private fun toggleNodeProps(checked: Boolean) = com.viewcompose.ui.node.spec.ToggleNodeProps(
        text = "Switch",
        enabled = true,
        checked = checked,
        controlColor = 0xFF000000.toInt(),
        onCheckedChange = {},
        textColor = 0xFF000000.toInt(),
        textSizeSp = 14.sp,
    )

    private fun sliderSpec(
        min: Int,
        value: Int,
        step: Int = 1,
        enabled: Boolean = true,
        onValueChangeStarted: (() -> Unit)? = null,
        onValueChangeFinished: (() -> Unit)? = null,
        onValueChange: (Int) -> Unit,
    ) = InputViewBinder.SliderSpec(
        min = min,
        max = min + 20,
        value = value,
        enabled = enabled,
        thumbColor = 0xFF000000.toInt(),
        trackColor = 0xFF888888.toInt(),
        onValueChange = onValueChange,
        step = step,
        onValueChangeStarted = onValueChangeStarted,
        onValueChangeFinished = onValueChangeFinished,
    )

    private fun buttonSpec(
        onClick: () -> Unit,
    ) = ContentViewBinder.ButtonSpec(
        text = "Action",
        enabled = true,
        iconSpacing = 0,
        leadingIcon = null,
        trailingIcon = null,
        iconTint = 0xFF000000.toInt(),
        iconSize = 16,
        onClick = onClick,
    )

    private fun pullToRefreshProps(
        enabled: Boolean,
        onRefresh: () -> Unit,
    ) = PullToRefreshNodeProps(
        isRefreshing = false,
        onRefresh = onRefresh,
        enabled = enabled,
        indicatorColor = 0xFF000000.toInt(),
    )

    private class CountingSwitch : Switch(RuntimeEnvironment.getApplication()) {
        var checkedAssignments: Int = 0

        override fun setChecked(checked: Boolean) {
            checkedAssignments += 1
            super.setChecked(checked)
        }
    }
}
