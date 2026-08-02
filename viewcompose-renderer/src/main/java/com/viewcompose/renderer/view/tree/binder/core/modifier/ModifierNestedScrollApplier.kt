package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.container.DeclarativeNestedScrollHostLayout

/**
 * Binds nested-scroll modifiers to an explicit NestedScrollHost View.
 * Binds nested scroll modifiers to explicit NestedScrollHost Views.
 */
internal object ModifierNestedScrollApplier {
    /**
     * Updates the nested-scroll connection and dispatcher.
     * Updates nested scroll connection and dispatcher.
     */
    fun apply(
        view: View,
        resolved: ResolvedModifiers,
    ) {
        val host = view as? DeclarativeNestedScrollHostLayout ?: return
        val nestedScroll = checkNotNull(resolved.nestedScroll) {
            "NestedScrollHost requires a NestedScrollModifierElement."
        }
        host.update(
            connection = nestedScroll.connection,
            dispatcher = nestedScroll.dispatcher,
        )
    }

    /**
     * Releases the dispatcher binding retained by a nested-scroll host.
     * Releases dispatcher bindings held by the nested scroll host.
     */
    fun dispose(view: View) {
        (view as? DeclarativeNestedScrollHostLayout)?.dispose()
    }
}
