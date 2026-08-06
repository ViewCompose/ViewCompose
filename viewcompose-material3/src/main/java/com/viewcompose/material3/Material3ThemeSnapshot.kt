package com.viewcompose.material3

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp
import com.viewcompose.ui.foundation.UiStateColor

/**
 * Snapshot of colors and state colors readable from an Android theme.
 */
internal data class Material3ThemeColorSnapshot(
    val background: Int? = null,
    val onBackground: Int? = null,
    val surface: Int? = null,
    val surfaceDim: Int? = null,
    val surfaceBright: Int? = null,
    val surfaceContainerLowest: Int? = null,
    val surfaceContainerLow: Int? = null,
    val surfaceContainer: Int? = null,
    val surfaceContainerHigh: Int? = null,
    val surfaceContainerHighest: Int? = null,
    val surfaceVariant: Int? = null,
    val onSurface: Int? = null,
    val onSurfaceVariant: Int? = null,
    val primary: Int? = null,
    val onPrimary: Int? = null,
    val primaryContainer: Int? = null,
    val onPrimaryContainer: Int? = null,
    val secondary: Int? = null,
    val onSecondary: Int? = null,
    val secondaryContainer: Int? = null,
    val onSecondaryContainer: Int? = null,
    val tertiary: Int? = null,
    val onTertiary: Int? = null,
    val tertiaryContainer: Int? = null,
    val onTertiaryContainer: Int? = null,
    val error: Int? = null,
    val onError: Int? = null,
    val errorContainer: Int? = null,
    val onErrorContainer: Int? = null,
    val outline: Int? = null,
    val outlineVariant: Int? = null,
    val surfaceTint: Int? = null,
    val inverseSurface: Int? = null,
    val inverseOnSurface: Int? = null,
    val inversePrimary: Int? = null,
    val scrim: Int? = null,
    val ripple: Int? = null,
    val primaryText: UiStateColor? = null,
    val secondaryText: UiStateColor? = null,
    val control: UiStateColor? = null,
    val controlActivated: UiStateColor? = null,
    val controlHighlight: UiStateColor? = null,
)

/**
 * Complete readable snapshot of an Android theme.
 */
internal data class Material3ThemeSnapshot(
    val colors: Material3ThemeColorSnapshot = Material3ThemeColorSnapshot(),
    val shapes: Material3ThemeShapeSnapshot = Material3ThemeShapeSnapshot(),
    val typography: Material3ThemeTypographySnapshot = Material3ThemeTypographySnapshot(),
    val scrimOpacity: Float? = null,
)

/**
 * Parsed result of Android shapeAppearance attributes.
 */
internal data class Material3ThemeShapeSnapshot(
    val small: UiShape? = null,
    val medium: UiShape? = null,
    val large: UiShape? = null,
)

/**
 * Parsed result of Android textAppearance attributes.
 */
internal data class Material3TextStyleSnapshot(
    val fontSizeSp: UiSp? = null,
    val fontWeight: Int? = null,
    val fontFamily: Typeface? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean? = null,
)

/**
 * Snapshot of Android typography-related textAppearance values.
 */
internal data class Material3ThemeTypographySnapshot(
    val titleLarge: Material3TextStyleSnapshot? = null,
    val titleMedium: Material3TextStyleSnapshot? = null,
    val titleSmall: Material3TextStyleSnapshot? = null,
    val bodyLarge: Material3TextStyleSnapshot? = null,
    val bodyMedium: Material3TextStyleSnapshot? = null,
    val bodySmall: Material3TextStyleSnapshot? = null,
    val labelLarge: Material3TextStyleSnapshot? = null,
    val labelMedium: Material3TextStyleSnapshot? = null,
    val labelSmall: Material3TextStyleSnapshot? = null,
)

/**
 * Reader for Android theme snapshots.
 */
