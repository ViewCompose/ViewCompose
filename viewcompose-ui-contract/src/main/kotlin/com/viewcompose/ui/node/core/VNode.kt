package com.viewcompose.ui.node

import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.spec.NodeSpec

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
)
