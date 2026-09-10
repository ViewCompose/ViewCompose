package com.viewcompose.host.android.runtime

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    // Even generations are idle; odd generations own one request. Queued work captures its owner.
    private val requestGeneration = AtomicLong(0L)
    private var postedGeneration = 0L

    override fun doFrame(frameTimeNanos: Long) {
        val generation = postedGeneration
        postedGeneration = 0L
        // Clear first so reentrant invalidation can schedule the following frame.
        if (generation == 0L || disposed.get() ||
            !requestGeneration.compareAndSet(generation, generation + 1L)
        ) return
        onFrameRender.renderFrame()
    }

    fun requestFrame() {
        if (disposed.get()) return
        val generation = claimRequest()
        if (generation == 0L) return
        if (isOnMainThread()) {
            postFrameOnMain(generation)
        } else {
            postOnMain(Runnable { postFrameOnMain(generation) })
        }
    }

    private fun claimRequest(): Long {
        while (true) {
            val previous = requestGeneration.get()
            if (previous and 1L != 0L) return 0L
            val next = previous + 1L
            if (requestGeneration.compareAndSet(previous, next)) return next
        }
    }

    fun cancelPending() {
        val generation = invalidateRequest()
        if (generation == 0L) return
        if (isOnMainThread()) {
            cancelFrameOnMain(generation)
        } else {
            postOnMain(Runnable { cancelFrameOnMain(generation) })
        }
    }

    private fun invalidateRequest(): Long {
        while (true) {
            val generation = requestGeneration.get()
            if (generation and 1L == 0L) return 0L
            if (requestGeneration.compareAndSet(generation, generation + 1L)) return generation
        }
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        cancelPending()
    }

    private fun postFrameOnMain(generation: Long) {
        if (disposed.get() || requestGeneration.get() != generation) return
        if (postedGeneration != 0L) frameClock.removeFrameCallback(this)
        postedGeneration = generation
        try {
            frameClock.postFrameCallback(this)
        } catch (error: Throwable) {
            postedGeneration = 0L
            requestGeneration.compareAndSet(generation, generation + 1L)
            throw error
        }
    }

    private fun cancelFrameOnMain(generation: Long) {
        if (postedGeneration == generation) {
            postedGeneration = 0L
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
