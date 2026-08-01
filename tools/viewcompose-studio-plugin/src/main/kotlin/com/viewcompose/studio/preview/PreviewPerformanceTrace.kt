package com.viewcompose.studio.preview

/** End-to-end timing data kept with a rendered preview for diagnostics and regression reports. */
internal data class PreviewPerformanceTrace(
    val phases: List<PreviewPerformancePhase> = emptyList(),
) {
    val measuredDurationMillis: Long
        get() = phases.sumOf(PreviewPerformancePhase::durationMillis)

    fun plus(
        phase: String,
        durationMillis: Long,
        shared: Boolean = false,
    ): PreviewPerformanceTrace {
        return copy(
            phases = phases + PreviewPerformancePhase(
                phase = phase,
                durationMillis = durationMillis.coerceAtLeast(0L),
                shared = shared,
            ),
        )
    }
}

internal data class PreviewPerformancePhase(
    val phase: String,
    val durationMillis: Long,
    val shared: Boolean = false,
) {
    init {
        require(phase.isNotBlank())
        require(durationMillis >= 0L)
    }
}
