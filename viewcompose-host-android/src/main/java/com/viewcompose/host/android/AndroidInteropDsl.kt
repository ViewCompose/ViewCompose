package com.viewcompose.host.android

import android.content.Context
import android.view.View
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * 挂载一个 Android [View] 到声明式树中。
 * Mounts an Android [View].
 *
 * [update] 与 [onReset] 必须可重放且只配置传入 View，因为失败渲染会在回滚期间重新绑定旧节点。
 * [update] and [onReset] must be replay-safe and limited to configuring the supplied View because a failed render can rebind the previous node during rollback.
 *
 * 不可重放的外部动作应放在 [onCommit]，它只会在完整 View-tree 事务成功后执行。
 * Non-replayable external actions belong in [onCommit], which runs only after the complete View-tree transaction succeeds.
 *
 * [onRelease] 是已提交移除或 session 释放后的单次资源清理。
 * [onRelease] is one-shot resource cleanup after committed removal or session disposal.
 */
fun UiTreeBuilder.AndroidView(
    factory: (Context) -> View,
    update: (View) -> Unit = {},
    key: Any? = null,
    modifier: Modifier = Modifier,
    onReset: ((View) -> Unit)? = null,
    onRelease: ((View) -> Unit)? = null,
    onCommit: ((View) -> Unit)? = null,
) {
    emit(
        type = NodeType.AndroidView,
        key = key,
        spec = AndroidViewNodeProps(
            factory = { context ->
                factory(context as Context)
            },
            update = { view ->
                update(view as View)
            },
            onReset = onReset?.let { reset ->
                { view -> reset(view as View) }
            },
            onRelease = onRelease?.let { release ->
                { view -> release(view as View) }
            },
            onCommit = onCommit?.let { commit ->
                { view -> commit(view as View) }
            },
        ),
        modifier = modifier,
    )
}

/**
 * 对已挂载的 Android [View] 直接应用可重放配置。
 * Applies replay-safe configuration directly to the mounted Android [View].
 *
 * 该回调参与 renderer apply/rollback 流程，不能执行外部副作用。
 * This callback participates in renderer apply/rollback and must not perform external side effects.
 */
fun Modifier.nativeView(
    key: Any = Unit,
    configure: (View) -> Unit,
): Modifier {
    return then(
        NativeViewElement(
            stableKey = key,
            configure = { view ->
                configure(view as View)
            },
        ),
    )
}
