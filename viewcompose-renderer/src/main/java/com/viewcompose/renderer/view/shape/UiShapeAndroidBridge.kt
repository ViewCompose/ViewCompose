package com.viewcompose.renderer.view.shape

import android.view.View
import com.google.android.material.shape.AbsoluteCornerSize
import com.google.android.material.shape.CutCornerTreatment
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.RoundedCornerTreatment
import com.google.android.material.shape.ShapeAppearanceModel
import com.viewcompose.ui.shape.UiCorner
import com.viewcompose.ui.shape.UiCornerFamily
import com.viewcompose.ui.shape.UiCornerSize
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.UiDp

/**
 * 将框架 UiShape 转换为 Material ShapeAppearanceModel。
 * Converts framework UiShape to Material ShapeAppearanceModel.
 */
internal fun UiShape?.toShapeAppearanceModel(
    layoutDirection: Int,
    density: UiDensity,
): ShapeAppearanceModel {
    val resolvedShape = this ?: UiShape.rounded(UiDp.Zero)
    val topLeft = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        resolvedShape.topEnd
    } else {
        resolvedShape.topStart
    }
    val topRight = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        resolvedShape.topStart
    } else {
        resolvedShape.topEnd
    }
    val bottomRight = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        resolvedShape.bottomStart
    } else {
        resolvedShape.bottomEnd
    }
    val bottomLeft = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        resolvedShape.bottomEnd
    } else {
        resolvedShape.bottomStart
    }
    return ShapeAppearanceModel.builder()
        .applyCorner(CornerPosition.TopLeft, topLeft, density)
        .applyCorner(CornerPosition.TopRight, topRight, density)
        .applyCorner(CornerPosition.BottomRight, bottomRight, density)
        .applyCorner(CornerPosition.BottomLeft, bottomLeft, density)
        .build()
}

private fun ShapeAppearanceModel.Builder.applyCorner(
    position: CornerPosition,
    corner: UiCorner,
    density: UiDensity,
): ShapeAppearanceModel.Builder {
    val treatment = when (corner.family) {
        UiCornerFamily.Rounded -> RoundedCornerTreatment()
        UiCornerFamily.Cut -> CutCornerTreatment()
    }
    val size = when (val cornerSize = corner.size) {
        is UiCornerSize.Absolute -> AbsoluteCornerSize(density.toPx(cornerSize.size))
        is UiCornerSize.Relative -> RelativeCornerSize(cornerSize.fraction)
    }
    when (position) {
        CornerPosition.TopLeft -> {
            setTopLeftCorner(treatment)
            setTopLeftCornerSize(size)
        }
        CornerPosition.TopRight -> {
            setTopRightCorner(treatment)
            setTopRightCornerSize(size)
        }
        CornerPosition.BottomRight -> {
            setBottomRightCorner(treatment)
            setBottomRightCornerSize(size)
        }
        CornerPosition.BottomLeft -> {
            setBottomLeftCorner(treatment)
            setBottomLeftCornerSize(size)
        }
    }
    return this
}

private enum class CornerPosition {
    TopLeft,
    TopRight,
    BottomRight,
    BottomLeft,
}
