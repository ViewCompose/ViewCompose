package com.viewcompose.runtime.composition

/**
 * Collects dirty [RecomposeScope] instances and drains them in insertion order.
 *
 * Enqueue, inspection, clearing, and draining are synchronized. Duplicate scopes are coalesced, and
 * compacted draining removes descendants when an invalidated ancestor already covers their work.
 */
class InvalidationQueue {
    private val pending = LinkedHashSet<RecomposeScope>()

    /**
     * Enqueues [scope] for a future recomposition pass.
     *
     * Repeated enqueue calls are coalesced and disposed scopes are ignored.
     *
     * @param scope dirty scope to enqueue
     */
    @Synchronized
    fun enqueue(scope: RecomposeScope) {
        if (scope.disposed) return
        pending += scope
    }

    /** Returns whether at least one scope is waiting to be drained. */
    @Synchronized
    fun isNotEmpty(): Boolean = pending.isNotEmpty()

    /** Removes every pending scope without disposing or recomposing it. */
    @Synchronized
    fun clear() {
        pending.clear()
    }

    @Synchronized
    internal fun drainAll(): List<RecomposeScope> {
        if (pending.isEmpty()) return emptyList()
        return pending.toList().also {
            pending.clear()
        }
    }

    /**
     * Removes and returns the minimal ordered set of scopes needed for recomposition.
     *
     * When both an ancestor and its descendant are pending, only the ancestor is returned. The queue
     * is empty after this call.
     *
     * @return compacted scopes in their effective insertion order
     */
    @Synchronized
    fun drainCompacted(): List<RecomposeScope> {
        if (pending.isEmpty()) return emptyList()
        val drained = pending.toList()
        pending.clear()
        val compacted = mutableListOf<RecomposeScope>()
        drained.forEach { scope ->
            if (compacted.any { ancestor -> scope.isDescendantOf(ancestor) }) {
                return@forEach
            }
            compacted.removeAll { candidate -> candidate.isDescendantOf(scope) }
            compacted += scope
        }
        return compacted
    }

    private fun RecomposeScope.isDescendantOf(ancestor: RecomposeScope): Boolean {
        var current: RecomposeScope? = this
        while (current != null) {
            if (current == ancestor) {
                return true
            }
            current = current.parent
        }
        return false
    }
}
