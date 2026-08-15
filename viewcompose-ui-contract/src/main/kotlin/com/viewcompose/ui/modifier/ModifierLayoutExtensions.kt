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
 * Appends independently sized logical-edge padding.
 *
 * The renderer maps [start] and [end] from the VNode's captured layout direction on every bind.
 * Existing [padding] overloads remain physical. A later physical or relative padding declaration
 * replaces the earlier declaration as one complete padding value.
 *
 * @sample com.viewcompose.ui.samples.relativeLayoutModifierSample
 * @receiver modifier chain to extend
 * @param start padding at the logical start edge in dp
 * @param top padding at the physical top edge in dp
 * @param end padding at the logical end edge in dp
 * @param bottom padding at the physical bottom edge in dp
 * @return a new modifier chain
 */
fun Modifier.paddingRelative(
    start: UiDp = UiDp.Zero,
    top: UiDp = UiDp.Zero,
    end: UiDp = UiDp.Zero,
    bottom: UiDp = UiDp.Zero,
): Modifier {
    return then(
        RelativePaddingModifierElement(
            start = start,
            top = top,
            end = end,
            bottom = bottom,
        ),
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
 * Adds selected logical system-bar insets to the node's inner padding.
 *
 * The renderer maps [start] and [end] from the VNode's captured layout direction. A later physical
 * or relative system-bar declaration replaces the earlier declaration. Insets remain unconsumed
 * for platform descendants, matching [systemBarsInsetsPadding].
 *
 * @sample com.viewcompose.ui.samples.relativeLayoutModifierSample
 * @receiver modifier chain to extend
 * @param start whether to include the logical start inset
 * @param top whether to include the physical top inset
 * @param end whether to include the logical end inset
 * @param bottom whether to include the physical bottom inset
 * @return a new modifier chain
 */
fun Modifier.systemBarsInsetsPaddingRelative(
    start: Boolean = true,
    top: Boolean = true,
    end: Boolean = true,
    bottom: Boolean = true,
): Modifier {
    return then(
        RelativeSystemBarsInsetsPaddingModifierElement(
            start = start,
            top = top,
            end = end,
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
 * Adds selected logical IME insets to the node's inner padding.
 *
 * The renderer maps [start] and [end] from the VNode's captured layout direction. A later physical
 * or relative IME declaration replaces the earlier declaration. Insets remain unconsumed for
 * platform descendants, matching [imeInsetsPadding].
 *
 * @sample com.viewcompose.ui.samples.relativeLayoutModifierSample
 * @receiver modifier chain to extend
 * @param start whether to include the logical start IME inset
 * @param top whether to include the physical top IME inset
 * @param end whether to include the logical end IME inset
 * @param bottom whether to include the physical bottom IME inset
 * @return a new modifier chain
 */
fun Modifier.imeInsetsPaddingRelative(
    start: Boolean = false,
    top: Boolean = false,
    end: Boolean = false,
    bottom: Boolean = true,
): Modifier {
    return then(
        RelativeImeInsetsPaddingModifierElement(
            start = start,
            top = top,
            end = end,
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
 * Appends independently sized logical-edge margins.
 *
 * The renderer maps [start] and [end] from the VNode's captured layout direction when creating
 * native parent LayoutParams. Existing [margin] overloads remain physical. A later physical or
 * relative margin declaration replaces the earlier declaration as one complete margin value.
 *
 * @sample com.viewcompose.ui.samples.relativeLayoutModifierSample
 * @receiver modifier chain to extend
 * @param start margin at the logical start edge in dp
 * @param top margin at the physical top edge in dp
 * @param end margin at the logical end edge in dp
 * @param bottom margin at the physical bottom edge in dp
 * @return a new modifier chain
 */
fun Modifier.marginRelative(
    start: UiDp = UiDp.Zero,
    top: UiDp = UiDp.Zero,
    end: UiDp = UiDp.Zero,
    bottom: UiDp = UiDp.Zero,
): Modifier {
    return then(
        RelativeMarginModifierElement(
            start = start,
            top = top,
            end = end,
            bottom = bottom,
        ),
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
 * Constrains the modified node to at most [maxWidth].
 *
 * The renderer enforces this through one measurement boundary shared with [maxHeight] and
 * [aspectRatio]. An exact width larger than this bound, or a minimum width larger than this bound,
 * is rejected deterministically during rendering.
 *
 * @sample com.viewcompose.ui.samples.layoutConstraintModifierSample
 * @receiver modifier chain to extend
 * @param maxWidth positive finite maximum width
 * @return a new modifier chain
 * @throws IllegalArgumentException when [maxWidth] is not positive and finite
 */
fun Modifier.maxWidth(maxWidth: UiDp): Modifier {
    require(maxWidth.value.isFinite() && maxWidth.value > 0f) {
        "maxWidth must be positive and finite."
    }
    return then(MaxWidthModifierElement(maxWidth))
}

/**
 * Constrains the modified node to at most [maxHeight].
 *
 * @sample com.viewcompose.ui.samples.layoutConstraintModifierSample
 * @receiver modifier chain to extend
 * @param maxHeight positive finite maximum height
 * @return a new modifier chain
 * @throws IllegalArgumentException when [maxHeight] is not positive and finite
 */
fun Modifier.maxHeight(maxHeight: UiDp): Modifier {
    require(maxHeight.value.isFinite() && maxHeight.value > 0f) {
        "maxHeight must be positive and finite."
    }
    return then(MaxHeightModifierElement(maxHeight))
}

/**
 * Constrains the modified node to the requested width-to-height [ratio].
 *
 * The renderer chooses a size inside incoming and declared min/max bounds. Width constraints are
 * preferred by default; set [matchHeightConstraintsFirst] when height is the authoritative axis.
 *
 * @sample com.viewcompose.ui.samples.layoutConstraintModifierSample
 * @receiver modifier chain to extend
 * @param ratio positive finite width divided by height
 * @param matchHeightConstraintsFirst whether height constraints are considered before width
 * @return a new modifier chain
 * @throws IllegalArgumentException when [ratio] is not positive and finite
 */
fun Modifier.aspectRatio(
    ratio: Float,
    matchHeightConstraintsFirst: Boolean = false,
): Modifier {
    require(ratio.isFinite() && ratio > 0f) { "aspectRatio must be positive and finite." }
    return then(
        AspectRatioModifierElement(
            ratio = ratio,
            matchHeightConstraintsFirst = matchHeightConstraintsFirst,
        ),
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
 * Offsets final placement along logical horizontal and physical vertical axes.
 *
 * Positive [horizontal] moves toward end: right in LTR and left in RTL. Positive [vertical] moves
 * down. The renderer re-resolves the translation after a captured layout-direction change.
 * Existing [offset] remains a physical x/y translation. A later physical or relative offset
 * declaration replaces the earlier declaration.
 *
 * @sample com.viewcompose.ui.samples.relativeLayoutModifierSample
 * @receiver modifier chain to extend
 * @param horizontal logical horizontal offset in dp, positive toward end
 * @param vertical physical vertical offset in dp, positive down
 * @return a new modifier chain
 */
fun Modifier.offsetRelative(
    horizontal: UiDp = UiDp.Zero,
    vertical: UiDp = UiDp.Zero,
): Modifier {
    return then(
        RelativeOffsetModifierElement(
            horizontal = horizontal,
            vertical = vertical,
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
