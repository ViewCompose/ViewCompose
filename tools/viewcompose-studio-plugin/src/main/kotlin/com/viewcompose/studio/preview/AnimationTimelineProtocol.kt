package com.viewcompose.studio.preview

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.StringReader
import java.nio.charset.StandardCharsets

internal const val ANIMATION_TIMELINE_REPORT_PATH =
    "cache/viewcompose/animation-timeline-v1.json"
internal const val ANIMATION_TIMELINE_REQUEST_ACTION =
    "com.viewcompose.preview.action.REQUEST_ANIMATION_TIMELINE"
internal const val ANIMATION_TIMELINE_REQUEST_ID_EXTRA = "request_id"
internal const val ANIMATION_TIMELINE_MODE_EXTRA = "mode"
internal const val ANIMATION_TIMELINE_TRANSITION_ID_EXTRA = "transition_id"
internal const val ANIMATION_TIMELINE_DISCOVER_MODE = "discover"
internal const val ANIMATION_TIMELINE_CAPTURE_MODE = "capture"
internal const val ANIMATION_TIMELINE_PROTOCOL_VERSION = 1

internal data class StudioAnimationTimelineReport(
    val requestId: String,
    val packageName: String,
    val processId: Int,
    val generatedAtEpochMillis: Long,
    val mode: String,
    val status: String,
    val transitions: List<StudioAnimationTimeline>,
)

internal data class StudioAnimationTimeline(
    val identity: String,
    val label: String,
    val samples: List<StudioAnimationTimelineSample>,
)

internal data class StudioAnimationTimelineSample(
    val currentState: StudioAnimationTimelineState,
    val targetState: StudioAnimationTimelineState,
    val segmentInitialState: StudioAnimationTimelineState,
    val segmentTargetState: StudioAnimationTimelineState,
    val segmentVersion: Long,
    val playTimeNanos: Long,
    val durationNanos: Long,
    val runState: String,
    val channels: List<StudioAnimationTimelineChannel>,
)

internal data class StudioAnimationTimelineState(
    val typeName: String,
    val displayValue: String?,
)

internal data class StudioAnimationTimelineChannel(
    val identity: String,
    val name: String,
    val specFamily: String,
    val startValue: StudioAnimationTimelineValue?,
    val currentValue: StudioAnimationTimelineValue?,
    val targetValue: StudioAnimationTimelineValue?,
    val velocity: StudioAnimationTimelineValue?,
    val durationNanos: Long,
    val finished: Boolean,
    val terminalCondition: String,
)

internal data class StudioAnimationTimelineValue(
    val kind: String,
    val components: List<Float>,
)

internal fun parseAnimationTimelineReport(json: String): StudioAnimationTimelineReport {
    require(json.toByteArray(StandardCharsets.UTF_8).size <= MAX_ANIMATION_REPORT_BYTES) {
        "Animation timeline report exceeds $MAX_ANIMATION_REPORT_BYTES bytes."
    }
    val root = StringReader(json).use(JsonParser::parseReader)
        .animationRequiredObject("root")
    val protocolVersion = root.animationRequiredInt("protocolVersion")
    require(protocolVersion == ANIMATION_TIMELINE_PROTOCOL_VERSION) {
        "Unsupported animation timeline protocol $protocolVersion."
    }
    val mode = root.animationRequiredEnum("mode", ANIMATION_TIMELINE_MODES)
    val transitions = root.animationBoundedArray("transitions", MAX_ANIMATION_TRANSITIONS)
        .map { element ->
            val transition = element.animationRequiredObject("transition")
            StudioAnimationTimeline(
                identity = transition.animationRequiredIdentity("identity"),
                label = transition.animationRequiredBoundedString("label", allowEmpty = true),
                samples = transition.animationBoundedArray("samples", MAX_ANIMATION_SAMPLES)
                    .map(::parseAnimationTimelineSample),
            )
        }
    return StudioAnimationTimelineReport(
        requestId = root.animationRequiredNonce("requestId"),
        packageName = root.animationRequiredBoundedString("packageName"),
        processId = root.animationRequiredInt("processId"),
        generatedAtEpochMillis = root.animationRequiredLong("generatedAtEpochMillis"),
        mode = mode,
        status = root.animationRequiredEnum("status", ANIMATION_TIMELINE_STATUSES),
        transitions = transitions,
    ).also { report ->
        require(report.processId > 0) { "Animation timeline process ID must be positive." }
        require(report.generatedAtEpochMillis > 0L) {
            "Animation timeline report timestamp must be positive."
        }
        require(report.transitions.all { transition -> transition.samples.isNotEmpty() }) {
            "Animation timeline entries must contain at least one sample."
        }
    }
}

