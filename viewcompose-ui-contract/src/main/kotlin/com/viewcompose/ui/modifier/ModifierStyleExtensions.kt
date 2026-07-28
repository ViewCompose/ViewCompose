package com.viewcompose.ui.modifier

import com.viewcompose.ui.shape.UiShape

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
    width: Int,
    color: Int,
): Modifier {
    return then(
        BorderModifierElement(
            width = width,
            color = color,
        ),
    )
}

fun Modifier.cornerRadius(radius: Int): Modifier {
    return cornerRadius(top = radius, bottom = radius)
}

fun Modifier.cornerRadius(
    top: Int = 0,
    bottom: Int = 0,
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
    topStart: Int = 0,
    topEnd: Int = 0,
    bottomEnd: Int = 0,
    bottomStart: Int = 0,
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

fun Modifier.elevation(elevation: Int): Modifier {
    return then(
        ElevationModifierElement(elevation),
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
