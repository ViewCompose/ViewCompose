package com.viewcompose.ui.foundation

/** Defines how long a snackbar presenter keeps an entry visible. */
enum class SnackbarDuration {
    /** Uses the platform's short snackbar duration. */
    Short,
    /** Uses the platform's long snackbar duration. */
    Long,
    /** Keeps the snackbar visible until an action, gesture, or declarative removal dismisses it. */
    Indefinite,
}

/** Controls how a transient-feedback request interacts with active and pending entries. */
enum class TransientFeedbackQueuePolicy {
    /** Appends the request after existing pending entries. */
    Enqueue,
    /** Dismisses the active entry and presents this request next. */
    ReplaceCurrent,
    /** Replaces the request with the same session-scoped key while preserving queue order otherwise. */
    ReplaceSameKey,
    /** Drops the request when another entry is active or pending. */
    DropIfBusy,
}

/** Identifies why a transient-feedback entry stopped being visible or was never shown. */
enum class TransientFeedbackDismissReason {
    /** The platform display duration elapsed. */
    Timeout,
    /** The user activated the snackbar action. */
    Action,
    /** A user gesture dismissed the entry. */
    Gesture,
    /** Another request replaced the entry. */
    Replaced,
    /** The declaration was removed from a later render frame. */
    Removed,
    /** The owning render session was cleared. */
    SessionCleared,
    /** Queue policy rejected the entry while the host was busy. */
    Dropped,
    /** The platform presenter ended the entry for another reason. */
    Platform,
}

/**
 * Describes snackbar content, duration, callbacks, and queue behavior.
 *
 * Callback identity does not participate in equality. A host can therefore reconcile callback
 * capture changes without restarting an otherwise unchanged snackbar.
 *
 * @property message text presented to the user
 * @property actionLabel optional action text; `null` omits the action
 * @property duration requested presenter lifetime
 * @property queuePolicy behavior when other transient feedback is active or pending
 * @property onAction invoked when the snackbar action is activated
 * @property onDismiss invoked once with the final dismissal reason
 */
class SnackbarOverlaySpec(
    val message: String,
    val actionLabel: String? = null,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val queuePolicy: TransientFeedbackQueuePolicy = TransientFeedbackQueuePolicy.Enqueue,
    val onAction: (() -> Unit)? = null,
    val onDismiss: ((TransientFeedbackDismissReason) -> Unit)? = null,
) {
    /** Compares visible content and queue policy while intentionally ignoring callback identity. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnackbarOverlaySpec) return false

        return message == other.message &&
            actionLabel == other.actionLabel &&
            duration == other.duration &&
            queuePolicy == other.queuePolicy
    }

    /** Returns a hash of visible content and queue policy. */
    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + (actionLabel?.hashCode() ?: 0)
        result = 31 * result + duration.hashCode()
        result = 31 * result + queuePolicy.hashCode()
        return result
    }
}

/** Defines how long a toast presenter keeps an entry visible. */
enum class ToastDuration {
    /** Uses the platform's short toast duration. */
    Short,
    /** Uses the platform's long toast duration. */
    Long,
}

/**
 * Describes toast content, duration, callback, and queue behavior.
 *
 * Callback identity does not participate in equality. A host can therefore reconcile callback
 * capture changes without restarting an otherwise unchanged toast.
 *
 * @property message text presented to the user
 * @property duration requested presenter lifetime
 * @property queuePolicy behavior when other transient feedback is active or pending
 * @property onDismiss invoked once with the final dismissal reason
 */
class ToastOverlaySpec(
    val message: String,
    val duration: ToastDuration = ToastDuration.Short,
    val queuePolicy: TransientFeedbackQueuePolicy = TransientFeedbackQueuePolicy.Enqueue,
    val onDismiss: ((TransientFeedbackDismissReason) -> Unit)? = null,
) {
    /** Compares visible content and queue policy while intentionally ignoring callback identity. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ToastOverlaySpec) return false

        return message == other.message &&
            duration == other.duration &&
            queuePolicy == other.queuePolicy
    }

    /** Returns a hash of visible content and queue policy. */
    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + queuePolicy.hashCode()
        return result
    }
}
