package com.viewcompose.ui.focus

/**
 * Platform-independent focus movement directions.
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

data class FocusState(
    val isFocused: Boolean,
    val hasFocus: Boolean,
) {
    companion object {
        val Inactive = FocusState(
            isFocused = false,
            hasFocus = false,
        )
    }
}

/**
 * Imperative focus owner exposed by a render session.
 */
interface FocusManager {
    fun clearFocus(force: Boolean = false)

    fun moveFocus(direction: FocusDirection): Boolean
}

/**
 * Platform connector used by [FocusRequester].
 *
 * This is a public renderer boundary, matching the state connector contracts used by lazy
 * containers. Application code should not implement it.
 */
interface FocusRequesterConnector {
    val identity: Any
        get() = this

    val restorationKey: Any
        get() = identity

    val focusState: FocusState

    fun requestFocus(direction: FocusDirection): Boolean
}

/**
 * Stable focus handle that can be remembered independently of a platform View.
 *
 * One requester may be attached to more than one target. Requests are offered in attachment
 * order until a target accepts them.
 */
class FocusRequester {
    private val lock = Any()
    private val connectors = LinkedHashSet<FocusRequesterConnector>()
    private var savedRestorationKey: Any? = null
    private var restorePending: Boolean = false

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
     * Saves the currently focused target so it can be restored after keyed reuse or remount.
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
     * If the target is temporarily detached, the restore remains pending and is attempted when
     * a connector carrying the same restoration key is attached again.
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

    fun detach(connector: FocusRequesterConnector) {
        synchronized(lock) {
            connectors -= connector
        }
    }
}

data class FocusProperties(
    val canFocus: Boolean? = null,
    val next: FocusRequester? = null,
    val previous: FocusRequester? = null,
    val left: FocusRequester? = null,
    val right: FocusRequester? = null,
    val up: FocusRequester? = null,
    val down: FocusRequester? = null,
) {
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

    companion object {
        val Default = FocusProperties()
    }
}

class FocusPropertiesReceiver {
    var canFocus: Boolean? = null
    var next: FocusRequester? = null
    var previous: FocusRequester? = null
    var left: FocusRequester? = null
    var right: FocusRequester? = null
    var up: FocusRequester? = null
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
