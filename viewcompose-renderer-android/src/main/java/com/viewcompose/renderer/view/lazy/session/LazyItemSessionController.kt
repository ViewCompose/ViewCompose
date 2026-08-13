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
    private var preparedRevision = Long.MIN_VALUE
    private var active = false

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long = nextImplicitSubmissionRevision(),
    ) {
        if (submissionRevision <= committedRevision) return
        val canActivatePrepared = !active &&
            preparedRevision == submissionRevision &&
            currentKey == item.key &&
            currentContentToken == item.contentToken
        if (canActivatePrepared) {
            checkNotNull(session).activate()
        } else {
            if (!active && session != null) {
                releaseSessionForReplacement()
            }
            applyActive(item = item, payload = payload)
        }
        active = true
        committedRevision = submissionRevision
        preparedRevision = Long.MIN_VALUE
        candidate = null
    }

    fun prepare(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long,
    ) {
        if (!stageCandidate(item, payload, submissionRevision)) return
        if (active || preparedRevision == submissionRevision) return

        releaseSessionForReplacement()
        replaceSession(item)
        checkNotNull(session).prepare()
        preparedRevision = submissionRevision
    }

    fun stage(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long,
    ) {
        stageCandidate(item, payload, submissionRevision)
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
            if (!active && preparedRevision == submissionRevision) {
                disposeSession()
            }
        }
    }

    fun recycle() {
        disposeSession()
        candidate = null
        committedRevision = Long.MIN_VALUE
        active = false
    }

    private fun applyActive(
        item: LazyListItem,
        payload: Any?,
    ) {
        val currentSession = session
        val replaced = when {
            currentSession == null || currentKey != item.key -> {
                replaceSession(item)
                true
            }

            payload is LazyListChangePayload.ContentTokenChanged ||
                currentContentToken != item.contentToken -> {
                updateOrReplaceSession(item, currentSession)
            }

            item.sessionUpdater != null -> {
                checkNotNull(item.sessionUpdater).invoke(currentSession)
                false
            }

            else -> {
                // Without an updater, a new committed submission can only install the latest
                // content factory by replacing the retained session.
                replaceSession(item)
                true
            }
        }
        if (replaced) {
            checkNotNull(session).activate()
        } else {
            checkNotNull(session).render()
        }
    }

    private fun updateOrReplaceSession(
        item: LazyListItem,
        currentSession: LazyListItemSession,
    ): Boolean {
        val updater = item.sessionUpdater
        if (updater != null) {
            updater(currentSession)
            currentContentToken = item.contentToken
            return false
        } else {
            replaceSession(item)
            return true
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

    private fun disposeSession() {
        session?.dispose()
        session = null
        currentKey = null
        currentContentToken = null
        preparedRevision = Long.MIN_VALUE
        clearContainer()
    }

    private fun releaseSessionForReplacement() {
        session?.dispose()
        session = null
        currentKey = null
        currentContentToken = null
        preparedRevision = Long.MIN_VALUE
    }

    private fun stageCandidate(
        item: LazyListItem,
        payload: Any?,
        submissionRevision: Long,
    ): Boolean {
        if (submissionRevision <= committedRevision) return false
        val currentCandidate = candidate
        if (currentCandidate != null) {
            if (submissionRevision <= currentCandidate.submissionRevision) {
                return false
            }
        }
        candidate = Candidate(
            item = item,
            payload = payload,
            submissionRevision = submissionRevision,
        )
        return true
    }

    private fun nextImplicitSubmissionRevision(): Long {
        nextImplicitRevision = maxOf(nextImplicitRevision + 1L, committedRevision + 1L)
        return nextImplicitRevision
    }
}
