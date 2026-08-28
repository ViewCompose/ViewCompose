package com.viewcompose.ui.node

import com.viewcompose.ui.node.policy.GridItemSpan

/**
 * Describes one keyed, lazily rendered collection item and its child-session lifecycle.
 *
 * [contentRevision] is the caller-owned semantic content version. [environmentRevision] is the
 * framework-owned environment version captured by delayed-content DSLs. Equality deliberately
 * excludes [sessionStrategy] and [sessionPayload]: declaration strategy and payload identity are
 * never invalidation signals. Equal key and revisions skip the child render completely; changing
 * either revision installs the latest payload and renders that logical session once. Changing
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
 * @property sessionStrategy declaration-owned strategy that creates and updates child sessions
 * @property sessionPayload opaque declaration payload interpreted only by [sessionStrategy]
 */
class LazyListItem(
    val key: Any,
    val contentRevision: Any?,
    val environmentRevision: Any? = null,
    val contentType: Any? = null,
    val kind: LazyListItemKind = LazyListItemKind.Item,
    val span: GridItemSpan = GridItemSpan.Single,
    val sessionStrategy: LazyListItemSessionStrategy,
    val sessionPayload: Any? = null,
) {
    /**
     * Creates a child session and installs this item's current payload.
     *
     * Renderers call this only when the logical key or physical compatibility class needs a new
     * session. A strategy shared by a typed declaration must not retain [LazyListItem] after this
     * call; the returned session owns only the installed key and payload state.
     *
     * @param container opaque platform container owned by the renderer
     * @return a new session whose lifecycle is transferred to the renderer
     */
    fun createSession(container: RenderContainerHandle): LazyListItemSession {
        return sessionStrategy.create(container, this)
    }

    /**
     * Installs this item's current payload into [session] before a revision render.
     *
     * @param session retained session created by the compatible [sessionStrategy]
     */
    fun updateSession(session: LazyListItemSession) {
        sessionStrategy.update(session, this)
    }

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

/**
 * Creates and updates renderer-owned child sessions for a lazy declaration.
 *
 * One strategy may be shared by every entry produced by a typed declaration. Implementations read
 * the current key and opaque [LazyListItem.sessionPayload] synchronously and must not retain the
 * item snapshot itself. [create] installs the initial payload; [update] installs a later payload
 * before the renderer calls [LazyListItemSession.render]. Both operations are UI-thread confined
 * by the enclosing item-session lifecycle. Cross-key reuse is disabled unless
 * [canReuseAcrossKeys] explicitly accepts the retained session.
 *
 * @sample com.viewcompose.ui.samples.lazyListItemSessionUpdateSample
 */
interface LazyListItemSessionStrategy {
    /**
     * Creates a session targeting [container] with [item]'s current payload installed.
     *
     * @param container opaque platform container owned by the renderer
     * @param item current logical item snapshot, valid only for this synchronous call
     * @return a new session whose lifecycle is transferred to the renderer
     */
    fun create(
        container: RenderContainerHandle,
        item: LazyListItem,
    ): LazyListItemSession

    /**
     * Installs [item]'s current payload into a compatible retained [session].
     *
     * @param session retained session created by this strategy
     * @param item current logical item snapshot, valid only for this synchronous call
     */
    fun update(
        session: LazyListItemSession,
        item: LazyListItem,
    )

    /**
     * Reports whether [update] can transfer [session] to another logical item key.
     *
     * The default is `false`, which makes the renderer dispose the old logical session before it
     * creates the replacement. Returning `true` promises that the next [update] followed by
     * [LazyListItemSession.render] transactionally replaces all key-owned remembered state,
     * observations, effects, callbacks, and saveable-state ownership. Outgoing effects must finish
     * before incoming effects activate, and a failed render must not expose callbacks or state from
     * the previous key. Physical compatibility remains controlled separately by
     * [LazyListItem.contentType] and [LazyListItem.kind].
     *
     * Renderers call this synchronously on the owning UI thread. Implementations should perform
     * only an identity/type check; expensive cleanup belongs to the subsequent render transaction.
     * Opt in only when retaining the session materially avoids allocation or lifecycle setup.
     *
     * @sample com.viewcompose.ui.samples.lazyListItemSessionUpdateSample
     * @param session retained session currently owned by another logical key
     * @return `true` when this strategy can safely install a different key into [session]
     */
    fun canReuseAcrossKeys(session: LazyListItemSession): Boolean = false
}

/**
 * Creates a strategy from callbacks that do not need to inspect the item payload.
 *
 * This convenience is intended for low-level static sessions. Typed collection implementations
 * that need [LazyListItem.sessionPayload] should implement [LazyListItemSessionStrategy] directly
 * and share one instance across the declaration.
 *
 * @sample com.viewcompose.ui.samples.lazyListItemSessionUpdateSample
 * @param create creates a new session for the supplied renderer container
 * @param update installs current declaration content into a retained session
 * @return one strategy object that may be shared by compatible item snapshots
 */
fun lazyListItemSessionStrategy(
    create: (RenderContainerHandle) -> LazyListItemSession,
    update: (LazyListItemSession) -> Unit,
): LazyListItemSessionStrategy {
    return object : LazyListItemSessionStrategy {
        override fun create(
            container: RenderContainerHandle,
            item: LazyListItem,
        ): LazyListItemSession = create(container)

        override fun update(
            session: LazyListItemSession,
            item: LazyListItem,
        ) = update(session)
    }
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
