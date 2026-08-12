package com.viewcompose.runtime

import com.viewcompose.runtime.observation.Observation
import com.viewcompose.runtime.observation.RuntimeObservation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Creates a cold [Flow] that reruns [block] when snapshot state read by it changes.
 *
 * Each collector evaluates [block] once in a pinned read snapshot, emits that initial result, and
 * subscribes to every observable state read during the evaluation. Dependency invalidations are
 * conflated; the collector reruns the block and emits only results that differ from the previous
 * emitted value by structural equality. Conditional reads replace the dependency set on every
 * evaluation.
 *
 * [block] may run more often than values are emitted and must be idempotent and free of side
 * effects. Collection and evaluation use the collector coroutine context. State writes may notify
 * from another thread; they only enqueue a conflated invalidation and never run [block] on the
 * writing thread. Cancellation disposes the active state observation before collection completes.
 * An exception from [block] terminates only that collector after releasing its observation.
 *
 * @sample com.viewcompose.runtime.samples.snapshotFlowSample
 * @param T type of value calculated and emitted
 * @param block synchronous snapshot-state calculation
 * @return a cold flow with an independent dependency observation per collector
 */
fun <T> snapshotFlow(block: () -> T): Flow<T> = flow {
    val invalidations = Channel<Unit>(capacity = Channel.CONFLATED)
    var observation: Observation? = null
    var initialized = false
    var lastValue: Any? = Unset
    invalidations.trySend(Unit)

    try {
        while (true) {
            invalidations.receive()

            var nextValue: T
            while (true) {
                observation?.dispose()
                observation = null
                val snapshot = Snapshot.takeSnapshot()
                val readId = snapshot.readId
                val observed = try {
                    snapshot.enter {
                        RuntimeObservation.observeReads(
                            onInvalidated = {
                                invalidations.trySend(Unit)
                                Unit
                            },
                            block = block,
                        )
                    }
                } finally {
                    snapshot.dispose()
                }
                observation = observed.second

                // A global commit between snapshot capture and subscription could otherwise leave
                // this collector parked on an already-stale value. Retry under the latest version;
                // normal dependency invalidations cover commits after this check.
                if (Snapshot.currentGlobalId() == readId) {
                    nextValue = observed.first
                    break
                }
                invalidations.tryReceive()
            }

            if (!initialized || lastValue != nextValue) {
                initialized = true
                lastValue = nextValue
                emit(nextValue)
            }
        }
    } finally {
        observation?.dispose()
        invalidations.cancel()
    }
}

private object Unset
