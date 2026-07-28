package com.viewcompose.ui.modifier

/**
 * 布局类 modifier 的声明式扩展入口。
 * Declarative extension entrypoints for layout modifiers.
 */
fun Modifier.padding(all: Int): Modifier {
    return padding(
        horizontal = all,
        vertical = all,
    )
}

fun Modifier.padding(
    horizontal: Int = 0,
    vertical: Int = 0,
): Modifier {
    return padding(
        left = horizontal,
        top = vertical,
        right = horizontal,
        bottom = vertical,
    )
}

fun Modifier.padding(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): Modifier {
    return then(
        PaddingModifierElement(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        ),
    )
}

fun Modifier.systemBarsInsetsPadding(
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true,
): Modifier {
    return then(
        SystemBarsInsetsPaddingModifierElement(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        ),
    )
}

fun Modifier.imeInsetsPadding(
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = true,
): Modifier {
    return then(
        ImeInsetsPaddingModifierElement(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        ),
    )
}

fun Modifier.margin(all: Int): Modifier {
    return margin(
        horizontal = all,
        vertical = all,
    )
}

fun Modifier.margin(
    horizontal: Int = 0,
    vertical: Int = 0,
): Modifier {
    return margin(
        left = horizontal,
        top = vertical,
        right = horizontal,
        bottom = vertical,
    )
}

fun Modifier.margin(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): Modifier {
    return then(
        MarginModifierElement(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        ),
    )
}

fun Modifier.size(
    width: Int,
    height: Int,
): Modifier {
    return then(
        SizeModifierElement(
            width = width,
            height = height,
        ),
    )
}

fun Modifier.width(width: Int): Modifier {
    return then(
        WidthModifierElement(width),
    )
}

fun Modifier.height(height: Int): Modifier {
    return then(
        HeightModifierElement(height),
    )
}

fun Modifier.minHeight(minHeight: Int): Modifier {
    return then(
        MinHeightModifierElement(minHeight),
    )
}

fun Modifier.minWidth(minWidth: Int): Modifier {
    return then(
        MinWidthModifierElement(minWidth),
    )
}

fun Modifier.layoutId(id: String): Modifier {
    return then(
        LayoutIdModifierElement(layoutId = id),
    )
}

fun Modifier.offset(
    x: Float = 0f,
    y: Float = 0f,
): Modifier {
    return then(
        OffsetModifierElement(
            x = x,
            y = y,
        ),
    )
}

fun Modifier.fillMaxWidth(): Modifier {
    return width(MATCH_PARENT)
}

fun Modifier.fillMaxHeight(): Modifier {
    return height(MATCH_PARENT)
}

fun Modifier.fillMaxSize(): Modifier {
    return size(
        width = MATCH_PARENT,
        height = MATCH_PARENT,
    )
}

private const val MATCH_PARENT: Int = -1
