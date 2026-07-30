package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.PreviewBuildManifest
import com.viewcompose.preview.tooling.PreviewConfiguration
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewDescriptorCatalog
import com.viewcompose.preview.tooling.PreviewJvmEntryPoint
import com.viewcompose.preview.tooling.PreviewVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PreviewRenderPlanTest {
    @Test
    fun `single variant defaults and produces content addressed cache path`() {
        val manifest = manifest()
        val descriptor = descriptor(
            variants = listOf(variant("default")),
        )

        val plan = planPreviewRender(
            manifest = manifest,
            catalog = catalog(manifest, descriptor),
            previewId = descriptor.id,
            requestedVariantId = null,
        )

        assertEquals("default", plan.variant.id)
        assertEquals(
            "render-cache/${manifest.inputFingerprint}/sample-card/default",
            plan.cacheRelativeDirectory,
        )
        assertEquals(
            "${manifest.inputFingerprint}:sample-card:default",
            plan.requestId,
        )
    }

    @Test
    fun `multiple variants require an explicit valid selection`() {
        val manifest = manifest()
        val descriptor = descriptor(
            variants = listOf(variant("light"), variant("dark")),
        )
        val catalog = catalog(manifest, descriptor)

        assertThrows(IllegalArgumentException::class.java) {
            planPreviewRender(
                manifest = manifest,
                catalog = catalog,
                previewId = descriptor.id,
                requestedVariantId = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            planPreviewRender(
                manifest = manifest,
                catalog = catalog,
                previewId = descriptor.id,
                requestedVariantId = "missing",
            )
        }
        assertEquals(
            "dark",
            planPreviewRender(
                manifest = manifest,
                catalog = catalog,
                previewId = descriptor.id,
                requestedVariantId = "dark",
            ).variant.id,
        )
    }

    @Test
    fun `stale catalog is rejected before worker startup`() {
        val manifest = manifest()
        val descriptor = descriptor(listOf(variant("default")))
        val stale = catalog(manifest, descriptor).copy(
            buildFingerprint = "b".repeat(64),
        )

        assertThrows(IllegalArgumentException::class.java) {
            planPreviewRender(
                manifest = manifest,
                catalog = stale,
                previewId = descriptor.id,
                requestedVariantId = null,
            )
        }
    }

    private fun manifest(): PreviewBuildManifest {
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
            resourcePackageNames = listOf("sample.app"),
            inputs = emptyList(),
            inputFingerprint = "a".repeat(64),
        )
    }

    private fun catalog(
        manifest: PreviewBuildManifest,
        descriptor: PreviewDescriptor,
    ): PreviewDescriptorCatalog {
        return PreviewDescriptorCatalog(
            modulePath = manifest.modulePath,
            buildVariant = manifest.buildVariant,
            buildFingerprint = manifest.inputFingerprint,
            descriptors = listOf(descriptor),
        )
    }

    private fun descriptor(variants: List<PreviewVariant>): PreviewDescriptor {
        return PreviewDescriptor(
            id = "sample-card",
            displayName = "Sample card",
            entryPoint = PreviewJvmEntryPoint(
                ownerClassName = "sample.PreviewKt",
                methodName = "SampleCard",
            ),
            variants = variants,
        )
    }

    private fun variant(id: String): PreviewVariant {
        return PreviewVariant(
            id = id,
            displayName = id.replaceFirstChar(Char::uppercase),
            configuration = PreviewConfiguration(),
        )
    }
}
