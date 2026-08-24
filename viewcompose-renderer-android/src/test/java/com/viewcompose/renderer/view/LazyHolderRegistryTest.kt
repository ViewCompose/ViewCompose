package com.viewcompose.renderer.view

/*
 * 测试职责：覆盖 renderer view 中的 Lazy Holder Registry 行为，防止渲染和 patch 契约在后续重构中回退。
 * Test responsibility: covers Lazy Holder Registry behavior in renderer view and guards render and patch contracts against regressions.
 */

import com.viewcompose.renderer.view.lazy.session.LazyHolderRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun `disposeDetachedWhere releases only matching detached holders once`() {
        val events = mutableListOf<String>()
        val registry = LazyHolderRegistry<String> { holder ->
            events += "dispose:$holder"
        }
        registry.onBound("attached-stale")
        registry.onAttached("attached-stale")
        registry.onBound("detached-stale")
        registry.onBound("detached-current")

        registry.disposeDetachedWhere { holder -> holder.endsWith("stale") }
        registry.onRecycled("detached-stale")
        registry.disposeAll()

        assertEquals(
            listOf(
                "dispose:detached-stale",
                "dispose:attached-stale",
                "dispose:detached-current",
            ),
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

    @Test
    fun `disposeAll attempts every holder and removes ownership when cleanup fails`() {
        val events = mutableListOf<String>()
        val registry = LazyHolderRegistry<String> { holder ->
            events += "dispose:$holder"
            if (holder != "holder-C") error("failed:$holder")
        }
        registry.onBound("holder-A")
        registry.onBound("holder-B")
        registry.onBound("holder-C")

        val failure = assertThrows(IllegalStateException::class.java, registry::disposeAll)

        assertEquals("failed:holder-A", failure.message)
        assertEquals(listOf("failed:holder-B"), failure.suppressed.map { it.message })
        assertEquals(
            listOf("dispose:holder-A", "dispose:holder-B", "dispose:holder-C"),
            events,
        )
        registry.disposeAll()
    }
}
