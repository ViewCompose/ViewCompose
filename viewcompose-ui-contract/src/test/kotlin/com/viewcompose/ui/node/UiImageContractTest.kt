package com.viewcompose.ui.node

import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.unit.dp
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiImageContractTest {
    @Test
    fun `source constructors reject invalid primary data`() {
        assertIllegalArgument("ImageSource.Url.url must not be blank.") {
            ImageSource.Url(" ")
        }
        assertIllegalArgument("ImageSource.Url.url must be an absolute URI.") {
            ImageSource.Url("example.com/image.png")
        }
        assertIllegalArgument("ImageSource.Url.url must use the http or https scheme.") {
            ImageSource.Url("ftp://example.com/image.png")
        }
        assertIllegalArgument("ImageSource.Uri.uri must not be blank.") {
            ImageSource.Uri("")
        }
        assertIllegalArgument("ImageSource.Uri.uri must be an absolute URI.") {
            ImageSource.Uri("images/avatar.png")
        }
        assertIllegalArgument("ImageSource.Resource.resId must be positive.") {
            ImageSource.Resource(0)
        }
        assertIllegalArgument("ImageSource.File.file must have a non-blank path.") {
            ImageSource.File(File(""))
        }
    }

    @Test
    fun `model identity ignores payload and redacts payload from diagnostics`() {
        val first = ImageSource.Model(
            value = Payload("first"),
            stableKey = "avatar-v1",
        )
        val sameIdentity = ImageSource.Model(
            value = Payload("second"),
            stableKey = "avatar-v1",
        )
        val changedKey = ImageSource.Model(
            value = Payload("first"),
            stableKey = "avatar-v2",
        )

        assertEquals(first, sameIdentity)
        assertEquals(first.hashCode(), sameIdentity.hashCode())
        assertNotEquals(first, changedKey)
        assertFalse(first.toString().contains("first"))
        assertTrue(first.toString().contains("avatar-v1"))
    }

    @Test
    fun `model identity includes the payload type discriminator`() {
        val stringModel = ImageSource.Model("same-key", "key")
        val integerModel = ImageSource.Model(1, "key")

        assertNotEquals(stringModel, integerModel)
    }

    @Test
    fun `request options defensively copy extension order`() {
        val mutableExtensions = mutableListOf<TestExtension>()
        val options = UiImageRequestOptions(extensions = mutableExtensions)
        mutableExtensions += TestExtension(stableKey = "late", payload = "late-payload")

        assertTrue(options.extensions.isEmpty())
    }

    @Test
    fun `request option equality uses extension type and stable key without payload traversal`() {
        val first = UiImageRequestOptions(
            extensions = listOf(TestExtension(stableKey = "same", payload = "first-secret")),
        )
        val sameIdentity = UiImageRequestOptions(
            extensions = listOf(TestExtension(stableKey = "same", payload = "second-secret")),
        )
        val changedKey = UiImageRequestOptions(
            extensions = listOf(TestExtension(stableKey = "changed", payload = "first-secret")),
        )
        val changedType = UiImageRequestOptions(
            extensions = listOf(OtherExtension(stableKey = "same")),
        )

        assertEquals(first, sameIdentity)
        assertEquals(first.hashCode(), sameIdentity.hashCode())
        assertNotEquals(first, changedKey)
        assertNotEquals(first, changedType)
        assertFalse(first.toString().contains("first-secret"))
    }

    @Test
    fun `transition and explicit decode dimensions validate their units`() {
        assertIllegalArgument(
            "UiImageDecodeSize.Fixed.width must be finite and positive.",
        ) {
            UiImageDecodeSize.Fixed(width = 0.dp, height = 1.dp)
        }
        assertIllegalArgument(
            "UiImageDecodeSize.Fixed.height must be finite and positive.",
        ) {
            UiImageDecodeSize.Fixed(width = 1.dp, height = UiDp(Float.NaN))
        }
        assertIllegalArgument(
            "UiImageTransition.Crossfade.durationMillis must be non-negative.",
        ) {
            UiImageTransition.Crossfade(durationMillis = -1)
        }
    }

    @Test
    fun `loader owns the returned handle lifecycle`() {
        val disposed = AtomicBoolean(false)
        var disposeCount = 0
        val loader = UiImageLoader { _, _ ->
            UiImageLoadHandle {
                if (disposed.compareAndSet(false, true)) {
                    disposeCount += 1
                }
            }
        }

        val handle = loader.load(
            target = object : UiImageTarget {},
            request = UiImageRequest(source = ImageSource.Url("https://example.com/image.png")),
        )
        handle.dispose()
        handle.dispose()

        assertEquals(1, disposeCount)
    }

    @Test
    fun `resource revision participates in normalized request identity`() {
        val first = UiImageRequest(
            source = ImageSource.Resource(1),
            resourceRevision = 3L,
        )
        val second = first.copy(resourceRevision = 4L)

        assertNotEquals(first, second)
        assertEquals(0L, UiImageRequest(source = ImageSource.Resource(1)).resourceRevision)
    }

    private data class Payload(val label: String)

    private class TestExtension(
        override val stableKey: Any,
        val payload: String,
    ) : UiImageRequestExtension

    private class OtherExtension(
        override val stableKey: Any,
    ) : UiImageRequestExtension

    private fun assertIllegalArgument(
        message: String,
        block: () -> Unit,
    ) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(message, error?.message)
    }
}
