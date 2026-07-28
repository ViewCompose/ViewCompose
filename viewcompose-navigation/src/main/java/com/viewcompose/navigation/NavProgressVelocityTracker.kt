package com.viewcompose.navigation

/**
 * 估算 predictive back 进度速度的滑动窗口采样器。
 * Sliding-window sampler that estimates predictive-back progress velocity.
 */
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
            // 乱序帧通常来自重启或测试时钟回拨，保留旧样本会让速度符号失真。
            // Out-of-order frames usually mean a restart or test-clock rewind; stale samples skew sign.
            samples.clear()
        } else if (last != null && frameTimeMillis == last.frameTimeMillis) {
            // 同一帧只保留最后一次进度，避免零时间差样本影响窗口端点。
            // Keep only the latest progress for a frame to avoid zero-delta samples at the boundary.
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
            // 保留窗口外的一个前置样本，让窗口边界附近仍能计算跨帧速度。
            // Keep one leading sample outside the window so boundary velocity still spans frames.
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
