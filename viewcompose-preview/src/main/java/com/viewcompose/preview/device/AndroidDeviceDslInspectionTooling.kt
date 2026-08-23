package com.viewcompose.preview.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.viewcompose.ui.foundation.RenderSessionInspectionPolicy
import com.viewcompose.ui.foundation.RenderSessionInspectionRegistration
import com.viewcompose.ui.foundation.RenderSessionInspectionTooling
import com.viewcompose.ui.foundation.RenderSessionNodeInspection
import com.viewcompose.ui.foundation.RenderInspectedNodeKind
import com.viewcompose.ui.foundation.RenderNodePlatformTarget
import com.viewcompose.ui.foundation.RenderDiagnosticContext
import com.viewcompose.ui.foundation.RenderSessionRole
import com.viewcompose.ui.node.RenderContainerHandle
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.nativeContainer
import com.viewcompose.ui.tooling.UiSourceCallSite
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal const val DEVICE_DSL_SOURCE_REQUEST_ACTION =
    "com.viewcompose.preview.action.REQUEST_DEVICE_DSL_SOURCE"
internal const val DEVICE_DSL_SOURCE_REQUEST_ID_EXTRA = "request_id"
internal const val DEVICE_DSL_SOURCE_REQUEST_OPERATION_EXTRA = "operation"
internal const val DEVICE_DSL_SOURCE_REQUEST_SESSION_ID_EXTRA = "session_id"
internal const val DEVICE_DSL_SOURCE_REQUEST_NODE_TOKEN_EXTRA = "node_token"
internal const val DEVICE_DSL_SOURCE_REPORT_RELATIVE_PATH =
    "viewcompose/device-dsl-source-v5.json"
internal const val DEVICE_DSL_SOURCE_PROTOCOL_VERSION = 5

/** Optional debug-scoped session-inspection service discovered by the Android Host. */
internal class AndroidDeviceDslInspectionTooling : RenderSessionInspectionTooling {
    override fun inspectionPolicy(
        container: RenderContainerHandle,
        context: RenderDiagnosticContext,
    ): RenderSessionInspectionPolicy {
        val viewGroup = container.nativeContainer as? ViewGroup
            ?: return RenderSessionInspectionPolicy.Ignore
        if (!AndroidDeviceToolingDebugGate.markAndGet(viewGroup.context)) {
            return RenderSessionInspectionPolicy.Ignore
        }
        return if (context.role in SOURCE_CAPTURE_ROLES) {
            RenderSessionInspectionPolicy.TrackSessionAndCaptureSources
        } else {
            RenderSessionInspectionPolicy.TrackSession
        }
    }

    override fun register(
        container: RenderContainerHandle,
        context: RenderDiagnosticContext,
        sourceCandidates: List<List<UiSourceCallSite>>,
        nodeInspection: RenderSessionNodeInspection,
    ): RenderSessionInspectionRegistration? {
        val viewGroup = container.nativeContainer as? ViewGroup ?: return null
        if (!AndroidDeviceToolingDebugGate.markAndGet(viewGroup.context)) return null
        return AndroidDeviceDslSourceRuntime.registry.register(
            container = viewGroup,
            sessionId = context.sessionId.value,
            parentSessionId = context.parentSessionId?.value,
            role = context.role,
            sourceCandidates = sourceCandidates,
            nodeInspection = nodeInspection,
        )
    }

    private companion object {
        val SOURCE_CAPTURE_ROLES = setOf(
            RenderSessionRole.Host,
            RenderSessionRole.NavigationDestination,
            RenderSessionRole.PagerPage,
        )
    }
}

/** Receives one explicit, ADB-authorized request and leaves no recurring View observation behind. */
internal class DeviceDslSourceRequestReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != DEVICE_DSL_SOURCE_REQUEST_ACTION) return
        if (!context.isDebuggableApplication()) return
        AndroidDeviceToolingDebugGate.markAndGet(context)
        val requestId = intent.getStringExtra(DEVICE_DSL_SOURCE_REQUEST_ID_EXTRA)
            ?.takeIf(::isValidDeviceDslRequestNonce)
            ?: return
        val request = DeviceDslToolingRequest.fromIntent(intent, requestId)
        val pendingResult = goAsync()
        val inspect = Runnable {
            runCatching {
                AndroidDeviceDslSourceRuntime.requestHandler.handle(
                    context = context,
                    request = request,
                    onFinished = pendingResult::finish,
                )
            }.onFailure { error ->
                pendingResult.finish()
                Log.w(TAG, "Device DSL source request failed without affecting the application.", error)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            inspect.run()
        } else {
            Handler(Looper.getMainLooper()).post(inspect)
        }
    }
}

internal enum class DeviceDslToolingOperation(val wireValue: String) {
    Source("source"),
    Nodes("nodes"),
    Select("select"),
    Clear("clear"),
    Rejected("rejected"),
    ;

    companion object {
        fun fromWireValue(value: String?): DeviceDslToolingOperation {
            if (value == null) return Source
            return entries.firstOrNull { operation -> operation.wireValue == value } ?: Rejected
        }
    }
}

