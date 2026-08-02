package com.viewcompose.preview.runner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File
import java.io.FileOutputStream

/**
 * Image boundary for the static runner. The IDE worker may replace this with a Layoutlib-backed
 * implementation while tests and device hosts can use [AndroidBitmapCaptureBackend].
 */
fun interface StaticPreviewCaptureBackend {
    /**
     * Captures the already measured and laid-out [view] into [outputFile].
     *
     * Implementations must complete the write before returning and should either publish a valid
     * image or throw. Atomic replacement of the final artifact is owned by `StaticPreviewWorker`.
     *
     * @throws IllegalArgumentException when the input View cannot be captured
     * @throws java.io.IOException when the output cannot be written
     */
    fun capture(view: View, outputFile: File)
}

/**
 * Captures an already measured and laid out Android View with the platform Canvas pipeline.
 */
object AndroidBitmapCaptureBackend : StaticPreviewCaptureBackend {
    /**
     * Draws [view] into an ARGB bitmap and encodes a lossless PNG at [outputFile].
     *
     * The temporary bitmap is recycled on both success and failure.
     *
     * @throws IllegalArgumentException when [view] has not completed measurement and layout
     * @throws IllegalStateException when Android cannot encode the bitmap
     */
    override fun capture(view: View, outputFile: File) {
        require(view.width > 0 && view.height > 0) {
            "Preview view must be measured and laid out before capture."
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            FileOutputStream(outputFile).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Android Bitmap failed to encode preview PNG."
                }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
