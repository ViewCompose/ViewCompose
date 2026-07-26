package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape

data class UiColors(
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int = contentColorFor(primary),
    val primaryContainer: Int = primary,
    val onPrimaryContainer: Int = contentColorFor(primaryContainer),
    val secondary: Int,
    val onSecondary: Int = contentColorFor(secondary),
    val secondaryContainer: Int = secondary,
    val onSecondaryContainer: Int = contentColorFor(secondaryContainer),
    val error: Int,
    val onError: Int = contentColorFor(error),
    val errorContainer: Int = error,
    val onErrorContainer: Int = contentColorFor(errorContainer),
    val success: Int,
    val warning: Int,
    val info: Int,
    val outline: Int,
    val outlineVariant: Int = outline,
    val surfaceTint: Int = primary,
    val inverseSurface: Int = onSurface,
    val inverseOnSurface: Int = background,
    val ripple: Int = pressedOverlayColorFor(onSurface),
)

data class UiShapes(
    val small: UiShape,
    val medium: UiShape,
    val large: UiShape = medium,
)

data class UiTextStyle(
    val fontSizeSp: Int,
    val fontWeight: Int? = null,
    val fontFamily: android.graphics.Typeface? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: Int? = null,
    val includeFontPadding: Boolean = false,
    val textDecoration: com.viewcompose.ui.node.TextDecoration? = null,
)

data class UiTypography(
    val titleMedium: UiTextStyle,
    val bodyMedium: UiTextStyle,
    val labelMedium: UiTextStyle,
    val titleLarge: UiTextStyle = titleMedium,
    val titleSmall: UiTextStyle = titleMedium,
    val bodyLarge: UiTextStyle = bodyMedium,
    val bodySmall: UiTextStyle = bodyMedium,
    val labelLarge: UiTextStyle = labelMedium,
    val labelSmall: UiTextStyle = labelMedium,
)

data class UiThemeTokens(
    val colors: UiColors,
    val typography: UiTypography,
    val shapes: UiShapes = UiShapeDefaults.default(),
    val controls: UiControlSizing = UiControlSizeDefaults.default(),
    val overlays: UiOverlays = UiOverlayDefaults.default(),
    val metadata: UiThemeMetadata = UiThemeMetadata(),
)

data class UiOverlays(
    val scrimOpacity: Float,
)

enum class UiThemeOrigin {
    Custom,
    FrameworkDefault,
    AndroidTheme,
    AndroidDynamicColor,
    Override,
}

data class UiThemeMetadata(
    val origin: UiThemeOrigin = UiThemeOrigin.Custom,
    val isDark: Boolean? = null,
    val revision: Long = 0L,
)

internal fun pressedOverlayColorFor(contentColor: Int): Int {
    val base = contentColor and 0x00FFFFFF
    return 0x22000000 or base
}

internal fun contentColorFor(backgroundColor: Int): Int {
    val red = backgroundColor shr 16 and 0xFF
    val green = backgroundColor shr 8 and 0xFF
    val blue = backgroundColor and 0xFF
    val luma = 0.299 * red + 0.587 * green + 0.114 * blue
    return if (luma >= 186) {
        0xFF000000.toInt()
    } else {
        0xFFFFFFFF.toInt()
    }
}
