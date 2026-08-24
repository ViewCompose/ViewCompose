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
     * @sample com.viewcompose.ui.foundation.samples.renderSessionInspectionToolingSample
     */
    val inspectionTooling: RenderSessionInspectionTooling?
        get() = null

    /** Returns platform monotonic time for event ordering and elapsed-time diagnostics. */
    fun monotonicTimeNanos(): Long = System.nanoTime()

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
 * [inspectionPolicy] runs before a session's initial tree build. [RenderSessionInspectionPolicy]
 * independently chooses whether the session is tracked and whether its first successful frame also
 * captures bounded source-candidate chains. This separation lets request-driven tools inspect
 * high-churn child sessions such as lazy items without paying source capture on their composition
 * path. [register] runs at most once after the first successful native frame for every tracked
 * session, including sessions whose source-candidate list is empty.
 *
 * [register] also receives request-only mounted-node, diagnostic-summary, and timing inspectors.
 * They own the session and native targets weakly, read no history, and perform no traversal or
 * timing until tooling explicitly calls them.
 *
 * Implementations must be optional, fast, and thread-confined to the platform render thread. They
 * may retain a weak container reference until the returned [RenderSessionInspectionRegistration] is
 * disposed, but registration itself stays passive: it cannot install listeners on scroll, global
 * layout, draw, touch, animation-frame, or recomposition paths. Live inspection is initiated by an
 * explicit tooling request. Tooling failures are diagnostic-only and must not become application
 * rendering dependencies.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderSessionInspectionToolingSample
 */
interface RenderSessionInspectionTooling {
    /**
     * Returns the fixed inspection policy for one logical render session.
     *
     * @param container opaque renderer container owned by the candidate session
     * @param context stable session identity, parent, and role; frame and event sequence are absent
     * @return whether to ignore, track, or track with source capture for the session lifetime
     */
    fun inspectionPolicy(
        container: RenderContainerHandle,
        context: RenderDiagnosticContext,
    ): RenderSessionInspectionPolicy

    /**
     * Registers one successfully rendered session and its optional bounded [sourceCandidates].
     *
     * The session calls this method at most once after its first successful native frame. A
     * [RenderSessionInspectionPolicy.TrackSession] session supplies an empty source list. Returning
     * `null` declines the session permanently; an exception is isolated as diagnostics and is not
     * retried on later frames.
     *
     * @param container opaque renderer container owned by the committed session
     * @param context stable session identity, parent, and role shared with runtime diagnostics
     * @param sourceCandidates emission-ordered candidates whose inner lists are nearest-first chains
     * @param nodeInspection request-only bounded mounted-node inspector for this logical session
     * @param diagnosticInspection request-only privacy-safe latest-frame/failure inspector
     * @param timingInspection request-only finite timing control for this logical session
     * @return a lifecycle handle, or `null` to decline this session permanently
     */
    fun register(
        container: RenderContainerHandle,
        context: RenderDiagnosticContext,
        sourceCandidates: List<List<UiSourceCallSite>>,
        nodeInspection: RenderSessionNodeInspection,
        diagnosticInspection: RenderSessionDiagnosticInspection,
        timingInspection: RenderSessionTimingInspection,
    ): RenderSessionInspectionRegistration?
}

/**
 * Selects the one-time setup performed by optional render-session inspection tooling.
 *
 * The policy is resolved once per logical session on the platform render thread. Implementations
 * use [TrackSession] for request-driven node inspection without composition-time source capture,
 * and [TrackSessionAndCaptureSources] only when source navigation is worth that bounded first-frame
 * cost. New enum entries may be added only through a documented compatibility change.
 */
enum class RenderSessionInspectionPolicy {
    /** Installs no inspection state or registration for the session. */
    Ignore,

    /** Tracks lifecycle and mounted-node inspection while capturing no source candidates. */
    TrackSession,

    /** Tracks the session and captures bounded source candidates during its first successful frame. */
    TrackSessionAndCaptureSources,
}

/**
 * Controls the lifecycle of a session registered through [RenderSessionInspectionTooling].
 *
 * Calls are serialized on the platform render thread. [dispose] is terminal and may be called
 * during failure cleanup; implementations should make it idempotent.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderSessionInspectionToolingSample
 */
interface RenderSessionInspectionRegistration {
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
