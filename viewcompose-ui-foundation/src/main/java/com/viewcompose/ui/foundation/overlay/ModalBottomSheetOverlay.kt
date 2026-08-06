package com.viewcompose.ui.foundation

/**
 * Describes platform-neutral behavior for one modal bottom-sheet overlay.
 *
 * Callback identity does not participate in equality. Recomposition therefore updates a platform
 * sheet only when a visual, navigation-bar, or dismissal policy changes.
 *
 * @property dismissOnBackPress whether a platform back action requests dismissal
 * @property dismissOnClickOutside whether tapping the scrim requests dismissal
 * @property skipPartiallyExpanded whether the presenter must omit a partially expanded state
 * @property scrimOpacity opacity in `0.0..1.0` requested for the background scrim
 * @property navigationBarColor optional platform navigation-bar color while the sheet is visible
 * @property onDismissRequest invoked when the platform requests dismissal; the owner must remove the declaration
 */
class ModalBottomSheetOverlaySpec(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val skipPartiallyExpanded: Boolean = false,
    val scrimOpacity: Float = 0.32f,
    val navigationBarColor: Int? = null,
    val onDismissRequest: (() -> Unit)? = null,
) {
    /** Compares the visual and dismissal policy while intentionally ignoring callback identity. */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ModalBottomSheetOverlaySpec) {
            return false
        }
        return dismissOnBackPress == other.dismissOnBackPress &&
            dismissOnClickOutside == other.dismissOnClickOutside &&
            skipPartiallyExpanded == other.skipPartiallyExpanded &&
            scrimOpacity == other.scrimOpacity &&
            navigationBarColor == other.navigationBarColor
    }

    /** Returns a hash of the visual and dismissal policy. */
    override fun hashCode(): Int {
        var result = dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + skipPartiallyExpanded.hashCode()
        result = 31 * result + scrimOpacity.hashCode()
        result = 31 * result + (navigationBarColor ?: 0)
        return result
    }
}

/**
 * Holds content that a runtime host can render into a bottom-sheet-owned surface.
 *
 * @property surface captured locals, overlay host, and declarative surface content
 */
data class ModalBottomSheetOverlayContent(
    val surface: OverlaySurfaceContent,
)
