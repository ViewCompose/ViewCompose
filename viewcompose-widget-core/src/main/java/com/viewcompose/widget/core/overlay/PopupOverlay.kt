package com.viewcompose.widget.core

import com.viewcompose.ui.environment.UiLayoutDirection

/** Declares the preferred alignment of a popup relative to its anchor. */
enum class PopupAlignment {
    /** Places the popup below the anchor and aligns logical starts. */
    BelowStart,
    /** Places the popup below the anchor and centers it horizontally. */
    BelowCenter,
    /** Places the popup below the anchor and aligns logical ends. */
    BelowEnd,
    /** Places the popup above the anchor and aligns logical starts. */
    AboveStart,
    /** Places the popup above the anchor and centers it horizontally. */
    AboveCenter,
    /** Places the popup above the anchor and aligns logical ends. */
    AboveEnd,
    /** Places the popup before the anchor and aligns top edges. */
    StartTop,
    /** Places the popup before the anchor and centers it vertically. */
    StartCenter,
    /** Places the popup before the anchor and aligns bottom edges. */
    StartBottom,
    /** Places the popup after the anchor and aligns top edges. */
    EndTop,
    /** Places the popup after the anchor and centers it vertically. */
    EndCenter,
    /** Places the popup after the anchor and aligns bottom edges. */
    EndBottom,
    /** Centers the popup over the anchor. */
    Center,
}

/** Controls how popup placement is adjusted when it overflows the available viewport. */
enum class PopupOverflowPolicy {
    /** Keeps the requested placement even when it is outside the viewport. */
    None,
    /** Clamps the requested placement into the viewport. */
    Clamp,
    /** Tries the opposite side and then clamps whichever placement overflows less. */
    FlipThenClamp,
}

/**
 * Represents rectangular bounds consumed by popup positioning.
 *
 * All edges use the same platform-defined coordinate space. Construction fails when an end edge
 * precedes its start edge.
 *
 * @property left inclusive horizontal start
 * @property top inclusive vertical start
 * @property right exclusive horizontal end
 * @property bottom exclusive vertical end
 */
data class PopupBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left) { "right must be greater than or equal to left" }
        require(bottom >= top) { "bottom must be greater than or equal to top" }
    }

    /** Width derived from [left] and [right]. */
    val width: Int
        get() = right - left

    /** Height derived from [top] and [bottom]. */
    val height: Int
        get() = bottom - top

    /**
     * Returns bounds reduced by [inset] on each edge.
     *
     * Negative values are treated as zero and values larger than half an axis collapse that axis
     * at its center instead of producing invalid bounds.
     */
    fun inset(inset: Int): PopupBounds {
        val clampedInset = inset.coerceAtLeast(0)
        val horizontalInset = clampedInset.coerceAtMost(width / 2)
        val verticalInset = clampedInset.coerceAtMost(height / 2)
        return PopupBounds(
            left = left + horizontalInset,
            top = top + verticalInset,
            right = right - horizontalInset,
            bottom = bottom - verticalInset,
        )
    }
}

/**
 * Size of a popup in the positioning coordinate space.
 *
 * @property width non-negative popup width
 * @property height non-negative popup height
 */
data class PopupSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width >= 0) { "width must be greater than or equal to zero" }
        require(height >= 0) { "height must be greater than or equal to zero" }
    }
}

/**
 * Result of resolving one popup placement request.
 *
 * @property x resolved horizontal origin
 * @property y resolved vertical origin
 * @property resolvedAlignment requested or flipped alignment used for the result
 * @property wasClamped whether either origin was constrained to the available viewport
 */
data class PopupPosition(
    val x: Int,
    val y: Int,
    val resolvedAlignment: PopupAlignment,
    val wasClamped: Boolean,
)

