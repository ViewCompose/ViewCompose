package com.viewcompose.renderer.view.tree

/**
 * Structural size snapshot for declarative and mounted renderer trees.
 *
 * @property vnodeCount total declarative nodes across all roots
 * @property mountedNodeCount total mounted renderer nodes across all roots
 * @property maxVNodeDepth deepest declarative node, where a root has depth one
 * @property maxMountedDepth deepest mounted node, where a root has depth one
 */
data class RenderStructureStats(
    val vnodeCount: Int = 0,
    val mountedNodeCount: Int = 0,
    val maxVNodeDepth: Int = 0,
    val maxMountedDepth: Int = 0,
) {
    /** Computes aggregate structure statistics from matching declarative and mounted roots. */
    companion object {
        /**
         * Computes structural size from declarative nodes and mounted nodes.
         *
         * @param nodes immutable declarative roots for the committed frame
         * @param mountedNodes mounted roots produced for the same frame
         * @return aggregate node counts and maximum depths; empty lists produce zeroes
         */
        fun from(
            nodes: List<com.viewcompose.ui.node.VNode>,
            mountedNodes: List<MountedNode>,
        ): RenderStructureStats {
            return RenderStructureStats(
                vnodeCount = nodes.sumOf { it.deepNodeCount() },
                mountedNodeCount = mountedNodes.sumOf { it.deepNodeCount() },
                maxVNodeDepth = nodes.maxOfOrNull { it.deepDepth() } ?: 0,
                maxMountedDepth = mountedNodes.maxOfOrNull { it.deepDepth() } ?: 0,
            )
        }
    }
}

private fun com.viewcompose.ui.node.VNode.deepNodeCount(): Int {
    return 1 + children.sumOf { it.deepNodeCount() }
}

private fun com.viewcompose.ui.node.VNode.deepDepth(): Int {
    return 1 + (children.maxOfOrNull { it.deepDepth() } ?: 0)
}

private fun MountedNode.deepNodeCount(): Int {
    return 1 + children.sumOf { it.deepNodeCount() }
}

private fun MountedNode.deepDepth(): Int {
    return 1 + (children.maxOfOrNull { it.deepDepth() } ?: 0)
}
