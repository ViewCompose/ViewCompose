package com.viewcompose.ui.gesture

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

enum class NestedScrollSource {
    UserInput,
    Fling,
    SideEffect,
}

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
 * Platform connector for imperative nested-scroll dispatch.
 *
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
