package com.viewcompose.ui.node

import org.junit.Assert.assertEquals
import org.junit.Test

class UiStateLayerColorsTest {
    @Test
    fun `omitted interaction colors retain one-color compatibility`() {
        val colors = UiStateLayerColors(pressedColor = 0x1A112233)

        assertEquals(colors.pressedColor, colors.focusedColor)
        assertEquals(colors.pressedColor, colors.hoveredColor)
    }
}
