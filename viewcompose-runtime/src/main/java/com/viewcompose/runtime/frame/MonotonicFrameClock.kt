package com.viewcompose.runtime.frame

/**
 * 平台无关的帧时钟契约，供动画和手势运行时等待下一帧。
 * Platform-agnostic frame clock contract used by animation and gesture runtimes.
 */
interface MonotonicFrameClock {
    /**
     * 在下一帧调用 onFrame，并传入单调递增的纳秒时间戳。
     * Calls onFrame on the next frame with a monotonically increasing nanosecond timestamp.
     */
    suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R,
    ): R
}

/**
 * 无平台帧源时的后备实现，立即使用 System.nanoTime() 响应。
 * Fallback implementation for hosts without a frame source, responding immediately with System.nanoTime().
 */
object FallbackMonotonicFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R,
    ): R {
        return onFrame(System.nanoTime())
    }
}
