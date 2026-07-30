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
    fun capture(view: View, outputFile: File)
}

/**
 * Captures an already measured and laid out Android View with the platform Canvas pipeline.
 */
object AndroidBitmapCaptureBackend : StaticPreviewCaptureBackend {
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
