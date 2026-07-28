package com.viewcompose.host.android.runtime

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 将渲染请求合并到主线程下一帧的轻量调度器。
 * Lightweight dispatcher that coalesces render requests onto the next main-thread frame.
 *
 * AtomicBoolean 让跨线程 request/cancel 保持幂等，真正的 frameClock 访问仍在主线程执行。
 * AtomicBoolean keeps cross-thread request/cancel idempotent while actual frameClock access still happens on the main thread.
 */
internal class FrameAlignedRenderDispatcher(
    private val frameClock: RenderFrameClock,
    private val onFrameRender: () -> Unit,
    private val isMainThread: () -> Boolean = { Looper.myLooper() == Looper.getMainLooper() },
    private val postToMain: (Runnable) -> Unit = { runnable ->
        Handler(Looper.getMainLooper()).post(runnable)
    },
) {
    private val disposed = AtomicBoolean(false)
    private val frameRequested = AtomicBoolean(false)

    private val frameCallback = RenderFrameCallback {
        if (disposed.get()) {
            frameRequested.set(false)
            return@RenderFrameCallback
        }
        // callback 开始时先清除 pending 标记，允许渲染过程中的再次 invalidation 排到下一帧。
        // Clear the pending flag before rendering so reentrant invalidation can schedule the following frame.
        frameRequested.set(false)
        onFrameRender()
    }

    private val requestOnMain = Runnable {
        if (disposed.get()) {
            frameRequested.set(false)
            return@Runnable
        }
        frameClock.postFrameCallback(frameCallback)
    }

    private val cancelOnMain = Runnable {
        if (frameRequested.compareAndSet(true, false)) {
            frameClock.removeFrameCallback(frameCallback)
        }
    }

    fun requestFrame() {
        if (disposed.get()) return
        if (!frameRequested.compareAndSet(false, true)) return
        if (isMainThread()) {
            requestOnMain.run()
        } else {
            postToMain(requestOnMain)
        }
    }

    fun cancelPending() {
        if (!frameRequested.get()) return
        if (isMainThread()) {
            cancelOnMain.run()
        } else {
            postToMain(cancelOnMain)
        }
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        cancelPending()
    }
}
