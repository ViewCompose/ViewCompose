package com.viewcompose.shadow.android

import com.viewcompose.ui.modifier.DropShadowModifierElement
import com.viewcompose.ui.modifier.InnerShadowModifierElement
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.UiDp

/**
 * Stores one shadow layer after logical dimensions have been resolved to physical pixels.
 *
 * Values are retained without additional validation. Layers remain ordered inside their group and
 * are painted in declaration order by the Android rasterizer.
 *
 * @property color packed Android ARGB color
 * @property blurRadiusPx mask-filter blur radius in physical pixels
 * @property spreadRadiusPx signed expansion of the shape bounds in physical pixels
 * @property offsetXPx horizontal offset in physical pixels; positive values move right
 * @property offsetYPx vertical offset in physical pixels; positive values move down
 */
data class ResolvedShadowLayer(
    val color: Int,
    val blurRadiusPx: Float,
    val spreadRadiusPx: Float,
    val offsetXPx: Float,
    val offsetYPx: Float,
)

/**
 * Groups declaration-ordered shadow layers that share one outline.
 *
 * The [shadows] list is retained as supplied; callers should treat it as immutable after creation.
 *
 * @property shape logical outline resolved against bounds, density, and layout direction at raster time
 * @property shadows ordered layers painted from first to last
 */
data class ResolvedShadowGroup(
    val shape: UiShape,
    val shadows: List<ResolvedShadowLayer>,
)

/**
 * Describes all resolved drop shadows for one Android `View`.
 *
 * The lists are retained rather than defensively copied. Treat the object graph as immutable so
 * equality remains a valid raster-cache key.
 *
 * @property density density used to resolve corner sizes during rasterization
 * @property groups declaration-ordered shape and shadow groups
 */
data class ResolvedShadowSpec(
    val density: UiDensity,
    val groups: List<ResolvedShadowGroup>,
) {
    /** Returns the current total number of layers across [groups]. */
    val layerCount: Int
        get() = groups.sumOf { it.shadows.size }

    /** Provides reusable shadow specifications. */
    companion object {
        /** Stable empty specification used when no drop-shadow modifier is present. */
        val Empty = ResolvedShadowSpec(
            density = UiDensity.Default,
            groups = emptyList(),
        )
    }
}

/**
 * Describes all resolved inner shadows for one Android `View` foreground plane.
 *
 * The lists are retained rather than defensively copied. Treat the object graph as immutable so
 * equality remains a valid raster-cache key.
 *
 * @property density density used to resolve corner sizes during rasterization
 * @property groups declaration-ordered shape and shadow groups
 */
data class ResolvedInnerShadowSpec(
    val density: UiDensity,
    val groups: List<ResolvedShadowGroup>,
) {
    /** Returns the current total number of layers across [groups]. */
    val layerCount: Int
        get() = groups.sumOf { it.shadows.size }

    /** Provides reusable inner-shadow specifications. */
    companion object {
        /** Stable empty specification used when no inner-shadow modifier is present. */
        val Empty = ResolvedInnerShadowSpec(
            density = UiDensity.Default,
            groups = emptyList(),
        )
    }
}

/** Resolves declarative drop-shadow elements into Android pixel-space specifications. */
object ShadowSpecResolver {
    private val Rectangle = UiShape.rounded(UiDp.Zero)

    /**
     * Converts every layer to physical pixels while preserving element and layer order.
     *
     * An element shape wins over [defaultShape]; a zero-corner rectangle is the final fallback. An
     * empty [elements] list returns [ResolvedShadowSpec.Empty] and intentionally discards [density].
     *
     * @sample com.viewcompose.shadow.android.samples.resolveShadowSpecSample
     * @param elements ordered drop-shadow modifier elements from one node
     * @param defaultShape node outline used when an element has no explicit shape
     * @param density logical-to-physical conversion used for every layer
     * @return a pixel-resolved specification, or the stable empty instance
     */
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

/** Resolves declarative inner-shadow elements into Android pixel-space specifications. */
object InnerShadowSpecResolver {
    private val Rectangle = UiShape.rounded(UiDp.Zero)

    /**
     * Converts every layer to physical pixels while preserving element and layer order.
     *
     * An element shape wins over [defaultShape]; a zero-corner rectangle is the final fallback. An
     * empty [elements] list returns [ResolvedInnerShadowSpec.Empty] and intentionally discards
     * [density].
     *
     * @sample com.viewcompose.shadow.android.samples.resolveInnerShadowSpecSample
     * @param elements ordered inner-shadow modifier elements from one node
     * @param defaultShape node outline used when an element has no explicit shape
     * @param density logical-to-physical conversion used for every layer
     * @return a pixel-resolved specification, or the stable empty instance
     */
    fun resolve(
        elements: List<InnerShadowModifierElement>,
        defaultShape: UiShape?,
        density: UiDensity,
    ): ResolvedInnerShadowSpec {
        if (elements.isEmpty()) return ResolvedInnerShadowSpec.Empty
        return ResolvedInnerShadowSpec(
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
