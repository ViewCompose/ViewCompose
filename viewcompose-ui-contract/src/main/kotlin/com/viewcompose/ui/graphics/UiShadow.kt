package com.viewcompose.ui.graphics

import com.viewcompose.ui.unit.UiDp

/**
 * 一层平台无关的精确阴影参数。
 * Platform-independent parameters for one exact shadow layer.
 *
 * [blurRadius] 不能为负；[spreadRadius] 允许为负，用于向轮廓内部收缩阴影 mask。
 * [blurRadius] must be non-negative. [spreadRadius] may be negative to contract the shadow mask.
 */
data class UiShadow(
    val color: Int = DefaultColor,
    val blurRadius: UiDp,
    val spreadRadius: UiDp = UiDp.Zero,
    val offsetX: UiDp = UiDp.Zero,
    val offsetY: UiDp = UiDp.Zero,
) {
    init {
        require(blurRadius.value.isFinite() && blurRadius >= UiDp.Zero) {
            "UiShadow.blurRadius must be finite and non-negative."
        }
        require(spreadRadius.value.isFinite()) {
            "UiShadow.spreadRadius must be finite."
        }
        require(offsetX.value.isFinite()) {
            "UiShadow.offsetX must be finite."
        }
        require(offsetY.value.isFinite()) {
            "UiShadow.offsetY must be finite."
        }
    }

    companion object {
        const val DefaultColor: Int = 0x40000000
    }
}
