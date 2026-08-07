package com.viewcompose.ui.foundation

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * Defines effective target and visible-container dimensions for each Button size tier.
 *
 * The height fields are minimum layout and semantic target heights. The visual-height fields
 * describe the centered background, border, shape, and ripple container inside that target. A
 * visual height defaults to its matching target height, so non-Material themes keep the existing
 * single-bound surface unless they opt into a smaller visible container. Values are immutable and
 * are not validated; renderers clamp the requested visual height into the effective bounds.
 *
 * @property compactHeight minimum height used by compact buttons
 * @property mediumHeight minimum height used by medium buttons
 * @property largeHeight minimum height used by large buttons
 * @property compactHorizontalPadding start and end content padding for compact buttons
 * @property mediumHorizontalPadding start and end content padding for medium buttons
 * @property largeHorizontalPadding start and end content padding for large buttons
 * @property compactVerticalPadding top and bottom content padding for compact buttons
 * @property mediumVerticalPadding top and bottom content padding for medium buttons
 * @property largeVerticalPadding top and bottom content padding for large buttons
 * @property compactVisualHeight visible container height inside the compact target
 * @property mediumVisualHeight visible container height inside the medium target
 * @property largeVisualHeight visible container height inside the large target
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
    val compactVisualHeight: UiDp = compactHeight,
    val mediumVisualHeight: UiDp = mediumHeight,
    val largeVisualHeight: UiDp = largeHeight,
)

/**
 * Defines compact, medium, and large text-field dimensions.
 *
 * @property compactHeight minimum height used by compact fields
 * @property mediumHeight minimum height used by medium fields
 * @property largeHeight minimum height used by large fields
 * @property compactHorizontalPadding start and end content padding for compact fields
 * @property mediumHorizontalPadding start and end content padding for medium fields
 * @property largeHorizontalPadding start and end content padding for large fields
 * @property compactVerticalPadding top and bottom content padding for compact fields
 * @property mediumVerticalPadding top and bottom content padding for medium fields
 * @property largeVerticalPadding top and bottom content padding for large fields
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
 * Defines compact, medium, and large segmented-control dimensions.
 *
 * @property compactHeight minimum height used by compact controls
 * @property mediumHeight minimum height used by medium controls
 * @property largeHeight minimum height used by large controls
 * @property compactHorizontalPadding horizontal segment content padding for compact controls
 * @property mediumHorizontalPadding horizontal segment content padding for medium controls
 * @property largeHorizontalPadding horizontal segment content padding for large controls
 * @property compactVerticalPadding vertical segment content padding for compact controls
 * @property mediumVerticalPadding vertical segment content padding for medium controls
 * @property largeVerticalPadding vertical segment content padding for large controls
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
 * Defines dimensions shared by linear and circular progress indicators.
 *
 * @property linearTrackThickness cross-axis thickness of a linear track
 * @property circularSize width and height of a circular indicator
 * @property circularTrackThickness stroke thickness of a circular track
 */
data class UiProgressIndicatorSizing(
    val linearTrackThickness: UiDp,
    val circularSize: UiDp,
    val circularTrackThickness: UiDp,
)

