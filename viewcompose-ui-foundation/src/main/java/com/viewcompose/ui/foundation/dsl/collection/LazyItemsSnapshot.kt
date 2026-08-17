package com.viewcompose.ui.foundation

/**
 * Carries one immutable lazy-collection submission with a framework-created identity.
 *
 * Create this value with [toLazyItemsSnapshot]. The factory shallow-copies item references in
 * iteration order but does not evaluate lazy-container selectors. On the first declaration of a
 * new snapshot identity, a consuming container evaluates and freezes its `key`, `contentType`,
 * `contentRevision`, and optional grid-span results. Reusing the same instance allows that
 * declaration to reuse a compatible committed ordered item list in constant time without invoking
 * selectors or scanning item keys.
 *
 * The snapshot retains item references rather than deep-copying application models. Each item and
 * every ordinary non-State value reachable from item content must therefore remain immutable for
 * the lifetime of this instance, or be represented by a replacement snapshot. Observable State
 * read by item content and framework environment changes remain independently invalidating.
 * Logical item sessions, saveable state, and native presentations are owned by each consuming
 * container, not by this value, so one snapshot may be submitted to more than one container.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyItemsSnapshotSample
 * @param T application item type retained by reference in declaration order
 */
class LazyItemsSnapshot<out T> internal constructor(
    internal val items: List<T>,
)

/**
 * Copies these item references into an immutable lazy-collection submission with a new identity.
 *
 * The copy runs synchronously on the calling thread and preserves iteration order. Later structural
 * mutation of the source iterable does not change the returned snapshot. The function does not
 * deep-copy an item or values reachable from it, and it does not evaluate `key`, `contentType`,
 * `contentRevision`, grid span, or item content declarations.
 *
 * Create a replacement snapshot whenever order or membership changes, an item reference can expose
 * changed ordinary non-State data, or a selector or item-content capture changes. Reusing one
 * returned instance enables a lazy-container whole-table constant-time path after its selectors
 * have been evaluated once. Calling this function again deliberately creates a distinct identity
 * even when all element references compare equal.
 *
 * Creation takes linear time and storage in the iterable size. Iteration exceptions propagate and
 * no snapshot is returned.
 *
 * @sample com.viewcompose.ui.foundation.samples.lazyItemsSnapshotSample
 * @param T element type retained by reference
 * @receiver finite iterable whose order and membership are copied exactly once
 * @return a new immutable submission whose identity is allocated and compared by the framework
 */
fun <T> Iterable<T>.toLazyItemsSnapshot(): LazyItemsSnapshot<T> {
    val copiedItems = if (this is Collection<T>) {
        ArrayList<T>(size).also { copy -> copy.addAll(this) }
    } else {
        toList()
    }
    return LazyItemsSnapshot(copiedItems)
}
