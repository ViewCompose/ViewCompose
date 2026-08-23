package com.viewcompose.ui.foundation

import com.viewcompose.runtime.composition.CompositionDiagnostics
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.tooling.UiNodeToolingMetadata

/**
 * Process-local identity of one logical render session.
 *
 * Values are non-zero and monotonically allocated within one process. They are diagnostic
 * correlation tokens only: do not persist them or use them as application, navigation, item,
 * account, analytics, or accessibility identities.
 *
 * @property value process-local numeric token allocated by the render-session runtime
 */
@JvmInline
value class RenderSessionTraceId internal constructor(val value: Long)

/** Logical ownership role of one render session. */
enum class RenderSessionRole {
    /** Application root hosted directly by an Activity, Fragment, or low-level container. */
    Host,

    /** Static or interactive preview root. */
    Preview,

    /** Independently retained navigation destination. */
    NavigationDestination,

    /** Lazy-list or grid item with independent composition ownership. */
    LazyItem,

    /** Horizontal or vertical pager page with independent composition ownership. */
    PagerPage,

    /** Dialog, popup, or modal surface with independent composition ownership. */
    OverlaySurface,
}

/**
 * Correlation and ordering metadata attached to every [RenderDiagnosticEvent].
 *
 * [sessionId] and [eventSequence] are scoped to the current process. [frameId] is present only
 * when the event can be attributed to one synchronous attempt. [monotonicTimestampNanos] is for
 * elapsed-time comparison and has no wall-clock meaning.
 *
 * @property sessionId logical session that emitted the event
 * @property parentSessionId owning session captured when this session was created, if any
 * @property role logical ownership role of the emitting session
 * @property frameId session-local synchronous frame identity, if proven
 * @property eventSequence monotonically increasing sequence within [sessionId]
 * @property monotonicTimestampNanos platform monotonic time at emission
 */
data class RenderDiagnosticContext(
    val sessionId: RenderSessionTraceId,
    val parentSessionId: RenderSessionTraceId?,
    val role: RenderSessionRole,
    val frameId: Long?,
    val eventSequence: Long,
    val monotonicTimestampNanos: Long,
)

/** Frame detail collected by a [RenderSession]. */
enum class RenderFrameDiagnosticLevel {
    /** Do not build renderer statistics or tree diagnostics. */
    None,

    /** Collect aggregate renderer counters only. */
    Stats,

    /** Collect counters plus bounded tree, patch, warning, and composition diagnostics. */
    Tree,
}

/**
 * Immutable event-selection policy for one diagnostics tree.
 *
 * @property lifecycle whether session start, activity, and end events are emitted
 * @property failures whether structured render failures are emitted
 * @property frameLevel renderer detail included with completed frames
 */
data class RenderDiagnosticCollection(
    val lifecycle: Boolean = true,
    val failures: Boolean = true,
    val frameLevel: RenderFrameDiagnosticLevel = RenderFrameDiagnosticLevel.None,
)

/**
 * Explicit diagnostics configuration installed at a root render session and inherited by children.
 *
 * Passing a new instance to a nested low-level session intentionally starts a new correlation tree.
 * Sink delivery is synchronous and serialized per session. A sink must not re-enter its emitting
 * session; a thrown sink is disabled for that session without changing render recovery.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderDiagnosticsEventSample
 * @property collection immutable event and frame-detail selection
 * @property sink event consumer for this diagnostics tree
 */
class RenderDiagnostics(
    val collection: RenderDiagnosticCollection,
    val sink: RenderDiagnosticsSink,
)

/**
 * Synchronous consumer of correlated render diagnostics.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderDiagnosticsEventSample
 */
fun interface RenderDiagnosticsSink {
    /** Receives one immutable event in session order. */
    fun onEvent(event: RenderDiagnosticEvent)
}

/**
 * One correlated lifecycle, failure, or frame event from a render session.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderDiagnosticsEventSample
 */
sealed interface RenderDiagnosticEvent {
    /** Correlation and ordering metadata for this event. */
    val context: RenderDiagnosticContext
}

/**
 * First subscribed event emitted when a logical session becomes observable.
 *
 * @property context correlation metadata for the started session
 */
data class RenderSessionStarted(
    override val context: RenderDiagnosticContext,
) : RenderDiagnosticEvent

/**
 * Rendering-activity transition for a retained logical session.
 *
 * @property context correlation metadata for the retained session
 * @property active whether frame-scheduled invalidation rendering is now active
 */
data class RenderSessionActivityChanged(
    override val context: RenderDiagnosticContext,
    val active: Boolean,
) : RenderDiagnosticEvent

/**
 * Structured failure emitted after its recovery state is known.
 *
 * @property context correlation metadata attributed to [failure]
 * @property failure immutable failure and recovery record
 */
data class RenderFailureObserved(
    override val context: RenderDiagnosticContext,
    val failure: RenderFailure,
) : RenderDiagnosticEvent

