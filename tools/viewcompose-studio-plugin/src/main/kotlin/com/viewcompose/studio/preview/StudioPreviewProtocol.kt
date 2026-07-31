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
    val widthDp: Int = DEFAULT_STUDIO_PREVIEW_WIDTH_DP,
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
    val phaseTimings: List<StudioPreviewPhaseTiming>,
)

internal data class StudioPreviewPhaseTiming(
    val phase: String,
    val durationMillis: Long,
)

internal data class StudioPreviewRenderSnapshot(
    val stats: StudioPreviewRenderStats,
    val structure: StudioPreviewRenderStructure,
    val warnings: List<String>,
    val tree: List<StudioPreviewRenderTreeNode>,
    val nativeViewTree: List<StudioPreviewNativeViewNode>,
    val layoutDiagnostics: List<StudioPreviewLayoutDiagnostic>,
    val patches: List<StudioPreviewPatchRecord>,
    val composition: StudioPreviewCompositionSnapshot,
)

internal data class StudioPreviewRenderStats(
    val inserts: Int,
    val reuses: Int,
    val removals: Int,
    val reboundNodes: Int,
    val patchedNodes: Int,
    val skippedBindings: Int,
    val skippedSubtrees: Int,
)

internal data class StudioPreviewRenderStructure(
    val vnodeCount: Int,
    val mountedNodeCount: Int,
    val maxVNodeDepth: Int,
    val maxMountedDepth: Int,
)

internal data class StudioPreviewRenderTreeNode(
    val type: String,
    val key: String?,
    val nodeId: String?,
    val sourceCallSites: List<StudioPreviewSourceCallSite>,
    val synthetic: Boolean,
    val children: List<StudioPreviewRenderTreeNode>,
)

internal data class StudioPreviewSourceCallSite(
    val className: String,
    val methodName: String,
    val fileName: String,
    val lineNumber: Int,
)

internal data class StudioPreviewNativeViewNode(
    val className: String,
    val bounds: StudioPreviewLayoutBounds,
    val measuredWidth: Int,
    val measuredHeight: Int,
    val visibility: String,
    val visibleBounds: StudioPreviewLayoutBounds?,
    val clippingState: StudioPreviewClippingState,
    val clippingAncestorClassName: String?,
    val clippingAncestorNodeId: String?,
    val clippingExpected: Boolean,
    val properties: Map<String, String> = emptyMap(),
    val nodeId: String?,
    val sourceCallSites: List<StudioPreviewSourceCallSite>,
    val synthetic: Boolean,
    val children: List<StudioPreviewNativeViewNode>,
)

internal enum class StudioPreviewClippingState {
    NotClipped,
    PartiallyClipped,
    FullyClipped,
}

internal data class StudioPreviewLayoutBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}

internal data class StudioPreviewLayoutDiagnostic(
    val kind: StudioPreviewLayoutDiagnosticKind,
    val severity: StudioPreviewDiagnosticSeverity,
    val className: String,
    val bounds: StudioPreviewLayoutBounds,
    val visibleBounds: StudioPreviewLayoutBounds?,
    val clippingAncestorClassName: String?,
    val clippingAncestorNodeId: String?,
    val clippingExpected: Boolean,
    val metrics: Map<String, Int>,
    val nodeId: String?,
    val sourceCallSites: List<StudioPreviewSourceCallSite>,
    val synthetic: Boolean,
)

internal enum class StudioPreviewLayoutDiagnosticKind {
    ZeroLayoutSize,
    PartiallyClipped,
    FullyClipped,
    TextEllipsized,
    TextContentClipped,
    Unknown,
}

internal data class StudioPreviewPatchRecord(
    val operation: String,
    val type: String,
    val key: String?,
    val parentKey: String?,
    val index: Int,
    val moved: Boolean,
    val detail: String?,
    val nodeId: String? = null,
    val sourceCallSites: List<StudioPreviewSourceCallSite> = emptyList(),
    val synthetic: Boolean = false,
)

internal data class StudioPreviewCompositionSnapshot(
    val invalidatedScopeCount: Int,
    val recomposedScopeCount: Int,
    val skippedScopeCount: Int,
    val scopes: List<StudioPreviewRecomposeScope>,
)

internal data class StudioPreviewRecomposeScope(
    val path: String,
    val signature: String,
    val depth: Int,
    val reasons: List<String>,
    val recomposed: Boolean,
    val skipped: Boolean,
    val locals: List<StudioPreviewCompositionLocal>,
    val sourceCallSites: List<StudioPreviewSourceCallSite> = emptyList(),
)

