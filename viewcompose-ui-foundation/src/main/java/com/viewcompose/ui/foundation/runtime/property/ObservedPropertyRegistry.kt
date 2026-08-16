package com.viewcompose.ui.foundation

import com.viewcompose.runtime.Snapshot
import com.viewcompose.runtime.composition.RecomposeScope
import com.viewcompose.runtime.observation.Observation
import com.viewcompose.runtime.observation.PreparedObservationReplacement
import com.viewcompose.runtime.observation.RuntimeObservation
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.NodeSpec
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Installs the candidate registry used by observed DSL emission on the composition thread. */
internal object ObservedPropertyContext {
    private val current = ThreadLocal<ObservedPropertyFullAttempt?>()

    fun <T> withAttempt(
        attempt: ObservedPropertyFullAttempt,
        block: () -> T,
    ): T {
        val previous = current.get()
        current.set(attempt)
        return try {
            block()
        } finally {
            current.set(previous)
        }
    }

    fun resolve(
        scope: RecomposeScope?,
        source: ObservedNodeSpec<out NodeSpec>,
        localSnapshot: LocalSnapshot,
    ): ObservedPropertyResolution {
        val attempt = current.get()
        if (attempt != null && scope != null) {
            return attempt.resolve(scope, source, localSnapshot)
        }
        val (spec, observation) = RuntimeObservation.observeReads(onInvalidated = {}) {
            LocalContext.withSnapshot(localSnapshot) {
                source.read()
            }
        }
        observation.dispose()
        return ObservedPropertyResolution(
            id = null,
            spec = spec,
        )
    }
}

internal data class ObservedPropertyResolution(
    val id: Long?,
    val spec: NodeSpec,
)