internal data class DeviceDslToolingRequest(
    val requestId: String,
    val operation: DeviceDslToolingOperation,
    val sessionId: Long? = null,
    val nodeToken: String? = null,
) {
    val valid: Boolean
        get() = when (operation) {
            DeviceDslToolingOperation.Source,
            DeviceDslToolingOperation.Clear,
            -> sessionId == null && nodeToken == null
            DeviceDslToolingOperation.Nodes -> sessionId != null && sessionId > 0L && nodeToken == null
            DeviceDslToolingOperation.Select -> {
                sessionId != null && sessionId > 0L && nodeToken?.let(::isValidNodeToken) == true
            }
            DeviceDslToolingOperation.Rejected -> false
        }

    companion object {
        fun fromIntent(
            intent: Intent,
            requestId: String,
        ): DeviceDslToolingRequest {
            val operation = DeviceDslToolingOperation.fromWireValue(
                intent.getStringExtra(DEVICE_DSL_SOURCE_REQUEST_OPERATION_EXTRA),
            )
            val sessionId = if (intent.hasExtra(DEVICE_DSL_SOURCE_REQUEST_SESSION_ID_EXTRA)) {
                intent.getLongExtra(DEVICE_DSL_SOURCE_REQUEST_SESSION_ID_EXTRA, 0L)
            } else {
                null
            }
            val nodeToken = intent.getStringExtra(DEVICE_DSL_SOURCE_REQUEST_NODE_TOKEN_EXTRA)
            return DeviceDslToolingRequest(
                requestId = requestId,
                operation = operation,
                sessionId = sessionId,
                nodeToken = nodeToken,
            )
        }
    }
}

private object AndroidDeviceDslSourceRuntime {
    val registry = AndroidDeviceDslSourceRegistry()
    val requestHandler = DeviceDslSourceRequestHandler(registry)
}

internal class AndroidDeviceDslSourceRegistry(
    private val processId: () -> Int = Process::myPid,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val sessions = linkedMapOf<Long, DeviceDslSourceSession>()

    fun register(
        container: ViewGroup,
        sessionId: Long,
        parentSessionId: Long?,
        role: RenderSessionRole,
        sourceCandidates: List<List<UiSourceCallSite>>,
        nodeInspection: RenderSessionNodeInspection,
    ): RenderSessionInspectionRegistration? {
        val boundedCandidates = sourceCandidates.boundedSourceCandidates()
        synchronized(this) {
            pruneReleasedSessions()
            while (sessions.size >= MAX_TRACKED_SESSIONS) {
                val removedId = sessions.values.minWithOrNull(
                    compareBy<DeviceDslSourceSession> { session -> session.retentionPriority() }
                        .thenBy { session -> session.id },
                )?.id ?: break
                sessions.remove(removedId)
                AndroidDeviceHighlightOverlay.clear(removedId)
            }
            sessions.remove(sessionId)?.let { AndroidDeviceHighlightOverlay.clear(sessionId) }
            sessions[sessionId] = DeviceDslSourceSession(
                id = sessionId,
                parentId = parentSessionId,
                role = role,
                container = WeakReference(container),
                sourceCandidates = boundedCandidates,
                nodeInspection = nodeInspection,
            )
        }
        return RegistryInspectionRegistration(this, sessionId)
    }

    fun snapshot(
        packageName: String,
        request: DeviceDslToolingRequest,
    ): DeviceDslSourceReport {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Device DSL source inspection must run on the Android main thread."
        }
        return synchronized(this) {
            pruneReleasedSessions()
            val result = when {
                !request.valid -> RegistryRequestResult(
                    sessions = emptyList(),
                    highlight = DeviceDslHighlightResult(DeviceDslHighlightState.Rejected),
                )
                request.operation == DeviceDslToolingOperation.Source -> RegistryRequestResult(
                    sessions = sourceSessionSnapshots(),
                )
                request.operation == DeviceDslToolingOperation.Nodes -> {
                    val session = sessions[checkNotNull(request.sessionId)]
                    if (session == null) {
                        RegistryRequestResult(
                            sessions = emptyList(),
                            highlight = DeviceDslHighlightResult(
                                state = DeviceDslHighlightState.EndedSession,
                                sessionId = request.sessionId,
                            ),
                        )
                    } else {
                        RegistryRequestResult(sessions = listOf(session.snapshotNodes()))
                    }
                }
                request.operation == DeviceDslToolingOperation.Select -> {
                    val sessionId = checkNotNull(request.sessionId)
                    val session = sessions[sessionId]
                    RegistryRequestResult(
                        sessions = emptyList(),
                        highlight = session?.select(checkNotNull(request.nodeToken))
                            ?: DeviceDslHighlightResult(
                                state = DeviceDslHighlightState.EndedSession,
                                sessionId = sessionId,
                                nodeToken = request.nodeToken,
                            ),
                    )
                }
                request.operation == DeviceDslToolingOperation.Clear -> RegistryRequestResult(
                    sessions = emptyList(),
                    highlight = AndroidDeviceHighlightOverlay.clear(),
                )
                else -> RegistryRequestResult(
                    sessions = emptyList(),
                    highlight = DeviceDslHighlightResult(DeviceDslHighlightState.Rejected),
                )
            }
            DeviceDslSourceReport(
                requestId = request.requestId,
                operation = request.operation,
                packageName = packageName,
                processId = processId(),
                generatedAtEpochMillis = currentTimeMillis(),
                sessions = result.sessions,
                highlight = result.highlight,
            )
        }
    }

    private fun sourceSessionSnapshots(): List<DeviceDslSourceSessionSnapshot> {
        return sessions.values.toList()
            .takeLast(MAX_REPORTED_SESSIONS)
            .mapNotNull(DeviceDslSourceSession::snapshotSource)
            .sortedWith(
                compareByDescending<DeviceDslSourceSessionSnapshot> { it.renderingActive }
                    .thenByDescending { it.attachedToWindow && it.shown }
                    .thenByDescending { it.hasWindowFocus }
                    .thenByDescending { it.viewDepth }
                    .thenByDescending { it.sessionId },
            )
    }

    private fun pruneReleasedSessions() {
        val releasedIds = sessions.entries
            .filter { (_, session) -> session.container.get() == null }
            .map { entry -> entry.key }
        releasedIds.forEach { sessionId ->
            sessions.remove(sessionId)
            AndroidDeviceHighlightOverlay.clear(sessionId)
        }
    }

    fun setRenderingActive(
        sessionId: Long,
        active: Boolean,
    ) {
        synchronized(this) {
            sessions[sessionId]?.renderingActive = active
        }
    }

    fun unregister(sessionId: Long) {
        synchronized(this) {
            sessions.remove(sessionId)
            AndroidDeviceHighlightOverlay.clear(sessionId)
        }
    }

    internal fun sessionCountForTest(): Int = synchronized(this) { sessions.size }
}

