package com.viewcompose

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.viewcompose.material3.Material3ThemeDefaults
import com.viewcompose.ui.foundation.UiColors
import com.viewcompose.ui.foundation.UiControlSizeDefaults
import com.viewcompose.ui.foundation.UiButtonSizing
import com.viewcompose.ui.foundation.UiProgressIndicatorSizing
import com.viewcompose.ui.foundation.UiSegmentedControlSizing
import com.viewcompose.ui.foundation.UiShapes
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTextFieldSizing
import com.viewcompose.ui.foundation.UiThemeMetadata
import com.viewcompose.ui.foundation.UiThemeTokens
import com.viewcompose.ui.foundation.override
import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.foundation.UiTypography
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

/**
 * demo 应用支持的主题模式。
 * Theme modes supported by the demo app.
 */
enum class DemoThemeMode {
    System,
    Light,
    Dark,
}

/** Identifies the isolated theme source rendered by the theme verification fixture. */
internal enum class DemoThemeSource(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
) {
    AndroidXml(
        id = "android-xml",
        labelRes = R.string.demo_theme_source_android_xml,
        descriptionRes = R.string.demo_theme_source_android_xml_description,
    ),
    Material3Defaults(
        id = "material3-static",
        labelRes = R.string.demo_theme_source_material3_static,
        descriptionRes = R.string.demo_theme_source_material3_static_description,
    ),
    DemoCustom(
        id = "demo-custom",
        labelRes = R.string.demo_theme_source_demo_custom,
        descriptionRes = R.string.demo_theme_source_demo_custom_description,
    ),
    ;

    fun tokens(isDark: Boolean): UiThemeTokens? {
        return when (this) {
            AndroidXml -> null
            Material3Defaults -> if (isDark) Material3ThemeDefaults.dark() else Material3ThemeDefaults.light()
            DemoCustom -> {
                val base = if (isDark) Material3ThemeDefaults.dark() else Material3ThemeDefaults.light()
                val application = if (isDark) DemoThemeTokens.dark else DemoThemeTokens.light
                base.override(
                    colors = application.colors,
                    typography = application.typography,
                    shapes = application.shapes,
                    controls = application.controls,
                    interactions = application.interactions,
                    overlays = application.overlays,
                )
            }
        }
    }

}

/**
 * 将 shape 转成面向 demo 页展示的可读标签。
 * Converts a shape into a readable label for demo pages.
 */
internal fun UiShape.demoLabel(): String {
    if (isUniform) {
        return topStart.demoLabel()
    }
    return listOf(topStart, topEnd, bottomEnd, bottomStart)
        .joinToString(separator = " / ") { corner -> corner.demoLabel() }
}

private fun UiCorner.demoLabel(): String {
    val sizeLabel = when (val cornerSize = size) {
        is UiCornerSize.Absolute -> {
            val value = cornerSize.size.value
            if (value == value.toInt().toFloat()) "${value.toInt()}dp" else "${value}dp"
        }
        is UiCornerSize.Relative -> "${(cornerSize.fraction * 100).toInt()}%"
    }
    return "${family.name.lowercase()} $sizeLabel"
}

/**
 * 创建稳定、与设备环境无关的 demo 主题 token。
 * Creates stable demo theme tokens that are independent of the device environment.
 */
private fun createDemoThemeTokens(
    colors: UiColors,
    typography: UiTypography,
    metadata: UiThemeMetadata,
): UiThemeTokens {
    val defaultControls = UiControlSizeDefaults.default()
    return UiThemeTokens(
        colors = colors,
        typography = typography,
        shapes = UiShapes(
            small = UiShape.rounded(18.dp),
            medium = UiShape.rounded(24.dp),
        ),
        controls = defaultControls.copy(
            button = UiButtonSizing(
                compactHeight = 38.dp,
                mediumHeight = 46.dp,
                largeHeight = 54.dp,
                compactHorizontalPadding = 14.dp,
                mediumHorizontalPadding = 18.dp,
                largeHorizontalPadding = 22.dp,
                compactVerticalPadding = 8.dp,
                mediumVerticalPadding = 10.dp,
                largeVerticalPadding = 12.dp,
            ),
            textField = UiTextFieldSizing(
                compactHeight = 42.dp,
                mediumHeight = 50.dp,
                largeHeight = 58.dp,
                compactHorizontalPadding = 14.dp,
                mediumHorizontalPadding = 16.dp,
                largeHorizontalPadding = 18.dp,
                compactVerticalPadding = 8.dp,
                mediumVerticalPadding = 10.dp,
                largeVerticalPadding = 12.dp,
            ),
            segmentedControl = UiSegmentedControlSizing(
                compactHeight = 38.dp,
                mediumHeight = 44.dp,
                largeHeight = 50.dp,
                compactHorizontalPadding = 12.dp,
                mediumHorizontalPadding = 14.dp,
                largeHorizontalPadding = 18.dp,
                compactVerticalPadding = 6.dp,
                mediumVerticalPadding = 8.dp,
                largeVerticalPadding = 10.dp,
            ),
            progressIndicator = UiProgressIndicatorSizing(
                linearTrackThickness = 6.dp,
                circularSize = 36.dp,
                circularTrackThickness = 4.dp,
            ),
        ),
        metadata = metadata,
    )
}

