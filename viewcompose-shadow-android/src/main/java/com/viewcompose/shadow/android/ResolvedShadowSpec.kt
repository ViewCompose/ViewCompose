package com.viewcompose.shadow.android

import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.UiDp

/**
 * Android 阴影后端消费的一层像素级阴影参数。
 * Pixel-resolved parameters for one shadow layer consumed by Android backends.
 */
data class ResolvedShadowLayer(
    val color: Int,
    val blurRadiusPx: Float,
    val spreadRadiusPx: Float,
    val offsetXPx: Float,
    val offsetYPx: Float,
)

/**
 * 一组共享 shape 且保持声明顺序的阴影。
 * A declaration-ordered shadow group sharing one shape.
 */
data class ResolvedShadowGroup(
    val shape: UiShape,
    val shadows: List<ResolvedShadowLayer>,
)

/**
 * 一个节点提交给 Android 阴影后端的完整不可变规格。
 * Complete immutable shadow specification submitted to the Android backend for one node.
 */
data class ResolvedShadowSpec(
    val density: UiDensity,
    val groups: List<ResolvedShadowGroup>,
) {
    val layerCount: Int
        get() = groups.sumOf { it.shadows.size }

    companion object {
        val Empty = ResolvedShadowSpec(
            density = UiDensity.Default,
            groups = emptyList(),
        )
    }
}

/**
 * 在 Android 渲染边界统一解析 density 和默认 shape。
 * Resolves density and the node's default shape once at the Android rendering boundary.
 */
object ShadowSpecResolver {
    private val Rectangle = UiShape.rounded(UiDp.Zero)

    fun resolve(
        elements: List<DropShadowModifierElement>,
        defaultShape: UiShape?,
        density: UiDensity,
    ): ResolvedShadowSpec {
        if (elements.isEmpty()) return ResolvedShadowSpec.Empty
        return ResolvedShadowSpec(
            density = density,
            groups = elements.map { element ->
                ResolvedShadowGroup(
                    shape = element.shape ?: defaultShape ?: Rectangle,
                    shadows = element.shadows.map { shadow ->
                        ResolvedShadowLayer(
                            color = shadow.color,
                            blurRadiusPx = density.toPx(shadow.blurRadius),
                            spreadRadiusPx = density.toPx(shadow.spreadRadius),
                            offsetXPx = density.toPx(shadow.offsetX),
                            offsetYPx = density.toPx(shadow.offsetY),
                        )
                    },
                )
            },
        )
    }
}
