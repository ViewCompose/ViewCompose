package com.viewcompose.studio.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewGalleryPriorityOrderTest {
    @Test
    fun `visible demand moves known selections first without duplicating them`() {
        val first = selection("First")
        val second = selection("Second")
        val third = selection("Third")
        val order = PreviewGalleryPriorityOrder(listOf(first, second, third))

        order.prioritize(listOf(third, second, third))

        assertEquals(listOf(third, second, first), order.order(listOf(first, second, third)))
    }

    private fun selection(symbol: String): PreviewSourceSelection {
        return PreviewSourceSelection("/project/$symbol.kt", symbol, 1)
    }
}
