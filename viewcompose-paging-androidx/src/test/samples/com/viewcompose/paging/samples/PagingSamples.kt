package com.viewcompose.paging.samples

import androidx.paging.PagingData
import com.viewcompose.paging.PagingLazyColumn
import com.viewcompose.paging.PagingLifecyclePolicy
import com.viewcompose.paging.collectAsViewComposePagingItems
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
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