/**
 * demo 应用专用的主题 token 集合和解析工具。
 * Demo-app-specific theme tokens and resolving helpers.
 *
 * 这里刻意独立于框架默认主题，用于验证业务侧覆盖 token 时的渲染表现。
 * This intentionally stays separate from framework defaults to verify rendering with app-provided tokens.
 */
object DemoThemeTokens {
    /**
     * demo 浅色主题 token。
     * Demo light-theme tokens.
     */
    val light: UiThemeTokens = createDemoThemeTokens(
        colors = UiColors(
            background = 0xFFF4FBF8.toInt(),
            onBackground = 0xFF161D1B.toInt(),
            surface = 0xFFF4FBF8.toInt(),
            surfaceVariant = 0xFFDAE5E1.toInt(),
            surfaceDim = 0xFFD5DBD9.toInt(),
            surfaceBright = 0xFFF4FBF8.toInt(),
            surfaceContainerLowest = 0xFFFFFFFF.toInt(),
            surfaceContainerLow = 0xFFEEF5F2.toInt(),
            surfaceContainer = 0xFFE8EFED.toInt(),
            surfaceContainerHigh = 0xFFE2E9E7.toInt(),
            surfaceContainerHighest = 0xFFDDE4E1.toInt(),
            onSurface = 0xFF161D1B.toInt(),
            onSurfaceVariant = 0xFF3F4946.toInt(),
            primary = 0xFF006A60.toInt(),
            onPrimary = 0xFFFFFFFF.toInt(),
            primaryContainer = 0xFF74F8E5.toInt(),
            onPrimaryContainer = 0xFF00201C.toInt(),
            secondary = 0xFF4A635E.toInt(),
            onSecondary = 0xFFFFFFFF.toInt(),
            secondaryContainer = 0xFFCCE8E1.toInt(),
            onSecondaryContainer = 0xFF06201C.toInt(),
            tertiary = 0xFF456179.toInt(),
            onTertiary = 0xFFFFFFFF.toInt(),
            tertiaryContainer = 0xFFCCE5FF.toInt(),
            onTertiaryContainer = 0xFF001E31.toInt(),
            error = 0xFFB3261E.toInt(),
            onError = 0xFFFFFFFF.toInt(),
            errorContainer = 0xFFF9DEDC.toInt(),
            onErrorContainer = 0xFF410E0B.toInt(),
            success = 0xFF2E7D32.toInt(),
            warning = 0xFFF57C00.toInt(),
            info = 0xFF1565C0.toInt(),
            outline = 0xFF6F7976.toInt(),
            outlineVariant = 0xFFBEC9C5.toInt(),
            surfaceTint = 0xFF006A60.toInt(),
            inverseSurface = 0xFF2B3230.toInt(),
            inverseOnSurface = 0xFFECF2F0.toInt(),
            inversePrimary = 0xFF53DBC8.toInt(),
        ),
        typography = UiTypography(
            titleMedium = UiTextStyle(fontSizeSp = 22.sp, lineHeightSp = 28.sp),
            bodyMedium = UiTextStyle(fontSizeSp = 16.sp, lineHeightSp = 24.sp),
            labelMedium = UiTextStyle(fontSizeSp = 14.sp, lineHeightSp = 20.sp),
        ),
        metadata = UiThemeMetadata(isDark = false),
    )

