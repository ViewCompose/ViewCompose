package com.viewcompose.studio.preview

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.StringReader
import java.nio.charset.StandardCharsets

internal const val DEVICE_DSL_SOURCE_REPORT_PATH =
    "cache/viewcompose/device-dsl-source-v2.json"
internal const val DEVICE_DSL_SOURCE_PROTOCOL_VERSION = 2

internal data class StudioDeviceDslSourceReport(
    val packageName: String,
    val processId: Int,
    val generatedAtEpochMillis: Long,
    val sessions: List<StudioDeviceDslSourceSession>,
)

internal data class StudioDeviceDslSourceSession(
    val sessionId: Long,
    val renderingActive: Boolean,
    val attachedToWindow: Boolean,
    val shown: Boolean,
    val hasWindowFocus: Boolean,
    val windowVisibility: Int,
    val viewDepth: Int,
    val sourceCandidates: List<List<StudioPreviewSourceCallSite>>,
)

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
            StudioDeviceDslSourceSession(
                sessionId = session.requiredLong("sessionId"),
                renderingActive = session.requiredBoolean("renderingActive"),
                attachedToWindow = session.requiredBoolean("attachedToWindow"),
                shown = session.requiredBoolean("shown"),
                hasWindowFocus = session.requiredBoolean("hasWindowFocus"),
                windowVisibility = session.requiredInt("windowVisibility"),
                viewDepth = session.requiredInt("viewDepth").coerceAtLeast(0),
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
            )
        }
    return StudioDeviceDslSourceReport(
        packageName = root.requiredBoundedString("packageName"),
        processId = root.requiredInt("processId"),
        generatedAtEpochMillis = root.requiredLong("generatedAtEpochMillis"),
        sessions = sessions,
    ).also { report ->
        require(report.processId > 0) { "Device DSL source process ID must be positive." }
        require(report.generatedAtEpochMillis > 0) {
            "Device DSL source report timestamp must be positive."
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