internal class DeviceDslSourceRequestHandler(
    private val registry: AndroidDeviceDslSourceRegistry,
    private val execute: (Runnable) -> Unit = DeviceDslSourceResponseWriter::execute,
    private val writeResponse: (File, String) -> Unit = DeviceDslSourceResponseWriter::write,
) {
    fun handle(
        context: Context,
        request: DeviceDslToolingRequest,
        onFinished: () -> Unit,
    ) {
        check(isValidDeviceDslRequestNonce(request.requestId)) {
            "Device DSL request nonce is invalid."
        }
        val appContext = context.applicationContext ?: context
        val packageName = appContext.packageName
        val report = registry.snapshot(
            packageName = packageName,
            request = request,
        )
        val target = File(appContext.cacheDir, DEVICE_DSL_SOURCE_REPORT_RELATIVE_PATH)
        execute(
            Runnable {
                try {
                    runCatching { writeResponse(target, report.toJson()) }
                        .onFailure { error ->
                            Log.w(
                                TAG,
                                "Device DSL source response could not be written.",
                                error,
                            )
                        }
                } finally {
                    onFinished()
                }
            },
        )
    }
}

private class RegistryInspectionRegistration(
    private val registry: AndroidDeviceDslSourceRegistry,
    private val sessionId: Long,
) : RenderSessionInspectionRegistration {
    private val disposed = AtomicBoolean(false)

    override fun setRenderingActive(active: Boolean) {
        if (!disposed.get()) {
            registry.setRenderingActive(sessionId, active)
        }
    }

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            registry.unregister(sessionId)
        }
    }
}