internal object Material3ThemeSnapshotReader {
    /**
     * Reads colors, shapes, typography, and scrim opacity.
     */
    fun read(context: Context): Material3ThemeSnapshot {
        return Material3ThemeSnapshot(
            colors = readColorSnapshot(context),
            shapes = readShapeSnapshot(context),
            typography = readTypographySnapshot(context),
            scrimOpacity = readScrimOpacity(context),
        )
    }

    private fun readColorSnapshot(context: Context): Material3ThemeColorSnapshot {
        val attrs = intArrayOf(
            android.R.attr.colorBackground,
            com.google.android.material.R.attr.colorOnBackground,
            com.google.android.material.R.attr.colorSurface,
            com.google.android.material.R.attr.colorSurfaceDim,
            com.google.android.material.R.attr.colorSurfaceBright,
            com.google.android.material.R.attr.colorSurfaceContainerLowest,
            com.google.android.material.R.attr.colorSurfaceContainerLow,
            com.google.android.material.R.attr.colorSurfaceContainer,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            com.google.android.material.R.attr.colorSurfaceContainerHighest,
            com.google.android.material.R.attr.colorSurfaceVariant,
            com.google.android.material.R.attr.colorOnSurface,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            androidx.appcompat.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorOnPrimary,
            com.google.android.material.R.attr.colorPrimaryContainer,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            com.google.android.material.R.attr.colorSecondary,
            com.google.android.material.R.attr.colorOnSecondary,
            com.google.android.material.R.attr.colorSecondaryContainer,
            com.google.android.material.R.attr.colorOnSecondaryContainer,
            com.google.android.material.R.attr.colorTertiary,
            com.google.android.material.R.attr.colorOnTertiary,
            com.google.android.material.R.attr.colorTertiaryContainer,
            com.google.android.material.R.attr.colorOnTertiaryContainer,
            android.R.attr.colorError,
            com.google.android.material.R.attr.colorOnError,
            com.google.android.material.R.attr.colorErrorContainer,
            com.google.android.material.R.attr.colorOnErrorContainer,
            com.google.android.material.R.attr.colorOutline,
            com.google.android.material.R.attr.colorOutlineVariant,
            com.google.android.material.R.attr.colorSurfaceInverse,
            com.google.android.material.R.attr.colorOnSurfaceInverse,
            com.google.android.material.R.attr.colorPrimaryInverse,
            android.R.attr.textColorPrimary,
            android.R.attr.textColorSecondary,
            androidx.appcompat.R.attr.colorControlNormal,
            androidx.appcompat.R.attr.colorControlActivated,
            androidx.appcompat.R.attr.colorControlHighlight,
        )
        val typedArray = context.obtainStyledAttributes(attrs)
        return try {
            val primaryText = typedArray.getStateColorOrNull(34)
            val secondaryText = typedArray.getStateColorOrNull(35)
            val control = typedArray.getStateColorOrNull(36)
            val controlActivated = typedArray.getStateColorOrNull(37)
            val controlHighlight = typedArray.getStateColorOrNull(38)
            val onSurface = typedArray.getColorOrNull(11) ?: primaryText?.defaultColor
            val onSurfaceVariant = typedArray.getColorOrNull(12) ?: secondaryText?.defaultColor
            Material3ThemeColorSnapshot(
                background = typedArray.getColorOrNull(0),
                onBackground = typedArray.getColorOrNull(1),
                surface = typedArray.getColorOrNull(2),
                surfaceDim = typedArray.getColorOrNull(3),
                surfaceBright = typedArray.getColorOrNull(4),
                surfaceContainerLowest = typedArray.getColorOrNull(5),
                surfaceContainerLow = typedArray.getColorOrNull(6),
                surfaceContainer = typedArray.getColorOrNull(7),
                surfaceContainerHigh = typedArray.getColorOrNull(8),
                surfaceContainerHighest = typedArray.getColorOrNull(9),
                surfaceVariant = typedArray.getColorOrNull(10),
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                primary = typedArray.getColorOrNull(13),
                onPrimary = typedArray.getColorOrNull(14),
                primaryContainer = typedArray.getColorOrNull(15),
                onPrimaryContainer = typedArray.getColorOrNull(16),
                secondary = typedArray.getColorOrNull(17),
                onSecondary = typedArray.getColorOrNull(18),
                secondaryContainer = typedArray.getColorOrNull(19),
                onSecondaryContainer = typedArray.getColorOrNull(20),
                tertiary = typedArray.getColorOrNull(21),
                onTertiary = typedArray.getColorOrNull(22),
                tertiaryContainer = typedArray.getColorOrNull(23),
                onTertiaryContainer = typedArray.getColorOrNull(24),
                error = typedArray.getColorOrNull(25),
                onError = typedArray.getColorOrNull(26),
                errorContainer = typedArray.getColorOrNull(27),
                onErrorContainer = typedArray.getColorOrNull(28),
                outline = typedArray.getColorOrNull(29),
                outlineVariant = typedArray.getColorOrNull(30),
                surfaceTint = typedArray.getColorOrNull(13),
                inverseSurface = typedArray.getColorOrNull(31),
                inverseOnSurface = typedArray.getColorOrNull(32),
                inversePrimary = typedArray.getColorOrNull(33),
                ripple = controlHighlight?.pressedColor,
                primaryText = primaryText,
                secondaryText = secondaryText,
                control = control,
                controlActivated = controlActivated,
                controlHighlight = controlHighlight,
            )
        } finally {
            typedArray.recycle()
        }
    }

