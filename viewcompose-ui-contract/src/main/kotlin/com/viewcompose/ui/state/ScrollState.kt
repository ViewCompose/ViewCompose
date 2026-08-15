package com.viewcompose.ui.state

import com.viewcompose.runtime.mutableStateOf

/**
 * Owns the observable offset and commands for one eager scroll container.
 *
 * Use this state with `ScrollableColumn` or `ScrollableRow`; lazy collections use
 * [LazyListState]. Reading [snapshot] or a derived property during composition records a normal
 * snapshot-state dependency. Offsets are non-negative renderer units measured from the logical
 * start edge and are physical pixels in the first-party Android renderer.
 *
 * The renderer attaches one [ScrollConnector] while mounted. Immediate commands retain their
 * offset while detached; animated commands are no-ops while detached. Commands, attachment, and
 * listeners are confined to the owning renderer thread, normally Android's main thread.
 *
 * @sample com.viewcompose.ui.samples.scrollStateSample
 * @param initialValue non-negative start-edge offset retained before attachment
 * @throws IllegalArgumentException if [initialValue] is negative
 */
class ScrollState(
    initialValue: Int = 0,
) {
    private var connector: ScrollConnector? = null
    private var connectorIdentity: Any? = null
    private val listeners = linkedSetOf<(ScrollStateSnapshot) -> Unit>()
    private val snapshotState = mutableStateOf(ScrollStateSnapshot.initial(initialValue))

    /** Latest immutable renderer snapshot; reads participate in snapshot observation. */
    val snapshot: ScrollStateSnapshot
        get() = snapshotState.value

    /** Current non-negative offset from the logical start edge. */
    val value: Int
        get() = snapshot.value

    /** Maximum reachable offset, or `null` before the renderer establishes a viewport. */
    val maxValue: Int?
        get() = snapshot.maxValue

    /** Current main-axis viewport size in renderer units. */
    val viewportSize: Int
        get() = snapshot.viewportSize

    /** Whether touch input or animated platform scrolling is active. */
    val isScrollInProgress: Boolean
        get() = snapshot.isScrollInProgress

    /** Whether content can scroll toward the logical start edge. */
    val canScrollBackward: Boolean
        get() = snapshot.canScrollBackward

    /** Whether content can scroll toward the logical end edge. */
    val canScrollForward: Boolean
        get() = snapshot.canScrollForward

    /** Whether the latest reported movement was toward the start edge. */
    val lastScrolledBackward: Boolean
        get() = snapshot.lastScrolledBackward

    /** Whether the latest reported movement was toward the end edge. */
    val lastScrolledForward: Boolean
        get() = snapshot.lastScrolledForward

    /**
     * Immediately moves to [value] and retains the requested offset while detached.
     *
     * The target is clamped when the current maximum is known. The local snapshot updates before
     * the renderer command; a newly attached renderer receives the retained target once.
     *
     * @param value non-negative target offset in renderer units
     * @throws IllegalArgumentException if [value] is negative
     */
    fun scrollTo(value: Int) {
        val requested = value.requireValidScrollValue()
        val target = snapshot.maxValue?.let { maximum -> requested.coerceAtMost(maximum) } ?: requested
        val movedBackward = target < snapshot.value
        val movedForward = target > snapshot.value
        updateSnapshot(
            snapshot.copy(
                value = target,
                isScrollInProgress = false,
                canScrollBackward = target > 0,
                canScrollForward = snapshot.maxValue?.let { target < it } ?: false,
                lastScrolledBackward = when {
                    movedBackward -> true
                    movedForward -> false
                    else -> snapshot.lastScrolledBackward
                },
                lastScrolledForward = when {
                    movedForward -> true
                    movedBackward -> false
                    else -> snapshot.lastScrolledForward
                },
            ),
        )
        connector?.scrollTo(target, animated = false)
    }

    /**
     * Requests an animated transition to [value] on the attached renderer.
     *
     * The request is a no-op while detached and does not predictively change [snapshot].
     *
     * @param value non-negative target offset in renderer units
     * @throws IllegalArgumentException if [value] is negative
     */
    fun animateScrollTo(value: Int) {
        connector?.scrollTo(value.requireValidScrollValue(), animated = true)
    }

    /**
     * Registers [listener] for distinct future snapshots without replaying the current value.
     *
     * @param listener callback invoked synchronously after a snapshot update
     */
    fun addOnSnapshotChangedListener(listener: (ScrollStateSnapshot) -> Unit) {
        listeners += listener
    }

    /**
     * Removes [listener] from future snapshot callbacks.
     *
     * @param listener previously registered callback; unknown callbacks are ignored
     */
    fun removeOnSnapshotChangedListener(listener: (ScrollStateSnapshot) -> Unit) {
        listeners -= listener
    }

    /**
     * Rebinds this state to [nextConnector] at the renderer boundary.
     *
     * The previous connector's latest snapshot is captured before its listener is cleared. A new
     * native identity receives the retained offset; a replacement wrapper with the same
     * [ScrollConnector.identity] does not reset it. Passing `null` detaches while retaining state.
     *
     * @param nextConnector renderer bridge, or `null` to detach
     */
    fun attach(nextConnector: ScrollConnector?) {
        if (connector === nextConnector) return

        val previousConnector = connector
        val samePlatformIdentity =
            previousConnector != null &&
                nextConnector != null &&
                connectorIdentity === nextConnector.identity

        previousConnector?.currentSnapshot()?.let(::updateSnapshot)
        previousConnector?.setOnSnapshotChangedListener(null)

        connector = nextConnector
        connectorIdentity = nextConnector?.identity
        if (nextConnector == null) return

        nextConnector.setOnSnapshotChangedListener(::updateSnapshot)
        if (!samePlatformIdentity) {
            nextConnector.scrollTo(snapshot.value, animated = false)
        }
        nextConnector.currentSnapshot()?.let(::updateSnapshot)
    }

    private fun updateSnapshot(next: ScrollStateSnapshot) {
        if (snapshotState.value == next) return
        snapshotState.value = next
        listeners.toList().forEach { listener -> listener(next) }
    }
}

