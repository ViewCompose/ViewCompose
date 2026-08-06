package com.viewcompose.renderer.view.tree

import android.widget.ImageView
import com.viewcompose.renderer.R
import com.viewcompose.renderer.interop.asUiImageTarget
import com.viewcompose.ui.node.UiImageLoadHandle
import com.viewcompose.ui.node.UiImageLoader
import com.viewcompose.ui.node.UiImageRequest

/**
 * Owns the current general image request associated with one [ImageView].
 *
 * The association is deliberately stored on the target instead of in a process-global registry:
 * Android target reuse, renderer rollback, and subtree disposal all follow the target's lifecycle.
 * Callers own the loader and request source; this controller owns only the returned handle.
 */
internal object ImageRequestBindingController {
    private data class Binding(
        val loader: UiImageLoader,
        val request: UiImageRequest,
        val handle: UiImageLoadHandle,
    )

    /**
     * Replaces the current request unless loader identity and normalized request identity are equal.
     *
     * The old handle is detached before disposal so a re-entrant or failing disposal cannot leave a
     * stale binding visible. [beforeStart] runs only for an actual replacement, after old work is
     * disposed and before new work starts. A loader failure leaves the target without a stored
     * handle.
     */
    fun replace(
        view: ImageView,
        loader: UiImageLoader,
        request: UiImageRequest,
        beforeStart: () -> Unit = {},
    ) {
        val previous = view.getTag(R.id.viewcompose_image_request_binding) as? Binding
        if (previous != null && previous.loader === loader && previous.request == request) {
            return
        }
        view.setTag(R.id.viewcompose_image_request_binding, null)
        previous?.handle?.dispose()
        beforeStart()

        val handle = loader.load(
            target = view.asUiImageTarget(),
            request = request,
        )
        view.setTag(
            R.id.viewcompose_image_request_binding,
            Binding(
                loader = loader,
                request = request,
                handle = handle,
            ),
        )
    }

    /** Clears and disposes the current request, if any. */
    fun clear(view: ImageView) {
        val binding = view.getTag(R.id.viewcompose_image_request_binding) as? Binding
        view.setTag(R.id.viewcompose_image_request_binding, null)
        binding?.handle?.dispose()
    }
}
