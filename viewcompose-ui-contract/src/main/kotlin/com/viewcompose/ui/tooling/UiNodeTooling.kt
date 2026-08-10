package com.viewcompose.ui.tooling

import com.viewcompose.ui.node.VNode
import java.util.concurrent.atomic.AtomicLong

/**
 * One runtime call site that contributed to a declarative node.
 *
 * JVM line tables do not retain an absolute source path, so tooling keeps both the declaring class
 * and source filename. Android Studio can resolve the pair against project source roots without
 * coupling runtime modules to IDE APIs.
 *
 * @property className binary JVM name of the declaring class
 * @property methodName JVM method that emitted or contributed to the node
 * @property fileName source filename from the JVM line table
 * @property lineNumber positive one-based source line
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
 *
 * @property nodeId tooling identity unique within the generating runtime process
 * @property callSites bounded source call chain ordered from the nearest user frame outward
 * @property synthetic whether a renderer-created host derived this metadata from a declarative node
 */
data class UiNodeToolingMetadata(
    val nodeId: String,
    val callSites: List<UiSourceCallSite>,
    val synthetic: Boolean = false,
)

/**
 * Opt-in source capture used by previews and diagnostics.
 *
 * Normal application rendering pays only one thread-local lookup per emitted node. Stack traces
 * are allocated only inside [withSourceCapture], [withFirstSourceCapture], or
 * [withSourceCandidateCapture]; IDs and metadata objects are allocated exclusively inside
 * [withSourceCapture].
 */
object UiNodeTooling {
    private val captureDepth = ThreadLocal<Int>()
    private val firstSourceCaptureScopes = ThreadLocal<MutableList<FirstSourceCaptureScope>>()
    private val sourceCandidateCaptureScopes =
        ThreadLocal<MutableList<SourceCandidateCaptureScope>>()
    private val nextNodeId = AtomicLong(1L)

    /**
     * Executes [block] with source capture enabled for the current thread.
     *
     * Nested capture scopes share the active state and restore the prior depth in `finally`.
     * Exceptions from [block] propagate after thread-local state is restored.
     *
     * @param T result type produced by [block]
     * @param block computation whose emitted nodes may receive tooling metadata
     * @return the value produced by [block]
     */
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
     * Captures the source chain of the first emitted [VNode] without annotating the complete tree.
     *
     * [onSourceCaptured] runs synchronously on the thread that emits the first node whose stack
     * contains at least one supported source frame. It runs at most once and is not called when
     * [block] emits no such node. Nested scopes independently observe the first eligible node after
     * their own entry. A surrounding [withSourceCapture] scope continues to attach normal tooling
     * metadata.
     *
     * This mode allocates at most one stack trace per scope and is intended for page- or
     * session-level IDE navigation. Exceptions from [onSourceCaptured] propagate from node emission;
     * the capture scope is still restored before [withFirstSourceCapture] returns or throws.
     *
     * @sample com.viewcompose.ui.samples.firstSourceCaptureSample
     * @param T result type produced by [block]
     * @param onSourceCaptured callback receiving the nearest-first bounded source chain
     * @param block computation whose first emitted node supplies the source chain
     * @return the value produced by [block]
     */
    fun <T> withFirstSourceCapture(
        onSourceCaptured: (List<UiSourceCallSite>) -> Unit,
        block: () -> T,
    ): T {
        val scopes = firstSourceCaptureScopes.get()
            ?: mutableListOf<FirstSourceCaptureScope>().also(firstSourceCaptureScopes::set)
        val scope = FirstSourceCaptureScope(onSourceCaptured)
        scopes += scope
        return try {
            block()
        } finally {
            scopes.remove(scope)
            if (scopes.isEmpty()) {
                firstSourceCaptureScopes.remove()
            }
        }
    }

    /**
     * Q3 tooling boundary that captures bounded source-chain candidates across one tree build
     * without annotating its nodes.
     *
     * Shared scaffolds commonly emit chrome before invoking their content lambda, so the first
     * emitted node alone cannot identify the authored page body. This mode samples at most 64
     * eligible node emissions and retains the first 16 plus the most recent 16 distinct chains.
     * [onSourceCandidatesCaptured] runs once, after [block] returns successfully and capture state
     * is restored. It is not called when [block] emits no eligible nodes or throws.
     *
     * Nested scopes collect independently. A surrounding [withSourceCapture] scope continues to
     * attach normal tooling metadata, while this mode itself never mutates a [VNode].
     *
     * @sample com.viewcompose.ui.samples.sourceCandidateCaptureSample
     * @param T result type produced by [block]
     * @param onSourceCandidatesCaptured callback receiving bounded, emission-ordered source chains
     * @param block computation whose emitted nodes supply source candidates
     * @return the value produced by [block]
     */
    fun <T> withSourceCandidateCapture(
        onSourceCandidatesCaptured: (List<List<UiSourceCallSite>>) -> Unit,
        block: () -> T,
    ): T {
        val scopes = sourceCandidateCaptureScopes.get()
            ?: mutableListOf<SourceCandidateCaptureScope>().also(
                sourceCandidateCaptureScopes::set,
            )
        val scope = SourceCandidateCaptureScope()
        scopes += scope
        val result = try {
            block()
        } finally {
            scopes.remove(scope)
            if (scopes.isEmpty()) {
                sourceCandidateCaptureScopes.remove()
            }
        }
        val candidates = scope.snapshot()
        if (candidates.isNotEmpty()) {
            onSourceCandidatesCaptured(candidates)
        }
        return result
    }

