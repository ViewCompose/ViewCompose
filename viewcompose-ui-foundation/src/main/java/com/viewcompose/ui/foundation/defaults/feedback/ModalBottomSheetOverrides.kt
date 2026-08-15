package com.viewcompose.ui.foundation

import com.viewcompose.ui.shape.UiShape

/**
 * Selects the system navigation-bar treatment while a modal bottom sheet is visible.
 *
 * The closed hierarchy distinguishes an exact application color from restoring the presenter's
 * captured platform default. A `null` [ModalBottomSheetOverrides.navigationBarColor] means inherit
 * the nearest scoped or semantic default and is therefore not another hierarchy entry.
 */
sealed interface ModalBottomSheetNavigationBarColor {
    /**
     * Requests one exact system navigation-bar ARGB [color].
     *
     * @property color exact ARGB color applied while the sheet is visible
     */
    data class Exact(
        val color: Int,
    ) : ModalBottomSheetNavigationBarColor

    /** Restores the navigation-bar value captured by the platform presenter before showing. */
    data object PlatformDefault : ModalBottomSheetNavigationBarColor
}

/**
 * Selectively replaces modal-bottom-sheet appearance before it crosses the overlay boundary.
 *
 * Visibility, request identity, dismissal behavior, partial-expansion policy, callbacks, and
 * content remain direct [ModalBottomSheet] contracts. [scrimOpacity] is validated when the patch
 * is constructed so every presenter receives a valid resolved snapshot.
 *
 * @property containerColor platform sheet-container ARGB color
 * @property contentColor default ARGB content color captured for sheet content
 * @property shape platform sheet-container shape
 * @property scrimOpacity finite background dim fraction in `0f..1f`
 * @property navigationBarColor exact or platform-default system navigation-bar policy
 * @throws IllegalArgumentException when [scrimOpacity] is not finite or outside `0f..1f`
 */
data class ModalBottomSheetOverrides(
    val containerColor: Int? = null,
    val contentColor: Int? = null,
    val shape: UiShape? = null,
    val scrimOpacity: Float? = null,
    val navigationBarColor: ModalBottomSheetNavigationBarColor? = null,
) {
    init {
        require(scrimOpacity == null || scrimOpacity.isFinite() && scrimOpacity in 0f..1f) {
            "ModalBottomSheetOverrides.scrimOpacity must be finite and in 0f..1f."
        }
    }

    /** Shared modal-bottom-sheet override values. */
    companion object {
        /** Shared empty modal-bottom-sheet appearance patch. */
        val None: ModalBottomSheetOverrides = ModalBottomSheetOverrides()
    }
}

internal fun ModalBottomSheetOverrides.merge(
    nearest: ModalBottomSheetOverrides,
): ModalBottomSheetOverrides {
    if (nearest === ModalBottomSheetOverrides.None) return this
    if (this === ModalBottomSheetOverrides.None) return nearest
    return ModalBottomSheetOverrides(
        containerColor = nearest.containerColor ?: containerColor,
        contentColor = nearest.contentColor ?: contentColor,
        shape = nearest.shape ?: shape,
        scrimOpacity = nearest.scrimOpacity ?: scrimOpacity,
        navigationBarColor = nearest.navigationBarColor ?: navigationBarColor,
    )
}

internal val LocalModalBottomSheetOverrides = uiLocalOf(
    debugName = "ModalBottomSheetOverrides",
    debugValueFormatter = ModalBottomSheetOverrides::toString,
) { ModalBottomSheetOverrides.None }

/**
 * Merges sparse modal-bottom-sheet [overrides] for [content].
 *
 * Nested scopes merge field by field and a ModalBottomSheet instance has the highest precedence.
 * The resolved snapshot is captured by each request rather than read later by a platform presenter.
 *
 * @sample com.viewcompose.ui.foundation.samples.modalBottomSheetAppearanceSample
 * @receiver active tree builder receiving the scoped content
 * @param overrides sparse appearance patch applied to descendant modal bottom sheets
 * @param content subtree built synchronously with the merged patch
 */
fun UiTreeBuilder.ProvideModalBottomSheetOverrides(
    overrides: ModalBottomSheetOverrides,
    content: UiTreeBuilder.() -> Unit,
) {
    ProvideLocal(
        LocalModalBottomSheetOverrides,
        UiLocals.current(LocalModalBottomSheetOverrides).merge(overrides),
        content,
    )
}
