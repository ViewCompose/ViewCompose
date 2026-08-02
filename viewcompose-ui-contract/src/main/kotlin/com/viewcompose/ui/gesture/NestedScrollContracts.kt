package com.viewcompose.ui.gesture

/**
 * Stores a platform-neutral two-dimensional scroll distance.
 *
 * Units and sign follow the renderer boundary; Android renderers normally use physical pixels in
 * local coordinates. Arithmetic is component-wise and does not clamp finite values.
 *
 * @property x horizontal scroll distance
 * @property y vertical scroll distance
 */
data class ScrollDelta(
    val x: Float,
    val y: Float,
) {
    /**
     * Adds [other] component-wise.
     *
     * @param other distance to add
     * @return a new combined distance
     */
    operator fun plus(other: ScrollDelta): ScrollDelta {
        return ScrollDelta(
            x = x + other.x,
            y = y + other.y,
        )
    }

    /**
     * Subtracts [other] component-wise.
     *
     * @param other distance to subtract
     * @return a new difference
     */
    operator fun minus(other: ScrollDelta): ScrollDelta {
        return ScrollDelta(
            x = x - other.x,
            y = y - other.y,
        )
    }

    /** Whether both components are exactly `0f`. */
    val isZero: Boolean
        get() = x == 0f && y == 0f

    /** Provides common scroll-distance values. */
    companion object {
        /** Distance with zero horizontal and vertical components. */
        val Zero = ScrollDelta(0f, 0f)
    }
}

/**
 * Stores a platform-neutral two-dimensional scroll velocity.
 *
 * Units and sign follow the renderer boundary; Android renderers normally use physical pixels per
 * second in local coordinates. Arithmetic is component-wise and does not clamp finite values.
 *
 * @property x horizontal velocity
 * @property y vertical velocity
 */
data class ScrollVelocity(
    val x: Float,
    val y: Float,
) {
    /**
     * Adds [other] component-wise.
     *
     * @param other velocity to add
     * @return a new combined velocity
     */
    operator fun plus(other: ScrollVelocity): ScrollVelocity {
        return ScrollVelocity(
            x = x + other.x,
            y = y + other.y,
        )
    }

    /**
     * Subtracts [other] component-wise.
     *
     * @param other velocity to subtract
     * @return a new difference
     */
    operator fun minus(other: ScrollVelocity): ScrollVelocity {
        return ScrollVelocity(
            x = x - other.x,
            y = y - other.y,
        )
    }

    /** Whether both components are exactly `0f`. */
    val isZero: Boolean
        get() = x == 0f && y == 0f

    /** Provides common scroll-velocity values. */
    companion object {
        /** Velocity with zero horizontal and vertical components. */
        val Zero = ScrollVelocity(0f, 0f)
    }
}

/** Distinguishes direct user scrolling, inertial fling, and programmatic side effects. */
enum class NestedScrollSource {
    UserInput,
    Fling,
    SideEffect,
}

/**
 * Lets an ancestor consume scroll distance or velocity before and after a nested child.
 *
 * Callbacks execute synchronously on the dispatching thread in pre-child or post-child order.
 * Default implementations consume nothing. Implementations should return only the portion they
 * consumed; the contract does not coerce over-consumption.
 */
