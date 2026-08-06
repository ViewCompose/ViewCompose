package com.viewcompose.renderer.view.tree

import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ImageNodeProps
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MediaViewBinderTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `resource delegates to an installed loader`() {
        val loader = RecordingLoader()
        val view = ImageView(context)

        MediaViewBinder.bindImage(view, spec(loader = loader, source = ImageSource.Resource(1)))

        assertEquals(1, loader.startCount)
        assertEquals(ImageSource.Resource(1), loader.requests.single().source)
    }

    @Test
    fun `image clips scaled drawable inside its padding bounds`() {
        val view = ImageView(context)

        MediaViewBinder.bindImage(
            view,
            spec(loader = null, source = ImageSource.Resource(android.R.drawable.ic_menu_gallery)),
        )

        assertTrue(view.cropToPadding)
    }

    @Test
    fun `resource renders directly without a loader`() {
        val view = ImageView(context)

        MediaViewBinder.bindImage(
            view,
            spec(loader = null, source = ImageSource.Resource(android.R.drawable.ic_menu_gallery)),
        )

        assertDrawableResource(view, android.R.drawable.ic_menu_gallery)
    }

    @Test
    fun `unsupported source without a loader binds error before placeholder and fallback`() {
        val view = ImageView(context)

        MediaViewBinder.bindImage(
            view,
            spec(
                loader = null,
                source = ImageSource.Url("https://example.com/a.png"),
                placeholder = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                error = ImageSource.Resource(android.R.drawable.stat_notify_error),
                fallback = ImageSource.Resource(android.R.drawable.ic_menu_help),
            ),
        )

        assertDrawableResource(view, android.R.drawable.stat_notify_error)
    }

    @Test
    fun `null source binds fallback without invoking loader`() {
        val loader = RecordingLoader()
        val view = ImageView(context)

        MediaViewBinder.bindImage(
            view,
            spec(
                loader = loader,
                source = null,
                fallback = ImageSource.Resource(android.R.drawable.ic_menu_help),
            ),
        )

        assertEquals(0, loader.startCount)
        assertDrawableResource(view, android.R.drawable.ic_menu_help)
    }

    @Test
    fun `removing loader clears stale work before direct resource binding`() {
        val loader = RecordingLoader()
        val view = ImageView(context)
        MediaViewBinder.bindImage(view, spec(loader = loader, source = ImageSource.Url("https://example.com/a.png")))

        MediaViewBinder.bindImage(
            view,
            spec(loader = null, source = ImageSource.Resource(android.R.drawable.ic_menu_gallery)),
        )

        assertEquals(1, loader.disposeCount)
        assertDrawableResource(view, android.R.drawable.ic_menu_gallery)
    }

    @Test
    fun `metadata-only patch does not restart request`() {
        val loader = RecordingLoader()
        val view = ImageView(context)
        val previous = specProps(loader, contentDescription = "old")
        val next = previous.copy(contentDescription = "new", tint = 0xFF112233.toInt())
        MediaViewBinder.bindImage(view, MediaViewBinder.readImageSpec(imageNode(previous)))

        com.viewcompose.renderer.view.tree.patch.MediaNodePatchApplier.applyImagePatch(
            view,
            ImageNodePatch(previous, next),
        )

        assertEquals(1, loader.startCount)
        assertEquals(0, loader.disposeCount)
    }

    @Test
    fun `request option patch restarts request`() {
        val loader = RecordingLoader()
        val view = ImageView(context)
        val previous = specProps(loader)
        val next = previous.copy(
            requestOptions = UiImageRequestOptions(
                decodeSize = com.viewcompose.ui.node.UiImageDecodeSize.Fixed(64.dp, 64.dp),
            ),
        )
        view.setTag(
            com.viewcompose.renderer.R.id.viewcompose_environment_values,
            UiEnvironmentValues.Default,
        )
        MediaViewBinder.bindImage(view, MediaViewBinder.readImageSpec(imageNode(previous)))

        com.viewcompose.renderer.view.tree.patch.MediaNodePatchApplier.applyImagePatch(
            view,
            ImageNodePatch(previous, next),
        )

        assertEquals(2, loader.startCount)
        assertEquals(1, loader.disposeCount)
    }

    @Test
    fun `loader request receives the node environment density`() {
        val loader = RecordingLoader()
        val density = UiDensity(density = 2.5f, fontScale = 1f)
        val node = VNode(
            type = NodeType.Image,
            spec = specProps(loader),
            environment = UiEnvironmentValues(density = density),
        )

        MediaViewBinder.bindImage(ImageView(context), MediaViewBinder.readImageSpec(node))

        assertEquals(density, loader.requests.single().density)
    }

    @Test
    fun `equivalent full bind preserves the loaded result`() {
        val loadedDrawable = ColorDrawable(0xFF336699.toInt())
        var startCount = 0
        val loader = UiImageLoader { target, _ ->
            startCount += 1
            val imageView = (target as com.viewcompose.ui.node.PlatformUiImageTarget).target as ImageView
            imageView.setImageDrawable(loadedDrawable)
            UiImageLoadHandle {}
        }
        val view = ImageView(context)
        val imageSpec = spec(
            loader = loader,
            source = ImageSource.Url("https://example.com/a.png"),
            placeholder = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
        )

        MediaViewBinder.bindImage(view, imageSpec)
        MediaViewBinder.bindImage(view, imageSpec)

        assertEquals(1, startCount)
        assertSame(loadedDrawable, view.drawable)
    }

    private fun spec(
        loader: UiImageLoader?,
        source: ImageSource?,
        placeholder: ImageSource.Resource? = null,
        error: ImageSource.Resource? = null,
        fallback: ImageSource.Resource? = null,
    ): MediaViewBinder.ImageSpec {
        return MediaViewBinder.ImageSpec(
            contentDescription = null,
            contentScale = ImageContentScale.Fit,
            scaleType = ImageView.ScaleType.FIT_CENTER,
            tint = null,
            source = source,
            placeholder = placeholder,
            error = error,
            fallback = fallback,
            imageLoader = loader,
            requestOptions = UiImageRequestOptions(),
            density = UiDensity.Default,
        )
    }

    private fun specProps(
        loader: UiImageLoader,
        contentDescription: String? = null,
    ): ImageNodeProps {
        return ImageNodeProps(
            contentDescription = contentDescription,
            contentScale = ImageContentScale.Fit,
            tint = null,
            source = ImageSource.Url("https://example.com/a.png"),
            placeholder = null,
            error = null,
            fallback = null,
            imageLoader = loader,
        )
    }

    private fun imageNode(spec: ImageNodeProps): VNode {
        return VNode(type = NodeType.Image, spec = spec)
    }

    private fun assertDrawableResource(
        view: ImageView,
        expectedResId: Int,
    ) {
        assertNotNull(view.drawable)
        assertEquals(expectedResId, Shadows.shadowOf(view.drawable).createdFromResId)
    }

    private class RecordingLoader : UiImageLoader {
        val requests = mutableListOf<UiImageRequest>()
        var startCount = 0
        var disposeCount = 0

        override fun load(
            target: com.viewcompose.ui.node.UiImageTarget,
            request: UiImageRequest,
        ): UiImageLoadHandle {
            startCount += 1
            requests += request
            var disposed = false
            return UiImageLoadHandle {
                if (!disposed) {
                    disposed = true
                    disposeCount += 1
                }
            }
        }
    }
}
