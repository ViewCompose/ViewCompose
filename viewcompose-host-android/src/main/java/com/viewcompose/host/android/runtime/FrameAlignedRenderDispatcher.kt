package com.viewcompose.host.android.runtime

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coalesces render requests onto the next main-thread frame.
 *
 * Atomic state makes cross-thread requests and cancellation idempotent while every frame-clock
 * operation remains confined to the main thread.
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
        // Clear first so reentrant invalidation can schedule the following frame.
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
