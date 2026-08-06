package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.container.DeclarativeCanvasLayout

/**
 * Applies drawing modifiers.
 * Applies drawing-related modifiers.
 */
internal object ModifierGraphicsApplier {
    /**
     * Passes draw modifiers to DeclarativeCanvasLayout.
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
