package com.viewcompose.ui.focus

/**
 * Selects sequential, spatial, enter, or exit traversal for a focus request.
 *
 * Left/right are physical directions. Sequential next/previous behavior and enter/exit traversal
 * are resolved by the platform focus manager.
 */
enum class FocusDirection {
    Next,
    Previous,
    Left,
    Right,
    Up,
    Down,
    Enter,
    Exit,
}

/**
 * Captures focus ownership for a target and its descendants at one instant.
 *
 * @property isFocused whether the target itself owns focus
 * @property hasFocus whether the target or any descendant owns focus
 */
data class FocusState(
    val isFocused: Boolean,
    val hasFocus: Boolean,
) {
    /** Provides common focus snapshots. */
    companion object {
        /** Snapshot for a target whose subtree does not own focus. */
        val Inactive = FocusState(
            isFocused = false,
            hasFocus = false,
        )
    }
}

/**
 * Exposes imperative focus operations owned by a render session.
 *
 * Calls execute synchronously on the caller's thread. An Android implementation requires the main
 * thread and resolves traversal through the currently mounted native view tree.
 */
interface FocusManager {
    /**
     * Clears focus from the currently focused target.
     *
     * @param force whether the platform may bypass normal focus-clear refusal behavior
     */
    fun clearFocus(force: Boolean = false)

    /**
     * Requests focus traversal in [direction].
     *
     * @param direction traversal direction resolved by the platform
     * @return `true` if another target accepted focus
     */
    fun moveFocus(direction: FocusDirection): Boolean
}

/**
 * Bridges a [FocusRequester] to one renderer-owned focus target.
 *
 * This is a renderer implementation boundary rather than an application extension point.
 * Properties are queried synchronously by the requester, and [requestFocus] executes on the
 * requester's calling thread.
 */
interface FocusRequesterConnector {
    /** Stable identity for the mounted platform focus target. */
    val identity: Any
        get() = this

    /**
     * Key used to match a target across keyed reuse or remount.
     *
     * The default uses [identity]. Renderers may provide a durable semantic key.
     */
    val restorationKey: Any
        get() = identity

    /** Latest focus snapshot for this target and its descendants. */
    val focusState: FocusState

    /**
     * Requests focus or traversal from this target.
     *
     * @param direction requested focus direction
     * @return `true` when the platform accepts the request
     */
    fun requestFocus(direction: FocusDirection): Boolean
}

/**
 * Holds a stable focus handle independently of any platform View instance.
 *
 * One requester may attach to multiple targets. Requests are offered in attachment order until a
 * target accepts. Connector membership and restoration state are synchronized; connector
 * callbacks run synchronously on the caller's thread without holding the membership lock, except
 * for focus-state inspection during [saveFocusedChild].
 *
 * @sample com.viewcompose.ui.samples.focusRequesterSample
 */
class FocusRequester {
    private val lock = Any()
    private val connectors = LinkedHashSet<FocusRequesterConnector>()
    private var savedRestorationKey: Any? = null
    private var restorePending: Boolean = false

    /**
     * Requests focus from attached targets in attachment order.
     *
     * @param direction direction offered to each target
     * @return `true` when one target accepts the request
     * @throws IllegalStateException if no target is attached
     */
    fun requestFocus(direction: FocusDirection = FocusDirection.Enter): Boolean {
        val targets = synchronized(lock) {
            check(connectors.isNotEmpty()) {
                "FocusRequester is not attached. Add Modifier.focusRequester(requester) " +
                    "to a mounted focus target before requesting focus."
            }
            connectors.toList()
        }
        return targets.any { connector -> connector.requestFocus(direction) }
    }

    /**
     * Saves the restoration key of the first attached target whose subtree owns focus.
     *
     * @return `true` when a focused target was captured; `false` when none has focus
     */
    fun saveFocusedChild(): Boolean {
        val focused = synchronized(lock) {
            connectors.firstOrNull { connector -> connector.focusState.hasFocus }
        } ?: return false
        synchronized(lock) {
            savedRestorationKey = focused.restorationKey
            restorePending = true
        }
        return true
    }

    /**
     * Restores the target previously captured by [saveFocusedChild].
     *
     * A missing or rejecting target leaves restoration pending. A later [attach] with the same
     * restoration key retries immediately.
     *
     * @return `true` when the matching target accepts focus; otherwise `false`
     */
    fun restoreFocusedChild(): Boolean {
        val target = synchronized(lock) {
            if (!restorePending) {
                return false
            }
            connectors.firstOrNull { connector ->
                connector.restorationKey == savedRestorationKey
            }
        } ?: return false
        val restored = target.requestFocus(FocusDirection.Enter)
        if (restored) {
            synchronized(lock) {
                restorePending = false
            }
        }
        return restored
    }

