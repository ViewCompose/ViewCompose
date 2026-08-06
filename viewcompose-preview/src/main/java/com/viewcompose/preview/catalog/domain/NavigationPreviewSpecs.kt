package com.viewcompose.preview.catalog.domain

import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.BottomAppBar
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TopAppBar
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.node.ImageSource

internal object NavigationPreviewSpecs {
    val all: List<PreviewSpec> = listOf(
        PreviewSpec(
            id = "navigation-app-bars",
            title = "Top/Bottom App Bar + NavigationBar",
            domain = PreviewDomain.Navigation,
            content = {
                val selectedNavState = remember { mutableStateOf(1) }
                TopAppBar(
                    title = "组件预览",
                    navigationIcon = {
                        IconButton(
                            icon = ImageSource.Resource(android.R.drawable.ic_menu_sort_by_size),
                            contentDescription = "菜单",
                            onClick = {},
                        )
                    },
                    actions = {
                        IconButton(
                            icon = ImageSource.Resource(android.R.drawable.ic_menu_search),
                            contentDescription = "搜索",
                            onClick = {},
                        )
                    },
                )
                NavigationBar(
                    selectedIndex = selectedNavState.value,
                    onItemSelected = { selectedNavState.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .margin(vertical = 8.dp),
                ) {
                    Item("首页", ImageSource.Resource(android.R.drawable.ic_menu_view))
                    Item("发现", ImageSource.Resource(android.R.drawable.ic_menu_compass))
                    Item("我的", ImageSource.Resource(android.R.drawable.ic_menu_myplaces))
                }
                BottomAppBar {
                    IconButton(
                        icon = ImageSource.Resource(android.R.drawable.ic_menu_add),
                        contentDescription = "新增",
                        onClick = {},
                    )
                    Text(text = "BottomAppBar")
                }
            },
        ),
    )
}