private class DeviceDslSourceSession(
    val id: Long,
    val parentId: Long?,
    val role: RenderSessionRole,
    val container: WeakReference<ViewGroup>,
    val sourceCandidates: List<List<UiSourceCallSite>>,
    val nodeInspection: RenderSessionNodeInspection,
    var renderingActive: Boolean = true,
) {
    private val currentTargets = LinkedHashMap<String, CapturedNodeTarget>()
    private val staleTokens = LinkedHashSet<String>()
    private var nodeGeneration: Long = 0L

    fun retentionPriority(): Int {
        val view = container.get() ?: return 0
        val lifecyclePriority = when {
            !renderingActive -> 0
            !view.isAttachedToWindow || !view.isVisibleToUser() -> 1
            else -> 2
        }
        val rolePriority = when (role) {
            RenderSessionRole.LazyItem -> 0
            RenderSessionRole.PagerPage,
            RenderSessionRole.NavigationDestination,
            RenderSessionRole.OverlaySurface,
            RenderSessionRole.Preview,
            -> 1
            RenderSessionRole.Host -> 2
        }
        return lifecyclePriority * 3 + rolePriority
    }

    fun snapshotSource(): DeviceDslSourceSessionSnapshot? {
        val view = container.get() ?: return null
        return DeviceDslSourceSessionSnapshot(
            sessionId = id,
            parentSessionId = parentId,
            role = role,
            renderingActive = renderingActive,
            attachedToWindow = view.isAttachedToWindow,
            shown = view.isVisibleToUser(),
            hasWindowFocus = view.hasWindowFocus(),
            windowVisibility = view.windowVisibility,
            viewDepth = view.depthInHierarchy(),
            sourceCandidates = sourceCandidates,
        )
    }

    fun snapshotNodes(): DeviceDslSourceSessionSnapshot {
        val base = snapshotSource() ?: endedSessionSnapshot()
        val snapshot = nodeInspection.snapshot()
        staleTokens += currentTargets.keys
        while (staleTokens.size > MAX_STALE_NODE_TOKENS) {
            staleTokens.remove(staleTokens.first())
        }
        currentTargets.clear()
        nodeGeneration += 1L
        val nodes = snapshot.nodes.map { node ->
            val token = node.token.value.toString(radix = 36)
            currentTargets[token] = CapturedNodeTarget(
                kind = node.kind,
                target = node.platformTarget,
            )
            DeviceDslNodeSnapshot(
                token = token,
                parentToken = node.parentToken?.value?.toString(radix = 36),
                type = node.type.toWireName(),
                depth = node.depth,
                synthetic = node.kind == RenderInspectedNodeKind.Synthetic,
                sourceCallSites = node.sourceCallSites.boundedNodeSourceCallSites(),
            )
        }
        return base.copy(
            nodes = nodes,
            nodeGeneration = nodeGeneration,
            nodeInspectionSupported = snapshot.supported,
            nodeInspectionEnded = snapshot.ended,
            visitedNodes = snapshot.visitedNodes,
            droppedNodes = snapshot.droppedNodes,
            nodesTruncated = snapshot.truncated,
        )
    }

    fun select(nodeToken: String): DeviceDslHighlightResult {
        val containerView = container.get()
            ?: return DeviceDslHighlightResult(
                state = DeviceDslHighlightState.EndedSession,
                sessionId = id,
                nodeToken = nodeToken,
            )
        val captured = currentTargets[nodeToken]
        if (captured == null) {
            return DeviceDslHighlightResult(
                state = if (nodeToken in staleTokens) {
                    DeviceDslHighlightState.Stale
                } else {
                    DeviceDslHighlightState.Missing
                },
                sessionId = id,
                nodeToken = nodeToken,
            )
        }
        if (captured.kind == RenderInspectedNodeKind.Synthetic) {
            return DeviceDslHighlightResult(
                state = DeviceDslHighlightState.UnsupportedSynthetic,
                sessionId = id,
                nodeToken = nodeToken,
            )
        }
        val view = captured.target.resolve() as? View
            ?: return DeviceDslHighlightResult(
                state = DeviceDslHighlightState.Stale,
                sessionId = id,
                nodeToken = nodeToken,
            )
        if (!view.isDescendantOrSame(containerView)) {
            return DeviceDslHighlightResult(
                state = if (view.isAttachedToWindow) {
                    DeviceDslHighlightState.Recycled
                } else {
                    DeviceDslHighlightState.Stale
                },
                sessionId = id,
                nodeToken = nodeToken,
            )
        }
        val screenBounds = view.screenBounds()
        if (!view.isShown || view.visibility != View.VISIBLE || screenBounds.isEmpty) {
            AndroidDeviceHighlightOverlay.clear(id)
            return DeviceDslHighlightResult(
                state = DeviceDslHighlightState.Hidden,
                sessionId = id,
                nodeToken = nodeToken,
                screenBounds = screenBounds,
            )
        }
        val visibleBounds = Rect()
        if (!view.getGlobalVisibleRect(visibleBounds) || visibleBounds.isEmpty) {
            AndroidDeviceHighlightOverlay.clear(id)
            return DeviceDslHighlightResult(
                state = DeviceDslHighlightState.FullyClipped,
                sessionId = id,
                nodeToken = nodeToken,
                screenBounds = screenBounds,
                visibleBounds = visibleBounds,
            )
        }
        val partiallyClipped = visibleBounds != screenBounds
        val shown = AndroidDeviceHighlightOverlay.show(
            sessionId = id,
            nodeToken = nodeToken,
            selectedView = view,
            overlayBounds = visibleBounds,
        )
        return DeviceDslHighlightResult(
            state = if (!shown) {
                DeviceDslHighlightState.Unsupported
            } else if (partiallyClipped) {
                DeviceDslHighlightState.Clipped
            } else {
                DeviceDslHighlightState.Selected
            },
            sessionId = id,
            nodeToken = nodeToken,
            screenBounds = screenBounds,
            visibleBounds = visibleBounds,
        )
    }

    private fun endedSessionSnapshot(): DeviceDslSourceSessionSnapshot {
        return DeviceDslSourceSessionSnapshot(
            sessionId = id,
            parentSessionId = parentId,
            role = role,
            renderingActive = false,
            attachedToWindow = false,
            shown = false,
            hasWindowFocus = false,
            windowVisibility = View.GONE,
            viewDepth = 0,
            sourceCandidates = sourceCandidates,
            nodeInspectionEnded = true,
        )
    }
}

private data class CapturedNodeTarget(
    val kind: RenderInspectedNodeKind,
    val target: RenderNodePlatformTarget,
)

private data class RegistryRequestResult(
    val sessions: List<DeviceDslSourceSessionSnapshot>,
    val highlight: DeviceDslHighlightResult? = null,
)

internal data class DeviceDslSourceSessionSnapshot(
    val sessionId: Long,
    val parentSessionId: Long?,
    val role: RenderSessionRole,
    val renderingActive: Boolean,
    val attachedToWindow: Boolean,
    val shown: Boolean,
    val hasWindowFocus: Boolean,
    val windowVisibility: Int,
    val viewDepth: Int,
    val sourceCandidates: List<List<UiSourceCallSite>>,
    val nodes: List<DeviceDslNodeSnapshot> = emptyList(),
    val nodeGeneration: Long = 0L,
    val nodeInspectionSupported: Boolean = true,
    val nodeInspectionEnded: Boolean = false,
    val visitedNodes: Int = 0,
    val droppedNodes: Int = 0,
    val nodesTruncated: Boolean = false,
)

