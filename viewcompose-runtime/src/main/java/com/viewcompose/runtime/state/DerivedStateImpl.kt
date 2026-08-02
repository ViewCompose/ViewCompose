package com.viewcompose.runtime.state

import com.viewcompose.runtime.SnapshotRuntime
import com.viewcompose.runtime.State
import com.viewcompose.runtime.observation.ObservableState
import com.viewcompose.runtime.observation.Observation
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
        observers += observer
    }

    override fun removeObserver(observer: Observation) {
        observers -= observer
    }

    private fun recompute() {
        dependencyObservation?.dispose()
        val (nextValue, nextObservation) = RuntimeObservation.observeReads(
            onInvalidated = ::invalidate,
        ) {
            block()
        }
        dependencyObservation = nextObservation
        cachedValue = nextValue
        dirty = false
    }

    private fun invalidate() {
        if (dirty) {
            return
        }
        dirty = true
        lastReadToken = Long.MIN_VALUE
        observers.toList().forEach { observer ->
            observer.invalidate()
        }
    }

    private object Uninitialized
}
