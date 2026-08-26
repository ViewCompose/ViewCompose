package com.viewcompose.samples.tutorials

import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.HorizontalPager
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.LazyVerticalGrid
import com.viewcompose.ui.foundation.StaticContentRevision
import com.viewcompose.ui.foundation.TabRow
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.foundation.rememberLazyListState
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.node.policy.GridCells
import com.viewcompose.ui.node.policy.GridItemSpan
import com.viewcompose.ui.state.PagerState
import com.viewcompose.ui.unit.dp

// DOCS_REGION_START(lazy-collections-state)
data class InboxMessage(
    val id: Long,
    val subject: String,
    val version: Int,
)

fun UiTreeBuilder.MessageList(messages: List<InboxMessage>) {
    val listState = rememberLazyListState()

    Column {
        Text(
            "visible=${listState.firstVisibleItemIndex}..${listState.lastVisibleItemIndex}",
        )
        Button(
            text = "Go to latest",
            enabled = messages.isNotEmpty(),
            onClick = { listState.animateScrollToItem(messages.lastIndex) },
        )
        LazyColumn(
            items = messages,
            key = InboxMessage::id,
            contentType = { "message" },
            contentRevision = InboxMessage::version,
            state = listState,
            spacing = 8.dp,
        ) { message ->
            Text(message.subject, modifier = Modifier.fillMaxWidth())
        }
    }
}
// DOCS_REGION_END(lazy-collections-state)

// DOCS_REGION_START(lazy-collections-grid)
data class GalleryCard(
    val id: Long,
    val title: String,
)

fun UiTreeBuilder.AdaptiveGallery(cards: List<GalleryCard>) {
    LazyVerticalGrid(
        cells = GridCells.Adaptive(minSize = 120.dp),
        horizontalSpacing = 12.dp,
        verticalSpacing = 12.dp,
    ) {
        item(
            key = "gallery-heading",
            contentRevision = StaticContentRevision,
            span = GridItemSpan.FullLine,
        ) {
            Text("Gallery")
        }
        items(
            items = cards,
            key = GalleryCard::id,
            contentType = { "gallery-card" },
            span = { GridItemSpan.Single },
        ) { card ->
            Text(card.title)
        }
    }
}
// DOCS_REGION_END(lazy-collections-grid)

// DOCS_REGION_START(lazy-collections-pager)
fun UiTreeBuilder.PagerWithTabs() {
    val titles = listOf("Overview", "Activity", "Settings")
    val selectedPage = remember { mutableStateOf(0) }
    val pagerState = remember { PagerState() }

    Column {
        TabRow(
            selectedIndex = selectedPage.value,
            onTabSelected = { page ->
                selectedPage.value = page
                pagerState.animateScrollToPage(page)
            },
            pagerState = pagerState,
        ) {
            titles.forEach { title ->
                Tab(key = title, contentRevision = title) {
                    Text(title)
                }
            }
        }
        HorizontalPager(
            currentPage = selectedPage.value,
            onPageChanged = { page -> selectedPage.value = page },
            pagerState = pagerState,
        ) {
            titles.forEach { title ->
                Page(key = title, contentRevision = title) {
                    Text(title)
                }
            }
        }
    }
}
// DOCS_REGION_END(lazy-collections-pager)