/**
 * Defines dimensions and elevation for floating action buttons.
 *
 * @property smallSize width and height of a small FAB
 * @property mediumSize width and height of a standard FAB
 * @property largeSize width and height of a large FAB
 * @property smallIconSize icon size inside a small FAB
 * @property mediumIconSize icon size inside a standard FAB
 * @property largeIconSize icon size inside a large FAB
 * @property elevation resting FAB elevation
 * @property extendedHeight minimum height of an extended FAB
 * @property extendedHorizontalPadding horizontal content padding of an extended FAB
 * @property extendedIconSpacing spacing between extended FAB icon and label
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
    /** Creates the framework baseline FAB dimensions. */
    companion object {
        /** Returns a new immutable framework baseline. */
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
 * Defines dimensions for compact labeled chips.
 *
 * @property height minimum chip height
 * @property horizontalPadding default label-side padding when no icon changes the leading edge
 * @property leadingIconPadding padding before a leading icon
 * @property iconSize leading icon width and height
 * @property trailingIconSize trailing icon width and height
 * @property iconSpacing spacing between an icon and label
 */
data class UiChipSizing(
    val height: UiDp,
    val horizontalPadding: UiDp,
    val leadingIconPadding: UiDp,
    val iconSize: UiDp,
    val trailingIconSize: UiDp,
    val iconSpacing: UiDp,
) {
    /** Creates the framework baseline chip dimensions. */
    companion object {
        /** Returns a new immutable framework baseline. */
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
 * Defines search-bar dimensions and resting elevation.
 *
 * @property height minimum search-bar height
 * @property horizontalPadding start and end content padding
 * @property iconSize leading and trailing icon size
 * @property iconSpacing spacing between an icon and editable text
 * @property elevation resting search-bar elevation
 */
data class UiSearchBarSizing(
    val height: UiDp,
    val horizontalPadding: UiDp,
    val iconSize: UiDp,
    val iconSpacing: UiDp,
    val elevation: UiDp,
) {
    /** Creates the framework baseline search-bar dimensions. */
    companion object {
        /** Returns a new immutable framework baseline. */
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
 * Defines bottom navigation-bar dimensions.
 *
 * @property height minimum bar height
 * @property iconSize destination icon width and height
 * @property labelSizeSp destination label text size
 */
data class UiNavigationBarSizing(
    val height: UiDp,
    val iconSize: UiDp,
    val labelSizeSp: UiSp,
) {
    /** Creates the framework baseline navigation-bar dimensions. */
    companion object {
        /** Returns a new immutable framework baseline. */
        fun default(): UiNavigationBarSizing = UiNavigationBarSizing(
            height = 80.dp,
            iconSize = 24.dp,
            labelSizeSp = 12.sp,
        )
    }
}

/**
 * Defines top and bottom app-bar dimensions.
 *
 * @property topHeight minimum top app-bar height
 * @property topHorizontalPadding outer horizontal top-bar padding
 * @property topTitleStartPadding spacing between navigation content and title
 * @property bottomHeight minimum bottom app-bar height
 * @property bottomHorizontalPadding outer horizontal bottom-bar padding
 * @property bottomElevation resting bottom-bar elevation
 */
data class UiAppBarSizing(
    val topHeight: UiDp,
    val topHorizontalPadding: UiDp,
    val topTitleStartPadding: UiDp,
    val bottomHeight: UiDp,
    val bottomHorizontalPadding: UiDp,
    val bottomElevation: UiDp,
) {
    /** Creates the framework baseline app-bar dimensions. */
    companion object {
        /** Returns a new immutable framework baseline. */
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
 * Defines list-item content spacing and minimum height.
 *
 * @property minHeight minimum item height before content expansion
 * @property horizontalPadding start and end item padding
 * @property verticalPadding top and bottom item padding
 * @property leadingTrailingSpacing spacing from leading/trailing content to text
 * @property textSpacing vertical spacing between headline, supporting, and overline text
 */
data class UiListItemSizing(
    val minHeight: UiDp,
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
    val leadingTrailingSpacing: UiDp,
    val textSpacing: UiDp,
) {
    /** Creates the framework baseline list-item dimensions. */
    companion object {
        /** Returns a new immutable framework baseline. */
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
 * Defines menu surface, item, and icon dimensions.
 *
 * @property elevation resting menu surface elevation
 * @property minWidth minimum menu surface width
 * @property verticalPadding padding before the first and after the last item
 * @property itemHeight minimum menu-item height
 * @property itemHorizontalPadding start and end menu-item padding
 * @property iconSize menu-item icon width and height
 * @property iconToTextSpacing spacing between an icon and label
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
    /** Creates the framework baseline menu dimensions. */
    companion object {
        /** Returns a new immutable framework baseline. */
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
 * Defines padding inside a tooltip surface.
 *
 * @property horizontalPadding start and end tooltip content padding
 * @property verticalPadding top and bottom tooltip content padding
 */
data class UiTooltipSizing(
    val horizontalPadding: UiDp,
    val verticalPadding: UiDp,
) {
    /** Creates the framework baseline tooltip dimensions. */
    companion object {
        /** Returns a new immutable framework baseline. */
        fun default(): UiTooltipSizing = UiTooltipSizing(
            horizontalPadding = 8.dp,
            verticalPadding = 4.dp,
        )
    }
}

/**
 * Defines dot and labeled badge dimensions.
 *
 * @property dotSize width and height of a badge without text
 * @property pillHeight minimum height of a labeled badge
 * @property pillMinWidth minimum width of a labeled badge
 * @property pillHorizontalPadding start and end padding around badge text
 */
data class UiBadgeSizing(
    val dotSize: UiDp,
    val pillHeight: UiDp,
    val pillMinWidth: UiDp,
    val pillHorizontalPadding: UiDp,
) {
    /** Creates the framework baseline badge dimensions. */
    companion object {
        /** Returns a new immutable framework baseline. */
        fun default(): UiBadgeSizing = UiBadgeSizing(
            dotSize = 8.dp,
            pillHeight = 16.dp,
            pillMinWidth = 16.dp,
            pillHorizontalPadding = 4.dp,
        )
    }
}

/**
 * Aggregates all core component sizing token families.
 *
 * @property button button size tiers
 * @property textField text-field size tiers
 * @property segmentedControl segmented-control size tiers
 * @property progressIndicator progress-indicator dimensions
 * @property fab floating action button dimensions
 * @property chip chip dimensions
 * @property searchBar search-bar dimensions
 * @property navigationBar bottom navigation-bar dimensions
 * @property appBar top and bottom app-bar dimensions
 * @property listItem list-item dimensions
 * @property menu menu dimensions
 * @property tooltip tooltip dimensions
 * @property badge badge dimensions
 * @property minimumInteractiveHeight minimum effective height for compact interactive controls;
 * zero preserves each native control's intrinsic measurement
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
    val minimumInteractiveHeight: UiDp = UiDp.Zero,
)
