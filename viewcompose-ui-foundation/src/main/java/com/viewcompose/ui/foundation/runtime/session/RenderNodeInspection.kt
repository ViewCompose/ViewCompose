package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.tooling.UiSourceCallSite
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

/**
 * Opaque process-local identity for one node in a requested mounted-tree snapshot.
 *
 * A token is allocated only while optional tooling handles an explicit request. It contains no
 * application key or content and is valid only for the snapshot and logical render session that
 * produced it. Replacement, cross-owner reuse, a newer snapshot, session disposal, or process
 * recreation makes it stale. Callers must not persist it or use it as application identity.
 *
 * @property value process-local opaque value; its numeric order has no semantic meaning
 */
@JvmInline
value class RenderNodeToken internal constructor(val value: Long)

/** Describes whether an inspected mounted node is application-authored or renderer infrastructure. */
enum class RenderInspectedNodeKind {
    /** A mounted node corresponding directly to a declarative node. */
    Declarative,

    /** A renderer-created host that cannot be selected as application content. */
    Synthetic,
}

/**
 * Weak platform target associated with one requested mounted-node snapshot entry.
 *
 * [resolve] runs only on the owning platform render thread during an explicit tooling request. It
 * returns the current native object when the mounted node still exists, or `null` after release.
 * Implementations must not retain an Activity, window, View, or mounted tree strongly. Callers may
 * inspect the returned object synchronously but must retain it weakly if a bounded follow-up
 * request needs it.
 */
fun interface RenderNodePlatformTarget {
    /**
     * Returns the current platform object without extending its lifetime.
     *
     * The caller invokes this query synchronously on the owning platform render thread. The result
     * is live for that call, is not cached, and is `null` after the mounted node is released.
     *
     * @return current weakly held platform target, or `null` when it no longer exists
     */
    fun resolve(): Any?
}

/**
 * One privacy-safe entry in a requested mounted-node snapshot.
 *
 * The flat list is emitted in depth-first declaration order. [parentToken] refers only to another
 * retained entry in the same snapshot. Application keys, text, semantics, state, Local values, and
 * arbitrary `toString()` output are deliberately absent. [sourceCallSites] is empty unless source
 * metadata was already captured by optional tooling.
 *
 * [platformTarget] is a weak, request-scoped bridge for downstream platform tooling. It is not a
 * stable application handle and must never be persisted or exposed through a wire protocol.
 *
 * @property token opaque identity unique to this requested snapshot
 * @property parentToken retained parent identity, or `null` for a root or truncated parent
 * @property type declarative renderer dispatch type
 * @property depth zero-based depth within the mounted session tree
 * @property kind whether this entry is declarative content or synthetic renderer infrastructure
 * @property sourceCallSites bounded nearest-first source chain, when available
 * @property platformTarget weak platform resolver used only by optional downstream tooling
 */
data class RenderInspectedNode(
    val token: RenderNodeToken,
    val parentToken: RenderNodeToken?,
    val type: NodeType,
    val depth: Int,
    val kind: RenderInspectedNodeKind,
    val sourceCallSites: List<UiSourceCallSite>,
    val platformTarget: RenderNodePlatformTarget,
)

/**
 * Bounded result of one explicit mounted-node inspection request.
 *
 * [nodes] contains at most 512 entries and inspection visits at most 2,048 mounted nodes to depth
 * 64. [truncated] reports any limit or renderer truncation. An unsupported renderer returns an
 * empty list with [supported] `false`; an ended session returns [ended] `true`.
 *
 * @property nodes retained depth-first entries
 * @property visitedNodes mounted nodes visited before stopping
 * @property droppedNodes visited nodes omitted after the retained-entry cap
 * @property truncated whether a depth, visit, retained-entry, or renderer limit was reached
 * @property supported whether the installed renderer implements mounted-node inspection
 * @property ended whether the logical render session had already ended
 */
data class RenderNodeInspectionSnapshot(
    val nodes: List<RenderInspectedNode>,
    val visitedNodes: Int,
    val droppedNodes: Int,
    val truncated: Boolean,
    val supported: Boolean,
    val ended: Boolean,
)