private fun parseAnimationTimelineSample(element: JsonElement): StudioAnimationTimelineSample {
    val sample = element.animationRequiredObject("sample")
    return StudioAnimationTimelineSample(
        currentState = sample.animationRequiredState("currentState"),
        targetState = sample.animationRequiredState("targetState"),
        segmentInitialState = sample.animationRequiredState("segmentInitialState"),
        segmentTargetState = sample.animationRequiredState("segmentTargetState"),
        segmentVersion = sample.animationRequiredNonNegativeLong("segmentVersion"),
        playTimeNanos = sample.animationRequiredNonNegativeLong("playTimeNanos"),
        durationNanos = sample.animationRequiredNonNegativeLong("durationNanos"),
        runState = sample.animationRequiredEnum("runState", ANIMATION_TIMELINE_RUN_STATES),
        channels = sample.animationBoundedArray("channels", MAX_ANIMATION_CHANNELS)
            .map(::parseAnimationTimelineChannel),
    )
}

private fun parseAnimationTimelineChannel(element: JsonElement): StudioAnimationTimelineChannel {
    val channel = element.animationRequiredObject("channel")
    return StudioAnimationTimelineChannel(
        identity = channel.animationRequiredIdentity("identity"),
        name = channel.animationRequiredBoundedString("name"),
        specFamily = channel.animationRequiredEnum("specFamily", ANIMATION_SPEC_FAMILIES),
        startValue = channel.animationOptionalValue("startValue"),
        currentValue = channel.animationOptionalValue("currentValue"),
        targetValue = channel.animationOptionalValue("targetValue"),
        velocity = channel.animationOptionalValue("velocity"),
        durationNanos = channel.animationRequiredNonNegativeLong("durationNanos"),
        finished = channel.animationRequiredBoolean("finished"),
        terminalCondition = channel.animationRequiredEnum(
            "terminalCondition",
            ANIMATION_TERMINAL_CONDITIONS,
        ),
    )
}

private fun JsonObject.animationRequiredState(name: String): StudioAnimationTimelineState {
    val state = get(name).animationRequiredObject(name)
    return StudioAnimationTimelineState(
        typeName = state.animationRequiredBoundedString("typeName"),
        displayValue = state.get("displayValue")?.let { value ->
            if (value.isJsonNull) {
                null
            } else {
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    "Animation timeline state displayValue must be a string or null."
                }
                value.asString.also(::requireBoundedAnimationText)
            }
        },
    )
}

private fun JsonObject.animationOptionalValue(name: String): StudioAnimationTimelineValue? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    val objectValue = value.animationRequiredObject(name)
    val kind = objectValue.animationRequiredEnum("kind", ANIMATION_VALUE_KINDS)
    val components = objectValue.animationOptionalArray("components").map { component ->
        require(component.isJsonPrimitive && component.asJsonPrimitive.isNumber) {
            "Animation timeline value component must be numeric."
        }
        component.asFloat.also { number ->
            require(number.isFinite()) { "Animation timeline value component must be finite." }
        }
    }
    val expectedSize = if (kind == "argb") 4 else 1
    require(components.size == expectedSize) {
        "Animation timeline $kind value requires $expectedSize components."
    }
    return StudioAnimationTimelineValue(kind = kind, components = components)
}