/** Session-owned committed and candidate observations for directly patchable node properties. */
internal class ObservedPropertyRegistry(
    private val onInvalidated: () -> Unit,
) {
    private val ownerThread = Thread.currentThread()
    private val nextId = AtomicLong(0L)
    private val bindingsById = LinkedHashMap<Long, CommittedObservedProperty>()
    private val idsByScope = IdentityHashMap<RecomposeScope, Long>()
    private val requestScheduled = AtomicBoolean(false)
    private var activeAttempt: ObservedPropertyFullAttempt? = null
    @Volatile
    private var disposed = false

    fun beginFullAttempt(): ObservedPropertyFullAttempt {
        requireOwnerThread("beginFullAttempt")
        check(!disposed) { "ObservedPropertyRegistry is disposed." }
        check(activeAttempt == null) { "An observed-property attempt is already active." }
        consumeScheduledRequest()
        return ObservedPropertyFullAttempt(this).also { attempt ->
            activeAttempt = attempt
        }
    }

    fun hasDirtyBindings(): Boolean {
        requireOwnerThread("hasDirtyBindings")
        return bindingsById.values.any { binding -> binding.signal.dirty.get() }
    }

    fun prepareDirty(): PreparedObservedPropertyTransaction? {
        requireOwnerThread("prepareDirty")
        check(!disposed) { "ObservedPropertyRegistry is disposed." }
        check(activeAttempt == null) { "A full observed-property attempt is active." }
        consumeScheduledRequest()
        val dirty = bindingsById.values.filter { binding -> binding.signal.dirty.get() }
        if (dirty.isEmpty()) return null
        val candidates = mutableListOf<ObservedPropertyCandidate>()
        val snapshot = Snapshot.takeSnapshot()
        return try {
            snapshot.enter {
                dirty.forEach { binding ->
                    candidates += binding.prepareCandidate(
                        inputs = binding.inputs,
                        localSnapshot = binding.localSnapshot,
                        reader = binding.reader,
                    )
                }
            }
            PreparedObservedPropertyTransaction(
                registry = this,
                candidates = candidates,
            )
        } catch (error: Throwable) {
            candidates.forEach(ObservedPropertyCandidate::abortObservation)
            throw error
        } finally {
            snapshot.dispose()
        }
    }

    fun dispose() {
        requireOwnerThread("dispose")
        if (disposed) return
        disposed = true
        activeAttempt?.abort()
        activeAttempt = null
        bindingsById.values.forEach { binding ->
            binding.signal.active.set(false)
            binding.observation.dispose()
        }
        bindingsById.clear()
        idsByScope.clear()
    }

    internal fun resolve(
        attempt: ObservedPropertyFullAttempt,
        scope: RecomposeScope,
        source: ObservedNodeSpec<out NodeSpec>,
        localSnapshot: LocalSnapshot,
    ): ObservedPropertyResolution {
        requireActive(attempt)
        val id = idsByScope[scope]
            ?: attempt.newIdsByScope[scope]
            ?: nextId.incrementAndGet().also { next -> attempt.newIdsByScope[scope] = next }
        attempt.touchedIds += id
        val committed = bindingsById[id]
        if (
            committed != null &&
            !committed.signal.dirty.get() &&
            committed.inputs == source.inputs &&
            committed.localSnapshot == localSnapshot
        ) {
            return ObservedPropertyResolution(id = id, spec = committed.spec)
        }
        val signal = committed?.signal ?: ObservedPropertySignal(::scheduleRequest)
        val candidate = prepareCandidate(
            id = id,
            scope = scope,
            signal = signal,
            inputs = source.inputs,
            localSnapshot = localSnapshot,
            reader = source.read,
            previousObservation = committed?.observation,
        )
        attempt.replaceCandidate(candidate)
        return ObservedPropertyResolution(id = id, spec = candidate.spec)
    }

    internal fun retainTree(
        attempt: ObservedPropertyFullAttempt,
        tree: List<VNode>,
    ) {
        requireActive(attempt)
        fun visit(nodes: List<VNode>) {
            nodes.forEach { node ->
                node.observedPropertyId?.let(attempt.touchedIds::add)
                visit(node.children)
            }
        }
        visit(tree)
        val unknown = attempt.touchedIds.firstOrNull { id ->
            id !in bindingsById && id !in attempt.candidatesById
        }
        check(unknown == null) {
            "Observed property id $unknown is not owned by this RenderSession."
        }
    }

    internal fun commit(attempt: ObservedPropertyFullAttempt) {
        requireActive(attempt)
        val retained = attempt.touchedIds
        val removed = bindingsById.keys.filterNot(retained::contains)
        attempt.candidatesById.values.forEach { candidate ->
            commitCandidate(candidate)
            idsByScope[candidate.scope] = candidate.id
        }
        removed.forEach { id ->
            bindingsById.remove(id)?.let { binding ->
                idsByScope.remove(binding.scope)
                binding.signal.active.set(false)
                binding.observation.dispose()
            }
        }
        attempt.complete()
        activeAttempt = null
    }

    internal fun abort(attempt: ObservedPropertyFullAttempt) {
        if (activeAttempt !== attempt) return
        attempt.candidatesById.values.forEach { candidate ->
            candidate.abortObservation()
            if (bindingsById[candidate.id]?.signal !== candidate.signal) {
                candidate.signal.active.set(false)
            }
        }
        attempt.complete()
        activeAttempt = null
    }

    internal fun commit(candidates: List<ObservedPropertyCandidate>) {
        requireOwnerThread("commitDirty")
        candidates.forEach(::commitCandidate)
    }

    internal fun committedSpec(id: Long): NodeSpec? = bindingsById[id]?.spec

    private fun commitCandidate(candidate: ObservedPropertyCandidate) {
        val previous = bindingsById[candidate.id]
        candidate.commitObservation()
        bindingsById[candidate.id] = CommittedObservedProperty(
            id = candidate.id,
            scope = candidate.scope,
            signal = candidate.signal,
            inputs = candidate.inputs,
            localSnapshot = candidate.localSnapshot,
            reader = candidate.reader,
            spec = candidate.spec,
            observation = candidate.observation,
        )
        candidate.signal.clearIfVersion(candidate.readVersion)
        previous?.observation?.takeUnless { observation ->
            observation === candidate.observation
        }?.dispose()
    }

    private fun prepareCandidate(
        id: Long,
        scope: RecomposeScope,
        signal: ObservedPropertySignal,
        inputs: List<Any?>,
        localSnapshot: LocalSnapshot,
        reader: () -> NodeSpec,
        previousObservation: Observation? = null,
    ): ObservedPropertyCandidate {
        val readVersion = signal.version.get()
        val spec: NodeSpec
        val observation: Observation
        val replacement: PreparedObservationReplacement?
        if (previousObservation == null) {
            val observed = RuntimeObservation.observeReads(
                onInvalidated = signal::invalidate,
            ) {
                LocalContext.withSnapshot(localSnapshot) {
                    reader()
                }
            }
            spec = observed.first
            observation = observed.second
            replacement = null
        } else {
            val observed = RuntimeObservation.prepareReplacement(previousObservation) {
                LocalContext.withSnapshot(localSnapshot) {
                    reader()
                }
            }
            spec = observed.first
            observation = previousObservation
            replacement = observed.second
        }
        return ObservedPropertyCandidate(
            id = id,
            scope = scope,
            signal = signal,
            // Public constructors already freeze inputs; retain that immutable snapshot per reader.
            inputs = inputs,
            localSnapshot = localSnapshot,
            reader = reader,
            spec = spec,
            observation = observation,
            replacement = replacement,
            readVersion = readVersion,
        )
    }

    private fun CommittedObservedProperty.prepareCandidate(
        inputs: List<Any?>,
        localSnapshot: LocalSnapshot,
        reader: () -> NodeSpec,
    ): ObservedPropertyCandidate = prepareCandidate(
        id = id,
        scope = scope,
        signal = signal,
        inputs = inputs,
        localSnapshot = localSnapshot,
        reader = reader,
        previousObservation = observation,
    )

    private fun requireActive(attempt: ObservedPropertyFullAttempt) {
        requireOwnerThread("fullAttempt")
        check(activeAttempt === attempt && !attempt.completed) {
            "Observed-property attempt is not active."
        }
    }

    private fun requireOwnerThread(operation: String) {
        check(Thread.currentThread() === ownerThread) {
            "ObservedPropertyRegistry.$operation must run on its owner thread '${ownerThread.name}'."
        }
    }

    private fun consumeScheduledRequest() {
        requestScheduled.set(false)
    }

    private fun scheduleRequest() {
        if (!disposed && requestScheduled.compareAndSet(false, true)) {
            onInvalidated()
        }
    }
}

