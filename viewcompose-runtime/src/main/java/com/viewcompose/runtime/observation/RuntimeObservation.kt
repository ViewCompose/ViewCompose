package com.viewcompose.runtime.observation

/**
 * 可被运行时观察读取的状态对象。
 * State object whose reads can be observed by the runtime.
 */
internal interface ObservableState {
    fun addObserver(observer: Observation)
    fun removeObserver(observer: Observation)
}

/**
 * 一次读取观察会话，保存被读取的状态集合并在任一状态变化时回调失效。
 * One read-observation session that stores read states and calls back when any of them invalidates.
 */
class Observation internal constructor(
    private val onInvalidated: () -> Unit,
) {
    private val stateLock = Any()
    private val states = LinkedHashSet<ObservableState>()
    @Volatile
    private var disposed: Boolean = false

    internal fun record(state: ObservableState) {
        synchronized(stateLock) {
            if (!disposed && states.add(state)) {
                state.addObserver(this)
            }
        }
    }

    internal fun invalidate() {
        if (!disposed) {
            onInvalidated()
        }
    }

    /**
     * 停止观察并从所有已记录状态上注销。
     * Stops observing and unregisters from every recorded state.
     */
    fun dispose() {
        synchronized(stateLock) {
            if (disposed) return
            disposed = true
            states.forEach { state ->
                state.removeObserver(this)
            }
            states.clear()
        }
    }
}

/**
 * 读取观察入口，使用线程局部变量把状态读取归属到当前观察会话。
 * Entry point for read observation, using thread-local state to attach reads to the active observation.
 */
object RuntimeObservation {
    private val currentObservation = ThreadLocal<Observation?>()

    /**
     * 执行 block 并收集其中读取到的可观察状态。
     * Runs block and collects observable states read inside it.
     */
    fun <T> observeReads(
        onInvalidated: () -> Unit,
        block: () -> T,
    ): Pair<T, Observation> {
        val observation = Observation(onInvalidated)
        val previous = currentObservation.get()
        currentObservation.set(observation)
        return try {
            block() to observation
        } catch (error: Throwable) {
            observation.dispose()
            throw error
        } finally {
            currentObservation.set(previous)
        }
    }

    internal fun recordRead(state: ObservableState) {
        currentObservation.get()?.record(state)
    }
}