internal data class StudioPreviewCompositionLocal(
    val name: String,
    val value: String,
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
    fun readBuildManifestInputPaths(path: Path): List<String> {
        val root = path.readJsonObject()
        val protocolVersion = root.requiredInt("protocolVersion")
        requireSupportedProtocol(protocolVersion, path)
        return root.optionalArray("inputs").flatMap { element ->
            element.requiredObject("build input")
                .optionalArray("paths")
                .map { pathElement -> pathElement.requiredStringValue("build input path") }
        }
    }

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
            phaseTimings = root.optionalArray("phaseTimings").map { element ->
                val timing = element.requiredObject("phase timing")
                StudioPreviewPhaseTiming(
                    phase = timing.requiredString("phase"),
                    durationMillis = timing.requiredLong("durationMillis").also { duration ->
                        require(duration >= 0L) { "Preview phase timing must be non-negative." }
                    },
                )
            },
        )
    }

    fun readRenderSnapshot(path: Path): StudioPreviewRenderSnapshot {
        val size = Files.size(path)
        require(size in 1..MAXIMUM_RENDER_SNAPSHOT_BYTES) {
            "Preview render snapshot '$path' has unsupported size $size bytes."
        }
        val root = path.readJsonObject()
        val budget = SnapshotParseBudget()
        return StudioPreviewRenderSnapshot(
            stats = root.optionalObject("stats").toRenderStats(),
            structure = root.optionalObject("structure").toRenderStructure(),
            warnings = root.optionalArray("warnings").map { element ->
                element.requiredStringValue("warning")
            },
            tree = root.optionalArray("tree").map { element ->
                element.requiredObject("tree node").toRenderTreeNode(
                    budget = budget,
                    depth = 0,
                )
            },
            nativeViewTree = root.optionalArray("nativeViewTree").map { element ->
                element.requiredObject("native View node").toNativeViewNode(
                    budget = budget,
                    depth = 0,
                )
            },
            layoutDiagnostics = root.optionalArray("layoutDiagnostics")
                .take(MAXIMUM_LAYOUT_DIAGNOSTICS)
                .map { element ->
                    element.requiredObject("layout diagnostic").toLayoutDiagnostic()
                },
            patches = root.optionalArray("patches").map { element ->
                element.requiredObject("patch").toPatchRecord()
            },
            composition = root.optionalObject("composition").toCompositionSnapshot(),
        )
    }
}

private fun JsonObject?.toRenderStats(): StudioPreviewRenderStats {
    return StudioPreviewRenderStats(
        inserts = this?.optionalInt("inserts") ?: 0,
        reuses = this?.optionalInt("reuses") ?: 0,
        removals = this?.optionalInt("removals") ?: 0,
        reboundNodes = this?.optionalInt("reboundNodes") ?: 0,
        patchedNodes = this?.optionalInt("patchedNodes") ?: 0,
        skippedBindings = this?.optionalInt("skippedBindings") ?: 0,
        skippedSubtrees = this?.optionalInt("skippedSubtrees") ?: 0,
    )
}

private fun JsonObject?.toRenderStructure(): StudioPreviewRenderStructure {
    return StudioPreviewRenderStructure(
        vnodeCount = this?.optionalInt("vnodeCount") ?: 0,
        mountedNodeCount = this?.optionalInt("mountedNodeCount") ?: 0,
        maxVNodeDepth = this?.optionalInt("maxVNodeDepth") ?: 0,
        maxMountedDepth = this?.optionalInt("maxMountedDepth") ?: 0,
    )
}

private fun JsonObject.toRenderTreeNode(
    budget: SnapshotParseBudget,
    depth: Int,
): StudioPreviewRenderTreeNode {
    budget.recordNode(depth)
    return StudioPreviewRenderTreeNode(
        type = requiredString("type"),
        key = optionalString("key"),
        nodeId = optionalString("nodeId"),
        sourceCallSites = sourceCallSites(),
        synthetic = optionalBoolean("synthetic") ?: false,
        children = optionalArray("children").map { child ->
            child.requiredObject("tree child").toRenderTreeNode(
                budget = budget,
                depth = depth + 1,
            )
        },
    )
}

