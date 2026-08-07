package com.viewcompose.renderer.view.tree

import android.graphics.Color
import com.viewcompose.ui.node.UiStateLayerColors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StateLayerColorStateListTest {
    @Test
    fun `selector preserves precedence and transparent inactive states`() {
        val colors = UiStateLayerColors(
            pressedColor = 0x1A112233,
            focusedColor = 0x1A223344,
            hoveredColor = 0x14334455,
        )
        val selector = colors.toColorStateList()

        assertEquals(
            colors.pressedColor,
            selector.getColorForState(
                intArrayOf(
                    android.R.attr.state_enabled,
                    android.R.attr.state_pressed,
                    android.R.attr.state_focused,
                    android.R.attr.state_hovered,
                ),
                Color.MAGENTA,
            ),
        )
        assertEquals(
            colors.focusedColor,
            selector.getColorForState(
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
                Color.MAGENTA,
            ),
        )
        assertEquals(
            colors.hoveredColor,
            selector.getColorForState(
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_hovered),
                Color.MAGENTA,
            ),
        )
        assertEquals(
            Color.TRANSPARENT,
            selector.getColorForState(intArrayOf(android.R.attr.state_enabled), Color.MAGENTA),
        )
        assertEquals(
            Color.TRANSPARENT,
            selector.getColorForState(intArrayOf(android.R.attr.state_pressed), Color.MAGENTA),
        )
    }
}
