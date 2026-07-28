package com.viewcompose.graphics.core

/*
 * 测试职责：覆盖 graphics core 中的 Draw Cache 行为，防止关键契约在后续重构中回退。
 * Test responsibility: covers Draw Cache behavior in graphics core and guards the contract against regressions.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DrawCacheTest {
    @Test
    fun `getOrBuild reuses cached value for same key`() {
        val cache = DrawCache<List<Int>>()
        var buildCount = 0

        val first = cache.getOrBuild("k1") {
            buildCount += 1
            listOf(1, 2, 3)
        }
        val second = cache.getOrBuild("k1") {
            buildCount += 1
            listOf(9, 9, 9)
        }

        assertSame(first, second)
        assertEquals(1, buildCount)
    }

    @Test
    fun `getOrBuild rebuilds for different key`() {
        val cache = DrawCache<Int>()
        var value = 0

        val first = cache.getOrBuild("a") { ++value }
        val second = cache.getOrBuild("b") { ++value }

        assertEquals(1, first)
        assertEquals(2, second)
    }
}
