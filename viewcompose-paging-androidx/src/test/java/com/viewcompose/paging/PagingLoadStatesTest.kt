package com.viewcompose.paging

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.LoadType
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PagingLoadStatesTest {
    @Test
    fun `load type projection preserves combined source and mediator origins`() {
        val sourceRefresh = LoadState.Error(IOException("source refresh"))
        val sourcePrepend = LoadState.NotLoading(endOfPaginationReached = true)
        val sourceAppend = LoadState.Loading
        val mediatorRefresh = LoadState.Loading
        val mediatorPrepend = LoadState.Error(IOException("mediator prepend"))
        val mediatorAppend = LoadState.NotLoading(endOfPaginationReached = false)
        val combinedRefresh = LoadState.Loading
        val combinedPrepend = mediatorPrepend
        val combinedAppend = sourceAppend
        val states = CombinedLoadStates(
            refresh = combinedRefresh,
            prepend = combinedPrepend,
            append = combinedAppend,
            source = LoadStates(sourceRefresh, sourcePrepend, sourceAppend),
            mediator = LoadStates(mediatorRefresh, mediatorPrepend, mediatorAppend),
        )

        val refresh = states.forLoadType(LoadType.REFRESH)
        assertEquals(LoadType.REFRESH, refresh.loadType)
        assertSame(combinedRefresh, refresh.combined)
        assertSame(sourceRefresh, refresh.source)
        assertSame(mediatorRefresh, refresh.mediator)

        val prepend = states.forLoadType(LoadType.PREPEND)
        assertEquals(LoadType.PREPEND, prepend.loadType)
        assertSame(combinedPrepend, prepend.combined)
        assertSame(sourcePrepend, prepend.source)
        assertSame(mediatorPrepend, prepend.mediator)

        val append = states.forLoadType(LoadType.APPEND)
        assertEquals(LoadType.APPEND, append.loadType)
        assertSame(combinedAppend, append.combined)
        assertSame(sourceAppend, append.source)
        assertSame(mediatorAppend, append.mediator)
    }

    @Test
    fun `load type projection keeps missing mediator absent`() {
        val complete = LoadState.NotLoading(endOfPaginationReached = true)
        val states = CombinedLoadStates(
            refresh = complete,
            prepend = complete,
            append = complete,
            source = LoadStates(complete, complete, complete),
            mediator = null,
        )

        assertNull(states.forLoadType(LoadType.REFRESH).mediator)
        assertNull(states.forLoadType(LoadType.PREPEND).mediator)
        assertNull(states.forLoadType(LoadType.APPEND).mediator)
    }
}
