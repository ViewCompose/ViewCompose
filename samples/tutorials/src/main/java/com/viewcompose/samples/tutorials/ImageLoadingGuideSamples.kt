package com.viewcompose.samples.tutorials

import android.widget.ImageView
import coil3.ImageLoader
import com.viewcompose.image.coil.CoilImageLoaderAdapter
import com.viewcompose.image.glide.GlideImageLoaderAdapter
import com.viewcompose.ui.foundation.Image
import com.viewcompose.ui.foundation.ProvideImageLoader
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageCachePolicy
import com.viewcompose.ui.node.UiImageDecodeSize
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.UiImageTarget
import com.viewcompose.ui.node.UiImageTransition
import com.viewcompose.ui.unit.dp
import java.io.File

private fun UiTreeBuilder.installImageLoaderSample(
    applicationCoilImageLoader: ImageLoader,
) {
    // DOCS_REGION_START(image-loader-install)
val imageLoader = CoilImageLoaderAdapter(applicationCoilImageLoader)

ProvideImageLoader(imageLoader) {
    Image(
        source = ImageSource.Url("https://example.test/banner.png"),
        contentDescription = "Banner",
        placeholder = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
        error = ImageSource.Resource(android.R.drawable.ic_dialog_alert),
        fallback = ImageSource.Resource(android.R.drawable.ic_menu_report_image),
        requestOptions = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Target,
            memoryCachePolicy = UiImageCachePolicy.Default,
            diskCachePolicy = UiImageCachePolicy.Default,
            transition = UiImageTransition.Crossfade(durationMillis = 180),
        ),
    )
}
    // DOCS_REGION_END(image-loader-install)
}

// DOCS_REGION_START(image-custom-loader)
class TestImageLoader : UiImageLoader {
    override fun load(target: UiImageTarget, request: UiImageRequest): UiImageLoadHandle {
        val imageView = (target as PlatformUiImageTarget).target as ImageView
        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        return UiImageLoadHandle { /* cancel only this request */ }
    }
}
// DOCS_REGION_END(image-custom-loader)

private fun UiTreeBuilder.coilUriSample(
    applicationCoilImageLoader: ImageLoader,
    contentUri: String,
) {
    // DOCS_REGION_START(image-coil-uri)
val imageLoader = CoilImageLoaderAdapter(applicationCoilImageLoader)
ProvideImageLoader(imageLoader) {
    Image(source = ImageSource.Uri(contentUri), contentDescription = "Content")
}
    // DOCS_REGION_END(image-coil-uri)
}

private fun UiTreeBuilder.glideFileSample(file: File) {
    // DOCS_REGION_START(image-glide-file)
val imageLoader = GlideImageLoaderAdapter()
ProvideImageLoader(imageLoader) {
    Image(
        source = ImageSource.File(file),
        contentDescription = "Downloaded image",
        requestOptions = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Fixed(width = 640.dp, height = 360.dp),
            transition = UiImageTransition.None,
        ),
    )
}
    // DOCS_REGION_END(image-glide-file)
}

private fun UiTreeBuilder.generalizedImageMigrationSample(
    imageLoader: ImageLoader,
    url: String,
) {
    // DOCS_REGION_START(image-migration-generalized)
ProvideImageLoader(CoilImageLoaderAdapter(imageLoader)) {
    Image(
        source = ImageSource.Url(url),
        requestOptions = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Target,
        ),
    )
}
    // DOCS_REGION_END(image-migration-generalized)
}

private fun UiTreeBuilder.imageModelMigrationSample(
    model: Any,
    modelId: String,
) {
    // DOCS_REGION_START(image-migration-model)
Image(source = ImageSource.Model(value = model, stableKey = modelId))
    // DOCS_REGION_END(image-migration-model)
}
