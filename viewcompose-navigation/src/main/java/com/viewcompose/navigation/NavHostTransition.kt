package com.viewcompose.navigation

import com.viewcompose.navigation.core.NavBackStackSnapshot
import com.viewcompose.navigation.core.NavCommand
import com.viewcompose.navigation.core.NavEntry
import com.viewcompose.navigation.core.NavEntryId
import com.viewcompose.navigation.core.NavPaneScene
import com.viewcompose.navigation.core.NavStackMutation

/**
 * Monotonic identifier for one visual transition.
 */
@JvmInline
internal value class NavHostTransitionId(
    val value: Long,
)

/**
 * Transition terminal reason, distinguishing completion, redirection, and host destruction.
 */
internal enum class NavHostTransitionOutcome {
    Completed,
    Cancelled,
    Redirected,
    HostDestroyed,
}

/**
 * Visual transition model for a committed navigation transaction.
 *
 * [beforeScene] and [afterScene] keep pane projections, [visibleEntryIds] bounds entries retained
 * in the host, and [layerOrder] determines draw order during animation.
 */
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

/**
 * Result returned to the coordinator when a transition finishes.
 */
internal data class NavHostTransitionResult(
    val transition: NavHostTransition,
    val outcome: NavHostTransitionOutcome,
)

/**
 * Monotonic identifier for one predictive-back preview.
 */
@JvmInline
internal value class NavHostBackPreviewId(
    val value: Long,
)

/**
 * Screen edge where a predictive-back gesture began.
 */
internal enum class NavHostBackSwipeEdge {
    Left,
    Right,
    None,
}

/**
 * Gesture sample reported by the Android back dispatcher.
 */
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

/**
 * Read-only preview model used to reveal the previous destination during predictive back.
 */
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

/**
 * Control handle for an in-flight transition.
 */
internal fun interface NavHostTransitionHandle {
    fun cancel()

    /**
     * Stops the current animator while preserving its visual properties for the next transition.
     */
    fun redirect() {
        cancel()
    }
}

/**
 * Control handle for an in-flight predictive-back preview.
 */
internal interface NavHostBackPreviewHandle {
    fun update(event: NavHostBackEvent)

    fun cancel()

    fun redirect() {
        cancel()
    }

    fun dispose() {
        cancel()
    }

    fun commit(
        transition: NavHostTransition,
        onCompleted: () -> Unit,
    ): NavHostTransitionHandle
}

/**
 * Abstraction that performs native View transitions and predictive-back previews.
 */
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

    fun destroy() = Unit
}

/**
 * No-animation driver for tests, disabled motion, or hosts that cannot animate.
 */
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
