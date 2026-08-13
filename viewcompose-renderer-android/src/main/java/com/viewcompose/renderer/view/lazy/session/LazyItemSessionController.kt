package com.viewcompose.renderer.view.lazy.session

import com.viewcompose.renderer.reconcile.LazyListChangePayload
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession

/** Manages the child render-session lifecycle and submission revisions for one retained item. */
internal class LazyItemSessionController(
    private val createSession: (LazyListItem) -> LazyListItemSession,
    private val clearContainer: () -> Unit,
) {
    private data class Candidate(
        val item: LazyListItem,
        val payload: Any?,
        val submissionRevision: Long,
    )

    private var currentKey: Any? = null
    private var currentContentToken: Any? = null
    private var committedRevision = Long.MIN_VALUE
    private var nextImplicitRevision = 0L
    private var candidate: Candidate? = null
    private var session: LazyListItemSession? = null

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long = nextImplicitSubmissionRevision(),
    ) {
        if (submissionRevision <= committedRevision) return
        apply(
            item = item,
            payload = payload,
        )
        committedRevision = submissionRevision
        candidate = null
    }

    fun stage(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long,
    ) {
        if (submissionRevision <= committedRevision) return
        val currentCandidate = candidate
        if (currentCandidate == null || submissionRevision >= currentCandidate.submissionRevision) {
            candidate = Candidate(
                item = item,
                payload = payload,
                submissionRevision = submissionRevision,
            )
        }
    }

    fun commit(submissionRevision: Long) {
        val pending = candidate ?: return
        if (pending.submissionRevision != submissionRevision) return
        bind(
            item = pending.item,
            payload = pending.payload,
            submissionRevision = pending.submissionRevision,
        )
    }

    fun hasCommitted(submissionRevision: Long): Boolean = submissionRevision <= committedRevision

    fun discard(submissionRevision: Long) {
        if (candidate?.submissionRevision == submissionRevision) {
            candidate = null
        }
    }

    fun recycle() {
        session?.dispose()
        session = null
        candidate = null
        currentKey = null
        currentContentToken = null
        committedRevision = Long.MIN_VALUE
        clearContainer()
    }

    private fun apply(
        item: LazyListItem,
        payload: Any?,
    ) {
        val currentSession = session
        when {
            currentSession == null || currentKey != item.key -> {
                replaceSession(item)
            }

            payload is LazyListChangePayload.ContentTokenChanged ||
                currentContentToken != item.contentToken -> {
                updateOrReplaceSession(item, currentSession)
            }

            item.sessionUpdater != null -> {
                checkNotNull(item.sessionUpdater).invoke(currentSession)
            }

            else -> {
                // Without an updater, a new committed submission can only install the latest
                // content factory by replacing the retained session.
                replaceSession(item)
            }
        }
        session?.render()
    }

    private fun updateOrReplaceSession(
        item: LazyListItem,
        currentSession: LazyListItemSession,
    ) {
        val updater = item.sessionUpdater
        if (updater != null) {
            updater(currentSession)
            currentContentToken = item.contentToken
        } else {
            replaceSession(item)
        }
    }

    private fun replaceSession(item: LazyListItem) {
        session?.dispose()
        clearContainer()
        val newSession = createSession(item)
        session = newSession
        currentKey = item.key
        currentContentToken = item.contentToken
        item.sessionUpdater?.invoke(newSession)
    }

    private fun nextImplicitSubmissionRevision(): Long {
        nextImplicitRevision = maxOf(nextImplicitRevision + 1L, committedRevision + 1L)
        return nextImplicitRevision
    }
}