private fun JsonObject.toNativeViewNode(
    budget: SnapshotParseBudget,
    depth: Int,
): StudioPreviewNativeViewNode {
    budget.recordNode(depth)
    val bounds = requireNotNull(optionalLayoutBounds("bounds")) {
        "Preview protocol field 'bounds' must be an object."
    }
    return StudioPreviewNativeViewNode(
        className = requiredString("className"),
        bounds = bounds,
        measuredWidth = optionalInt("measuredWidth") ?: 0,
        measuredHeight = optionalInt("measuredHeight") ?: 0,
        visibility = optionalString("visibility") ?: "VISIBLE",
        visibleBounds = optionalLayoutBounds("visibleBounds"),
        clippingState = optionalString("clippingState")
            ?.let { state -> runCatching { enumValueOf<StudioPreviewClippingState>(state) }.getOrNull() }
            ?: StudioPreviewClippingState.NotClipped,
        clippingAncestorClassName = optionalString("clippingAncestorClassName"),
        clippingAncestorNodeId = optionalString("clippingAncestorNodeId"),
        clippingExpected = optionalBoolean("clippingExpected") ?: false,
        properties = optionalObject("properties")
            ?.entrySet()
            .orEmpty()
            .take(MAXIMUM_NATIVE_VIEW_PROPERTIES)
            .mapNotNull { (name, value) ->
                value.takeIf { element ->
                    element.isJsonPrimitive && element.asJsonPrimitive.isString
                }?.asString?.let { propertyValue ->
                    name.take(MAXIMUM_NATIVE_VIEW_PROPERTY_NAME_LENGTH) to
                        propertyValue.take(MAXIMUM_NATIVE_VIEW_PROPERTY_VALUE_LENGTH)
                }
            }
            .toMap(),
        nodeId = optionalString("nodeId"),
        sourceCallSites = sourceCallSites(),
        synthetic = optionalBoolean("synthetic") ?: false,
        children = optionalArray("children").map { child ->
            child.requiredObject("native View child").toNativeViewNode(
                budget = budget,
                depth = depth + 1,
            )
        },
    )
}

private fun JsonObject.toLayoutDiagnostic(): StudioPreviewLayoutDiagnostic {
    val kindName = requiredString("kind")
    val metricsObject = optionalObject("metrics")
    return StudioPreviewLayoutDiagnostic(
        kind = runCatching {
            enumValueOf<StudioPreviewLayoutDiagnosticKind>(kindName)
        }.getOrDefault(StudioPreviewLayoutDiagnosticKind.Unknown),
        severity = enumValueOf(requiredString("severity")),
        className = requiredString("className"),
        bounds = requireNotNull(optionalLayoutBounds("bounds")) {
            "Preview layout diagnostic field 'bounds' must be an object."
        },
        visibleBounds = optionalLayoutBounds("visibleBounds"),
        clippingAncestorClassName = optionalString("clippingAncestorClassName"),
        clippingAncestorNodeId = optionalString("clippingAncestorNodeId"),
        clippingExpected = optionalBoolean("clippingExpected") ?: false,
        metrics = metricsObject
            ?.entrySet()
            .orEmpty()
            .take(MAXIMUM_LAYOUT_DIAGNOSTIC_METRICS)
            .associate { (name, _) ->
                name to checkNotNull(metricsObject?.optionalInt(name))
            },
        nodeId = optionalString("nodeId"),
        sourceCallSites = sourceCallSites(),
        synthetic = optionalBoolean("synthetic") ?: false,
    )
}

private fun JsonObject.optionalLayoutBounds(name: String): StudioPreviewLayoutBounds? {
    val bounds = optionalObject(name) ?: return null
    return StudioPreviewLayoutBounds(
        left = bounds.requiredInt("left"),
        top = bounds.requiredInt("top"),
        right = bounds.requiredInt("right"),
        bottom = bounds.requiredInt("bottom"),
    )
}

private fun JsonObject.sourceCallSites(): List<StudioPreviewSourceCallSite> {
    return optionalArray("sourceCallSites")
        .take(MAXIMUM_SOURCE_CALL_SITES)
        .map { element ->
            val source = element.requiredObject("source call site")
            StudioPreviewSourceCallSite(
                className = source.requiredString("className"),
                methodName = source.requiredString("methodName"),
                fileName = source.requiredString("fileName"),
                lineNumber = source.requiredInt("lineNumber"),
            )
        }
}

private fun JsonObject.toPatchRecord(): StudioPreviewPatchRecord {
    return StudioPreviewPatchRecord(
        operation = requiredString("operation"),
        type = requiredString("type"),
        key = optionalString("key"),
        parentKey = optionalString("parentKey"),
        index = optionalInt("index") ?: 0,
        moved = optionalBoolean("moved") ?: false,
        detail = optionalString("detail"),
        nodeId = optionalString("nodeId"),
        sourceCallSites = sourceCallSites(),
        synthetic = optionalBoolean("synthetic") ?: false,
    )
}

