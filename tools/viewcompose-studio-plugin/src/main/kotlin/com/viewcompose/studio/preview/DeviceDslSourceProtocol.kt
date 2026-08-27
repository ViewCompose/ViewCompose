package com.viewcompose.studio.preview

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.StringReader
import java.nio.charset.StandardCharsets

internal const val DEVICE_DSL_SOURCE_REPORT_PATH =
    "cache/viewcompose/device-dsl-source-v7.json"
internal const val DEVICE_DSL_SOURCE_REQUEST_ACTION =
    "com.viewcompose.preview.action.REQUEST_DEVICE_DSL_SOURCE"
internal const val DEVICE_DSL_SOURCE_REQUEST_ID_EXTRA = "request_id"
internal const val DEVICE_DSL_SOURCE_REQUEST_OPERATION_EXTRA = "operation"
internal const val DEVICE_DSL_SOURCE_REQUEST_SESSION_ID_EXTRA = "session_id"
internal const val DEVICE_DSL_SOURCE_REQUEST_NODE_TOKEN_EXTRA = "node_token"
internal const val DEVICE_DSL_TIMING_PHASES_EXTRA = "timing_phases"
internal const val DEVICE_DSL_TIMING_FUTURE_LAZY_ITEM_EXTRA = "timing_future_lazy_item"
internal const val DEVICE_DSL_SOURCE_PROTOCOL_VERSION = 7

internal enum class StudioDeviceDslOperation(val wireValue: String) {
    Source("source"),
    Nodes("nodes"),
    Select("select"),
    Clear("clear"),
    Timing("timing"),
    Rejected("rejected"),
}

internal data class StudioDeviceDslSourceReport(
    val requestId: String,
    val operation: StudioDeviceDslOperation,
    val packageName: String,
    val processId: Int,
    val generatedAtEpochMillis: Long,
    val sessions: List<StudioDeviceDslSourceSession>,
    val highlight: StudioDeviceDslHighlightResult?,
    val timing: StudioDeviceDslTimingSnapshot?,
)

internal enum class StudioDeviceDslTimingStartStatus(val wireValue: String) {
    Started("started"),
    AlreadyActive("already_active"),
    EndedSession("ended_session"),
}

internal enum class StudioDeviceDslTimingArmEndReason(val wireValue: String) {
    Matched("matched"),
    DurationLimit("duration_limit"),
    ParentEnded("parent_ended"),
    Superseded("superseded"),
    CaptureRejected("capture_rejected"),
}

internal data class StudioDeviceDslTimingArmSnapshot(
    val parentSessionId: Long,
    val matchedSessionId: Long?,
    val matchedPhysicalContainerToken: Long?,
    val startedAtNanos: Long,
    val endedAtNanos: Long?,
    val endReason: StudioDeviceDslTimingArmEndReason?,
)

internal enum class StudioDeviceDslTimingPhase(val wireValue: String) {
    Composition("composition"),
    Reconciliation("reconciliation"),
    Binding("binding"),
}

internal enum class StudioDeviceDslTimingInclusion(val wireValue: String) {
    Inclusive("inclusive"),
    Self("self"),
    Direct("direct"),
}

internal data class StudioDeviceDslTimingRecord(
    val frameId: Long,
    val nodeToken: String,
    val parentNodeToken: String?,
    val nodeType: String?,
    val depth: Int,
    val synthetic: Boolean,
    val phase: StudioDeviceDslTimingPhase,
    val inclusion: StudioDeviceDslTimingInclusion,
    val durationNanos: Long,
    val repetitions: Long,
    val truncated: Boolean,
    val sourceCallSites: List<StudioPreviewSourceCallSite>,
)

internal data class StudioDeviceDslTimingResult(
    val sessionId: Long,
    val parentSessionId: Long?,
    val role: StudioRenderSessionRole,
    val completedFrames: Int,
    val startedAtNanos: Long,
    val endedAtNanos: Long?,
    val attemptedClockReads: Long,
    val retainedClockReads: Long,
    val emptyPairOverheadNanos: Long,
    val droppedTimedNodes: Int,
    val droppedRecords: Int,
    val droppedStrings: Int,
    val truncated: Boolean,
    val complete: Boolean,
    val endReason: String?,
    val unsupportedDomains: List<String>,
    val records: List<StudioDeviceDslTimingRecord>,
    val recordsTruncated: Boolean,
)

