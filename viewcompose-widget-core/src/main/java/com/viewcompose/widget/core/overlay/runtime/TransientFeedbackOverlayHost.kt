package com.viewcompose.widget.core

/**
 * 唯一标识一次 overlay 请求在某个 session 内的运行时条目。
 * Uniquely identifies the runtime entry for one overlay request inside a session.
 */
data class OverlayEntryId(
    val sessionId: OverlaySessionId,
    val requestKey: String,
)

/**
 * 由平台实现的 snackbar 展示与关闭入口。
 * Platform-provided entry point for showing and dismissing snackbars.
 */
interface SnackbarOverlayPresenter {
    fun show(
        entryId: OverlayEntryId,
        spec: SnackbarOverlaySpec,
        onDismissed: (TransientFeedbackDismissReason) -> Unit,
    )

    fun dismiss(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
    )
}

/**
 * 由平台实现的 toast 展示与关闭入口。
 * Platform-provided entry point for showing and dismissing toasts.
 */
interface ToastOverlayPresenter {
    fun show(
        entryId: OverlayEntryId,
        spec: ToastOverlaySpec,
        onDismissed: (TransientFeedbackDismissReason) -> Unit,
    )

    fun dismiss(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
    )
}

/**
 * 暴露当前临时反馈队列状态，主要用于测试和诊断。
 * Exposes current transient-feedback queue state, primarily for tests and diagnostics.
 */
data class TransientFeedbackQueueSnapshot(
    val active: OverlayEntryId?,
    val pending: List<OverlayEntryId>,
    val consumed: Set<OverlayEntryId>,
)

/**
 * 管理 snackbar/toast 的排队、替换和清理，并把最终展示动作委托给平台 presenter。
 * Manages snackbar/toast queueing, replacement, and clearing while delegating final presentation to platform presenters.
 */
