package com.viewcompose.ui.modifier

import com.viewcompose.ui.graphics.UiShadow
import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * 样式类 modifier 的声明式扩展入口。
 * Declarative extension entrypoints for style modifiers.
 */
fun Modifier.backgroundColor(color: Int): Modifier {
    return then(
        BackgroundColorModifierElement(color),
    )
}

fun Modifier.backgroundDrawableRes(resId: Int): Modifier {
    return then(
        BackgroundDrawableResModifierElement(resId),
    )
}

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

fun Modifier.cornerRadius(radius: UiDp): Modifier {
    return cornerRadius(top = radius, bottom = radius)
}

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

fun Modifier.shape(shape: UiShape): Modifier {
    return then(ShapeModifierElement(shape))
}

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

fun Modifier.clip(): Modifier {
    return then(
        ClipModifierElement(clip = true),
    )
}

fun Modifier.elevation(elevation: UiDp): Modifier {
    return then(
        ElevationModifierElement(elevation),
    )
}

/**
 * 添加一层精确外阴影。该能力与 Material elevation 独立。
 * Adds one exact drop shadow independently from Material elevation.
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
 * 添加一组按声明顺序绘制的精确外阴影。
 * Adds a group of exact drop shadows drawn in declaration order.
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

fun Modifier.alpha(alpha: Float): Modifier {
    return then(
        AlphaModifierElement(alpha),
    )
}

fun Modifier.zIndex(zIndex: Float): Modifier {
    return then(
        ZIndexModifierElement(zIndex),
    )
}

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
