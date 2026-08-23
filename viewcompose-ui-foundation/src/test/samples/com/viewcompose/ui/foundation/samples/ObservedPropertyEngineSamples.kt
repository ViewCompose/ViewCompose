package com.viewcompose.ui.foundation.samples

import com.viewcompose.ui.foundation.CoreObservedPropertyPatch
import com.viewcompose.ui.foundation.CoreMountedNodeInspection
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel
import com.viewcompose.ui.foundation.CoreRenderEngine
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.tooling.UiNodeTooling
import com.viewcompose.ui.node.spec.NodeSpec

fun mountedNodeInspectionEngineSample(
    engine: CoreRenderEngine,
    mountedNodes: List<Any>,
): CoreMountedNodeInspection {
    val snapshot = engine.inspectMountedNodes(
        mountedNodes = mountedNodes,
        maxVisitedNodes = 2_048,
        maxReturnedNodes = 512,
        maxDepth = 64,
    )
    if (!snapshot.supported) return snapshot
    snapshot.nodes.firstOrNull { !it.synthetic }?.let { node ->
        // Resolve only synchronously on the platform render thread; retain no native target.
        println("${node.depth}:${node.type}:${node.platformTarget.resolve()?.javaClass?.name}")
    }
    return snapshot
}

fun observedPropertyEngineSample(
    engine: CoreRenderEngine,
    container: RenderContainerHandle,
    previousMountedNodes: List<Any>,
    previousFrame: com.viewcompose.ui.foundation.CoreRenderFrame,
    propertyId: Long,
    nextSpec: NodeSpec,
) {
    val target = checkNotNull(previousFrame.observedPropertyTargets[propertyId])
    val nextNode = UiNodeTooling.inheritCopy(
        target = target.node.copy(spec = nextSpec),
        source = target.node,
    )
    engine.patchObservedProperties(
        container = container,
        mountedNodes = previousMountedNodes,
        patches = listOf(
            CoreObservedPropertyPatch(
                id = propertyId,
                target = target,
                previous = target.node,
                next = nextNode,
            ),
        ),
        diagnosticLevel = RenderFrameDiagnosticLevel.None,
    )
}
