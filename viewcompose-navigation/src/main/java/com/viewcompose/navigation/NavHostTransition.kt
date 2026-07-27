package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavPaneScene
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
    val beforeScene: NavPaneScene,
    val afterScene: NavPaneScene,
    val retainedEntries: List<NavEntry>,
    val visibleEntryIds: Set<NavEntryId>,
    val layerOrder: List<NavEntryId>,
)

internal data class NavHostTransitionResult(
    val transition: NavHostTransition,
    val outcome: NavHostTransitionOutcome,
)

@JvmInline
internal value class NavHostBackPreviewId(
    val value: Long,
)

internal enum class NavHostBackSwipeEdge {
    Left,
    Right,
    None,
}

internal data class NavHostBackEvent(
    val touchX: Float,
    val touchY: Float,
    val progress: Float,
    val swipeEdge: NavHostBackSwipeEdge,
    val frameTimeMillis: Long,
) {
    init {
        require(progress.isFinite() && progress in 0f..1f) {
            "Back progress must be finite and within 0..1; value=$progress."
        }
    }
}

internal data class NavHostBackPreview(
    val id: NavHostBackPreviewId,
    val command: NavCommand,
    val snapshot: NavBackStackSnapshot,
    val outgoingEntry: NavEntry,
    val incomingEntry: NavEntry,
    val beforeScene: NavPaneScene,
    val afterScene: NavPaneScene,
    val retainedEntries: List<NavEntry>,
    val visibleEntryIds: Set<NavEntryId>,
    val layerOrder: List<NavEntryId>,
)

internal fun interface NavHostTransitionHandle {
    fun cancel()

    /**
     * Stops the current animator while preserving its visual properties for the next transition.
     */
    fun redirect() {
        cancel()
    }
}

internal interface NavHostBackPreviewHandle {
    fun update(event: NavHostBackEvent)

    fun cancel()

    fun commit(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle
}

internal interface NavHostTransitionDriver {
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

    fun startBackPreview(
        preview: NavHostBackPreview,
        initialEvent: NavHostBackEvent,
    ): NavHostBackPreviewHandle
}

internal object ImmediateNavHostTransitionDriver : NavHostTransitionDriver {
    override fun start(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle {
        onCompleted()
        return NavHostTransitionHandle {}
    }

    override fun startBackPreview(
        preview: NavHostBackPreview,
        initialEvent: NavHostBackEvent,
    ): NavHostBackPreviewHandle {
        return object : NavHostBackPreviewHandle {
            override fun update(event: NavHostBackEvent) = Unit

            override fun cancel() = Unit

            override fun commit(
                transition: NavHostTransition,
                onCompleted: () -> Unit,
            ): NavHostTransitionHandle {
                onCompleted()
                return NavHostTransitionHandle {}
            }
        }
    }
}