    /**
     * Attaches fresh tooling metadata when capture is active and returns [node] for fluent use.
     *
     * Outside [withSourceCapture], or when no user call site survives filtering, this is a no-op.
     * The same node instance is always returned; tooling metadata does not affect VNode equality.
     *
     * @param node declarative node to annotate
     * @return the same [node] instance
     */
    fun attach(node: VNode): VNode {
        val captureMetadata = (captureDepth.get() ?: 0) > 0
        val pendingFirstCaptures = firstSourceCaptureScopes.get()
            ?.filterNot(FirstSourceCaptureScope::captured)
            .orEmpty()
        val pendingCandidateCaptures = sourceCandidateCaptureScopes.get()
            ?.filter(SourceCandidateCaptureScope::shouldSample)
            .orEmpty()
        if (
            !captureMetadata &&
            pendingFirstCaptures.isEmpty() &&
            pendingCandidateCaptures.isEmpty()
        ) {
            return node
        }
        val callSites = captureCallSites()
        if (callSites.isEmpty()) return node
        pendingFirstCaptures.forEach { scope -> scope.captured = true }
        pendingFirstCaptures.forEach { scope -> scope.onSourceCaptured(callSites) }
        pendingCandidateCaptures.forEach { scope -> scope.record(callSites) }
        if (!captureMetadata) return node
        node.toolingMetadata = UiNodeToolingMetadata(
            nodeId = "node-${nextNodeId.getAndIncrement().toString(36)}",
            callSites = callSites,
        )
        return node
    }

    /**
     * Copies non-semantic metadata from [source] to a semantic node copy.
     *
     * @param target copied node that should retain tooling identity
     * @param source original node that owns the metadata
     * @return the same [target] instance
     */
    fun inheritCopy(
        target: VNode,
        source: VNode,
    ): VNode {
        target.toolingMetadata = source.toolingMetadata
        return target
    }

    /**
     * Associates a renderer-created host with [source] using a derived synthetic identity.
     *
     * If [source] has no metadata, this is a no-op. [discriminator] is appended to the source ID so
     * callers must choose a stable value that distinguishes synthetic siblings.
     *
     * @param target renderer-created node to annotate
     * @param source declarative source node
     * @param discriminator stable suffix distinguishing this synthetic host
     * @return the same [target] instance
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

    /**
     * Returns non-semantic metadata currently attached to [node].
     *
     * @param node node to inspect
     * @return metadata, or `null` when capture did not annotate the node
     */
    fun metadataOf(node: VNode): UiNodeToolingMetadata? = node.toolingMetadata

    /**
     * Captures a bounded user-source call chain for the current thread.
     *
     * Runtime, renderer, reflection, and Kotlin infrastructure frames are filtered. Outside an
     * active capture scope this function returns an empty list without allocating a stack trace.
     *
     * @return nearest-first source call sites, limited to 32 entries
     */
    fun captureCallSites(): List<UiSourceCallSite> {
        val captureMetadata = (captureDepth.get() ?: 0) > 0
        val hasPendingFirstCapture = firstSourceCaptureScopes.get()
            ?.any { scope -> !scope.captured }
            ?: false
        val hasPendingCandidateCapture = sourceCandidateCaptureScopes.get()
            ?.any(SourceCandidateCaptureScope::shouldSample)
            ?: false
        if (!captureMetadata && !hasPendingFirstCapture && !hasPendingCandidateCapture) {
            return emptyList()
        }
        return selectSourceCallSites(Throwable().stackTrace.asSequence())
    }

    internal fun selectSourceCallSites(
        stackTrace: Sequence<StackTraceElement>,
    ): List<UiSourceCallSite> {
        return stackTrace
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
            INFRASTRUCTURE_CLASS_PREFIXES.any(className::startsWith) ||
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

    private const val MAX_CALL_SITES = 32
    private val INFRASTRUCTURE_CLASS_PREFIXES = listOf(
        "com.viewcompose.runtime.",
        "com.viewcompose.host.android.runtime.",
    )
    private const val UI_TREE_BUILDER_CLASS = "com.viewcompose.ui.foundation.UiTreeBuilder"
    private const val COMPOSER_LITE_CLASS =
        "com.viewcompose.runtime.composition.ComposerLite"
    private const val COMPOSER_CONTEXT_CLASS =
        "com.viewcompose.ui.foundation.ComposerContext"
    private const val RENDER_SESSION_CLASS =
        "com.viewcompose.ui.foundation.RenderSession"
}

private class FirstSourceCaptureScope(
    val onSourceCaptured: (List<UiSourceCallSite>) -> Unit,
) {
    var captured: Boolean = false
}

private class SourceCandidateCaptureScope {
    private val firstCandidates = linkedSetOf<List<UiSourceCallSite>>()
    private val recentCandidates = linkedSetOf<List<UiSourceCallSite>>()
    private var sampledEmissions = 0

    fun shouldSample(): Boolean = sampledEmissions < MAX_SAMPLED_EMISSIONS

    fun record(callSites: List<UiSourceCallSite>) {
        if (!shouldSample()) return
        sampledEmissions += 1
        if (callSites in firstCandidates) return
        if (firstCandidates.size < RETAINED_EDGE_CANDIDATES) {
            firstCandidates += callSites
            return
        }
        recentCandidates.remove(callSites)
        recentCandidates += callSites
        while (recentCandidates.size > RETAINED_EDGE_CANDIDATES) {
            val oldest = recentCandidates.iterator().next()
            recentCandidates.remove(oldest)
        }
    }

    fun snapshot(): List<List<UiSourceCallSite>> {
        return (firstCandidates + recentCandidates).distinct()
    }

    private companion object {
        const val MAX_SAMPLED_EMISSIONS = 64
        const val RETAINED_EDGE_CANDIDATES = 16
    }
}
