package com.viewcompose.runtime.composition

/**
 * 重组失效队列，按插入顺序收集脏 scope 并在 drain 时去重/压缩。
 * Recomposition invalidation queue that collects dirty scopes in insertion order and deduplicates/compacts on drain.
 */
class InvalidationQueue {
    private val pending = LinkedHashSet<RecomposeScope>()

    /**
     * 加入需要重组的 scope；已 dispose 的 scope 会被忽略。
     * Enqueues a scope for recomposition; disposed scopes are ignored.
     */
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

    /**
     * 取出并压缩失效项：父 scope 已重组时，子 scope 不再单独返回。
     * Drains and compacts invalidations: when a parent scope recomposes, child scopes are not returned separately.
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
