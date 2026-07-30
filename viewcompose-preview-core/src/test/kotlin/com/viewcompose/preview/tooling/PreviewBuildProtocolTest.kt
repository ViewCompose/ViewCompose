package com.viewcompose.preview.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreviewBuildProtocolTest {
    @Test
    fun `build manifest and catalog round trip through canonical json`() {
        val fingerprint = "a".repeat(64)
        val manifest = PreviewBuildManifest(
            modulePath = ":app",
            buildVariant = "debug",
            namespace = "com.example",
            androidGradlePluginVersion = "8.13.2",
            minSdk = 24,
            targetSdk = 35,
            compileSdk = 35,
            sdkDirectory = "/sdk",
            mergedManifestPath = "/project/AndroidManifest.xml",
            artifactRootDirectory = "/project/build/viewcompose-preview/debug",
            resourcePackageNames = listOf("com.example"),
            inputs = listOf(
                PreviewBuildInput(
                    kind = PreviewBuildInputKind.ProjectClassDirectory,
                    paths = listOf("/project/classes"),
                ),
                PreviewBuildInput(
                    kind = PreviewBuildInputKind.RuntimeClasspath,
                    paths = listOf("/cache/a.jar", "/cache/b.jar"),
                ),
            ),
            inputFingerprint = fingerprint,
        )
        val catalog = PreviewDescriptorCatalog(
            modulePath = ":app",
            buildVariant = "debug",
            buildFingerprint = fingerprint,
            descriptors = emptyList(),
        )
        val command = PreviewWorkerCommand(
            buildManifestPath = "/project/build-manifest.json",
            renderRequestPath = "/project/request.json",
            renderResponsePath = "/project/response.json",
            layoutlibRuntimeRoot = "/cache/layoutlib-runtime",
            layoutlibResourcesRoot = "/cache/layoutlib-resources",
        )

        assertEquals(
            manifest,
            PreviewProtocolJson.decodeBuildManifest(
                PreviewProtocolJson.encodeBuildManifest(manifest),
            ),
        )
        assertEquals(
            catalog,
            PreviewProtocolJson.decodeDescriptorCatalog(
                PreviewProtocolJson.encodeDescriptorCatalog(catalog),
            ),
        )
        assertEquals(
            command,
            PreviewProtocolJson.decodeWorkerCommand(
                PreviewProtocolJson.encodeWorkerCommand(command),
            ),
        )
    }

    @Test
    fun `build protocol rejects nondeterministic inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            PreviewBuildInput(
                kind = PreviewBuildInputKind.RuntimeClasspath,
                paths = listOf("/z.jar", "/a.jar"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreviewDescriptorCatalog(
                modulePath = ":app",
                buildVariant = "debug",
                buildFingerprint = "not-a-fingerprint",
                descriptors = emptyList(),
            )
        }
    }
}