internal data class DeviceDslNodeSnapshot(
    val token: String,
    val parentToken: String?,
    val type: String,
    val depth: Int,
    val synthetic: Boolean,
    val sourceCallSites: List<UiSourceCallSite>,
)

internal enum class DeviceDslHighlightState(val wireValue: String) {
    Selected("selected"),
    Clipped("clipped"),
    Missing("missing"),
    Stale("stale"),
    Recycled("recycled"),
    Hidden("hidden"),
    FullyClipped("fully_clipped"),
    UnsupportedSynthetic("unsupported_synthetic"),
    Unsupported("unsupported"),
    EndedSession("ended_session"),
    Rejected("rejected"),
    Cleared("cleared"),
}

internal data class DeviceDslHighlightResult(
    val state: DeviceDslHighlightState,
    val sessionId: Long? = null,
    val nodeToken: String? = null,
    val screenBounds: Rect? = null,
    val visibleBounds: Rect? = null,
)

internal data class DeviceDslSourceReport(
    val requestId: String,
    val operation: DeviceDslToolingOperation,
    val packageName: String,
    val processId: Int,
    val generatedAtEpochMillis: Long,
    val sessions: List<DeviceDslSourceSessionSnapshot>,
    val highlight: DeviceDslHighlightResult? = null,
)

internal fun deviceDslSourceReportJson(
    requestId: String,
    packageName: String,
    processId: Int,
    generatedAtEpochMillis: Long,
    sessions: List<DeviceDslSourceSessionSnapshot>,
): String {
    return DeviceDslSourceReport(
        requestId = requestId,
        operation = DeviceDslToolingOperation.Source,
        packageName = packageName,
        processId = processId,
        generatedAtEpochMillis = generatedAtEpochMillis,
        sessions = sessions,
    ).toJson()
}

private fun DeviceDslSourceReport.toJson(): String = buildString {
    append('{')
    append("\"protocolVersion\":")
    append(DEVICE_DSL_SOURCE_PROTOCOL_VERSION)
    append(",\"requestId\":")
    appendJsonString(requestId)
    append(",\"operation\":")
    appendJsonString(operation.wireValue)
    append(",\"packageName\":")
    appendJsonString(packageName)
    append(",\"processId\":")
    append(processId)
    append(",\"generatedAtEpochMillis\":")
    append(generatedAtEpochMillis)
    append(",\"sessions\":[")
    var emittedSessions = 0
    sessions.forEach { session ->
        val encodedSession = session.toBoundedJson()
        val separatorBytes = if (emittedSessions == 0) 0 else 1
        val closingBytes = 2
        if (
            utf8Size() + separatorBytes + encodedSession.utf8Size() + closingBytes >
            MAX_REPORT_BYTES
        ) {
            return@forEach
        }
        if (emittedSessions > 0) append(',')
        append(encodedSession)
        emittedSessions += 1
    }
    append(']')
    highlight?.let { result ->
        append(",\"highlight\":")
        append(result.toBoundedJson())
    }
    append('}')
}

private fun DeviceDslSourceSessionSnapshot.toBoundedJson(): String = buildString {
    append('{')
    append("\"sessionId\":")
    append(sessionId)
    append(",\"parentSessionId\":")
    append(parentSessionId ?: "null")
    append(",\"role\":")
    appendJsonString(role.name)
    append(",\"renderingActive\":")
    append(renderingActive)
    append(",\"attachedToWindow\":")
    append(attachedToWindow)
    append(",\"shown\":")
    append(shown)
    append(",\"hasWindowFocus\":")
    append(hasWindowFocus)
    append(",\"windowVisibility\":")
    append(windowVisibility)
    append(",\"viewDepth\":")
    append(viewDepth)
    append(",\"nodeGeneration\":")
    append(nodeGeneration)
    append(",\"nodeInspectionSupported\":")
    append(nodeInspectionSupported)
    append(",\"nodeInspectionEnded\":")
    append(nodeInspectionEnded)
    append(",\"visitedNodes\":")
    append(visitedNodes)
    append(",\"droppedNodes\":")
    append(droppedNodes)
    append(",\"sourceCandidates\":[")
    var emittedCandidates = 0
    sourceCandidates.preferredResponseOrder().forEach { candidate ->
        val encodedCandidate = candidate.toBoundedJson() ?: return@forEach
        val separatorBytes = if (emittedCandidates == 0) 0 else 1
        val closingBytes = 2
        if (
            utf8Size() + separatorBytes + encodedCandidate.utf8Size() + closingBytes >
            MAX_SESSION_BYTES
        ) {
            return@forEach
        }
        if (emittedCandidates > 0) append(',')
        append(encodedCandidate)
        emittedCandidates += 1
    }
    append(']')
    append(",\"nodes\":[")
    var emittedNodes = 0
    nodes.forEach { node ->
        val encodedNode = node.toBoundedJson()
        val separatorBytes = if (emittedNodes == 0) 0 else 1
        val closingBytes = 2
        if (utf8Size() + separatorBytes + encodedNode.utf8Size() + closingBytes > MAX_SESSION_BYTES) {
            return@forEach
        }
        if (emittedNodes > 0) append(',')
        append(encodedNode)
        emittedNodes += 1
    }
    append(']')
    append(",\"nodesTruncated\":")
    append(nodesTruncated || emittedNodes < nodes.size)
    append('}')
}

