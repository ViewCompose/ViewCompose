package com.viewcompose.host.android.runtime

import android.os.Trace
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.viewcompose.ui.focus.FocusDirection
import com.viewcompose.ui.focus.FocusManager
import com.viewcompose.ui.foundation.RenderSessionPlatformDiagnostics
import com.viewcompose.ui.foundation.RenderSessionSourceTooling
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.nativeContainer
import com.viewcompose.ui.foundation.installRenderSessionPlatform
import java.util.ServiceLoader
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
            focusManagerFactory = { container -> AndroidSessionFocusManager(container) },
            diagnostics = AndroidRenderSessionDiagnostics,
        )
        installed.set(true)
    }
}

/** Android focus adapter installed for each render-session container. */
private class AndroidSessionFocusManager(
    container: RenderContainerHandle,
) : FocusManager {
    private val root = container.nativeContainer as? ViewGroup
        ?: error("AndroidSessionFocusManager requires a ViewGroup-backed render container.")

    override fun clearFocus(force: Boolean) {
        val focused = root.findFocus()
        focused?.clearFocus()
        if (force && root.hasFocus()) {
            root.clearFocus()
        }
    }

    override fun moveFocus(direction: FocusDirection): Boolean {
        val focused = root.findFocus()
        if (direction == FocusDirection.Exit) {
            val parent = focused?.parent as? View
            return parent?.requestFocus(View.FOCUS_BACKWARD) == true
        }
        val androidDirection = direction.toAndroidDirection()
        val target = focused?.focusSearch(androidDirection)
            ?: root.focusSearch(androidDirection)
            ?: return false
        return target.requestFocus(androidDirection)
    }
}

/** Android logging and tracing adapter for the platform-neutral session coordinator. */
private object AndroidRenderSessionDiagnostics : RenderSessionPlatformDiagnostics {
    override val sourceTooling: RenderSessionSourceTooling?
        get() = AndroidRenderSessionSourceToolingDiscovery.sourceTooling

    override fun monotonicTimeNanos(): Long = SystemClock.elapsedRealtimeNanos()

    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun warning(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun error(tag: String, message: String, cause: Throwable) {
        Log.e(tag, message, cause)
    }

    override fun <T> trace(
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
}

private object AndroidRenderSessionSourceToolingDiscovery {
    val sourceTooling: RenderSessionSourceTooling? by lazy {
        runCatching {
            val providers = ServiceLoader.load(
                RenderSessionSourceTooling::class.java,
                RenderSessionSourceTooling::class.java.classLoader,
            ).toList()
            selectSingleRenderSessionSourceTooling(providers)
        }.getOrElse { error ->
            Log.w(
                "ViewCompose",
                "Optional render-session inspection service discovery failed; continuing without it.",
                error,
            )
            null
        }
    }
}

internal fun selectSingleRenderSessionSourceTooling(
    providers: List<RenderSessionSourceTooling>,
): RenderSessionSourceTooling? {
    if (providers.size <= 1) return providers.singleOrNull()
    Log.w(
        "ViewCompose",
        "Multiple render-session inspection services were installed; disabling all of them: " +
            providers.joinToString { provider -> provider.javaClass.name },
    )
    return null
}

private fun FocusDirection.toAndroidDirection(): Int {
    return when (this) {
        FocusDirection.Next,
        FocusDirection.Enter,
        -> View.FOCUS_FORWARD
        FocusDirection.Previous -> View.FOCUS_BACKWARD
        FocusDirection.Left -> View.FOCUS_LEFT
        FocusDirection.Right -> View.FOCUS_RIGHT
        FocusDirection.Up -> View.FOCUS_UP
        FocusDirection.Down -> View.FOCUS_DOWN
        FocusDirection.Exit -> View.FOCUS_BACKWARD
    }
}
