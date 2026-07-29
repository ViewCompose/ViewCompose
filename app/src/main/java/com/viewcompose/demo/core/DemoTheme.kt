package com.viewcompose

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.viewcompose.widget.core.UiColors
import com.viewcompose.widget.core.UiControlSizeDefaults
import com.viewcompose.widget.core.UiButtonSizing
import com.viewcompose.widget.core.UiProgressIndicatorSizing
import com.viewcompose.widget.core.UiSegmentedControlSizing
import com.viewcompose.widget.core.UiShapes
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTextFieldSizing
import com.viewcompose.widget.core.UiThemeMetadata
import com.viewcompose.widget.core.UiThemeTokens
import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.widget.core.UiTypography
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
            background = 0xFFF7F2EA.toInt(),
            surface = 0xFFEFE4D2.toInt(),
            surfaceVariant = 0xFFF8EED8.toInt(),
            primary = 0xFF7B9E68.toInt(),
            secondary = 0xFF9A7AAE.toInt(),
            error = 0xFFB3261E.toInt(),
            success = 0xFF2E7D32.toInt(),
            warning = 0xFFF57C00.toInt(),
            info = 0xFF1565C0.toInt(),
            onSurface = 0xFF2F241B.toInt(),
            onSurfaceVariant = 0xFF6A5A4A.toInt(),
            outline = 0xFFCCBDAA.toInt(),
        ),
        typography = UiTypography(
            titleMedium = UiTextStyle(fontSizeSp = 22.sp),
            bodyMedium = UiTextStyle(fontSizeSp = 16.sp),
            labelMedium = UiTextStyle(fontSizeSp = 14.sp),
        ),
        metadata = UiThemeMetadata(isDark = false),
    )

    /**
     * demo 深色主题 token。
     * Demo dark-theme tokens.
     */
    val dark: UiThemeTokens = createDemoThemeTokens(
        colors = UiColors(
            background = 0xFF1F1B18.toInt(),
            surface = 0xFF2C2621.toInt(),
            surfaceVariant = 0xFF3A332D.toInt(),
            primary = 0xFF98C27F.toInt(),
            secondary = 0xFFB39AC9.toInt(),
            error = 0xFFF2B8B5.toInt(),
            success = 0xFF81C784.toInt(),
            warning = 0xFFFBC02D.toInt(),
            info = 0xFF64B5F6.toInt(),
            onSurface = 0xFFF4EFE8.toInt(),
            onSurfaceVariant = 0xFFD0C4B6.toInt(),
            outline = 0xFF5B5046.toInt(),
        ),
        typography = UiTypography(
            titleMedium = UiTextStyle(fontSizeSp = 22.sp),
            bodyMedium = UiTextStyle(fontSizeSp = 16.sp),
            labelMedium = UiTextStyle(fontSizeSp = 14.sp),
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
        return modeLabel(
            mode = mode,
            isSystemDark = isSystemDark(context),
        )
    }

    /**
     * 使用显式系统明暗状态生成主题模式标签。
     * Builds a theme-mode label from an explicit system-dark flag.
     */
    fun modeLabel(
        mode: DemoThemeMode,
        isSystemDark: Boolean,
    ): String {
        return when (mode) {
            DemoThemeMode.System -> if (isSystemDark) "System (Dark)" else "System (Light)"
            DemoThemeMode.Light -> "Light"
            DemoThemeMode.Dark -> "Dark"
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
