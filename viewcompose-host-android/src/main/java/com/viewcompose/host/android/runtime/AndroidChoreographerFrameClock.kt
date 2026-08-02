package com.viewcompose.host.android.runtime

import android.view.Choreographer
import java.util.WeakHashMap

/** Main-thread frame clock backed by Android [Choreographer]. */
internal class AndroidChoreographerFrameClock(
    private val choreographer: Choreographer = Choreographer.getInstance(),
) : RenderFrameClock {
    // Retain wrappers so cancellation addresses the exact callback registered with Choreographer.
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
