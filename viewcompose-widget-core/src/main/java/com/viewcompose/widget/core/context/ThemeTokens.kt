package com.viewcompose.widget.core

import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiSp

/**
 * ViewCompose 主题颜色 token，颜色格式为 ARGB Int。
 * ViewCompose theme color tokens stored as ARGB Int values.
 */
data class UiColors(
    val background: Int,
    val onBackground: Int = contentColorFor(background),
    val surface: Int,
    val surfaceVariant: Int,
    val surfaceDim: Int = surface,
    val surfaceBright: Int = surface,
    val surfaceContainerLowest: Int = background,
    val surfaceContainerLow: Int = surface,
    val surfaceContainer: Int = surface,
    val surfaceContainerHigh: Int = surfaceVariant,
    val surfaceContainerHighest: Int = surfaceVariant,
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
    val tertiary: Int = secondary,
    val onTertiary: Int = contentColorFor(tertiary),
    val tertiaryContainer: Int = tertiary,
    val onTertiaryContainer: Int = contentColorFor(tertiaryContainer),
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
    val inversePrimary: Int = primary,
    val scrim: Int = 0xFF000000.toInt(),
    val ripple: Int = pressedOverlayColorFor(onSurface),
)

/**
 * 同一语义颜色在不同交互状态下的取值。
 * Values for one semantic color across interaction states.
 */
data class UiStateColor(
    val defaultColor: Int,
    val disabledColor: Int = defaultColor,
    val pressedColor: Int = defaultColor,
    val focusedColor: Int = pressedColor,
    val checkedColor: Int = defaultColor,
    val selectedColor: Int = checkedColor,
) {
    /**
     * 按当前交互状态解析最终颜色。
     * Resolves the final color for the current interaction state.
     */
    fun resolve(
        enabled: Boolean = true,
        pressed: Boolean = false,
        focused: Boolean = false,
        checked: Boolean = false,
        selected: Boolean = false,
    ): Int {
        return when {
            !enabled -> disabledColor
            pressed -> pressedColor
            focused -> focusedColor
            checked -> checkedColor
            selected -> selectedColor
            else -> defaultColor
        }
    }
}

/**
 * 组件常用状态色集合。
 * Common state-color set used by components.
 */
data class UiStateColors(
    val primaryText: UiStateColor,
    val secondaryText: UiStateColor,
    val control: UiStateColor,
    val controlActivated: UiStateColor,
    val controlHighlight: UiStateColor,
)

/**
 * 从基础颜色派生默认状态色。
 * Derives default state colors from base colors.
 */
object UiStateColorDefaults {
    fun from(colors: UiColors): UiStateColors {
        return UiStateColors(
            primaryText = UiStateColor(
                defaultColor = colors.onSurface,
                disabledColor = colors.onSurfaceVariant,
            ),
            secondaryText = UiStateColor(
                defaultColor = colors.onSurfaceVariant,
                disabledColor = colors.outline,
            ),
            control = UiStateColor(
                defaultColor = colors.outline,
                disabledColor = colors.outlineVariant,
                pressedColor = colors.primary,
                focusedColor = colors.primary,
                checkedColor = colors.primary,
                selectedColor = colors.primary,
            ),
            controlActivated = UiStateColor(
                defaultColor = colors.primary,
                disabledColor = colors.outlineVariant,
                pressedColor = colors.primary,
                focusedColor = colors.primary,
                checkedColor = colors.primary,
                selectedColor = colors.primary,
            ),
            controlHighlight = UiStateColor(
                defaultColor = colors.ripple,
                disabledColor = 0x00000000,
                pressedColor = colors.ripple,
                focusedColor = colors.ripple,
                checkedColor = colors.ripple,
                selectedColor = colors.ripple,
            ),
        )
    }
}

/**
 * 主题形状 token。
 * Theme shape tokens.
 */
data class UiShapes(
    val small: UiShape,
    val medium: UiShape,
    val large: UiShape = medium,
)

/**
 * 主题文本样式 token。
 * Theme text-style token.
 */
data class UiTextStyle(
    val fontSizeSp: UiSp,
    val fontWeight: Int? = null,
    val fontFamily: android.graphics.Typeface? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean = false,
    val textDecoration: com.viewcompose.ui.node.TextDecoration? = null,
)

/**
 * 主题排版 token 集合。
 * Theme typography token set.
 */
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

/**
 * ViewCompose 主题完整 token 快照。
 * Complete ViewCompose theme-token snapshot.
 */
data class UiThemeTokens(
    val colors: UiColors,
    val typography: UiTypography,
    val stateColors: UiStateColors = UiStateColorDefaults.from(colors),
    val shapes: UiShapes = UiShapeDefaults.default(),
    val controls: UiControlSizing = UiControlSizeDefaults.default(),
    val overlays: UiOverlays = UiOverlayDefaults.default(),
    val metadata: UiThemeMetadata = UiThemeMetadata(),
)

/**
 * overlay 相关主题 token。
 * Theme tokens for overlays.
 */
data class UiOverlays(
    val scrimOpacity: Float,
)

/**
 * 主题 token 来源，用于诊断和 host 桥接。
 * Theme-token origin used for diagnostics and host bridging.
 */
enum class UiThemeOrigin {
    Custom,
    FrameworkDefault,
    AndroidTheme,
    AndroidDynamicColor,
    Override,
}

/**
 * 主题诊断元数据。
 * Theme diagnostic metadata.
 */
data class UiThemeMetadata(
    val origin: UiThemeOrigin = UiThemeOrigin.Custom,
    val isDark: Boolean? = null,
    val revision: Long = 0L,
)

/**
 * 基于内容色生成按压态 overlay 颜色。
 * Builds a pressed-state overlay color from the content color.
 */
internal fun pressedOverlayColorFor(contentColor: Int): Int {
    val base = contentColor and 0x00FFFFFF
    return 0x22000000 or base
}

/**
 * 根据背景亮度推导黑/白内容色。
 * Derives black or white content color from background luminance.
 */
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
