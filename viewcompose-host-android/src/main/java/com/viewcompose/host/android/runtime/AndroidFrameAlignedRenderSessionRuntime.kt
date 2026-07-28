package com.viewcompose.host.android.runtime

import android.os.Trace
import com.viewcompose.widget.core.RenderSessionRuntime
import java.util.concurrent.atomic.AtomicBoolean

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
            if (renderRequested.get()) {
                frameDispatcher.requestFrame()
            }
        } else {
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
