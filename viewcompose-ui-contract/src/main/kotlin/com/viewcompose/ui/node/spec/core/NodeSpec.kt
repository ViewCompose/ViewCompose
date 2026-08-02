package com.viewcompose.ui.node.spec

/**
 * Marks an immutable, platform-neutral property snapshot for one node type.
 *
 * A concrete spec is paired with a compatible `NodeType` by the DSL and renderer registry. Specs
 * participate in VNode equality and therefore must describe render semantics without mutable
 * platform objects unless the concrete contract explicitly defines an identity boundary.
 */
interface NodeSpec