/**
 * Q3 request-only mounted-node inspection owned by one logical render session.
 *
 * [snapshot] must run on the owning platform render thread. It performs a finite traversal only
 * for the call, allocates fresh process-local tokens, and installs no layout, scroll, draw, touch,
 * frame, or recomposition listener. The returned platform targets are weak. Snapshot or target
 * failure is diagnostic-only and must not alter rendering, focus, accessibility, input, layout, or
 * application callbacks.
 *
 * A newer snapshot invalidates the earlier token set for downstream tooling. Session disposal is
 * terminal and yields an ended result. This contract has no persistence, restoration, I/O,
 * cancellation, or background-thread behavior.
 *
 * @sample com.viewcompose.ui.foundation.samples.renderSessionNodeInspectionSample
 */
interface RenderSessionNodeInspection {
    /**
     * Captures one bounded snapshot of the currently mounted session tree.
     *
     * Each call invalidates the preceding token set and performs its finite traversal synchronously
     * on the owning platform render thread. Diagnostic failure does not mutate the render session.
     *
     * @return fresh request-time snapshot, or an unsupported or ended result
     */
    fun snapshot(): RenderNodeInspectionSnapshot
}

/** Renderer-neutral weak holder used by the session implementation. */
internal class RenderSessionMountedNodeState {
    var mountedNodes: List<Any> = emptyList()
    var ended: Boolean = false
}

/** Request-only inspection implementation that never strongly owns the session or mounted tree. */
internal class DefaultRenderSessionNodeInspection(
    state: RenderSessionMountedNodeState,
    private val renderEngine: CoreRenderEngine,
) : RenderSessionNodeInspection {
    private val state = WeakReference(state)

    override fun snapshot(): RenderNodeInspectionSnapshot {
        val currentState = state.get()
            ?: return endedNodeInspectionSnapshot()
        if (currentState.ended) return endedNodeInspectionSnapshot()
        val core = renderEngine.inspectMountedNodes(
            mountedNodes = currentState.mountedNodes,
            maxVisitedNodes = MAX_VISITED_NODES,
            maxReturnedNodes = MAX_RETURNED_NODES,
            maxDepth = MAX_NODE_DEPTH,
        )
        if (!core.supported) {
            return RenderNodeInspectionSnapshot(
                nodes = emptyList(),
                visitedNodes = core.visitedNodes,
                droppedNodes = core.droppedNodes,
                truncated = core.truncated,
                supported = false,
                ended = false,
            )
        }
        val nodes = ArrayList<RenderInspectedNode>(core.nodes.size)
        val tokensByIndex = HashMap<Int, RenderNodeToken>(core.nodes.size)
        core.nodes.forEachIndexed { index, node ->
            val token = RenderNodeToken(nextRenderNodeToken())
            tokensByIndex[index] = token
            nodes += RenderInspectedNode(
                token = token,
                parentToken = node.parentIndex?.let(tokensByIndex::get),
                type = node.type,
                depth = node.depth,
                kind = if (node.synthetic) {
                    RenderInspectedNodeKind.Synthetic
                } else {
                    RenderInspectedNodeKind.Declarative
                },
                sourceCallSites = node.sourceCallSites,
                platformTarget = node.platformTarget,
            )
        }
        return RenderNodeInspectionSnapshot(
            nodes = nodes,
            visitedNodes = core.visitedNodes,
            droppedNodes = core.droppedNodes,
            truncated = core.truncated,
            supported = true,
            ended = false,
        )
    }
}

private fun endedNodeInspectionSnapshot(): RenderNodeInspectionSnapshot {
    return RenderNodeInspectionSnapshot(
        nodes = emptyList(),
        visitedNodes = 0,
        droppedNodes = 0,
        truncated = false,
        supported = true,
        ended = true,
    )
}

private fun nextRenderNodeToken(): Long {
    while (true) {
        val value = renderNodeTokens.getAndIncrement()
        if (value != 0L) return value
    }
}

private val renderNodeTokens = AtomicLong(1L)
private const val MAX_VISITED_NODES = 2_048
private const val MAX_RETURNED_NODES = 512
private const val MAX_NODE_DEPTH = 64