private fun JsonObject?.toCompositionSnapshot(): StudioPreviewCompositionSnapshot {
    return StudioPreviewCompositionSnapshot(
        invalidatedScopeCount = this?.optionalInt("invalidatedScopeCount") ?: 0,
        recomposedScopeCount = this?.optionalInt("recomposedScopeCount") ?: 0,
        skippedScopeCount = this?.optionalInt("skippedScopeCount") ?: 0,
        scopes = this?.optionalArray("scopes").orEmpty().map { element ->
            element.requiredObject("recompose scope").toRecomposeScope()
        },
    )
}

private fun JsonObject.toRecomposeScope(): StudioPreviewRecomposeScope {
    return StudioPreviewRecomposeScope(
        path = requiredString("path"),
        signature = requiredString("signature"),
        depth = optionalInt("depth") ?: 0,
        reasons = optionalArray("reasons").map { element ->
            element.requiredStringValue("recomposition reason")
        },
        recomposed = optionalBoolean("recomposed") ?: false,
        skipped = optionalBoolean("skipped") ?: false,
        locals = optionalArray("locals").map { element ->
            val local = element.requiredObject("composition local")
            StudioPreviewCompositionLocal(
                name = local.requiredString("name"),
                value = local.requiredString("value"),
            )
        },
        sourceCallSites = sourceCallSites(),
    )
}

private fun JsonObject.toDescriptor(): StudioPreviewDescriptor {
    return StudioPreviewDescriptor(
        id = requiredString("id").also(::requireStableId),
        displayName = requiredString("displayName"),
        group = optionalString("group").orEmpty(),
        variants = requiredArray("variants").map { element ->
            val variant = element.requiredObject("variant")
            val configuration = variant.optionalObject("configuration")
            StudioPreviewVariant(
                id = variant.requiredString("id").also(::requireStableId),
                displayName = variant.requiredString("displayName"),
                widthDp = (configuration?.optionalInt("widthDp")
                    ?: DEFAULT_STUDIO_PREVIEW_WIDTH_DP).also { widthDp ->
                    require(widthDp > 0) {
                        "Preview protocol field 'widthDp' must be greater than zero."
                    }
                },
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

private fun JsonObject.requiredLong(name: String): Long {
    return requireNotNull(optionalLong(name)) {
        "Preview protocol field '$name' must be a long."
    }
}

private fun JsonObject.optionalBoolean(name: String): Boolean? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
        "Preview protocol field '$name' must be a boolean when present."
    }
    return value.asBoolean
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

private fun JsonElement.requiredStringValue(label: String): String {
    require(isJsonPrimitive && asJsonPrimitive.isString) {
        "Preview protocol '$label' must be a string."
    }
    return asString
}

private class SnapshotParseBudget {
    private var nodeCount: Int = 0

    fun recordNode(depth: Int) {
        require(depth <= MAXIMUM_RENDER_TREE_DEPTH) {
            "Preview render tree exceeds the maximum depth of $MAXIMUM_RENDER_TREE_DEPTH."
        }
        nodeCount += 1
        require(nodeCount <= MAXIMUM_RENDER_TREE_NODES) {
            "Preview render tree exceeds the maximum node count of $MAXIMUM_RENDER_TREE_NODES."
        }
    }
}

private const val SUPPORTED_PREVIEW_PROTOCOL_VERSION = 1
private const val MAXIMUM_RENDER_SNAPSHOT_BYTES = 16L * 1024L * 1024L
private const val MAXIMUM_SOURCE_CALL_SITES = 16
private const val MAXIMUM_LAYOUT_DIAGNOSTICS = 10_000
private const val MAXIMUM_LAYOUT_DIAGNOSTIC_METRICS = 32
private const val MAXIMUM_NATIVE_VIEW_PROPERTIES = 32
private const val MAXIMUM_NATIVE_VIEW_PROPERTY_NAME_LENGTH = 64
private const val MAXIMUM_NATIVE_VIEW_PROPERTY_VALUE_LENGTH = 256
private const val MAXIMUM_RENDER_TREE_DEPTH = 256
private const val MAXIMUM_RENDER_TREE_NODES = 100_000
private const val DEFAULT_STUDIO_PREVIEW_WIDTH_DP = 411
private val SHA_256_PATTERN = Regex("[a-f0-9]{64}")
private val STABLE_ID_PATTERN = Regex("[a-z0-9]+(?:(?:-|__)[a-z0-9]+)*")
