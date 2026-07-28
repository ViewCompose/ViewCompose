package com.viewcompose.host.android.runtime

import com.viewcompose.widget.core.installRenderSessionPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers

private val installed = AtomicBoolean(false)

/**
 * 按需安装 Android RenderSession 平台实现。
 * Installs the Android RenderSession platform implementation on demand.
 *
 * 安装过程是进程级幂等的，确保 widget-core 在第一次 Android renderInto 前获得 renderer、主线程和 runtime factory。
 * Installation is process-wide idempotent and ensures widget-core has the renderer, main dispatcher, and runtime factory before first Android renderInto.
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
