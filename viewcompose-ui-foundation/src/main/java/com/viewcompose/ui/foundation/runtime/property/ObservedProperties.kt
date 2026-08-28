package com.viewcompose.ui.foundation

import com.viewcompose.ui.node.spec.NodeSpec

/**
 * Supplies one typed value whose Snapshot State reads can update a supported node property without
 * recomposing the surrounding declaration.
 *
 * The reader runs synchronously on the owning RenderSession thread inside a frame-batched read
 * Snapshot. State dependencies are replaced only after the complete native property transaction
 * succeeds. Ordinary Kotlin captures are not inferred: every changing non-State capture must be
 * represented by [observedValue]'s `inputs` argument.
 *
 * Instances carry a declaration, not a live value. They do not observe changes when evaluated by
 * [buildVNodeTree] outside an active RenderSession.
 *
 * @sample com.viewcompose.ui.foundation.samples.observedTextValueSample
 * @param T type returned by the observed reader
 */
class ObservedValue<T> internal constructor(
    internal val inputs: List<Any?>,
    internal val read: () -> T,
)

/**
 * Creates a typed node-property value observed by the owning RenderSession.
 *
 * State reads made by [read] do not invalidate the enclosing composition scope. Invalidations are
 * coalesced with other observed properties and applied in one renderer transaction. Equal [inputs]
 * authorize reuse of the previously committed reader; changing an ordinary capture without also
 * changing [inputs] can retain stale data and is unsupported.
 *
 * @sample com.viewcompose.ui.foundation.samples.observedTextValueSample
 * @param T type returned by the reader
 * @param inputs semantic versions of every changing non-State value captured by [read]
 * @param read synchronous, side-effect-free calculation whose State reads become dependencies
 * @return an observed declaration consumed by a compatible widget overload
 */
fun <T> observedValue(
    inputs: List<Any?> = emptyList(),
    read: () -> T,
): ObservedValue<T> = ObservedValue(
    inputs = inputs.toList(),
    read = read,
)

/**
 * Derives another directly patchable value from this observed declaration.
 *
 * The derived reader observes the same underlying State reads and applies [transform] inside the
 * owning RenderSession's consistent read Snapshot. [transform] must be synchronous, replay-safe,
 * and side-effect-free. Changing ordinary values captured by [transform] requires matching
 * [inputs]; the transform identity itself also participates in declaration reuse.
 *
 * @sample com.viewcompose.ui.foundation.samples.observedLazyItemsSnapshotSample
 * @param R derived value type
 * @param inputs semantic versions of changing non-State values captured by [transform]
 * @param transform side-effect-free conversion of the latest source value
 * @return an observed declaration suitable for a compatible widget overload
 */
fun <T, R> ObservedValue<T>.map(
    inputs: List<Any?> = emptyList(),
    transform: (T) -> R,
): ObservedValue<R> = ObservedValue(
    inputs = this.inputs + inputs + transform,
    read = { transform(this.read()) },
)

/**
 * Supplies a complete node-property snapshot for direct transactional patching.
 *
 * A committed reader may return a different value of the same concrete NodeSpec type. It must not
 * change node type, key, Modifier, children, or environment; those values are structural and
 * require ordinary composition. Dependencies, captured Locals, and the current value are owned by
 * the logical emitting scope and released with that scope or its RenderSession.
 *
 * Instances do not observe changes when evaluated outside an active RenderSession.
 *
 * @sample com.viewcompose.ui.foundation.samples.observedNodeSpecSample
 * @param S concrete NodeSpec type produced by the reader
 */
class ObservedNodeSpec<S : NodeSpec> internal constructor(
    internal val inputs: List<Any?>,
    internal val prepare: () -> PreparedObservedNodeSpec<S>,
)

/** Candidate value and post-native-commit publication owned by one observed-property attempt. */
internal class PreparedObservedNodeSpec<out S : NodeSpec>(
    val spec: S,
    val commitEffect: (() -> Unit)? = null,
)

/**
 * Creates a complete observed NodeSpec for the low-level observed [UiTreeBuilder.emit] overload.
 *
 * All dirty readers in one RenderSession frame observe one consistent Snapshot. [read] must be
 * synchronous, replay-safe, and free of external side effects because a failed native transaction
 * abandons its candidate value and may evaluate it again. Equal [inputs] reuse the committed reader
 * until one of its State dependencies changes.
 *
 * @sample com.viewcompose.ui.foundation.samples.observedNodeSpecSample
 * @param S concrete NodeSpec type produced by the reader
 * @param inputs semantic versions of every changing non-State value captured by [read]
 * @param read calculation returning the complete non-structural property snapshot
 * @return an observed NodeSpec declaration owned after emission by the active RenderSession
 */
fun <S : NodeSpec> observedNodeSpec(
    inputs: List<Any?> = emptyList(),
    read: () -> S,
): ObservedNodeSpec<S> = ObservedNodeSpec(
    inputs = inputs.toList(),
    prepare = {
        PreparedObservedNodeSpec(spec = read())
    },
)

/** Creates an observed NodeSpec whose auxiliary state publishes only after native patch success. */
internal fun <S : NodeSpec> preparedObservedNodeSpec(
    inputs: List<Any?>,
    prepare: () -> PreparedObservedNodeSpec<S>,
): ObservedNodeSpec<S> = ObservedNodeSpec(
    inputs = inputs.toList(),
    prepare = prepare,
)
