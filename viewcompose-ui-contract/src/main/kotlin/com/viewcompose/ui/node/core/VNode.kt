package com.viewcompose.ui.node

import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.node.spec.NodeSpec
import com.viewcompose.ui.tooling.UiNodeToolingMetadata

/**
 * Stores one immutable semantic node in the declarative UI tree.
 *
 * Renderers consume this platform-neutral model without depending on widget DSL modules. [type] and
 * [spec] must be a supported pair; construction does not validate that registry-level invariant.
 * The [children] list is immutable by contract and must not be mutated after construction.
 *
 * @sample com.viewcompose.ui.samples.vNodeModelSample
 * @property type renderer dispatch key for the platform node family
 * @property key optional semantic identity used for sibling reconciliation
 * @property spec immutable properties whose concrete type must match [type]
 * @property modifier ordered behavior and parent-data chain
 * @property children declarative child nodes in placement order
 * @property environment captured density, locale, and layout direction for this subtree
 * @property observedPropertyId opaque session identity for a directly observed [spec], or `null`
 * when the node participates only in ordinary tree rendering; callers constructing VNodes directly
 * leave this value `null`
 */
data class VNode(
    val type: NodeType,
    val key: Any? = null,
    val spec: NodeSpec,
    val modifier: Modifier = Modifier,
    val children: List<VNode> = emptyList(),
    val environment: UiEnvironmentValues = UiEnvironmentValues.Default,
    val observedPropertyId: Long? = null,
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
