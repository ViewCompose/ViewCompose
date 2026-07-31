package com.viewcompose.preview.tooling

import kotlinx.serialization.Serializable

/**
 * Platform-neutral diagnostics emitted beside a static preview image.
 *
 * The model deliberately contains only stable strings and primitive values. It can cross a
 * worker-process boundary without exposing Android Views, VNodes, Throwable instances, or other
 * runtime-owned objects.
 */
@Serializable
data class PreviewRenderSnapshot(
    val stats: PreviewRenderStats = PreviewRenderStats(),
    val structure: PreviewRenderStructure = PreviewRenderStructure(),
    val warnings: List<String> = emptyList(),
    val tree: List<PreviewRenderTreeNode> = emptyList(),
    val nativeViewTree: List<PreviewNativeViewNode> = emptyList(),
    val patches: List<PreviewPatchRecord> = emptyList(),
    val composition: PreviewCompositionSnapshot = PreviewCompositionSnapshot(),
)

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

@Serializable
data class PreviewNodeBindingStats(
    val rebound: Int = 0,
    val patched: Int = 0,
    val skipped: Int = 0,
)

@Serializable
data class PreviewRenderStructure(
    val vnodeCount: Int = 0,
    val mountedNodeCount: Int = 0,
    val maxVNodeDepth: Int = 0,
    val maxMountedDepth: Int = 0,
)

@Serializable
data class PreviewRenderTreeNode(
    val type: String,
    val key: String? = null,
    val children: List<PreviewRenderTreeNode> = emptyList(),
)

/**
 * One laid-out Android View captured after the static preview root completes measure and layout.
 *
 * [bounds] are absolute pixel coordinates relative to the preview root. Keeping this separate from
 * the VNode tree makes wrapper Views, lazy-container children, and platform-owned descendants
 * visible without coupling the stable preview protocol to renderer internals.
 */
@Serializable
data class PreviewNativeViewNode(
    val className: String,
    val bounds: PreviewLayoutBounds,
    val measuredWidth: Int,
    val measuredHeight: Int,
    val visibility: String,
    val children: List<PreviewNativeViewNode> = emptyList(),
)

@Serializable
data class PreviewLayoutBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

@Serializable
data class PreviewPatchRecord(
    val operation: String,
    val type: String,
    val key: String? = null,
    val parentKey: String? = null,
    val index: Int,
    val moved: Boolean = false,
    val detail: String? = null,
)

@Serializable
data class PreviewCompositionSnapshot(
    val invalidatedScopeCount: Int = 0,
    val recomposedScopeCount: Int = 0,
    val skippedScopeCount: Int = 0,
    val scopes: List<PreviewRecomposeScope> = emptyList(),
)

@Serializable
data class PreviewRecomposeScope(
    val path: String,
    val signature: String,
    val depth: Int,
    val reasons: List<String> = emptyList(),
    val recomposed: Boolean,
    val skipped: Boolean,
    val locals: List<PreviewCompositionLocal> = emptyList(),
)

@Serializable
data class PreviewCompositionLocal(
    val name: String,
    val value: String,
)
