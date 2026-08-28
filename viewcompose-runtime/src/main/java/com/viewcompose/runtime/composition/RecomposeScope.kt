package com.viewcompose.runtime.composition

import com.viewcompose.runtime.observation.Observation
import java.util.concurrent.atomic.AtomicLong

/**
 * Identifies one node in the lightweight composition scope tree.
 *
 * The type is public so renderer and widget integrations can associate opaque host data with a
 * scope. Construction, hierarchy mutation, invalidation, and slot storage remain owned by
 * [ComposerLite]. Instances are thread-confined to their owning composer.
 */
class RecomposeScope internal constructor(
    internal var signature: Any,
    internal val parent: RecomposeScope?,
    internal val saveablePath: String = parent?.saveablePath ?: "root",
    internal val sourceCallSites: List<CompositionSourceCallSite> = emptyList(),
) {
    internal val children: MutableList<RecomposeScope> = mutableListOf()
    internal val rememberSlots: MutableList<RememberSlot> = mutableListOf()
    internal var observation: Observation? = null
    internal var cachedResult: Any? = Unset
    @Volatile
    internal var dirty: Boolean = true
    @Volatile
    internal var composing: Boolean = false
    internal var composed: Boolean = false
    @Volatile
    internal var disposed: Boolean = false
    internal var localSnapshot: Any? = null
    internal var latestInputs: Any? = NoInputs
    internal var childCursor: Int = 0
    internal var rememberCursor: Int = 0
    internal var sideEffectCursor: Int = 0
    internal var saveableCursor: Int = 0
    private val invalidationVersion = AtomicLong(0L)
    private var timingNodeIdentity: CompositionTimingNodeIdentity? = null
    private var timingScopeActive: Boolean = false

    /**
     * Returns the timing identity exposed while this scope body is actively being measured.
     *
     * The result is non-null only inside a finite [ComposerLite.prepareRootWithTiming] scope body
     * accepted by its collector. Callers may copy [CompositionTimingNodeIdentity.value] into
     * non-semantic tooling metadata for downstream correlation, but must not persist it or use it as
     * application identity.
     *
     * @return active process-local timing identity, or `null` outside a measured scope body
     */
    fun timingNodeIdentityOrNull(): CompositionTimingNodeIdentity? =
        timingNodeIdentity.takeIf { timingScopeActive }

    internal fun ensureTimingNodeIdentity(): CompositionTimingNodeIdentity {
        timingNodeIdentity?.let { identity -> return identity }
        return CompositionTimingNodeIdentity(nextCompositionTimingNodeIdentity()).also { identity ->
            timingNodeIdentity = identity
        }
    }

    internal fun setTimingScopeActive(active: Boolean) {
        timingScopeActive = active
    }

    /** Starts a scope pass and resets each positional slot cursor. */
    internal fun beginCompose() {
        composing = true
        childCursor = 0
        rememberCursor = 0
        sideEffectCursor = 0
        saveableCursor = 0
    }

    /** Marks the current scope pass as complete. */
    internal fun endCompose() {
        composing = false
    }

    /** Removes child and remember slots not visited by the current pass. */
    internal fun trimAfterCompose() {
        while (children.size > childCursor) {
            children.removeAt(children.lastIndex)
        }
        while (rememberSlots.size > rememberCursor) {
            rememberSlots.removeAt(rememberSlots.lastIndex)
        }
    }

    /** Recursively disposes committed resources and invokes [RememberObserver.onForgotten]. */
    internal fun disposeRecursively() {
        if (disposed) return
        disposed = true
        val failures = mutableListOf<Throwable>()
        fun cleanup(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                failures += error
            }
        }
        observation?.let { currentObservation ->
            cleanup(currentObservation::dispose)
        }
        observation = null
        rememberSlots.forEach { slot ->
            cleanup(slot.lifecycle::leave)
        }
        rememberSlots.clear()
        children.forEach { child ->
            cleanup(child::disposeRecursively)
        }
        children.clear()
        cachedResult = Unset
        dirty = true
        composed = false
        localSnapshot = null
        latestInputs = NoInputs
        timingScopeActive = false
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    /** Recursively abandons uncommitted resources and invokes [RememberObserver.onAbandoned]. */
    internal fun abandonRecursively() {
        if (disposed) return
        disposed = true
        val failures = mutableListOf<Throwable>()
        fun cleanup(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                failures += error
            }
        }
        observation?.let { currentObservation ->
            cleanup(currentObservation::dispose)
        }
        observation = null
        rememberSlots.forEach { slot ->
            cleanup(slot.lifecycle::leave)
        }
        rememberSlots.clear()
        children.forEach { child ->
            cleanup(child::abandonRecursively)
        }
        children.clear()
        cachedResult = Unset
        dirty = true
        composed = false
        localSnapshot = null
        latestInputs = NoInputs
        timingScopeActive = false
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    /** Marks this scope dirty and reports whether it produced a new invalidation version. */
    internal fun markDirty(): Boolean {
        if (disposed) return false
        if (dirty && !composing) return false
        invalidationVersion.incrementAndGet()
        dirty = true
        return true
    }

    internal fun currentInvalidationVersion(): Long = invalidationVersion.get()

    internal fun restoreInvalidationVersion(version: Long) {
        invalidationVersion.set(version)
    }

    /** Clears the dirty marker only when composition observed the latest invalidation version. */
    internal fun clearDirtyIfUnchanged(version: Long) {
        if (invalidationVersion.get() != version) return
        dirty = false
        if (invalidationVersion.get() != version) {
            dirty = true
        }
    }

    /** Marks this scope and each ancestor dirty for structure-level fallback recomposition. */
    internal fun markDirtyWithAncestors(): Boolean {
        var current: RecomposeScope? = this
        var changed = false
        while (current != null) {
            changed = current.markDirty() || changed
            current = current.parent
        }
        return changed
    }

    /**
     * Returns the opaque composition-local snapshot most recently associated with this scope.
     *
     * The value is owned and interpreted by the host integration that supplied it. Runtime clients
     * MUST NOT cast or mutate it unless they also own the corresponding snapshot provider.
     *
     * @return the latest opaque snapshot, or `null` when none has been captured
     */
    fun localSnapshotOrNull(): Any? = localSnapshot

    /**
     * Replaces the opaque composition-local snapshot used by diagnostics and host bridging.
     *
     * Integrations call this while composing the owning scope. The value is retained until replaced,
     * rolled back, or the scope is disposed.
     *
     * @param snapshot opaque host-owned snapshot, or `null` to clear the current value
     */
    fun updateLocalSnapshot(snapshot: Any?) {
        localSnapshot = snapshot
    }

    internal data class RememberSlot(
        val keys: List<Any?>,
        val value: Any?,
        val lifecycle: RememberLifecycle,
    )

    /** Owns successful activation, retryable activation failure, and terminal cleanup. */
    internal class RememberLifecycle(
        private val observer: RememberObserver?,
        private val diagnostic: EffectDiagnostic,
        private val warningLogger: ((String) -> Unit)?,
        private val warningThresholdNanos: Long?,
        private val frameIdProvider: (() -> Long?)?,
    ) {
        private var state: State = State.Pending

        fun activate() {
            if (state != State.Pending) return
            observer?.let { currentObserver ->
                runSynchronousEffectOperation(
                    diagnostic = diagnostic,
                    operation = "remember",
                    warningLogger = warningLogger,
                    warningThresholdNanos = warningThresholdNanos,
                    frameIdProvider = frameIdProvider,
                    block = currentObserver::onRemembered,
                )
            }
            // Publish Active only after the callback completes. A throwing attempt remains Pending
            // so a later composition commit can retry it or structural removal can abandon it.
            state = State.Active
        }

        val isPending: Boolean
            get() = state == State.Pending

        val isTerminal: Boolean
            get() = state == State.Terminal

        fun leave() {
            when (state) {
                State.Pending -> {
                    state = State.Terminal
                    observer?.let { currentObserver ->
                        runSynchronousEffectOperation(
                            diagnostic = diagnostic,
                            operation = "abandon",
                            warningLogger = warningLogger,
                            warningThresholdNanos = warningThresholdNanos,
                            frameIdProvider = frameIdProvider,
                            block = currentObserver::onAbandoned,
                        )
                    }
                }

                State.Active -> {
                    state = State.Terminal
                    observer?.let { currentObserver ->
                        runSynchronousEffectOperation(
                            diagnostic = diagnostic,
                            operation = "forget",
                            warningLogger = warningLogger,
                            warningThresholdNanos = warningThresholdNanos,
                            frameIdProvider = frameIdProvider,
                            block = currentObserver::onForgotten,
                        )
                    }
                }

                State.Terminal -> Unit
            }
        }

        private enum class State {
            Pending,
            Active,
            Terminal,
        }
    }

    internal data class Checkpoint(
        val signature: Any,
        val children: List<RecomposeScope>,
        val rememberSlots: List<RememberSlot>,
        val observation: Observation?,
        val cachedResult: Any?,
        val dirty: Boolean,
        val composed: Boolean,
        val disposed: Boolean,
        val localSnapshot: Any?,
        val latestInputs: Any?,
        val childCursor: Int,
        val rememberCursor: Int,
        val sideEffectCursor: Int,
        val saveableCursor: Int,
        val invalidationVersion: Long,
    )

    /** Captures mutable scope state so a prepared composition can roll back transactionally. */
    internal fun checkpoint(): Checkpoint = Checkpoint(
        signature = signature,
        children = children.toList(),
        rememberSlots = rememberSlots.toList(),
        observation = observation,
        cachedResult = cachedResult,
        dirty = dirty,
        composed = composed,
        disposed = disposed,
        localSnapshot = localSnapshot,
        latestInputs = latestInputs,
        childCursor = childCursor,
        rememberCursor = rememberCursor,
        sideEffectCursor = sideEffectCursor,
        saveableCursor = saveableCursor,
        invalidationVersion = currentInvalidationVersion(),
    )

    /** Restores mutable scope state from a prepared-composition checkpoint. */
    internal fun restore(checkpoint: Checkpoint) {
        signature = checkpoint.signature
        children.clear()
        children += checkpoint.children
        rememberSlots.clear()
        rememberSlots += checkpoint.rememberSlots
        observation = checkpoint.observation
        cachedResult = checkpoint.cachedResult
        dirty = checkpoint.dirty
        composed = checkpoint.composed
        disposed = checkpoint.disposed
        localSnapshot = checkpoint.localSnapshot
        latestInputs = checkpoint.latestInputs
        childCursor = checkpoint.childCursor
        rememberCursor = checkpoint.rememberCursor
        sideEffectCursor = checkpoint.sideEffectCursor
        saveableCursor = checkpoint.saveableCursor
        restoreInvalidationVersion(checkpoint.invalidationVersion)
    }

    internal object Unset

    internal object NoInputs
}

private fun nextCompositionTimingNodeIdentity(): Long {
    while (true) {
        val candidate = compositionTimingNodeIdentities.getAndIncrement()
        if (candidate != 0L) return candidate
    }
}

private val compositionTimingNodeIdentities = AtomicLong(1L)
