package com.viewcompose.runtime.state

import com.viewcompose.runtime.SnapshotRuntime
import com.viewcompose.runtime.State
import com.viewcompose.runtime.observation.ObservableState
import com.viewcompose.runtime.observation.Observation
import com.viewcompose.runtime.observation.ObservationInvalidations
import com.viewcompose.runtime.observation.RuntimeObservation

/**
 * Derived state that caches its result and observes state dependencies read by [block].
 *
 * Dependency invalidation only marks the cached value dirty. Recalculation remains lazy and happens
 * on the next read.
 */
internal class DerivedStateImpl<T>(
    private val block: () -> T,
) : State<T>, ObservableState {
    private val observers = LinkedHashSet<Observation>()

    private var dependencyObservation: Observation? = null
    private var cachedValue: Any? = Uninitialized
    private var dirty: Boolean = true
    private var lastReadToken: Long = Long.MIN_VALUE

    override val value: T
        get() {
            RuntimeObservation.recordRead(this)
            val readToken = SnapshotRuntime.currentReadToken()
            if (dirty || lastReadToken != readToken) {
                recompute()
                lastReadToken = readToken
            }
            @Suppress("UNCHECKED_CAST")
            return cachedValue as T
        }

    override fun addObserver(observer: Observation) {
        if (observers.isEmpty()) dirty = true
        observers += observer
    }

    override fun removeObserver(observer: Observation) {
        observers -= observer
        if (observers.isEmpty()) {
            dependencyObservation?.dispose()
            dependencyObservation = null
        }
    }

    private fun recompute() {
        val previous = dependencyObservation
        val nextValue: T
        if (previous == null) {
            val (value, observation) = RuntimeObservation.observeReads(onInvalidated = ::invalidate, block = block)
            nextValue = value
            if (observers.isEmpty()) observation.dispose() else dependencyObservation = observation
        } else {
            val (value, replacement) = RuntimeObservation.prepareReplacement(previous, block)
            replacement.commit()
            nextValue = value
        }
        cachedValue = nextValue
        dirty = false
    }

    private fun invalidate() {
        dirty = true
        lastReadToken = Long.MIN_VALUE
        ObservationInvalidations.enqueue(observers.toList())
    }

    private object Uninitialized
}
