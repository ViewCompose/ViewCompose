package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.container.DeclarativeCanvasLayout

/**
 * 应用绘制类 modifier。
 * Applies drawing-related modifiers.
 */
internal object ModifierGraphicsApplier {
    /**
     * 将 draw modifier 传递给 DeclarativeCanvasLayout。
     * Passes draw modifiers to DeclarativeCanvasLayout.
     */
    fun applyGraphicsModifiers(
        view: View,
        resolved: ResolvedModifiers,
    ) {
        if (view !is DeclarativeCanvasLayout) return
        view.setDrawModifierElements(resolved.drawElements)
    }
}
