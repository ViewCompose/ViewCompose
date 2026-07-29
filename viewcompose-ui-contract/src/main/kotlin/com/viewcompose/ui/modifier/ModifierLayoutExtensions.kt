package com.viewcompose.ui.modifier

import com.viewcompose.ui.unit.UiDimension
import com.viewcompose.ui.unit.UiDp

/**
 * 布局类 modifier 的声明式扩展入口。
 * Declarative extension entrypoints for layout modifiers.
 */
fun Modifier.padding(all: UiDp): Modifier {
    return padding(
        horizontal = all,
        vertical = all,
    )
}

fun Modifier.padding(
    horizontal: UiDp = UiDp.Zero,
    vertical: UiDp = UiDp.Zero,
): Modifier {
    return padding(
        left = horizontal,
        top = vertical,
        right = horizontal,
        bottom = vertical,
    )
}

fun Modifier.padding(
    left: UiDp = UiDp.Zero,
    top: UiDp = UiDp.Zero,
    right: UiDp = UiDp.Zero,
    bottom: UiDp = UiDp.Zero,
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

fun Modifier.margin(all: UiDp): Modifier {
    return margin(
        horizontal = all,
        vertical = all,
    )
}

fun Modifier.margin(
    horizontal: UiDp = UiDp.Zero,
    vertical: UiDp = UiDp.Zero,
): Modifier {
    return margin(
        left = horizontal,
        top = vertical,
        right = horizontal,
        bottom = vertical,
    )
}

fun Modifier.margin(
    left: UiDp = UiDp.Zero,
    top: UiDp = UiDp.Zero,
    right: UiDp = UiDp.Zero,
    bottom: UiDp = UiDp.Zero,
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
    width: UiDp,
    height: UiDp,
): Modifier {
    return then(
        SizeModifierElement(
            width = UiDimension.Exact(width),
            height = UiDimension.Exact(height),
        ),
    )
}

fun Modifier.width(width: UiDp): Modifier {
    return then(
        WidthModifierElement(UiDimension.Exact(width)),
    )
}

fun Modifier.height(height: UiDp): Modifier {
    return then(
        HeightModifierElement(UiDimension.Exact(height)),
    )
}

fun Modifier.minHeight(minHeight: UiDp): Modifier {
    return then(
        MinHeightModifierElement(minHeight),
    )
}

fun Modifier.minWidth(minWidth: UiDp): Modifier {
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
    x: UiDp = UiDp.Zero,
    y: UiDp = UiDp.Zero,
): Modifier {
    return then(
        OffsetModifierElement(
            x = x,
            y = y,
        ),
    )
}

fun Modifier.fillMaxWidth(): Modifier {
    return then(WidthModifierElement(UiDimension.MatchParent))
}

fun Modifier.fillMaxHeight(): Modifier {
    return then(HeightModifierElement(UiDimension.MatchParent))
}

fun Modifier.fillMaxSize(): Modifier {
    return then(
        SizeModifierElement(
            width = UiDimension.MatchParent,
            height = UiDimension.MatchParent,
        ),
    )
}
