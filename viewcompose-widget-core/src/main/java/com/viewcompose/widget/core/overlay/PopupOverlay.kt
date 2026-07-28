package com.viewcompose.widget.core

/**
 * 声明 popup 相对锚点的首选对齐方式。
 * Declares the preferred alignment of a popup relative to its anchor.
 */
enum class PopupAlignment {
    BelowStart,
    BelowCenter,
    BelowEnd,
    AboveStart,
    AboveCenter,
    AboveEnd,
    StartTop,
    StartCenter,
    StartBottom,
    EndTop,
    EndCenter,
    EndBottom,
    Center,
}

/**
 * 控制 popup 超出可用边界时的修正策略。
 * Controls how popup placement is adjusted when it would overflow available bounds.
 */
enum class PopupOverflowPolicy {
    None,
    Clamp,
    FlipThenClamp,
}

/**
 * 表示 popup 定位算法使用的矩形边界，单位由平台宿主保持一致。
 * Represents rectangle bounds consumed by popup positioning; units are kept consistent by the platform host.
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

    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top

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

data class PopupSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width >= 0) { "width must be greater than or equal to zero" }
        require(height >= 0) { "height must be greater than or equal to zero" }
    }
}

data class PopupPosition(
    val x: Int,
    val y: Int,
    val resolvedAlignment: PopupAlignment,
    val wasClamped: Boolean,
)

/**
 * 计算 popup 的最终窗口位置，集中处理锚点、偏移、尺寸和溢出策略。
 * Calculates the final popup window position, centralizing anchor, offset, size, and overflow handling.
 */
object PopupPositioner {
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
 * 描述 popup overlay 的平台无关参数，包含定位、可关闭性和焦点策略。
 * Describes platform-neutral popup overlay parameters including placement, dismiss behavior, and focus policy.
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
 * 保存 popup 内容 token，host 用它在 show/update 之间维持声明式内容身份。
 * Stores popup content tokens so hosts preserve declarative content identity between show and update calls.
 */
data class PopupOverlayContent(
    val surface: OverlaySurfaceContent,
)
