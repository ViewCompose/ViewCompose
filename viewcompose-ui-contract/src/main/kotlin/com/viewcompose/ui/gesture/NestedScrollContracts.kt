package com.viewcompose.ui.gesture

/**
 * 滚动距离增量，x/y 单位由平台 renderer 定义，Android 侧通常为像素。
 * Scroll distance delta; x/y units are defined by the platform renderer, usually pixels on Android.
 */
data class ScrollDelta(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: ScrollDelta): ScrollDelta {
        return ScrollDelta(
            x = x + other.x,
            y = y + other.y,
        )
    }

    operator fun minus(other: ScrollDelta): ScrollDelta {
        return ScrollDelta(
            x = x - other.x,
            y = y - other.y,
        )
    }

    val isZero: Boolean
        get() = x == 0f && y == 0f

    companion object {
        val Zero = ScrollDelta(0f, 0f)
    }
}

/**
 * 滚动速度，x/y 单位由平台 renderer 定义，Android 侧通常为像素每秒。
 * Scroll velocity; x/y units are defined by the platform renderer, usually pixels per second on Android.
 */
data class ScrollVelocity(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: ScrollVelocity): ScrollVelocity {
        return ScrollVelocity(
            x = x + other.x,
            y = y + other.y,
        )
    }

    operator fun minus(other: ScrollVelocity): ScrollVelocity {
        return ScrollVelocity(
            x = x - other.x,
            y = y - other.y,
        )
    }

    val isZero: Boolean
        get() = x == 0f && y == 0f

    companion object {
        val Zero = ScrollVelocity(0f, 0f)
    }
}

/**
 * 嵌套滚动来源，用于区分用户拖动、fling 和程序触发的滚动。
 * Nested-scroll source used to distinguish user drag, fling, and programmatic scrolls.
 */
enum class NestedScrollSource {
    UserInput,
    Fling,
    SideEffect,
}

/**
 * 嵌套滚动连接，允许父级在子级滚动前后消费距离或速度。
 * Nested-scroll connection that lets parents consume distance or velocity before/after child scrolling.
 */
interface NestedScrollConnection {
    fun onPreScroll(
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta = ScrollDelta.Zero

    fun onPostScroll(
        consumed: ScrollDelta,
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta = ScrollDelta.Zero

    fun onPreFling(available: ScrollVelocity): ScrollVelocity = ScrollVelocity.Zero

    fun onPostFling(
        consumed: ScrollVelocity,
        available: ScrollVelocity,
    ): ScrollVelocity = ScrollVelocity.Zero
}

/**
 * 命令式嵌套滚动分发的平台连接器。
 * Platform connector for imperative nested-scroll dispatch.
 *
 * 这是公开 renderer 边界。业务代码应使用 [NestedScrollDispatcher]，而不是直接实现该接口。
 * This is a public renderer boundary. Application code should use [NestedScrollDispatcher] rather
 * than implementing it.
 */
interface NestedScrollDispatcherConnector {
    val identity: Any
        get() = this

    fun dispatchPreScroll(
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta

    fun dispatchPostScroll(
        consumed: ScrollDelta,
        available: ScrollDelta,
        source: NestedScrollSource,
    ): ScrollDelta

    fun dispatchPreFling(available: ScrollVelocity): ScrollVelocity

    fun dispatchPostFling(
        consumed: ScrollVelocity,
        available: ScrollVelocity,
    ): ScrollVelocity
}

/**
 * 业务侧持有的嵌套滚动分发器，renderer attach 后将命令转发到平台滚动链。
 * App-side nested-scroll dispatcher; after renderer attachment it forwards commands into the platform scroll chain.
 */
class NestedScrollDispatcher {
    private val lock = Any()
    private var connector: NestedScrollDispatcherConnector? = null

    fun dispatchPreScroll(
        available: ScrollDelta,
        source: NestedScrollSource = NestedScrollSource.SideEffect,
    ): ScrollDelta {
        return currentConnector()?.dispatchPreScroll(available, source)
            ?: ScrollDelta.Zero
    }

    fun dispatchPostScroll(
        consumed: ScrollDelta,
        available: ScrollDelta,
        source: NestedScrollSource = NestedScrollSource.SideEffect,
    ): ScrollDelta {
        return currentConnector()?.dispatchPostScroll(consumed, available, source)
            ?: ScrollDelta.Zero
    }

    fun dispatchPreFling(available: ScrollVelocity): ScrollVelocity {
        return currentConnector()?.dispatchPreFling(available)
            ?: ScrollVelocity.Zero
    }

    fun dispatchPostFling(
        consumed: ScrollVelocity,
        available: ScrollVelocity,
    ): ScrollVelocity {
        return currentConnector()?.dispatchPostFling(consumed, available)
            ?: ScrollVelocity.Zero
    }

    fun attach(nextConnector: NestedScrollDispatcherConnector) {
        synchronized(lock) {
            connector = nextConnector
        }
    }

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