/**
 * Authoritative result of one completed synchronous frame attempt.
 *
 * [stats] is present for [RenderFrameDiagnosticLevel.Stats] and
 * [RenderFrameDiagnosticLevel.Tree]. [tree] is present only for Tree. Rolled-back frames expose
 * neither because candidate diagnostics did not become authoritative.
 *
 * @property context correlation metadata attributed to [report]
 * @property report authoritative synchronous frame result
 * @property stats selected aggregate counters for a committed frame, or `null`
 * @property tree selected bounded tree diagnostics for a committed frame, or `null`
 */
data class RenderFrameCompleted(
    override val context: RenderDiagnosticContext,
    val report: RenderFrameReport,
    val stats: RenderStats?,
    val tree: RenderTreeResult?,
) : RenderDiagnosticEvent

/**
 * Terminal event emitted after logical session cleanup finishes.
 *
 * @property context correlation metadata for the disposed session
 */
data class RenderSessionEnded(
    override val context: RenderDiagnosticContext,
) : RenderDiagnosticEvent

/**
 * Aggregate renderer binding statistics for one frame.
 *
 * @property inserts newly mounted nodes
 * @property reuses existing mounted nodes retained by identity
 * @property removals previously mounted nodes removed from the tree
 * @property reboundNodes nodes whose complete binding ran again
 * @property patchedNodes nodes updated through a targeted patch
 * @property skippedBindings nodes whose binding was proven unchanged
 * @property skippedSubtrees subtrees omitted because their structure and bindings were unchanged
 * @property bindingsByType binding outcomes grouped by declarative node type
 */
data class RenderStats(
    val inserts: Int = 0,
    val reuses: Int = 0,
    val removals: Int = 0,
    val reboundNodes: Int = 0,
    val patchedNodes: Int = 0,
    val skippedBindings: Int = 0,
    val skippedSubtrees: Int = 0,
    val bindingsByType: Map<NodeType, NodeTypeBindingStats> = emptyMap(),
)

/**
 * Binding statistics for one node type.
 *
 * @property rebound full bindings executed
 * @property patched targeted binding patches executed
 * @property skipped unchanged bindings omitted
 */
data class NodeTypeBindingStats(
    val rebound: Int = 0,
    val patched: Int = 0,
    val skipped: Int = 0,
)

/**
 * Size and depth statistics for declarative and mounted trees.
 *
 * @property vnodeCount total declarative nodes in the frame
 * @property mountedNodeCount total renderer nodes after reconciliation
 * @property maxVNodeDepth deepest declarative node, where root depth is one
 * @property maxMountedDepth deepest mounted node, where root depth is one
 */
data class RenderStructureStats(
    val vnodeCount: Int = 0,
    val mountedNodeCount: Int = 0,
    val maxVNodeDepth: Int = 0,
    val maxMountedDepth: Int = 0,
)

/**
 * Complete opt-in diagnostics for one render frame.
 *
 * @property stats aggregate reconciliation and binding counters
 * @property structure declarative and mounted tree size
 * @property warnings non-fatal renderer diagnostics
 * @property tree platform-independent snapshot of the rendered tree
 * @property patches ordered reconciliation operations performed for the frame
 * @property composition recomposition, invalidation, and skip diagnostics
 */
data class RenderTreeResult(
    val stats: RenderStats = RenderStats(),
    val structure: RenderStructureStats = RenderStructureStats(),
    val warnings: List<String> = emptyList(),
    val tree: List<RenderTreeNode> = emptyList(),
    val patches: List<RenderPatchRecord> = emptyList(),
    val composition: CompositionDiagnostics = CompositionDiagnostics(),
)

/**
 * Platform-independent node in a diagnostic render-tree snapshot.
 *
 * @property type declarative node type
 * @property key stable declarative identity, if supplied
 * @property toolingMetadata source mapping captured while the node was built
 * @property children diagnostic child nodes in render order
 */
data class RenderTreeNode(
    val type: NodeType,
    val key: Any?,
    val toolingMetadata: UiNodeToolingMetadata? = null,
    val children: List<RenderTreeNode> = emptyList(),
)

/**
 * One ordered reconciliation operation applied to the mounted tree.
 *
 * @property operation performed reconciliation action
 * @property type affected declarative node type
 * @property key affected node identity, if supplied
 * @property parentKey parent identity after the operation, if supplied
 * @property index child index after the operation
 * @property moved whether a reused node changed sibling position
 * @property detail optional renderer-specific diagnostic detail
 * @property toolingMetadata source mapping for the affected node
 */
data class RenderPatchRecord(
    val operation: RenderPatchOperation,
    val type: NodeType,
    val key: Any?,
    val parentKey: Any?,
    val index: Int,
    val moved: Boolean = false,
    val detail: String? = null,
    val toolingMetadata: UiNodeToolingMetadata? = null,
)

/** Reconciliation operations reported by the renderer. */
enum class RenderPatchOperation {
    /** A new mounted node was inserted. */
    Insert,
    /** A mounted node was removed. */
    Remove,
    /** All bindings for a reused node ran again. */
    Rebind,
    /** A targeted subset of bindings changed. */
    Patch,
    /** The node binding was unchanged while descendants were still considered. */
    SkipSelf,
    /** The node and its complete subtree were unchanged. */
    SkipSubtree,
}
