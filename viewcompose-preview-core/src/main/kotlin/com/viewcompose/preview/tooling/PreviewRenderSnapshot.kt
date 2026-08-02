package com.viewcompose.preview.tooling

import kotlinx.serialization.Serializable

/**
 * Platform-neutral diagnostics emitted beside a static preview image.
 *
 * The model deliberately contains only stable strings and primitive values. It can cross a
 * worker-process boundary without exposing Android Views, VNodes, Throwable instances, or other
 * runtime-owned objects.
 *
 * @property stats reconciliation and binding counters for the captured render
 * @property structure aggregate virtual and mounted tree sizes
 * @property warnings human-readable non-fatal render warnings
 * @property tree renderer-neutral VNode tree
 * @property nativeViewTree measured Android View tree
 * @property layoutDiagnostics structured post-layout problems
 * @property patches reconciliation operations applied during the render
 * @property composition recomposition scope diagnostics
 */
@Serializable
data class PreviewRenderSnapshot(
    val stats: PreviewRenderStats = PreviewRenderStats(),
    val structure: PreviewRenderStructure = PreviewRenderStructure(),
    val warnings: List<String> = emptyList(),
    val tree: List<PreviewRenderTreeNode> = emptyList(),
    val nativeViewTree: List<PreviewNativeViewNode> = emptyList(),
    val layoutDiagnostics: List<PreviewLayoutDiagnostic> = emptyList(),
    val patches: List<PreviewPatchRecord> = emptyList(),
    val composition: PreviewCompositionSnapshot = PreviewCompositionSnapshot(),
)

/**
 * Aggregate reconciliation and native-binding counters.
 *
 * @property inserts newly mounted nodes
 * @property reuses nodes retained by reconciliation
 * @property removals unmounted nodes
 * @property reboundNodes nodes receiving a full native bind
 * @property patchedNodes nodes receiving a targeted patch
 * @property skippedBindings native bindings skipped as unchanged
 * @property skippedSubtrees subtrees skipped by composition/reconciliation
 * @property bindingsByType per-node-type binding counters
 */
@Serializable
data class PreviewRenderStats(
    val inserts: Int = 0,
    val reuses: Int = 0,
    val removals: Int = 0,
    val reboundNodes: Int = 0,
    val patchedNodes: Int = 0,
    val skippedBindings: Int = 0,
    val skippedSubtrees: Int = 0,
    val bindingsByType: Map<String, PreviewNodeBindingStats> = emptyMap(),
)

/**
 * Native-binding counters for one node type.
 *
 * @property rebound full binding count
 * @property patched targeted patch count
 * @property skipped unchanged binding count
 */
@Serializable
data class PreviewNodeBindingStats(
    val rebound: Int = 0,
    val patched: Int = 0,
    val skipped: Int = 0,
)

/**
 * Aggregate sizes and depths of one rendered structure.
 *
 * @property vnodeCount virtual nodes emitted by the DSL
 * @property mountedNodeCount renderer-owned mounted nodes
 * @property maxVNodeDepth maximum zero-based virtual-tree depth
 * @property maxMountedDepth maximum zero-based mounted-tree depth
 */
@Serializable
data class PreviewRenderStructure(
    val vnodeCount: Int = 0,
    val mountedNodeCount: Int = 0,
    val maxVNodeDepth: Int = 0,
    val maxMountedDepth: Int = 0,
)

/**
 * Portable recursive representation of one rendered VNode.
 *
 * @property type stable node type name
 * @property key optional reconciliation key
 * @property nodeId optional render-session node identity used to correlate diagnostics
 * @property sourceCallSites ordered JVM line-table candidates for source navigation
 * @property synthetic whether tooling introduced the node rather than user DSL
 * @property children ordered rendered children
 */
@Serializable
data class PreviewRenderTreeNode(
    val type: String,
    val key: String? = null,
    val nodeId: String? = null,
    val sourceCallSites: List<PreviewSourceCallSite> = emptyList(),
    val synthetic: Boolean = false,
    val children: List<PreviewRenderTreeNode> = emptyList(),
)

/**
 * One JVM line-table call site associated with a preview node.
 *
 * @property className binary class containing the call
 * @property methodName JVM method containing the call
 * @property fileName source-file name from debug metadata
 * @property lineNumber one-based source line, or a JVM-provided non-positive sentinel when absent
 */
@Serializable
data class PreviewSourceCallSite(
    val className: String,
    val methodName: String,
    val fileName: String,
    val lineNumber: Int,
)

/**
 * One laid-out Android View captured after the static preview root completes measure and layout.
 *
 * [bounds] are absolute pixel coordinates relative to the preview root. Keeping this separate from
 * the VNode tree makes wrapper Views, lazy-container children, and platform-owned descendants
 * visible without coupling the stable preview protocol to renderer internals.
 *
 * @property className Android View binary class name
 * @property bounds absolute laid-out bounds in preview-root pixels
 * @property measuredWidth measured width in physical pixels
 * @property measuredHeight measured height in physical pixels
 * @property visibility stable Android visibility label
 * @property visibleBounds bounds remaining after ancestor/root clipping, or `null` when unavailable
 * @property clippingState relationship between [bounds] and [visibleBounds]
 * @property clippingAncestorClassName first clipping ancestor class when known
 * @property clippingAncestorNodeId first clipping ancestor node identity when known
 * @property clippingExpected whether clipping is intentional for the captured container
 * @property properties bounded common View properties safe for IDE presentation
 * @property nodeId optional renderer node identity
 * @property sourceCallSites ordered JVM source candidates
 * @property synthetic whether the View has no direct user DSL node
 * @property children ordered native child Views
 */
