package com.viewcompose.ui.foundation

import com.viewcompose.ui.focus.FocusManager
import com.viewcompose.ui.node.RenderContainerHandle
import kotlin.coroutines.CoroutineContext

/**
 * Atomically installs the platform capabilities required by every [RenderSession].
 *
 * Android applications normally receive this installation through `viewcompose-host-android`.
 * Custom hosts must install one complete platform before creating a session.
 */
fun installRenderSessionPlatform(
    renderEngine: CoreRenderEngine,
    coroutineContext: CoroutineContext,
    runtimeFactory: RenderSessionRuntimeFactory,
    focusManagerFactory: RenderSessionFocusManagerFactory,
    diagnostics: RenderSessionPlatformDiagnostics,
) {
    RenderSessionPlatformProvider.install(
        RenderSessionPlatform(
            renderEngine = renderEngine,
            coroutineContext = coroutineContext,
            runtimeFactory = runtimeFactory,
            focusManagerFactory = focusManagerFactory,
            diagnostics = diagnostics,
        ),
    )
}

/**
 * Complete platform capability set required by RenderSession.
 */
internal data class RenderSessionPlatform(
    val renderEngine: CoreRenderEngine,
    val coroutineContext: CoroutineContext,
    val runtimeFactory: RenderSessionRuntimeFactory,
    val focusManagerFactory: RenderSessionFocusManagerFactory,
    val diagnostics: RenderSessionPlatformDiagnostics,
)

/** Creates the focus adapter associated with one renderer-owned container. */
fun interface RenderSessionFocusManagerFactory {
    /** Returns the focus manager whose lifetime is bounded by the owning render session. */
    fun create(container: RenderContainerHandle): FocusManager
}

/** Platform diagnostics used by the composition-to-render session coordinator. */
interface RenderSessionPlatformDiagnostics {
    /** Records optional frame detail enabled by a debug render session. */
    fun debug(tag: String, message: String)

    /** Records a recoverable structural warning. */
    fun warning(tag: String, message: String)

    /** Records a render or callback failure. */
    fun error(tag: String, message: String, cause: Throwable)

    /** Runs [block] inside a platform trace section and always closes that section. */
    fun <T> trace(
        name: String,
        block: () -> T,
    ): T
}

/**
 * Process-wide provider for platform capabilities.
 */
internal object RenderSessionPlatformProvider {
    private val registry = RenderSessionPlatformRegistry()

    fun install(platform: RenderSessionPlatform) {
        registry.install(platform)
    }

    fun requirePlatform(): RenderSessionPlatform = registry.requirePlatform()
}

/**
 * One-shot platform registry that prevents mixing multiple renderer/runtime stacks in one process.
 */
internal class RenderSessionPlatformRegistry {
    @Volatile
    private var installedPlatform: RenderSessionPlatform? = null

    fun install(platform: RenderSessionPlatform) {
        synchronized(this) {
            check(installedPlatform == null) {
                "A RenderSession platform is already installed. " +
                    "Platform capabilities must be installed exactly once per process."
            }
            installedPlatform = platform
        }
    }

    fun requirePlatform(): RenderSessionPlatform {
        return checkNotNull(installedPlatform) {
            "RenderSession platform is not installed. Use viewcompose-host-android " +
                "renderInto/setUiContent, or install a complete custom platform with " +
                "installRenderSessionPlatform()."
        }
    }
}
