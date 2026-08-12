package com.viewcompose.ui.node

/**
 * Describes one keyed, lazily rendered collection item and its child-session lifecycle.
 *
 * [contentToken] is the semantic version used by collection diffing. Equality deliberately
 * excludes [sessionFactory] and [sessionUpdater]: an equal token lets the renderer retain a bound
 * item while installing and rendering its latest closure when a parent refresh reaches that item.
 * Renderers create, update, render, and dispose sessions on their owning UI thread.
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
 * Defines rendering and disposal operations for one mounted lazy-item child tree.
 *
 * The renderer calls [render] zero or more times and calls [dispose] once when it permanently
 * releases the item container. Implementations must keep both operations on the owning UI thread.
 */
interface LazyListItemSession {
    /** Renders the latest content installed for this item into its container. */
    fun render()

    /** Releases observations, child views, and container references owned by this session. */
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
