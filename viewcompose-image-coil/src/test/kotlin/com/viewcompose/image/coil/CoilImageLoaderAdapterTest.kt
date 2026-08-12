package com.viewcompose.image.coil

import android.content.Context
import android.widget.ImageView
import coil3.ImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.crossfadeMillis
import coil3.size.Dimension
import coil3.size.SizeResolver
import com.viewcompose.ui.node.ImageContentScale
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageCachePolicy
import com.viewcompose.ui.node.UiImageRequest
import com.viewcompose.ui.node.UiImageRequestOptions
import com.viewcompose.ui.node.UiImageTarget
import com.viewcompose.ui.node.UiImageTransition
import com.viewcompose.ui.unit.UiDensity
import com.viewcompose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CoilImageLoaderAdapterTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `source mapping preserves general source values`() {
        val adapter = CoilImageLoaderAdapter(ImageLoader.Builder(context).build())
        val imageView = ImageView(context)

        val cases = listOf(
            ImageSource.Resource(android.R.drawable.ic_menu_gallery) to android.R.drawable.ic_menu_gallery,
            ImageSource.Url("https://example.com/a.png") to "https://example.com/a.png",
            ImageSource.Uri("content://com.example/a") to android.net.Uri.parse("content://com.example/a"),
            ImageSource.File(java.io.File("/tmp/a.png")) to java.io.File("/tmp/a.png"),
        )
        cases.forEach { (source, expected) ->
            assertEquals(
                expected,
                adapter.buildRequest(imageView, UiImageRequest(source = source)).data,
            )
        }
    }

    @Test
    fun `common options map to Coil request fields`() {
        val adapter = CoilImageLoaderAdapter(ImageLoader.Builder(context).build())
        val request = UiImageRequest(
            source = ImageSource.Model(value = "payload", stableKey = "avatar-v1"),
            placeholder = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
            error = ImageSource.Resource(android.R.drawable.stat_notify_error),
            options = UiImageRequestOptions(
                decodeSize = com.viewcompose.ui.node.UiImageDecodeSize.Fixed(60.dp, 40.dp),
                memoryCachePolicy = UiImageCachePolicy.Disabled,
                diskCachePolicy = UiImageCachePolicy.Disabled,
                transition = UiImageTransition.Crossfade(250),
            ),
            contentScale = ImageContentScale.Crop,
            density = UiDensity(density = 2f, fontScale = 1f),
        )

        val imageRequest = adapter.buildRequest(ImageView(context), request)

        assertEquals("payload", imageRequest.data)
        val decodedSize = runBlocking { imageRequest.sizeResolver.size() }
        assertEquals(120, (decodedSize.width as Dimension.Pixels).px)
        assertEquals(80, (decodedSize.height as Dimension.Pixels).px)
        assertEquals(coil3.request.CachePolicy.DISABLED, imageRequest.memoryCachePolicy)
        assertEquals(coil3.request.CachePolicy.DISABLED, imageRequest.diskCachePolicy)
        assertEquals(250, imageRequest.crossfadeMillis)
        assertEquals(coil3.size.Scale.FILL, imageRequest.scale)
        assertNotNull(imageRequest.placeholder())
        assertNotNull(imageRequest.error())
    }

    @Test
    fun `original decode size uses Coil original resolver`() {
        val adapter = CoilImageLoaderAdapter(ImageLoader.Builder(context).build())
        val imageRequest = adapter.buildRequest(
            ImageView(context),
            UiImageRequest(
                source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                options = UiImageRequestOptions(
                    decodeSize = com.viewcompose.ui.node.UiImageDecodeSize.Original,
                ),
            ),
        )

        assertSame(SizeResolver.ORIGINAL, imageRequest.sizeResolver)
    }

    @Test
    fun `resource cache identity changes with revision but remote identity stays loader owned`() {
        val adapter = CoilImageLoaderAdapter(ImageLoader.Builder(context).build())

        assertEquals(
            "viewcompose-resource:${android.R.drawable.ic_menu_gallery}:7",
            adapter.resourceCacheIdentity(
                UiImageRequest(
                    source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                    resourceRevision = 7L,
                ),
            ),
        )
        assertEquals(
            null,
            adapter.resourceCacheIdentity(
                UiImageRequest(
                    source = ImageSource.Url("https://example.com/a.png"),
                    placeholder = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
                    resourceRevision = 7L,
                ),
            ),
        )
    }

    @Test
    fun `load returns idempotent handle without shutting down caller loader`() {
        val recordingImageLoader = RecordingImageLoader(context)
        val adapter = CoilImageLoaderAdapter(recordingImageLoader)
        val imageView = ImageView(context)
        val target = object : PlatformUiImageTarget {
            override val target: Any = imageView
        }

        val handle = adapter.load(
            target = target,
            request = UiImageRequest(source = ImageSource.Url("https://example.com/a.png")),
        )
        handle.dispose()
        handle.dispose()

        assertEquals(1, recordingImageLoader.disposable.disposeCount)
        assertEquals(0, recordingImageLoader.shutdownCount)
    }

    @Test
    fun `non ImageView targets fail before enqueue`() {
        val recordingImageLoader = RecordingImageLoader(context)
        val adapter = CoilImageLoaderAdapter(recordingImageLoader)

        val error = runCatching {
            adapter.load(
                target = object : UiImageTarget {},
                request = UiImageRequest(source = ImageSource.Url("https://example.com/a.png")),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, recordingImageLoader.enqueueCount)
    }

    private class RecordingImageLoader(context: Context) : ImageLoader by ImageLoader.Builder(context).build() {
        val disposable = RecordingDisposable()
        var enqueueCount = 0
            private set
        var shutdownCount = 0
            private set

        override fun enqueue(request: ImageRequest): Disposable {
            enqueueCount += 1
            return disposable
        }

        override fun shutdown() {
            shutdownCount += 1
        }
    }

    private class RecordingDisposable : Disposable {
        private val deferred = CompletableDeferred<ImageResult>()
        var disposeCount = 0
            private set

        override val job: Deferred<ImageResult>
            get() = deferred

        override val isDisposed: Boolean
            get() = disposeCount > 0

        override fun dispose() {
            disposeCount += 1
        }
    }
}
