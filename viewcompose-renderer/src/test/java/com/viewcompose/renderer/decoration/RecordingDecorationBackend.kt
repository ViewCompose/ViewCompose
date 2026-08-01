package com.viewcompose.renderer.decoration

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import java.util.IdentityHashMap

internal class RecordingDecorationBackend : AndroidViewDecorationBackend {
    private val requests = IdentityHashMap<View, AndroidViewDecorationRequest>()

    fun requestOrNull(view: View): AndroidViewDecorationRequest? = requests[view]

    override fun update(
        view: View,
        request: AndroidViewDecorationRequest,
    ): AndroidViewDecorationPresence {
        requests[view] = request
        return AndroidViewDecorationPresence(
            behindChild = request.dropShadows.isNotEmpty(),
            overChild = request.innerShadows.isNotEmpty(),
        )
    }

    override fun clear(view: View) {
        requests.remove(view)
    }

    override fun drawBehindChild(canvas: Canvas, parent: ViewGroup, child: View) = Unit

    override fun drawOverChild(canvas: Canvas, parent: ViewGroup, child: View) = Unit
}
