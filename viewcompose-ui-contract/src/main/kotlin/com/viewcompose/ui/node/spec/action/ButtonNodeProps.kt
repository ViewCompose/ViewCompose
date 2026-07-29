package com.viewcompose.ui.node.spec

import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp

/**
 * Button 节点传给 renderer 的完整属性快照。
 * Complete property snapshot passed to the renderer for a Button node.
 */
data class ButtonNodeProps(
    val text: CharSequence?,
    val enabled: Boolean,
    val onClick: (() -> Unit)?,
    val textColor: Int,
    val textSizeSp: UiSp,
    val fontWeight: Int? = null,
    val fontFamily: UiFontFamily? = null,
    val letterSpacingEm: Float? = null,
    val lineHeightSp: UiSp? = null,
    val includeFontPadding: Boolean = false,
    val backgroundColor: Int,
    val borderWidth: UiDp,
    val borderColor: Int,
    val shape: UiShape,
    val rippleColor: Int,
    val minHeight: UiDp,
    val paddingHorizontal: UiDp,
    val paddingVertical: UiDp,
    val leadingIcon: ImageSource.Resource?,
    val trailingIcon: ImageSource.Resource?,
    val iconTint: Int,
    val iconSize: UiDp,
    val iconSpacing: UiDp,
) : NodeSpec
