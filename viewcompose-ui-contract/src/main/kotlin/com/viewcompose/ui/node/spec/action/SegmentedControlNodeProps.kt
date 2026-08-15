package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.SegmentedControlItem
import com.viewcompose.ui.node.UiStateLayerColors
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * Immutable renderer properties for a single-selection segmented control.
 *
 * @property items ordered segment models
 * @property selectedIndex selected item index, or `-1` only when [items] is empty
 * @property onSelectionChange callback receiving an accepted item index
 * @property enabled whether the control and its items accept input
 * @property backgroundColor unselected track color
 * @property indicatorColor selected-segment indicator color
 * @property shape outline shared by track and indicator
 * @property textColor unselected label color
 * @property selectedTextColor selected label color
 * @property unselectedStateLayerColors interaction colors based on the enabled unselected content role
 * @property selectedStateLayerColors interaction colors based on the enabled selected content role
 * @property textSizeSp label size in scale-independent pixels
 * @property fontWeight optional platform font weight override
 * @property fontFamily optional renderer-compatible font family
 * @property letterSpacingEm optional label letter spacing in em units
 * @property lineHeightSp optional label line height
 * @property includeFontPadding whether platform font top and bottom padding is included
 * @property paddingHorizontal horizontal padding inside each segment
 * @property paddingVertical vertical padding inside each segment
 * @throws IllegalArgumentException for duplicate item keys or a selected index outside [items]
 */
data class SegmentedControlNodeProps(
    val items: List<SegmentedControlItem>,
    val selectedIndex: Int,
    val onSelectionChange: ((Int) -> Unit)?,
    val enabled: Boolean,
    val backgroundColor: Int,
    val indicatorColor: Int,
    val shape: UiShape,
    val textColor: Int,
    val selectedTextColor: Int,
    val textSizeSp: UiSp,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean = false,
    val paddingHorizontal: UiDp,
    val paddingVertical: UiDp,
    val unselectedStateLayerColors: UiStateLayerColors,
    val selectedStateLayerColors: UiStateLayerColors,
) : NodeSpec {
    init {
        require(items.map(SegmentedControlItem::key).toSet().size == items.size) {
            "SegmentedControl item keys must be unique."
        }
        require(selectedIndex.isValidSelectionFor(items)) {
            "SegmentedControl selectedIndex must identify an item, or be -1 when empty."
        }
    }
}

private fun Int.isValidSelectionFor(items: List<SegmentedControlItem>): Boolean {
    return if (items.isEmpty()) this == -1 else this in items.indices
}
