package com.viewcompose.ui.foundation

import com.viewcompose.ui.shape.UiShape

/** Default visual tokens for modal bottom sheets. */
object ModalBottomSheetDefaults {
    /** Returns the opacity of the scrim behind a visible sheet. */
    fun scrimOpacity(): Float = scoped().scrimOpacity ?: Theme.overlays.scrimOpacity

    /** Returns the navigation-bar color while the sheet is visible. */
    fun navigationBarColor(): Int = Theme.colors.surface

    /** Returns the resolved system navigation-bar policy. */
    fun navigationBarAppearance(): ModalBottomSheetNavigationBarColor =
        scoped().navigationBarColor ?: ModalBottomSheetNavigationBarColor.Exact(navigationBarColor())

    /** Returns the sheet container color. */
    fun containerColor(): Int = scoped().containerColor ?: Theme.colors.surface

    /** Returns the default content color inside the sheet. */
    fun contentColor(): Int = scoped().contentColor ?: Theme.colors.onSurface

    /** Returns the current extra-large shape for the platform sheet container. */
    fun shape(): UiShape = scoped().shape ?: Theme.shapes.extraLarge

    internal fun resolve(instance: ModalBottomSheetOverrides): ModalBottomSheetAppearance {
        val overrides = scoped().merge(instance)
        return ModalBottomSheetAppearance(
            containerColor = overrides.containerColor ?: Theme.colors.surface,
            contentColor = overrides.contentColor ?: Theme.colors.onSurface,
            shape = overrides.shape ?: Theme.shapes.extraLarge,
            scrimOpacity = overrides.scrimOpacity ?: Theme.overlays.scrimOpacity,
            navigationBarColor = overrides.navigationBarColor
                ?: ModalBottomSheetNavigationBarColor.Exact(Theme.colors.surface),
        )
    }

    private fun scoped(): ModalBottomSheetOverrides =
        UiLocals.current(LocalModalBottomSheetOverrides)
}

/**
 * Carries complete modal-bottom-sheet appearance across an overlay session boundary.
 *
 * The DSL resolves this immutable value from Theme and sparse overrides. Presenters must apply the
 * complete snapshot on show and every same-key update without retaining a composition Local.
 *
 * @property containerColor platform sheet-container ARGB color
 * @property contentColor default ARGB color already captured for declarative sheet content
 * @property shape platform sheet-container shape
 * @property scrimOpacity finite background dim fraction in `0f..1f`
 * @property navigationBarColor exact or platform-default system navigation-bar policy
 * @throws IllegalArgumentException when [scrimOpacity] is not finite or outside `0f..1f`
 */
data class ModalBottomSheetAppearance(
    val containerColor: Int,
    val contentColor: Int,
    val shape: UiShape,
    val scrimOpacity: Float,
    val navigationBarColor: ModalBottomSheetNavigationBarColor,
) {
    init {
        require(scrimOpacity.isFinite() && scrimOpacity in 0f..1f) {
            "ModalBottomSheetAppearance.scrimOpacity must be finite and in 0f..1f."
        }
    }
}