interface NestedScrollConnection {
    /**
     * Consumes part of [available] before the child scrolls.
     *
     * @param available distance offered by the child
     * @param source origin of the scroll operation
     * @return distance consumed by this connection, or [ScrollDelta.Zero]
     */
    fun onPreScroll(
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta = ScrollDelta.Zero

    /**
     * Consumes remaining distance after the child scrolls.
     *
     * @param consumed distance already consumed by the child
     * @param available distance still available to ancestors
     * @param source origin of the scroll operation
     * @return additional distance consumed by this connection
     */
    fun onPostScroll(
        consumed: ScrollDelta,
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta = ScrollDelta.Zero

    /**
     * Consumes part of [available] before the child starts a fling.
     *
     * @param available velocity offered by the child
     * @return velocity consumed by this connection
     */
    fun onPreFling(available: ScrollVelocity): ScrollVelocity = ScrollVelocity.Zero

    /**
     * Consumes remaining velocity after the child finishes its fling dispatch.
     *
     * @param consumed velocity already consumed by the child
     * @param available velocity still available to ancestors
     * @return additional velocity consumed by this connection
     */
    fun onPostFling(
        consumed: ScrollVelocity,
        available: ScrollVelocity,
    ): ScrollVelocity = ScrollVelocity.Zero
}

/**
 * Bridges [NestedScrollDispatcher] commands to one renderer-owned nested-scroll chain.
 *
 * This is a renderer implementation boundary rather than an application extension point.
 * Implementations execute callbacks synchronously and must preserve the pre/post ordering defined
 * by [NestedScrollConnection].
 */
interface NestedScrollDispatcherConnector {
    /**
     * Stable identity of the mounted platform chain.
     *
     * The default is connector object identity. Renderers may override it when connector wrappers
     * are recreated for the same native chain.
     */
    val identity: Any
        get() = this

    /**
     * Dispatches a pre-scroll offer to the mounted ancestor chain.
     *
     * @param available distance available before child consumption
     * @param source origin of the scroll
     * @return distance consumed by ancestors
     */
    fun dispatchPreScroll(
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta

    /**
     * Dispatches a post-scroll offer to the mounted ancestor chain.
     *
     * @param consumed distance consumed by the child
     * @param available distance remaining after child consumption
     * @param source origin of the scroll
     * @return additional distance consumed by ancestors
     */
    fun dispatchPostScroll(
        consumed: ScrollDelta,
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta

    /**
     * Dispatches velocity before the child starts a fling.
     *
     * @param available velocity offered by the child
     * @return velocity consumed by ancestors
     */
    fun dispatchPreFling(available: ScrollVelocity): ScrollVelocity

    /**
     * Dispatches remaining velocity after the child's fling phase.
     *
     * @param consumed velocity consumed by the child
     * @param available velocity remaining after child consumption
     * @return additional velocity consumed by ancestors
     */
    fun dispatchPostFling(
        consumed: ScrollVelocity,
        available: ScrollVelocity,
    ): ScrollVelocity
}

/**
 * Holds an application-facing handle for imperative nested-scroll dispatch.
 *
 * Before a renderer calls [attach], every dispatch consumes zero. Attachment state is synchronized,
 * and dispatch uses the connector current at call time without holding the dispatcher lock during
 * callbacks. The callback itself runs synchronously on the caller's thread; platform renderers may
 * impose a stricter UI-thread requirement.
 *
 * @sample com.viewcompose.ui.samples.nestedScrollDispatcherSample
 */
class NestedScrollDispatcher {
    private val lock = Any()
    private var connector: NestedScrollDispatcherConnector? = null

    /**
     * Offers [available] to ancestors before child scrolling.
     *
     * @param available distance available for consumption
     * @param source origin of the scroll; programmatic dispatch defaults to [NestedScrollSource.SideEffect]
     * @return distance consumed by the attached chain, or [ScrollDelta.Zero] while detached
     */
    fun dispatchPreScroll(
        available: ScrollDelta,
        source: NestedScrollSource = NestedScrollSource.SideEffect,
    ): ScrollDelta {
        return currentConnector()?.dispatchPreScroll(available, source)
            ?: ScrollDelta.Zero
    }

    /**
     * Offers remaining distance after child scrolling.
     *
     * @param consumed distance already consumed by the child
     * @param available distance remaining after child consumption
     * @param source origin of the scroll; programmatic dispatch defaults to [NestedScrollSource.SideEffect]
     * @return additional distance consumed by the attached chain, or [ScrollDelta.Zero] while detached
     */
    fun dispatchPostScroll(
        consumed: ScrollDelta,
        available: ScrollDelta,
        source: NestedScrollSource = NestedScrollSource.SideEffect,
    ): ScrollDelta {
        return currentConnector()?.dispatchPostScroll(consumed, available, source)
            ?: ScrollDelta.Zero
    }

    /**
     * Offers [available] velocity before the child starts a fling.
     *
     * @param available velocity offered to ancestors
     * @return velocity consumed by the attached chain, or [ScrollVelocity.Zero] while detached
     */
    fun dispatchPreFling(available: ScrollVelocity): ScrollVelocity {
        return currentConnector()?.dispatchPreFling(available)
            ?: ScrollVelocity.Zero
    }

    /**
     * Offers velocity after the child's fling phase.
     *
     * @param consumed velocity already consumed by the child
     * @param available velocity remaining after child consumption
     * @return additional velocity consumed by the attached chain, or [ScrollVelocity.Zero] while detached
     */
    fun dispatchPostFling(
        consumed: ScrollVelocity,
        available: ScrollVelocity,
    ): ScrollVelocity {
        return currentConnector()?.dispatchPostFling(consumed, available)
            ?: ScrollVelocity.Zero
    }

    /**
     * Attaches [nextConnector] as the current platform chain, replacing any previous connector.
     *
     * @param nextConnector renderer-owned connector that receives subsequent dispatches
     */
    fun attach(nextConnector: NestedScrollDispatcherConnector) {
        synchronized(lock) {
            connector = nextConnector
        }
    }

    /**
     * Detaches [detachedConnector] only if it is still the current connector.
     *
     * Identity comparison prevents disposal of an obsolete renderer binding from detaching a
     * newer one.
     *
     * @param detachedConnector connector whose renderer binding is being disposed
     */
    fun detach(detachedConnector: NestedScrollDispatcherConnector) {
        synchronized(lock) {
            if (connector === detachedConnector) {
                connector = null
            }
        }
    }

    private fun currentConnector(): NestedScrollDispatcherConnector? {
        return synchronized(lock) { connector }
    }
}
