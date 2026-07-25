package com.viewcompose.runtime.observation

internal interface ObservableState {
    fun addObserver(observer: Observation)
    fun removeObserver(observer: Observation)
}

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

object RuntimeObservation {
    private val currentObservation = ThreadLocal<Observation?>()

    fun <T> observeReads(
        onInvalidated: () -> Unit,
        block: () -> T,
    ): Pair<T, Observation> {
        val observation = Observation(onInvalidated)
        val previous = currentObservation.get()
        currentObservation.set(observation)
        return try {
            block() to observation
        } finally {
            currentObservation.set(previous)
        }
    }

    internal fun recordRead(state: ObservableState) {
        currentObservation.get()?.record(state)
    }
}
