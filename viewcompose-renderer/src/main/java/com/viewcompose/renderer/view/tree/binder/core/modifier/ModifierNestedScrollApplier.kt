package com.viewcompose.renderer.view.tree

import android.view.View
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.container.DeclarativeNestedScrollHostLayout

/**
 * 将 nested scroll modifier 绑定到显式 NestedScrollHost View。
 * Binds nested scroll modifiers to explicit NestedScrollHost Views.
 */
internal object ModifierNestedScrollApplier {
    /**
     * 更新 nested scroll connection 与 dispatcher。
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
     * 释放 nested scroll host 内部持有的 dispatcher 绑定。
     * Releases dispatcher bindings held by the nested scroll host.
     */
    fun dispose(view: View) {
        (view as? DeclarativeNestedScrollHostLayout)?.dispose()
    }
}
