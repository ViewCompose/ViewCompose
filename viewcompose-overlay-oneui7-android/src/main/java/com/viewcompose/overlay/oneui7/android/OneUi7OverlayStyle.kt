package com.viewcompose.overlay.oneui7.android

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import com.viewcompose.oneui7.OneUi7ThemeDefaults
import com.viewcompose.ui.foundation.UiThemeTokens

/** Resolved Android chrome values kept private to the One UI overlay integration. */
internal data class OneUi7OverlayStyle(
    val surfaceColor: Int,
    val contentColor: Int,
    val secondaryContentColor: Int,
    val snackbarColor: Int,
    val snackbarContentColor: Int,
    val actionColor: Int,
    val outlineColor: Int,
    val cornerRadiusDp: Float,
    val horizontalMarginDp: Int,
) {
    companion object {
        fun from(tokens: UiThemeTokens): OneUi7OverlayStyle = OneUi7OverlayStyle(
            surfaceColor = tokens.colors.surface,
            contentColor = tokens.colors.onSurface,
            secondaryContentColor = tokens.colors.onSurfaceVariant,
            snackbarColor = tokens.colors.inverseSurface,
            snackbarContentColor = tokens.colors.inverseOnSurface,
            actionColor = tokens.stateColors.controlActivated.checkedColor,
            outlineColor = tokens.colors.outline,
            cornerRadiusDp = tokens.shapes.large.uniformAbsoluteSizeOrNull?.value ?: 26f,
            horizontalMarginDp = 24,
        )
    }
}

internal fun defaultOneUi7Tokens(context: Context): UiThemeTokens {
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
        OneUi7ThemeDefaults.dark()
    } else {
        OneUi7ThemeDefaults.light()
    }
}

internal fun roundedDrawable(
    color: Int,
    radiusPx: Float,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(color)
    cornerRadius = radiusPx
}
