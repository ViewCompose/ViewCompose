package com.viewcompose.renderer.view.tree

import android.widget.ImageView
import com.viewcompose.renderer.R
import com.viewcompose.renderer.view.lazy.session.LazyItemSessionController
import com.viewcompose.ui.node.ImageSource
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
import com.viewcompose.ui.node.PlatformUiImageTarget
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageRequestBindingControllerTest {
    private val view = ImageView(RuntimeEnvironment.getApplication())

    @Test
    fun `first request stores one handle`() {
        val loader = RecordingLoader()

        ImageRequestBindingController.replace(view, loader, request("first"))

        assertEquals(1, loader.startCount)
        assertNotNull(view.getTag(R.id.viewcompose_image_request_binding))
    }

    @Test
    fun `equivalent request does not restart`() {
        val loader = RecordingLoader()
        val first = request("same")

        ImageRequestBindingController.replace(view, loader, first)
        ImageRequestBindingController.replace(view, loader, request("same"))

        assertEquals(1, loader.startCount)
        assertEquals(0, loader.disposeCount)
    }

    @Test
    fun `changed request disposes before starting replacement`() {
        val events = mutableListOf<String>()
        val loader = RecordingLoader(events = events)

        ImageRequestBindingController.replace(view, loader, request("first"))
        ImageRequestBindingController.replace(view, loader, request("second"))

        assertEquals(listOf("start:first", "dispose:first", "start:second"), events)
    }

    @Test
    fun `changed resource revision restarts an otherwise equal request`() {
        val loader = RecordingResourceLoader()
        val first = UiImageRequest(
            source = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
            resourceRevision = 1L,
        )

        ImageRequestBindingController.replace(view, loader, first)
        ImageRequestBindingController.replace(view, loader, first.copy(resourceRevision = 2L))

        assertEquals(2, loader.startCount)
        assertEquals(1, loader.disposeCount)
    }

    @Test
    fun `out of order completion from a disposed request cannot overwrite the newest binding`() {
        val loader = DelayedLoader()

        ImageRequestBindingController.replace(view, loader, request("first"))
        ImageRequestBindingController.replace(view, loader, request("second"))

        loader.complete("first")
        assertNull(view.contentDescription)

        loader.complete("second")
        assertEquals("loaded:second", view.contentDescription)
    }

    @Test
    fun `loader identity participates in replacement`() {
        val firstLoader = RecordingLoader()
        val secondLoader = RecordingLoader()
        val request = request("same")

        ImageRequestBindingController.replace(view, firstLoader, request)
        ImageRequestBindingController.replace(view, secondLoader, request)

        assertEquals(1, firstLoader.disposeCount)
        assertEquals(1, secondLoader.startCount)
    }

    @Test
    fun `synchronous loader failure leaves no stored handle`() {
        val loader = UiImageLoader { _, _ -> error("start failed") }

        runCatching {
            ImageRequestBindingController.replace(view, loader, request("failure"))
        }

        assertNull(view.getTag(R.id.viewcompose_image_request_binding))
    }

    @Test
    fun `rollback can restart the previous request after replacement fails`() {
        val stableLoader = RecordingLoader()
        val previousRequest = request("stable")
        ImageRequestBindingController.replace(view, stableLoader, previousRequest)
        val failingLoader = UiImageLoader { _, _ -> error("replacement failed") }

        runCatching {
            ImageRequestBindingController.replace(view, failingLoader, request("failure"))
        }
        ImageRequestBindingController.replace(view, stableLoader, previousRequest)

        assertEquals(2, stableLoader.startCount)
        assertEquals(1, stableLoader.disposeCount)
        assertNotNull(view.getTag(R.id.viewcompose_image_request_binding))
    }

    @Test
    fun `clear is idempotent and disposes exactly once`() {
        val loader = RecordingLoader()
        ImageRequestBindingController.replace(view, loader, request("clear"))

        ImageRequestBindingController.clear(view)
        ImageRequestBindingController.clear(view)

        assertEquals(1, loader.disposeCount)
        assertNull(view.getTag(R.id.viewcompose_image_request_binding))
    }

    @Test
    fun `mounted image disposal clears the target binding`() {
        val loader = RecordingLoader()
        ImageRequestBindingController.replace(view, loader, request("mounted"))
        val mounted = mountedImageNode()

        ViewTreeDisposer.disposeMountedNode(mounted)

        assertEquals(1, loader.disposeCount)
        assertNull(mounted.view.getTag(R.id.viewcompose_image_request_binding))
    }

    @Test
    fun `lazy item recycle disposes its mounted image request`() {
        val loader = RecordingLoader()
        ImageRequestBindingController.replace(view, loader, request("lazy"))
        val mounted = mountedImageNode()
        val item = LazyListItem(
            key = "item",
            contentToken = "content",
            sessionFactory = LazyListItemSessionFactory { error("unused") },
        )
        val controller = LazyItemSessionController(
            createSession = {
                object : LazyListItemSession {
                    override fun render() = Unit

                    override fun dispose() {
                        ViewTreeDisposer.disposeMountedNode(mounted)
                    }
                }
            },
            clearContainer = {},
        )

        controller.bind(item)
        controller.recycle()

        assertEquals(1, loader.disposeCount)
        assertNull(view.getTag(R.id.viewcompose_image_request_binding))
    }

    private fun mountedImageNode(): MountedNode {
        return MountedNode(
            vnode = com.viewcompose.ui.node.VNode(
                type = com.viewcompose.ui.node.NodeType.Image,
                spec = com.viewcompose.ui.node.spec.EmptyNodeSpec,
            ),
            view = view,
        )
    }

    private fun request(key: String): UiImageRequest {
        return UiImageRequest(
            source = ImageSource.Model(
                value = key,
                stableKey = key,
            ),
        )
    }

    private class RecordingLoader(
        private val events: MutableList<String> = mutableListOf(),
    ) : UiImageLoader {
        var startCount: Int = 0
            private set
        var disposeCount: Int = 0
            private set

        override fun load(
            target: com.viewcompose.ui.node.UiImageTarget,
            request: UiImageRequest,
        ): UiImageLoadHandle {
            val key = (request.source as ImageSource.Model).stableKey.toString()
            startCount += 1
            events += "start:$key"
            var disposed = false
            return UiImageLoadHandle {
                if (!disposed) {
                    disposed = true
                    disposeCount += 1
                    events += "dispose:$key"
                }
            }
        }
    }

    private class DelayedLoader : UiImageLoader {
        private val operations = mutableMapOf<String, Operation>()

        override fun load(
            target: com.viewcompose.ui.node.UiImageTarget,
            request: UiImageRequest,
        ): UiImageLoadHandle {
            val key = (request.source as ImageSource.Model).stableKey.toString()
            val imageView = (target as PlatformUiImageTarget).target as ImageView
            val operation = Operation(imageView, key)
            operations[key] = operation
            return UiImageLoadHandle { operation.disposed = true }
        }

        fun complete(key: String) {
            operations.getValue(key).complete()
        }

        private class Operation(
            private val imageView: ImageView,
            private val key: String,
            var disposed: Boolean = false,
        ) {
            fun complete() {
                if (!disposed) {
                    imageView.contentDescription = "loaded:$key"
                }
            }
        }
    }

    private class RecordingResourceLoader : UiImageLoader {
        var startCount = 0
        var disposeCount = 0

        override fun load(
            target: com.viewcompose.ui.node.UiImageTarget,
            request: UiImageRequest,
        ): UiImageLoadHandle {
            startCount += 1
            return UiImageLoadHandle { disposeCount += 1 }
        }
    }
}
