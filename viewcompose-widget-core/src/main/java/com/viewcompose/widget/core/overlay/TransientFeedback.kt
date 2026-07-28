package com.viewcompose.widget.core

/**
 * 定义 snackbar 在 presenter 中展示的生命周期策略。
 * Defines the snackbar lifetime policy used by presenters.
 */
enum class SnackbarDuration {
    Short,
    Long,
    Indefinite,
}

/**
 * 控制临时反馈进入队列时与现有条目的关系。
 * Controls how transient feedback entries interact with existing queued entries.
 */
enum class TransientFeedbackQueuePolicy {
    Enqueue,
    ReplaceCurrent,
    ReplaceSameKey,
    DropIfBusy,
}

/**
 * 标识临时反馈消失原因，便于业务区分超时、动作点击和主动清理。
 * Identifies why transient feedback disappeared so business code can distinguish timeout, action, and clear events.
 */
enum class TransientFeedbackDismissReason {
    Timeout,
    Action,
    Gesture,
    Replaced,
    Removed,
    SessionCleared,
    Dropped,
    Platform,
}

/**
 * 描述 snackbar 的内容、动作和队列策略，保持与平台展示层解耦。
 * Describes snackbar text, action, and queue policy while staying decoupled from platform presentation.
 */
class SnackbarOverlaySpec(
    val message: String,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val queuePolicy: TransientFeedbackQueuePolicy = TransientFeedbackQueuePolicy.Enqueue,
    val onAction: (() -> Unit)? = null,
    val onDismiss: ((TransientFeedbackDismissReason) -> Unit)? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnackbarOverlaySpec) return false

        return message == other.message &&
            actionLabel == other.actionLabel &&
            duration == other.duration &&
            queuePolicy == other.queuePolicy
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + (actionLabel?.hashCode() ?: 0)
        result = 31 * result + duration.hashCode()
        result = 31 * result + queuePolicy.hashCode()
        return result
    }
}

/**
 * 定义 toast 的短/长展示时长。
 * Defines short and long toast display durations.
 */
enum class ToastDuration {
    Short,
    Long,
}

/**
 * 描述 toast 临时反馈的文本和展示时长。
 * Describes toast transient-feedback text and duration.
 */
class ToastOverlaySpec(
    val message: String,
    val duration: ToastDuration = ToastDuration.Short,
    val queuePolicy: TransientFeedbackQueuePolicy = TransientFeedbackQueuePolicy.Enqueue,
    val onDismiss: ((TransientFeedbackDismissReason) -> Unit)? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToastOverlaySpec) return false

        return message == other.message &&
            duration == other.duration &&
            queuePolicy == other.queuePolicy
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + queuePolicy.hashCode()
        return result
    }
}
