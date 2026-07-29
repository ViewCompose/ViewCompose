package com.viewcompose.widget.core

import com.viewcompose.ui.unit.UiDensity
/**
 * Button 组件尺寸 token。
 * Size tokens for Button components.
 */
data class UiButtonSizing(
    val compactHeight: Int,
    val mediumHeight: Int,
    val largeHeight: Int,
    val compactHorizontalPadding: Int,
    val mediumHorizontalPadding: Int,
    val largeHorizontalPadding: Int,
    val compactVerticalPadding: Int,
    val mediumVerticalPadding: Int,
    val largeVerticalPadding: Int,
)

/**
 * TextField 组件尺寸 token。
 * Size tokens for TextField components.
 */
data class UiTextFieldSizing(
    val compactHeight: Int,
    val mediumHeight: Int,
    val largeHeight: Int,
    val compactHorizontalPadding: Int,
    val mediumHorizontalPadding: Int,
    val largeHorizontalPadding: Int,
    val compactVerticalPadding: Int,
    val mediumVerticalPadding: Int,
    val largeVerticalPadding: Int,
)

/**
 * SegmentedControl 组件尺寸 token。
 * Size tokens for SegmentedControl components.
 */
data class UiSegmentedControlSizing(
    val compactHeight: Int,
    val mediumHeight: Int,
    val largeHeight: Int,
    val compactHorizontalPadding: Int,
    val mediumHorizontalPadding: Int,
    val largeHorizontalPadding: Int,
    val compactVerticalPadding: Int,
    val mediumVerticalPadding: Int,
    val largeVerticalPadding: Int,
)

/**
 * ProgressIndicator 组件尺寸 token。
 * Size tokens for ProgressIndicator components.
 */
data class UiProgressIndicatorSizing(
    val linearTrackThickness: Int,
    val circularSize: Int,
    val circularTrackThickness: Int,
)

/**
 * Floating action button 尺寸 token。
 * Size tokens for floating action buttons.
 */
data class UiFabSizing(
    val smallSize: Int,
    val mediumSize: Int,
    val largeSize: Int,
    val smallIconSize: Int,
    val mediumIconSize: Int,
    val largeIconSize: Int,
    val elevation: Int,
    val extendedHeight: Int,
    val extendedHorizontalPadding: Int,
    val extendedIconSpacing: Int,
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
    val height: Int,
    val horizontalPadding: Int,
    val leadingIconPadding: Int,
    val iconSize: Int,
    val trailingIconSize: Int,
    val iconSpacing: Int,
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
    val height: Int,
    val horizontalPadding: Int,
    val iconSize: Int,
    val iconSpacing: Int,
    val elevation: Int,
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
    val height: Int,
    val iconSize: Int,
    val labelSizeSp: Int,
) {
    companion object {
        fun default(): UiNavigationBarSizing = UiNavigationBarSizing(
            height = 80.dp,
            iconSize = 24.dp,
            labelSizeSp = 12,
        )
    }
}

/**
 * AppBar 组件尺寸 token。
 * Size tokens for AppBar components.
 */
data class UiAppBarSizing(
    val topHeight: Int,
    val topHorizontalPadding: Int,
    val topTitleStartPadding: Int,
    val bottomHeight: Int,
    val bottomHorizontalPadding: Int,
    val bottomElevation: Int,
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
    val minHeight: Int,
    val horizontalPadding: Int,
    val verticalPadding: Int,
    val leadingTrailingSpacing: Int,
    val textSpacing: Int,
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
    val elevation: Int,
    val minWidth: Int,
    val verticalPadding: Int,
    val itemHeight: Int,
    val itemHorizontalPadding: Int,
    val iconSize: Int,
    val iconToTextSpacing: Int,
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
    val horizontalPadding: Int,
    val verticalPadding: Int,
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
    val dotSize: Int,
    val pillHeight: Int,
    val pillMinWidth: Int,
    val pillHorizontalPadding: Int,
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