class TransientFeedbackOverlayHost(
    private val snackbarPresenter: SnackbarOverlayPresenter,
    private val toastPresenter: ToastOverlayPresenter,
) : OverlayHost {
    private val desiredRequests = linkedMapOf<OverlayEntryId, OverlayRequest>()
    private val pendingRequests = mutableListOf<QueueEntry>()
    private val consumedRequests = mutableMapOf<OverlayEntryId, OverlayRequest>()
    private var activeRequest: ActiveEntry? = null
    private var nextActivationToken = 0L
    private var reconciliationDepth = 0

    override fun commit(
        sessionId: OverlaySessionId,
        requests: List<OverlayRequest>,
    ) {
        reconcile {
            val nextRequests = linkedMapOf<OverlayEntryId, OverlayRequest>()
            requests.forEach { request ->
                request.toSupportedEntry(sessionId)?.let { entry ->
                    nextRequests[entry.entryId] = entry.request
                }
            }
            val removedIds = desiredRequests.keys
                .filter { it.sessionId == sessionId && it !in nextRequests }
            removedIds.forEach { entryId ->
                removeDesired(
                    entryId = entryId,
                    reason = TransientFeedbackDismissReason.Removed,
                )
            }
            nextRequests.forEach { (entryId, nextRequest) ->
                val previousRequest = desiredRequests.put(entryId, nextRequest)
                if (previousRequest == nextRequest) {
                    return@forEach
                }
                consumedRequests.remove(entryId)
                pendingRequests.removeAll { it.entryId == entryId }
                if (activeRequest?.entry?.entryId == entryId) {
                    pendingRequests.add(
                        index = 0,
                        element = QueueEntry(entryId, nextRequest),
                    )
                    dismissActive(TransientFeedbackDismissReason.Replaced)
                } else {
                    enqueue(QueueEntry(entryId, nextRequest))
                }
            }
        }
    }

    override fun clear(sessionId: OverlaySessionId) {
        reconcile {
            desiredRequests.keys
                .filter { it.sessionId == sessionId }
                .forEach { entryId ->
                    removeDesired(
                        entryId = entryId,
                        reason = TransientFeedbackDismissReason.SessionCleared,
                    )
                }
        }
    }

    fun snapshot(): TransientFeedbackQueueSnapshot {
        return TransientFeedbackQueueSnapshot(
            active = activeRequest?.entry?.entryId,
            pending = pendingRequests.map { it.entryId },
            consumed = consumedRequests.keys.toSet(),
        )
    }

    private fun enqueue(entry: QueueEntry) {
        when (entry.request.queuePolicy()) {
            TransientFeedbackQueuePolicy.Enqueue,
            TransientFeedbackQueuePolicy.ReplaceSameKey,
            -> pendingRequests += entry

            TransientFeedbackQueuePolicy.ReplaceCurrent -> {
                pendingRequests.add(index = 0, element = entry)
                dismissActive(TransientFeedbackDismissReason.Replaced)
            }

            TransientFeedbackQueuePolicy.DropIfBusy -> {
                if (activeRequest != null || pendingRequests.isNotEmpty()) {
                    consumedRequests[entry.entryId] = entry.request
                    entry.request.notifyDismissed(TransientFeedbackDismissReason.Dropped)
                } else {
                    pendingRequests += entry
                }
            }
        }
    }

    private fun removeDesired(
        entryId: OverlayEntryId,
        reason: TransientFeedbackDismissReason,
    ) {
        desiredRequests.remove(entryId)
        consumedRequests.remove(entryId)
        pendingRequests.removeAll { it.entryId == entryId }
        if (activeRequest?.entry?.entryId == entryId) {
            dismissActive(reason)
        }
    }

    private fun dismissActive(reason: TransientFeedbackDismissReason) {
        val active = activeRequest ?: return
        if (active.dismissRequested) {
            return
        }
        active.dismissRequested = true
        when (active.entry.request.type) {
            OverlayType.Snackbar -> snackbarPresenter.dismiss(active.entry.entryId, reason)
            OverlayType.Toast -> toastPresenter.dismiss(active.entry.entryId, reason)
            OverlayType.Dialog,
            OverlayType.Popup,
            OverlayType.ModalBottomSheet,
            -> Unit
        }
    }

    private fun drainQueue() {
        if (reconciliationDepth > 0 || activeRequest != null) {
            return
        }
        while (pendingRequests.isNotEmpty()) {
            val next = pendingRequests.removeAt(0)
            if (
                desiredRequests[next.entryId] != next.request ||
                consumedRequests[next.entryId] == next.request
            ) {
                continue
            }
            show(next)
            return
        }
    }

    private fun show(entry: QueueEntry) {
        val token = ++nextActivationToken
        activeRequest = ActiveEntry(
            entry = entry,
            token = token,
        )
        val onDismissed: (TransientFeedbackDismissReason) -> Unit = { reason ->
            complete(
                token = token,
                reason = reason,
            )
        }
        try {
            when (entry.request.type) {
                OverlayType.Snackbar -> {
                    val spec = entry.request.payload as SnackbarOverlaySpec
                    snackbarPresenter.show(entry.entryId, spec, onDismissed)
                }

                OverlayType.Toast -> {
                    val spec = entry.request.payload as ToastOverlaySpec
                    toastPresenter.show(entry.entryId, spec, onDismissed)
                }

                OverlayType.Dialog,
                OverlayType.Popup,
                OverlayType.ModalBottomSheet,
                -> complete(token, TransientFeedbackDismissReason.Platform)
            }
        } catch (throwable: Throwable) {
            complete(token, TransientFeedbackDismissReason.Platform)
            throw throwable
        }
    }

    private fun complete(
        token: Long,
        reason: TransientFeedbackDismissReason,
    ) {
        val completed = activeRequest?.takeIf { it.token == token } ?: return
        activeRequest = null
        if (desiredRequests[completed.entry.entryId] == completed.entry.request) {
            consumedRequests[completed.entry.entryId] = completed.entry.request
        }
        try {
            completed.entry.request.notifyDismissed(reason)
        } finally {
            drainQueue()
        }
    }

    private inline fun reconcile(block: () -> Unit) {
        reconciliationDepth += 1
        try {
            block()
        } finally {
            reconciliationDepth -= 1
            drainQueue()
        }
    }

    private fun OverlayRequest.toSupportedEntry(
        sessionId: OverlaySessionId,
    ): QueueEntry? {
        val supportedPayload = when (type) {
            OverlayType.Snackbar -> payload as? SnackbarOverlaySpec
            OverlayType.Toast -> payload as? ToastOverlaySpec
            OverlayType.Dialog,
            OverlayType.Popup,
            OverlayType.ModalBottomSheet,
            -> null
        } ?: return null
        return QueueEntry(
            entryId = OverlayEntryId(
                sessionId = sessionId,
                requestKey = key,
            ),
            request = copy(
                payload = supportedPayload,
                contentToken = contentToken ?: supportedPayload,
            ),
        )
    }

    private fun OverlayRequest.queuePolicy(): TransientFeedbackQueuePolicy {
        return when (val spec = payload) {
            is SnackbarOverlaySpec -> spec.queuePolicy
            is ToastOverlaySpec -> spec.queuePolicy
            else -> TransientFeedbackQueuePolicy.Enqueue
        }
    }

    private fun OverlayRequest.notifyDismissed(reason: TransientFeedbackDismissReason) {
        when (val spec = payload) {
            is SnackbarOverlaySpec -> spec.onDismiss?.invoke(reason)
            is ToastOverlaySpec -> spec.onDismiss?.invoke(reason)
        }
    }

    private data class QueueEntry(
        val entryId: OverlayEntryId,
        val request: OverlayRequest,
    )

    private data class ActiveEntry(
        val entry: QueueEntry,
        val token: Long,
        var dismissRequested: Boolean = false,
    )
}
