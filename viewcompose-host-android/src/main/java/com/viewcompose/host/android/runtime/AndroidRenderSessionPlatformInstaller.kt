package com.viewcompose.host.android.runtime

import com.viewcompose.widget.core.installRenderSessionPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers

private val installed = AtomicBoolean(false)

/**
 * Installs the process-wide Android render engine, dispatcher, and session runtime exactly once.
 */
internal fun ensureAndroidRenderSessionPlatformInstalled() {
    if (installed.get()) return
    synchronized(installed) {
        if (installed.get()) return
        installRenderSessionPlatform(
            renderEngine = AndroidCoreRenderEngine(),
            coroutineContext = Dispatchers.Main.immediate,
            runtimeFactory = { onRenderNow, onDisposeNow ->
                AndroidFrameAlignedRenderSessionRuntime(
                    onRenderNow = onRenderNow,
                    onDisposeNow = onDisposeNow,
                )
            },
        )
        installed.set(true)
    }
}
