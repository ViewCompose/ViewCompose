package com.viewcompose.navigation

import android.content.Context
import android.view.View
import androidx.annotation.MainThread
import com.viewcompose.host.android.renderInto
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.widget.core.OverlayHost
import com.viewcompose.widget.core.OverlayHostDefaults
import com.viewcompose.widget.core.RenderFailure
import com.viewcompose.widget.core.RenderFrameStatus
import com.viewcompose.widget.core.UiLocalSnapshot
import com.viewcompose.widget.core.UiTreeBuilder
import com.viewcompose.widget.core.withUiLocalSnapshot

internal typealias NavDestinationContent = UiTreeBuilder.(NavEntry) -> Unit

internal class NavDestinationSessionStore(
    context: Context,
    private val ownerStore: NavEntryOwnerStore,
    private val overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    private val debug: Boolean = false,
    private val onRenderFailure: ((RenderFailure) -> Unit)? = null,
) {
    val hostView = NavHostView(context)

    private val sessions = linkedMapOf<NavEntryId, NavDestinationSession>()
    private var pendingEntryId: NavEntryId? = null
    private var pendingCandidate: NavDestinationCandidate? = null
    private var destroyed = false

    @MainThread
    fun prepare(
        entry: NavEntry,
        localSnapshot: UiLocalSnapshot,
        content: NavDestinationContent,
    ): NavDestinationPreparation {
        check(!destroyed) {
            "A destroyed destination session store cannot prepare pages."
        }
        check(pendingEntryId == null) {
            "Destination ${pendingEntryId} is already prepared."
        }
        check(entry.id !in sessions) {
            "Destination ${entry.id} already owns a committed page session."
        }
        check(ownerStore.ownerOrNull(entry.id) == null) {
            "Destination ${entry.id} already owns page state without a committed session."
        }
        pendingEntryId = entry.id
        val owner = ownerStore.ownerFor(entry)
        owner.moveTo(NavEntryLifecycleState.Created)
        val container = destinationContainer(hostView.context)
        val renderEnvironment = NavDestinationRenderEnvironment(
            localSnapshot = localSnapshot,
            content = content,
        )
        var renderSession: com.viewcompose.host.android.RenderSession? = null
        return try {
            renderSession = renderInto(
                container = container,
                debug = debug,
                debugTag = "ViewComposeNavigation:${entry.route.name}:${entry.id}",
                overlayHost = overlayHost,
                onRenderFailure = onRenderFailure,
            ) {
                withUiLocalSnapshot(renderEnvironment.localSnapshot) {
                    ProvideNavEntryOwner(owner) {
                        renderEnvironment.content(this, entry)
                    }
                }
            }
            val destinationSession = NavDestinationSession(
                entry = entry,
                owner = owner,
                container = container,
                renderSession = renderSession,
                renderEnvironment = renderEnvironment,
            )
            val frameReport = destinationSession.lastFrameReport
            if (frameReport?.status != RenderFrameStatus.Committed) {
                cleanupFailedPreparation(
                    entryId = entry.id,
                    renderSession = renderSession,
                    container = container,
                )
                NavDestinationPreparation.Failed(
                    entry = entry,
                    frameReport = frameReport,
                    cause = frameReport?.failures?.firstOrNull()?.cause,
                )
            } else {
                val candidate = NavDestinationCandidate(
                    store = this,
                    destinationSession = destinationSession,
                )
                pendingCandidate = candidate
                NavDestinationPreparation.Ready(candidate)
            }
        } catch (throwable: Throwable) {
            cleanupFailedPreparation(
                entryId = entry.id,
                renderSession = renderSession,
                container = container,
            )
            NavDestinationPreparation.Failed(
                entry = entry,
                frameReport = renderSession?.lastFrameReport,
                cause = throwable,
            )
        }
    }

    @MainThread
    internal fun stage(candidate: NavDestinationCandidate) {
        requirePending(candidate)
        val container = candidate.destinationSession.container
        check(container.parent == null) {
            "Destination candidate ${candidate.entry.id} is already attached."
        }
        container.visibility = View.GONE
        hostView.addView(container)
    }

    @MainThread
    internal fun commit(candidate: NavDestinationCandidate): NavDestinationSession {
        requirePending(candidate)
        val session = candidate.destinationSession
        check(session.container.parent === hostView) {
            "Destination candidate ${candidate.entry.id} must be staged in its host before commit."
        }
        check(sessions.putIfAbsent(candidate.entry.id, session) == null) {
            "Destination ${candidate.entry.id} already has a committed page session."
        }
        pendingCandidate = null
        pendingEntryId = null
        return session
    }

    @MainThread
    internal fun rollback(candidate: NavDestinationCandidate) {
        requirePending(candidate)
        val session = candidate.destinationSession
        if (session.container.parent === hostView) {
            hostView.removeView(session.container)
        }
        try {
            session.dispose()
        } finally {
            ownerStore.remove(candidate.entry.id)
            pendingCandidate = null
            pendingEntryId = null
        }
    }

    @MainThread
    fun sessionOrNull(entryId: NavEntryId): NavDestinationSession? = sessions[entryId]

    @MainThread
    fun present(
        layerOrder: List<NavEntryId>,
        visibleEntryIds: Set<NavEntryId>,
    ) {
        check(layerOrder.distinct().size == layerOrder.size) {
            "Destination layer order must not contain duplicate entry IDs."
        }
        check(layerOrder.all(sessions::containsKey)) {
            "Destination layer order contains an entry without a committed page session."
        }
        check(visibleEntryIds.all(sessions::containsKey)) {
            "A visible destination must own a committed page session."
        }
        check(visibleEntryIds.all(layerOrder::contains)) {
            "Every visible destination must be included in the destination layer order."
        }
        sessions.forEach { (entryId, session) ->
            session.container.visibility = if (entryId in visibleEntryIds) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        layerOrder.forEach { entryId ->
            hostView.bringChildToFront(checkNotNull(sessions[entryId]).container)
        }
    }

    @MainThread
    fun remove(entryId: NavEntryId) {
        val session = sessions.remove(entryId) ?: return
        if (session.container.parent === hostView) {
            hostView.removeView(session.container)
        }
        try {
            session.dispose()
        } finally {
            ownerStore.remove(entryId)
        }
    }

    @MainThread
    fun destroy() {
        if (destroyed) {
            return
        }
        val failures = mutableListOf<Throwable>()
        pendingCandidate?.let { candidate ->
            runCatching(candidate::rollback)
                .exceptionOrNull()
                ?.let(failures::add)
        }
        sessions.values.toList().asReversed().forEach { session ->
            if (session.container.parent === hostView) {
                hostView.removeView(session.container)
            }
            runCatching {
                session.dispose()
            }.exceptionOrNull()?.let(failures::add)
        }
        sessions.clear()
        try {
            ownerStore.destroy()
        } catch (throwable: Throwable) {
            failures += throwable
        } finally {
            destroyed = true
        }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    private fun requirePending(candidate: NavDestinationCandidate) {
        check(pendingCandidate === candidate && pendingEntryId == candidate.entry.id) {
            "Destination candidate ${candidate.entry.id} is not the pending page."
        }
    }

    private fun cleanupFailedPreparation(
        entryId: NavEntryId,
        renderSession: com.viewcompose.host.android.RenderSession?,
        container: View,
    ) {
        if (container.parent === hostView) {
            hostView.removeView(container)
        }
        try {
            renderSession?.dispose()
        } finally {
            ownerStore.remove(entryId)
            pendingCandidate = null
            pendingEntryId = null
        }
    }
}
