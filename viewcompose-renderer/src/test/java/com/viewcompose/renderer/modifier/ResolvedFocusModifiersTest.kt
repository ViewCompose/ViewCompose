package com.viewcompose.renderer.modifier

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
