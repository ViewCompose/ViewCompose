package com.viewcompose.preview.runner

import app.cash.paparazzi.detectEnvironment
import com.viewcompose.preview.tooling.PreviewBuildManifest
import com.viewcompose.preview.tooling.PreviewConfiguration
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewJvmEntryPoint
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderStatus
import com.viewcompose.preview.tooling.PreviewVariant
import com.viewcompose.preview.tooling.PreviewWorkerCommand
import com.viewcompose.preview.worker.PreviewWorkerHost
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewWorkerHostIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `standalone host owns Layoutlib lifecycle and invokes native runner`() {
        val environment = detectEnvironment().copy(compileSdkVersion = COMPILE_SDK)
        val outputDirectory = temporaryFolder.newFolder("artifacts")
        val descriptor = PreviewDescriptor(
            id = "host-integration",
            displayName = "Host integration",
            entryPoint = PreviewJvmEntryPoint(
                ownerClassName =
                    "com.viewcompose.preview.runner.StaticPreviewWorkerPaparazziTestKt",
                methodName = "resolvedStaticPreviewEntryPoint",
                methodDescriptor = "(Lcom/viewcompose/widget/core/UiTreeBuilder;)V",
            ),
            variants = listOf(
                PreviewVariant(
                    id = "default",
                    displayName = "Default",
                    configuration = PreviewConfiguration(apiLevel = COMPILE_SDK),
                ),
            ),
        )
        val request = PreviewRenderRequest(
            requestId = "host-integration-request",
            descriptor = descriptor,
            variantId = "default",
            modulePath = ":viewcompose-preview-runner",
            buildVariant = "debug",
            buildFingerprint = "a".repeat(64),
            outputDirectory = outputDirectory.absolutePath,
        )
        val manifest = PreviewBuildManifest(
            modulePath = request.modulePath,
            buildVariant = request.buildVariant,
            namespace = checkNotNull(environment.packageName),
            androidGradlePluginVersion = "8.13.2",
            minSdk = 24,
            targetSdk = COMPILE_SDK,
            compileSdk = COMPILE_SDK,
            sdkDirectory = File(checkNotNull(System.getProperty("user.dir"))).absolutePath,
            mergedManifestPath = temporaryFolder.newFile("AndroidManifest.xml").apply {
                writeText("<manifest />")
            }.absolutePath,
            artifactRootDirectory = temporaryFolder.root.absolutePath,
            resourcePackageNames = environment.resourcePackageNames.distinct().sorted(),
            inputs = emptyList(),
            inputFingerprint = "a".repeat(64),
        )
        val manifestFile = temporaryFolder.newFile("build-manifest.json").apply {
            writeText(PreviewProtocolJson.encodeBuildManifest(manifest))
        }
        val requestFile = temporaryFolder.newFile("request.json").apply {
            writeText(PreviewProtocolJson.encodeRequest(request))
        }
        val responseFile = temporaryFolder.root.resolve("response.json")
        val commandFile = temporaryFolder.newFile("command.json").apply {
            writeText(
                PreviewProtocolJson.encodeWorkerCommand(
                    PreviewWorkerCommand(
                        buildManifestPath = manifestFile.absolutePath,
                        renderRequestPath = requestFile.absolutePath,
                        renderResponsePath = responseFile.absolutePath,
                        layoutlibRuntimeRoot = layoutlibRoot(LAYOUTLIB_RUNTIME_PROPERTY),
                        layoutlibResourcesRoot = layoutlibRoot(LAYOUTLIB_RESOURCES_PROPERTY),
                    ),
                ),
            )
        }

        val response = PreviewWorkerHost.execute(commandFile)

        assertEquals(PreviewRenderStatus.Success, response.status)
        assertTrue(responseFile.isFile)
        assertEquals(
            response,
            PreviewProtocolJson.decodeResponse(responseFile.readText()),
        )
        assertTrue(File(checkNotNull(response.artifacts?.imagePath)).isFile)
        assertTrue(File(checkNotNull(response.artifacts?.renderTreePath)).isFile)
    }

    private fun layoutlibRoot(property: String): String {
        val configured = checkNotNull(System.getProperty(property)) {
            "Missing Paparazzi test property '$property'."
        }
        val compileSdkRoot = configured.replace("android-36", "android-$COMPILE_SDK")
        return File(compileSdkRoot).absolutePath
    }

    private companion object {
        const val COMPILE_SDK = 35
        const val LAYOUTLIB_RUNTIME_PROPERTY = "paparazzi.layoutlib.runtime.root"
        const val LAYOUTLIB_RESOURCES_PROPERTY = "paparazzi.layoutlib.resources.root"
    }
}
