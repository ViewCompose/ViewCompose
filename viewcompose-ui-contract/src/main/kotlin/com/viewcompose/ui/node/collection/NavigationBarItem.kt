package com.viewcompose.ui.node

/**
 * Describes one stable destination item in a navigation bar.
 *
 * @property label user-visible localized destination label
 * @property icon drawable resource used while unselected
 * @property selectedIcon optional drawable resource used while selected; [icon] is the fallback
 * @property badgeCount optional renderer-formatted badge value, or `null` for no badge
 * @property key semantic destination identity; defaults to [label]
 */
data class NavigationBarItem(
    val label: String,
    val icon: ImageSource.Resource,
    val selectedIcon: ImageSource.Resource? = null,
    val badgeCount: Int? = null,
    val key: Any? = label,
)
