package com.viewcompose.preview.tooling

import kotlinx.serialization.Serializable

/** Versioned boundary between Gradle/IDE clients and an isolated preview render worker. */
object ViewComposePreviewProtocol {
    /** Current wire version; clients and workers require exact equality. */
    const val CURRENT_VERSION: Int = 1

    /**
     * Validates that [version] exactly matches [CURRENT_VERSION].
     *
     * No forward- or backward-compatible fallback is attempted because protocol models affect
     * class loading, filesystem writes, and render interpretation.
     *
     * @throws IllegalArgumentException when versions differ
     */
    fun requireSupported(version: Int) {
        require(version == CURRENT_VERSION) {
            "Unsupported ViewCompose preview protocol version $version; " +
                "expected $CURRENT_VERSION."
        }
    }
}

/**
 * Source location used for diagnostics and IDE navigation.
 *
 * @property filePath non-blank source path exported by discovery
 * @property line one-based source line
 * @property column one-based source column
 * @property symbolName optional source symbol for fallback navigation
 */
@Serializable
data class PreviewSourceLocation(
    val filePath: String,
    val line: Int,
    val column: Int = 1,
    val symbolName: String? = null,
) {
    init {
        require(filePath.isNotBlank()) { "Preview source filePath must not be blank." }
        require(line > 0) { "Preview source line must be greater than zero." }
        require(column > 0) { "Preview source column must be greater than zero." }
    }
}

/**
 * Compiled JVM function that a render worker invokes for a preview.
 *
 * @property ownerClassName binary JVM owner name
 * @property methodName compiled static method name
 * @property methodDescriptor optional JVM descriptor used to disambiguate overloads
 */
@Serializable
data class PreviewJvmEntryPoint(
    val ownerClassName: String,
    val methodName: String,
    val methodDescriptor: String? = null,
) {
    init {
        require(ownerClassName.isNotBlank()) {
            "Preview entry-point ownerClassName must not be blank."
        }
        require(methodName.isNotBlank()) { "Preview entry-point methodName must not be blank." }
    }
}

/**
 * IDE- and renderer-independent description of one preview function.
 *
 * @sample com.viewcompose.preview.tooling.samples.previewProtocolRoundTripSample
 * @property id path-safe stable identity for caching and artifact layout
 * @property displayName non-blank human-readable function label
 * @property group optional IDE grouping label
 * @property entryPoint compiled function invoked by the worker
 * @property variants non-empty configurations with IDs unique within this descriptor
 * @property sourceLocation optional declaration location for diagnostics and navigation
 * @property themeProviderClassName optional application theme-provider binary class name
 */
@Serializable
data class PreviewDescriptor(
    val id: String,
    val displayName: String,
    val group: String = "",
    val entryPoint: PreviewJvmEntryPoint,
    val variants: List<PreviewVariant>,
    val sourceLocation: PreviewSourceLocation? = null,
    val themeProviderClassName: String? = null,
) {
    init {
        requireStablePreviewId(id, "Preview descriptor id")
        require(displayName.isNotBlank()) { "Preview descriptor displayName must not be blank." }
        require(themeProviderClassName == null || themeProviderClassName.isNotBlank()) {
            "Preview descriptor themeProviderClassName must be null or non-blank."
        }
        require(variants.isNotEmpty()) { "Preview descriptor must contain at least one variant." }
        val duplicateVariantIds = variants
            .groupingBy(PreviewVariant::id)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateVariantIds.isEmpty()) {
            "Preview descriptor contains duplicate variant ids: " +
                duplicateVariantIds.sorted().joinToString()
        }
    }
}

/**
 * Canonical artifact directory layout. IDs are path-safe by protocol validation.
 */
object PreviewArtifactLayout {
    /**
     * Returns the path-safe relative artifact directory for one preview variant.
     *
     * @throws IllegalArgumentException when either ID violates the stable-ID grammar
     */
    fun relativeDirectory(
        previewId: String,
        variantId: String,
    ): String {
        requireStablePreviewId(previewId, "Preview artifact preview id")
        requireStablePreviewId(variantId, "Preview artifact variant id")
        return "$previewId/$variantId"
    }
}

/**
 * One isolated static render request.
 *
 * Paths remain strings at this process boundary so the model can be consumed by Gradle, the IDE,
 * and workers using different filesystem abstractions.
 *
 * @property protocolVersion wire-format version
 * @property requestId non-blank correlation ID copied into the response
 * @property descriptor preview and available variants
 * @property variantId selected ID declared by [descriptor]
 * @property modulePath owning Gradle project path
 * @property buildVariant Android variant used for rendering
 * @property buildFingerprint lowercase SHA-256 of render-affecting build inputs
 * @property outputDirectory directory reserved for this request's artifacts
 */
@Serializable
data class PreviewRenderRequest(
    val protocolVersion: Int = ViewComposePreviewProtocol.CURRENT_VERSION,
    val requestId: String,
    val descriptor: PreviewDescriptor,
    val variantId: String,
    val modulePath: String,
    val buildVariant: String,
    val buildFingerprint: String,
    val outputDirectory: String,
) {
    init {
        ViewComposePreviewProtocol.requireSupported(protocolVersion)
        require(requestId.isNotBlank()) { "Preview requestId must not be blank." }
        require(variantId.isNotBlank()) { "Preview variantId must not be blank." }
        require(descriptor.variants.any { variant -> variant.id == variantId }) {
            "Preview variantId '$variantId' is not declared by preview '${descriptor.id}'."
        }
        require(modulePath.isNotBlank()) { "Preview modulePath must not be blank." }
        require(buildVariant.isNotBlank()) { "Preview buildVariant must not be blank." }
        requireSha256(buildFingerprint, "Preview buildFingerprint")
        require(outputDirectory.isNotBlank()) {
            "Preview outputDirectory must not be blank."
        }
    }

    /** Resolved configuration for [variantId]. */
    val configuration: PreviewConfiguration
        get() = descriptor.variants.first { variant -> variant.id == variantId }.configuration
}

