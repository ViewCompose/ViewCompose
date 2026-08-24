package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemKind
import com.viewcompose.ui.node.LazyListItemSessionStrategy
import com.viewcompose.ui.node.policy.GridItemSpan

/**
 * Creates type-safe lazy-item snapshots backed by Foundation child composition Sessions.
 *
 * This Q3 bridge is intended for compact [com.viewcompose.ui.node.LazyItemTable]
 * implementations. The factory captures the current locals and saveable-state owner once for its
 * enclosing collection revision. [createItem] preserves payload type safety while returning the
 * neutral UI Contract item consumed by renderers.
 *
 * A factory is revision-scoped and must not be retained after the current parent declaration.
 * Logical state is owned by item key, not by the factory or native holder.
 *
 * @sample com.viewcompose.ui.foundation.samples.compactLazyItemTableSample
 * @param T payload rendered by one delayed item Session
 */
class LazyItemContentFactory<T> internal constructor(
    private val capturedEnvironmentRevision: LocalSnapshot,
    private val strategy: LazyListItemSessionStrategy,
) {
    /**
     * Opaque framework-environment revision captured for this factory.
     *
     * Compact tables can compare this token when deciding whether a newer declaration is
     * semantically equal to a predecessor. The token is process-local implementation data: do not
     * inspect, transform, persist, or use it as application identity.
     */
    val environmentRevision: Any
        get() = capturedEnvironmentRevision

    /**
     * Creates one immutable logical item for a compact table position.
     *
     * [payload] is retained only by this revision's item snapshot and installed into a child
     * Session when the renderer prepares or activates that position.
     *
     * @param key unique logical identity in the enclosing table
     * @param contentRevision semantic version of [payload] and ordinary captured values
     * @param payload type-safe value supplied to the factory's delayed content
     * @param contentType physical-tree compatibility class
     * @param kind normal item or sticky-header placement
     * @param span renderer-neutral grid span
     * @return neutral lazy item backed by the captured Foundation environment
     */
    fun createItem(
        key: Any,
        contentRevision: Any?,
        payload: T,
        contentType: Any? = null,
        kind: LazyListItemKind = LazyListItemKind.Item,
        span: GridItemSpan = GridItemSpan.Single,
    ): LazyListItem {
        return LazyListItem(
            key = key,
            contentRevision = contentRevision,
            environmentRevision = capturedEnvironmentRevision,
            contentType = contentType,
            kind = kind,
            span = if (span is GridItemSpan.Fixed && span.count == 1) {
                GridItemSpan.Single
            } else {
                span
            },
            sessionStrategy = strategy,
            sessionPayload = payload,
        )
    }
}

/**
 * Captures a type-safe delayed-content factory for one compact lazy-table revision.
 *
 * [retainedKeys] is the successfully committed logical state set. Keys omitted from this set lose
 * retained saveable state after their active Sessions are disposed; unloaded positional
 * placeholders should therefore be omitted. The set is copied immediately and applied only after
 * the parent composition commits. [itemContent] must emit exactly one root node.
 *
 * @sample com.viewcompose.ui.foundation.samples.compactLazyItemTableSample
 * @param T delayed item payload type
 * @receiver builder declaring the collection that owns the factory
 * @param retainedKeys logical keys whose saveable state remains valid in this revision
 * @param itemContent delayed item declaration invoked by an active child Session
 * @return revision-scoped typed item factory
 * @throws IllegalStateException when [itemContent] emits zero or multiple root nodes
 */
fun <T> UiTreeBuilder.lazyItemContentFactory(
    retainedKeys: Set<Any>,
    itemContent: UiTreeBuilder.(T) -> Unit,
): LazyItemContentFactory<T> {
    val saveableStateHolder = rememberSaveableStateHolder()
    val retainedKeySnapshot = retainedKeys.toSet()
    if (saveableStateHolder != null) {
        SideEffect {
            saveableStateHolder.retainKeys(retainedKeySnapshot)
        }
    }
    val localSnapshot = LocalContext.snapshot()
    return LazyItemContentFactory(
        capturedEnvironmentRevision = localSnapshot,
        strategy = WidgetLazyItemSessionStrategy(
            localSnapshot = localSnapshot,
            saveableStateHolder = saveableStateHolder,
            content = TypedWidgetLazyItemContent(itemContent),
        ),
    )
}
