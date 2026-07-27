package com.viewcompose.host.android.runtime

import android.os.Trace
import com.viewcompose.widget.core.RenderSessionRuntime
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidFrameAlignedRenderSessionRuntime(
    private val onRenderNow: () -> Unit,
    private val onDisposeNow: () -> Unit,
) : RenderSessionRuntime {
    private val disposed = AtomicBoolean(false)
    private val frameDispatcher = FrameAlignedRenderDispatcher(
        frameClock = AndroidChoreographerFrameClock(),
        onFrameRender = {
            if (!disposed.get()) {
                traceSection("VC.FrameRender") {
                    onRenderNow()
                }
            }
        },
    )

    override fun requestRender() {
        if (disposed.get()) return
        frameDispatcher.requestFrame()
    }

    override fun render() {
        if (disposed.get()) return
        frameDispatcher.cancelPending()
        traceSection("VC.DirectRender") {
            onRenderNow()
        }
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
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
