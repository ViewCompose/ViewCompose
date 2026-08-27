package com.viewcompose.camerax.samples

import androidx.camera.lifecycle.ProcessCameraProvider
import com.viewcompose.camerax.CameraXLensFacing
import com.viewcompose.camerax.CameraXPreviewConfiguration
import com.viewcompose.camerax.CameraXPreviewView
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts one integration-owned preview after the caller has resolved permission and a provider. */
// DOCS_REGION_START(camerax-preview)
fun UiTreeBuilder.cameraXPreviewViewSample(provider: ProcessCameraProvider?) {
    CameraXPreviewView(
        cameraProvider = provider,
        lensFacing = CameraXLensFacing.Back,
        configuration = CameraXPreviewConfiguration(
            contentDescription = "Document camera preview",
        ),
        onFailure = { failure ->
            // Present failure.reason and let the application decide whether or when to retry.
        },
    )
}
// DOCS_REGION_END(camerax-preview)
