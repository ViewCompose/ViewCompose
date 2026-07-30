package com.viewcompose.preview.tooling

import kotlinx.serialization.Serializable

/**
 * Versioned boundary between Gradle/IDE clients and an isolated preview render worker.
 */
object ViewComposePreviewProtocol {
    const val CURRENT_VERSION: Int = 1

    fun requireSupported(version: Int) {
        require(version == CURRENT_VERSION) {
            "Unsupported ViewCompose preview protocol version $version; " +
                "expected $CURRENT_VERSION."
        }
    }
}

/**
 * Source location used for diagnostics and IDE navigation.
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
 */
@Serializable
data class PreviewDescriptor(
    val id: String,
    val displayName: String,
    val group: String = "",
    val entryPoint: PreviewJvmEntryPoint,
    val variants: List<PreviewVariant>,
    val sourceLocation: PreviewSourceLocation? = null,
) {
    init {
        requireStablePreviewId(id, "Preview descriptor id")
        require(displayName.isNotBlank()) { "Preview descriptor displayName must not be blank." }
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
 */
@Serializable
data class PreviewRenderRequest(
    val protocolVersion: Int = ViewComposePreviewProtocol.CURRENT_VERSION,
    val requestId: String,
    val descriptor: PreviewDescriptor,
    val variantId: String,
    val modulePath: String,
    val buildVariant: String,
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
        require(outputDirectory.isNotBlank()) {
            "Preview outputDirectory must not be blank."
        }
    }

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

@Serializable
enum class PreviewDiagnosticSeverity {
    Info,
    Warning,
    Error,
}

/**
 * One structured compile, discovery, render, or export diagnostic.
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
 * Response returned by an isolated preview worker.
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
) {
    init {
        ViewComposePreviewProtocol.requireSupported(protocolVersion)
        require(requestId.isNotBlank()) { "Preview response requestId must not be blank." }
        require(previewId.isNotBlank()) { "Preview response previewId must not be blank." }
        require(variantId.isNotBlank()) { "Preview response variantId must not be blank." }
        require(durationMillis == null || durationMillis >= 0L) {
            "Preview response durationMillis must be null or non-negative."
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
