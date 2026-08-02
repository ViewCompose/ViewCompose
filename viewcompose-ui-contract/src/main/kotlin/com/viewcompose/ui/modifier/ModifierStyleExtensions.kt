package com.viewcompose.ui.modifier

import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Appends a packed ARGB background color.
 *
 * Later background-color elements override earlier ones.
 *
 * @receiver modifier chain to extend
 * @param color packed ARGB color
 * @return a new modifier chain
 */
fun Modifier.backgroundColor(color: Int): Modifier {
    return then(
        BackgroundColorModifierElement(color),
    )
}

/**
 * Appends an Android drawable-resource background.
 *
 * The renderer resolves [resId] from the native View context so resource qualifiers and theme
 * attributes follow that host. This platform-specific contract is retained here as an integer to
 * keep the model free of Android classes.
 *
 * @receiver modifier chain to extend
 * @param resId Android drawable resource identifier
 * @return a new modifier chain
 */
fun Modifier.backgroundDrawableRes(resId: Int): Modifier {
    return then(
        BackgroundDrawableResModifierElement(resId),
    )
}

/**
 * Appends a border following the currently resolved shape.
 *
 * @receiver modifier chain to extend
 * @param width border thickness in dp
 * @param color packed ARGB border color
 * @return a new modifier chain
 */
fun Modifier.border(
    width: UiDp,
    color: Int,
): Modifier {
    return then(
        BorderModifierElement(
            width = width,
            color = color,
        ),
    )
}

/**
 * Appends one rounded radius for every logical corner.
 *
 * @receiver modifier chain to extend
 * @param radius corner radius in dp
 * @return a new modifier chain
 */
fun Modifier.cornerRadius(radius: UiDp): Modifier {
    return cornerRadius(top = radius, bottom = radius)
}

/**
 * Appends separate rounded radii for the top and bottom corner pairs.
 *
 * @receiver modifier chain to extend
 * @param top radius for top-start and top-end
 * @param bottom radius for bottom-start and bottom-end
 * @return a new modifier chain
 */
fun Modifier.cornerRadius(
    top: UiDp = UiDp.Zero,
    bottom: UiDp = UiDp.Zero,
): Modifier {
    return cornerRadius(
        topStart = top,
        topEnd = top,
        bottomEnd = bottom,
        bottomStart = bottom,
    )
}

/**
 * Appends a general shape for background, border, clipping, and default shadow outlines.
 *
 * Shape and legacy corner-radius elements are mutually overriding in chain order.
 *
 * @receiver modifier chain to extend
 * @param shape logical-corner shape to resolve at render time
 * @return a new modifier chain
 */
fun Modifier.shape(shape: UiShape): Modifier {
    return then(ShapeModifierElement(shape))
}

/**
 * Appends independently sized rounded logical corners.
 *
 * Corner-radius and general shape elements are mutually overriding in chain order.
 *
 * @receiver modifier chain to extend
 * @param topStart top-start radius in dp
 * @param topEnd top-end radius in dp
 * @param bottomEnd bottom-end radius in dp
 * @param bottomStart bottom-start radius in dp
 * @return a new modifier chain
 */
fun Modifier.cornerRadius(
    topStart: UiDp = UiDp.Zero,
    topEnd: UiDp = UiDp.Zero,
    bottomEnd: UiDp = UiDp.Zero,
    bottomStart: UiDp = UiDp.Zero,
): Modifier {
    return then(
        CornerRadiusModifierElement(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
        ),
    )
}

/**
 * Enables clipping to the resolved shape or node bounds.
 *
 * @receiver modifier chain to extend
 * @return a new modifier chain
 */
fun Modifier.clip(): Modifier {
    return then(
        ClipModifierElement(clip = true),
    )
}

/**
 * Appends native platform elevation independently of exact shadow rendering.
 *
 * Elevation affects the platform shadow but ViewCompose sibling drawing order remains controlled by
 * [zIndex].
 *
 * @receiver modifier chain to extend
 * @param elevation native elevation in dp
 * @return a new modifier chain
 */
fun Modifier.elevation(elevation: UiDp): Modifier {
    return then(
        ElevationModifierElement(elevation),
    )
}

/**
 * Appends one exact outer shadow independently of native elevation.
 *
 * @receiver modifier chain to extend
 * @param shadow platform-neutral shadow layer
 * @param shape explicit outline, or `null` to use the node's resolved shape
 * @return a new modifier chain
 */
fun Modifier.dropShadow(
    shadow: UiShadow,
    shape: UiShape? = null,
): Modifier {
    return dropShadows(
        shadows = listOf(shadow),
        shape = shape,
    )
}

