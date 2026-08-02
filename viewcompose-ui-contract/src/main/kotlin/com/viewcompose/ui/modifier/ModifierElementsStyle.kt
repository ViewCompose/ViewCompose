package com.viewcompose.ui.modifier

import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.unit.UiDp

/**
 * Applies a packed ARGB background color.
 *
 * @property color packed ARGB color consumed by the renderer
 */
data class BackgroundColorModifierElement(
    val color: Int,
) : ModifierElement

/**
 * Resolves an Android drawable resource as the node background.
 *
 * @property resId Android drawable resource identifier resolved from the rendered View context
 */
data class BackgroundDrawableResModifierElement(
    val resId: Int,
) : ModifierElement

/**
 * Draws a border following the resolved node shape.
 *
 * @property width border thickness in dp
 * @property color packed ARGB border color
 */
data class BorderModifierElement(
    val width: UiDp,
    val color: Int,
) : ModifierElement

/**
 * Describes legacy rounded logical corners for background, border, and clipping.
 *
 * A later general [ShapeModifierElement] replaces this value during renderer resolution.
 *
 * @property topStart top-start radius in dp
 * @property topEnd top-end radius in dp
 * @property bottomEnd bottom-end radius in dp
 * @property bottomStart bottom-start radius in dp
 */
data class CornerRadiusModifierElement(
    val topStart: UiDp,
    val topEnd: UiDp,
    val bottomEnd: UiDp,
    val bottomStart: UiDp,
) : ModifierElement {
    /** Whether all four radii are equal. */
    val isUniform: Boolean
        get() = topStart == topEnd && topEnd == bottomEnd && bottomEnd == bottomStart
}

/**
 * Applies a platform-neutral shape to background, border, clipping, and default shadows.
 *
 * @property shape logical-corner shape resolved using the node environment
 */
data class ShapeModifierElement(
    val shape: UiShape,
) : ModifierElement

/**
 * Enables or disables clipping to the resolved node shape or bounds.
 *
 * @property clip whether descendant and content drawing is clipped
 */
data class ClipModifierElement(
    val clip: Boolean = true,
) : ModifierElement

/**
 * Applies native platform elevation independently of exact shadow modifiers.
 *
 * @property elevation platform elevation in dp
 */
data class ElevationModifierElement(
    val elevation: UiDp,
) : ModifierElement

/**
 * Stores an ordered, non-empty group of outer shadows with an optional shape override.
 *
 * @property shadows shadow layers drawn in declaration order
 * @property shape explicit shared outline, or `null` to use the node's resolved shape
 * @throws IllegalArgumentException if [shadows] is empty
 */
data class DropShadowModifierElement(
    val shadows: List<UiShadow>,
    val shape: UiShape? = null,
) : ModifierElement {
    init {
        require(shadows.isNotEmpty()) {
            "DropShadowModifierElement requires at least one shadow."
        }
    }
}

/**
 * Stores an ordered, non-empty group of inner shadows with an optional shape override.
 *
 * @property shadows shadow layers drawn over node content in declaration order
 * @property shape explicit shared outline, or `null` to use the node's resolved shape
 * @throws IllegalArgumentException if [shadows] is empty
 */
data class InnerShadowModifierElement(
    val shadows: List<UiShadow>,
    val shape: UiShape? = null,
) : ModifierElement {
    init {
        require(shadows.isNotEmpty()) {
            "InnerShadowModifierElement requires at least one shadow."
        }
    }
}

/**
 * Applies node opacity through the native rendering layer.
 *
 * @property alpha requested opacity where conventional visible values use `0.0..1.0`; this model
 * does not coerce the value before the platform receives it
 */
data class AlphaModifierElement(
    val alpha: Float,
) : ModifierElement

/**
 * Adds a finite sibling-order contribution without changing native elevation.
 *
 * Multiple z-index elements are summed by the renderer. Siblings with equal totals retain
 * declarative order.
 *
 * @property zIndex finite ordering contribution
 * @throws IllegalArgumentException if [zIndex] is non-finite
 */
data class ZIndexModifierElement(
    val zIndex: Float,
) : ModifierElement {
    init {
        require(zIndex.isFinite()) {
            "ZIndexModifierElement.zIndex must be finite."
        }
    }
}

/**
 * Defines a transform pivot as fractions of the rendered View bounds.
 *
 * Fractions are not clamped, allowing pivots outside the bounds.
 *
 * @property pivotFractionX horizontal fraction where `0` is left and `1` is right
 * @property pivotFractionY vertical fraction where `0` is top and `1` is bottom
 */
data class TransformOrigin(
    val pivotFractionX: Float,
    val pivotFractionY: Float,
) {
    /** Provides common transform pivots. */
    companion object {
        /** Pivot at the center of both axes. */
        val Center = TransformOrigin(
            pivotFractionX = 0.5f,
            pivotFractionY = 0.5f,
        )
    }
}

/**
 * Applies optional native View transform and clipping properties as one layer contract.
 *
 * `null` leaves the renderer default for that property. Translation values are physical pixels,
 * rotations are degrees, and alpha conventionally uses `0.0..1.0`; this model performs no range
 * coercion. A graphics-layer alpha takes precedence over [AlphaModifierElement].
 *
 * @property scaleX horizontal scale factor
 * @property scaleY vertical scale factor
 * @property rotationZ clockwise two-dimensional rotation in degrees
 * @property rotationX rotation around the horizontal axis in degrees
 * @property rotationY rotation around the vertical axis in degrees
 * @property translationX physical horizontal translation in pixels
 * @property translationY physical vertical translation in pixels
 * @property alpha native layer opacity
 * @property transformOrigin fractional pivot, or `null` for the native default
 * @property clip whether to clip to the resolved shape or bounds
 */
data class GraphicsLayerModifierElement(
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    val rotationZ: Float? = null,
    val rotationX: Float? = null,
    val rotationY: Float? = null,
    val translationX: Float? = null,
    val translationY: Float? = null,
    val alpha: Float? = null,
    val transformOrigin: TransformOrigin? = null,
    val clip: Boolean? = null,
) : ModifierElement