internal data class StudioDeviceDslTimingSnapshot(
    val startStatus: StudioDeviceDslTimingStartStatus,
    val arm: StudioDeviceDslTimingArmSnapshot?,
    val result: StudioDeviceDslTimingResult?,
)

internal enum class StudioDeviceDslFrameStatus(val wireValue: String) {
    Committed("committed"),
    RolledBack("rolled_back"),
}

internal enum class StudioRenderFailurePhase(val wireValue: String) {
    CompositionPrepare("composition_prepare"),
    ObservedPropertyPrepare("observed_property_prepare"),
    ObservedPropertyRender("observed_property_render"),
    ObservedPropertyCommit("observed_property_commit"),
    ViewTreeRender("view_tree_render"),
    ViewTreeCommit("view_tree_commit"),
    CompositionCommit("composition_commit"),
    CompositionSideEffect("composition_side_effect"),
    NativeViewCommit("native_view_commit"),
    OverlayCommit("overlay_commit"),
    DiagnosticsSink("diagnostics_sink"),
    CompositionCoroutine("composition_coroutine"),
    SessionDispose("session_dispose"),
}

internal enum class StudioRenderFailureRecovery(val wireValue: String) {
    PreviousFrameRestored("previous_frame_restored"),
    FrameCommitted("frame_committed"),
    FrameUnchanged("frame_unchanged"),
    SessionDisposed("session_disposed"),
}

internal enum class StudioRenderFailureOperation(val wireValue: String) {
    AndroidViewFactory("android_view_factory"),
    AndroidViewUpdate("android_view_update"),
    AndroidViewReset("android_view_reset"),
    AndroidViewCommit("android_view_commit"),
    AndroidViewRelease("android_view_release"),
}

internal data class StudioDeviceDslFailure(
    val frameId: Long?,
    val phase: StudioRenderFailurePhase,
    val recovery: StudioRenderFailureRecovery,
    val operation: StudioRenderFailureOperation?,
    val exceptionType: String,
)

internal data class StudioDeviceDslFrame(
    val frameId: Long,
    val status: StudioDeviceDslFrameStatus,
    val failures: List<StudioDeviceDslFailure>,
    val droppedFailures: Int,
)

internal data class StudioDeviceDslSessionDiagnostics(
    val sessionId: Long,
    val parentSessionId: Long?,
    val role: StudioRenderSessionRole,
    val renderingActive: Boolean,
    val committedFrameId: Long?,
    val latestFrame: StudioDeviceDslFrame?,
    val latestFailure: StudioDeviceDslFailure?,
    val ended: Boolean,
)

internal data class StudioDeviceDslSourceSession(
    val sessionId: Long,
    val parentSessionId: Long?,
    val role: StudioRenderSessionRole,
    val physicalContainerToken: Long?,
    val renderingActive: Boolean,
    val attachedToWindow: Boolean,
    val shown: Boolean,
    val hasWindowFocus: Boolean,
    val windowVisibility: Int,
    val viewDepth: Int,
    val diagnostics: StudioDeviceDslSessionDiagnostics?,
    val sourceCandidates: List<List<StudioPreviewSourceCallSite>>,
    val nodes: List<StudioDeviceDslNode>,
    val nodeGeneration: Long,
    val nodeInspectionSupported: Boolean,
    val nodeInspectionEnded: Boolean,
    val visitedNodes: Int,
    val droppedNodes: Int,
    val nodesTruncated: Boolean,
)

internal data class StudioDeviceDslNode(
    val token: String,
    val parentToken: String?,
    val type: String,
    val depth: Int,
    val synthetic: Boolean,
    val sourceCallSites: List<StudioPreviewSourceCallSite>,
)

