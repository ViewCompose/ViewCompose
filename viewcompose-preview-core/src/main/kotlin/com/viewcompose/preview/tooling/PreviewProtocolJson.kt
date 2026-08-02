package com.viewcompose.preview.tooling

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Canonical JSON codec shared by Gradle, render workers, tests, and the Android Studio plugin.
 */
object PreviewProtocolJson {
    /**
     * Canonical serializer configuration.
     *
     * Defaults are encoded for deterministic artifacts, unknown keys are ignored for additive
     * readers, explicit nulls are omitted, and output is pretty-printed for diagnostics.
     */
    val format: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    /** Encodes [request] to canonical protocol JSON. */
    fun encodeRequest(request: PreviewRenderRequest): String = format.encodeToString(request)

    /** Decodes and validates one render request from [value]. */
    fun decodeRequest(value: String): PreviewRenderRequest = format.decodeFromString(value)

    /** Encodes [response] to canonical protocol JSON. */
    fun encodeResponse(response: PreviewRenderResponse): String = format.encodeToString(response)

    /** Decodes and validates one render response from [value]. */
    fun decodeResponse(value: String): PreviewRenderResponse = format.decodeFromString(value)

    /** Encodes [snapshot] to canonical protocol JSON. */
    fun encodeRenderSnapshot(snapshot: PreviewRenderSnapshot): String =
        format.encodeToString(snapshot)

    /** Decodes one platform-neutral render snapshot from [value]. */
    fun decodeRenderSnapshot(value: String): PreviewRenderSnapshot =
        format.decodeFromString(value)

    /** Encodes [manifest] to canonical protocol JSON. */
    fun encodeBuildManifest(manifest: PreviewBuildManifest): String =
        format.encodeToString(manifest)

    /** Decodes and validates one build manifest from [value]. */
    fun decodeBuildManifest(value: String): PreviewBuildManifest =
        format.decodeFromString(value)

    /** Encodes [catalog] to canonical protocol JSON. */
    fun encodeDescriptorCatalog(catalog: PreviewDescriptorCatalog): String =
        format.encodeToString(catalog)

    /** Decodes and validates one descriptor catalog from [value]. */
    fun decodeDescriptorCatalog(value: String): PreviewDescriptorCatalog =
        format.decodeFromString(value)

    /** Encodes [command] to canonical protocol JSON. */
    fun encodeWorkerCommand(command: PreviewWorkerCommand): String =
        format.encodeToString(command)

    /** Decodes and validates one worker command from [value]. */
    fun decodeWorkerCommand(value: String): PreviewWorkerCommand =
        format.decodeFromString(value)

    /** Encodes [command] to canonical protocol JSON. */
    fun encodeWorkerBatchCommand(command: PreviewWorkerBatchCommand): String =
        format.encodeToString(command)

    /** Decodes and validates one worker batch from [value]. */
    fun decodeWorkerBatchCommand(value: String): PreviewWorkerBatchCommand =
        format.decodeFromString(value)
}
