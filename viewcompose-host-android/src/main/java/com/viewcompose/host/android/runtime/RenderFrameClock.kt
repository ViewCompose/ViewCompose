package com.viewcompose.host.android.runtime

/**
 * 统一 Choreographer 回调形态，便于 runtime 测试替换时钟。
 * Normalizes Choreographer callback shape so runtime tests can replace the clock.
 */
internal fun interface RenderFrameCallback {
    fun doFrame(frameTimeNanos: Long)
}

/**
 * RenderSessionRuntime 使用的帧时钟抽象。
 * Frame-clock abstraction used by RenderSessionRuntime.
 */
internal interface RenderFrameClock {
    /**
     * 注册下一帧回调。
     * Registers a callback for the next frame.
     */
    fun postFrameCallback(callback: RenderFrameCallback)

    /**
     * 移除尚未执行的帧回调。
     * Removes a frame callback that has not run yet.
     */
    fun removeFrameCallback(callback: RenderFrameCallback)
}
