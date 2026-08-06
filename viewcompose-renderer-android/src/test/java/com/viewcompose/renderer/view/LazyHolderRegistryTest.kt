package com.viewcompose.renderer.view

/*
 * 测试职责：覆盖 renderer view 中的 Lazy Holder Registry 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy Holder Registry behavior in renderer view and guards render and patch contracts against regressions.
 */

import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class LazyHolderRegistryTest {
    @Test
    fun `disposeAll disposes bound holders even after detach`() {
        val events = mutableListOf<String>()
        val registry = LazyHolderRegistry<String> { holder ->
            events += "dispose:$holder"
        }

        registry.onBound("holder-A")
        registry.onAttached("holder-A")
        registry.onDetached("holder-A")
        registry.disposeAll()

        assertEquals(
            listOf("dispose:holder-A"),
            events,
        )
    }

    @Test
    fun `onRecycled disposes holder once and removes it from future disposeAll`() {
        val events = mutableListOf<String>()
        val registry = LazyHolderRegistry<String> { holder ->
            events += "dispose:$holder"
        }

        registry.onBound("holder-A")
        registry.onRecycled("holder-A")
        registry.disposeAll()

        assertEquals(
            listOf("dispose:holder-A"),
            events,
        )
    }

    @Test
    fun `disposeAll disposes every currently bound holder`() {
        val events = mutableListOf<String>()
        val registry = LazyHolderRegistry<String> { holder ->
            events += "dispose:$holder"
        }

        registry.onBound("holder-A")
        registry.onBound("holder-B")
        registry.disposeAll()

        assertEquals(
            listOf("dispose:holder-A", "dispose:holder-B"),
            events,
        )
    }
}
