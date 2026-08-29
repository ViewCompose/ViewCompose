package com.viewcompose.navigation

import android.view.ViewGroup
import com.viewcompose.host.android.RenderSession
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.ui.foundation.RenderFrameReport
import com.viewcompose.ui.foundation.UiLocalSnapshot

/**
 * Android View render session for one committed destination.
 */
internal class NavDestinationSession(
    val entry: NavEntry,
    val owner: NavEntryOwner,
    val container: ViewGroup,
    private val renderSession: RenderSession,
    private val renderEnvironment: NavDestinationRenderEnvironment,
) {
    var isRenderingActive: Boolean = true
        private set

    val lastFrameReport: RenderFrameReport?
        get() = renderSession.lastFrameReport

    fun render(
        localSnapshot: UiLocalSnapshot,
        content: NavDestinationContent,
    ): RenderFrameReport? {
        updateEnvironment(localSnapshot, content)
        renderSession.render()
        return lastFrameReport
    }

    fun updateEnvironment(
        localSnapshot: UiLocalSnapshot,
        content: NavDestinationContent,
    ) {
        renderEnvironment.localSnapshot = localSnapshot
        renderEnvironment.content = content
    }

    fun setRenderingActive(active: Boolean) {
        if (isRenderingActive == active) {
            return
        }
        isRenderingActive = active
        renderSession.setRenderingActive(active)
    }

    fun dispose() {
        renderSession.dispose()
    }
}

/**
 * Mutable environment read by destination render closures.
 */
internal class NavDestinationRenderEnvironment(
    var localSnapshot: UiLocalSnapshot,
    var content: NavDestinationContent,
)

/**
 * Two-phase commit state for a candidate destination session.
 */
internal enum class NavDestinationCandidateStatus {
    Prepared,
    Staged,
    Committed,
    RolledBack,
}

/**
 * Prepared destination that has not been fully committed yet.
 */
internal class NavDestinationCandidate internal constructor(
    private val store: NavDestinationSessionStore,
    internal val destinationSession: NavDestinationSession,
    internal val newGraphOwnerIds: Set<NavEntryId>,
    internal val removeOwnerOnRollback: Boolean,
) : AutoCloseable {
    var status: NavDestinationCandidateStatus = NavDestinationCandidateStatus.Prepared
        private set

    val entry: NavEntry
        get() = destinationSession.entry

    fun stage() {
        check(status == NavDestinationCandidateStatus.Prepared) {
            "Destination candidate ${entry.id} is already $status."
        }
        store.stage(this)
        status = NavDestinationCandidateStatus.Staged
    }

    fun commit(): NavDestinationSession {
        check(status == NavDestinationCandidateStatus.Staged) {
            "Destination candidate ${entry.id} must be staged before commit; current=$status."
        }
        val committed = store.commit(this)
        status = NavDestinationCandidateStatus.Committed
        return committed
    }

    fun rollback() {
        check(
            status == NavDestinationCandidateStatus.Prepared ||
                status == NavDestinationCandidateStatus.Staged,
        ) {
            "Destination candidate ${entry.id} cannot roll back from $status."
        }
        try {
            store.rollback(this)
        } finally {
            status = NavDestinationCandidateStatus.RolledBack
        }
    }

    override fun close() {
        if (
            status == NavDestinationCandidateStatus.Prepared ||
            status == NavDestinationCandidateStatus.Staged
        ) {
            rollback()
        }
    }
}

/**
 * Result of preparing a destination session.
 */
internal sealed interface NavDestinationPreparation {
    /**
     * Rendering succeeded and the candidate can be staged/committed.
     */
    data class Ready(
        val candidate: NavDestinationCandidate,
    ) : NavDestinationPreparation

    /**
     * Rendering or owner creation failed; callers must keep the navigation stack uncommitted.
     */
    data class Failed(
        val entry: NavEntry,
        val frameReport: RenderFrameReport?,
        val cause: Throwable?,
    ) : NavDestinationPreparation
}
