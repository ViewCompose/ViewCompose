package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

object UiShapeDefaults {
    fun default(): UiShapes {
        return UiShapes(
            small = UiShape.rounded(14.dp),
            medium = UiShape.rounded(20.dp),
        )
    }
}

object UiControlSizeDefaults {
    fun default(): UiControlSizing {
        return UiControlSizing(
            button = UiButtonSizing(
                compactHeight = 36.dp,
                mediumHeight = 44.dp,
                largeHeight = 52.dp,
                compactHorizontalPadding = 12.dp,
                mediumHorizontalPadding = 16.dp,
                largeHorizontalPadding = 20.dp,
                compactVerticalPadding = 8.dp,
                mediumVerticalPadding = 10.dp,
                largeVerticalPadding = 12.dp,
            ),
            textField = UiTextFieldSizing(
                compactHeight = 40.dp,
                mediumHeight = 48.dp,
                largeHeight = 56.dp,
                compactHorizontalPadding = 12.dp,
                mediumHorizontalPadding = 14.dp,
                largeHorizontalPadding = 16.dp,
                compactVerticalPadding = 8.dp,
                mediumVerticalPadding = 10.dp,
                largeVerticalPadding = 12.dp,
            ),
            segmentedControl = UiSegmentedControlSizing(
                compactHeight = 36.dp,
                mediumHeight = 42.dp,
                largeHeight = 48.dp,
                compactHorizontalPadding = 12.dp,
                mediumHorizontalPadding = 14.dp,
                largeHorizontalPadding = 18.dp,
                compactVerticalPadding = 6.dp,
                mediumVerticalPadding = 8.dp,
                largeVerticalPadding = 10.dp,
            ),
            progressIndicator = UiProgressIndicatorSizing(
                linearTrackThickness = 6.dp,
                circularSize = 32.dp,
                circularTrackThickness = 4.dp,
            ),
            fab = UiFabSizing.default(),
            chip = UiChipSizing.default(),
            searchBar = UiSearchBarSizing.default(),
            navigationBar = UiNavigationBarSizing.default(),
            appBar = UiAppBarSizing.default(),
            listItem = UiListItemSizing.default(),
            menu = UiMenuSizing.default(),
            tooltip = UiTooltipSizing.default(),
            badge = UiBadgeSizing.default(),
        )
    }
}

object UiOverlayDefaults {
    fun default(): UiOverlays {
        return UiOverlays(
            scrimOpacity = 0.32f,
        )
    }
}

object UiThemeDefaults {
    fun light(): UiThemeTokens {
        return UiThemeTokens(
            colors = UiColors(
                background = 0xFFF4F1EA.toInt(),
                surface = 0xFFE6D9C6.toInt(),
                surfaceVariant = 0xFFFDECC8.toInt(),
                onSurface = 0xFF2F241B.toInt(),
                onSurfaceVariant = 0xFF6A5A4A.toInt(),
                primary = 0xFFBFD8A6.toInt(),
                onPrimary = 0xFF1E2B14.toInt(),
                primaryContainer = 0xFFD7E8C5.toInt(),
                onPrimaryContainer = 0xFF1A2712.toInt(),
                secondary = 0xFFD6C6F0.toInt(),
                onSecondary = 0xFF2E2142.toInt(),
                secondaryContainer = 0xFFE9DEFA.toInt(),
                onSecondaryContainer = 0xFF2A1E3D.toInt(),
                error = 0xFFB3261E.toInt(),
                onError = 0xFFFFFFFF.toInt(),
                errorContainer = 0xFFF9DEDC.toInt(),
                onErrorContainer = 0xFF410E0B.toInt(),
                success = 0xFF2E7D32.toInt(),
                warning = 0xFFF57C00.toInt(),
                info = 0xFF1565C0.toInt(),
                outline = 0xFF8A7F72.toInt(),
                outlineVariant = 0xFFD8CCBA.toInt(),
                surfaceTint = 0xFFBFD8A6.toInt(),
                inverseSurface = 0xFF342A22.toInt(),
                inverseOnSurface = 0xFFF9EFE3.toInt(),
                ripple = 0x22302A24,
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 24.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 16.sp),
                labelMedium = UiTextStyle(fontSizeSp = 14.sp),
            ),
            metadata = UiThemeMetadata(
                origin = UiThemeOrigin.FrameworkDefault,
                isDark = false,
            ),
        )
    }

    fun dark(): UiThemeTokens {
        return UiThemeTokens(
            colors = UiColors(
                background = 0xFF1F1B18.toInt(),
                surface = 0xFF2C2621.toInt(),
                surfaceVariant = 0xFF3A332D.toInt(),
                onSurface = 0xFFF4EFE8.toInt(),
                onSurfaceVariant = 0xFFD0C4B6.toInt(),
                primary = 0xFF7EA16D.toInt(),
                onPrimary = 0xFF16210F.toInt(),
                primaryContainer = 0xFF314428.toInt(),
                onPrimaryContainer = 0xFFD7E8C5.toInt(),
                secondary = 0xFF8B7AA8.toInt(),
                onSecondary = 0xFF1F1630.toInt(),
                secondaryContainer = 0xFF43355A.toInt(),
                onSecondaryContainer = 0xFFE9DEFA.toInt(),
                error = 0xFFF2B8B5.toInt(),
                onError = 0xFF601410.toInt(),
                errorContainer = 0xFF8C1D18.toInt(),
                onErrorContainer = 0xFFF9DEDC.toInt(),
                success = 0xFF81C784.toInt(),
                warning = 0xFFFBC02D.toInt(),
                info = 0xFF64B5F6.toInt(),
                outline = 0xFF9C8F84.toInt(),
                outlineVariant = 0xFF51473E.toInt(),
                surfaceTint = 0xFF7EA16D.toInt(),
                inverseSurface = 0xFFE8DED3.toInt(),
                inverseOnSurface = 0xFF332D28.toInt(),
                ripple = 0x22F4EFE8,
            ),
            typography = UiTypography(
                titleMedium = UiTextStyle(fontSizeSp = 24.sp),
                bodyMedium = UiTextStyle(fontSizeSp = 16.sp),
                labelMedium = UiTextStyle(fontSizeSp = 14.sp),
            ),
            metadata = UiThemeMetadata(
                origin = UiThemeOrigin.FrameworkDefault,
                isDark = true,
            ),
        )
    }
}
