package com.viewcompose.performance

import androidx.paging.PagingSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PagingPerformanceSourceTest {
    @Test
    fun `refresh exposes one compact page inside a million-position generation`() = runBlocking {
        val result = PagingPerformanceSource(query = 1).load(
            PagingSource.LoadParams.Refresh(
                key = 64,
                loadSize = PERFORMANCE_PAGING_PAGE_SIZE,
                placeholdersEnabled = true,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        assertEquals(64, page.itemsBefore)
        assertEquals(
            PERFORMANCE_PAGING_TOTAL_ITEMS - 64 - PERFORMANCE_PAGING_PAGE_SIZE,
            page.itemsAfter,
        )
        assertEquals(32, page.prevKey)
        assertEquals(96, page.nextKey)
        assertEquals(64, page.data.first().position)
        assertEquals(95, page.data.last().position)
        assertTrue(page.data.all { row -> row.query == 1 })
    }

    @Test
    fun `final page terminates and query identity cannot cross generations`() = runBlocking {
        val start = PERFORMANCE_PAGING_TOTAL_ITEMS - 16
        val queryZero = PagingPerformanceSource(query = 0).load(
            PagingSource.LoadParams.Append(
                key = start,
                loadSize = PERFORMANCE_PAGING_PAGE_SIZE,
                placeholdersEnabled = true,
            ),
        ) as PagingSource.LoadResult.Page
        val queryOne = PagingPerformanceRow(query = 1, position = start)

        assertEquals(16, queryZero.data.size)
        assertNull(queryZero.nextKey)
        assertEquals(start, queryZero.data.first().position)
        assertTrue(queryZero.data.first().stableId != queryOne.stableId)
    }
}
