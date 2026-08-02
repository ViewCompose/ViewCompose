package com.viewcompose.runtime.frame

/** Provides platform-neutral frame timing to animation and gesture runtimes. */
interface MonotonicFrameClock {
    /**
     * Suspends until this clock's next frame and returns the result of [onFrame].
     *
     * The clock invokes [onFrame] once with a nanosecond timestamp that is monotonic relative to
     * other frames from the same clock. Callback exceptions propagate to the caller. Implementations
     * define the scheduling context and cancellation integration.
     *
     * @param R type of value returned by the frame callback
     * @param onFrame callback invoked for the next frame with its monotonic timestamp
     * @return the value returned by [onFrame]
     */
    suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R,
    ): R
}

/**
 * Provides an unpaced fallback clock using [System.nanoTime].
 *
 * [withFrameNanos] invokes its callback immediately without suspending or synchronizing to a
 * display refresh. Use this clock only for headless hosts, deterministic runtime plumbing, or when
 * no platform frame source is available; it is not suitable for visually paced animation.
 */
object FallbackMonotonicFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R,
    ): R {
        return onFrame(System.nanoTime())
    }
}
