package com.viewcompose.preview.runner

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import com.viewcompose.preview.tooling.PreviewConfiguration
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewJvmEntryPoint
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewRenderStatus
import com.viewcompose.preview.tooling.PreviewRenderTreeNode
import com.viewcompose.preview.tooling.PreviewVariant
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiTreeBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class StaticPreviewWorkerPaparazziTest {
    private val runtimeRootFallbackRule = TestRule { base: Statement, _: Description ->
        object : Statement() {
            override fun evaluate() {
                val key = "paparazzi.layoutlib.runtime.root"
                val original = System.getProperty(key)
                val patched = original?.replace("android-36", "android-35")
                if (patched != null && patched != original) {
                    System.setProperty(key, patched)
                }
                try {
                    base.evaluate()
                } finally {
                    if (original == null) {
                        System.clearProperty(key)
                    } else {
                        System.setProperty(key, original)
                    }
                }
            }
        }
    }
    private val temporaryFolder = TemporaryFolder()
    private val paparazzi = Paparazzi(
        environment = detectEnvironment().copy(compileSdkVersion = 35),
        deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(runtimeRootFallbackRule)
        .around(temporaryFolder)
        .around(paparazzi)

    @Test
    fun `worker renders native view image and structured diagnostics without compose`() {
        val outputDirectory = temporaryFolder.newFolder("static-preview")
        val entry = entry()
        val request = request(entry, outputDirectory)

        val response = StaticPreviewWorker().render(
            context = paparazzi.context,
            request = request,
            entry = entry,
        )

        assertEquals(PreviewRenderStatus.Success, response.status)
        val artifacts = checkNotNull(response.artifacts)
        val imageFile = File(checkNotNull(artifacts.imagePath))
        val treeFile = File(checkNotNull(artifacts.renderTreePath))
        assertTrue(imageFile.isFile)
        assertTrue(imageFile.length() > 0L)
        val pngDimensions = imageFile.readPngDimensions()
        assertEquals(request.configuration.widthDp, pngDimensions.first)
        assertEquals(request.configuration.heightDp, pngDimensions.second)
        assertTrue(treeFile.isFile)

        val snapshot = PreviewProtocolJson.decodeRenderSnapshot(treeFile.readText())
        assertEquals(1, snapshot.structure.vnodeCount)
        assertEquals(1, snapshot.stats.inserts)
        assertTrue(snapshot.tree.containsNodeType("Text"))
        assertNotNull(snapshot.composition)
    }

    @Test
    fun `missing compiled entry point returns structured diagnostic`() {
        val entry = entry()
        val request = request(
            entry = entry.copy(
                descriptor = entry.descriptor.copy(
                    entryPoint = PreviewJvmEntryPoint(
                        ownerClassName = "missing.PreviewKt",
                        methodName = "MissingPreview",
                    ),
                ),
            ),
            outputDirectory = temporaryFolder.newFolder("missing-preview"),
        )

        val response = StaticPreviewWorker().render(
            context = paparazzi.context,
            request = request,
            classLoader = checkNotNull(javaClass.classLoader),
        )

        assertEquals(PreviewRenderStatus.RenderFailure, response.status)
        assertEquals("entry-resolution", response.diagnostics.single().phase)
        assertTrue(response.artifacts == null)
    }

    @Test
    fun `resolver invokes public UiTreeBuilder extension entry point`() {
        val entry = entry().let { original ->
            original.copy(
                descriptor = original.descriptor.copy(
                    entryPoint = PreviewJvmEntryPoint(
                        ownerClassName =
                            "com.viewcompose.preview.runner.StaticPreviewWorkerPaparazziTestKt",
                        methodName = "resolvedStaticPreviewEntryPoint",
                        methodDescriptor =
                            "(Lcom/viewcompose/widget/core/UiTreeBuilder;)V",
                    ),
                ),
            )
        }
        val request = request(
            entry = entry,
            outputDirectory = temporaryFolder.newFolder("resolved-preview"),
        )

        val response = StaticPreviewWorker().render(
            context = paparazzi.context,
            request = request,
            classLoader = checkNotNull(javaClass.classLoader),
        )

        assertEquals(PreviewRenderStatus.Success, response.status)
    }

    private fun entry(): StaticPreviewEntry {
        val variant = PreviewVariant(
            id = "phone-light",
            displayName = "Phone Light",
            configuration = PreviewConfiguration(),
        )
        return StaticPreviewEntry(
            descriptor = PreviewDescriptor(
                id = "sample-static-preview",
                displayName = "Sample static preview",
                entryPoint = PreviewJvmEntryPoint(
                    ownerClassName = "sample.SamplePreviewKt",
                    methodName = "SamplePreview",
                ),
                variants = listOf(variant),
            ),
        ) {
            Text("Static preview")
        }
    }

    private fun request(
        entry: StaticPreviewEntry,
        outputDirectory: File,
    ): PreviewRenderRequest {
        return PreviewRenderRequest(
            requestId = "request-static-preview",
            descriptor = entry.descriptor,
            variantId = entry.descriptor.variants.single().id,
            modulePath = ":sample",
            buildVariant = "debug",
            outputDirectory = outputDirectory.absolutePath,
        )
    }

    private fun List<PreviewRenderTreeNode>.containsNodeType(type: String): Boolean {
        return any { node ->
            node.type == type || node.children.containsNodeType(type)
        }
    }

    private fun File.readPngDimensions(): Pair<Int, Int> {
        val header = inputStream().use { input ->
            ByteArray(PNG_HEADER_SIZE).also { bytes ->
                check(input.read(bytes) == bytes.size) { "Incomplete PNG header." }
            }
        }
        check(header.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            "Artifact is not a PNG file."
        }
        return header.readBigEndianInt(16) to header.readBigEndianInt(20)
    }

    private fun ByteArray.readBigEndianInt(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    private companion object {
        const val PNG_HEADER_SIZE: Int = 24
        val PNG_SIGNATURE: ByteArray = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}

fun UiTreeBuilder.resolvedStaticPreviewEntryPoint() {
    Text("Resolved static preview")
}
