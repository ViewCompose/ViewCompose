package com.viewcompose.widget.core

/**
 * 定义对话框在宿主窗口中的停靠位置。
 * Defines where a dialog is anchored inside the host window.
 */
enum class DialogPosition {
    Top,
    Center,
    Bottom,
}

/**
 * 描述一次对话框 overlay 的平台无关行为，实际展示由平台 presenter 完成。
 * Describes platform-neutral behavior for one dialog overlay; presentation is delegated to the platform presenter.
 */
class DialogOverlaySpec(
    val dismissOnBackPress: Boolean = true,
    val dismissOnClickOutside: Boolean = true,
    val position: DialogPosition = DialogPosition.Center,
    val scrimOpacity: Float = 0.32f,
    val onDismissRequest: (() -> Unit)? = null,
) {
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

    override fun hashCode(): Int {
        var result = dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + position.hashCode()
        result = 31 * result + scrimOpacity.hashCode()
        return result
    }
}

/**
 * 保存对话框内容的可恢复 token，运行时宿主用它更新或重建当前展示内容。
 * Stores the restorable dialog content token used by runtime hosts to update or recreate displayed content.
 */
data class DialogOverlayContent(
    val surface: OverlaySurfaceContent,
)
