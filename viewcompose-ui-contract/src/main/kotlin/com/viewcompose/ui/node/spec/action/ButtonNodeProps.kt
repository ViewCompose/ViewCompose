package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.shape.UiShape

data class ButtonNodeProps(
    val text: CharSequence?,
    val enabled: Boolean,
    val onClick: (() -> Unit)?,
    val textColor: Int,
    val textSizeSp: Int,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: Int? = null,
    val includeFontPadding: Boolean = false,
    val backgroundColor: Int,
    val borderWidth: Int,
    val borderColor: Int,
    val shape: UiShape,
    val rippleColor: Int,
    val minHeight: Int,
    val paddingHorizontal: Int,
    val paddingVertical: Int,
    val leadingIcon: ImageSource.Resource?,
    val trailingIcon: ImageSource.Resource?,
    val iconTint: Int,
    val iconSize: Int,
    val iconSpacing: Int,
) : NodeSpec
