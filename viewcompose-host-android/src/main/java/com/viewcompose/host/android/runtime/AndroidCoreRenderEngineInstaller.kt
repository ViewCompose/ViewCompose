package com.viewcompose.host.android.runtime

import com.viewcompose.widget.core.installCoreRenderEngine
import com.viewcompose.widget.core.installRenderSessionCoroutineContext
import com.viewcompose.widget.core.installRenderSessionRuntimeFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers

private val installed = AtomicBoolean(false)

internal fun ensureAndroidCoreRenderEngineInstalled() {
    if (installed.compareAndSet(false, true)) {
        installCoreRenderEngine(AndroidCoreRenderEngine())
        installRenderSessionCoroutineContext(Dispatchers.Main.immediate)
        installRenderSessionRuntimeFactory { onRenderNow, onDisposeNow ->
            AndroidFrameAlignedRenderSessionRuntime(
                onRenderNow = onRenderNow,
                onDisposeNow = onDisposeNow,
            )
        }
    }
}
