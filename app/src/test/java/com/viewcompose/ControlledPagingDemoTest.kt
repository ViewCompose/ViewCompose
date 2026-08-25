package com.viewcompose

import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledPagingDemoTest {
    @Test
    fun `data loads wait for resolution and preserve deterministic page keys`() = runBlocking {
        val fixture = fixture()
        val source = fixture.newPagingSource()
        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            source.load(refreshParams())
        }

        assertFalse(refresh.isCompleted)
        fixture.resolveNext(DemoPagingOutcome.Data)
        val refreshPage = refresh.await() as PagingSource.LoadResult.Page
        assertEquals((1..10).toList(), refreshPage.data.map(DemoPagingRow::id))
        assertEquals(1, refreshPage.nextKey)

        val append = async(start = CoroutineStart.UNDISPATCHED) {
            source.load(appendParams(key = 1))
        }
        assertFalse(append.isCompleted)
        fixture.resolveNext(DemoPagingOutcome.Data)
        val appendPage = append.await() as PagingSource.LoadResult.Page
        assertEquals((11..20).toList(), appendPage.data.map(DemoPagingRow::id))
        assertEquals(2, appendPage.nextKey)
    }

    @Test
    fun `empty and error outcomes remain explicit PagingSource results`() = runBlocking {
        val emptyFixture = fixture()
        val empty = async(start = CoroutineStart.UNDISPATCHED) {
            emptyFixture.newPagingSource().load(refreshParams())
        }
        emptyFixture.resolveNext(DemoPagingOutcome.Empty)
        val emptyPage = empty.await() as PagingSource.LoadResult.Page
        assertTrue(emptyPage.data.isEmpty())
        assertEquals(null, emptyPage.nextKey)

        val errorFixture = fixture()
        val error = async(start = CoroutineStart.UNDISPATCHED) {
            errorFixture.newPagingSource().load(refreshParams())
        }
        errorFixture.resolveNext(DemoPagingOutcome.Error)
        val failure = error.await() as PagingSource.LoadResult.Error
        assertEquals(ERROR_MESSAGE, failure.throwable.message)
    }

    @Test
    fun `outcome cycle is closed and stable`() {
        assertSame(DemoPagingOutcome.Empty, DemoPagingOutcome.Data.next())
        assertSame(DemoPagingOutcome.Error, DemoPagingOutcome.Empty.next())
        assertSame(DemoPagingOutcome.Data, DemoPagingOutcome.Error.next())
    }

    private fun fixture(): ControlledPagingDemo = ControlledPagingDemo(
        rows = (1..30).map { id -> DemoPagingRow(id, "row-$id") },
        errorMessage = ERROR_MESSAGE,
    )

    private fun refreshParams(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(
            key = null,
            loadSize = 10,
            placeholdersEnabled = false,
        )

    private fun appendParams(key: Int): PagingSource.LoadParams.Append<Int> =
        PagingSource.LoadParams.Append(
            key = key,
            loadSize = 10,
            placeholdersEnabled = false,
        )

    private companion object {
        const val ERROR_MESSAGE = "controlled failure"
    }
}
