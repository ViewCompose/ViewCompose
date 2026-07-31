package com.viewcompose.ui.tooling

import com.viewcompose.ui.node.VNode
import java.util.concurrent.atomic.AtomicLong

/**
 * One runtime call site that contributed to a declarative node.
 *
 * JVM line tables do not retain an absolute source path, so tooling keeps both the declaring class
 * and source filename. Android Studio can resolve the pair against project source roots without
 * coupling runtime modules to IDE APIs.
 */
data class UiSourceCallSite(
    val className: String,
    val methodName: String,
    val fileName: String,
    val lineNumber: Int,
)

/**
 * Non-semantic metadata attached to a [VNode] only while a tooling capture is active.
 *
 * [nodeId] identifies one logical node inside a committed tooling frame. [callSites] preserves a
 * bounded call chain instead of prematurely choosing between a reusable component definition and
 * its caller. Synthetic renderer hosts inherit the chain while receiving a derived identity.
 */
data class UiNodeToolingMetadata(
    val nodeId: String,
    val callSites: List<UiSourceCallSite>,
    val synthetic: Boolean = false,
)

/**
 * Opt-in source capture used by previews and diagnostics.
 *
 * Normal application rendering pays only one thread-local lookup per emitted node. Stack traces,
 * IDs, and metadata objects are allocated exclusively inside [withSourceCapture].
 */
object UiNodeTooling {
    private val captureDepth = ThreadLocal<Int>()
    private val nextNodeId = AtomicLong(1L)

    fun <T> withSourceCapture(block: () -> T): T {
        val previousDepth = captureDepth.get() ?: 0
        captureDepth.set(previousDepth + 1)
        return try {
            block()
        } finally {
            if (previousDepth == 0) {
                captureDepth.remove()
            } else {
                captureDepth.set(previousDepth)
            }
        }
    }

    /**
     * Attaches fresh tooling metadata when capture is active and returns [node] for fluent use.
     */
    fun attach(node: VNode): VNode {
        if ((captureDepth.get() ?: 0) == 0) return node
        val callSites = captureCallSites()
        if (callSites.isEmpty()) return node
        node.toolingMetadata = UiNodeToolingMetadata(
            nodeId = "node-${nextNodeId.getAndIncrement().toString(36)}",
            callSites = callSites,
        )
        return node
    }

    /**
     * Copies the non-semantic identity from [source] to a semantic copy of that node.
     */
    fun inheritCopy(
        target: VNode,
        source: VNode,
    ): VNode {
        target.toolingMetadata = source.toolingMetadata
        return target
    }

    /**
     * Associates a renderer-created host with [source] without pretending it is the same node.
     */
    fun inheritSynthetic(
        target: VNode,
        source: VNode,
        discriminator: String,
    ): VNode {
        val sourceMetadata = source.toolingMetadata ?: return target
        target.toolingMetadata = sourceMetadata.copy(
            nodeId = "${sourceMetadata.nodeId}/$discriminator",
            synthetic = true,
        )
        return target
    }

    fun metadataOf(node: VNode): UiNodeToolingMetadata? = node.toolingMetadata

    fun captureCallSites(): List<UiSourceCallSite> {
        if ((captureDepth.get() ?: 0) == 0) return emptyList()
        return Throwable().stackTrace
            .asSequence()
            .filter { frame ->
                frame.lineNumber > 0 &&
                    frame.fileName != null &&
                    !frame.isToolingInfrastructure()
            }
            .take(MAX_CALL_SITES)
            .map { frame ->
                UiSourceCallSite(
                    className = frame.className,
                    methodName = frame.methodName,
                    fileName = checkNotNull(frame.fileName),
                    lineNumber = frame.lineNumber,
                )
            }
            .toList()
    }

    private fun StackTraceElement.isToolingInfrastructure(): Boolean {
        return className == UiNodeTooling::class.java.name ||
            className.startsWith("${UiNodeTooling::class.java.name}\$") ||
            className == UI_TREE_BUILDER_CLASS ||
            className.startsWith("$UI_TREE_BUILDER_CLASS\$") ||
            className == COMPOSER_LITE_CLASS ||
            className.startsWith("$COMPOSER_LITE_CLASS\$") ||
            className == COMPOSER_CONTEXT_CLASS ||
            className.startsWith("$COMPOSER_CONTEXT_CLASS\$") ||
            className == RENDER_SESSION_CLASS ||
            className.startsWith("$RENDER_SESSION_CLASS\$") ||
            className.startsWith("java.lang.reflect.") ||
            className.startsWith("jdk.internal.reflect.") ||
            className.startsWith("kotlin.jvm.internal.")
    }

    private const val MAX_CALL_SITES = 16
    private const val UI_TREE_BUILDER_CLASS = "com.viewcompose.widget.core.UiTreeBuilder"
    private const val COMPOSER_LITE_CLASS =
        "com.viewcompose.runtime.composition.ComposerLite"
    private const val COMPOSER_CONTEXT_CLASS =
        "com.viewcompose.widget.core.ComposerContext"
    private const val RENDER_SESSION_CLASS =
        "com.viewcompose.widget.core.RenderSession"
}
