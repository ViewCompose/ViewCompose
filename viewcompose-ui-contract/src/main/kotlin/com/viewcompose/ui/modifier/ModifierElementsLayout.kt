package com.viewcompose.ui.modifier

import com.viewcompose.ui.layout.BoxAlignment
import com.viewcompose.ui.layout.HorizontalAlignment
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.unit.UiDimension
import com.viewcompose.ui.unit.UiDp

/**
 * Adds physical-edge padding inside the node's measured content bounds.
 *
 * @property left left padding in dp
 * @property top top padding in dp
 * @property right right padding in dp
 * @property bottom bottom padding in dp
 */
data class PaddingModifierElement(
    val left: UiDp,
    val top: UiDp,
    val right: UiDp,
    val bottom: UiDp,
) : ModifierElement

/**
 * Requests selected physical system-bar insets as additional inner padding.
 *
 * @property left whether to consume the left system-bar inset
 * @property top whether to consume the top system-bar inset
 * @property right whether to consume the right system-bar inset
 * @property bottom whether to consume the bottom system-bar inset
 */
data class SystemBarsInsetsPaddingModifierElement(
    val left: Boolean,
    val top: Boolean,
    val right: Boolean,
    val bottom: Boolean,
) : ModifierElement

/**
 * Requests selected physical IME insets as additional inner padding.
 *
 * @property left whether to consume the left IME inset
 * @property top whether to consume the top IME inset
 * @property right whether to consume the right IME inset
 * @property bottom whether to consume the bottom IME inset
 */
data class ImeInsetsPaddingModifierElement(
    val left: Boolean,
    val top: Boolean,
    val right: Boolean,
    val bottom: Boolean,
) : ModifierElement

/**
 * Supplies physical-edge margins to the native parent layout parameters.
 *
 * @property left left margin in dp
 * @property top top margin in dp
 * @property right right margin in dp
 * @property bottom bottom margin in dp
 */
data class MarginModifierElement(
    val left: UiDp,
    val top: UiDp,
    val right: UiDp,
    val bottom: UiDp,
) : ModifierElement

/**
 * Requests both width and height measurement policies.
 *
 * @property width exact or parent-filling width
 * @property height exact or parent-filling height
 */
data class SizeModifierElement(
    val width: UiDimension,
    val height: UiDimension,
) : ModifierElement

/**
 * Requests one width measurement policy.
 *
 * @property width exact or parent-filling width
 */
data class WidthModifierElement(
    val width: UiDimension,
) : ModifierElement

/**
 * Requests one height measurement policy.
 *
 * @property height exact or parent-filling height
 */
data class HeightModifierElement(
    val height: UiDimension,
) : ModifierElement

/**
 * Supplies a minimum measured height.
 *
 * @property minHeight minimum height in dp
 */
data class MinHeightModifierElement(
    val minHeight: UiDp,
) : ModifierElement

/**
 * Supplies a minimum measured width.
 *
 * @property minWidth minimum width in dp
 */
data class MinWidthModifierElement(
    val minWidth: UiDp,
) : ModifierElement

/**
 * Publishes [layoutId] as parent data for ConstraintLayout and related helpers.
 *
 * A renderer diagnoses use under an incompatible parent; the element has no standalone layout
 * effect outside a consuming parent.
 *
 * @property layoutId caller-defined parent-data identifier
 */
data class LayoutIdModifierElement(
    val layoutId: String,
) : ModifierElement

/**
 * Supplies one ConstraintLayout item contract as parent data.
 *
 * @property constraint links and dimension policy for the child
 * @property referenceId optional DSL reference associated with the child
 */
data class ConstraintModifierElement(
    val constraint: ConstraintItemSpec,
    val referenceId: String? = null,
) : ModifierElement

/**
 * Supplies [weight] as row/column parent data for distributing remaining main-axis space.
 *
 * Renderers diagnose use under an incompatible parent. Scoped DSL creation validates the range;
 * direct model construction does not.
 *
 * @property weight relative positive share of remaining main-axis space
 */
data class WeightModifierElement(
    val weight: Float,
) : ModifierElement

/**
 * Supplies alignment as Box parent data for one child.
 *
 * @property alignment requested logical box position
 */
data class BoxAlignModifierElement(
    val alignment: BoxAlignment,
) : ModifierElement

/**
 * Supplies alignment as Column horizontal parent data for one child.
 *
 * @property alignment requested logical horizontal position
 */
data class HorizontalAlignModifierElement(
    val alignment: HorizontalAlignment,
) : ModifierElement

/**
 * Supplies alignment as Row vertical parent data for one child.
 *
 * @property alignment requested vertical position
 */
data class VerticalAlignModifierElement(
    val alignment: VerticalAlignment,
) : ModifierElement

/**
 * Offsets final placement without changing the size requested from the parent.
 *
 * @property x physical horizontal offset in dp, positive right
 * @property y vertical offset in dp, positive down
 */
data class OffsetModifierElement(
    val x: UiDp,
    val y: UiDp,
) : ModifierElement
