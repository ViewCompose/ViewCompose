package com.viewcompose.preview.tooling.samples

import com.viewcompose.preview.tooling.PreviewConfiguration
import com.viewcompose.preview.tooling.PreviewConfigurationMatrix
import com.viewcompose.preview.tooling.PreviewConfigurationPresets
import com.viewcompose.preview.tooling.PreviewDescriptor
import com.viewcompose.preview.tooling.PreviewJvmEntryPoint
import com.viewcompose.preview.tooling.PreviewProtocolJson
import com.viewcompose.preview.tooling.PreviewRenderRequest
import com.viewcompose.preview.tooling.PreviewVariant

fun previewConfigurationMatrixSample(): List<PreviewVariant> {
    return PreviewConfigurationMatrix(
        axes = listOf(
            PreviewConfigurationPresets.Theme,
            PreviewConfigurationPresets.LayoutDirection,
        ),
    ).variants()
}

fun previewProtocolRoundTripSample(): PreviewRenderRequest {
    val variant = PreviewVariant(
        id = "phone-light",
        displayName = "Phone / Light",
        configuration = PreviewConfiguration(),
    )
    val request = PreviewRenderRequest(
        requestId = "render-1",
        descriptor = PreviewDescriptor(
            id = "account-preview",
            displayName = "Account preview",
            entryPoint = PreviewJvmEntryPoint(
                ownerClassName = "com.example.AccountPreviewsKt",
                methodName = "accountPreview",
                methodDescriptor = "(Lcom/viewcompose/widget/core/UiTreeBuilder;)V",
            ),
            variants = listOf(variant),
        ),
        variantId = variant.id,
        modulePath = ":app",
        buildVariant = "debug",
        buildFingerprint = "0".repeat(64),
        outputDirectory = "build/viewcompose-preview/account-preview/phone-light",
    )
    return PreviewProtocolJson.decodeRequest(PreviewProtocolJson.encodeRequest(request))
}
