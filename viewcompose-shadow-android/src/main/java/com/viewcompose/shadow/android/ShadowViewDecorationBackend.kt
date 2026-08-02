package com.viewcompose.shadow.android

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.decoration.AndroidViewDecorationBackend
import com.viewcompose.renderer.decoration.AndroidViewDecorationPresence
import com.viewcompose.renderer.decoration.AndroidViewDecorationRequest

/**
 * Resolves ViewCompose shadow requests and renders them through parent View drawing planes.
 *
 * Instances retain no per-View object map: resolved specs live in resource-keyed View tags, while
 * raster caches are owned process-wide by [ShadowDecorationLayer]. The backend is UI-thread confined.
 */
class ShadowViewDecorationBackend : AndroidViewDecorationBackend {
    /**
     * Resolves and completely replaces the View's outer and inner shadow state.
     *
     * @param view mounted View receiving resource-keyed shadow tags
     * @param request complete current modifier-derived decoration request
     * @return parent drawing planes needed by the resolved non-empty specifications
     */
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

    /** Removes both shadow tags from [view] and invalidates changed drawing state. */
    override fun clear(view: View) {
        ShadowDecorationLayer.update(view, ResolvedShadowSpec.Empty)
        ShadowDecorationLayer.updateInner(view, ResolvedInnerShadowSpec.Empty)
    }

    /** Delegates the parent's behind-child drawing plane to [ShadowDecorationLayer]. */
    override fun drawBehindChild(canvas: Canvas, parent: ViewGroup, child: View) {
        ShadowDecorationLayer.drawBehindChild(canvas, parent, child)
    }

    /** Delegates the parent's over-child drawing plane to [ShadowDecorationLayer]. */
    override fun drawOverChild(canvas: Canvas, parent: ViewGroup, child: View) {
        ShadowDecorationLayer.drawOverChild(canvas, parent, child)
    }
}
