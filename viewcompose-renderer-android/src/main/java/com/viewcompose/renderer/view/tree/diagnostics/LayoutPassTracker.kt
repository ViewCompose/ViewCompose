package com.viewcompose.renderer.view.tree

/**
 * Aggregated measure and layout timings for one Android View class name.
 *
 * @property viewName simple View class name used as the aggregation key
 * @property measureCount number of recorded measure calls
 * @property layoutCount number of recorded layout calls
 * @property totalMeasureNs cumulative measure time in nanoseconds
 * @property totalLayoutNs cumulative layout time in nanoseconds
 */
data class LayoutPassEntry(
    val viewName: String,
    val measureCount: Int,
    val layoutCount: Int,
    val totalMeasureNs: Long,
    val totalLayoutNs: Long,
)

/**
 * Immutable process-local snapshot of sampled View layout work.
 *
 * @property totalMeasureCount measure calls across all entries
 * @property totalLayoutCount layout calls across all entries
 * @property totalMeasureNs cumulative measure time in nanoseconds
 * @property totalLayoutNs cumulative layout time in nanoseconds
 * @property entries per-class entries ordered by descending total time, call count, then name
 */
data class LayoutPassSnapshot(
    val totalMeasureCount: Int = 0,
    val totalLayoutCount: Int = 0,
    val totalMeasureNs: Long = 0L,
    val totalLayoutNs: Long = 0L,
    val entries: List<LayoutPassEntry> = emptyList(),
)

/**
 * Opt-in process-local sampler for Android View measure and layout calls.
 *
 * Sampling is disabled by default. Recording methods are safe to call from multiple threads, but
 * typical renderer use is UI-thread confined. Enabling adds a `nanoTime` call and synchronized
 * aggregation to every instrumented pass, so it is intended for diagnostics rather than production
 * telemetry. Snapshots retain no View instances.
 */
object LayoutPassTracker {
    private val counters = linkedMapOf<String, MutableLayoutPassCounter>()

    @Volatile
    /** Returns whether new timing samples are currently accepted. */
    var isEnabled: Boolean = false
        private set

    /**
     * Enables timing collection and optionally clears accumulated counters atomically.
     *
     * @param resetCounters whether to discard samples collected by an earlier session
     */
    @Synchronized
    fun start(resetCounters: Boolean = true) {
        if (resetCounters) {
            counters.clear()
        }
        isEnabled = true
    }

    /** Stops accepting new samples while preserving counters for [snapshot]. */
    fun stop() {
        isEnabled = false
    }

    /**
     * Returns a monotonic timing origin, or an internal sentinel while sampling is disabled.
     *
     * @return value to pass unchanged to [recordMeasureSince] or [recordLayoutSince]
     */
    fun beginTiming(): Long = if (isEnabled) System.nanoTime() else TIMING_DISABLED

    /**
     * Records elapsed measure time since [startNs] for [viewClass].
     *
     * @param viewClass concrete View class used as the aggregation key
     * @param startNs value returned by [beginTiming]; the disabled sentinel is ignored
     */
    fun recordMeasureSince(
        viewClass: Class<*>,
        startNs: Long,
    ) {
        if (startNs == TIMING_DISABLED) return
        recordMeasure(
            viewName = viewClass.simpleName,
            durationNs = System.nanoTime() - startNs,
        )
    }

    /**
     * Records elapsed layout time since [startNs] for [viewClass].
     *
     * @param viewClass concrete View class used as the aggregation key
     * @param startNs value returned by [beginTiming]; the disabled sentinel is ignored
     */
    fun recordLayoutSince(
        viewClass: Class<*>,
        startNs: Long,
    ) {
        if (startNs == TIMING_DISABLED) return
        recordLayout(
            viewName = viewClass.simpleName,
            durationNs = System.nanoTime() - startNs,
        )
    }

    /**
     * Adds one explicit measure duration to [viewName]'s aggregate.
     *
     * @param viewName stable human-readable View type name
     * @param durationNs elapsed measure time in nanoseconds
     */
    @Synchronized
    fun recordMeasure(
        viewName: String,
        durationNs: Long,
    ) {
        val counter = counters.getOrPut(viewName) {
            MutableLayoutPassCounter(viewName)
        }
        counter.measureCount += 1
        counter.totalMeasureNs += durationNs
    }

    /**
     * Adds one explicit layout duration to [viewName]'s aggregate.
     *
     * @param viewName stable human-readable View type name
     * @param durationNs elapsed layout time in nanoseconds
     */
    @Synchronized
    fun recordLayout(
        viewName: String,
        durationNs: Long,
    ) {
        val counter = counters.getOrPut(viewName) {
            MutableLayoutPassCounter(viewName)
        }
        counter.layoutCount += 1
        counter.totalLayoutNs += durationNs
    }

    /**
     * Returns an immutable, deterministically ordered copy of all current counters.
     *
     * @return snapshot that remains valid after sampling resumes or counters are reset
     */
    @Synchronized
    fun snapshot(): LayoutPassSnapshot {
        val entries = counters.values
            .map { counter ->
                LayoutPassEntry(
                    viewName = counter.viewName,
                    measureCount = counter.measureCount,
                    layoutCount = counter.layoutCount,
                    totalMeasureNs = counter.totalMeasureNs,
                    totalLayoutNs = counter.totalLayoutNs,
                )
            }
            .sortedWith(
                compareByDescending<LayoutPassEntry> { it.totalMeasureNs + it.totalLayoutNs }
                    .thenByDescending { it.measureCount + it.layoutCount }
                    .thenBy { it.viewName },
            )
        return LayoutPassSnapshot(
            totalMeasureCount = entries.sumOf { it.measureCount },
            totalLayoutCount = entries.sumOf { it.layoutCount },
            totalMeasureNs = entries.sumOf { it.totalMeasureNs },
            totalLayoutNs = entries.sumOf { it.totalLayoutNs },
            entries = entries,
        )
    }

    /** Clears all accumulated counters without changing [isEnabled]. */
    @Synchronized
    fun reset() {
        counters.clear()
    }

    private const val TIMING_DISABLED: Long = 0L
}

private class MutableLayoutPassCounter(
    val viewName: String,
) {
    var measureCount: Int = 0
    var layoutCount: Int = 0
    var totalMeasureNs: Long = 0L
    var totalLayoutNs: Long = 0L
}
