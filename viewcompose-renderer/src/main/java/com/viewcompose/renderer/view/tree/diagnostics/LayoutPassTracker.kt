package com.viewcompose.renderer.view.tree

data class LayoutPassEntry(
    val viewName: String,
    val measureCount: Int,
    val layoutCount: Int,
    val totalMeasureNs: Long,
    val totalLayoutNs: Long,
)

data class LayoutPassSnapshot(
    val totalMeasureCount: Int = 0,
    val totalLayoutCount: Int = 0,
    val totalMeasureNs: Long = 0L,
    val totalLayoutNs: Long = 0L,
    val entries: List<LayoutPassEntry> = emptyList(),
)

object LayoutPassTracker {
    private val counters = linkedMapOf<String, MutableLayoutPassCounter>()

    @Volatile
    var isEnabled: Boolean = false
        private set

    @Synchronized
    fun start(resetCounters: Boolean = true) {
        if (resetCounters) {
            counters.clear()
        }
        isEnabled = true
    }

    fun stop() {
        isEnabled = false
    }

    fun beginTiming(): Long = if (isEnabled) System.nanoTime() else TIMING_DISABLED

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
