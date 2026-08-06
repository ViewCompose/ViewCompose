package com.viewcompose.ui.foundation

/*
 * 测试职责：覆盖 widget-core context 中的 Local Focus Manager 行为，防止 DSL、状态或主题契约在后续重构中回退。
 * Test responsibility: covers Local Focus Manager behavior in widget-core context and guards DSL, state, or theme contracts against regressions.
 */

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