/** Resolves anchor-relative popup placement, layout direction, offsets, and viewport overflow. */
object PopupPositioner {
    /**
     * Calculates the final position for a popup.
     *
     * With [PopupOverflowPolicy.FlipThenClamp], the opposite side is selected only when it reduces
     * total overflow. [windowMargin] is clamped to the valid viewport extent by [PopupBounds.inset].
     *
     * @param anchorBounds anchor rectangle in viewport coordinates
     * @param popupSize measured popup size in the same coordinate space
     * @param viewportBounds available window bounds
     * @param alignment preferred anchor-relative placement
     * @param layoutDirection resolves logical start and end
     * @param offsetX horizontal offset applied before overflow correction
     * @param offsetY vertical offset applied before overflow correction
     * @sample com.viewcompose.widget.core.samples.popupPositioningSample
     */
    fun calculate(
        anchorBounds: PopupBounds,
        popupSize: PopupSize,
        viewportBounds: PopupBounds,
        alignment: PopupAlignment,
        layoutDirection: UiLayoutDirection = UiLayoutDirection.Ltr,
        overflowPolicy: PopupOverflowPolicy = PopupOverflowPolicy.FlipThenClamp,
        windowMargin: Int = 0,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ): PopupPosition {
        val availableBounds = viewportBounds.inset(windowMargin)
        val requestedCandidate = candidate(
            anchorBounds = anchorBounds,
            popupSize = popupSize,
            alignment = alignment,
            layoutDirection = layoutDirection,
            offsetX = offsetX,
            offsetY = offsetY,
        )
        val resolvedCandidate = if (overflowPolicy == PopupOverflowPolicy.FlipThenClamp) {
            val flippedAlignment = alignment.flipped()
            if (flippedAlignment == alignment) {
                requestedCandidate
            } else {
                val flippedCandidate = candidate(
                    anchorBounds = anchorBounds,
                    popupSize = popupSize,
                    alignment = flippedAlignment,
                    layoutDirection = layoutDirection,
                    offsetX = offsetX,
                    offsetY = offsetY,
                )
                if (
                    flippedCandidate.overflow(
                        popupSize = popupSize,
                        bounds = availableBounds,
                    ) < requestedCandidate.overflow(
                        popupSize = popupSize,
                        bounds = availableBounds,
                    )
                ) {
                    flippedCandidate
                } else {
                    requestedCandidate
                }
            }
        } else {
            requestedCandidate
        }
        if (overflowPolicy == PopupOverflowPolicy.None) {
            return PopupPosition(
                x = resolvedCandidate.x,
                y = resolvedCandidate.y,
                resolvedAlignment = resolvedCandidate.alignment,
                wasClamped = false,
            )
        }
        val clampedX = resolvedCandidate.x.clampPopupAxis(
            popupSize = popupSize.width,
            availableStart = availableBounds.left,
            availableEnd = availableBounds.right,
        )
        val clampedY = resolvedCandidate.y.clampPopupAxis(
            popupSize = popupSize.height,
            availableStart = availableBounds.top,
            availableEnd = availableBounds.bottom,
        )
        return PopupPosition(
            x = clampedX,
            y = clampedY,
            resolvedAlignment = resolvedCandidate.alignment,
            wasClamped = clampedX != resolvedCandidate.x || clampedY != resolvedCandidate.y,
        )
    }

    private fun candidate(
        anchorBounds: PopupBounds,
        popupSize: PopupSize,
        alignment: PopupAlignment,
        layoutDirection: UiLayoutDirection,
        offsetX: Int,
        offsetY: Int,
    ): Candidate {
        val startX = when (layoutDirection) {
            UiLayoutDirection.Ltr -> anchorBounds.left
            UiLayoutDirection.Rtl -> anchorBounds.right - popupSize.width
        }
        val endX = when (layoutDirection) {
            UiLayoutDirection.Ltr -> anchorBounds.right - popupSize.width
            UiLayoutDirection.Rtl -> anchorBounds.left
        }
        val beforeX = when (layoutDirection) {
            UiLayoutDirection.Ltr -> anchorBounds.left - popupSize.width
            UiLayoutDirection.Rtl -> anchorBounds.right
        }
        val afterX = when (layoutDirection) {
            UiLayoutDirection.Ltr -> anchorBounds.right
            UiLayoutDirection.Rtl -> anchorBounds.left - popupSize.width
        }
        val centerX = anchorBounds.left + (anchorBounds.width - popupSize.width) / 2
        val topY = anchorBounds.top
        val bottomY = anchorBounds.bottom - popupSize.height
        val centerY = anchorBounds.top + (anchorBounds.height - popupSize.height) / 2
        val x = when (alignment) {
            PopupAlignment.BelowStart,
            PopupAlignment.AboveStart,
            -> startX

            PopupAlignment.BelowCenter,
            PopupAlignment.AboveCenter,
            PopupAlignment.Center,
            -> centerX

            PopupAlignment.BelowEnd,
            PopupAlignment.AboveEnd,
            -> endX

            PopupAlignment.StartTop,
            PopupAlignment.StartCenter,
            PopupAlignment.StartBottom,
            -> beforeX

            PopupAlignment.EndTop,
            PopupAlignment.EndCenter,
            PopupAlignment.EndBottom,
            -> afterX
        }
        val y = when (alignment) {
            PopupAlignment.BelowStart,
            PopupAlignment.BelowCenter,
            PopupAlignment.BelowEnd,
            -> anchorBounds.bottom

            PopupAlignment.AboveStart,
            PopupAlignment.AboveCenter,
            PopupAlignment.AboveEnd,
            -> anchorBounds.top - popupSize.height

            PopupAlignment.StartTop,
            PopupAlignment.EndTop,
            -> topY

            PopupAlignment.StartCenter,
            PopupAlignment.EndCenter,
            PopupAlignment.Center,
            -> centerY

            PopupAlignment.StartBottom,
            PopupAlignment.EndBottom,
            -> bottomY
        }
        return Candidate(
            x = x + offsetX,
            y = y + offsetY,
            alignment = alignment,
        )
    }

