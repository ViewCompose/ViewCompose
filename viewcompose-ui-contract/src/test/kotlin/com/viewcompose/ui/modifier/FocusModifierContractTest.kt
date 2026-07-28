package com.viewcompose.ui.modifier

/*
 * 测试职责：覆盖 UI contract 中的 Focus Modifier Contract 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Focus Modifier Contract behavior in UI contract and guards the contract against regressions.
 */

import com.viewcompose.ui.focus.FocusRequester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModifierContractTest {
    @Test
    fun `focus modifiers retain declarative order and targets`() {
        val self = FocusRequester()
        val next = FocusRequester()
        val modifier = Modifier
            .focusRequester(self)
            .focusable()
            .focusGroup()
            .focusProperties {
                canFocus = true
                this.next = next
            }
            .onFocusChanged {}
            .onPreviewKeyEvent { false }
            .onKeyEvent { false }

        assertEquals(7, modifier.elements.size)
        assertSame(
            self,
            (modifier.elements[0] as FocusRequesterModifierElement).requester,
        )
        assertTrue((modifier.elements[1] as FocusableModifierElement).enabled)
        assertTrue((modifier.elements[2] as FocusGroupModifierElement).enabled)
        val properties = (modifier.elements[3] as FocusPropertiesModifierElement).properties
        assertEquals(true, properties.canFocus)
        assertSame(next, properties.next)
        assertTrue(modifier.elements[4] is OnFocusChangedModifierElement)
        assertTrue(modifier.elements[5] is PreviewKeyEventModifierElement)
        assertTrue(modifier.elements[6] is KeyEventModifierElement)
    }
}
