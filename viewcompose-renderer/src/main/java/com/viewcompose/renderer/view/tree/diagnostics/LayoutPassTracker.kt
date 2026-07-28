package com.viewcompose.renderer.view.tree

/**
 * 单个 View 类型的 measure/layout 统计条目。
 * Measure/layout statistics entry for one View type.
 */
data class LayoutPassEntry(
    val viewName: String,
    val measureCount: Int,
    val layoutCount: Int,
    val totalMeasureNs: Long,
    val totalLayoutNs: Long,
)

/**
 * layout pass 采样快照。
 * Snapshot of sampled layout passes.
 */
data class LayoutPassSnapshot(
    val totalMeasureCount: Int = 0,
    val totalLayoutCount: Int = 0,
    val totalMeasureNs: Long = 0L,
    val totalLayoutNs: Long = 0L,
    val entries: List<LayoutPassEntry> = emptyList(),
)

/**
 * 轻量级 layout pass 采样器。
 * Lightweight layout pass sampler.
 *
 * 默认关闭，仅在诊断页面或测试显式启用时记录 measure/layout 耗时。
 * Disabled by default; records measure/layout timing only when diagnostics pages or tests enable it.
 */
object LayoutPassTracker {
    private val counters = linkedMapOf<String, MutableLayoutPassCounter>()

    @Volatile
    var isEnabled: Boolean = false
        private set

    /**
     * 启用采样，可选择是否清空历史计数。
     * Enables sampling and optionally clears historical counters.
     */
    @Synchronized
    fun start(resetCounters: Boolean = true) {
        if (resetCounters) {
            counters.clear()
        }
        isEnabled = true
    }

    /**
     * 关闭采样但保留当前计数，便于随后读取 snapshot。
     * Stops sampling while preserving current counters for later snapshot reads.
     */
    fun stop() {
        isEnabled = false
    }

    /**
     * 返回采样起点；未启用时返回哨兵值避免额外 nanoTime 成本。
     * Returns a timing start point, or a sentinel when disabled to avoid extra nanoTime cost.
     */
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
