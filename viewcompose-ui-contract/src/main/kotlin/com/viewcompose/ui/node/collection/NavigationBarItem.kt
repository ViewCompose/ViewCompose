package com.viewcompose.ui.node

/**
 * NavigationBar 的单个 item 描述。
 * Descriptor for one NavigationBar item.
 */
data class NavigationBarItem(
    val label: String,
    val icon: ImageSource.Resource,
    val selectedIcon: ImageSource.Resource? = null,
    val badgeCount: Int? = null,
    val key: Any? = label,
)
