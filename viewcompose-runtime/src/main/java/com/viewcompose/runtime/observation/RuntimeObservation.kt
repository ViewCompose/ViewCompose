package com.viewcompose.runtime.observation

/** Internal subscription contract for state objects whose reads can be observed. */
internal interface ObservableState {
    fun addObserver(observer: Observation)
    fun removeObserver(observer: Observation)
}

/**
 * Owns the state subscriptions collected by one [RuntimeObservation.observeReads] call.
 *
 * Each state is subscribed at most once per observation. A successful state commit invokes the
 * observation callback on the applying thread once for each changed observed state, so one
 * multi-state transaction may invoke it more than once. Call [dispose] when the consumer no longer
 * needs invalidations so observed states can release the subscription.
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
     * Detaches this observation from every state read during collection.
     *
     * Disposal is idempotent and safe to call concurrently with subscription changes. It prevents
     * future invalidations but does not cancel a callback that has already begun racing with
     * disposal.
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

/** Collects snapshot-state reads and exposes their later invalidations. */
object RuntimeObservation {
    private val currentObservation = ThreadLocal<Observation?>()

    /**
     * Runs [block] and collects every observable state read on the current thread.
     *
     * Nested calls temporarily replace the outer observation and restore it before returning. When
     * [block] throws, the partial observation is disposed and the same exception is rethrown. The
     * caller owns the returned [Observation] and MUST dispose it.
     *
     * @sample com.viewcompose.runtime.samples.runtimeObservationSample
     * @param T type of value returned by [block]
     * @param onInvalidated callback invoked on the thread that commits a dependency change
     * @param block synchronous calculation whose state reads are collected
     * @return the calculation result and its active observation
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
