package com.viewcompose.studio.preview

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal data class StudioPreviewCatalog(
    val protocolVersion: Int,
    val modulePath: String,
    val buildVariant: String,
    val buildFingerprint: String,
    val descriptors: List<StudioPreviewDescriptor>,
    val diagnostics: List<StudioPreviewDiagnostic>,
)

internal data class StudioPreviewDescriptor(
    val id: String,
    val displayName: String,
    val group: String,
    val variants: List<StudioPreviewVariant>,
    val sourceLocation: StudioPreviewSourceLocation?,
)

internal data class StudioPreviewVariant(
    val id: String,
    val displayName: String,
)

internal data class StudioPreviewSourceLocation(
    val filePath: String,
    val line: Int,
    val column: Int,
    val symbolName: String?,
)

internal data class StudioPreviewDiagnostic(
    val severity: StudioPreviewDiagnosticSeverity,
    val message: String,
    val phase: String,
    val sourceLocation: StudioPreviewSourceLocation?,
    val details: String?,
)

internal enum class StudioPreviewDiagnosticSeverity {
    Info,
    Warning,
    Error,
}

internal data class StudioPreviewRenderResponse(
    val protocolVersion: Int,
    val requestId: String,
    val previewId: String,
    val variantId: String,
    val status: StudioPreviewRenderStatus,
    val imagePath: String?,
    val renderTreePath: String?,
    val diagnostics: List<StudioPreviewDiagnostic>,
    val durationMillis: Long?,
)

internal enum class StudioPreviewRenderStatus {
    Success,
    CompileFailure,
    RenderFailure,
    Cancelled,
    TimedOut,
    ProtocolMismatch,
}

internal object StudioPreviewProtocolReader {
    fun readCatalog(path: Path): StudioPreviewCatalog {
        val root = path.readJsonObject()
        val protocolVersion = root.requiredInt("protocolVersion")
        requireSupportedProtocol(protocolVersion, path)
        return StudioPreviewCatalog(
            protocolVersion = protocolVersion,
            modulePath = root.requiredString("modulePath"),
            buildVariant = root.requiredString("buildVariant"),
            buildFingerprint = root.requiredString("buildFingerprint")
                .also(::requireSha256),
            descriptors = root.requiredArray("descriptors").map { element ->
                element.requiredObject("descriptor").toDescriptor()
            },
            diagnostics = root.optionalArray("diagnostics").map { element ->
                element.requiredObject("diagnostic").toDiagnostic()
            },
        )
    }

    fun readResponse(path: Path): StudioPreviewRenderResponse {
        val root = path.readJsonObject()
        val protocolVersion = root.requiredInt("protocolVersion")
        requireSupportedProtocol(protocolVersion, path)
        val artifacts = root.optionalObject("artifacts")
        return StudioPreviewRenderResponse(
            protocolVersion = protocolVersion,
            requestId = root.requiredString("requestId"),
            previewId = root.requiredString("previewId"),
            variantId = root.requiredString("variantId"),
            status = enumValueOf(root.requiredString("status")),
            imagePath = artifacts?.optionalString("imagePath"),
            renderTreePath = artifacts?.optionalString("renderTreePath"),
            diagnostics = root.optionalArray("diagnostics").map { element ->
                element.requiredObject("diagnostic").toDiagnostic()
            },
            durationMillis = root.optionalLong("durationMillis"),
        )
    }
}

private fun JsonObject.toDescriptor(): StudioPreviewDescriptor {
    return StudioPreviewDescriptor(
        id = requiredString("id").also(::requireStableId),
        displayName = requiredString("displayName"),
        group = optionalString("group").orEmpty(),
        variants = requiredArray("variants").map { element ->
            val variant = element.requiredObject("variant")
            StudioPreviewVariant(
                id = variant.requiredString("id").also(::requireStableId),
                displayName = variant.requiredString("displayName"),
            )
        }.also { variants ->
            require(variants.isNotEmpty()) { "Preview descriptor must contain variants." }
        },
        sourceLocation = optionalObject("sourceLocation")?.toSourceLocation(),
    )
}

