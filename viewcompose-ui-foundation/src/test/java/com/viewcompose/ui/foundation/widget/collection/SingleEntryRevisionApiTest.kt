package com.viewcompose.ui.foundation

/*
 * Test responsibility: detects accidental single-entry revision/content-type swaps despite their
 * identical JVM erasure.
 */

import com.viewcompose.ui.node.spec.LazyColumnNodeProps
import com.viewcompose.ui.node.spec.LazyVerticalGridNodeProps
import org.junit.Assert.assertSame
import org.junit.Test

class SingleEntryRevisionApiTest {
    @Test
    fun `single entry APIs bind positional revision before optional content type`() {
        val listItemRevision = RevisionSentinel("list-item")
        val listItemType = ContentTypeSentinel("list-item")
        val listHeaderRevision = RevisionSentinel("list-header")
        val listHeaderType = ContentTypeSentinel("list-header")
        val gridItemRevision = RevisionSentinel("grid-item")
        val gridItemType = ContentTypeSentinel("grid-item")
        val gridHeaderRevision = RevisionSentinel("grid-header")
        val gridHeaderType = ContentTypeSentinel("grid-header")
        val pageRevision = RevisionSentinel("page")
        val pageType = ContentTypeSentinel("page")
        val tabRevision = RevisionSentinel("tab")

        val tree = buildVNodeTree {
            LazyColumn {
                item("list-item", listItemRevision, contentType = listItemType) { Text("Item") }
                stickyHeader(
                    "list-header",
                    listHeaderRevision,
                    contentType = listHeaderType,
                ) { Text("Header") }
            }
            LazyVerticalGrid {
                item("grid-item", gridItemRevision, contentType = gridItemType) { Text("Item") }
                stickyHeader(
                    "grid-header",
                    gridHeaderRevision,
                    contentType = gridHeaderType,
                ) { Text("Header") }
            }
        }

        val listItems = (tree[0].spec as LazyColumnNodeProps).items.associateBy { it.key }
        assertSame(listItemRevision, listItems.getValue("list-item").contentRevision)
        assertSame(listItemType, listItems.getValue("list-item").contentType)
        assertSame(listHeaderRevision, listItems.getValue("list-header").contentRevision)
        assertSame(listHeaderType, listItems.getValue("list-header").contentType)

        val gridItems = (tree[1].spec as LazyVerticalGridNodeProps).items.associateBy { it.key }
        assertSame(gridItemRevision, gridItems.getValue("grid-item").contentRevision)
        assertSame(gridItemType, gridItems.getValue("grid-item").contentType)
        assertSame(gridHeaderRevision, gridItems.getValue("grid-header").contentRevision)
        assertSame(gridHeaderType, gridItems.getValue("grid-header").contentType)

        val page = HorizontalPagerScope().apply {
            Page("page", pageRevision, contentType = pageType) { Text("Page") }
        }.build().single()
        assertSame(pageRevision, page.contentRevision)
        assertSame(pageType, page.contentType)

        val tab = TabRowScope().apply {
            Tab("tab", tabRevision) { Text("Tab") }
        }.build().single()
        assertSame(tabRevision, tab.contentRevision)
    }

    private class RevisionSentinel(val name: String)

    private class ContentTypeSentinel(val name: String)
}
