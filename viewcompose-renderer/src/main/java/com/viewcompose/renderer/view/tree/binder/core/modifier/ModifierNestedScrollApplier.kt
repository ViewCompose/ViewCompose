package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.container.DeclarativeNestedScrollHostLayout

internal object ModifierNestedScrollApplier {
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

    fun dispose(view: View) {
        (view as? DeclarativeNestedScrollHostLayout)?.dispose()
    }
}