/**
 * Stable preview worker outcome. Failures are data rather than thrown across the process boundary.
 */
@Serializable
enum class PreviewRenderStatus {
    Success,
    CompileFailure,
    RenderFailure,
    Cancelled,
    TimedOut,
    ProtocolMismatch,
}

/** Portable diagnostic importance interpreted by Gradle and IDE clients. */
@Serializable
enum class PreviewDiagnosticSeverity {
    Info,
    Warning,
    Error,
}

/**
 * One structured compile, discovery, render, or export diagnostic.
 *
 * @property severity presentation and failure importance
 * @property message non-blank user-facing summary
 * @property phase non-blank pipeline phase identifier
 * @property sourceLocation optional navigable source location
 * @property details optional extended diagnostic text such as a stack trace
 */
@Serializable
data class PreviewDiagnostic(
    val severity: PreviewDiagnosticSeverity,
    val message: String,
    val phase: String,
    val sourceLocation: PreviewSourceLocation? = null,
    val details: String? = null,
) {
    init {
        require(message.isNotBlank()) { "Preview diagnostic message must not be blank." }
        require(phase.isNotBlank()) { "Preview diagnostic phase must not be blank." }
    }
}

/**
 * Files emitted by a successful or partially successful render.
 *
 * At least one path must be non-blank. Paths are host filesystem strings and may be absolute.
 *
 * @property imagePath rendered image path when image export succeeded
 * @property renderTreePath structured render snapshot path when diagnostics export succeeded
 * @property diagnosticsPath optional additional diagnostic artifact path
 */
@Serializable
data class PreviewArtifacts(
    val imagePath: String? = null,
    val renderTreePath: String? = null,
    val diagnosticsPath: String? = null,
) {
    init {
        require(
            listOf(imagePath, renderTreePath, diagnosticsPath).any { path -> !path.isNullOrBlank() },
        ) {
            "Preview artifacts must contain at least one output path."
        }
    }
}

/**
 * One deterministic phase measurement emitted by the isolated render pipeline.
 *
 * @property phase unique non-blank phase name within one response
 * @property durationMillis non-negative elapsed wall time
 */
@Serializable
data class PreviewPhaseTiming(
    val phase: String,
    val durationMillis: Long,
) {
    init {
        require(phase.isNotBlank()) { "Preview timing phase must not be blank." }
        require(durationMillis >= 0L) { "Preview timing duration must be non-negative." }
    }
}

/**
 * Response returned by an isolated preview worker.
 *
 * Success requires an image artifact. Every non-success status requires at least one diagnostic;
 * process-boundary failures are represented as data rather than thrown to clients.
 *
 * @property protocolVersion wire-format version
 * @property requestId correlation ID from the request
 * @property previewId stable descriptor ID
 * @property variantId selected variant ID
 * @property status stable worker outcome
 * @property artifacts exported files, required to contain an image on success
 * @property diagnostics structured warnings and failures
 * @property durationMillis optional non-negative end-to-end duration
 * @property phaseTimings unique per-phase durations
 */
@Serializable
data class PreviewRenderResponse(
    val protocolVersion: Int = ViewComposePreviewProtocol.CURRENT_VERSION,
    val requestId: String,
    val previewId: String,
    val variantId: String,
    val status: PreviewRenderStatus,
    val artifacts: PreviewArtifacts? = null,
    val diagnostics: List<PreviewDiagnostic> = emptyList(),
    val durationMillis: Long? = null,
    val phaseTimings: List<PreviewPhaseTiming> = emptyList(),
) {
    init {
        ViewComposePreviewProtocol.requireSupported(protocolVersion)
        require(requestId.isNotBlank()) { "Preview response requestId must not be blank." }
        require(previewId.isNotBlank()) { "Preview response previewId must not be blank." }
        require(variantId.isNotBlank()) { "Preview response variantId must not be blank." }
        require(durationMillis == null || durationMillis >= 0L) {
            "Preview response durationMillis must be null or non-negative."
        }
        require(phaseTimings.map(PreviewPhaseTiming::phase).distinct().size == phaseTimings.size) {
            "Preview response timing phases must be unique."
        }
        require(status != PreviewRenderStatus.Success || artifacts?.imagePath?.isNotBlank() == true) {
            "A successful preview response must contain an image artifact."
        }
        require(status == PreviewRenderStatus.Success || diagnostics.isNotEmpty()) {
            "A failed preview response must contain at least one diagnostic."
        }
    }
}

internal fun requireStablePreviewId(
    value: String,
    label: String,
) {
    require(PREVIEW_ID_PATTERN.matches(value)) {
        "$label must use lowercase ASCII words separated by hyphens or reserved '__': '$value'."
    }
}

private val PREVIEW_ID_PATTERN = Regex("[a-z0-9]+(?:(?:-|__)[a-z0-9]+)*")
