package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.NavigationBarItem
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * Immutable renderer properties for a bottom navigation bar.
 *
 * @property items ordered destination models
 * @property selectedIndex externally selected destination index
 * @property onItemSelected callback receiving an accepted destination index
 * @property containerColor bar surface color
 * @property selectedIconColor selected item icon tint
 * @property unselectedIconColor unselected item icon tint
 * @property selectedLabelColor selected item label color
 * @property unselectedLabelColor unselected item label color
 * @property indicatorColor selected item indicator color
 * @property rippleColor pressed-state ripple color
 * @property iconSize requested icon width and height
 * @property labelSizeSp label size in scale-independent pixels
 * @property labelFontWeight optional platform label font weight
 * @property labelFontFamily optional renderer-compatible label font family
 * @property labelLetterSpacingEm optional label letter spacing in em units
 * @property labelLineHeightSp optional label line height
 * @property labelIncludeFontPadding whether platform font top and bottom padding is included
 * @property badgeColor badge background color
 * @property badgeTextColor badge text color
 * @throws IllegalArgumentException for duplicate item keys or a selected index outside [items]
 */
data class NavigationBarNodeProps(
    val items: List<NavigationBarItem>,
    val selectedIndex: Int,
    val onItemSelected: ((Int) -> Unit)?,
    val containerColor: Int,
    val selectedIconColor: Int,
    val unselectedIconColor: Int,
    val selectedLabelColor: Int,
    val unselectedLabelColor: Int,
    val indicatorColor: Int,
    val rippleColor: Int,
    val iconSize: UiDp,
    val labelSizeSp: UiSp,
    val labelFontWeight: Int? = null,
    val labelFontFamily: UiFontFamily? = null,
    val labelLetterSpacingEm: Float? = null,
    val labelLineHeightSp: UiSp? = null,
    val labelIncludeFontPadding: Boolean = false,
    val badgeColor: Int,
    val badgeTextColor: Int,
) : NodeSpec {
    init {
        require(items.map(NavigationBarItem::key).toSet().size == items.size) {
            "NavigationBar item keys must be unique."
        }
        require(selectedIndex.isValidSelectionFor(items)) {
            "NavigationBar selectedIndex must identify an item, or be -1 for an empty bar."
        }
    }
}

private fun Int.isValidSelectionFor(items: List<NavigationBarItem>): Boolean {
    return if (items.isEmpty()) this == -1 else this in items.indices
}
