package com.viewcompose.host.android

import android.content.Context
import android.view.View
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.NativeViewElement
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.AndroidViewNodeProps
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Mounts a typed Android [View] adapter as a transaction-aware declarative node.
 *
 * [key] identifies logical content. [constructionKey] combines with the adapter's runtime
 * implementation class to identify constructor-sensitive View state. Equal construction identity
 * reuses the current View and invokes only replay-safe update binding; a changed identity creates
 * and updates a candidate atomically, preserving the committed View if candidate work fails.
 *
 * Adapter callbacks run on the Android main thread. [state] remains caller-owned and is retained
 * only by the current and rollback VNodes. [modifier] controls declarative layout, input,
 * semantics, and native configuration around the adapter-owned View. This Q3 API does not provide
 * Lifecycle, SavedState, permission, SDK registry, or background-work ownership.
 *
 * @sample com.viewcompose.host.android.samples.typedAndroidViewAdapterSample
 * @param V exact Android View type created by [adapter]
 * @param S caller-owned state type applied by [AndroidViewAdapter.update]
 * @receiver tree builder receiving the Android View node
 * @param adapter typed lifecycle and transaction contract for the renderer-owned View
 * @param state current complete state snapshot for replay-safe update and committed publication
 * @param key optional stable logical identity used for keyed reconciliation
 * @param constructionKey stable constructor-sensitive identity within the adapter class
 * @param modifier declarative layout, input, semantics, and native configuration
 */
@Suppress("UNCHECKED_CAST")
fun <V : View, S> UiTreeBuilder.AndroidView(
    adapter: AndroidViewAdapter<V, S>,
    state: S,
    key: Any? = null,
    constructionKey: Any? = Unit,
    modifier: Modifier = Modifier,
) {
    val adapterClass = adapter.javaClass
    val callbackPresence = adapter as? AdapterCallbackPresence
    val reusePolicy = adapter.reusePolicy
    emit(
        type = NodeType.AndroidView,
        key = key,
        spec = AndroidViewNodeProps(
            factory = { context, environment ->
                adapter.create(
                    AndroidViewCreateScope(
                        context = context as Context,
                        environment = environment,
                    ),
                )
            },
            update = { view, environment ->
                adapter.update(
                    scope = AndroidViewUpdateScope(
                        view = view as V,
                        environment = environment,
                    ),
                    state = state,
                )
            },
            onReset = when (reusePolicy) {
                AndroidViewReusePolicy.Never -> null
                AndroidViewReusePolicy.Resettable -> { view, environment ->
                    adapter.onReset(
                        scope = AndroidViewResetScope(
                            view = view as V,
                            environment = environment,
                        ),
                        reason = AndroidViewResetReason.MountedTreeReuse,
                    )
                }
            },
            onRelease = if (callbackPresence?.hasReleaseCallback == false) {
                null
            } else {
                { view -> adapter.onRelease(view as V) }
            },
            onCommit = if (callbackPresence?.hasCommitCallback == false) {
                null
            } else {
                { view, environment ->
                    adapter.onCommit(
                        scope = AndroidViewCommitScope(
                            view = view as V,
                            environment = environment,
                        ),
                        state = state,
                    )
                }
            },
            constructionIdentity = AndroidViewConstructionIdentity(
                adapterClass = adapterClass,
                constructionKey = constructionKey,
            ),
            adapterName = callbackPresence?.adapterName
                ?: adapterClass.name.take(MAX_ADAPTER_NAME_LENGTH),
        ),
        modifier = modifier,
    )
}

/**
 * Mounts callback-configured Android [View] content through the typed adapter transaction path.
 *
 * [factory] runs only for a new construction identity. [update] is replay-safe and may run during
 * insertion, changed binding, cross-key adoption, or rollback. [onReset] opts into mounted-tree
 * reuse across logical keys and runs only for that transfer; it is never an ordinary update hook.
 * Non-replayable external work belongs in [onCommit]. [onRelease] performs one-shot permanent
 * cleanup for candidate rollback, replacement, removal, final cache eviction, or session disposal.
 *
 * This Q3 escape hatch is intentionally untyped after construction. Prefer the typed
 * [AndroidViewAdapter] overload for reusable integrations. Callbacks run on the Android main
 * thread. A changed [constructionKey] atomically replaces the View without changing [key].
 *
 * @sample com.viewcompose.host.android.samples.androidViewInteropSample
 * @receiver tree builder receiving the Android View node
 * @param factory creates a native View for a newly inserted construction identity
 * @param update applies complete replay-safe state during insertion, binding, or rollback
 * @param key optional stable logical identity used for keyed reconciliation
 * @param modifier declarative layout, input, semantics, and native configuration
 * @param onReset optional replay-safe cleanup enabling cross-key mounted-tree reuse
 * @param onRelease optional one-shot cleanup after permanent abandonment
 * @param onCommit optional external effect published after the containing composition commits
 * @param constructionKey stable constructor-sensitive identity for callback-created Views
 */
fun UiTreeBuilder.AndroidView(
    factory: (Context) -> View,
    update: (View) -> Unit = {},
    key: Any? = null,
    modifier: Modifier = Modifier,
    onReset: ((View) -> Unit)? = null,
    onRelease: ((View) -> Unit)? = null,
    onCommit: ((View) -> Unit)? = null,
    constructionKey: Any? = Unit,
) {
    AndroidView(
        adapter = CallbackAndroidViewAdapter(
            factory = factory,
            update = update,
            onReset = onReset,
            onRelease = onRelease,
            onCommit = onCommit,
        ),
        state = Unit,
        key = key,
        constructionKey = constructionKey,
        modifier = modifier,
    )
}

private data class AndroidViewConstructionIdentity(
    val adapterClass: Class<*>,
    val constructionKey: Any?,
)

private interface AdapterCallbackPresence {
    val hasCommitCallback: Boolean
    val hasReleaseCallback: Boolean
    val adapterName: String
}

private class CallbackAndroidViewAdapter(
    private val factory: (Context) -> View,
    private val update: (View) -> Unit,
    private val onReset: ((View) -> Unit)?,
    private val onRelease: ((View) -> Unit)?,
    private val onCommit: ((View) -> Unit)?,
) : AndroidViewAdapter<View, Unit>, AdapterCallbackPresence {
    override val reusePolicy: AndroidViewReusePolicy
        get() = if (onReset == null) {
            AndroidViewReusePolicy.Never
        } else {
            AndroidViewReusePolicy.Resettable
        }

    override val hasCommitCallback: Boolean
        get() = onCommit != null

    override val hasReleaseCallback: Boolean
        get() = onRelease != null

    override val adapterName: String
        get() = "callback"

    override fun create(scope: AndroidViewCreateScope): View = factory(scope.context)

    override fun update(scope: AndroidViewUpdateScope<View>, state: Unit) {
        update(scope.view)
    }

    override fun onReset(
        scope: AndroidViewResetScope<View>,
        reason: AndroidViewResetReason,
    ) {
        onReset?.invoke(scope.view)
    }

    override fun onCommit(scope: AndroidViewCommitScope<View>, state: Unit) {
        onCommit?.invoke(scope.view)
    }

    override fun onRelease(view: View) {
        onRelease?.invoke(view)
    }
}

private const val MAX_ADAPTER_NAME_LENGTH = 160

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
