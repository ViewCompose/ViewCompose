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
    private val onFrameRender: FrameRenderAction,
    private val isMainThread: (() -> Boolean)? = null,
    private val postToMain: ((Runnable) -> Unit)? = null,
) : RenderFrameCallback {
    private val disposed = AtomicBoolean(false)
    private val frameRequested = AtomicBoolean(false)

    override fun doFrame(frameTimeNanos: Long) {
        if (disposed.get()) {
            frameRequested.set(false)
            return
        }
        // Clear first so reentrant invalidation can schedule the following frame.
        frameRequested.set(false)
        onFrameRender.renderFrame()
    }

    private val requestOnMain = Runnable {
        postFrameOnMain()
    }

    private val cancelOnMain = Runnable {
        cancelFrameOnMain()
    }

    fun requestFrame() {
        if (disposed.get()) return
        if (!frameRequested.compareAndSet(false, true)) return
        if (isOnMainThread()) {
            postFrameOnMain()
        } else {
            postOnMain(requestOnMain)
        }
    }

    fun cancelPending() {
        if (!frameRequested.get()) return
        if (isOnMainThread()) {
            cancelFrameOnMain()
        } else {
            postOnMain(cancelOnMain)
        }
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        cancelPending()
    }

    private fun postFrameOnMain() {
        if (disposed.get()) {
            frameRequested.set(false)
            return
        }
        frameClock.postFrameCallback(this)
    }

    private fun cancelFrameOnMain() {
        if (frameRequested.compareAndSet(true, false)) {
            frameClock.removeFrameCallback(this)
        }
    }

    private fun isOnMainThread(): Boolean {
        return isMainThread?.invoke() ?: (Looper.myLooper() == Looper.getMainLooper())
    }

    private fun postOnMain(action: Runnable) {
        val customPost = postToMain
        if (customPost != null) {
            customPost(action)
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }
}

/** Dedicated frame action avoids R8 merging the hot callback with unrelated Kotlin lambdas. */
internal fun interface FrameRenderAction {
    fun renderFrame()
}
