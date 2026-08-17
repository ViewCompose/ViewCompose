package com.viewcompose.runtime.observation

/** Internal subscription contract for state objects whose reads can be observed. */
internal interface ObservableState {
    fun addObserver(observer: Observation)
    fun removeObserver(observer: Observation)
}

/**
 * Owns the state subscriptions collected by one [RuntimeObservation.observeReads] call.
 *
 * Each state is subscribed at most once per observation. One successful global snapshot apply
 * invokes the observation callback at most once on the applying thread, even when that transaction
 * changes several observed states. Separate applies remain separate invalidation opportunities.
 * Call [dispose] when the consumer no longer needs invalidations so observed states can release the
 * subscription.
 */
class Observation internal constructor(
    private val onInvalidated: () -> Unit,
) {
    private val stateLock = Any()
    private val states = LinkedHashSet<ObservableState>()
    @Volatile
    private var disposed: Boolean = false
    private var replacementActive: Boolean = false

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
            replacementActive = false
            states.forEach { state ->
                state.removeObserver(this)
            }
            states.clear()
        }
    }

    internal fun beginReplacement(): Set<ObservableState> = synchronized(stateLock) {
        check(!disposed) { "Cannot replace dependencies on a disposed Observation." }
        check(!replacementActive) { "Observation already has a prepared dependency replacement." }
        replacementActive = true
        states.toHashSet()
    }

    internal fun addReplacementDependency(state: ObservableState) {
        synchronized(stateLock) {
            check(!disposed) { "Cannot replace dependencies on a disposed Observation." }
            check(replacementActive) { "Observation has no active dependency replacement." }
            if (states.add(state)) {
                state.addObserver(this)
            }
        }
    }

    internal fun commitReplacement(next: Set<ObservableState>) {
        synchronized(stateLock) {
            check(!disposed) { "Cannot replace dependencies on a disposed Observation." }
            check(replacementActive) { "Observation has no active dependency replacement." }
            next.forEach { state ->
                if (states.add(state)) {
                    state.addObserver(this)
                }
            }
            val iterator = states.iterator()
            while (iterator.hasNext()) {
                val state = iterator.next()
                if (state !in next) {
                    iterator.remove()
                    state.removeObserver(this)
                }
            }
            replacementActive = false
        }
    }

    internal fun abortReplacement(added: Set<ObservableState>) {
        synchronized(stateLock) {
            if (disposed || !replacementActive) return
            added.forEach { state ->
                if (states.remove(state)) {
                    state.removeObserver(this)
                }
            }
            replacementActive = false
        }
    }
}

/**
 * Candidate dependency replacement for a committed [Observation].
 *
 * Reads have already completed when this value is returned. [commit] atomically makes their
 * dependency set authoritative while retaining subscriptions shared with the previous set;
 * [abort] releases only candidate-only subscriptions and leaves the committed observation
 * unchanged. The original Observation owns temporary subscriptions too, preserving its
 * at-most-once callback identity when one apply changes old and candidate dependencies together.
 * Exactly one terminal method may be called.
 *
 * @sample com.viewcompose.runtime.samples.observationReplacementSample
 */
class PreparedObservationReplacement internal constructor(
    private val previous: Observation,
    private val collector: ReplacementReadObserver,
) {
    private var completed = false

    /** Commits the collected dependency set and retains the original Observation identity. */
    fun commit() {
        check(!completed) { "Observation replacement is already completed." }
        completed = true
        previous.commitReplacement(collector.dependencies)
    }

    /** Abandons candidate-only subscriptions without changing committed dependencies. */
    fun abort() {
        if (completed) return
        completed = true
        previous.abortReplacement(collector.addedDependencies)
    }
}

internal class ReplacementReadObserver(
    private val previous: Observation,
) {
    private val previousDependencies = previous.beginReplacement()
    val dependencies = LinkedHashSet<ObservableState>()
    val addedDependencies = LinkedHashSet<ObservableState>()

    fun record(state: ObservableState) {
        if (dependencies.add(state) && state !in previousDependencies) {
            previous.addReplacementDependency(state)
            addedDependencies += state
        }
    }

    fun abort() {
        previous.abortReplacement(addedDependencies)
    }
}

/** Collects snapshot-state reads and exposes their later invalidations. */
object RuntimeObservation {
    private val currentObservation = ThreadLocal<((ObservableState) -> Unit)?>()

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
        currentObservation.set(observation::record)
        return try {
            block() to observation
        } catch (error: Throwable) {
            observation.dispose()
            throw error
        } finally {
            currentObservation.set(previous)
        }
    }

    /**
     * Reads a candidate value while preparing a transactional dependency replacement.
     *
     * Dependencies already owned by [previous] are not unsubscribed or subscribed again. Newly
     * encountered dependencies subscribe the same [previous] Observation immediately so
     * invalidations racing the caller's external transaction are not lost or duplicated. Only one
     * replacement may be prepared for an Observation at a time. The caller must invoke exactly one
     * terminal method on the returned [PreparedObservationReplacement].
     *
     * @sample com.viewcompose.runtime.samples.observationReplacementSample
     * @param T value produced by [block]
     * @param previous currently committed observation whose callback remains authoritative
     * @param block synchronous, side-effect-free candidate read
     * @return candidate value and its explicit commit/abort dependency transaction
     */
    fun <T> prepareReplacement(
        previous: Observation,
        block: () -> T,
    ): Pair<T, PreparedObservationReplacement> {
        val collector = ReplacementReadObserver(previous)
        val prior = currentObservation.get()
        currentObservation.set(collector::record)
        return try {
            block() to PreparedObservationReplacement(previous, collector)
        } catch (error: Throwable) {
            collector.abort()
            throw error
        } finally {
            currentObservation.set(prior)
        }
    }

    internal fun recordRead(state: ObservableState) {
        currentObservation.get()?.invoke(state)
    }
}
