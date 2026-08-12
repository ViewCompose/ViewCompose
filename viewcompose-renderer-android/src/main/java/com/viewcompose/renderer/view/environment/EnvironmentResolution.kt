package com.viewcompose.renderer.view

import android.view.View
import com.viewcompose.renderer.R
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.environment.UiLayoutDirection
import com.viewcompose.ui.unit.UiDimension
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.UiSp
import com.viewcompose.ui.modifier.CornerRadiusModifierElement
import com.viewcompose.ui.modifier.PaddingModifierElement
import com.viewcompose.ui.node.policy.LazyContentPadding

internal fun UiEnvironmentValues.roundToPx(value: UiDp): Int = density.roundToPx(value)

internal fun UiEnvironmentValues.toPx(value: UiDp): Float = density.toPx(value)

internal fun UiEnvironmentValues.toPx(value: UiSp): Float = density.toPx(value)

internal fun UiEnvironmentValues.resolveLayoutDimension(value: UiDimension): Int {
    return when (value) {
        is UiDimension.Exact -> roundToPx(value.value)
        UiDimension.MatchParent -> android.view.ViewGroup.LayoutParams.MATCH_PARENT
    }
}

internal fun View.requireUiEnvironment(): UiEnvironmentValues {
    return checkNotNull(getTag(R.id.viewcompose_environment_values) as? UiEnvironmentValues) {
        "ViewCompose environment must be installed before binding or patching a native View."
    }
}

internal data class PaddingPx(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        val Zero = PaddingPx(0, 0, 0, 0)
    }
}

internal data class CornerRadiiPx(
    val topStart: Int,
    val topEnd: Int,
    val bottomEnd: Int,
    val bottomStart: Int,
) {
    val isUniform: Boolean
        get() = topStart == topEnd && topEnd == bottomEnd && bottomEnd == bottomStart
}

internal fun UiEnvironmentValues.resolve(value: PaddingModifierElement): PaddingPx {
    return PaddingPx(
        left = roundToPx(value.left),
        top = roundToPx(value.top),
        right = roundToPx(value.right),
        bottom = roundToPx(value.bottom),
    )
}

internal fun UiEnvironmentValues.resolvePadding(value: LazyContentPadding): PaddingPx {
    val (left, right) = when (layoutDirection) {
        UiLayoutDirection.Ltr -> value.start to value.end
        UiLayoutDirection.Rtl -> value.end to value.start
    }
    return PaddingPx(
        left = roundToPx(left),
        top = roundToPx(value.top),
        right = roundToPx(right),
        bottom = roundToPx(value.bottom),
    )
}

internal fun UiEnvironmentValues.resolve(value: CornerRadiusModifierElement): CornerRadiiPx {
    return CornerRadiiPx(
        topStart = roundToPx(value.topStart),
        topEnd = roundToPx(value.topEnd),
        bottomEnd = roundToPx(value.bottomEnd),
        bottomStart = roundToPx(value.bottomStart),
    )
}
