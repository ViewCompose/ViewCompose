package com.viewcompose.paging.samples

import androidx.paging.LoadState
import androidx.paging.LoadType
import androidx.paging.PagingData
import com.viewcompose.paging.PagingContentState
import com.viewcompose.paging.PagingLazyColumn
import com.viewcompose.paging.PagingLifecyclePolicy
import com.viewcompose.paging.collectAsViewComposePagingItems
import com.viewcompose.paging.contentState
import com.viewcompose.paging.forLoadType
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.key
import kotlinx.coroutines.flow.Flow

data class PagingSampleRow(
    val id: Long,
    val version: Int,
    val label: String,
)

fun UiTreeBuilder.pagingLazyColumnSample(pages: Flow<PagingData<PagingSampleRow>>) {
    val items = pages.collectAsViewComposePagingItems(
        lifecyclePolicy = PagingLifecyclePolicy.Composition,
    )
    PagingLazyColumn(
        items = items,
        key = PagingSampleRow::id,
        contentType = { "paging-row" },
        contentRevision = PagingSampleRow::version,
        placeholderContentRevision = "paging-placeholder-v1",
        placeholderContent = { index -> Text("Loading row $index") },
    ) { row ->
        Text(row.label)
    }
}

fun UiTreeBuilder.pagingLoadStateCompositionSample(
    pages: Flow<PagingData<PagingSampleRow>>,
) {
    val items = pages.collectAsViewComposePagingItems(
        lifecyclePolicy = PagingLifecyclePolicy.Composition,
    )
    when (val state = items.contentState) {
        PagingContentState.InitialLoading -> Text("Loading contacts")
        is PagingContentState.InitialError -> {
            Text(state.error.message ?: "Unable to load contacts")
            Button("Retry", onClick = items::retry)
        }
        PagingContentState.Empty -> Text("No contacts")
        PagingContentState.Content -> {
            val refresh = items.loadStates.forLoadType(LoadType.REFRESH)
            if (refresh.combined is LoadState.Loading) {
                Text("Refreshing")
            }
            val prepend = items.loadStates.forLoadType(LoadType.PREPEND)
            when (prepend.combined) {
                is LoadState.Loading -> Text("Loading previous contacts")
                is LoadState.Error -> Button("Retry previous", onClick = items::retry)
                is LoadState.NotLoading -> Unit
            }
            key("paging-contact-list") {
                PagingLazyColumn(
                    items = items,
                    key = PagingSampleRow::id,
                    contentType = { "paging-row" },
                    contentRevision = PagingSampleRow::version,
                ) { row ->
                    Text(row.label)
                }
            }
            val append = items.loadStates.forLoadType(LoadType.APPEND)
            when (append.combined) {
                is LoadState.Loading -> Text("Loading more contacts")
                is LoadState.Error -> Button("Retry more", onClick = items::retry)
                is LoadState.NotLoading -> Unit
            }
            (refresh.source as? LoadState.Error)?.let { source ->
                Text("Source: ${source.error.message}")
            }
            (refresh.mediator as? LoadState.Error)?.let { mediator ->
                Text("Mediator: ${mediator.error.message}")
            }
            Button("Refresh", onClick = items::refresh)
        }
    }
}
