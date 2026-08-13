package com.viewcompose.ui.foundation

import com.viewcompose.ui.focus.FocusManager
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.tooling.UiSourceCallSite
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
    /**
     * Optional Q3 source-session integration used by development tooling.
     *
     * The default is `null`, which performs no source capture. A platform implementation may
     * return a process-scoped adapter that owns only tooling metadata; application rendering must
     * not depend on its presence.
     *
     * @sample com.viewcompose.ui.foundation.samples.renderSessionSourceToolingSample
     */
    val sourceTooling: RenderSessionSourceTooling?
        get() = null

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
 * Q3 platform adapter for tracking the source and visibility lifetime of nested render sessions.
 *
 * [shouldCapture] runs before a session's initial tree build. When it returns `true`, the session
 * captures bounded candidate chains from eligible VNodes and calls [register] only after the frame
 * establishes a native tree. Candidate chains let tooling distinguish reusable page chrome from
 * content DSL without retaining node metadata. Lazy-list and pager item sessions use the same path,
 * allowing platform tooling to prefer the deepest container that is actually visible.
 *
 * Implementations must be optional, fast, and thread-confined to the platform render thread. They
 * may retain a weak container reference until the returned [RenderSessionSourceRegistration] is
 * disposed, but registration itself stays passive: it cannot install listeners on scroll, global
 * layout, draw, touch, animation-frame, or recomposition paths. Live inspection is initiated by an
 * explicit tooling request. Tooling failures are diagnostic-only and must not become application
 * rendering dependencies.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderSessionSourceToolingSample
 */
interface RenderSessionSourceTooling {
    /**
     * Returns whether [container] belongs to a process where source capture is permitted.
     *
     * @param container opaque renderer container owned by the candidate session
     * @return `true` to capture bounded source candidates during the session's initial tree build
     */
    fun shouldCapture(container: RenderContainerHandle): Boolean

    /**
     * Registers one successfully rendered session and its bounded [sourceCandidates].
     *
     * @param container opaque renderer container owned by the committed session
     * @param sourceCandidates emission-ordered candidates whose inner lists are nearest-first chains
     * @return a lifecycle handle, or `null` when the session should not be reported
     */
    fun register(
        container: RenderContainerHandle,
        sourceCandidates: List<List<UiSourceCallSite>>,
    ): RenderSessionSourceRegistration?
}

/**
 * Lifecycle handle for a source session registered through [RenderSessionSourceTooling].
 *
 * Calls are serialized on the platform render thread. [dispose] is terminal and may be called
 * during failure cleanup; implementations should make it idempotent.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderSessionSourceToolingSample
 */
interface RenderSessionSourceRegistration {
    /**
     * Updates whether frame-scheduled rendering is active for the owning session.
     *
     * @param active whether scheduled invalidation rendering is currently enabled
     */
    fun setRenderingActive(active: Boolean)

    /** Removes the owning session and releases all retained tooling state. */
    fun dispose()
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
