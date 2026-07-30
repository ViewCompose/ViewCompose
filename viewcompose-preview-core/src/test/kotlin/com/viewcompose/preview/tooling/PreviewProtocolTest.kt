package com.viewcompose.preview.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreviewProtocolTest {
    @Test
    fun `request resolves selected variant configuration`() {
        val light = PreviewVariant(
            id = "light",
            displayName = "Light",
            configuration = PreviewConfiguration(),
        )
        val dark = PreviewVariant(
            id = "dark",
            displayName = "Dark",
            configuration = PreviewConfiguration(theme = PreviewTheme.Dark),
        )

        val request = PreviewRenderRequest(
            requestId = "request-1",
            descriptor = descriptor(light, dark),
            variantId = "dark",
            modulePath = ":app",
            buildVariant = "debug",
            outputDirectory = "build/viewcompose-preview/request-1",
        )

        assertEquals(PreviewTheme.Dark, request.configuration.theme)
    }

    @Test
    fun `request rejects unknown variant and protocol`() {
        val light = PreviewVariant(
            id = "light",
            displayName = "Light",
            configuration = PreviewConfiguration(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            PreviewRenderRequest(
                requestId = "request-1",
                descriptor = descriptor(light),
                variantId = "missing",
                modulePath = ":app",
                buildVariant = "debug",
                outputDirectory = "build/viewcompose-preview/request-1",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ViewComposePreviewProtocol.requireSupported(
                ViewComposePreviewProtocol.CURRENT_VERSION + 1,
            )
        }
    }

    @Test
    fun `success requires image while failure requires diagnostics`() {
        assertThrows(IllegalArgumentException::class.java) {
            PreviewRenderResponse(
                requestId = "request-1",
                previewId = "sample",
                variantId = "light",
                status = PreviewRenderStatus.Success,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreviewRenderResponse(
                requestId = "request-1",
                previewId = "sample",
                variantId = "light",
                status = PreviewRenderStatus.RenderFailure,
            )
        }

        val response = PreviewRenderResponse(
            requestId = "request-1",
            previewId = "sample",
            variantId = "light",
            status = PreviewRenderStatus.Success,
            artifacts = PreviewArtifacts(imagePath = "preview.png"),
        )
        assertEquals(PreviewRenderStatus.Success, response.status)
    }

    private fun descriptor(vararg variants: PreviewVariant): PreviewDescriptor {
        return PreviewDescriptor(
            id = "sample",
            displayName = "Sample",
            entryPoint = PreviewJvmEntryPoint(
                ownerClassName = "sample.PreviewKt",
                methodName = "SamplePreview",
            ),
            variants = variants.toList(),
        )
    }
}
