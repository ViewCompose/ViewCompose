package com.viewcompose.preview.tooling

import kotlinx.serialization.Serializable

/**
 * Stable roles for files exported by the Gradle bridge.
 *
 * Paths stay opaque to preview-core. Gradle resolves them, while the isolated worker and IDE only
 * consume the resulting manifest.
 */
@Serializable
enum class PreviewBuildInputKind {
    ProjectClassDirectory,
    ProjectClassJar,
    RuntimeClasspath,
    BootClasspath,
    SourceDirectory,
    ResourceDirectory,
}

/**
 * One deterministic, non-empty group of build inputs.
 */
@Serializable
data class PreviewBuildInput(
    val kind: PreviewBuildInputKind,
    val paths: List<String>,
) {
    init {
        require(paths.isNotEmpty()) { "Preview build input '$kind' must contain paths." }
        require(paths.none(String::isBlank)) {
            "Preview build input '$kind' must not contain blank paths."
        }
        require(paths == paths.distinct().sorted()) {
            "Preview build input '$kind' paths must be unique and sorted."
        }
    }
}

/**
 * Fully resolved Android build target consumed by discovery, rendering, and IDE tooling.
 */
@Serializable
data class PreviewBuildManifest(
    val protocolVersion: Int = ViewComposePreviewProtocol.CURRENT_VERSION,
    val modulePath: String,
    val buildVariant: String,
    val namespace: String,
    val androidGradlePluginVersion: String,
    val minSdk: Int,
    val targetSdk: Int,
    val sdkDirectory: String,
    val mergedManifestPath: String,
    val artifactRootDirectory: String,
    val inputs: List<PreviewBuildInput>,
    val inputFingerprint: String,
) {
    init {
        ViewComposePreviewProtocol.requireSupported(protocolVersion)
        require(modulePath.isNotBlank()) { "Preview build modulePath must not be blank." }
        require(buildVariant.isNotBlank()) { "Preview buildVariant must not be blank." }
        require(namespace.isNotBlank()) { "Preview build namespace must not be blank." }
        require(androidGradlePluginVersion.isNotBlank()) {
            "Preview Android Gradle Plugin version must not be blank."
        }
        require(minSdk > 0) { "Preview build minSdk must be greater than zero." }
        require(targetSdk > 0) { "Preview build targetSdk must be greater than zero." }
        require(sdkDirectory.isNotBlank()) { "Preview SDK directory must not be blank." }
        require(mergedManifestPath.isNotBlank()) {
            "Preview merged manifest path must not be blank."
        }
        require(artifactRootDirectory.isNotBlank()) {
            "Preview artifact root directory must not be blank."
        }
        require(inputs.map(PreviewBuildInput::kind).distinct().size == inputs.size) {
            "Preview build input kinds must be unique."
        }
        require(inputs == inputs.sortedBy { input -> input.kind.ordinal }) {
            "Preview build inputs must be sorted by kind."
        }
        require(SHA_256_PATTERN.matches(inputFingerprint)) {
            "Preview inputFingerprint must be a lowercase SHA-256 value."
        }
    }
}

/**
 * Machine-readable discovery result written even when individual preview functions are invalid.
 */
@Serializable
data class PreviewDescriptorCatalog(
    val protocolVersion: Int = ViewComposePreviewProtocol.CURRENT_VERSION,
    val modulePath: String,
    val buildVariant: String,
    val buildFingerprint: String,
    val descriptors: List<PreviewDescriptor>,
    val diagnostics: List<PreviewDiagnostic> = emptyList(),
) {
    init {
        ViewComposePreviewProtocol.requireSupported(protocolVersion)
        require(modulePath.isNotBlank()) { "Preview catalog modulePath must not be blank." }
        require(buildVariant.isNotBlank()) { "Preview catalog buildVariant must not be blank." }
        require(SHA_256_PATTERN.matches(buildFingerprint)) {
            "Preview catalog buildFingerprint must be a lowercase SHA-256 value."
        }
        require(descriptors.map(PreviewDescriptor::id).distinct().size == descriptors.size) {
            "Preview catalog descriptor ids must be unique."
        }
        require(descriptors == descriptors.sortedBy(PreviewDescriptor::id)) {
            "Preview catalog descriptors must be sorted by id."
        }
    }
}

private val SHA_256_PATTERN = Regex("[a-f0-9]{64}")
