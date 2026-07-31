package com.viewcompose.preview.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreviewProtocolTest {
    @Test
    fun `default height is auto while positive heights remain fixed viewports`() {
        val automatic = PreviewConfiguration()
        val fixed = PreviewConfiguration(heightDp = 720)

        assertEquals(PreviewDefaults.AUTO_HEIGHT_DP, automatic.heightDp)
        assertEquals(PreviewDefaults.VIEWPORT_HEIGHT_DP, automatic.viewportHeightDp)
        assertEquals(true, automatic.isAutoHeight)
        assertEquals(720, fixed.viewportHeightDp)
        assertEquals(false, fixed.isAutoHeight)
        assertThrows(IllegalArgumentException::class.java) {
            PreviewConfiguration(heightDp = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreviewConfiguration(heightDp = -2)
        }
    }

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
            buildFingerprint = "a".repeat(64),
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
                buildFingerprint = "a".repeat(64),
                outputDirectory = "build/viewcompose-preview/request-1",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreviewRenderRequest(
                requestId = "request-1",
                descriptor = descriptor(light),
                variantId = light.id,
                modulePath = ":app",
                buildVariant = "debug",
                buildFingerprint = "stale",
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

    @Test
    fun `worker batch round trips and enforces bounded process size`() {
        fun command(index: Int) = PreviewWorkerCommand(
            buildManifestPath = "manifest.json",
            renderRequestPath = "request-$index.json",
            renderResponsePath = "response-$index.json",
            layoutlibRuntimeRoot = "runtime",
            layoutlibResourcesRoot = "resources",
        )
        val batch = PreviewWorkerBatchCommand(commands = listOf(command(1), command(2)))

        assertEquals(
            batch,
            PreviewProtocolJson.decodeWorkerBatchCommand(
                PreviewProtocolJson.encodeWorkerBatchCommand(batch),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            PreviewWorkerBatchCommand(
                commands = (0..MAX_PREVIEW_WORKER_BATCH_SIZE).map(::command),
            )
        }
    }

    @Test
    fun `request response and render snapshot round trip through canonical json`() {
        val variant = PreviewVariant(
            id = "dark-rtl",
            displayName = "Dark RTL",
            configuration = PreviewConfiguration(
                localeTags = listOf("ar-EG", "en-US"),
                layoutDirection = PreviewLayoutDirection.Rtl,
                theme = PreviewTheme.Dark,
            ),
        )
        val request = PreviewRenderRequest(
            requestId = "request-json",
            descriptor = descriptor(variant),
            variantId = variant.id,
            modulePath = ":sample",
            buildVariant = "debug",
            buildFingerprint = "a".repeat(64),
            outputDirectory = "build/preview/request-json",
        )
        val response = PreviewRenderResponse(
            requestId = request.requestId,
            previewId = request.descriptor.id,
            variantId = variant.id,
            status = PreviewRenderStatus.Success,
            artifacts = PreviewArtifacts(
                imagePath = "preview.png",
                renderTreePath = "render-tree.json",
            ),
            durationMillis = 14L,
        )
        val snapshot = PreviewRenderSnapshot(
            stats = PreviewRenderStats(
                inserts = 2,
                bindingsByType = mapOf(
                    "Text" to PreviewNodeBindingStats(rebound = 1),
                ),
            ),
            tree = listOf(
                PreviewRenderTreeNode(
                    type = "Column",
                    key = "root",
                    children = listOf(PreviewRenderTreeNode(type = "Text")),
                ),
            ),
            nativeViewTree = listOf(
                PreviewNativeViewNode(
                    className = "android.widget.FrameLayout",
                    bounds = PreviewLayoutBounds(
                        left = 0,
                        top = 0,
                        right = 1080,
                        bottom = 1920,
                    ),
                    measuredWidth = 1080,
                    measuredHeight = 1920,
                    visibility = "VISIBLE",
                    visibleBounds = PreviewLayoutBounds(
                        left = 0,
                        top = 0,
                        right = 1080,
                        bottom = 1920,
                    ),
                    properties = mapOf(
                        "enabled" to "true",
                        "childCount" to "1",
                    ),
                ),
            ),
            layoutDiagnostics = listOf(
                PreviewLayoutDiagnostic(
                    kind = PreviewLayoutDiagnosticKind.TextEllipsized,
                    severity = PreviewDiagnosticSeverity.Info,
                    className = "android.widget.TextView",
                    bounds = PreviewLayoutBounds(
                        left = 32,
                        top = 48,
                        right = 256,
                        bottom = 112,
                    ),
                    metrics = mapOf(
                        "lineCount" to 1,
                        "ellipsizedLineCount" to 1,
                    ),
                    nodeId = "node-title",
                ),
            ),
            composition = PreviewCompositionSnapshot(
                recomposedScopeCount = 1,
                scopes = listOf(
                    PreviewRecomposeScope(
                        path = "root/0",
                        signature = "SamplePreview",
                        depth = 0,
                        reasons = listOf("InitialComposition"),
                        recomposed = true,
                        skipped = false,
                    ),
                ),
            ),
        )

        assertEquals(
            request,
            PreviewProtocolJson.decodeRequest(PreviewProtocolJson.encodeRequest(request)),
        )
        assertEquals(
            response,
            PreviewProtocolJson.decodeResponse(PreviewProtocolJson.encodeResponse(response)),
        )
        assertEquals(
            snapshot,
            PreviewProtocolJson.decodeRenderSnapshot(
                PreviewProtocolJson.encodeRenderSnapshot(snapshot),
            ),
        )
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
