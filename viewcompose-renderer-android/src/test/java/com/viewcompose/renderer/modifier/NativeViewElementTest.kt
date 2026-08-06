package com.viewcompose.renderer.modifier

/*
 * 测试职责：覆盖 renderer modifier 中的 Native View Element 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Native View Element behavior in renderer modifier and guards render and patch contracts against regressions.
 */

import com.viewcompose.ui.modifier.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeViewElementTest {
    @Test
    fun `equals compares only stableKey`() {
        val a = NativeViewElement(stableKey = "rotation") { }
        val b = NativeViewElement(stableKey = "rotation") { }
        val c = NativeViewElement(stableKey = "elevation") { }

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `hashCode depends only on stableKey`() {
        val a = NativeViewElement(stableKey = 42) { }
        val b = NativeViewElement(stableKey = 42) { }

        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `nativeView extension appends NativeViewElement to modifier chain`() {
        val modifier = Modifier.then(NativeViewElement(stableKey = "test") { })

        assertTrue(modifier.elements.single() is NativeViewElement)
        assertEquals("test", (modifier.elements.single() as NativeViewElement).stableKey)
    }

    @Test
    fun `modifier with same nativeView key equals another`() {
        val m1 = Modifier.then(NativeViewElement(stableKey = "a") { })
        val m2 = Modifier.then(NativeViewElement(stableKey = "a") { })

        assertEquals(m1.elements, m2.elements)
    }

    @Test
    fun `modifier with different nativeView key differs`() {
        val m1 = Modifier.then(NativeViewElement(stableKey = "a") { })
        val m2 = Modifier.then(NativeViewElement(stableKey = "b") { })

        assertNotEquals(m1.elements, m2.elements)
    }
}
