package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.VNode

/**
 * Narrow render-engine contract installed by a host module.
 *
 * UI Foundation depends on this protocol instead of a concrete renderer or native container,
 * preserving the module boundary and allowing a host to choose its engine/runtime stack once per
 * process.
 */
interface CoreRenderEngine {
    /**
     * Renders the VNode tree into the container and returns new mounted nodes plus optional diagnostics/commit work.
     */
    fun renderInto(
        container: RenderContainerHandle,
        previousMountedNodes: List<Any>,
        nodes: List<VNode>,
        diagnosticLevel: RenderFrameDiagnosticLevel,
    ): CoreRenderFrame

    /**
     * Applies one exact-target batch whose VNodes differ only by [VNode.spec].
     *
     * Implementations must validate every target before mutation and restore the complete previous
     * batch when any patch fails. Engines that do not implement direct observed-property patching
     * reject the operation instead of silently falling back to full-tree rendering.
     *
     * This is a Q3 host-integration API.
     *
     * @sample com.viewcompose.ui.foundation.samples.observedPropertyEngineSample
     * @param container unchanged render container that owns [mountedNodes]
     * @param mountedNodes exact roots returned by the preceding successful full frame
     * @param patches non-empty, declaration-ordered, unique exact-target property replacements
     * @param diagnosticLevel renderer detail required by the owning diagnostics collection
     * @return property-only commit work and diagnostics; mounted roots and targets remain unchanged
     * @throws IllegalStateException when this engine does not support observed transactions or a
     * target no longer belongs to the committed frame
     * @throws Throwable when validation or native mutation fails; the engine must restore every
     * earlier mutation before propagating the failure
     */
    fun patchObservedProperties(
        container: RenderContainerHandle,
        mountedNodes: List<Any>,
        patches: List<CoreObservedPropertyPatch>,
        diagnosticLevel: RenderFrameDiagnosticLevel,
    ): CoreObservedPropertyFrame {
        error("${this::class.qualifiedName} does not support observed-property transactions.")
    }

    /**
     * Disposes renderer nodes that are already mounted.
     */
    fun disposeMounted(
        container: RenderContainerHandle,
        mountedNodes: List<Any>,
    ): List<CoreRenderCommitFailure>

    /**
     * Resets and detaches a mounted tree for reuse by another logical owner.
     *
     * The default declines reuse. Implementations return `null` when any node cannot be reset
     * safely; the caller then disposes the mounted tree normally.
     */
    fun detachMountedForReuse(
        container: RenderContainerHandle,
        mountedNodes: List<Any>,
    ): CoreReusableRenderTree? = null

    /** Attaches a compatible detached tree and returns its opaque mounted nodes. */
    fun attachReusableMounted(
        container: RenderContainerHandle,
        tree: CoreReusableRenderTree,
    ): List<Any> = emptyList()

    /** Permanently releases a detached tree after cache eviction. */
    fun releaseReusableMounted(tree: CoreReusableRenderTree): List<CoreRenderCommitFailure> = emptyList()
}

/** Opaque renderer-owned native tree detached from a render container and logical session. */
interface CoreReusableRenderTree

/**
 * Output prepared by a renderer for one frame.
 *
 * [commitEffects] run only after composition commit. [commitFailures] contains failures already
 * encountered while establishing [mountedNodes]; that native tree cannot be rolled back by core.
 *
 * @property mountedNodes opaque renderer nodes that become the previous tree for the next frame
 * @property observedPropertyTargets all exact targets after a full frame
 * @property renderStats aggregate binding statistics
 * @property renderResult optional detailed diagnostics when collection was requested
 * @property commitEffects native mutations deferred until composition commit
 * @property commitFailures native failures captured while rendering the tree
 */
data class CoreRenderFrame(
    val mountedNodes: List<Any>,
    val observedPropertyTargets: Map<Long, CoreObservedPropertyTarget> = emptyMap(),
    val renderStats: RenderStats = RenderStats(),
    val renderResult: RenderTreeResult? = null,
    val commitEffects: List<CoreRenderCommitEffect> = emptyList(),
    val commitFailures: List<CoreRenderCommitFailure> = emptyList(),
)

/**
 * Lightweight renderer result for one property-only transaction.
 *
 * Mounted roots and target handles cannot change in this transaction. The owning RenderSession
 * advances the existing [CoreObservedPropertyTarget] snapshots only after this result succeeds,
 * avoiding a replacement root list and target map on every property frame.
 *
 * @property renderStats aggregate binding statistics
 * @property renderResult optional detailed diagnostics when collection was requested
 * @property commitEffects native mutations deferred until session commit
 * @property commitFailures native failures captured after the property batch became visible
 */
data class CoreObservedPropertyFrame(
    val renderStats: RenderStats = RenderStats(),
    val renderResult: RenderTreeResult? = null,
    val commitEffects: List<CoreRenderCommitEffect> = emptyList(),
    val commitFailures: List<CoreRenderCommitFailure> = emptyList(),
)

/**
 * Renderer-owned exact target recorded for one committed observed property.
 *
 * This is a Q3 host-integration model.
 *
 * @sample com.viewcompose.ui.foundation.samples.observedPropertyEngineSample
 * @property handle opaque renderer-owned target; UI Foundation never casts or replaces it
 * @property node last committed VNode snapshot associated with [handle]
 */
class CoreObservedPropertyTarget(
    val handle: Any,
    node: VNode,
) {
    /** Last committed VNode snapshot associated with [handle]. */
    var node: VNode = node
        internal set

    internal fun advance(
        previous: VNode,
        next: VNode,
    ) {
        check(node === previous) { "Observed-property target no longer owns its previous VNode." }
        node = next
    }
}

/**
 * One renderer-neutral property-only VNode replacement in an atomic batch.
 *
 * This is a Q3 host-integration model.
 *
 * @sample com.viewcompose.ui.foundation.samples.observedPropertyEngineSample
 */
data class CoreObservedPropertyPatch(
    /** Session-owned observed-property identity, unique within [CoreRenderFrame]. */
    val id: Long,
    /** Exact renderer target published by the preceding committed full frame. */
    val target: CoreObservedPropertyTarget,
    /** VNode snapshot currently owned by [target]. */
    val previous: VNode,
    /** Candidate VNode that differs from [previous] only by its concrete NodeSpec value. */
    val next: VNode,
)

/**
 * Native mutation deferred by the renderer until the session commit phase.
 *
 * @property operation native interoperability operation represented by this effect
 * @property nodeKey declarative node identity used for diagnostics, if available
 * @property commit operation invoked once after composition commit
 */
data class CoreRenderCommitEffect(
    val operation: RenderFailureOperation,
    val nodeKey: Any?,
    val commit: () -> Unit,
)

/**
 * Native failure captured by the renderer while rendering or disposing mounted nodes.
 *
 * @property operation related native interoperability operation, if known
 * @property nodeKey declarative node identity used for diagnostics, if available
 * @property cause original platform failure
 */
data class CoreRenderCommitFailure(
    val operation: RenderFailureOperation?,
    val nodeKey: Any?,
    val cause: Throwable,
)
