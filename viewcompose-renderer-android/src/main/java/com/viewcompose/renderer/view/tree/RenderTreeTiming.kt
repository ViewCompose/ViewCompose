package com.viewcompose.renderer.view.tree

import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.tooling.UiNodeTooling

/**
 * Identifies a renderer phase measured by one finite tree-timing request.
 *
 * Reconciliation covers child matching, patch planning, and structural application. Binding covers
 * only direct native View creation, modifier work, full binds, or fine-grained patches. Measure,
 * layout, draw, GPU, decoding, network, database, and external-SDK work are not represented.
 */
enum class RenderTreeTimingPhase {
    /** Child matching, patch planning, and structural application, including nested intervals. */
    Reconciliation,

    /** Direct native View creation, modifier binding, full binding, or fine-grained patching. */
    Binding,
}

/**
 * Describes one privacy-safe node offered to a finite renderer timing collector.
 *
 * [nodeIdentity] is a process-local composition identity, never an application key. A `null` value
 * identifies the renderer's virtual root container. The descriptor contains no text, semantics,
 * listener, URL, native View, or application-owned value.
 *
 * @property nodeIdentity opaque process-local correlation identity, or `null` for the virtual root
 * @property nodeType renderer dispatch type, or `null` for the virtual root
 * @property depth zero-based structural depth where the virtual root is zero
 * @property synthetic whether renderer-neutral rewriting introduced this node
 */
data class RenderTreeTimingSubject(
    val nodeIdentity: Long?,
    val nodeType: NodeType?,
    val depth: Int,
    val synthetic: Boolean,
)

/**
 * Closes one renderer timing interval.
 *
 * [close] runs exactly once on the Android render thread after the observed operation returns or
 * throws. Collector failures are isolated and cannot replace renderer results or exceptions.
 */
fun interface RenderTreeTimingSpan {
    /** Finishes the interval and publishes any bounded measurement owned by the collector. */
    fun close()
}

/**
 * Q3 request-scoped port for finite reconciliation and direct native-binding timing.
 *
 * Calls are serialized on the Android render thread and may nest. The collector owns the single
 * monotonic-nanosecond clock, inclusive/self accounting, caps, truncation, and overhead estimate.
 * Returning `null` declines an interval. Implementations must be fast and non-blocking and cannot
 * invoke application code, perform I/O, retain a native View, or re-enter the renderer.
 *
 * Ordinary rendering supplies no collector and performs zero per-node clock reads or record
 * allocation. This port excludes measure/layout/draw, GPU, RenderThread, SurfaceFlinger, decoding,
 * networking, databases, and external SDK work.
 *
 * @sample com.viewcompose.renderer.samples.renderTreeTimingCollectorSample
 */
fun interface RenderTreeTimingCollector {
    /**
     * Starts one renderer interval.
     *
     * @param subject privacy-safe node or virtual-root descriptor
     * @param phase phase whose exact meaning is defined by [RenderTreeTimingPhase]
     * @return interval closed after the operation, or `null` to omit it
     */
    fun beginInterval(
        subject: RenderTreeTimingSubject,
        phase: RenderTreeTimingPhase,
    ): RenderTreeTimingSpan?
}

internal inline fun <T> RenderTreeTimingCollector?.measureRenderInterval(
    node: VNode?,
    depth: Int,
    phase: RenderTreeTimingPhase,
    block: () -> T,
): T {
    if (this == null) return block()
    val subject = if (node == null) {
        RenderTreeTimingSubject(
            nodeIdentity = null,
            nodeType = null,
            depth = depth,
            synthetic = true,
        )
    } else {
        RenderTreeTimingSubject(
            nodeIdentity = UiNodeTooling.ensureTimingIdentity(node),
            nodeType = node.type,
            depth = depth,
            synthetic = node.type in syntheticTimingNodeTypes,
        )
    }
    val span = try {
        beginInterval(subject, phase)
    } catch (_: Throwable) {
        null
    }
    return try {
        block()
    } finally {
        if (span != null) {
            try {
                span.close()
            } catch (_: Throwable) {
                // Optional diagnostics cannot replace renderer results or application failures.
            }
        }
    }
}

private val syntheticTimingNodeTypes = setOf(
    NodeType.AnimatedSizeHost,
    NodeType.AnimatedBoundsHost,
    NodeType.LayoutConstraintHost,
    NodeType.NestedScrollHost,
)
