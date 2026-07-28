package com.viewcompose.ui.focus

/**
 * 平台无关焦点移动方向。
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

/**
 * 焦点状态快照，isFocused 表示自身聚焦，hasFocus 表示自身或子节点持有焦点。
 * Focus-state snapshot; isFocused means the target itself is focused, while hasFocus includes descendants.
 */
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
 * render session 暴露的命令式焦点管理入口。
 * Imperative focus owner exposed by a render session.
 */
interface FocusManager {
    /**
     * 清除当前焦点，force 由平台决定是否绕过常规拒绝逻辑。
     * Clears current focus; force lets the platform decide whether to bypass normal refusal rules.
     */
    fun clearFocus(force: Boolean = false)

    /**
     * 按方向移动焦点，返回是否成功。
     * Moves focus in a direction and returns whether it succeeded.
     */
    fun moveFocus(direction: FocusDirection): Boolean
}

/**
 * [FocusRequester] 使用的平台连接器。
 * Platform connector used by [FocusRequester].
 *
 * 这是公开 renderer 边界，与 lazy 容器的 state connector 契约一致。业务代码不应直接实现。
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
 * 稳定的焦点句柄，可独立于平台 View 被 remember。
 * Stable focus handle that can be remembered independently of a platform View.
 *
 * 一个 requester 可连接多个目标；请求按连接顺序尝试，直到某个目标接受。
 * One requester may be attached to more than one target. Requests are offered in attachment
 * order until a target accepts them.
 */
class FocusRequester {
    private val lock = Any()
    private val connectors = LinkedHashSet<FocusRequesterConnector>()
    private var savedRestorationKey: Any? = null
    private var restorePending: Boolean = false

    /**
     * 请求已连接目标获取焦点，按 attach 顺序尝试。
     * Requests focus from attached targets in attach order.
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
     * 保存当前持有焦点的目标，以便 keyed reuse 或重新挂载后恢复。
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
     * 恢复之前由 [saveFocusedChild] 捕获的目标。
     * Restores the target previously captured by [saveFocusedChild].
     *
     * 如果目标临时 detach，恢复请求会保持 pending，并在相同 restoration key 的 connector 再次 attach 时重试。
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

    /**
     * 连接一个平台焦点目标；如有 pending restore，会在 key 匹配时立即尝试恢复。
     * Attaches one platform focus target; pending restore is attempted immediately when the key matches.
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
     * 断开一个平台焦点目标。
     * Detaches one platform focus target.
     */
    fun detach(connector: FocusRequesterConnector) {
        synchronized(lock) {
            connectors -= connector
        }
    }
}

/**
 * 声明式焦点属性，后声明的非空字段会覆盖先前字段。
 * Declarative focus properties where later non-null fields override earlier fields.
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

/**
 * focusProperties DSL 的临时接收器。
 * Temporary receiver used by the focusProperties DSL.
 */
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