internal enum class StudioDeviceDslHighlightState(val wireValue: String) {
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

internal data class StudioDeviceDslBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal data class StudioDeviceDslHighlightResult(
    val state: StudioDeviceDslHighlightState,
    val sessionId: Long?,
    val nodeToken: String?,
    val screenBounds: StudioDeviceDslBounds?,
    val visibleBounds: StudioDeviceDslBounds?,
)

internal enum class StudioRenderSessionRole {
    Host,
    Preview,
    NavigationDestination,
    LazyItem,
    PagerPage,
    OverlaySurface,
}

internal fun parseDeviceDslSourceReport(json: String): StudioDeviceDslSourceReport {
    require(json.toByteArray(StandardCharsets.UTF_8).size <= MAX_REPORT_BYTES) {
        "Device DSL source report exceeds $MAX_REPORT_BYTES bytes."
    }
    val root = StringReader(json).use { reader ->
        JsonParser.parseReader(reader)
    }.requiredObject("root")
    val protocolVersion = root.requiredInt("protocolVersion")
    require(protocolVersion == DEVICE_DSL_SOURCE_PROTOCOL_VERSION) {
        "Unsupported device DSL source protocol $protocolVersion."
    }
    val sessions = root.optionalArray("sessions")
        .take(MAX_REPORTED_SESSIONS)
        .map { element ->
            val session = element.requiredObject("session")
            val sessionId = session.requiredLong("sessionId").also { require(it > 0L) }
            val parentSessionId = session.optionalLong("parentSessionId")?.also {
                require(it > 0L && it != sessionId)
            }
            val role = session.requiredRenderSessionRole("role")
            val diagnostics = session.optionalObject("diagnostics")
                ?.toDeviceDslSessionDiagnostics()
            StudioDeviceDslSourceSession(
                sessionId = sessionId,
                parentSessionId = parentSessionId,
                role = role,
                physicalContainerToken = session.optionalLong("physicalContainerToken")?.also {
                    require(it > 0L) { "Physical container tokens must be positive." }
                },
                renderingActive = session.requiredBoolean("renderingActive"),
                attachedToWindow = session.requiredBoolean("attachedToWindow"),
                shown = session.requiredBoolean("shown"),
                hasWindowFocus = session.requiredBoolean("hasWindowFocus"),
                windowVisibility = session.requiredInt("windowVisibility"),
                viewDepth = session.requiredInt("viewDepth").coerceAtLeast(0),
                diagnostics = diagnostics,
                sourceCandidates = session.optionalArray("sourceCandidates")
                    .take(MAX_SOURCE_CANDIDATES)
                    .map { candidateElement ->
                        candidateElement
                            .requiredObject("source candidate")
                            .optionalArray("callSites")
                            .take(MAX_SOURCE_CALL_SITES_PER_CANDIDATE)
                            .map { sourceElement ->
                                val source = sourceElement.requiredObject("source call site")
                                StudioPreviewSourceCallSite(
                                    className = source.requiredBoundedString("className"),
                                    methodName = source.requiredBoundedString("methodName"),
                                    fileName = source.requiredBoundedString("fileName"),
                                    lineNumber = source.requiredInt("lineNumber"),
                                )
                            }
                            .filter { source -> source.lineNumber > 0 }
                    }
                    .filter(List<StudioPreviewSourceCallSite>::isNotEmpty),
                nodes = session.optionalArray("nodes")
                    .take(MAX_RETURNED_NODES)
                    .map { nodeElement -> nodeElement.requiredDeviceDslNode() },
                nodeGeneration = session.requiredLong("nodeGeneration").coerceAtLeast(0L),
                nodeInspectionSupported = session.requiredBoolean("nodeInspectionSupported"),
                nodeInspectionEnded = session.requiredBoolean("nodeInspectionEnded"),
                visitedNodes = session.requiredInt("visitedNodes").coerceAtLeast(0),
                droppedNodes = session.requiredInt("droppedNodes").coerceAtLeast(0),
                nodesTruncated = session.requiredBoolean("nodesTruncated"),
            ).also {
                require(
                    diagnostics == null ||
                        (
                            diagnostics.sessionId == sessionId &&
                                diagnostics.parentSessionId == parentSessionId &&
                                diagnostics.role == role
                            )
                ) {
                    "Device DSL diagnostics identity does not match its session envelope."
                }
            }
        }
    return StudioDeviceDslSourceReport(
        requestId = root.requiredRequestId("requestId"),
        operation = root.requiredDeviceDslOperation("operation"),
        packageName = root.requiredBoundedString("packageName"),
        processId = root.requiredInt("processId"),
        generatedAtEpochMillis = root.requiredLong("generatedAtEpochMillis"),
        sessions = sessions,
        highlight = root.optionalObject("highlight")?.toDeviceDslHighlightResult(),
        timing = root.optionalObject("timing")?.toDeviceDslTimingSnapshot(),
    ).also { report ->
        require(report.processId > 0) { "Device DSL source process ID must be positive." }
        require(report.generatedAtEpochMillis > 0) {
            "Device DSL source report timestamp must be positive."
        }
    }
}

private fun JsonObject.toDeviceDslSessionDiagnostics(): StudioDeviceDslSessionDiagnostics {
    val latestFrame = optionalObject("latestFrame")?.let { frame ->
        val statusValue = frame.requiredBoundedString("status")
        val status = StudioDeviceDslFrameStatus.entries.firstOrNull { candidate ->
            candidate.wireValue == statusValue
        } ?: throw IllegalArgumentException("Unknown device DSL frame status '$statusValue'.")
        StudioDeviceDslFrame(
            frameId = frame.requiredLong("frameId").also { require(it > 0L) },
            status = status,
            failures = frame.optionalArray("failures")
                .take(MAX_FRAME_FAILURES)
                .map { failure -> failure.requiredObject("frame failure").toDeviceDslFailure() },
            droppedFailures = frame.requiredInt("droppedFailures").coerceAtLeast(0),
        )
    }
    return StudioDeviceDslSessionDiagnostics(
        sessionId = requiredLong("sessionId").also { require(it > 0L) },
        parentSessionId = optionalLong("parentSessionId"),
        role = requiredRenderSessionRole("role"),
        renderingActive = requiredBoolean("renderingActive"),
        committedFrameId = optionalLong("committedFrameId")?.also { require(it > 0L) },
        latestFrame = latestFrame,
        latestFailure = optionalObject("latestFailure")?.toDeviceDslFailure(),
        ended = requiredBoolean("ended"),
    )
}

private fun JsonObject.toDeviceDslFailure(): StudioDeviceDslFailure {
    return StudioDeviceDslFailure(
        frameId = optionalLong("frameId")?.also { require(it > 0L) },
        phase = requiredWireEnum(
            "phase",
            StudioRenderFailurePhase.entries,
            StudioRenderFailurePhase::wireValue,
        ),
        recovery = requiredWireEnum(
            "recovery",
            StudioRenderFailureRecovery.entries,
            StudioRenderFailureRecovery::wireValue,
        ),
        operation = optionalWireEnum(
            "operation",
            StudioRenderFailureOperation.entries,
            StudioRenderFailureOperation::wireValue,
        ),
        exceptionType = requiredBoundedString("exceptionType").also { type ->
            require(type.length <= MAX_DIAGNOSTIC_STRING_LENGTH) {
                "Device DSL exception type exceeds $MAX_DIAGNOSTIC_STRING_LENGTH characters."
            }
        },
    )
}

private fun <T> JsonObject.requiredWireEnum(
    name: String,
    values: Iterable<T>,
    toWireValue: (T) -> String,
): T {
    val wireValue = requiredBoundedString(name)
    return values.firstOrNull { value -> toWireValue(value) == wireValue }
        ?: throw IllegalArgumentException("Unknown device DSL $name '$wireValue'.")
}

private fun <T> JsonObject.optionalWireEnum(
    name: String,
    values: Iterable<T>,
    toWireValue: (T) -> String,
): T? {
    val wireValue = optionalBoundedString(name) ?: return null
    return values.firstOrNull { value -> toWireValue(value) == wireValue }
        ?: throw IllegalArgumentException("Unknown device DSL $name '$wireValue'.")
}

private fun JsonObject.requiredRequestId(name: String): String {
    return requiredBoundedString(name).also { requestId ->
        require(
            requestId.length in 1..MAX_NONCE_LENGTH &&
                requestId.all { character ->
                    character in 'A'..'Z' ||
                        character in 'a'..'z' ||
                        character in '0'..'9' ||
                        character == '.' || character == '_' || character == '-'
                },
        ) {
            "Device DSL source field '$name' must be a bounded ASCII nonce."
        }
    }
}

internal fun StudioDeviceDslSourceReport.visibleSourceSessions(): List<StudioDeviceDslSourceSession> {
    val sourced = sessions.filter { session -> session.sourceCandidates.isNotEmpty() }
    val active = sourced.filter(StudioDeviceDslSourceSession::renderingActive)
    val visible = active.filter { session ->
        session.attachedToWindow && session.shown && session.windowVisibility == ANDROID_VIEW_VISIBLE
    }
    val eligible = visible.ifEmpty { active }.ifEmpty { sourced }
    val focused = eligible.filter(StudioDeviceDslSourceSession::hasWindowFocus)
        .ifEmpty { eligible }
    val deepest = focused.maxOfOrNull(StudioDeviceDslSourceSession::viewDepth) ?: return emptyList()
    return focused
        .filter { session -> session.viewDepth == deepest }
        .sortedByDescending(StudioDeviceDslSourceSession::sessionId)
}

internal fun StudioDeviceDslSourceReport.visibleTimingSessions(): List<StudioDeviceDslSourceSession> {
    val active = sessions.filter(StudioDeviceDslSourceSession::renderingActive)
    val visible = active.filter { session ->
        session.attachedToWindow && session.shown && session.windowVisibility == ANDROID_VIEW_VISIBLE
    }
    return visible.ifEmpty { active }.ifEmpty { sessions }
        .sortedWith(
            compareByDescending<StudioDeviceDslSourceSession>(StudioDeviceDslSourceSession::hasWindowFocus)
                .thenByDescending(StudioDeviceDslSourceSession::viewDepth)
                .thenByDescending(StudioDeviceDslSourceSession::sessionId),
        )
}

private fun JsonElement.requiredDeviceDslNode(): StudioDeviceDslNode {
    val node = requiredObject("mounted node")
    val token = node.requiredBoundedString("token").also { value ->
        require(value.length <= MAX_NODE_TOKEN_LENGTH && value.all { it in '0'..'9' || it in 'a'..'z' }) {
            "Device DSL node token is invalid."
        }
    }
    val parentToken = node.optionalBoundedString("parentToken")?.also { value ->
        require(value.length <= MAX_NODE_TOKEN_LENGTH && value.all { it in '0'..'9' || it in 'a'..'z' }) {
            "Device DSL parent node token is invalid."
        }
    }
    return StudioDeviceDslNode(
        token = token,
        parentToken = parentToken,
        type = node.requiredBoundedString("type"),
        depth = node.requiredInt("depth").coerceIn(0, MAX_NODE_DEPTH),
        synthetic = node.requiredBoolean("synthetic"),
        sourceCallSites = node.optionalArray("callSites")
            .take(MAX_SOURCE_CALL_SITES_PER_CANDIDATE)
            .map { sourceElement ->
                val source = sourceElement.requiredObject("node source call site")
                StudioPreviewSourceCallSite(
                    className = source.requiredBoundedString("className"),
                    methodName = source.requiredBoundedString("methodName"),
                    fileName = source.requiredBoundedString("fileName"),
                    lineNumber = source.requiredInt("lineNumber"),
                )
            }
            .filter { source -> source.lineNumber > 0 },
    )
}

private fun JsonObject.requiredDeviceDslOperation(name: String): StudioDeviceDslOperation {
    val wireValue = requiredBoundedString(name)
    return StudioDeviceDslOperation.entries.firstOrNull { operation ->
        operation.wireValue == wireValue
    } ?: throw IllegalArgumentException("Unknown device DSL operation '$wireValue'.")
}

private fun JsonObject.toDeviceDslHighlightResult(): StudioDeviceDslHighlightResult {
    val stateValue = requiredBoundedString("state")
    val state = StudioDeviceDslHighlightState.entries.firstOrNull { candidate ->
        candidate.wireValue == stateValue
    } ?: throw IllegalArgumentException("Unknown device DSL highlight state '$stateValue'.")
    return StudioDeviceDslHighlightResult(
        state = state,
        sessionId = optionalLong("sessionId"),
        nodeToken = optionalBoundedString("nodeToken"),
        screenBounds = optionalObject("screenBounds")?.toDeviceDslBounds(),
        visibleBounds = optionalObject("visibleBounds")?.toDeviceDslBounds(),
    )
}

private fun JsonObject.toDeviceDslTimingSnapshot(): StudioDeviceDslTimingSnapshot {
    val startStatusValue = requiredBoundedString("startStatus")
    val startStatus = StudioDeviceDslTimingStartStatus.entries.firstOrNull { status ->
        status.wireValue == startStatusValue
    } ?: throw IllegalArgumentException("Unknown device DSL timing start status '$startStatusValue'.")
    val arm = optionalObject("arm")?.let { armed ->
        val endReason = armed.optionalBoundedString("endReason")?.let { value ->
            StudioDeviceDslTimingArmEndReason.entries.firstOrNull { reason ->
                reason.wireValue == value
            } ?: throw IllegalArgumentException("Unknown device DSL timing arm reason '$value'.")
        }
        StudioDeviceDslTimingArmSnapshot(
            parentSessionId = armed.requiredLong("parentSessionId").also { value ->
                require(value > 0L) { "Armed timing parent session ID must be positive." }
            },
            matchedSessionId = armed.optionalLong("matchedSessionId")?.also { value ->
                require(value > 0L) { "Matched timing session ID must be positive." }
            },
            matchedPhysicalContainerToken =
                armed.optionalLong("matchedPhysicalContainerToken")?.also { value ->
                    require(value > 0L) { "Matched physical container token must be positive." }
                },
            startedAtNanos = armed.requiredLong("startedAtNanos").coerceAtLeast(0L),
            endedAtNanos = armed.optionalLong("endedAtNanos")?.coerceAtLeast(0L),
            endReason = endReason,
        ).also { parsed ->
            require(parsed.endedAtNanos == null || parsed.endedAtNanos >= parsed.startedAtNanos) {
                "Armed timing cannot end before it starts."
            }
            require(
                parsed.endReason != StudioDeviceDslTimingArmEndReason.Matched ||
                    (
                        parsed.matchedSessionId != null &&
                            parsed.matchedPhysicalContainerToken != null
                        )
            ) {
                "Matched timing must identify its logical session and physical container."
            }
        }
    }
    val result = optionalObject("result")?.let { capture ->
        val clock = capture.requiredBoundedString("clock")
        require(clock == "monotonic_nanoseconds") {
            "Device DSL timing clock '$clock' is unsupported."
        }
        StudioDeviceDslTimingResult(
            sessionId = capture.requiredLong("sessionId").also { value ->
                require(value > 0L) { "Device DSL timing session ID must be positive." }
            },
            parentSessionId = capture.optionalLong("parentSessionId")?.also { value ->
                require(value > 0L) { "Device DSL timing parent session ID must be positive." }
            },
            role = capture.requiredRenderSessionRole("role"),
            completedFrames = capture.requiredInt("completedFrames").coerceIn(0, MAX_TIMING_FRAMES),
            startedAtNanos = capture.requiredLong("startedAtNanos").coerceAtLeast(0L),
            endedAtNanos = capture.optionalLong("endedAtNanos")?.coerceAtLeast(0L),
            attemptedClockReads = capture.requiredLong("attemptedClockReads").coerceAtLeast(0L),
            retainedClockReads = capture.requiredLong("retainedClockReads").coerceAtLeast(0L),
            emptyPairOverheadNanos = capture.requiredLong("emptyPairOverheadNanos").coerceAtLeast(0L),
            droppedTimedNodes = capture.requiredInt("droppedTimedNodes").coerceAtLeast(0),
            droppedRecords = capture.requiredInt("droppedRecords").coerceAtLeast(0),
            droppedStrings = capture.requiredInt("droppedStrings").coerceAtLeast(0),
            truncated = capture.requiredBoolean("truncated"),
            complete = capture.requiredBoolean("complete"),
            endReason = capture.optionalBoundedString("endReason"),
            unsupportedDomains = capture.optionalArray("unsupportedDomains")
                .take(MAX_UNSUPPORTED_DOMAINS)
                .map { domain -> domain.requiredBoundedStringPrimitive("unsupported timing domain") },
            records = capture.optionalArray("records")
                .take(MAX_TIMING_RECORDS)
                .map { record -> record.requiredDeviceDslTimingRecord() },
            recordsTruncated = capture.requiredBoolean("recordsTruncated"),
        ).also { parsed ->
            require(parsed.retainedClockReads <= parsed.attemptedClockReads) {
                "Retained timing clock reads cannot exceed attempted reads."
            }
            require(!parsed.complete || parsed.endReason != null) {
                "A complete timing capture must provide an end reason."
            }
        }
    }
    return StudioDeviceDslTimingSnapshot(
        startStatus = startStatus,
        arm = arm,
        result = result,
    )
}

private fun JsonElement.requiredDeviceDslTimingRecord(): StudioDeviceDslTimingRecord {
    val record = requiredObject("timing record")
    val phaseValue = record.requiredBoundedString("phase")
    val phase = StudioDeviceDslTimingPhase.entries.firstOrNull { candidate ->
        candidate.wireValue == phaseValue
    } ?: throw IllegalArgumentException("Unknown device DSL timing phase '$phaseValue'.")
    val inclusionValue = record.requiredBoundedString("inclusion")
    val inclusion = StudioDeviceDslTimingInclusion.entries.firstOrNull { candidate ->
        candidate.wireValue == inclusionValue
    } ?: throw IllegalArgumentException("Unknown device DSL timing inclusion '$inclusionValue'.")
    val token = record.requiredTimingToken("nodeToken")
    val parentToken = record.optionalBoundedString("parentNodeToken")?.also(::requireTimingToken)
    return StudioDeviceDslTimingRecord(
        frameId = record.requiredLong("frameId").also { value ->
            require(value > 0L) { "Device DSL timing frame ID must be positive." }
        },
        nodeToken = token,
        parentNodeToken = parentToken,
        nodeType = record.optionalBoundedString("nodeType"),
        depth = record.requiredInt("depth").coerceIn(0, MAX_TIMING_DEPTH),
        synthetic = record.requiredBoolean("synthetic"),
        phase = phase,
        inclusion = inclusion,
        durationNanos = record.requiredLong("durationNanos").coerceAtLeast(0L),
        repetitions = record.requiredLong("repetitions").also { value ->
            require(value > 0L) { "Device DSL timing repetitions must be positive." }
        },
        truncated = record.requiredBoolean("truncated"),
        sourceCallSites = record.optionalArray("callSites")
            .take(MAX_SOURCE_CALL_SITES_PER_CANDIDATE)
            .map { sourceElement ->
                val source = sourceElement.requiredObject("timing source call site")
                StudioPreviewSourceCallSite(
                    className = source.requiredBoundedString("className"),
                    methodName = source.requiredBoundedString("methodName"),
                    fileName = source.requiredBoundedString("fileName"),
                    lineNumber = source.requiredInt("lineNumber"),
                )
            }
            .filter { source -> source.lineNumber > 0 },
    )
}

private fun JsonObject.requiredTimingToken(name: String): String =
    requiredBoundedString(name).also(::requireTimingToken)

private fun requireTimingToken(value: String) {
    require(value.length <= MAX_NODE_TOKEN_LENGTH && value.all { it in '0'..'9' || it in 'a'..'z' }) {
        "Device DSL timing node token is invalid."
    }
}

private fun JsonElement.requiredBoundedStringPrimitive(description: String): String {
    require(isJsonPrimitive && asJsonPrimitive.isString) {
        "Device DSL source $description must be a string."
    }
    return asString.also { value ->
        require(value.isNotBlank() && value.length <= MAX_STRING_LENGTH)
    }
}

private fun JsonObject.toDeviceDslBounds(): StudioDeviceDslBounds {
    val bounds = StudioDeviceDslBounds(
        left = requiredInt("left"),
        top = requiredInt("top"),
        right = requiredInt("right"),
        bottom = requiredInt("bottom"),
    )
    require(bounds.right >= bounds.left && bounds.bottom >= bounds.top) {
        "Device DSL bounds are inverted."
    }
    return bounds
}

private fun JsonElement.requiredObject(description: String): JsonObject {
    require(isJsonObject) { "Device DSL source $description must be an object." }
    return asJsonObject
}

private fun JsonObject.requiredInt(name: String): Int {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        "Device DSL source field '$name' must be an integer."
    }
    return value.asInt
}