private fun DeviceDslNodeSnapshot.toBoundedJson(): String = buildString {
    append('{')
    append("\"token\":")
    appendJsonString(token)
    append(",\"parentToken\":")
    if (parentToken == null) append("null") else appendJsonString(parentToken)
    append(",\"type\":")
    appendJsonString(type.take(MAX_NODE_STRING_LENGTH))
    append(",\"depth\":")
    append(depth)
    append(",\"synthetic\":")
    append(synthetic)
    append(",\"callSites\":[")
    sourceCallSites.forEachIndexed { index, source ->
        if (index > 0) append(',')
        append(source.toNodeBoundedJson())
    }
    append("]}")
}

private fun DeviceDslHighlightResult.toBoundedJson(): String = buildString {
    append('{')
    append("\"state\":")
    appendJsonString(state.wireValue)
    append(",\"sessionId\":")
    append(sessionId ?: "null")
    append(",\"nodeToken\":")
    if (nodeToken == null) append("null") else appendJsonString(nodeToken)
    append(",\"screenBounds\":")
    appendRect(screenBounds)
    append(",\"visibleBounds\":")
    appendRect(visibleBounds)
    append('}')
}

private fun StringBuilder.appendRect(rect: Rect?) {
    if (rect == null) {
        append("null")
        return
    }
    append("{\"left\":")
    append(rect.left)
    append(",\"top\":")
    append(rect.top)
    append(",\"right\":")
    append(rect.right)
    append(",\"bottom\":")
    append(rect.bottom)
    append('}')
}

private fun List<UiSourceCallSite>.toBoundedJson(): String? {
    val encoded = buildString {
        append("{\"callSites\":[")
        var emittedCallSites = 0
        this@toBoundedJson.forEach { source ->
            val encodedSource = source.toBoundedJson()
            val separatorBytes = if (emittedCallSites == 0) 0 else 1
            val closingBytes = 2
            if (
                utf8Size() + separatorBytes + encodedSource.utf8Size() + closingBytes >
                MAX_CANDIDATE_BYTES
            ) {
                return@forEach
            }
            if (emittedCallSites > 0) append(',')
            append(encodedSource)
            emittedCallSites += 1
        }
        append("]}")
    }
    return encoded.takeIf { json -> json != "{\"callSites\":[]}" }
}

private fun UiSourceCallSite.toBoundedJson(): String = buildString {
    append('{')
    append("\"className\":")
    appendJsonString(className.take(MAX_SOURCE_STRING_LENGTH))
    append(",\"methodName\":")
    appendJsonString(methodName.take(MAX_SOURCE_STRING_LENGTH))
    append(",\"fileName\":")
    appendJsonString(fileName.take(MAX_SOURCE_STRING_LENGTH))
    append(",\"lineNumber\":")
    append(lineNumber)
    append('}')
}

private fun UiSourceCallSite.toNodeBoundedJson(): String = buildString {
    append('{')
    append("\"className\":")
    appendJsonString(className.take(MAX_NODE_STRING_LENGTH))
    append(",\"methodName\":")
    appendJsonString(methodName.take(MAX_NODE_STRING_LENGTH))
    append(",\"fileName\":")
    appendJsonString(fileName.take(MAX_NODE_STRING_LENGTH))
    append(",\"lineNumber\":")
    append(lineNumber)
    append('}')
}

private fun List<UiSourceCallSite>.boundedNodeSourceCallSites(): List<UiSourceCallSite> {
    return asSequence()
        .filter { source -> source.lineNumber > 0 }
        .take(MAX_NODE_SOURCE_CALL_SITES)
        .map { source ->
            source.copy(
                className = source.className.take(MAX_NODE_STRING_LENGTH),
                methodName = source.methodName.take(MAX_NODE_STRING_LENGTH),
                fileName = source.fileName.take(MAX_NODE_STRING_LENGTH),
            )
        }
        .toList()
}

private fun List<List<UiSourceCallSite>>.preferredResponseOrder(): List<List<UiSourceCallSite>> {
    return (takeLast(RESPONSE_RECENT_CANDIDATES) + take(RESPONSE_FIRST_CANDIDATES)).distinct()
}

private fun List<List<UiSourceCallSite>>.boundedSourceCandidates(): List<List<UiSourceCallSite>> {
    return asSequence()
        .map { candidate ->
            candidate.asSequence()
                .filter { source -> source.lineNumber > 0 }
                .take(MAX_SOURCE_CALL_SITES_PER_CANDIDATE)
                .map { source ->
                    source.copy(
                        className = source.className.take(MAX_SOURCE_STRING_LENGTH),
                        methodName = source.methodName.take(MAX_SOURCE_STRING_LENGTH),
                        fileName = source.fileName.take(MAX_SOURCE_STRING_LENGTH),
                    )
                }
                .toList()
        }
        .filter(List<UiSourceCallSite>::isNotEmpty)
        .distinct()
        .take(MAX_SOURCE_CANDIDATES)
        .toList()
}

