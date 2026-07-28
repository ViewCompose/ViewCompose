package com.viewcompose.renderer.modifier

/*
 * 测试职责：覆盖 renderer modifier 中的 Resolved Focus Modifiers 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Resolved Focus Modifiers behavior in renderer modifier and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.focus.FocusRequester
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.focusProperties
import com.viewcompose.ui.modifier.focusRequester
import com.viewcompose.ui.modifier.focusable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedFocusModifiersTest {
    @Test
    fun `resolve merges focus properties and keeps latest scalar modifiers`() {
        val owner = FocusRequester()
        val next = FocusRequester()
        val right = FocusRequester()
        val resolved = Modifier
            .focusable(enabled = false)
            .focusable(enabled = true)
            .focusRequester(owner)
            .focusProperties {
                canFocus = false
                this.next = next
            }.focusProperties {
                canFocus = true
                this.right = right
            }.resolve()

        assertTrue(resolved.focusable?.enabled == true)
        assertSame(owner, resolved.focusRequester?.requester)
        assertEquals(true, resolved.focusProperties.canFocus)
        assertSame(next, resolved.focusProperties.next)
        assertSame(right, resolved.focusProperties.right)
    }
}