/**
 * Bridges [ScrollState] commands and snapshots to one eager platform scroll container.
 *
 * This is a renderer implementation boundary. Methods run synchronously on the state owner's
 * renderer thread; optional snapshot methods permit a command-only custom renderer.
 */
interface ScrollConnector {
    /** Stable native-container identity across connector-wrapper replacement. */
    val identity: Any
        get() = this

    /**
     * Moves to [value] using the requested motion policy.
     *
     * @param value validated non-negative target whose upper bound is resolved by the renderer
     * @param animated whether the platform should animate toward the target
     */
    fun scrollTo(
        value: Int,
        animated: Boolean,
    )

    /**
     * Returns the latest platform snapshot, or `null` before layout establishes a viewport.
     *
     * @return immutable current snapshot or `null`
     */
    fun currentSnapshot(): ScrollStateSnapshot? = null

    /**
     * Replaces the callback used for future platform snapshot changes.
     *
     * @param listener callback for platform updates, or `null` to detach it
     */
    fun setOnSnapshotChangedListener(listener: ((ScrollStateSnapshot) -> Unit)?) = Unit
}

/**
 * Captures one eager scroll container's offset, range, viewport, and motion at an instant.
 *
 * @property value non-negative offset from the logical start edge
 * @property maxValue maximum reachable offset, or `null` before layout establishes the range
 * @property viewportSize non-negative main-axis viewport size in renderer units
 * @property isScrollInProgress whether touch or animated scrolling is active
 * @property canScrollBackward whether movement toward the logical start is available
 * @property canScrollForward whether movement toward the logical end is available
 * @property lastScrolledBackward whether the latest movement was toward the start
 * @property lastScrolledForward whether the latest movement was toward the end
 * @throws IllegalArgumentException for negative values, a value beyond a known maximum, or
 * contradictory direction flags
 */
data class ScrollStateSnapshot(
    val value: Int,
    val maxValue: Int?,
    val viewportSize: Int,
    val isScrollInProgress: Boolean,
    val canScrollBackward: Boolean,
    val canScrollForward: Boolean,
    val lastScrolledBackward: Boolean,
    val lastScrolledForward: Boolean,
) {
    init {
        value.requireValidScrollValue()
        maxValue?.let { maximum ->
            maximum.requireValidScrollValue()
            require(value <= maximum) { "Scroll value must not exceed maxValue." }
        }
        require(viewportSize >= 0) { "Scroll viewportSize must be non-negative." }
        require(canScrollBackward == (value > 0)) {
            "Scroll backward capability must match its logical position."
        }
        require(canScrollForward == (maxValue != null && value < maxValue)) {
            "Scroll forward capability must match its logical position."
        }
        require(!(lastScrolledBackward && lastScrolledForward)) {
            "A scroll snapshot cannot report both movement directions."
        }
    }

    /** Creates detached snapshots before a renderer establishes range information. */
    companion object {
        /**
         * Creates a non-scrolling detached snapshot retaining [value].
         *
         * @param value non-negative retained offset
         * @return immutable initial snapshot with an unknown maximum
         * @throws IllegalArgumentException if [value] is negative
         */
        fun initial(value: Int = 0): ScrollStateSnapshot {
            return ScrollStateSnapshot(
                value = value.requireValidScrollValue(),
                maxValue = null,
                viewportSize = 0,
                isScrollInProgress = false,
                canScrollBackward = value > 0,
                canScrollForward = false,
                lastScrolledBackward = false,
                lastScrolledForward = false,
            )
        }
    }
}

private fun Int.requireValidScrollValue(): Int {
    require(this >= 0) { "Scroll value must be non-negative." }
    return this
}
