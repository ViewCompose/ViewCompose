package com.viewcompose.host.android

import android.content.Context
import android.view.View
import com.viewcompose.ui.environment.UiEnvironmentValues

/** Selects whether a mounted Android View may move between different logical content keys. */
enum class AndroidViewReusePolicy {
    /** Keeps the View with its current logical key until permanent release. */
    Never,

    /** Allows cross-key mounted-tree reuse after the adapter receives a reset callback. */
    Resettable,
}

/** Identifies why a retained Android View is being reset before another binding. */
enum class AndroidViewResetReason {
    /** The physical mounted tree is moving to a different logical lazy-item or pager key. */
    MountedTreeReuse,
}

/**
 * Supplies Android construction inputs for one candidate View.
 *
 * The renderer creates this immutable snapshot on the Android main thread. [context] is the
 * destination container's current themed Context; implementations must not retain it beyond the
 * View or another Android object whose lifecycle they explicitly own.
 *
 * @property context renderer-supplied themed Context used to construct the candidate View
 * @property environment immutable framework environment captured by the candidate VNode
 */
class AndroidViewCreateScope internal constructor(
    val context: Context,
    val environment: UiEnvironmentValues,
)

/**
 * Supplies a typed View and environment for one replay-safe binding.
 *
 * The renderer creates this immutable snapshot on the Android main thread. The scope is valid only
 * for the callback; adapters must not retain it.
 *
 * @param V exact Android View type configured by the adapter
 * @property view renderer-owned View receiving the complete replay-safe state
 * @property environment immutable framework environment captured by the binding VNode
 */
class AndroidViewUpdateScope<V : View> internal constructor(
    val view: V,
    val environment: UiEnvironmentValues,
)

/**
 * Supplies a typed View and environment before cross-key mounted-tree reuse.
 *
 * The renderer creates this immutable snapshot on the Android main thread after the old logical
 * owner has ended and before the next owner's update. The scope is valid only for the callback.
 *
 * @param V exact Android View type reset by the adapter
 * @property view retained renderer-owned View leaving its previous logical key
 * @property environment immutable environment of the logical owner being reset
 */
class AndroidViewResetScope<V : View> internal constructor(
    val view: V,
    val environment: UiEnvironmentValues,
)

/**
 * Supplies a typed View and environment after its containing composition commits.
 *
 * The renderer creates this immutable snapshot on the Android main thread. The scope is valid only
 * for the callback and must not be retained.
 *
 * @param V exact Android View type whose committed state is being published
 * @property view committed renderer-owned View
 * @property environment immutable framework environment of the committed VNode
 */
class AndroidViewCommitScope<V : View> internal constructor(
    val view: V,
    val environment: UiEnvironmentValues,
)

/**
 * Defines the typed transactional contract for one family of renderer-owned Android Views.
 *
 * Callbacks run synchronously on the Android main thread. [update] must apply complete, replay-safe
 * configuration because a later failure can restore the previously committed adapter and state by
 * invoking it again. [onReset] is reserved for cross-logical-key mounted-tree reuse and never runs
 * for an ordinary same-identity update. Non-replayable publication or attachment work belongs in
 * [onCommit], while [onRelease] permanently cleans up only resources owned by this adapter.
 *
 * Adapter instances with the same runtime implementation class are one construction family.
 * Per-instance constructor inputs must therefore be represented by the `constructionKey` supplied
 * to `AndroidView`; render-time values belong in caller-owned immutable state. Implementations must
 * not retain callback scopes or arbitrary state snapshots after their callback returns.
 *
 * @sample com.viewcompose.host.android.samples.typedAndroidViewAdapterSample
 * @param V exact Android View type created and managed by this adapter
 * @param S caller-owned state snapshot applied by [update] and published by [onCommit]
 */
interface AndroidViewAdapter<V : View, S> {
    /**
     * Returns the cross-key reuse policy captured when the VNode is built.
     *
     * The default keeps the View with one logical key. Implementations returning
     * [AndroidViewReusePolicy.Resettable] must make [onReset] sufficient for a different key's
     * complete [update] to bind the retained View safely.
     */
    val reusePolicy: AndroidViewReusePolicy
        get() = AndroidViewReusePolicy.Never

    /**
     * Creates one candidate View on the Android main thread.
     *
     * The returned View becomes renderer-owned. If its initial [update] or a later operation in the
     * same transaction fails, the candidate is passed to [onRelease] and never committed.
     *
     * @param scope destination Context and immutable environment for this construction
     * @return a new unattached View owned by the renderer until permanent release
     */
    fun create(scope: AndroidViewCreateScope): V

    /**
     * Applies the complete replay-safe state to a renderer-owned View on the Android main thread.
     *
     * The callback runs for insertion, a changed same-identity binding, cross-key adoption, and
     * rollback restoration. It may run more than once for an equivalent state and must not publish
     * irreversible external work.
     *
     * @param scope current typed View and immutable environment snapshot
     * @param state caller-owned state for this binding; ViewCompose does not clone mutable values
     */
    fun update(scope: AndroidViewUpdateScope<V>, state: S)

    /**
     * Resets a retained View before it moves to another logical key.
     *
     * This replay-safe callback runs exactly once per accepted cross-key transfer and only when
     * [reusePolicy] is [AndroidViewReusePolicy.Resettable]. The default performs no additional
     * cleanup.
     *
     * @param scope retained typed View and the previous owner's immutable environment
     * @param reason reason the old logical binding is being cleared
     */
    fun onReset(
        scope: AndroidViewResetScope<V>,
        reason: AndroidViewResetReason,
    ) = Unit

    /**
     * Publishes work after the containing composition commits on the Android main thread.
     *
     * The callback runs at most once for each successful insert or rebind, never for skipped or
     * rolled-back work, and may run again for later state. A failure is reported as a committed-frame
     * native effect failure and does not restore the previous View tree.
     *
     * @param scope committed typed View and immutable environment snapshot
     * @param state caller-owned state associated with the committed binding
     */
    fun onCommit(scope: AndroidViewCommitScope<V>, state: S) = Unit

    /**
     * Permanently releases adapter-owned resources for one created View on the Android main thread.
     *
     * The renderer invokes this callback exactly once after candidate rollback, committed
     * replacement or removal, reuse-cache eviction, or session disposal. It must not release
     * caller-owned resources that were merely attached to the View.
     *
     * @param view created View that will never be rebound by this renderer
     */
    fun onRelease(view: V) = Unit
}