@Serializable
data class PreviewNativeViewNode(
    val className: String,
    val bounds: PreviewLayoutBounds,
    val measuredWidth: Int,
    val measuredHeight: Int,
    val visibility: String,
    val visibleBounds: PreviewLayoutBounds? = null,
    val clippingState: PreviewClippingState = PreviewClippingState.NotClipped,
    val clippingAncestorClassName: String? = null,
    val clippingAncestorNodeId: String? = null,
    val clippingExpected: Boolean = false,
    val properties: Map<String, String> = emptyMap(),
    val nodeId: String? = null,
    val sourceCallSites: List<PreviewSourceCallSite> = emptyList(),
    val synthetic: Boolean = false,
    val children: List<PreviewNativeViewNode> = emptyList(),
)

/** Degree to which a native View's laid-out bounds remain visible. */
@Serializable
enum class PreviewClippingState {
    NotClipped,
    PartiallyClipped,
    FullyClipped,
}

/**
 * Integer pixel bounds relative to the preview root.
 *
 * @property left inclusive left coordinate
 * @property top inclusive top coordinate
 * @property right exclusive right coordinate
 * @property bottom exclusive bottom coordinate
 */
@Serializable
data class PreviewLayoutBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * One source-aware layout condition captured after Android measure and layout complete.
 *
 * The protocol stores structured facts instead of a preformatted message so IDE clients can
 * localize and present the same diagnostic in different ways.
 *
 * @property kind stable diagnostic category
 * @property severity diagnostic importance
 * @property className affected Android View class
 * @property bounds affected laid-out pixel bounds
 * @property visibleBounds visible portion after clipping, when available
 * @property clippingAncestorClassName first clipping ancestor class when known
 * @property clippingAncestorNodeId first clipping ancestor node identity when known
 * @property clippingExpected whether the container intentionally clips this node
 * @property metrics kind-specific integer facts
 * @property nodeId optional renderer node identity
 * @property sourceCallSites ordered source-navigation candidates
 * @property synthetic whether no direct user DSL node owns the View
 */
@Serializable
data class PreviewLayoutDiagnostic(
    val kind: PreviewLayoutDiagnosticKind,
    val severity: PreviewDiagnosticSeverity,
    val className: String,
    val bounds: PreviewLayoutBounds,
    val visibleBounds: PreviewLayoutBounds? = null,
    val clippingAncestorClassName: String? = null,
    val clippingAncestorNodeId: String? = null,
    val clippingExpected: Boolean = false,
    val metrics: Map<String, Int> = emptyMap(),
    val nodeId: String? = null,
    val sourceCallSites: List<PreviewSourceCallSite> = emptyList(),
    val synthetic: Boolean = false,
)

/** Stable post-layout problem categories understood by IDE clients. */
@Serializable
enum class PreviewLayoutDiagnosticKind {
    ZeroLayoutSize,
    PartiallyClipped,
    FullyClipped,
    TextEllipsized,
    TextContentClipped,
}

/**
 * One portable reconciliation operation captured for diagnostics.
 *
 * @property operation stable operation label such as insert, reuse, move, patch, or remove
 * @property type affected node type
 * @property key optional affected node key
 * @property parentKey optional parent key
 * @property index target sibling index
 * @property moved whether reuse also changed sibling position
 * @property detail optional implementation-neutral explanation
 * @property nodeId optional renderer node identity
 * @property sourceCallSites ordered source-navigation candidates
 * @property synthetic whether tooling introduced the affected node
 */
@Serializable
data class PreviewPatchRecord(
    val operation: String,
    val type: String,
    val key: String? = null,
    val parentKey: String? = null,
    val index: Int,
    val moved: Boolean = false,
    val detail: String? = null,
    val nodeId: String? = null,
    val sourceCallSites: List<PreviewSourceCallSite> = emptyList(),
    val synthetic: Boolean = false,
)

/**
 * Aggregate recomposition diagnostics for one rendered frame.
 *
 * @property invalidatedScopeCount scopes marked invalid before composition
 * @property recomposedScopeCount scopes executed again
 * @property skippedScopeCount scopes proven unchanged and skipped
 * @property scopes ordered per-scope records
 */
@Serializable
data class PreviewCompositionSnapshot(
    val invalidatedScopeCount: Int = 0,
    val recomposedScopeCount: Int = 0,
    val skippedScopeCount: Int = 0,
    val scopes: List<PreviewRecomposeScope> = emptyList(),
)

/**
 * One source-aware composition scope outcome.
 *
 * @property path stable runtime scope path
 * @property signature structural scope signature
 * @property depth zero-based composition depth
 * @property reasons invalidation or execution explanations
 * @property recomposed whether the scope executed in the captured frame
 * @property skipped whether the scope was explicitly skipped
 * @property locals visible composition-local names and printable values
 * @property sourceCallSites ordered source-navigation candidates
 */
@Serializable
data class PreviewRecomposeScope(
    val path: String,
    val signature: String,
    val depth: Int,
    val reasons: List<String> = emptyList(),
    val recomposed: Boolean,
    val skipped: Boolean,
    val locals: List<PreviewCompositionLocal> = emptyList(),
    val sourceCallSites: List<PreviewSourceCallSite> = emptyList(),
)

/**
 * Printable composition-local diagnostic value.
 *
 * @property name local key or diagnostic name
 * @property value bounded human-readable value captured by the renderer
 */
@Serializable
data class PreviewCompositionLocal(
    val name: String,
    val value: String,
)
