package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavStackMutation

@JvmInline
internal value class NavHostTransitionId(
    val value: Long,
)

internal enum class NavHostTransitionOutcome {
    Completed,
    Cancelled,
    Redirected,
    HostDestroyed,
}

internal data class NavHostTransition(
    val id: NavHostTransitionId,
    val command: NavCommand,
    val before: NavBackStackSnapshot,
    val after: NavBackStackSnapshot,
    val mutation: NavStackMutation,
    val outgoingEntry: NavEntry,
    val incomingEntry: NavEntry,
    val retainedEntries: List<NavEntry>,
    val visibleEntryIds: Set<NavEntryId>,
    val layerOrder: List<NavEntryId>,
)

internal data class NavHostTransitionResult(
    val transition: NavHostTransition,
    val outcome: NavHostTransitionOutcome,
)

internal fun interface NavHostTransitionHandle {
    fun cancel()
}

internal fun interface NavHostTransitionDriver {
    /**
     * Starts visual work for [transition].
     *
     * [onCompleted] must be invoked on the Android main thread. The returned handle is cancelled
     * when a newer navigation command redirects this transition or when the host is destroyed.
     */
    fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle
}

internal object ImmediateNavHostTransitionDriver : NavHostTransitionDriver {
    override fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        onCompleted()
        return NavHostTransitionHandle {}
    }
}