    private fun readScrimOpacity(context: Context): Float? {
        val typedArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.backgroundDimAmount))
        return try {
            if (typedArray.hasValue(0)) typedArray.getFloat(0, 0f) else null
        } finally {
            typedArray.recycle()
        }
    }

    private fun readShapeSnapshot(context: Context): Material3ThemeShapeSnapshot {
        val attrs = intArrayOf(
            com.google.android.material.R.attr.shapeAppearanceSmallComponent,
            com.google.android.material.R.attr.shapeAppearanceMediumComponent,
            com.google.android.material.R.attr.shapeAppearanceLargeComponent,
        )
        val typedArray = context.obtainStyledAttributes(attrs)
        return try {
            Material3ThemeShapeSnapshot(
                small = typedArray.getStyleShapeOrNull(context, 0),
                medium = typedArray.getStyleShapeOrNull(context, 1),
                large = typedArray.getStyleShapeOrNull(context, 2),
            )
        } finally {
            typedArray.recycle()
        }
    }

    private fun readTypographySnapshot(context: Context): Material3ThemeTypographySnapshot {
        val attrs = intArrayOf(
            com.google.android.material.R.attr.textAppearanceTitleLarge,
            com.google.android.material.R.attr.textAppearanceTitleMedium,
            com.google.android.material.R.attr.textAppearanceTitleSmall,
            com.google.android.material.R.attr.textAppearanceBodyLarge,
            com.google.android.material.R.attr.textAppearanceBodyMedium,
            com.google.android.material.R.attr.textAppearanceBodySmall,
            com.google.android.material.R.attr.textAppearanceLabelLarge,
            com.google.android.material.R.attr.textAppearanceLabelMedium,
            com.google.android.material.R.attr.textAppearanceLabelSmall,
            android.R.attr.textAppearanceLarge,
            android.R.attr.textAppearanceMedium,
            android.R.attr.textAppearanceSmall,
        )
        val typedArray = context.obtainStyledAttributes(attrs)
        return try {
            val legacyTitle = typedArray.getTextStyleSnapshot(context, 9)
            val legacyBody = typedArray.getTextStyleSnapshot(context, 10)
            val legacyLabel = typedArray.getTextStyleSnapshot(context, 11)
            Material3ThemeTypographySnapshot(
                titleLarge = typedArray.getTextStyleSnapshot(context, 0) ?: legacyTitle,
                titleMedium = typedArray.getTextStyleSnapshot(context, 1) ?: legacyTitle,
                titleSmall = typedArray.getTextStyleSnapshot(context, 2) ?: legacyTitle,
                bodyLarge = typedArray.getTextStyleSnapshot(context, 3) ?: legacyBody,
                bodyMedium = typedArray.getTextStyleSnapshot(context, 4) ?: legacyBody,
                bodySmall = typedArray.getTextStyleSnapshot(context, 5) ?: legacyBody,
                labelLarge = typedArray.getTextStyleSnapshot(context, 6) ?: legacyLabel,
                labelMedium = typedArray.getTextStyleSnapshot(context, 7) ?: legacyLabel,
                labelSmall = typedArray.getTextStyleSnapshot(context, 8) ?: legacyLabel,
            )
        } finally {
            typedArray.recycle()
        }
    }
}

