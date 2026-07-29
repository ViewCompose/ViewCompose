package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp
/**
 * Button 组件尺寸 token。
 * Size tokens for Button components.
 */
data class UiButtonSizing(
    val compactHeight: UiDp,
    val mediumHeight: UiDp,
    val largeHeight: UiDp,
    val compactHorizontalPadding: UiDp,
    val mediumHorizontalPadding: UiDp,
    val largeHorizontalPadding: UiDp,
    val compactVerticalPadding: UiDp,
    val mediumVerticalPadding: UiDp,
    val largeVerticalPadding: UiDp,
)

/**
 * TextField 组件尺寸 token。
 * Size tokens for TextField components.
 */
data class UiTextFieldSizing(
    val compactHeight: UiDp,
    val mediumHeight: UiDp,
    val largeHeight: UiDp,
    val compactHorizontalPadding: UiDp,
    val mediumHorizontalPadding: UiDp,
    val largeHorizontalPadding: UiDp,
    val compactVerticalPadding: UiDp,
    val mediumVerticalPadding: UiDp,
    val largeVerticalPadding: UiDp,
)

/**
 * SegmentedControl 组件尺寸 token。
 * Size tokens for SegmentedControl components.
 */
data class UiSegmentedControlSizing(
    val compactHeight: UiDp,
    val mediumHeight: UiDp,
    val largeHeight: UiDp,
    val compactHorizontalPadding: UiDp,
    val mediumHorizontalPadding: UiDp,
    val largeHorizontalPadding: UiDp,
    val compactVerticalPadding: UiDp,
    val mediumVerticalPadding: UiDp,
    val largeVerticalPadding: UiDp,
)

/**
 * ProgressIndicator 组件尺寸 token。
 * Size tokens for ProgressIndicator components.
 */
data class UiProgressIndicatorSizing(
    val linearTrackThickness: UiDp,
    val circularSize: UiDp,
    val circularTrackThickness: UiDp,
)

/**
 * Floating action button 尺寸 token。
 * Size tokens for floating action buttons.
 */
data class UiFabSizing(
    val smallSize: UiDp,
    val mediumSize: UiDp,
    val largeSize: UiDp,
    val smallIconSize: UiDp,
    val mediumIconSize: UiDp,
    val largeIconSize: UiDp,
    val elevation: UiDp,
    val extendedHeight: UiDp,
    val extendedHorizontalPadding: UiDp,
    val extendedIconSpacing: UiDp,
) {
    companion object {
        fun default(): UiFabSizing = UiFabSizing(
            smallSize = 40.dp,
            mediumSize = 56.dp,
            largeSize = 96.dp,
            smallIconSize = 20.dp,
            mediumIconSize = 24.dp,
            largeIconSize = 36.dp,
            elevation = 6.dp,
            extendedHeight = 56.dp,
            extendedHorizontalPadding = 16.dp,
            extendedIconSpacing = 8.dp,
        )
    }
}

/**
 * Chip 组件尺寸 token。
 * Size tokens for Chip components.
 */
data class UiChipSizing(
    val height: UiDp,
    val horizontalPadding: UiDp,
    val leadingIconPadding: UiDp,
    val iconSize: UiDp,
    val trailingIconSize: UiDp,
    val iconSpacing: UiDp,
) {
    companion object {
        fun default(): UiChipSizing = UiChipSizing(
            height = 32.dp,
            horizontalPadding = 16.dp,
            leadingIconPadding = 8.dp,
            iconSize = 18.dp,
            trailingIconSize = 18.dp,
            iconSpacing = 8.dp,
        )
    }
}

/**
 * SearchBar 组件尺寸 token。
 * Size tokens for SearchBar components.
 */
data class UiSearchBarSizing(
    val height: UiDp,
    val horizontalPadding: UiDp,
    val iconSize: UiDp,
    val iconSpacing: UiDp,
    val elevation: UiDp,
) {
    companion object {
        fun default(): UiSearchBarSizing = UiSearchBarSizing(
            height = 56.dp,
            horizontalPadding = 16.dp,
            iconSize = 24.dp,
            iconSpacing = 16.dp,
            elevation = 2.dp,
        )
    }
}

/**
 * NavigationBar 组件尺寸 token。
 * Size tokens for NavigationBar components.
 */
