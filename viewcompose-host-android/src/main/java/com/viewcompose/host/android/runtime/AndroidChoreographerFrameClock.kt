package com.viewcompose.host.android.runtime

import android.view.Choreographer
import java.util.WeakHashMap

/**
 * 基于 Android Choreographer 的 RenderFrameClock 实现。
 * RenderFrameClock implementation backed by Android Choreographer.
 */
internal class AndroidChoreographerFrameClock(
    private val choreographer: Choreographer = Choreographer.getInstance(),
) : RenderFrameClock {
    // 缓存包装 callback，保证 removeFrameCallback 能找到同一个 Choreographer.FrameCallback。
    // Cache wrapper callbacks so removeFrameCallback can find the same Choreographer.FrameCallback.
    private val callbacks = WeakHashMap<RenderFrameCallback, Choreographer.FrameCallback>()

    override fun postFrameCallback(callback: RenderFrameCallback) {
        val choreographerCallback = callbacks.getOrPut(callback) {
            Choreographer.FrameCallback { frameTimeNanos ->
                callback.doFrame(frameTimeNanos)
            }
        }
        choreographer.postFrameCallback(choreographerCallback)
    }

    override fun removeFrameCallback(callback: RenderFrameCallback) {
        callbacks.remove(callback)?.let { choreographerCallback ->
            choreographer.removeFrameCallback(choreographerCallback)
        }
    }
}
