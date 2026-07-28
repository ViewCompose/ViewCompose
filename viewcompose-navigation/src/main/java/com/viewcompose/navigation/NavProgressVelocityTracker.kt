package com.viewcompose.navigation

internal class NavProgressVelocityTracker(
    private val sampleWindowMillis: Long,
    private val maxAbsoluteVelocity: Float,
) {
    private val samples = ArrayDeque<Sample>()

    fun add(
        frameTimeMillis: Long,
        progress: Float,
    ): Float {
        val last = samples.lastOrNull()
        if (last != null && frameTimeMillis < last.frameTimeMillis) {
            samples.clear()
        } else if (last != null && frameTimeMillis == last.frameTimeMillis) {
            samples.removeLast()
        }
        samples += Sample(
            frameTimeMillis = frameTimeMillis,
            progress = progress,
        )
        val earliestTime = frameTimeMillis - sampleWindowMillis
        while (
            samples.size > 2 &&
            samples[1].frameTimeMillis <= earliestTime
        ) {
            samples.removeFirst()
        }
        val first = samples.first()
        val latest = samples.last()
        val elapsedMillis = latest.frameTimeMillis - first.frameTimeMillis
        if (elapsedMillis <= 0L) {
            return 0f
        }
        return (
            (latest.progress - first.progress) /
                (elapsedMillis / MILLIS_PER_SECOND)
            ).coerceIn(-maxAbsoluteVelocity, maxAbsoluteVelocity)
    }

    private data class Sample(
        val frameTimeMillis: Long,
        val progress: Float,
    )

    private companion object {
        const val MILLIS_PER_SECOND = 1_000f
    }
}
