package com.viewcompose.ui.node

/**
 * Describes one stable destination item in a navigation bar.
 *
 * @property key semantic destination identity that remains stable across label and locale changes
 * @property label user-visible localized destination label
 * @property icon drawable resource used while unselected
 * @property selectedIcon optional drawable resource used while selected; [icon] is the fallback
 * @property badgeCount optional non-negative renderer-formatted badge value, or `null` for no badge
 * @property enabled whether this destination accepts selection input
 * @throws IllegalArgumentException when [badgeCount] is negative
 */
data class NavigationBarItem(
    val key: Any,
    val label: String,
    val icon: ImageSource.Resource,
    val selectedIcon: ImageSource.Resource? = null,
    val badgeCount: Int? = null,
    val enabled: Boolean = true,
) {
    init {
        require(badgeCount == null || badgeCount >= 0) {
            "NavigationBar badgeCount must be non-negative."
        }
    }
}
