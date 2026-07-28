package com.viewcompose.widget.core

/**
 * 描述模态底部面板 overlay 的业务行为和手势策略，不绑定具体 Android 实现。
 * Describes business behavior and gesture policy for a modal bottom sheet overlay without binding to Android APIs.
 */
class ModalBottomSheetOverlaySpec(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val skipPartiallyExpanded: Boolean = false,
    val scrimOpacity: Float = 0.32f,
    val navigationBarColor: Int? = null,
    val onDismissRequest: (() -> Unit)? = null,
) {
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
 * 保存底部面板内容 token，供 overlay host 在同一 entry 更新时复用内容。
 * Stores the bottom-sheet content token so the overlay host can reuse content across updates to the same entry.
 */
data class ModalBottomSheetOverlayContent(
    val surface: OverlaySurfaceContent,
)