data class UiNavigationBarSizing(
    val height: UiDp,
    val iconSize: UiDp,
    val labelSizeSp: UiSp,
) {
    companion object {
        fun default(): UiNavigationBarSizing = UiNavigationBarSizing(
            height = 80.dp,
            iconSize = 24.dp,
            labelSizeSp = 12.sp,
        )
    }
}

/**
 * AppBar 组件尺寸 token。
 * Size tokens for AppBar components.
 */
data class UiAppBarSizing(
    val topHeight: UiDp,
    val topHorizontalPadding: UiDp,
    val topTitleStartPadding: UiDp,
    val bottomHeight: UiDp,
    val bottomHorizontalPadding: UiDp,
    val bottomElevation: UiDp,
) {
    companion object {
        fun default(): UiAppBarSizing = UiAppBarSizing(
            topHeight = 64.dp,
            topHorizontalPadding = 4.dp,
            topTitleStartPadding = 16.dp,
            bottomHeight = 80.dp,
            bottomHorizontalPadding = 16.dp,
            bottomElevation = 3.dp,
        )
    }
}

/**
 * List item 组件尺寸 token。
 * Size tokens for list item components.
 */
data class UiListItemSizing(
    val minHeight: UiDp,
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
    val leadingTrailingSpacing: UiDp,
    val textSpacing: UiDp,
) {
    companion object {
        fun default(): UiListItemSizing = UiListItemSizing(
            minHeight = 56.dp,
            horizontalPadding = 16.dp,
            verticalPadding = 8.dp,
            leadingTrailingSpacing = 16.dp,
            textSpacing = 2.dp,
        )
    }
}

/**
 * Menu 组件尺寸 token。
 * Size tokens for Menu components.
 */
data class UiMenuSizing(
    val elevation: UiDp,
    val minWidth: UiDp,
    val verticalPadding: UiDp,
    val itemHeight: UiDp,
    val itemHorizontalPadding: UiDp,
    val iconSize: UiDp,
    val iconToTextSpacing: UiDp,
) {
    companion object {
        fun default(): UiMenuSizing = UiMenuSizing(
            elevation = 3.dp,
            minWidth = 112.dp,
            verticalPadding = 8.dp,
            itemHeight = 48.dp,
            itemHorizontalPadding = 12.dp,
            iconSize = 24.dp,
            iconToTextSpacing = 12.dp,
        )
    }
}

/**
 * Tooltip 组件尺寸 token。
 * Size tokens for Tooltip components.
 */
data class UiTooltipSizing(
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
) {
    companion object {
        fun default(): UiTooltipSizing = UiTooltipSizing(
            horizontalPadding = 8.dp,
            verticalPadding = 4.dp,
        )
    }
}

/**
 * Badge 组件尺寸 token。
 * Size tokens for Badge components.
 */
data class UiBadgeSizing(
    val dotSize: UiDp,
    val pillHeight: UiDp,
    val pillMinWidth: UiDp,
    val pillHorizontalPadding: UiDp,
) {
    companion object {
        fun default(): UiBadgeSizing = UiBadgeSizing(
            dotSize = 8.dp,
            pillHeight = 16.dp,
            pillMinWidth = 16.dp,
            pillHorizontalPadding = 4.dp,
        )
    }
}

/**
 * 所有核心组件尺寸 token 的聚合。
 * Aggregate size tokens for all core components.
 */
data class UiControlSizing(
    val button: UiButtonSizing,
    val textField: UiTextFieldSizing,
    val segmentedControl: UiSegmentedControlSizing,
    val progressIndicator: UiProgressIndicatorSizing,
    val fab: UiFabSizing = UiFabSizing.default(),
    val chip: UiChipSizing = UiChipSizing.default(),
    val searchBar: UiSearchBarSizing = UiSearchBarSizing.default(),
    val navigationBar: UiNavigationBarSizing = UiNavigationBarSizing.default(),
    val appBar: UiAppBarSizing = UiAppBarSizing.default(),
    val listItem: UiListItemSizing = UiListItemSizing.default(),
    val menu: UiMenuSizing = UiMenuSizing.default(),
    val tooltip: UiTooltipSizing = UiTooltipSizing.default(),
    val badge: UiBadgeSizing = UiBadgeSizing.default(),
)
