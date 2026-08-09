package com.viewcompose.overlay.oneui7.android.presenter

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidOneUi7SnackbarPresenterTest {
    @Test
    fun cornerRadiusTracksHalfOfActualSnackbarHeight() {
        assertEquals(24f, oneUi7SnackbarCornerRadius(heightPx = 48), 0f)
        assertEquals(36f, oneUi7SnackbarCornerRadius(heightPx = 72), 0f)
        assertEquals(0f, oneUi7SnackbarCornerRadius(heightPx = -1), 0f)
    }
}
