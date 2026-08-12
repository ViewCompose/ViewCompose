package com.viewcompose.image.glide

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterInside
import com.bumptech.glide.request.target.Target
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageCachePolicy
import com.viewcompose.ui.node.UiImageDecodeSize
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.UiImageTarget
import com.viewcompose.ui.node.UiImageTransition
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GlideImageLoaderAdapterTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `source mapping preserves general source values`() {
        val adapter = GlideImageLoaderAdapter()
        val cases = listOf(
            ImageSource.Resource(android.R.drawable.ic_menu_gallery) to android.R.drawable.ic_menu_gallery,
            ImageSource.Url("https://example.com/a.png") to "https://example.com/a.png",
            ImageSource.Uri("content://com.example/a") to android.net.Uri.parse("content://com.example/a"),
            ImageSource.File(java.io.File("/tmp/a.png")) to java.io.File("/tmp/a.png"),
        )

        cases.forEach { (source, expected) ->
            assertEquals(expected, adapter.mapSourceForTest(source))
        }
    }

    @Test
    fun `common options map to Glide request options`() {
        val adapter = GlideImageLoaderAdapter()
        val request = UiImageRequest(
            source = ImageSource.Model(value = "payload", stableKey = "avatar-v1"),
            placeholder = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
            error = ImageSource.Resource(android.R.drawable.stat_notify_error),
            options = UiImageRequestOptions(
                decodeSize = UiImageDecodeSize.Fixed(60.dp, 40.dp),
                memoryCachePolicy = UiImageCachePolicy.Disabled,
                diskCachePolicy = UiImageCachePolicy.Disabled,
            ),
            contentScale = ImageContentScale.Crop,
            density = UiDensity(density = 2f, fontScale = 1f),
        )
        val options = adapter
            .buildRequest(Glide.with(context), ImageView(context), request)

        assertEquals(120, options.overrideWidth)
        assertEquals(80, options.overrideHeight)
        assertEquals(DiskCacheStrategy.NONE, options.diskCacheStrategy)
        assertTrue(!options.isMemoryCacheable)
        assertEquals(android.R.drawable.ic_menu_gallery, options.placeholderId)
        assertEquals(android.R.drawable.stat_notify_error, options.errorId)
        assertTrue(options.isTransformationSet)
    }

    @Test
    fun `decode size and inside scale preserve their distinct Glide semantics`() {
        val adapter = GlideImageLoaderAdapter()
        val targetBounds = adapter.buildRequest(
            Glide.with(context),
            ImageView(context),
            UiImageRequest(
                source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                options = UiImageRequestOptions(decodeSize = UiImageDecodeSize.Target),
            ),
        )
        val original = adapter.buildRequest(
            Glide.with(context),
            ImageView(context),
            UiImageRequest(
                source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                options = UiImageRequestOptions(decodeSize = UiImageDecodeSize.Original),
            ),
        )
        val inside = adapter.buildRequest(
            Glide.with(context),
            ImageView(context),
            UiImageRequest(
                source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                contentScale = ImageContentScale.Inside,
            ),
        )

        assertFalse(targetBounds.isValidOverride)
        assertEquals(Target.SIZE_ORIGINAL, original.overrideWidth)
        assertEquals(Target.SIZE_ORIGINAL, original.overrideHeight)
        assertTrue(inside.transformations[Bitmap::class.java] is CenterInside)
    }

    @Test
    fun `default transition preserves Glide configuration while explicit policies override it`() {
        val adapter = GlideImageLoaderAdapter()

        val defaultRequest = adapter.buildRequest(
            Glide.with(context),
            ImageView(context),
            UiImageRequest(source = ImageSource.Resource(android.R.drawable.ic_menu_gallery)),
        )
        val noTransitionRequest = adapter.buildRequest(
            Glide.with(context),
            ImageView(context),
            UiImageRequest(
                source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                options = UiImageRequestOptions(transition = UiImageTransition.None),
            ),
        )
        val crossfadeRequest = adapter.buildRequest(
            Glide.with(context),
            ImageView(context),
            UiImageRequest(
                source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                options = UiImageRequestOptions(
                    transition = UiImageTransition.Crossfade(durationMillis = 180),
                ),
            ),
        )

        assertTrue(defaultRequest.usesDefaultTransitionOptions())
        assertFalse(noTransitionRequest.usesDefaultTransitionOptions())
        assertFalse(crossfadeRequest.usesDefaultTransitionOptions())
    }

    @Test
    fun `resource cache identity changes with revision but remote identity stays loader owned`() {
        val adapter = GlideImageLoaderAdapter()

        assertEquals(
            "viewcompose-resource:${android.R.drawable.ic_menu_gallery}:9",
            adapter.resourceCacheIdentity(
                UiImageRequest(
                    source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                    resourceRevision = 9L,
                ),
            ),
        )
        assertEquals(
            null,
            adapter.resourceCacheIdentity(
                UiImageRequest(
                    source = ImageSource.Url("https://example.com/a.png"),
                    error = ImageSource.Resource(android.R.drawable.stat_notify_error),
                    resourceRevision = 9L,
                ),
            ),
        )
    }

    @Test
    fun `load returns a repeatably clearable handle`() {
        val adapter = GlideImageLoaderAdapter()
        val imageView = ImageView(context)
        val handle = adapter.load(
            target = object : PlatformUiImageTarget {
                override val target: Any = imageView
            },
            request = UiImageRequest(
                source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
            ),
        )

        handle.dispose()
        handle.dispose()
    }

    @Test
    fun `non ImageView targets fail before Glide starts`() {
        val adapter = GlideImageLoaderAdapter()

        val error = runCatching {
            adapter.load(
                target = object : UiImageTarget {},
                request = UiImageRequest(source = ImageSource.Url("https://example.com/a.png")),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun Any.usesDefaultTransitionOptions(): Boolean {
        val field = com.bumptech.glide.RequestBuilder::class.java
            .getDeclaredField("isDefaultTransitionOptionsSet")
            .apply { isAccessible = true }
        return field.getBoolean(this)
    }
}
