package com.viewcompose.widget.core

import com.viewcompose.ui.focus.FocusDirection
import com.viewcompose.ui.focus.FocusManager
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFocusManagerTest {
    @Test
    fun `focus manager context publishes and restores session owner`() {
        val manager = FakeFocusManager()

        FocusManagerContext.withFocusManager(manager) {
            assertSame(manager, LocalFocusManager.current)
        }

        assertTrue(runCatching { LocalFocusManager.current }.isFailure)
    }

    private class FakeFocusManager : FocusManager {
        override fun clearFocus(force: Boolean) = Unit

        override fun moveFocus(direction: FocusDirection): Boolean = false
    }
}
