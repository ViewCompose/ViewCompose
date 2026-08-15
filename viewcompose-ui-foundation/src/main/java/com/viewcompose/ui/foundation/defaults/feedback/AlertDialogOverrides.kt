package com.viewcompose.ui.foundation

import com.viewcompose.ui.shape.UiShape
import com.viewcompose.ui.unit.UiDp

/**
 * Selectively replaces [AlertDialog] surface, typography, icon, and spacing appearance.
 *
 * Visibility, request identity, dismissal policy, callbacks, and semantic text remain direct
 * AlertDialog contracts. Action buttons continue to resolve through Button overrides.
 *
 * @property containerColor dialog surface ARGB color
 * @property titleColor title ARGB color
 * @property textColor supporting-text ARGB color
 * @property iconTint optional leading-icon ARGB tint
 * @property titleStyle title typography
 * @property textStyle supporting-text typography
 * @property shape dialog surface shape
 * @property contentPadding uniform content inset in dp
 * @property titleToTextSpacing gap between title and supporting text in dp
 * @property textToButtonsSpacing gap between supporting text and actions in dp
 * @property buttonSpacing gap between adjacent actions in dp
 * @property iconBottomSpacing gap below a leading icon in dp
 * @property iconSize square leading-icon size in dp
 * @property minWidth minimum dialog surface width in dp
 * @throws IllegalArgumentException when a supplied dimension is negative
 */
data class AlertDialogOverrides(
    val containerColor: Int? = null,
    val titleColor: Int? = null,
    val textColor: Int? = null,
    val iconTint: Int? = null,
    val titleStyle: UiTextStyle? = null,
    val textStyle: UiTextStyle? = null,
    val shape: UiShape? = null,
    val contentPadding: UiDp? = null,
    val titleToTextSpacing: UiDp? = null,
    val textToButtonsSpacing: UiDp? = null,
    val buttonSpacing: UiDp? = null,
    val iconBottomSpacing: UiDp? = null,
    val iconSize: UiDp? = null,
    val minWidth: UiDp? = null,
) {
    init {
        contentPadding.requireNonNegative("AlertDialogOverrides.contentPadding")
        titleToTextSpacing.requireNonNegative("AlertDialogOverrides.titleToTextSpacing")
        textToButtonsSpacing.requireNonNegative("AlertDialogOverrides.textToButtonsSpacing")
        buttonSpacing.requireNonNegative("AlertDialogOverrides.buttonSpacing")
        iconBottomSpacing.requireNonNegative("AlertDialogOverrides.iconBottomSpacing")
        iconSize.requireNonNegative("AlertDialogOverrides.iconSize")
        minWidth.requireNonNegative("AlertDialogOverrides.minWidth")
    }

    /** Shared AlertDialog override values. */
    companion object {
        /** Shared empty AlertDialog appearance patch. */
        val None: AlertDialogOverrides = AlertDialogOverrides()
    }
}

internal fun AlertDialogOverrides.merge(nearest: AlertDialogOverrides): AlertDialogOverrides {
    if (nearest === AlertDialogOverrides.None) return this
    if (this === AlertDialogOverrides.None) return nearest
    return AlertDialogOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        titleColor = nearest.titleColor ?: titleColor,
        textColor = nearest.textColor ?: textColor,
        iconTint = nearest.iconTint ?: iconTint,
        titleStyle = nearest.titleStyle ?: titleStyle,
        textStyle = nearest.textStyle ?: textStyle,
        shape = nearest.shape ?: shape,
        contentPadding = nearest.contentPadding ?: contentPadding,
        titleToTextSpacing = nearest.titleToTextSpacing ?: titleToTextSpacing,
        textToButtonsSpacing = nearest.textToButtonsSpacing ?: textToButtonsSpacing,
        buttonSpacing = nearest.buttonSpacing ?: buttonSpacing,
        iconBottomSpacing = nearest.iconBottomSpacing ?: iconBottomSpacing,
        iconSize = nearest.iconSize ?: iconSize,
        minWidth = nearest.minWidth ?: minWidth,
    )
}

internal val LocalAlertDialogOverrides = uiLocalOf(
    debugName = "AlertDialogOverrides",
    debugValueFormatter = AlertDialogOverrides::toString,
) { AlertDialogOverrides.None }

/**
 * Merges sparse AlertDialog [overrides] for [content].
 *
 * Nested scopes merge field by field and an AlertDialog instance has the highest precedence.
 *
 * @sample com.viewcompose.ui.foundation.samples.remainingComponentOverridesSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant alert dialogs
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideAlertDialogOverrides(
    overrides: AlertDialogOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        LocalAlertDialogOverrides,
        UiLocals.current(LocalAlertDialogOverrides).merge(overrides),
        content,
    )
}

private fun UiDp?.requireNonNegative(propertyName: String) {
    require(this == null || this >= UiDp.Zero) { "$propertyName must be non-negative." }
}
