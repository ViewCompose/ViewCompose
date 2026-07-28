package com.viewcompose.renderer.modifier

/*
 * 测试职责：覆盖 renderer modifier 中的 Insets Padding Modifier 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Insets Padding Modifier behavior in renderer modifier and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.modifier.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsetsPaddingModifierTest {
    @Test
    fun `imeInsetsPadding defaults to bottom only`() {
        val modifier = Modifier.imeInsetsPadding()

        val element = modifier.elements.single() as ImeInsetsPaddingModifierElement
        assertTrue(element.bottom)
        assertEquals(false, element.left)
        assertEquals(false, element.top)
        assertEquals(false, element.right)
    }

    @Test
    fun `resolve keeps last imeInsetsPadding element`() {
        val resolved = Modifier
            .imeInsetsPadding(bottom = true)
            .imeInsetsPadding(left = true, bottom = false)
            .resolve()

        val imeInsets = resolved.imeInsetsPadding
        assertNotNull(imeInsets)
        assertTrue(imeInsets!!.left)
        assertEquals(false, imeInsets.bottom)
    }

    @Test
    fun `system bars and ime insets can coexist`() {
        val resolved = Modifier
            .systemBarsInsetsPadding()
            .imeInsetsPadding(bottom = true)
            .resolve()

        assertNotNull(resolved.systemBarsInsetsPadding)
        assertNotNull(resolved.imeInsetsPadding)
    }
}
