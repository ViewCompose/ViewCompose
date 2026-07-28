package com.viewcompose.host.android.runtime

import android.os.Trace
import com.viewcompose.widget.core.RenderSessionRuntime
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 上按 Choreographer 帧对齐的 RenderSessionRuntime。
 * RenderSessionRuntime aligned to Choreographer frames on Android.
 *
 * 多次 requestRender 会合并到下一帧；显式 render 会取消待执行帧并立即同步渲染。
 * Multiple requestRender calls coalesce into the next frame; explicit render cancels pending frames and renders synchronously.
 */
internal class AndroidFrameAlignedRenderSessionRuntime(
    private val onRenderNow: () -> Unit,
    private val onDisposeNow: () -> Unit,
    frameClock: RenderFrameClock = AndroidChoreographerFrameClock(),
) : RenderSessionRuntime {
    private val disposed = AtomicBoolean(false)
    private val renderingActive = AtomicBoolean(true)
    private val renderRequested = AtomicBoolean(false)
    private val frameDispatcher = FrameAlignedRenderDispatcher(
        frameClock = frameClock,
        onFrameRender = {
            if (
                !disposed.get() &&
                renderingActive.get() &&
                renderRequested.compareAndSet(true, false)
            ) {
                traceSection("VC.FrameRender") {
                    onRenderNow()
                }
            }
        },
    )

    override fun requestRender() {
        if (disposed.get()) return
        renderRequested.set(true)
        if (renderingActive.get()) {
            frameDispatcher.requestFrame()
        }
    }

    override fun render() {
        if (disposed.get()) return
        frameDispatcher.cancelPending()
        renderRequested.set(false)
        traceSection("VC.DirectRender") {
            onRenderNow()
        }
    }

    override fun setRenderingActive(active: Boolean) {
        if (disposed.get()) return
        if (renderingActive.getAndSet(active) == active) return
        if (active) {
            // 恢复渲染时保留 pending invalidation，并在下一帧合并执行。
            // When rendering resumes, keep the pending invalidation and execute it on the next frame.
            if (renderRequested.get()) {
                frameDispatcher.requestFrame()
            }
        } else {
            // 暂停期间取消 Choreographer callback，但不清除 renderRequested 标记。
            // While paused, cancel the Choreographer callback without clearing the renderRequested flag.
            frameDispatcher.cancelPending()
        }
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        renderRequested.set(false)
        frameDispatcher.dispose()
        onDisposeNow()
    }
}

private inline fun <T> traceSection(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}
