package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

/*
 * 测试职责：覆盖 renderer view/tree 中的 Native Listener Binding 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Native Listener Binding behavior in renderer view/tree and guards render and patch contracts against regressions.
 */

import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Switch
import com.viewcompose.renderer.R
import com.viewcompose.renderer.view.tree.patch.InputNodePatchApplier
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
        rippleColor = 0x33000000,
    )

    private fun sliderSpec(
        min: Int,
        value: Int,
        onValueChange: (Int) -> Unit,
    ) = InputViewBinder.SliderSpec(
        min = min,
        max = min + 20,
        value = value,
        enabled = true,
        thumbColor = 0xFF000000.toInt(),
        trackColor = 0xFF888888.toInt(),
        onValueChange = onValueChange,
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

    private class CountingSwitch : Switch(RuntimeEnvironment.getApplication()) {
        var checkedAssignments: Int = 0

        override fun setChecked(checked: Boolean) {
            checkedAssignments += 1
            super.setChecked(checked)
        }
    }
}
