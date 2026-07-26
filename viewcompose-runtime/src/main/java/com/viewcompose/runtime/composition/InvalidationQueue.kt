package com.viewcompose.runtime.composition

class InvalidationQueue {
    private val pending = LinkedHashSet<RecomposeScope>()

    @Synchronized
    fun enqueue(scope: RecomposeScope) {
        if (scope.disposed) return
        pending += scope
    }

    @Synchronized
    fun isNotEmpty(): Boolean = pending.isNotEmpty()

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