/**
 * Appends exact outer shadows drawn in list and modifier declaration order.
 *
 * The list is copied. An empty list is a no-op and returns the same modifier instance.
 *
 * @receiver modifier chain to extend
 * @param shadows outer shadow layers in draw order
 * @param shape explicit shared outline, or `null` to use the node's resolved shape
 * @return this receiver for an empty list, otherwise a new modifier chain
 */
fun Modifier.dropShadows(
    shadows: List<UiShadow>,
    shape: UiShape? = null,
): Modifier {
    if (shadows.isEmpty()) return this
    return then(
        DropShadowModifierElement(
            shadows = shadows.toList(),
            shape = shape,
        ),
    )
}

/**
 * Appends one exact inner shadow drawn above node content.
 *
 * @receiver modifier chain to extend
 * @param shadow platform-neutral inner shadow layer
 * @param shape explicit outline, or `null` to use the node's resolved shape
 * @return a new modifier chain
 */
fun Modifier.innerShadow(
    shadow: UiShadow,
    shape: UiShape? = null,
): Modifier {
    return innerShadows(
        shadows = listOf(shadow),
        shape = shape,
    )
}

/**
 * Appends exact inner shadows drawn above content in list and modifier declaration order.
 *
 * The list is copied. An empty list is a no-op and returns the same modifier instance.
 *
 * @receiver modifier chain to extend
 * @param shadows inner shadow layers in draw order
 * @param shape explicit shared outline, or `null` to use the node's resolved shape
 * @return this receiver for an empty list, otherwise a new modifier chain
 */
fun Modifier.innerShadows(
    shadows: List<UiShadow>,
    shape: UiShape? = null,
): Modifier {
    if (shadows.isEmpty()) return this
    return then(
        InnerShadowModifierElement(
            shadows = shadows.toList(),
            shape = shape,
        ),
    )
}

/**
 * Appends native node opacity.
 *
 * A later graphics-layer alpha takes precedence. This API does not coerce [alpha]; conventional
 * visible values use `0.0..1.0`.
 *
 * @receiver modifier chain to extend
 * @param alpha requested opacity
 * @return a new modifier chain
 */
fun Modifier.alpha(alpha: Float): Modifier {
    return then(
        AlphaModifierElement(alpha),
    )
}

/**
 * Adds [zIndex] to the node's sibling drawing-order total.
 *
 * Multiple z-index modifiers accumulate. Equal totals retain declarative sibling order, and the
 * value does not alter native elevation or shadow geometry.
 *
 * @receiver modifier chain to extend
 * @param zIndex finite ordering contribution
 * @return a new modifier chain
 * @throws IllegalArgumentException if [zIndex] is non-finite
 */
fun Modifier.zIndex(zIndex: Float): Modifier {
    require(zIndex.isFinite()) {
        "Modifier.zIndex must be finite."
    }
    return then(
        ZIndexModifierElement(zIndex),
    )
}

/**
 * Appends optional native View transforms, opacity, pivot, and clipping as one layer contract.
 *
 * `null` leaves each property at its renderer default. Translation uses physical pixels rather than
 * dp; rotation uses degrees. Later graphics-layer elements override earlier ones as a whole.
 *
 * @receiver modifier chain to extend
 * @param scaleX horizontal scale factor
 * @param scaleY vertical scale factor
 * @param rotationZ clockwise two-dimensional rotation in degrees
 * @param rotationX rotation around the horizontal axis in degrees
 * @param rotationY rotation around the vertical axis in degrees
 * @param translationX physical horizontal translation in pixels
 * @param translationY physical vertical translation in pixels
 * @param alpha native layer opacity, conventionally `0.0..1.0`
 * @param transformOrigin fractional pivot, or `null` for the native default
 * @param clip whether to clip to the resolved shape or bounds
 * @return a new modifier chain
 */
fun Modifier.graphicsLayer(
    scaleX: Float? = null,
    scaleY: Float? = null,
    rotationZ: Float? = null,
    rotationX: Float? = null,
    rotationY: Float? = null,
    translationX: Float? = null,
    translationY: Float? = null,
    alpha: Float? = null,
    transformOrigin: TransformOrigin? = null,
    clip: Boolean? = null,
): Modifier {
    return then(
        GraphicsLayerModifierElement(
            scaleX = scaleX,
            scaleY = scaleY,
            rotationZ = rotationZ,
            rotationX = rotationX,
            rotationY = rotationY,
            translationX = translationX,
            translationY = translationY,
            alpha = alpha,
            transformOrigin = transformOrigin,
            clip = clip,
        ),
    )
}