private fun TypedArray.getColorOrNull(index: Int): Int? {
    return if (hasValue(index)) getColor(index, 0) else null
}

private fun TypedArray.getStateColorOrNull(index: Int): UiStateColor? {
    if (!hasValue(index)) return null
    val colors: ColorStateList = getColorStateList(index) ?: return null
    val defaultColor = colors.defaultColor
    return UiStateColor(
        defaultColor = defaultColor,
        disabledColor = colors.getColorForState(
            intArrayOf(-android.R.attr.state_enabled),
            defaultColor,
        ),
        pressedColor = colors.getColorForState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
            defaultColor,
        ),
        focusedColor = colors.getColorForState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
            defaultColor,
        ),
        checkedColor = colors.getColorForState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
            defaultColor,
        ),
        selectedColor = colors.getColorForState(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_selected),
            defaultColor,
        ),
    )
}

private fun TypedArray.getStyleShapeOrNull(context: Context, index: Int): UiShape? {
    if (!hasValue(index)) return null
    val styleRes = getResourceId(index, 0)
    if (styleRes == 0) return null
    val styleArray = context.obtainStyledAttributes(
        styleRes,
        intArrayOf(
            com.google.android.material.R.attr.cornerFamily,
            com.google.android.material.R.attr.cornerFamilyTopLeft,
            com.google.android.material.R.attr.cornerFamilyTopRight,
            com.google.android.material.R.attr.cornerFamilyBottomRight,
            com.google.android.material.R.attr.cornerFamilyBottomLeft,
            com.google.android.material.R.attr.cornerSize,
            com.google.android.material.R.attr.cornerSizeTopLeft,
            com.google.android.material.R.attr.cornerSizeTopRight,
            com.google.android.material.R.attr.cornerSizeBottomRight,
            com.google.android.material.R.attr.cornerSizeBottomLeft,
        ),
    )
    return try {
        val defaultFamily = styleArray.readCornerFamily(index = 0)
        val density = context.resources.displayMetrics.density
        val defaultSize = styleArray.readCornerSize(index = 5, density = density)
            ?: UiCornerSize.Absolute(UiDp.Zero)
        val topLeft = UiCorner(
            family = styleArray.readCornerFamily(index = 1, fallback = defaultFamily),
            size = styleArray.readCornerSize(index = 6, density = density) ?: defaultSize,
        )
        val topRight = UiCorner(
            family = styleArray.readCornerFamily(index = 2, fallback = defaultFamily),
            size = styleArray.readCornerSize(index = 7, density = density) ?: defaultSize,
        )
        val bottomRight = UiCorner(
            family = styleArray.readCornerFamily(index = 3, fallback = defaultFamily),
            size = styleArray.readCornerSize(index = 8, density = density) ?: defaultSize,
        )
        val bottomLeft = UiCorner(
            family = styleArray.readCornerFamily(index = 4, fallback = defaultFamily),
            size = styleArray.readCornerSize(index = 9, density = density) ?: defaultSize,
        )
        if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            UiShape(
                topStart = topRight,
                topEnd = topLeft,
                bottomEnd = bottomLeft,
                bottomStart = bottomRight,
            )
        } else {
            UiShape(
                topStart = topLeft,
                topEnd = topRight,
                bottomEnd = bottomRight,
                bottomStart = bottomLeft,
            )
        }
    } finally {
        styleArray.recycle()
    }
}

