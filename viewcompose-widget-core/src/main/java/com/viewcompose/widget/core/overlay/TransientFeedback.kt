package com.viewcompose.widget.core

enum class SnackbarDuration {
    Short,
    Long,
    Indefinite,
}

enum class TransientFeedbackQueuePolicy {
    Enqueue,
    ReplaceCurrent,
    ReplaceSameKey,
    DropIfBusy,
}

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

enum class ToastDuration {
    Short,
    Long,
}

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
