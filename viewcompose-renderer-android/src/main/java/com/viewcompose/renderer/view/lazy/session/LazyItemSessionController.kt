package com.viewcompose.renderer.view.lazy.session

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.ReusableItemPresentation

/** Direct holder bridge that keeps hot session lifecycle calls out of generic Kotlin callbacks. */
internal interface LazyItemSessionHost {
    fun createSession(item: LazyListItem): LazyListItemSession

    fun clearContainer()
}

/** Allocation-free result of routing one adapter submission through an item session. */
internal enum class LazyItemBindOutcome(
    val satisfiesSubmission: Boolean,
) {
    AlreadyCommitted(satisfiesSubmission = true),
    AcceptedUnchanged(satisfiesSubmission = true),
    ActivatedPrepared(satisfiesSubmission = true),
    ActivatedNewSession(satisfiesSubmission = true),
    RenderedRevision(satisfiesSubmission = true),
    PreparedNewSession(satisfiesSubmission = false),
    Staged(satisfiesSubmission = false),
    NotCommitted(satisfiesSubmission = false),
}

/** Manages the child render-session lifecycle and submission revisions for one retained item. */
internal class LazyItemSessionController(
    private val host: LazyItemSessionHost,
) {
    /** Lambda adapter retained for isolated tests and non-hot internal call sites. */
    constructor(
        createSession: (LazyListItem) -> LazyListItemSession,
        clearContainer: () -> Unit,
    ) : this(
        host = object : LazyItemSessionHost {
            override fun createSession(item: LazyListItem): LazyListItemSession = createSession(item)

            override fun clearContainer() = clearContainer()
        },
    )

    private data class Candidate(
        val item: LazyListItem,
        val payload: Any?,
        val submissionRevision: Long,
    )

    private var currentKey: Any? = null
    private var currentContentType: Any? = null
    private var currentItemKind: LazyListItemKind? = null
    private var currentContentRevision: Any? = null
    private var currentEnvironmentRevision: Any? = null
    private var committedRevision = Long.MIN_VALUE
    private var committedItem: LazyListItem? = null
    private var nextImplicitRevision = 0L
    private var candidate: Candidate? = null
    private var session: LazyListItemSession? = null
    private var preparedRevision = Long.MIN_VALUE
    private var active = false
    private var pendingPresentation: ReusableItemPresentation? = null
    val hasPendingPresentation: Boolean
        get() = pendingPresentation != null

    fun bind(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long = nextImplicitSubmissionRevision(),
    ): LazyItemBindOutcome {
        if (submissionRevision <= committedRevision) {
            return LazyItemBindOutcome.AlreadyCommitted
        }
        val canActivatePrepared = !active &&
            preparedRevision == submissionRevision &&
            currentKey == item.key &&
            currentContentType == item.contentType &&
            currentItemKind == item.kind &&
            hasSameRevisions(item)
        val outcome = try {
            if (canActivatePrepared) {
                if (checkNotNull(session).activate()) {
                    LazyItemBindOutcome.ActivatedPrepared
                } else {
                    markInstalledRevisionUncommitted()
                    LazyItemBindOutcome.NotCommitted
                }
            } else {
                if (!active && session != null) {
                    releaseSessionForReplacement()
                }
                applyActive(item = item)
            }
        } catch (error: Throwable) {
            abandonSessionAfterBindingFailure(error)
            throw error
        }
        active = true
        preparedRevision = Long.MIN_VALUE
        if (!outcome.satisfiesSubmission) return outcome
        committedRevision = submissionRevision
        committedItem = item
        candidate = null
        return outcome
    }

    fun prepare(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long,
    ): LazyItemBindOutcome {
        if (!stageCandidate(item, payload, submissionRevision)) {
            return if (submissionRevision <= committedRevision) {
                LazyItemBindOutcome.AlreadyCommitted
            } else {
                LazyItemBindOutcome.Staged
            }
        }
        return prepareCandidate(item, submissionRevision)
    }

    private fun prepareCandidate(
        item: LazyListItem,
        submissionRevision: Long,
    ): LazyItemBindOutcome {
        if (active || preparedRevision == submissionRevision) {
            return LazyItemBindOutcome.Staged
        }

        try {
            releaseSessionForReplacement()
            replaceSession(item)
            checkNotNull(session).prepare()
        } catch (error: Throwable) {
            abandonSessionAfterBindingFailure(error)
            candidate = null
            throw error
        }
        preparedRevision = submissionRevision
        return LazyItemBindOutcome.PreparedNewSession
    }

    fun stage(
        item: LazyListItem,
        payload: Any? = null,
        submissionRevision: Long,
    ) {
        stageCandidate(item, payload, submissionRevision)
    }

    fun commit(submissionRevision: Long): LazyItemBindOutcome {
        val pending = candidate ?: return if (hasCommitted(submissionRevision)) {
            LazyItemBindOutcome.AlreadyCommitted
        } else {
            LazyItemBindOutcome.NotCommitted
        }
        if (pending.submissionRevision != submissionRevision) {
            return LazyItemBindOutcome.NotCommitted
        }
        return bind(
            item = pending.item,
            payload = pending.payload,
            submissionRevision = pending.submissionRevision,
        )
    }

    fun hasCommitted(submissionRevision: Long): Boolean = submissionRevision <= committedRevision

    fun hasCommittedExact(
        item: LazyListItem,
        submissionRevision: Long,
    ): Boolean {
        return committedRevision == submissionRevision &&
            committedItem === item
    }

    fun discard(submissionRevision: Long) {
        if (candidate?.submissionRevision == submissionRevision) {
            candidate = null
            if (!active && preparedRevision == submissionRevision) {
                disposeSession()
            }
        }
    }

    fun recycle() {
        try {
            disposeSession()
        } finally {
            candidate = null
            committedRevision = Long.MIN_VALUE
            committedItem = null
            active = false
        }
    }

    fun detachForReuse(): ReusableItemPresentation? {
        val stagedPresentation = pendingPresentation
        pendingPresentation = null
        var reusable: ReusableItemPresentation? = null
        var failure: Throwable? = null
        try {
            reusable = session?.disposeForReuse()
        } catch (error: Throwable) {
            failure = error
        } finally {
            session = null
            currentKey = null
            currentContentType = null
            currentItemKind = null
            currentContentRevision = null
            currentEnvironmentRevision = null
            preparedRevision = Long.MIN_VALUE
            candidate = null
            committedRevision = Long.MIN_VALUE
            committedItem = null
            active = false
            try {
                host.clearContainer()
            } catch (clearError: Throwable) {
                if (failure == null) failure = clearError else failure.addSuppressed(clearError)
            }
        }
        if (failure != null) {
            reusable?.let { presentation ->
                try {
                    presentation.release()
                } catch (releaseError: Throwable) {
                    failure.addSuppressed(releaseError)
                }
            }
            stagedPresentation?.let { presentation ->
                try {
                    presentation.release()
                } catch (releaseError: Throwable) {
                    failure.addSuppressed(releaseError)
                }
            }
            throw failure
        }
        if (reusable != null) {
            stagedPresentation?.release()
            return reusable
        }
        return stagedPresentation
    }

    fun adoptForNextSession(presentation: ReusableItemPresentation) {
        val previous = pendingPresentation
        pendingPresentation = null
        try {
            previous?.release()
        } catch (error: Throwable) {
            try {
                presentation.release()
            } catch (releaseError: Throwable) {
                error.addSuppressed(releaseError)
            }
            throw error
        }
        pendingPresentation = presentation
    }

    private fun applyActive(
        item: LazyListItem,
    ): LazyItemBindOutcome {
        val currentSession = session
        val presentationTypeChanged = currentSession != null &&
            (currentContentType != item.contentType || currentItemKind != item.kind)
        val revisionsChanged = !hasSameRevisions(item)
        return when {
            currentSession == null || currentKey != item.key || presentationTypeChanged -> {
                replaceSession(item)
                if (checkNotNull(session).activate()) {
                    LazyItemBindOutcome.ActivatedNewSession
                } else {
                    markInstalledRevisionUncommitted()
                    LazyItemBindOutcome.NotCommitted
                }
            }

            revisionsChanged -> {
                item.sessionUpdater(currentSession)
                val committed = currentSession.render()
                if (committed) {
                    currentContentRevision = item.contentRevision
                    currentEnvironmentRevision = item.environmentRevision
                }
                if (committed) {
                    LazyItemBindOutcome.RenderedRevision
                } else {
                    LazyItemBindOutcome.NotCommitted
                }
            }

            else -> {
                // A newer parent submission is not itself an item invalidation. Preserve the
                // installed closure and perform no child composition or native patch. The newer
                // parent submission is nevertheless satisfied by the already committed item.
                LazyItemBindOutcome.AcceptedUnchanged
            }
        }
    }

    private fun replaceSession(item: LazyListItem) {
        releaseSessionForReplacement()
        host.clearContainer()
        val newSession = host.createSession(item)
        try {
            item.sessionUpdater(newSession)
            pendingPresentation?.let { presentation ->
                pendingPresentation = null
                val adopted = try {
                    newSession.adoptReusablePresentation(presentation)
                } catch (error: Throwable) {
                    try {
                        presentation.release()
                    } catch (releaseError: Throwable) {
                        error.addSuppressed(releaseError)
                    }
                    throw error
                }
                if (!adopted) {
                    presentation.release()
                }
            }
        } catch (error: Throwable) {
            try {
                newSession.dispose()
            } catch (disposeError: Throwable) {
                error.addSuppressed(disposeError)
            }
            throw error
        }
        session = newSession
        currentKey = item.key
        currentContentType = item.contentType
        currentItemKind = item.kind
        currentContentRevision = item.contentRevision
        currentEnvironmentRevision = item.environmentRevision
    }

    private fun disposeSession() {
        var failure: Throwable? = null
        try {
            terminateCurrentSession()
        } catch (error: Throwable) {
            failure = error
        }
        val presentation = pendingPresentation
        pendingPresentation = null
        try {
            presentation?.release()
        } catch (releaseError: Throwable) {
            if (failure == null) failure = releaseError else failure.addSuppressed(releaseError)
        }
        failure?.let { throw it }
    }

    private fun terminateCurrentSession() {
        val ownedSession = session
        session = null
        currentKey = null
        currentContentType = null
        currentItemKind = null
        currentContentRevision = null
        currentEnvironmentRevision = null
        preparedRevision = Long.MIN_VALUE
        committedRevision = Long.MIN_VALUE
        committedItem = null
        var failure: Throwable? = null
        try {
            ownedSession?.dispose()
        } catch (disposeError: Throwable) {
            failure = disposeError
        }
        try {
            host.clearContainer()
        } catch (clearError: Throwable) {
            if (failure == null) failure = clearError else failure.addSuppressed(clearError)
        }
        failure?.let { throw it }
    }

    private fun releaseSessionForReplacement() {
        val ownedSession = session
        session = null
        currentKey = null
        currentContentType = null
        currentItemKind = null
        currentContentRevision = null
        currentEnvironmentRevision = null
        preparedRevision = Long.MIN_VALUE
        committedRevision = Long.MIN_VALUE
        committedItem = null
        ownedSession?.dispose()
    }

    private fun abandonSessionAfterBindingFailure(primary: Throwable) {
        active = false
        committedRevision = Long.MIN_VALUE
        committedItem = null
        preparedRevision = Long.MIN_VALUE
        try {
            disposeSession()
        } catch (cleanupError: Throwable) {
            primary.addSuppressed(cleanupError)
        }
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

    private fun hasSameRevisions(item: LazyListItem): Boolean {
        return currentContentRevision == item.contentRevision &&
            currentEnvironmentRevision == item.environmentRevision
    }

    private fun markInstalledRevisionUncommitted() {
        currentContentRevision = UncommittedRevision
        currentEnvironmentRevision = UncommittedRevision
    }

    private data object UncommittedRevision
}
