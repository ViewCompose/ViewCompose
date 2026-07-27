package com.viewcompose.navigation

import android.view.ViewGroup
import com.viewcompose.host.android.RenderSession
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.widget.core.RenderFrameReport
import com.viewcompose.widget.core.UiLocalSnapshot

internal class NavDestinationSession(
    val entry: NavEntry,
    val owner: NavEntryOwner,
    val container: ViewGroup,
    private val renderSession: RenderSession,
    private val renderEnvironment: NavDestinationRenderEnvironment,
) {
    val lastFrameReport: RenderFrameReport?
        get() = renderSession.lastFrameReport

    fun render(
        localSnapshot: UiLocalSnapshot,
        content: NavDestinationContent,
    ): RenderFrameReport? {
        renderEnvironment.localSnapshot = localSnapshot
        renderEnvironment.content = content
        renderSession.render()
        return lastFrameReport
    }

    fun dispose() {
        renderSession.dispose()
    }
}

internal class NavDestinationRenderEnvironment(
    var localSnapshot: UiLocalSnapshot,
    var content: NavDestinationContent,
)

internal enum class NavDestinationCandidateStatus {
    Prepared,
    Staged,
    Committed,
    RolledBack,
}

internal class NavDestinationCandidate internal constructor(
    private val store: NavDestinationSessionStore,
    internal val destinationSession: NavDestinationSession,
    internal val newGraphOwnerIds: Set<NavEntryId>,
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

internal sealed interface NavDestinationPreparation {
    data class Ready(
        val candidate: NavDestinationCandidate,
    ) : NavDestinationPreparation

    data class Failed(
        val entry: NavEntry,
        val frameReport: RenderFrameReport?,
        val cause: Throwable?,
    ) : NavDestinationPreparation
}