    /**
     * demo 深色主题 token。
     * Demo dark-theme tokens.
     */
    val dark: UiThemeTokens = createDemoThemeTokens(
        colors = UiColors(
            background = 0xFF0E1513.toInt(),
            onBackground = 0xFFDDE4E1.toInt(),
            surface = 0xFF0E1513.toInt(),
            surfaceVariant = 0xFF3F4946.toInt(),
            surfaceDim = 0xFF0E1513.toInt(),
            surfaceBright = 0xFF343B39.toInt(),
            surfaceContainerLowest = 0xFF090F0D.toInt(),
            surfaceContainerLow = 0xFF161D1B.toInt(),
            surfaceContainer = 0xFF1A211F.toInt(),
            surfaceContainerHigh = 0xFF252B29.toInt(),
            surfaceContainerHighest = 0xFF2F3634.toInt(),
            onSurface = 0xFFDDE4E1.toInt(),
            onSurfaceVariant = 0xFFBEC9C5.toInt(),
            primary = 0xFF53DBC8.toInt(),
            onPrimary = 0xFF003731.toInt(),
            primaryContainer = 0xFF005047.toInt(),
            onPrimaryContainer = 0xFF74F8E5.toInt(),
            secondary = 0xFFB1CCC5.toInt(),
            onSecondary = 0xFF1C3530.toInt(),
            secondaryContainer = 0xFF334B46.toInt(),
            onSecondaryContainer = 0xFFCCE8E1.toInt(),
            tertiary = 0xFFADCAE5.toInt(),
            onTertiary = 0xFF143349.toInt(),
            tertiaryContainer = 0xFF2C4961.toInt(),
            onTertiaryContainer = 0xFFCCE5FF.toInt(),
            error = 0xFFF2B8B5.toInt(),
            onError = 0xFF601410.toInt(),
            errorContainer = 0xFF8C1D18.toInt(),
            onErrorContainer = 0xFFF9DEDC.toInt(),
            success = 0xFF81C784.toInt(),
            warning = 0xFFFBC02D.toInt(),
            info = 0xFF64B5F6.toInt(),
            outline = 0xFF89938F.toInt(),
            outlineVariant = 0xFF3F4946.toInt(),
            surfaceTint = 0xFF53DBC8.toInt(),
            inverseSurface = 0xFFDDE4E1.toInt(),
            inverseOnSurface = 0xFF2B3230.toInt(),
            inversePrimary = 0xFF006A60.toInt(),
        ),
        typography = UiTypography(
            titleMedium = UiTextStyle(fontSizeSp = 22.sp, lineHeightSp = 28.sp),
            bodyMedium = UiTextStyle(fontSizeSp = 16.sp, lineHeightSp = 24.sp),
            labelMedium = UiTextStyle(fontSizeSp = 14.sp, lineHeightSp = 20.sp),
        ),
        metadata = UiThemeMetadata(isDark = true),
    )

    /**
     * 选择稳定的明暗主题 token，不读取或解析环境。
     * Selects stable light/dark theme tokens without reading or resolving the environment.
     */
    fun select(
        mode: DemoThemeMode,
        isSystemDark: Boolean,
    ): UiThemeTokens {
        return when (mode) {
            DemoThemeMode.System -> if (isSystemDark) dark else light
            DemoThemeMode.Light -> light
            DemoThemeMode.Dark -> dark
        }
    }

    /**
     * 生成包含系统跟随结果的主题模式标签。
     * Builds a theme-mode label that includes the resolved system-following result.
     */
    fun modeLabel(
        mode: DemoThemeMode,
        context: Context,
    ): String {
        return context.getString(modeLabelRes(mode, isSystemDark(context)))
    }

    /**
     * 使用显式系统明暗状态生成主题模式标签。
     * Builds a theme-mode label from an explicit system-dark flag.
     */
    @StringRes
    fun modeLabelRes(
        mode: DemoThemeMode,
        isSystemDark: Boolean,
    ): Int {
        return when (mode) {
            DemoThemeMode.System -> if (isSystemDark) {
                R.string.demo_theme_mode_system_dark
            } else {
                R.string.demo_theme_mode_system_light
            }
            DemoThemeMode.Light -> R.string.demo_theme_mode_light
            DemoThemeMode.Dark -> R.string.demo_theme_mode_dark
        }
    }

    /**
     * 读取当前资源配置中的夜间模式。
     * Reads night mode from the current resource configuration.
     */
    fun isSystemDark(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }
}

/**
 * 根据当前 demo 主题同步系统栏明暗外观。
 * Synchronizes status/navigation bar appearance with the current demo theme.
 */
internal fun AppCompatActivity.applyDemoThemeWindowAppearance(
    tokens: UiThemeTokens,
) {
    val isDark = tokens.metadata.isDark ?: return
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = !isDark
        isAppearanceLightNavigationBars = !isDark
    }
}