    private fun PopupAlignment.flipped(): PopupAlignment {
        return when (this) {
            PopupAlignment.BelowStart -> PopupAlignment.AboveStart
            PopupAlignment.BelowCenter -> PopupAlignment.AboveCenter
            PopupAlignment.BelowEnd -> PopupAlignment.AboveEnd
            PopupAlignment.AboveStart -> PopupAlignment.BelowStart
            PopupAlignment.AboveCenter -> PopupAlignment.BelowCenter
            PopupAlignment.AboveEnd -> PopupAlignment.BelowEnd
            PopupAlignment.StartTop -> PopupAlignment.EndTop
            PopupAlignment.StartCenter -> PopupAlignment.EndCenter
            PopupAlignment.StartBottom -> PopupAlignment.EndBottom
            PopupAlignment.EndTop -> PopupAlignment.StartTop
            PopupAlignment.EndCenter -> PopupAlignment.StartCenter
            PopupAlignment.EndBottom -> PopupAlignment.StartBottom
            PopupAlignment.Center -> PopupAlignment.Center
        }
    }

    private data class Candidate(
        val x: Int,
        val y: Int,
        val alignment: PopupAlignment,
    ) {
        fun overflow(
            popupSize: PopupSize,
            bounds: PopupBounds,
        ): Long {
            val leftOverflow = (bounds.left - x).coerceAtLeast(0).toLong()
            val topOverflow = (bounds.top - y).coerceAtLeast(0).toLong()
            val rightOverflow = (x.toLong() + popupSize.width - bounds.right).coerceAtLeast(0L)
            val bottomOverflow = (y.toLong() + popupSize.height - bounds.bottom).coerceAtLeast(0L)
            return leftOverflow + topOverflow + rightOverflow + bottomOverflow
        }
    }

    private fun Int.clampPopupAxis(
        popupSize: Int,
        availableStart: Int,
        availableEnd: Int,
    ): Int {
        val maximumStart = (availableEnd - popupSize).coerceAtLeast(availableStart)
        return coerceIn(availableStart, maximumStart)
    }
}

/**
 * Describes platform-neutral placement, dismissal, and focus behavior for one popup.
 *
 * Callback identity does not participate in equality. Recomposition therefore updates a platform
 * popup only when placement or interaction policy changes.
 *
 * @property anchorId platform identifier of the view used as the placement anchor
 * @property alignment preferred placement relative to the anchor
 * @property overflowPolicy correction applied when the popup exceeds the viewport
 * @property windowMargin minimum requested distance from viewport edges, in host coordinate units
 * @property dismissOnClickOutside whether an outside pointer event requests dismissal
 * @property focusable whether the popup may receive input focus
 * @property offsetX horizontal offset in host coordinate units
 * @property offsetY vertical offset in host coordinate units
 * @property onDismissRequest invoked when the platform requests dismissal; the owner must remove the declaration
 */
class PopupOverlaySpec(
    val anchorId: String,
    val alignment: PopupAlignment = PopupAlignment.BelowStart,
    val overflowPolicy: PopupOverflowPolicy = PopupOverflowPolicy.FlipThenClamp,
    val windowMargin: Int = 0,
    val dismissOnClickOutside: Boolean = true,
    val focusable: Boolean = true,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val onDismissRequest: (() -> Unit)? = null,
) {
    /** Compares placement and interaction policy while intentionally ignoring callback identity. */
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is PopupOverlaySpec) {
            return false
        }
        return anchorId == other.anchorId &&
            alignment == other.alignment &&
            overflowPolicy == other.overflowPolicy &&
            windowMargin == other.windowMargin &&
            dismissOnClickOutside == other.dismissOnClickOutside &&
            focusable == other.focusable &&
            offsetX == other.offsetX &&
            offsetY == other.offsetY
    }

    /** Returns a hash of the placement and interaction policy. */
    override fun hashCode(): Int {
        var result = anchorId.hashCode()
        result = 31 * result + alignment.hashCode()
        result = 31 * result + overflowPolicy.hashCode()
        result = 31 * result + windowMargin
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + focusable.hashCode()
        result = 31 * result + offsetX
        result = 31 * result + offsetY
        return result
    }
}

/**
 * Holds content that a runtime host can render into a popup-owned surface.
 *
 * @property surface captured locals, overlay host, and declarative surface content
 */
data class PopupOverlayContent(
    val surface: OverlaySurfaceContent,
)
