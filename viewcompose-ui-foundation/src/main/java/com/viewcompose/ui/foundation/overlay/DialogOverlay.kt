package com.viewcompose.ui.foundation

/** Defines where a dialog is anchored inside its host window. */
enum class DialogPosition {
    /** Aligns the dialog near the top of the available window. */
    Top,
    /** Centers the dialog in the available window. */
    Center,
    /** Aligns the dialog near the bottom of the available window. */
    Bottom,
}

/**
 * Describes platform-neutral behavior for one dialog overlay.
 *
 * Callback identity does not participate in equality. Recomposition therefore updates a platform
 * dialog only when a visual or dismissal policy changes.
 *
 * @property dismissOnBackPress whether a platform back action requests dismissal
 * @property dismissOnClickOutside whether a pointer event outside the surface requests dismissal
 * @property position preferred placement inside the host window
 * @property scrimOpacity opacity in `0.0..1.0` requested for the background scrim
 * @property onDismissRequest invoked when the platform requests dismissal; the owner must remove the declaration
 */
class DialogOverlaySpec(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val position: DialogPosition = DialogPosition.Center,
    val scrimOpacity: Float = 0.32f,
    val onDismissRequest: (() -> Unit)? = null,
) {
    /** Compares the visual and dismissal policy while intentionally ignoring callback identity. */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is DialogOverlaySpec) {
            return false
        }
        return dismissOnBackPress == other.dismissOnBackPress &&
            dismissOnClickOutside == other.dismissOnClickOutside &&
            position == other.position &&
            scrimOpacity == other.scrimOpacity
    }

    /** Returns a hash of the visual and dismissal policy. */
    override fun hashCode(): Int {
        var result = dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + position.hashCode()
        result = 31 * result + scrimOpacity.hashCode()
        return result
    }
}

/**
 * Holds content that a runtime host can render into a dialog-owned surface.
 *
 * @property surface captured locals, overlay host, and declarative surface content
 */
data class DialogOverlayContent(
    val surface: OverlaySurfaceContent,
)
