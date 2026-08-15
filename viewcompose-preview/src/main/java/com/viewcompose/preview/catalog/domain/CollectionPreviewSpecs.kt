package com.viewcompose.preview.catalog.domain

import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.HorizontalPager
import com.viewcompose.ui.foundation.LazyColumn
import com.viewcompose.ui.foundation.LazyVerticalGrid
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.TabRow
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.VerticalPager
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember

/**
 * 提供集合类组件的预览规格，覆盖列表、网格和导航栏的 catalog 示例。
 * Provides preview specs for collection components, covering list, grid, and navigation bar catalog samples.
 */
internal object CollectionPreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "collection-lazy-column",
            title = "LazyColumn",
            domain = PreviewDomain.Collection,
            content = {
                LazyColumn(
                    spacing = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(
                        items = (1..8).toList(),
                        key = { index -> index },
                    ) { index ->
                        Surface(
                            variant = SurfaceVariant.Variant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(text = "List Item $index")
                        }
                    }
                }
            },
        ),
        PreviewSpec(
            id = "collection-grid-pagers-tabs",
            title = "Grid + Pager + TabRow",
            domain = PreviewDomain.Collection,
            content = {
                val horizontalPageState = remember { mutableStateOf(0) }
                val verticalPageState = remember { mutableStateOf(0) }
                val tabIndexState = remember { mutableStateOf(0) }
                Column(
                    spacing = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    LazyVerticalGrid(
                        cells = com.viewcompose.ui.node.policy.GridCells.Fixed(3),
                        horizontalSpacing = 8.dp,
                        verticalSpacing = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    ) {
                        items(
                            items = (1..6).toList(),
                            key = { index -> index },
                        ) { index ->
                            Surface(
                                variant = SurfaceVariant.Variant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp),
                            ) {
                                Text(text = "G$index")
                            }
                        }
                    }
                    HorizontalPager(
                        currentPage = horizontalPageState.value,
                        onPageChanged = { horizontalPageState.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                    ) {
                        Page(key = "page-1") { Text(text = "Horizontal Page 1") }
                        Page(key = "page-2") { Text(text = "Horizontal Page 2") }
                    }
                    VerticalPager(
                        currentPage = verticalPageState.value,
                        onPageChanged = { verticalPageState.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp),
                    ) {
                        Page(key = "v-page-1") { Text(text = "Vertical Page 1") }
                        Page(key = "v-page-2") { Text(text = "Vertical Page 2") }
                    }
                    TabRow(
                        selectedIndex = tabIndexState.value,
                        onTabSelected = { tabIndexState.value = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Tab(key = "tab-1") { selected ->
                            Text(text = if (selected) "Tab A*" else "Tab A")
                        }
                        Tab(key = "tab-2") { selected ->
                            Text(text = if (selected) "Tab B*" else "Tab B")
                        }
                    }
                }
            },
        ),
    )
}
