package com.viewcompose.host.android.runtime

import android.view.Choreographer
import com.viewcompose.runtime.frame.MonotonicFrameClock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Exposes Android [Choreographer] frames through the runtime monotonic-frame-clock contract.
 *
 * The clock is main-thread confined because [Choreographer] is thread-local. Cancelling a suspended
 * caller removes its pending callback. Exceptions thrown by a frame callback resume the caller with
 * that exception rather than escaping through Choreographer.
 *
 * @param choreographer frame source owned by the current thread
 */
class AndroidMonotonicFrameClock(
    private val choreographer: Choreographer = Choreographer.getInstance(),
) : MonotonicFrameClock {
    /**
     * Suspends until the next frame and evaluates [onFrame] with its monotonic timestamp.
     *
     * @param onFrame callback evaluated once on the Choreographer thread
     * @return the callback result
     */
    override suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R,
    ): R {
        return suspendCancellableCoroutine { continuation ->
            val callback = Choreographer.FrameCallback { frameTimeNanos ->
                if (!continuation.isActive) {
                    return@FrameCallback
                }
                // Propagate caller exceptions through the suspended coroutine.
                try {
                    continuation.resume(onFrame(frameTimeNanos))
                } catch (throwable: Throwable) {
                    continuation.resumeWithException(throwable)
                }
            }
            continuation.invokeOnCancellation {
                // Remove unexecuted callbacks so cancelled work cannot wake the main thread.
                choreographer.removeFrameCallback(callback)
            }
            choreographer.postFrameCallback(callback)
        }
    }
}
