package com.viewcompose.ui.node

/**
 * Describes one keyed, lazily rendered collection item and its child-session lifecycle.
 *
 * [contentToken] is the semantic version used by collection diffing. Equality deliberately
 * excludes [sessionFactory] and [sessionUpdater]. A parent submission containing a different item
 * instance creates one logical renderer revision for that item; callback object identity does not.
 * After the parent render commits, a renderer may prepare a detached holder without committing its
 * child work, activate it when presented, then retain the active bound session, install the latest
 * captured content, and render that revision at most once. Renderers prepare, activate, update,
 * render, and dispose sessions on their owning UI thread.
 *
 * @sample com.viewcompose.ui.samples.lazyListItemSessionUpdateSample
 *
 * @property key optional semantic identity used for item reconciliation
 * @property contentToken semantic content version compared during diffing
 * @property contentType optional renderer reuse classification
 * @property kind normal item or sticky-header behavior
 * @property span positive grid span count
 * @property sessionFactory factory invoked when a renderer needs a child render session
 * @property sessionUpdater optional callback that installs latest captured content into a retained session
 * @throws IllegalArgumentException if [span] is not greater than zero
 */
class LazyListItem(
    val key: Any?,
    val contentToken: Any?,
    val contentType: Any? = null,
    val kind: LazyListItemKind = LazyListItemKind.Item,
    val span: Int = 1,
    val sessionFactory: LazyListItemSessionFactory,
    val sessionUpdater: ((LazyListItemSession) -> Unit)? = null,
) {
    init {
        require(span > 0) { "Lazy item span must be greater than zero." }
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
            contentToken == other.contentToken &&
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
        result = 31 * result + (contentToken?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + kind.hashCode()
        result = 31 * result + span
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
     * called at most once and only after an optional [prepare].
     */
    fun activate() {
        render()
    }

    /** Renders the latest installed content into an already active item container. */
    fun render()

    /**
     * Releases observations, candidate or active child views, and owned container references.
     *
     * Disposal is terminal and must be idempotent whether or not [activate] ran.
     */
    fun dispose()
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