internal class ObservedPropertyFullAttempt internal constructor(
    private val registry: ObservedPropertyRegistry,
) {
    internal val candidatesById = LinkedHashMap<Long, ObservedPropertyCandidate>()
    internal val newIdsByScope = IdentityHashMap<RecomposeScope, Long>()
    internal val touchedIds = LinkedHashSet<Long>()
    internal var completed = false
        private set

    fun resolve(
        scope: RecomposeScope,
        source: ObservedNodeSpec<out NodeSpec>,
        localSnapshot: LocalSnapshot,
    ): ObservedPropertyResolution = registry.resolve(this, scope, source, localSnapshot)

    fun retainTree(tree: List<VNode>) {
        registry.retainTree(this, tree)
    }

    fun commit() {
        registry.commit(this)
    }

    fun abort() {
        registry.abort(this)
    }

    fun hasInvalidatedCandidates(): Boolean {
        return candidatesById.values.any { candidate ->
            candidate.signal.version.get() != candidate.readVersion
        }
    }

    internal fun replaceCandidate(candidate: ObservedPropertyCandidate) {
        candidatesById.put(candidate.id, candidate)?.abortObservation()
    }

    internal fun complete() {
        completed = true
    }
}

internal class PreparedObservedPropertyTransaction internal constructor(
    private val registry: ObservedPropertyRegistry,
    val candidates: List<ObservedPropertyCandidate>,
) {
    private var completed = false

    val changes: List<ObservedPropertyChange>
        get() = candidates.mapNotNull { candidate ->
            val previous = candidate.previousSpec(registry) ?: return@mapNotNull null
            if (previous == candidate.spec) return@mapNotNull null
            ObservedPropertyChange(
                id = candidate.id,
                previous = previous,
                next = candidate.spec,
            )
        }

    fun commit() {
        check(!completed) { "Observed-property transaction is already completed." }
        completed = true
        registry.commit(candidates)
    }

    fun abort() {
        if (completed) return
        completed = true
        candidates.forEach(ObservedPropertyCandidate::abortObservation)
    }
}

internal data class ObservedPropertyChange(
    val id: Long,
    val previous: NodeSpec,
    val next: NodeSpec,
)

internal data class ObservedPropertyCandidate(
    val id: Long,
    val scope: RecomposeScope,
    val signal: ObservedPropertySignal,
    val inputs: List<Any?>,
    val localSnapshot: LocalSnapshot,
    val reader: () -> NodeSpec,
    val spec: NodeSpec,
    val observation: Observation,
    val replacement: PreparedObservationReplacement?,
    val readVersion: Long,
) {
    fun previousSpec(registry: ObservedPropertyRegistry): NodeSpec? =
        registry.committedSpec(id)

    fun commitObservation() {
        replacement?.commit()
    }

    fun abortObservation() {
        if (replacement == null) {
            observation.dispose()
        } else {
            replacement.abort()
        }
    }
}

private data class CommittedObservedProperty(
    val id: Long,
    val scope: RecomposeScope,
    val signal: ObservedPropertySignal,
    val inputs: List<Any?>,
    val localSnapshot: LocalSnapshot,
    val reader: () -> NodeSpec,
    val spec: NodeSpec,
    val observation: Observation,
)

internal class ObservedPropertySignal(
    private val onInvalidated: () -> Unit,
) {
    val version = AtomicLong(0L)
    val dirty = AtomicBoolean(true)
    val active = AtomicBoolean(true)

    fun invalidate() {
        if (!active.get()) return
        version.incrementAndGet()
        dirty.set(true)
        onInvalidated()
    }

    fun clearIfVersion(expected: Long) {
        if (version.get() != expected) return
        dirty.set(false)
        if (version.get() != expected) {
            dirty.set(true)
            onInvalidated()
        }
    }
}
