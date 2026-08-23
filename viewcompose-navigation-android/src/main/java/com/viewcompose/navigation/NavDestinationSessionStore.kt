package com.viewcompose.navigation

import android.view.View
import androidx.annotation.MainThread
import com.viewcompose.host.android.renderInto
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavEntryLifecycleState
import com.viewcompose.navigation.core.NavHostLifecycleState
import com.viewcompose.ui.foundation.OverlayHost
import com.viewcompose.ui.foundation.OverlayHostDefaults
import com.viewcompose.ui.foundation.RenderFrameStatus
import com.viewcompose.ui.foundation.RenderSessionRole
import com.viewcompose.ui.foundation.UiLocalSnapshot
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.withUiLocalSnapshot

internal typealias NavDestinationContent = UiTreeBuilder.(NavEntry) -> Unit

/**
 * Manages destination View sessions, candidate sessions, and host child layout.
 */
internal class NavDestinationSessionStore(
    val hostView: NavHostView,
    private val ownerStore: NavEntryOwnerStore,
    private val overlayHost: OverlayHost = OverlayHostDefaults.noOp,
    private val debug: Boolean = false,
    private val debugTag: String = "ViewComposeNavigation",
) {
    private val sessions = linkedMapOf<NavEntryId, NavDestinationSession>()
    private var pendingEntryId: NavEntryId? = null
    private var pendingCandidate: NavDestinationCandidate? = null
    private var destroyed = false

    @MainThread
    fun prepare(
        entry: NavEntry,
        localSnapshot: UiLocalSnapshot,
        hostLifecycleState: NavHostLifecycleState,
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
        val newGraphOwnerIds = entry.graphEntries
            .filter { graphEntry -> ownerStore.graphOwnerOrNull(graphEntry.id) == null }
            .mapTo(linkedSetOf()) { graphEntry -> graphEntry.id }
        val container = destinationContainer(hostView.context)
        val renderEnvironment = NavDestinationRenderEnvironment(
            localSnapshot = localSnapshot,
            content = content,
        )
        var renderSession: com.viewcompose.host.android.RenderSession? = null
        return try {
            // Create graph owners before the destination owner so lifecycle teardown can destroy child first.
            val graphOwners = entry.graphEntries.mapIndexed { depth, graphEntry ->
                ownerStore.graphOwnerFor(
                    entry = graphEntry,
                    depth = depth,
                )
            }
            val owner = ownerStore.ownerFor(entry)
            when (hostLifecycleState) {
                NavHostLifecycleState.Initialized -> Unit
                NavHostLifecycleState.Created,
                NavHostLifecycleState.Started,
                NavHostLifecycleState.Resumed,
                -> {
                    newGraphOwnerIds.forEach { graphEntryId ->
                        checkNotNull(ownerStore.graphOwnerOrNull(graphEntryId))
                            .delegate
                            .moveTo(NavEntryLifecycleState.Created)
                    }
                    owner.moveTo(NavEntryLifecycleState.Created)
                }
                NavHostLifecycleState.Destroyed -> {
                    error("A destroyed navigation host cannot prepare a destination.")
                }
            }
            renderSession = renderInto(
                container = container,
                debug = debug,
                debugTag = "$debugTag:${entry.route.name}:${entry.id}",
                overlayHost = overlayHost,
                role = RenderSessionRole.NavigationDestination,
                parentLocalSnapshot = renderEnvironment.localSnapshot,
            ) {
                withUiLocalSnapshot(renderEnvironment.localSnapshot) {
                    ProvideNavGraphOwnerScope(
                        NavGraphOwnerScope(
                            entries = entry.graphEntries,
                            owners = graphOwners,
                        ),
                    ) {
                        ProvideNavEntryOwner(owner) {
                            renderEnvironment.content(this, entry)
                        }
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
                    newGraphOwnerIds = newGraphOwnerIds,
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
                    newGraphOwnerIds = newGraphOwnerIds,
                )
                pendingCandidate = candidate
                NavDestinationPreparation.Ready(candidate)
            }
        } catch (throwable: Throwable) {
            cleanupFailedPreparation(
                entryId = entry.id,
                newGraphOwnerIds = newGraphOwnerIds,
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
        val session = candidate.destinationSession
        val container = session.container
        check(container.parent == null) {
            "Destination candidate ${candidate.entry.id} is already attached."
        }
        session.setRenderingActive(false)
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
            candidate.newGraphOwnerIds.forEach(ownerStore::removeGraphOwner)
            pendingCandidate = null
            pendingEntryId = null
        }
    }

    @MainThread
    fun sessionOrNull(entryId: NavEntryId): NavDestinationSession? = sessions[entryId]

    @MainThread
    fun updateRenderEnvironment(
        localSnapshot: UiLocalSnapshot,
        content: NavDestinationContent,
    ) {
        sessions.values.forEach { session ->
            session.updateEnvironment(localSnapshot, content)
        }
    }

    @MainThread
    fun present(
        layerOrder: List<NavEntryId>,
        visibleEntryIds: Set<NavEntryId>,
        paneLayouts: Map<NavEntryId, NavPaneLayout> = visibleEntryIds.associateWith {
            NavPaneLayout.Single
        },
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
        check(paneLayouts.keys == visibleEntryIds) {
            "Every visible destination must have exactly one pane layout."
        }
        // Visibility and render-active are separate: hidden pages keep sessions but pause frame work.
        sessions.forEach { (entryId, session) ->
            if (entryId in visibleEntryIds) {
                session.container.visibility = View.VISIBLE
                session.setRenderingActive(true)
            } else {
                session.setRenderingActive(false)
                session.container.visibility = View.GONE
            }
        }
        hostView.updatePaneLayouts(
            paneLayouts.mapKeys { (entryId, _) ->
                checkNotNull(sessions[entryId]).container
            },
        )
        layerOrder.forEach { entryId ->
            hostView.bringChildToFront(checkNotNull(sessions[entryId]).container)
        }
    }

    /**
     * Suspends or resumes frame-driven rendering without changing destination visibility.
     *
     * A committed transition animates the already-rendered outgoing surface. Keeping that
     * destination's composition active would let lifecycle invalidations rebuild its View tree during
     * the first motion frame, even though those updates cannot affect the settled scene.
     */
    @MainThread
    fun setRenderingActive(
        entryIds: Set<NavEntryId>,
        active: Boolean,
    ) {
        check(entryIds.all(sessions::containsKey)) {
            "Rendering state contains an entry without a committed page session."
        }
        entryIds.forEach { entryId ->
            checkNotNull(sessions[entryId]).setRenderingActive(active)
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
        newGraphOwnerIds: Set<NavEntryId>,
        renderSession: com.viewcompose.host.android.RenderSession?,
        container: View,
    ) {
        if (container.parent === hostView) {
            hostView.removeView(container)
        }
        try {
            renderSession?.dispose()
        } finally {
            // Failed candidates must also release new graph owners so retries do not reuse half-built owners.
            ownerStore.remove(entryId)
            newGraphOwnerIds.forEach(ownerStore::removeGraphOwner)
            pendingCandidate = null
            pendingEntryId = null
        }
    }
}
