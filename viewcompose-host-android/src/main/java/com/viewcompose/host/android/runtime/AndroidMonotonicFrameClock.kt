package com.viewcompose.host.android.runtime

import android.view.Choreographer
import com.viewcompose.runtime.frame.MonotonicFrameClock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 将 Choreographer 暴露为 runtime 使用的 MonotonicFrameClock。
 * Exposes Choreographer as the MonotonicFrameClock used by the runtime.
 */
class AndroidMonotonicFrameClock(
    private val choreographer: Choreographer = Choreographer.getInstance(),
) : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R,
    ): R {
        return suspendCancellableCoroutine { continuation ->
            val callback = Choreographer.FrameCallback { frameTimeNanos ->
                if (!continuation.isActive) {
                    return@FrameCallback
                }
                // onFrame 由调用方提供，异常要回传给挂起协程而不是吞掉。
                // onFrame is caller-provided, so exceptions must resume the suspended coroutine instead of being swallowed.
                try {
                    continuation.resume(onFrame(frameTimeNanos))
                } catch (throwable: Throwable) {
                    continuation.resumeWithException(throwable)
                }
            }
            continuation.invokeOnCancellation {
                // 协程取消时移除未执行 callback，避免无意义唤醒主线程。
                // Remove an unexecuted callback on coroutine cancellation to avoid unnecessary main-thread wakeups.
                choreographer.removeFrameCallback(callback)
            }
            choreographer.postFrameCallback(callback)
        }
    }
}