    /**
     * Attaches one platform focus target.
     *
     * Reattaching the same connector is idempotent because membership uses equality semantics. A
     * pending restoration with a matching key is attempted synchronously.
     *
     * @param connector renderer-owned focus target connector
     */
    fun attach(connector: FocusRequesterConnector) {
        val shouldRestore = synchronized(lock) {
            connectors += connector
            restorePending && connector.restorationKey == savedRestorationKey
        }
        if (shouldRestore && connector.requestFocus(FocusDirection.Enter)) {
            synchronized(lock) {
                restorePending = false
            }
        }
    }

    /**
     * Detaches [connector] from future requests.
     *
     * Saved restoration state is retained so a target with the same key may restore after remount.
     *
     * @param connector target connector to remove
     */
    fun detach(connector: FocusRequesterConnector) {
        synchronized(lock) {
            connectors -= connector
        }
    }
}

/**
 * Describes optional focus participation and directional traversal overrides for one node.
 *
 * A `null` field leaves the renderer or an earlier modifier value unchanged. [merge] applies a
 * later set of properties by replacing only its non-null fields.
 *
 * @property canFocus whether the target may receive focus, or `null` to keep the inherited policy
 * @property next explicit target for sequential next traversal
 * @property previous explicit target for sequential previous traversal
 * @property left explicit target for physical-left traversal
 * @property right explicit target for physical-right traversal
 * @property up explicit target for upward traversal
 * @property down explicit target for downward traversal
 */
data class FocusProperties(
    val canFocus: Boolean? = null,
    val next: FocusRequester? = null,
    val previous: FocusRequester? = null,
    val left: FocusRequester? = null,
    val right: FocusRequester? = null,
    val up: FocusRequester? = null,
    val down: FocusRequester? = null,
) {
    /**
     * Returns properties with non-null values from [nextProperties] applied over this value.
     *
     * @param nextProperties later modifier values with higher precedence
     * @return a new merged property set
     */
    fun merge(nextProperties: FocusProperties): FocusProperties {
        return FocusProperties(
            canFocus = nextProperties.canFocus ?: canFocus,
            next = nextProperties.next ?: next,
            previous = nextProperties.previous ?: previous,
            left = nextProperties.left ?: left,
            right = nextProperties.right ?: right,
            up = nextProperties.up ?: up,
            down = nextProperties.down ?: down,
        )
    }

    /**
     * Returns the explicit requester configured for [direction].
     *
     * [FocusDirection.Enter] and [FocusDirection.Exit] do not have fields in this contract and
     * therefore return `null`.
     *
     * @param direction traversal direction to resolve
     * @return the configured requester, or `null` when traversal remains platform-defined
     */
    fun requesterFor(direction: FocusDirection): FocusRequester? {
        return when (direction) {
            FocusDirection.Next -> next
            FocusDirection.Previous -> previous
            FocusDirection.Left -> left
            FocusDirection.Right -> right
            FocusDirection.Up -> up
            FocusDirection.Down -> down
            FocusDirection.Enter,
            FocusDirection.Exit,
            -> null
        }
    }

    /** Provides common focus-property values. */
    companion object {
        /** Property set that leaves every focus policy to the renderer or inherited modifiers. */
        val Default = FocusProperties()
    }
}

/**
 * Collects mutable assignments inside the `focusProperties` modifier DSL.
 *
 * The receiver is temporary and should not be retained. Each nullable property has the same
 * meaning as its counterpart in [FocusProperties]; leaving it `null` preserves prior policy.
 */
class FocusPropertiesReceiver {
    /** Whether the target may receive focus, or `null` to keep inherited policy. */
    var canFocus: Boolean? = null

    /** Explicit target for sequential next traversal, or `null` for platform resolution. */
    var next: FocusRequester? = null

    /** Explicit target for sequential previous traversal, or `null` for platform resolution. */
    var previous: FocusRequester? = null

    /** Explicit target for physical-left traversal, or `null` for platform resolution. */
    var left: FocusRequester? = null

    /** Explicit target for physical-right traversal, or `null` for platform resolution. */
    var right: FocusRequester? = null

    /** Explicit target for upward traversal, or `null` for platform resolution. */
    var up: FocusRequester? = null

    /** Explicit target for downward traversal, or `null` for platform resolution. */
    var down: FocusRequester? = null

    internal fun build(): FocusProperties {
        return FocusProperties(
            canFocus = canFocus,
            next = next,
            previous = previous,
            left = left,
            right = right,
            up = up,
            down = down,
        )
    }
}
