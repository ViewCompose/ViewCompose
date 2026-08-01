package com.viewcompose.shadow.android

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.decoration.AndroidViewDecorationBackend
import com.viewcompose.renderer.decoration.AndroidViewDecorationPresence
import com.viewcompose.renderer.decoration.AndroidViewDecorationRequest

/** Optional renderer backend that resolves and draws ViewCompose multi-layer shadows. */
class ShadowViewDecorationBackend : AndroidViewDecorationBackend {
    override fun update(
        view: View,
        request: AndroidViewDecorationRequest,
    ): AndroidViewDecorationPresence {
        val outer = ShadowSpecResolver.resolve(
            elements = request.dropShadows,
            defaultShape = request.defaultShape,
            density = request.density,
        )
        val inner = InnerShadowSpecResolver.resolve(
            elements = request.innerShadows,
            defaultShape = request.defaultShape,
            density = request.density,
        )
        ShadowDecorationLayer.update(view, outer)
        ShadowDecorationLayer.updateInner(view, inner)
        return AndroidViewDecorationPresence(
            behindChild = outer.groups.isNotEmpty(),
            overChild = inner.groups.isNotEmpty(),
        )
    }

    override fun clear(view: View) {
        ShadowDecorationLayer.update(view, ResolvedShadowSpec.Empty)
        ShadowDecorationLayer.updateInner(view, ResolvedInnerShadowSpec.Empty)
    }

    override fun drawBehindChild(canvas: Canvas, parent: ViewGroup, child: View) {
        ShadowDecorationLayer.drawBehindChild(canvas, parent, child)
    }

    override fun drawOverChild(canvas: Canvas, parent: ViewGroup, child: View) {
        ShadowDecorationLayer.drawOverChild(canvas, parent, child)
    }
}
