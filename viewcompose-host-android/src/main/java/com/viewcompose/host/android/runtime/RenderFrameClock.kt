package com.viewcompose.host.android.runtime

/** Normalized frame callback shape that allows tests to replace Choreographer. */
internal fun interface RenderFrameCallback {
    fun doFrame(frameTimeNanos: Long)
}

/** Frame-clock abstraction used by the Android render-session runtime. */
internal interface RenderFrameClock {
    /** Registers [callback] for the next frame. */
    fun postFrameCallback(callback: RenderFrameCallback)

    /** Removes [callback] if it has not run yet. */
    fun removeFrameCallback(callback: RenderFrameCallback)
}
