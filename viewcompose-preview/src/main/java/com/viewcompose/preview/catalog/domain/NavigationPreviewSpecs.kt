package com.viewcompose.preview.catalog.domain

import com.viewcompose.preview.catalog.model.PreviewDomain
import com.viewcompose.preview.catalog.model.PreviewSpec
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.modifier.sharedBounds
import com.viewcompose.ui.modifier.sharedElement
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.BottomAppBar
import com.viewcompose.ui.foundation.IconButton
import com.viewcompose.ui.foundation.NavigationBar
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.TopAppBar
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.shared.SharedContentKey

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
                    Item("home", "首页", ImageSource.Resource(android.R.drawable.ic_menu_view))
                    Item("discover", "发现", ImageSource.Resource(android.R.drawable.ic_menu_compass))
                    Item("profile", "我的", ImageSource.Resource(android.R.drawable.ic_menu_myplaces))
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
        PreviewSpec(
            id = "navigation-shared-content-endpoints",
            title = "Shared element / bounds endpoints",
            domain = PreviewDomain.Navigation,
            content = {
                val boundsKey = SharedContentKey("preview-card")
                val elementKey = SharedContentKey("preview-chip")
                Text(text = "Source destination")
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .margin(vertical = 6.dp)
                        .padding(12.dp)
                        .sharedBounds(boundsKey),
                ) {
                    Text(text = "Compact shared bounds")
                }
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .sharedElement(elementKey),
                ) {
                    Text(text = "Shared chip")
                }
                Text(
                    text = "Target destination",
                    modifier = Modifier.margin(top = 18.dp),
                )
                Surface(
                    variant = SurfaceVariant.Variant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .margin(horizontal = 24.dp, vertical = 6.dp)
                        .padding(12.dp)
                        .sharedBounds(boundsKey),
                ) {
                    Text(text = "Expanded shared bounds")
                }
                Surface(
                    modifier = Modifier
                        .margin(left = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .sharedElement(elementKey),
                ) {
                    Text(text = "Shared chip")
                }
            },
        ),
    )
}
