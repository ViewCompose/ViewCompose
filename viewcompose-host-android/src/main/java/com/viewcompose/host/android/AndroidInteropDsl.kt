package com.viewcompose.host.android

import android.content.Context
import android.view.View
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * Mounts an Android [View].
 *
 * [update] and [onReset] must be replay-safe and limited to configuring the supplied View because
 * a failed render can rebind the previous node during rollback. Non-replayable external actions
 * belong in [onCommit], which runs only after the complete View-tree transaction succeeds.
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
 * Applies replay-safe configuration directly to the mounted Android [View].
 *
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
