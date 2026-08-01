package com.viewcompose.ui.node

import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.spec.NodeSpec
import com.viewcompose.ui.tooling.UiNodeToolingMetadata

/**
 * 声明式 UI 树中的不可变虚拟节点。
 * Immutable virtual node in the declarative UI tree.
 *
 * renderer 只依赖 type/spec/modifier/children 这组平台中立数据，不依赖 widget DSL。
 * Renderers depend only on this platform-neutral type/spec/modifier/children data, not on widget DSLs.
 */
data class VNode(
    val type: NodeType,
    val key: Any? = null,
    val spec: NodeSpec,
    val modifier: Modifier = Modifier,
    val children: List<VNode> = emptyList(),
    val environment: UiEnvironmentValues = UiEnvironmentValues.Default,
) {
    /**
     * Tooling-only identity and source information.
     *
     * This property deliberately lives outside the data-class constructor so it cannot affect
     * semantic equality, hash codes, reconciliation, or normal composition reuse.
     */
    @Volatile
    internal var toolingMetadata: UiNodeToolingMetadata? = null
}
