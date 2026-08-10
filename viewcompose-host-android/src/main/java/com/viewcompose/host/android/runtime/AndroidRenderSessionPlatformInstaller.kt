package com.viewcompose.host.android.runtime

import android.os.Trace
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.viewcompose.ui.focus.FocusDirection
import com.viewcompose.ui.focus.FocusManager
import com.viewcompose.ui.foundation.RenderSessionPlatformDiagnostics
import com.viewcompose.ui.foundation.RenderSessionSourceRegistration
import com.viewcompose.ui.foundation.RenderSessionSourceTooling
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.nativeContainer
import com.viewcompose.ui.foundation.installRenderSessionPlatform
import com.viewcompose.host.android.tooling.AndroidDeviceDslSourceRegistry
import com.viewcompose.ui.tooling.UiSourceCallSite
import com.viewcompose.ui.tooling.UiSourceSessionContainerHandle
import com.viewcompose.ui.tooling.UiSourceSessionRole
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
    override val sourceTooling: RenderSessionSourceTooling = AndroidRenderSessionSourceTooling

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

private object AndroidRenderSessionSourceTooling : RenderSessionSourceTooling {
    override fun shouldCapture(container: RenderContainerHandle): Boolean {
        val role = (container as? UiSourceSessionContainerHandle)?.sourceSessionRole
        if (role != UiSourceSessionRole.Host && role != UiSourceSessionRole.Page) return false
        val viewGroup = container.nativeContainer as? ViewGroup ?: return false
        return AndroidDeviceDslSourceRegistry.shouldCapture(viewGroup.context)
    }

    override fun register(
        container: RenderContainerHandle,
        sourceCandidates: List<List<UiSourceCallSite>>,
    ): RenderSessionSourceRegistration? {
        val viewGroup = container.nativeContainer as? ViewGroup ?: return null
        return AndroidDeviceDslSourceRegistry.register(
            container = viewGroup,
            sourceCandidates = sourceCandidates,
        )
    }
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
