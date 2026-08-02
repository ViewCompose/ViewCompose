package com.viewcompose.ui.modifier

import com.viewcompose.ui.unit.UiDimension
import com.viewcompose.ui.unit.UiDp

/**
 * Appends equal physical-edge padding inside the modified node.
 *
 * @receiver modifier chain to extend
 * @param all padding applied to every edge in dp
 * @return a new modifier chain
 */
fun Modifier.padding(all: UiDp): Modifier {
    return padding(
        horizontal = all,
        vertical = all,
    )
}

/**
 * Appends symmetric horizontal and vertical physical-edge padding.
 *
 * @receiver modifier chain to extend
 * @param horizontal padding applied to left and right edges
 * @param vertical padding applied to top and bottom edges
 * @return a new modifier chain
 */
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

/**
 * Appends independently sized physical-edge padding.
 *
 * Padding participates in measurement inside the node's requested outer size. Later padding
 * elements override earlier ones during renderer resolution.
 *
 * @receiver modifier chain to extend
 * @param left left padding in dp
 * @param top top padding in dp
 * @param right right padding in dp
 * @param bottom bottom padding in dp
 * @return a new modifier chain
 */
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

/**
 * Adds selected physical system-bar insets to the node's inner padding.
 *
 * The host supplies current platform insets; without a compatible Android host the renderer may
 * resolve them to zero.
 *
 * @receiver modifier chain to extend
 * @param left whether to include the left inset
 * @param top whether to include the top inset
 * @param right whether to include the right inset
 * @param bottom whether to include the bottom inset
 * @return a new modifier chain
 */
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

/**
 * Adds selected physical IME insets to the node's inner padding.
 *
 * Insets follow host updates; the default consumes only the bottom edge used by common soft
 * keyboards.
 *
 * @receiver modifier chain to extend
 * @param left whether to include the left IME inset
 * @param top whether to include the top IME inset
 * @param right whether to include the right IME inset
 * @param bottom whether to include the bottom IME inset
 * @return a new modifier chain
 */
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

/**
 * Appends equal physical-edge margins for the native parent layout parameters.
 *
 * @receiver modifier chain to extend
 * @param all margin applied to every edge in dp
 * @return a new modifier chain
 */
fun Modifier.margin(all: UiDp): Modifier {
    return margin(
        horizontal = all,
        vertical = all,
    )
}

/**
 * Appends symmetric horizontal and vertical physical-edge margins.
 *
 * @receiver modifier chain to extend
 * @param horizontal margin applied to left and right edges
 * @param vertical margin applied to top and bottom edges
 * @return a new modifier chain
 */
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

/**
 * Appends independently sized physical-edge margins.
 *
 * Later margin elements override earlier ones during renderer resolution.
 *
 * @receiver modifier chain to extend
 * @param left left margin in dp
 * @param top top margin in dp
 * @param right right margin in dp
 * @param bottom bottom margin in dp
 * @return a new modifier chain
 */
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

/**
 * Requests exact [width] and [height] for the modified node.
 *
 * @receiver modifier chain to extend
 * @param width requested width in dp
 * @param height requested height in dp
 * @return a new modifier chain
 */
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

/**
 * Requests an exact width while leaving height to existing policy.
 *
 * @receiver modifier chain to extend
 * @param width requested width in dp
 * @return a new modifier chain
 */
fun Modifier.width(width: UiDp): Modifier {
    return then(
        WidthModifierElement(UiDimension.Exact(width)),
    )
}

/**
 * Requests an exact height while leaving width to existing policy.
 *
 * @receiver modifier chain to extend
 * @param height requested height in dp
 * @return a new modifier chain
 */
fun Modifier.height(height: UiDp): Modifier {
    return then(
        HeightModifierElement(UiDimension.Exact(height)),
    )
}

/**
 * Requests a minimum measured height without forcing a larger exact size.
 *
 * @receiver modifier chain to extend
 * @param minHeight minimum height in dp
 * @return a new modifier chain
 */
fun Modifier.minHeight(minHeight: UiDp): Modifier {
    return then(
        MinHeightModifierElement(minHeight),
    )
}

/**
 * Requests a minimum measured width without forcing a larger exact size.
 *
 * @receiver modifier chain to extend
 * @param minWidth minimum width in dp
 * @return a new modifier chain
 */
fun Modifier.minWidth(minWidth: UiDp): Modifier {
    return then(
        MinWidthModifierElement(minWidth),
    )
}

/**
 * Supplies a parent-data identifier consumed by ConstraintLayout helpers.
 *
 * The renderer diagnoses use under an incompatible parent.
 *
 * @receiver modifier chain to extend
 * @param id identifier referenced by the owning constraint set
 * @return a new modifier chain
 */
fun Modifier.layoutId(id: String): Modifier {
    return then(
        LayoutIdModifierElement(layoutId = id),
    )
}

/**
 * Offsets final placement without changing measurement or sibling placement.
 *
 * @receiver modifier chain to extend
 * @param x physical horizontal offset in dp, positive right
 * @param y vertical offset in dp, positive down
 * @return a new modifier chain
 */
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

/**
 * Requests the maximum width offered by the parent.
 *
 * @receiver modifier chain to extend
 * @return a new modifier chain
 */
fun Modifier.fillMaxWidth(): Modifier {
    return then(WidthModifierElement(UiDimension.MatchParent))
}

/**
 * Requests the maximum height offered by the parent.
 *
 * @receiver modifier chain to extend
 * @return a new modifier chain
 */
fun Modifier.fillMaxHeight(): Modifier {
    return then(HeightModifierElement(UiDimension.MatchParent))
}

/**
 * Requests the maximum width and height offered by the parent.
 *
 * @receiver modifier chain to extend
 * @return a new modifier chain
 */
fun Modifier.fillMaxSize(): Modifier {
    return then(
        SizeModifierElement(
            width = UiDimension.MatchParent,
            height = UiDimension.MatchParent,
        ),
    )
}
