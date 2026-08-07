package com.viewcompose.renderer.view.tree

import android.graphics.PorterDuff
import android.widget.SeekBar
import android.widget.Switch
import com.viewcompose.renderer.view.tree.patch.InputNodePatchApplier
import com.viewcompose.ui.node.spec.SliderNodeProps
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeInputColorBindingTest {
    @Test
    fun `switch binding preserves native mask while applying semantic tints`() {
        val thumbColor = 0xFFFDF8FF.toInt()
        val trackColor = 0xFF246B4A.toInt()
        val view = Switch(RuntimeEnvironment.getApplication())

        InputViewBinder.bindSwitch(
            view = view,
            spec = InputViewBinder.ToggleSpec(
                text = "Switch",
                enabled = true,
                checked = true,
                controlColor = trackColor,
                thumbColor = thumbColor,
                trackColor = trackColor,
                onCheckedChange = null,
            ),
        )

        assertEquals(PorterDuff.Mode.SRC_IN, view.thumbTintMode)
        assertEquals(PorterDuff.Mode.SRC_IN, view.trackTintMode)
        assertEquals(thumbColor, view.thumbTintList?.defaultColor)
        assertEquals(trackColor, view.trackTintList?.defaultColor)
    }

    @Test
    fun `slider binding owns active inactive and thumb colors`() {
        val thumbColor = 0xFF246B4A.toInt()
        val activeTrackColor = 0xFF246B4A.toInt()
        val inactiveTrackColor = 0xFFDDE8DF.toInt()
        val view = SeekBar(RuntimeEnvironment.getApplication())

        InputViewBinder.bindSlider(
            view = view,
            spec = sliderSpec(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = inactiveTrackColor,
            ),
        )

        assertEquals(PorterDuff.Mode.SRC_IN, view.thumbTintMode)
        assertEquals(PorterDuff.Mode.SRC_IN, view.progressTintMode)
        assertEquals(PorterDuff.Mode.SRC_IN, view.progressBackgroundTintMode)
        assertEquals(thumbColor, view.thumbTintList?.defaultColor)
        assertEquals(activeTrackColor, view.progressTintList?.defaultColor)
        assertEquals(inactiveTrackColor, view.progressBackgroundTintList?.defaultColor)
    }

    @Test
    fun `slider patch updates inactive track without recreating the view`() {
        val previous = sliderNodeProps(inactiveTrackColor = 0xFFDDE8DF.toInt())
        val next = previous.copy(inactiveTrackColor = 0xFFC3D8C9.toInt())
        val view = SeekBar(RuntimeEnvironment.getApplication())
        InputViewBinder.bindSlider(
            view = view,
            spec = sliderSpec(
                thumbColor = previous.thumbColor,
                activeTrackColor = previous.trackColor,
                inactiveTrackColor = previous.inactiveTrackColor,
            ),
        )

        InputNodePatchApplier.applySliderPatch(
            view = view,
            patch = SliderNodePatch(previous = previous, next = next),
        )

        assertEquals(next.inactiveTrackColor, view.progressBackgroundTintList?.defaultColor)
    }

    private fun sliderSpec(
        thumbColor: Int,
        activeTrackColor: Int,
        inactiveTrackColor: Int,
    ) = InputViewBinder.SliderSpec(
        min = 0,
        max = 100,
        value = 50,
        enabled = true,
        thumbColor = thumbColor,
        trackColor = activeTrackColor,
        onValueChange = null,
        inactiveTrackColor = inactiveTrackColor,
    )

    private fun sliderNodeProps(
        inactiveTrackColor: Int,
    ) = SliderNodeProps(
        min = 0,
        max = 100,
        value = 50,
        enabled = true,
        thumbColor = 0xFF246B4A.toInt(),
        trackColor = 0xFF246B4A.toInt(),
        onValueChange = null,
        inactiveTrackColor = inactiveTrackColor,
    )
}
