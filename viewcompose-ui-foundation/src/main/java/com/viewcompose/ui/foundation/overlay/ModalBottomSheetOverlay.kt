package com.viewcompose.ui.foundation

/**
 * Describes platform-neutral behavior for one modal bottom-sheet overlay.
 *
 * Callback identity does not participate in equality. Recomposition therefore updates a platform
 * sheet only when [appearance] or a dismissal/expansion policy changes.
 *
 * @property dismissOnBackPress whether a platform back action requests dismissal
 * @property dismissOnClickOutside whether tapping the scrim requests dismissal
 * @property skipPartiallyExpanded whether the presenter must omit a partially expanded state
 * @property appearance complete resolved appearance applied by every presenter update
 * @property onDismissRequest invoked when the platform requests dismissal; the owner must remove the declaration
 */
class ModalBottomSheetOverlaySpec(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val skipPartiallyExpanded: Boolean = false,
    val appearance: ModalBottomSheetAppearance,
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
            appearance == other.appearance
    }

    /** Returns a hash of the visual and dismissal policy. */
    override fun hashCode(): Int {
        var result = dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + skipPartiallyExpanded.hashCode()
        result = 31 * result + appearance.hashCode()
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
