package com.viewcompose.viewmodel

/*
 * 测试职责：覆盖 viewmodel integration 中的 Saved State Handle Composition 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Saved State Handle Composition behavior in viewmodel integration and guards the contract against regressions.
 */

import org.junit.Assert.assertTrue
import org.junit.Test

class SavedStateHandleCompositionTest {
    @Test
    fun `savedStateHandle throws when owner is missing`() {
        val error = runCatching {
            savedStateHandle()
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("ProvideViewModelStoreOwner"))
    }
}