private fun JsonObject.requiredLong(name: String): Long {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        "Device DSL source field '$name' must be a long."
    }
    return value.asLong
}

private fun JsonObject.optionalLong(name: String): Long? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        "Device DSL source field '$name' must be a long or null."
    }
    return value.asLong
}

private fun JsonObject.optionalBoundedString(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "Device DSL source field '$name' must be a string or null."
    }
    return value.asString.also { text ->
        require(text.isNotBlank() && text.length <= MAX_STRING_LENGTH) {
            "Device DSL source field '$name' must contain 1..$MAX_STRING_LENGTH characters."
        }
    }
}

private fun JsonObject.optionalObject(name: String): JsonObject? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    return value.requiredObject(name)
}

private fun JsonObject.requiredRenderSessionRole(name: String): StudioRenderSessionRole {
    val value = requiredBoundedString(name)
    return runCatching { StudioRenderSessionRole.valueOf(value) }
        .getOrElse { throw IllegalArgumentException("Unknown render session role '$value'.") }
}

private fun JsonObject.requiredBoolean(name: String): Boolean {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
        "Device DSL source field '$name' must be a boolean."
    }
    return value.asBoolean
}

private fun JsonObject.requiredBoundedString(name: String): String {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "Device DSL source field '$name' must be a string."
    }
    return value.asString.also { text ->
        require(text.isNotBlank() && text.length <= MAX_STRING_LENGTH) {
            "Device DSL source field '$name' must contain 1..$MAX_STRING_LENGTH characters."
        }
    }
}

private fun JsonObject.optionalArray(name: String): List<JsonElement> {
    val value = get(name) ?: return emptyList()
    require(value.isJsonArray) { "Device DSL source field '$name' must be an array." }
    return value.asJsonArray.toList()
}

private const val ANDROID_VIEW_VISIBLE = 0
private const val MAX_REPORT_BYTES = 256 * 1024
private const val MAX_REPORTED_SESSIONS = 64
private const val MAX_SOURCE_CANDIDATES = 32
private const val MAX_SOURCE_CALL_SITES_PER_CANDIDATE = 24
private const val MAX_STRING_LENGTH = 1024
private const val MAX_NONCE_LENGTH = 128
private const val MAX_NODE_TOKEN_LENGTH = 32
private const val MAX_RETURNED_NODES = 512
private const val MAX_NODE_DEPTH = 64
private const val MAX_TIMING_DEPTH = 32
private const val MAX_TIMING_FRAMES = 8
private const val MAX_TIMING_RECORDS = 512
private const val MAX_UNSUPPORTED_DOMAINS = 16
private const val MAX_FRAME_FAILURES = 16
private const val MAX_DIAGNOSTIC_STRING_LENGTH = 256
