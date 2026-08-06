package com.viewcompose.host.android

import android.content.Context
import android.view.View
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Mounts an Android [View] as a transaction-aware declarative node.
 *
 * [factory] runs only when reconciliation cannot reuse an existing node. [update] and [onReset] must
 * be replay-safe because a failed frame can restore and rebind the previously committed node.
 * Non-replayable external work belongs in [onCommit], which runs only after the complete View-tree
 * transaction succeeds. [onRelease] is one-shot cleanup after committed removal or session disposal.
 *
 * @sample com.viewcompose.host.android.samples.androidViewInteropSample
 * @param factory creates a native View for a newly inserted node
 * @param update applies replay-safe state during insertion, patching, or rollback
 * @param key optional stable identity used for keyed reconciliation
 * @param modifier declarative layout, input, semantics, and native configuration
 * @param onReset optional replay-safe reset before a retained View is rebound
 * @param onRelease optional one-shot cleanup after permanent removal
 * @param onCommit optional one-shot effect after the containing frame commits
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
 * Applies replay-safe configuration directly to a mounted Android [View].
 *
 * [configure] participates in renderer apply and rollback, so it may run more than once and must not
 * perform external side effects. Changing [key] replaces the modifier operation's identity.
 *
 * @param key stable identity for this native operation
 * @param configure replay-safe View configuration
 * @return this modifier followed by the native operation
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
