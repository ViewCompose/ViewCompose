package com.viewcompose

import android.content.Context
import android.content.res.Configuration
import com.viewcompose.widget.core.UiColors
import com.viewcompose.widget.core.UiControlSizing
import com.viewcompose.widget.core.UiButtonSizing
import com.viewcompose.widget.core.UiProgressIndicatorSizing
import com.viewcompose.widget.core.UiSegmentedControlSizing
import com.viewcompose.widget.core.UiShapes
import com.viewcompose.widget.core.UiTextStyle
import com.viewcompose.widget.core.UiTextFieldSizing
import com.viewcompose.widget.core.UiThemeTokens
import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.widget.core.UiTypography
import com.viewcompose.widget.core.dp
import com.viewcompose.widget.core.sp

enum class DemoThemeMode {
    System,
    Light,
    Dark,
}

internal fun UiShape.demoLabel(): String {
    if (isUniform) {
        return topStart.demoLabel()
    }
    return listOf(topStart, topEnd, bottomEnd, bottomStart)
        .joinToString(separator = " / ") { corner -> corner.demoLabel() }
}

private fun UiCorner.demoLabel(): String {
    val sizeLabel = when (val cornerSize = size) {
        is UiCornerSize.Absolute -> "${cornerSize.pixels}px"
        is UiCornerSize.Relative -> "${(cornerSize.fraction * 100).toInt()}%"
    }
    return "${family.name.lowercase()} $sizeLabel"
}

object DemoThemeTokens {
    val light: UiThemeTokens = UiThemeTokens(
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
        shapes = UiShapes(
            small = UiShape.rounded(18.dp),
            medium = UiShape.rounded(24.dp),
        ),
        controls = UiControlSizing(
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
    )

    val dark: UiThemeTokens = UiThemeTokens(
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
        shapes = UiShapes(
            small = UiShape.rounded(18.dp),
            medium = UiShape.rounded(24.dp),
        ),
        controls = UiControlSizing(
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
    )

    fun resolve(
        mode: DemoThemeMode,
        context: Context,
    ): UiThemeTokens {
        return resolve(
            mode = mode,
            isSystemDark = isSystemDark(context),
        )
    }

    fun resolve(
        mode: DemoThemeMode,
        isSystemDark: Boolean,
    ): UiThemeTokens {
        return when (mode) {
            DemoThemeMode.System -> if (isSystemDark) dark else light
            DemoThemeMode.Light -> light
            DemoThemeMode.Dark -> dark
        }
    }

    fun modeLabel(
        mode: DemoThemeMode,
        context: Context,
    ): String {
        return modeLabel(
            mode = mode,
            isSystemDark = isSystemDark(context),
        )
    }

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

    fun isSystemDark(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }
}