internal fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

internal fun CharSequence.utf8Size(): Int = utf8Size(0, length)

internal fun CharSequence.utf8Size(
    startIndex: Int,
    endIndex: Int,
): Int {
    require(startIndex in 0..endIndex && endIndex <= length)
    var byteCount = 0
    var index = startIndex
    while (index < endIndex) {
        val character = this[index]
        byteCount += when {
            character.code <= 0x7f -> 1
            character.code <= 0x7ff -> 2
            character.isHighSurrogate() &&
                index + 1 < endIndex &&
                this[index + 1].isLowSurrogate() -> {
                index += 1
                4
            }
            character.isSurrogate() -> 1
            else -> 3
        }
        index += 1
    }
    return byteCount
}

private fun View.depthInHierarchy(): Int {
    var depth = 0
    var current = parent
    while (current is View) {
        depth += 1
        current = current.parent
    }
    return depth
}

private fun View.isVisibleToUser(): Boolean {
    if (!isShown) return false
    val visibleBounds = Rect()
    return getGlobalVisibleRect(visibleBounds) &&
        visibleBounds.width() > 0 &&
        visibleBounds.height() > 0
}

private fun View.isDescendantOrSame(ancestor: View): Boolean {
    var current: Any? = this
    while (current is View) {
        if (current === ancestor) return true
        current = current.parent
    }
    return false
}

private fun View.screenBounds(): Rect {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return Rect(
        location[0],
        location[1],
        location[0] + width,
        location[1] + height,
    )
}

private object AndroidDeviceHighlightOverlay {
    private val handler = Handler(Looper.getMainLooper())
    private var active: ActiveHighlight? = null

    fun show(
        sessionId: Long,
        nodeToken: String,
        selectedView: View,
        overlayBounds: Rect,
    ): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val overlayHost = selectedView.rootView as? ViewGroup ?: return false
        if (!overlayHost.isAttachedToWindow) return false
        clearActive()
        val rootLocation = IntArray(2)
        overlayHost.getLocationOnScreen(rootLocation)
        val localBounds = Rect(overlayBounds).apply {
            offset(-rootLocation[0], -rootLocation[1])
        }
        val drawable = DeviceNodeHighlightDrawable(
            bounds = localBounds,
            density = overlayHost.resources.displayMetrics.density,
        )
        overlayHost.overlay.add(drawable)
        val highlight = ActiveHighlight(
            sessionId = sessionId,
            nodeToken = nodeToken,
            selectedView = WeakReference(selectedView),
            overlayHost = WeakReference(overlayHost),
            drawable = drawable,
        )
        highlight.attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                clearIfActive(highlight)
            }
        }
        selectedView.addOnAttachStateChangeListener(checkNotNull(highlight.attachListener))
        highlight.timeout = Runnable { clearIfActive(highlight) }
        active = highlight
        handler.postDelayed(checkNotNull(highlight.timeout), HIGHLIGHT_DURATION_MILLIS)
        return true
    }

    fun clear(sessionId: Long? = null): DeviceDslHighlightResult {
        check(Looper.myLooper() == Looper.getMainLooper())
        val current = active
        if (sessionId == null || current?.sessionId == sessionId) {
            clearActive()
        }
        return DeviceDslHighlightResult(
            state = DeviceDslHighlightState.Cleared,
            sessionId = current?.sessionId,
            nodeToken = current?.nodeToken,
        )
    }

    private fun clearIfActive(highlight: ActiveHighlight) {
        if (active === highlight) clearActive()
    }

    private fun clearActive() {
        val current = active ?: return
        active = null
        current.timeout?.let(handler::removeCallbacks)
        current.attachListener?.let { listener ->
            current.selectedView.get()?.removeOnAttachStateChangeListener(listener)
        }
        current.overlayHost.get()?.overlay?.remove(current.drawable)
    }

    private class ActiveHighlight(
        val sessionId: Long,
        val nodeToken: String,
        val selectedView: WeakReference<View>,
        val overlayHost: WeakReference<ViewGroup>,
        val drawable: Drawable,
        var timeout: Runnable? = null,
        var attachListener: View.OnAttachStateChangeListener? = null,
    )
}

