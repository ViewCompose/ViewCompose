package com.viewcompose.ui.graphics

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class UiShadowTest {
    @Test
    fun `shadow retains logical values`() {
        val shadow = UiShadow(
            color = 0x33010203,
            blurRadius = 12.dp,
            spreadRadius = (-2).dp,
            offsetX = 1.dp,
            offsetY = 6.dp,
        )

        assertEquals(0x33010203, shadow.color)
        assertEquals(12.dp, shadow.blurRadius)
        assertEquals((-2).dp, shadow.spreadRadius)
        assertEquals(1.dp, shadow.offsetX)
        assertEquals(6.dp, shadow.offsetY)
    }

    @Test
    fun `negative blur radius is rejected`() {
        try {
            UiShadow(blurRadius = (-1).dp)
            fail("Expected negative blur radius to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "UiShadow.blurRadius must be finite and non-negative.",
                expected.message,
            )
        }
    }

    @Test
    fun `non finite values are rejected`() {
        try {
            UiShadow(
                blurRadius = 1.dp,
                offsetY = UiDp(Float.NaN),
            )
            fail("Expected non-finite offset to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "UiShadow.offsetY must be finite.",
                expected.message,
            )
        }
    }
}
