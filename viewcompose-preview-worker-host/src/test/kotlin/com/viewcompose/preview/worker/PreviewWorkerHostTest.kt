package com.viewcompose.preview.worker

import com.viewcompose.preview.tooling.PreviewBuildInput
import com.viewcompose.preview.tooling.PreviewBuildInputKind
import com.viewcompose.preview.tooling.PreviewBuildManifest
import com.viewcompose.preview.tooling.PreviewConfiguration
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewJvmEntryPoint
import com.viewcompose.preview.tooling.PreviewLayoutDirection
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewTheme
import com.viewcompose.preview.tooling.PreviewVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewWorkerHostTest {
    @Test
    fun `manifest maps to Paparazzi environment without losing resource roles`() {
        val manifest = buildManifest()

        val environment = PreviewWorkerHost.environmentFor(manifest)

        assertEquals("sample.app", environment.packageName)
        assertEquals(35, environment.compileSdkVersion)
        assertEquals(listOf("sample.app", "sample.library"), environment.resourcePackageNames)
        assertEquals(listOf("/project/res"), environment.localResourceDirs)
        assertEquals(listOf("/module/res"), environment.moduleResourceDirs)
        assertEquals(listOf("/cache/library/res"), environment.libraryResourceDirs)
        assertEquals(
            listOf("/project/assets", "/module/assets"),
            environment.allModuleAssetDirs,
        )
        assertEquals(listOf("/cache/library/assets"), environment.libraryAssetDirs)
    }

    @Test
    fun `request maps theme locale direction size and density to Layoutlib device`() {
        val request = renderRequest()

        val device = PreviewWorkerHost.deviceConfigFor(request)

        assertEquals(450, device.screenWidth)
        assertEquals(900, device.screenHeight)
        assertEquals(200, device.xdpi)
        assertEquals(200, device.ydpi)
        assertEquals(1.3f, device.fontScale)
        assertEquals("ar-rEG", device.locale)
        assertEquals(com.android.resources.NightMode.NIGHT, device.nightMode)
        assertEquals(com.android.resources.LayoutDirection.RTL, device.layoutDirection)
    }

    private fun buildManifest(): PreviewBuildManifest {
        return PreviewBuildManifest(
            modulePath = ":app",
            buildVariant = "debug",
            namespace = "sample.app",
            androidGradlePluginVersion = "8.13.2",
            minSdk = 24,
            targetSdk = 35,
            compileSdk = 35,
            sdkDirectory = "/sdk",
            mergedManifestPath = "/project/AndroidManifest.xml",
            artifactRootDirectory = "/project/build/viewcompose-preview/debug",
            resourcePackageNames = listOf("sample.app", "sample.library"),
            inputs = listOf(
                PreviewBuildInput(
                    PreviewBuildInputKind.LocalResourceDirectory,
                    listOf("/project/res"),
                ),
                PreviewBuildInput(
                    PreviewBuildInputKind.ModuleResourceDirectory,
                    listOf("/module/res"),
                ),
                PreviewBuildInput(
                    PreviewBuildInputKind.LibraryResourceDirectory,
                    listOf("/cache/library/res"),
                ),
                PreviewBuildInput(
                    PreviewBuildInputKind.LocalAssetDirectory,
                    listOf("/project/assets"),
                ),
                PreviewBuildInput(
                    PreviewBuildInputKind.ModuleAssetDirectory,
                    listOf("/module/assets"),
                ),
                PreviewBuildInput(
                    PreviewBuildInputKind.LibraryAssetDirectory,
                    listOf("/cache/library/assets"),
                ),
            ),
            inputFingerprint = "a".repeat(64),
        )
    }

    private fun renderRequest(): PreviewRenderRequest {
        val configuration = PreviewConfiguration(
            widthDp = 360,
            heightDp = 720,
            density = 1.25f,
            fontScale = 1.3f,
            localeTags = listOf("ar-EG"),
            layoutDirection = PreviewLayoutDirection.Rtl,
            theme = PreviewTheme.Dark,
        )
        val descriptor = PreviewDescriptor(
            id = "sample",
            displayName = "Sample",
            entryPoint = PreviewJvmEntryPoint(
                ownerClassName = "sample.PreviewKt",
                methodName = "Sample",
            ),
            variants = listOf(
                PreviewVariant(
                    id = "dark",
                    displayName = "Dark",
                    configuration = configuration,
                ),
            ),
        )
        return PreviewRenderRequest(
            requestId = "request",
            descriptor = descriptor,
            variantId = "dark",
            modulePath = ":app",
            buildVariant = "debug",
            outputDirectory = "/output",
        )
    }
}