private fun JsonObject.toDiagnostic(): StudioPreviewDiagnostic {
    return StudioPreviewDiagnostic(
        severity = enumValueOf(requiredString("severity")),
        message = requiredString("message"),
        phase = requiredString("phase"),
        sourceLocation = optionalObject("sourceLocation")?.toSourceLocation(),
        details = optionalString("details"),
    )
}

private fun JsonObject.toSourceLocation(): StudioPreviewSourceLocation {
    return StudioPreviewSourceLocation(
        filePath = requiredString("filePath"),
        line = requiredInt("line"),
        column = optionalInt("column") ?: 1,
        symbolName = optionalString("symbolName"),
    )
}

private fun Path.readJsonObject(): JsonObject {
    require(Files.isRegularFile(this)) { "Preview protocol file does not exist: '$this'." }
    Files.newBufferedReader(this, StandardCharsets.UTF_8).use { reader ->
        return JsonParser.parseReader(reader).requiredObject("root")
    }
}

private fun JsonElement.requiredObject(label: String): JsonObject {
    require(isJsonObject) { "Preview protocol '$label' must be an object." }
    return asJsonObject
}

private fun JsonObject.requiredString(name: String): String {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "Preview protocol field '$name' must be a string."
    }
    return value.asString.also { text ->
        require(text.isNotBlank()) { "Preview protocol field '$name' must not be blank." }
    }
}

private fun JsonObject.optionalString(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "Preview protocol field '$name' must be a string when present."
    }
    return value.asString
}

private fun JsonObject.requiredInt(name: String): Int {
    return requireNotNull(optionalInt(name)) {
        "Preview protocol field '$name' must be an integer."
    }
}

private fun JsonObject.optionalInt(name: String): Int? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    return runCatching { value.asInt }.getOrElse {
        throw IllegalArgumentException("Preview protocol field '$name' must be an integer.", it)
    }
}

private fun JsonObject.optionalLong(name: String): Long? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    return runCatching { value.asLong }.getOrElse {
        throw IllegalArgumentException("Preview protocol field '$name' must be a long.", it)
    }
}

private fun JsonObject.requiredArray(name: String): List<JsonElement> {
    val value = get(name)
    require(value != null && value.isJsonArray) {
        "Preview protocol field '$name' must be an array."
    }
    return value.asJsonArray.toList()
}

private fun JsonObject.optionalArray(name: String): List<JsonElement> {
    val value = get(name) ?: return emptyList()
    if (value.isJsonNull) return emptyList()
    require(value.isJsonArray) {
        "Preview protocol field '$name' must be an array when present."
    }
    return value.asJsonArray.toList()
}

private fun JsonObject.optionalObject(name: String): JsonObject? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    return value.requiredObject(name)
}

private fun requireSupportedProtocol(
    protocolVersion: Int,
    source: Path,
) {
    require(protocolVersion == SUPPORTED_PREVIEW_PROTOCOL_VERSION) {
        "Unsupported ViewCompose preview protocol $protocolVersion in '$source'; " +
            "expected $SUPPORTED_PREVIEW_PROTOCOL_VERSION."
    }
}

private fun requireSha256(value: String) {
    require(SHA_256_PATTERN.matches(value)) {
        "Preview build fingerprint must be a lowercase SHA-256 value."
    }
}

private fun requireStableId(value: String) {
    require(STABLE_ID_PATTERN.matches(value)) {
        "Preview id must use lowercase ASCII words separated by '-' or reserved '__': '$value'."
    }
}

private const val SUPPORTED_PREVIEW_PROTOCOL_VERSION = 1
private val SHA_256_PATTERN = Regex("[a-f0-9]{64}")
private val STABLE_ID_PATTERN = Regex("[a-z0-9]+(?:(?:-|__)[a-z0-9]+)*")
