package com.viewcompose.ui.node

import com.viewcompose.ui.node.policy.GridItemSpan

/**
 * Describes one keyed, lazily rendered collection item and its child-session lifecycle.
 *
 * [contentRevision] is the caller-owned semantic content version. [environmentRevision] is the
 * framework-owned environment version captured by delayed-content DSLs. Equality deliberately
 * excludes [sessionFactory] and [sessionUpdater]: callback allocation is never an invalidation
 * signal. Equal key and revisions skip the child render completely; changing either revision
 * installs the latest captured content and renders that logical session once. Changing
 * [contentType], including under the same key and revisions, terminates the old logical session
 * and requires a full presentation rebuild.
 *
 * A value captured by item content must either be observable State or participate in
 * [contentRevision]. Omitting a changing value is an explicit promise that it remains stable for
 * this key. Renderers prepare, activate, update, render, and dispose sessions on their owning UI
 * thread.
 *
 * @sample com.viewcompose.ui.samples.lazyListItemSessionUpdateSample
 *
 * @property key stable semantic identity used for item reconciliation and logical-session ownership
 * @property contentRevision caller-owned semantic content version compared during diffing
 * @property environmentRevision framework-owned environment version compared during diffing
 * @property contentType optional renderer reuse classification
 * @property kind normal item or sticky-header behavior
 * @property span renderer-neutral grid span policy
 * @property sessionFactory factory invoked when a renderer needs a child render session
 * @property sessionUpdater callback that installs latest captured content into a retained session
 */
class LazyListItem(
    val key: Any,
    val contentRevision: Any?,
    val environmentRevision: Any? = null,
    val contentType: Any? = null,
    val kind: LazyListItemKind = LazyListItemKind.Item,
    val span: GridItemSpan = GridItemSpan.Single,
    val sessionFactory: LazyListItemSessionFactory,
    val sessionUpdater: (LazyListItemSession) -> Unit,
) {
    /**
     * Compares semantic item identity, content version, reuse type, kind, and span.
     *
     * @param other value to compare
     * @return `true` when all diff-relevant fields are equal
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LazyListItem) return false
        return key == other.key &&
            contentRevision == other.contentRevision &&
            environmentRevision == other.environmentRevision &&
            contentType == other.contentType &&
            kind == other.kind &&
            span == other.span
    }

    /**
     * Returns a hash of fields participating in [equals].
     *
     * @return semantic item hash excluding session callbacks
     */
    override fun hashCode(): Int {
        var result = key?.hashCode() ?: 0
        result = 31 * result + (contentRevision?.hashCode() ?: 0)
        result = 31 * result + (environmentRevision?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + kind.hashCode()
        result = 31 * result + span.hashCode()
        return result
    }
}

/** Selects normal flow placement or sticky-header behavior for a lazy item. */
enum class LazyListItemKind {
    Item,
    StickyHeader,
}

/** Creates a renderer-owned child session for one mounted lazy item. */
fun interface LazyListItemSessionFactory {
    /**
     * Creates a session targeting [container].
     *
     * @param container opaque platform container owned by the renderer
     * @return a new session whose lifecycle is transferred to the renderer
     */
    fun create(container: RenderContainerHandle): LazyListItemSession
}

/**
 * Defines preparation, activation, rendering, and disposal for one lazy-item child tree.
 *
 * This is a Q3 lifecycle contract. A renderer may call [prepare] once while the holder is detached,
 * then calls [activate] at most once before any later [render] calls. Preparation is speculative:
 * it must not publish remember activation, user effects, native commit callbacks, overlays, or
 * committed-frame diagnostics. [activate] makes the prepared content externally observable, and
 * [render] applies later committed submissions to that active session. The renderer calls [dispose]
 * once when it permanently releases the item container; disposal must also be safe before
 * activation. Every operation is confined to the owning UI thread and must not re-enter rendering.
 *
 * Implementations that do not support speculative native-tree construction can inherit the
 * defaults: [prepare] does nothing and [activate] performs the initial [render]. Implementations
 * that override [prepare] must also override [activate] to commit that exact candidate or rebuild
 * current content when the candidate became stale.
 *
 * @sample com.viewcompose.ui.samples.lazyListItemSessionUpdateSample
 */
interface LazyListItemSession {
    /**
     * Optionally prepares the latest installed content without publishing committed work.
     *
     * The default performs no work. A custom implementation may allocate and bind a candidate
     * native tree, but must retain all commit-bound callbacks until [activate]. Failure propagates
     * to the renderer, which may retry through activation or dispose the session.
     */
    fun prepare() = Unit

    /**
     * Activates this session and publishes its first committed frame.
     *
     * The default calls [render], preserving existing session implementations. An optimized
     * implementation commits a valid prepared candidate without rebuilding it. This operation is
     * called at most once and only after an optional [prepare]. Returning `false` reports that the
     * installed content rolled back and keeps its semantic revision eligible for retry.
     *
     * @return `true` only when the installed content committed
     */
    fun activate(): Boolean = render()

    /**
     * Attempts to render the latest installed content into an already active item container.
     *
     * @return `true` only when this attempt committed the installed content; `false` keeps the
     * installed semantic revision eligible for retry without replacing the logical session
     */
    fun render(): Boolean

    /**
     * Terminates this logical session and detaches a reset native presentation for cross-key reuse.
     *
     * The default calls [dispose] and returns `null`. An optimized implementation must dispose all
     * key-owned state, observations, saveable-state leases, and effects before resetting native
     * interop nodes. The returned presentation contains no logical identity and remains owned by
     * the caller until adopted or released.
     *
     * @return detached presentation eligible for another key, or `null` when reuse is unsafe
     */
    fun disposeForReuse(): ReusableItemPresentation? {
        dispose()
        return null
    }

    /**
     * Adopts a detached presentation before this session's first prepare or activation.
     *
     * Returning `false` leaves [presentation] owned by the caller. An implementation must not
     * adopt a presentation produced by an incompatible render engine.
     *
     * @param presentation reset, logically empty physical tree
     * @return `true` when ownership moved to this session
     */
    fun adoptReusablePresentation(presentation: ReusableItemPresentation): Boolean = false

    /**
     * Releases observations, candidate or active child views, and owned container references.
     *
     * Disposal is terminal and must be idempotent whether or not [activate] ran.
     */
    fun dispose()
}

/**
 * Opaque physical item presentation detached from all logical key state.
 *
 * A lazy-container renderer owns this UI-thread-confined handle until exactly one successful
 * adoption or [release]. Release is idempotent and permanently frees native resources.
 */
interface ReusableItemPresentation {
    /** Permanently releases this presentation and its native resources exactly once. */
    fun release()
}

/** Opaque, platform-neutral handle for a renderer-owned lazy-item container. */
interface RenderContainerHandle

/**
 * Returns the native container stored by a [PlatformRenderContainerHandle].
 *
 * This property is an interop escape hatch. Platform integrations must cast the result to their
 * expected native type and must not retain it beyond the item session lifecycle.
 *
 * @receiver renderer container handle backed by [PlatformRenderContainerHandle]
 * @return opaque native container object
 * @throws ClassCastException if the receiver is not a [PlatformRenderContainerHandle]
 */
val RenderContainerHandle.nativeContainer: Any
    get() = (this as PlatformRenderContainerHandle).container

/** Exposes an opaque native container to platform-specific lazy-item session factories. */
interface PlatformRenderContainerHandle : RenderContainerHandle {
    /** Native container object owned by the renderer. */
    val container: Any
}