private fun TypedArray.readCornerFamily(
    index: Int,
    fallback: UiCornerFamily = UiCornerFamily.Rounded,
): UiCornerFamily {
    if (!hasValue(index)) return fallback
    return when (getInt(index, 0)) {
        1 -> UiCornerFamily.Cut
        else -> UiCornerFamily.Rounded
    }
}

private fun TypedArray.readCornerSize(
    index: Int,
    density: Float,
): UiCornerSize? {
    if (!hasValue(index)) return null
    return when (peekValue(index)?.type) {
        TypedValue.TYPE_FRACTION -> UiCornerSize.Relative(
            fraction = getFraction(index, 1, 1, 0f).coerceIn(0f, 1f),
        )

        TypedValue.TYPE_DIMENSION -> UiCornerSize.Absolute(
            size = UiDp(getDimension(index, 0f).coerceAtLeast(0f) / density),
        )

        else -> null
    }
}

private fun TypedArray.getTextStyleSnapshot(context: Context, index: Int): Material3TextStyleSnapshot? {
    if (!hasValue(index)) return null
    val styleRes = getResourceId(index, 0)
    if (styleRes == 0) return null
    val styleArray = context.obtainStyledAttributes(
        styleRes,
        intArrayOf(
            android.R.attr.textSize,
            android.R.attr.textFontWeight,
            android.R.attr.fontFamily,
            androidx.appcompat.R.attr.fontFamily,
            android.R.attr.letterSpacing,
            android.R.attr.lineHeight,
            android.R.attr.includeFontPadding,
        ),
    )
    return try {
        val fontSizePx = if (styleArray.hasValue(0)) styleArray.getDimensionPixelSize(0, 0) else 0
        val lineHeightPx = if (styleArray.hasValue(5)) styleArray.getDimensionPixelSize(5, 0) else 0
        Material3TextStyleSnapshot(
            fontSizeSp = fontSizePx.takeIf { it > 0 }?.let(context::pxToSp),
            fontWeight = if (styleArray.hasValue(1)) styleArray.getInt(1, 400) else null,
            fontFamily = resolveFontFamily(context, styleArray),
            letterSpacingEm = if (styleArray.hasValue(4)) styleArray.getFloat(4, 0f) else null,
            lineHeightSp = lineHeightPx.takeIf { it > 0 }?.let(context::pxToSp),
            includeFontPadding = if (styleArray.hasValue(6)) styleArray.getBoolean(6, false) else null,
        )
    } finally {
        styleArray.recycle()
    }
}

private fun resolveFontFamily(context: Context, styleArray: TypedArray): Typeface? {
    for (index in intArrayOf(2, 3)) {
        if (!styleArray.hasValue(index)) continue
        val resourceId = styleArray.getResourceId(index, 0)
        if (resourceId != 0) {
            runCatching { ResourcesCompat.getFont(context, resourceId) }.getOrNull()?.let { return it }
        }
        val value = styleArray.peekValue(index)
        if (value?.type == TypedValue.TYPE_STRING) {
            val familyName = value.string?.toString()
            if (!familyName.isNullOrBlank()) {
                return Typeface.create(familyName, Typeface.NORMAL)
            }
        }
    }
    return null
}

private fun Context.pxToSp(value: Int): UiSp {
    val density = resources.displayMetrics.density
    val fontScale = resources.configuration.fontScale.takeIf { it > 0f } ?: 1f
    return UiSp(value / (density * fontScale))
}