private fun JsonElement?.animationRequiredObject(description: String): JsonObject {
    require(this != null && isJsonObject) {
        "Animation timeline $description must be an object."
    }
    return asJsonObject
}

private fun JsonObject.animationRequiredInt(name: String): Int {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        "Animation timeline field '$name' must be an integer."
    }
    return value.asInt
}

private fun JsonObject.animationRequiredLong(name: String): Long {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        "Animation timeline field '$name' must be a long."
    }
    return value.asLong
}

private fun JsonObject.animationRequiredNonNegativeLong(name: String): Long {
    return animationRequiredLong(name).also { value ->
        require(value >= 0L) { "Animation timeline field '$name' must be non-negative." }
    }
}

private fun JsonObject.animationRequiredBoolean(name: String): Boolean {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
        "Animation timeline field '$name' must be a boolean."
    }
    return value.asBoolean
}

private fun JsonObject.animationRequiredBoundedString(
    name: String,
    allowEmpty: Boolean = false,
): String {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "Animation timeline field '$name' must be a string."
    }
    return value.asString.also { text ->
        require((allowEmpty || text.isNotEmpty()) && text.length <= MAX_ANIMATION_STRING_LENGTH) {
            "Animation timeline field '$name' is outside its text bound."
        }
    }
}

private fun JsonObject.animationRequiredIdentity(name: String): String {
    return animationRequiredBoundedString(name).also { identity ->
        require(identity.all { character -> character.isLetterOrDigit() || character == '-' }) {
            "Animation timeline field '$name' is not a valid identity."
        }
    }
}

private fun JsonObject.animationRequiredNonce(name: String): String {
    return animationRequiredBoundedString(name).also { nonce ->
        require(nonce.matches(ANIMATION_REQUEST_NONCE)) {
            "Animation timeline field '$name' must be a lowercase hexadecimal nonce."
        }
    }
}

private fun JsonObject.animationRequiredEnum(name: String, supported: Set<String>): String {
    return animationRequiredBoundedString(name).also { value ->
        require(value in supported) {
            "Animation timeline field '$name' contains unsupported value '$value'."
        }
    }
}

private fun JsonObject.animationOptionalArray(name: String): List<JsonElement> {
    val value = get(name) ?: return emptyList()
    require(value.isJsonArray) { "Animation timeline field '$name' must be an array." }
    return value.asJsonArray.toList()
}

private fun JsonObject.animationBoundedArray(name: String, maximum: Int): List<JsonElement> {
    return animationOptionalArray(name).also { values ->
        require(values.size <= maximum) {
            "Animation timeline field '$name' exceeds its $maximum-item bound."
        }
    }
}

private fun requireBoundedAnimationText(text: String) {
    require(text.length <= MAX_ANIMATION_STRING_LENGTH) {
        "Animation timeline text exceeds $MAX_ANIMATION_STRING_LENGTH characters."
    }
}

private val ANIMATION_REQUEST_NONCE = Regex("[a-f0-9]{32}")
private val ANIMATION_TIMELINE_MODES = setOf("discover", "capture")
private val ANIMATION_TIMELINE_STATUSES = setOf("success", "missing", "busy", "stale")
private val ANIMATION_TIMELINE_RUN_STATES = setOf("idle", "running", "interrupted")
private val ANIMATION_SPEC_FAMILIES =
    setOf("tween", "spring", "keyframes", "snap", "repeatable", "unsupported")
private val ANIMATION_TERMINAL_CONDITIONS = setOf("finished", "durationlimitreached")
private val ANIMATION_VALUE_KINDS = setOf("float", "int", "dp", "argb")
private const val MAX_ANIMATION_REPORT_BYTES = 256 * 1024
private const val MAX_ANIMATION_TRANSITIONS = 64
private const val MAX_ANIMATION_SAMPLES = 64
private const val MAX_ANIMATION_CHANNELS = 32
private const val MAX_ANIMATION_STRING_LENGTH = 256