private class DeviceNodeHighlightDrawable(
    bounds: Rect,
    density: Float,
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(52, 0, 122, 255)
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.rgb(0, 100, 230)
    }

    init {
        this.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        val inset = strokePaint.strokeWidth / 2f
        canvas.drawRect(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset,
            fillPaint,
        )
        canvas.drawRect(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset,
            strokePaint,
        )
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha.coerceIn(0, 255) * 52 / 255
        strokePaint.alpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private fun NodeType.toWireName(): String = when (this) {
    NodeType.Text -> "Text"
    NodeType.TextField -> "TextField"
    NodeType.Checkbox -> "Checkbox"
    NodeType.Switch -> "Switch"
    NodeType.RadioButton -> "RadioButton"
    NodeType.Slider -> "Slider"
    NodeType.LinearProgressIndicator -> "LinearProgressIndicator"
    NodeType.CircularProgressIndicator -> "CircularProgressIndicator"
    NodeType.Button -> "Button"
    NodeType.IconButton -> "IconButton"
    NodeType.Row -> "Row"
    NodeType.Column -> "Column"
    NodeType.Box -> "Box"
    NodeType.Surface -> "Surface"
    NodeType.ConstraintLayout -> "ConstraintLayout"
    NodeType.AnimatedVisibilityHost -> "AnimatedVisibilityHost"
    NodeType.AnimatedContentHost -> "AnimatedContentHost"
    NodeType.AnimatedContentItemHost -> "AnimatedContentItemHost"
    NodeType.AnimatedSizeHost -> "AnimatedSizeHost"
    NodeType.AnimatedBoundsHost -> "AnimatedBoundsHost"
    NodeType.LayoutConstraintHost -> "LayoutConstraintHost"
    NodeType.NestedScrollHost -> "NestedScrollHost"
    NodeType.Spacer -> "Spacer"
    NodeType.Divider -> "Divider"
    NodeType.Canvas -> "Canvas"
    NodeType.Image -> "Image"
    NodeType.AndroidView -> "AndroidView"
    NodeType.LazyColumn -> "LazyColumn"
    NodeType.LazyRow -> "LazyRow"
    NodeType.SegmentedControl -> "SegmentedControl"
    NodeType.ScrollableColumn -> "ScrollableColumn"
    NodeType.ScrollableRow -> "ScrollableRow"
    NodeType.FlowRow -> "FlowRow"
    NodeType.FlowColumn -> "FlowColumn"
    NodeType.NavigationBar -> "NavigationBar"
    NodeType.HorizontalPager -> "HorizontalPager"
    NodeType.VerticalPager -> "VerticalPager"
    NodeType.TabRow -> "TabRow"
    NodeType.LazyVerticalGrid -> "LazyVerticalGrid"
    NodeType.PullToRefresh -> "PullToRefresh"
}

internal fun Context.isDebuggableApplication(): Boolean {
    return runCatching {
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }.getOrDefault(false)
}

internal fun isValidDeviceDslRequestNonce(requestId: String): Boolean {
    return requestId.length in 1..MAX_NONCE_LENGTH &&
        requestId.all { character ->
            character in 'A'..'Z' ||
                character in 'a'..'z' ||
                character in '0'..'9' ||
                character == '.' ||
                character == '_' ||
                character == '-'
        }
}

/** Shared nonce validator retained for the independent animation-timeline protocol. */
internal fun isValidDeviceDslSourceRequestId(requestId: String): Boolean {
    return requestId.length == 32 &&
        requestId.all { character -> character in 'a'..'f' || character in '0'..'9' }
}

private fun isValidNodeToken(token: String): Boolean {
    return token.length in 1..MAX_NODE_TOKEN_LENGTH &&
        token.all { character -> character in '0'..'9' || character in 'a'..'z' }
}

internal object DeviceDslSourceResponseWriter {
    val executor: Executor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ViewCompose-DeviceDslResponse").apply { isDaemon = true }
        }
    }

    fun execute(runnable: Runnable) {
        executor.execute(runnable)
    }

    fun write(
        file: File,
        content: String,
    ) {
        val parent = checkNotNull(file.parentFile)
        check(parent.isDirectory || parent.mkdirs()) {
            "Unable to create device DSL source response directory."
        }
        val temporary = File(parent, ".${file.name}.${System.nanoTime()}.tmp")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.delete()
                check(temporary.renameTo(file)) {
                    "Unable to replace device DSL source response."
                }
            }
        } finally {
            temporary.delete()
        }
    }
}

internal object AndroidDeviceToolingDebugGate {
    private val debuggable = AtomicBoolean(false)

    fun markAndGet(context: Context): Boolean {
        if (debuggable.get()) return true
        val permitted = context.isDebuggableApplication()
        if (permitted) debuggable.set(true)
        return permitted
    }

    fun isDebuggable(): Boolean = debuggable.get()

    internal fun resetForTest() {
        debuggable.set(false)
    }
}

private const val TAG = "ViewCompose"
private const val MAX_SOURCE_CANDIDATES = 32
private const val MAX_SOURCE_CALL_SITES_PER_CANDIDATE = 24
private const val MAX_TRACKED_SESSIONS = 64
private const val MAX_REPORTED_SESSIONS = 64
private const val MAX_REPORT_BYTES = 256 * 1024
private const val MAX_SESSION_BYTES = 48 * 1024
private const val MAX_CANDIDATE_BYTES = 12 * 1024
private const val MAX_SOURCE_STRING_LENGTH = 1024
private const val MAX_NODE_STRING_LENGTH = 256
private const val MAX_NODE_SOURCE_CALL_SITES = 24
private const val MAX_STALE_NODE_TOKENS = 512
private const val MAX_NODE_TOKEN_LENGTH = 32
private const val MAX_NONCE_LENGTH = 128
private const val RESPONSE_FIRST_CANDIDATES = 16
private const val RESPONSE_RECENT_CANDIDATES = 16
private const val HIGHLIGHT_DURATION_MILLIS = 5_000L
