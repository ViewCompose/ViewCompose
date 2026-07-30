package com.viewcompose.preview.tooling

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Canonical JSON codec shared by Gradle, render workers, tests, and the Android Studio plugin.
 */
object PreviewProtocolJson {
    val format: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encodeRequest(request: PreviewRenderRequest): String = format.encodeToString(request)

    fun decodeRequest(value: String): PreviewRenderRequest = format.decodeFromString(value)

    fun encodeResponse(response: PreviewRenderResponse): String = format.encodeToString(response)

    fun decodeResponse(value: String): PreviewRenderResponse = format.decodeFromString(value)

    fun encodeRenderSnapshot(snapshot: PreviewRenderSnapshot): String =
        format.encodeToString(snapshot)

    fun decodeRenderSnapshot(value: String): PreviewRenderSnapshot =
        format.decodeFromString(value)

    fun encodeBuildManifest(manifest: PreviewBuildManifest): String =
        format.encodeToString(manifest)

    fun decodeBuildManifest(value: String): PreviewBuildManifest =
        format.decodeFromString(value)

    fun encodeDescriptorCatalog(catalog: PreviewDescriptorCatalog): String =
        format.encodeToString(catalog)

    fun decodeDescriptorCatalog(value: String): PreviewDescriptorCatalog =
        format.decodeFromString(value)
}
