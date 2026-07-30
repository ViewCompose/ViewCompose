package com.viewcompose.preview.gradle

import com.viewcompose.preview.tooling.PreviewArtifactLayout
import com.viewcompose.preview.tooling.PreviewBuildManifest
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewDescriptorCatalog
import com.viewcompose.preview.tooling.PreviewVariant

internal data class PreviewRenderPlan(
    val descriptor: PreviewDescriptor,
    val variant: PreviewVariant,
    val requestId: String,
    val cacheRelativeDirectory: String,
)

internal fun planPreviewRender(
    manifest: PreviewBuildManifest,
    catalog: PreviewDescriptorCatalog,
    previewId: String,
    requestedVariantId: String?,
): PreviewRenderPlan {
    require(catalog.modulePath == manifest.modulePath) {
        "Preview catalog module '${catalog.modulePath}' does not match '${manifest.modulePath}'."
    }
    require(catalog.buildVariant == manifest.buildVariant) {
        "Preview catalog variant '${catalog.buildVariant}' does not match " +
            "'${manifest.buildVariant}'."
    }
    require(catalog.buildFingerprint == manifest.inputFingerprint) {
        "Preview catalog fingerprint '${catalog.buildFingerprint}' does not match " +
            "'${manifest.inputFingerprint}'."
    }
    val descriptor = requireNotNull(
        catalog.descriptors.firstOrNull { candidate -> candidate.id == previewId },
    ) {
        "Unknown ViewCompose preview '$previewId'. Available previews: " +
            catalog.descriptors.joinToString { descriptor -> descriptor.id }
    }
    val variant = if (requestedVariantId == null) {
        require(descriptor.variants.size == 1) {
            "Preview '$previewId' declares ${descriptor.variants.size} variants; " +
                "--variant-id is required."
        }
        descriptor.variants.single()
    } else {
        requireNotNull(
            descriptor.variants.firstOrNull { candidate -> candidate.id == requestedVariantId },
        ) {
            "Unknown variant '$requestedVariantId' for preview '$previewId'. Available variants: " +
                descriptor.variants.joinToString { candidate -> candidate.id }
        }
    }
    val artifactDirectory = PreviewArtifactLayout.relativeDirectory(
        previewId = descriptor.id,
        variantId = variant.id,
    )
    return PreviewRenderPlan(
        descriptor = descriptor,
        variant = variant,
        requestId = "${manifest.inputFingerprint}:${descriptor.id}:${variant.id}",
        cacheRelativeDirectory =
            "render-cache/${manifest.inputFingerprint}/$artifactDirectory",
    )
}
