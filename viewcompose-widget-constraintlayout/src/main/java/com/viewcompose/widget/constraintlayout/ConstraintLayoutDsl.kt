package com.viewcompose.widget.constraintlayout

import com.viewcompose.ui.modifier.ConstraintModifierElement
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.layoutId
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintBarrierDirection
import com.viewcompose.ui.node.spec.ConstraintBarrierSpec
import com.viewcompose.ui.node.spec.ConstraintChainOrientation
import com.viewcompose.ui.node.spec.ConstraintChainSpec
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintCircleSpec
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.node.spec.ConstraintFlowHorizontalAlign
import com.viewcompose.ui.node.spec.ConstraintFlowOrientation
import com.viewcompose.ui.node.spec.ConstraintFlowSpec
import com.viewcompose.ui.node.spec.ConstraintFlowVerticalAlign
import com.viewcompose.ui.node.spec.ConstraintFlowWrapMode
import com.viewcompose.ui.node.spec.ConstraintGuidelineDirection
import com.viewcompose.ui.node.spec.ConstraintGuidelinePosition
import com.viewcompose.ui.node.spec.ConstraintGuidelineSpec
import com.viewcompose.ui.node.spec.ConstraintGroupSpec
import com.viewcompose.ui.node.spec.ConstraintHelperVisibility
import com.viewcompose.ui.node.spec.ConstraintHelpersSpec
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.node.spec.ConstraintLayerSpec
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.ConstraintPlaceholderSpec
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.widget.core.UiTreeBuilder

/** Source-level alias for the UI-tree builder available inside [ConstraintLayout] content. */
typealias ConstraintLayoutScope = UiTreeBuilder

/**
 * Identifies a child or virtual helper inside one ConstraintLayout specification.
 *
 * IDs are matched as strings by the renderer and should be unique within the owning layout. The
 * constructor does not validate emptiness or uniqueness.
 *
 * @property id stable local identifier shared by layoutId, constraints, and helpers
 */
data class ConstraintReference(
    override val id: String,
) : ConstraintReferenceTarget

/** Exposes the optional string identity accepted by constraint-anchor links. */
sealed interface ConstraintReferenceTarget {
    /** Child/helper ID, or `null` for the ConstraintLayout parent. */
    val id: String?
}

/** Canonical anchor target for the owning ConstraintLayout rather than a child ID. */
data object ConstraintParentReference : ConstraintReferenceTarget {
    /** Always `null`, which the renderer interprets as the parent. */
    override val id: String? = null
}

/** Returns the canonical reference to the current ConstraintLayout parent. */
val parent: ConstraintReferenceTarget
    get() = ConstraintParentReference

/** Collects helper specs created by one ConstraintLayout DSL evaluation. */
private class MutableConstraintHelpersCollector {
    private var nextAutoId = 0
    val guidelines = mutableListOf<ConstraintGuidelineSpec>()
    val barriers = mutableListOf<ConstraintBarrierSpec>()
    val chains = mutableListOf<ConstraintChainSpec>()
    val flows = mutableListOf<ConstraintFlowSpec>()
    val groups = mutableListOf<ConstraintGroupSpec>()
    val layers = mutableListOf<ConstraintLayerSpec>()
    val placeholders = mutableListOf<ConstraintPlaceholderSpec>()

    fun allocId(prefix: String): String {
        val id = "$prefix-${nextAutoId}"
        nextAutoId += 1
        return id
    }

    fun toSpec(): ConstraintHelpersSpec {
        return ConstraintHelpersSpec(
            guidelines = guidelines,
            barriers = barriers,
            chains = chains,
            flows = flows,
            groups = groups,
            layers = layers,
            placeholders = placeholders,
        )
    }
}

private class ConstraintLayoutDslContext(
    val helpers: MutableConstraintHelpersCollector,
)

/** Tracks nested ConstraintLayout DSL evaluations independently on each thread. */
private object ConstraintLayoutDslContextStack {
    private val threadLocal: ThreadLocal<ArrayDeque<ConstraintLayoutDslContext>> = ThreadLocal.withInitial {
        ArrayDeque<ConstraintLayoutDslContext>()
    }

    private fun deque(): ArrayDeque<ConstraintLayoutDslContext> {
        return requireNotNull(threadLocal.get()) {
            "ConstraintLayout DSL context stack is unexpectedly unavailable."
        }
    }

    fun push(context: ConstraintLayoutDslContext) {
        deque().addLast(context)
    }

    fun pop() {
        val currentDeque = deque()
        if (currentDeque.isNotEmpty()) {
            currentDeque.removeLast()
        }
    }

    fun current(): ConstraintLayoutDslContext? = deque().lastOrNull()
}

private fun requireConstraintContext(): ConstraintLayoutDslContext {
    return requireNotNull(ConstraintLayoutDslContextStack.current()) {
        "ConstraintLayout helper APIs can only be called inside ConstraintLayout { ... }."
    }
}

private fun ConstraintReferenceTarget.toAnchorTarget(anchor: ConstraintAnchor): ConstraintAnchorTarget {
    return ConstraintAnchorTarget(
        id = id,
        anchor = anchor,
    )
}

/**
 * Builds the complete constraint specification for one child ID.
 *
 * Repeated calls targeting the same source anchor replace the previous link. Values are encoded
 * without DSL-level range validation and are interpreted by the Android ConstraintLayout renderer.
 */
class ConstraintConstrainScope internal constructor() {
    private var start: ConstraintAnchorLink? = null
    private var end: ConstraintAnchorLink? = null
    private var top: ConstraintAnchorLink? = null
    private var bottom: ConstraintAnchorLink? = null
    private var baseline: ConstraintAnchorTarget? = null
    private var baselineToTop: ConstraintAnchorLink? = null
    private var baselineToBottom: ConstraintAnchorLink? = null
    /** Width mode, defaulting to native wrap content. */
    var width: ConstraintDimension = ConstraintDimension.WrapContent
    /** Height mode, defaulting to native wrap content. */
    var height: ConstraintDimension = ConstraintDimension.WrapContent
    /** Optional minimum width in dp; `null` leaves the native minimum unset. */
    var widthMin: UiDp? = null
    /** Optional maximum width in dp; `null` leaves the native maximum unset. */
    var widthMax: UiDp? = null
    /** Optional match-constraint width fraction clamped to `0f..1f` by the renderer. */
    var widthPercent: Float? = null
    /** Optional minimum height in dp; `null` leaves the native minimum unset. */
    var heightMin: UiDp? = null
    /** Optional maximum height in dp; `null` leaves the native maximum unset. */
    var heightMax: UiDp? = null
    /** Optional match-constraint height fraction clamped to `0f..1f` by the renderer. */
    var heightPercent: Float? = null
    /** Whether wrap-content width may shrink to satisfy constraints. */
    var constrainedWidth: Boolean = false
    /** Whether wrap-content height may shrink to satisfy constraints. */
    var constrainedHeight: Boolean = false
    /** Optional horizontal position bias between two connected horizontal anchors. */
    var horizontalBias: Float? = null
    /** Optional vertical position bias between two connected vertical anchors. */
    var verticalBias: Float? = null
    /** Optional native ConstraintLayout dimension-ratio expression, such as `16:9` or `W,16:9`. */
    var dimensionRatio: String? = null
    private var circle: ConstraintCircleSpec? = null

    /**
     * Connects this child's logical start to [target]'s logical start.
     * @param target parent, child, or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun startToStart(
        target: ConstraintReferenceTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        start = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.Start),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's logical start to [target]'s logical end.
     * @param target child or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun startToEnd(
        target: ConstraintReferenceTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        start = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.End),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's logical end to [target]'s logical start.
     * @param target child or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun endToStart(
        target: ConstraintReferenceTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        end = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.Start),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's logical end to [target]'s logical end.
     * @param target parent, child, or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun endToEnd(
        target: ConstraintReferenceTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        end = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.End),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's top to [target]'s top.
     * @param target parent, child, or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun topToTop(
        target: ConstraintReferenceTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        top = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.Top),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's top to [target]'s bottom.
     * @param target child or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun topToBottom(
        target: ConstraintReferenceTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        top = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.Bottom),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's bottom to [target]'s top.
     * @param target child or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun bottomToTop(
        target: ConstraintReferenceTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        bottom = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.Top),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's bottom to [target]'s bottom.
     * @param target parent, child, or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun bottomToBottom(
        target: ConstraintReferenceTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        bottom = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.Bottom),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's text baseline to [target]'s baseline.
     * @param target child whose native baseline is the destination
     */
    fun baselineToBaseline(target: ConstraintReference) {
        baseline = ConstraintAnchorTarget.ref(
            id = target.id,
            anchor = ConstraintAnchor.Baseline,
        )
    }

    /**
     * Connects this child's baseline to [target]'s top.
     * @param target parent, child, or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun baselineToTop(
        target: ConstraintReferenceTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        baselineToTop = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.Top),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's baseline to [target]'s bottom.
     * @param target parent, child, or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun baselineToBottom(
        target: ConstraintReferenceTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        baselineToBottom = ConstraintAnchorLink(
            target = target.toAnchorTarget(ConstraintAnchor.Bottom),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Positions this child on a circle around [target].
     *
     * @param target child at the circle center
     * @param radius center-to-center distance in dp
     * @param angle clockwise Android ConstraintLayout angle in degrees
     */
    fun circular(
        target: ConstraintReference,
        radius: UiDp,
        angle: Float,
    ) {
        circle = ConstraintCircleSpec(
            targetId = target.id,
            radius = radius,
            angle = angle,
        )
    }

    /**
     * Replaces start/end links so this child is horizontally centered on [target].
     * @param target parent, child, or helper to center against
     */
    fun centerHorizontallyTo(target: ConstraintReferenceTarget = parent) {
        startToStart(target)
        endToEnd(target)
    }

    /**
     * Replaces top/bottom links so this child is vertically centered on [target].
     * @param target parent, child, or helper to center against
     */
    fun centerVerticallyTo(target: ConstraintReferenceTarget = parent) {
        topToTop(target)
        bottomToBottom(target)
    }

    internal fun build(): ConstraintItemSpec {
        return ConstraintItemSpec(
            start = start,
            end = end,
            top = top,
            bottom = bottom,
            baseline = baseline,
            baselineToTop = baselineToTop,
            baselineToBottom = baselineToBottom,
            width = width,
            height = height,
            widthMin = widthMin,
            widthMax = widthMax,
            widthPercent = widthPercent,
            heightMin = heightMin,
            heightMax = heightMax,
            heightPercent = heightPercent,
            constrainedWidth = constrainedWidth,
            constrainedHeight = constrainedHeight,
            horizontalBias = horizontalBias,
            verticalBias = verticalBias,
            dimensionRatio = dimensionRatio,
            circle = circle,
        )
    }
}

private fun buildConstraintSpec(content: ConstraintConstrainScope.() -> Unit): ConstraintItemSpec {
    return ConstraintConstrainScope()
        .apply(content)
        .build()
}

private fun validateChainWeights(
    weights: List<Float>?,
    expectedSize: Int,
) {
    if (weights == null) {
        return
    }
    require(weights.size == expectedSize) {
        "Constraint chain weights size must match referenced ids size. expected=$expectedSize actual=${weights.size}"
    }
}

/**
 * Binds this modifier to [ref] and appends the child's complete constraint specification.
 *
 * The returned chain contains both a layout ID and a constraint element. Later conflicting modifier
 * elements follow the renderer's normal modifier-resolution rules.
 *
 * @receiver modifier chain to extend
 * @param ref reference shared with anchors and helpers
 * @param content constraint builder evaluated immediately
 * @return a new modifier chain
 */
fun Modifier.constrainAs(
    ref: ConstraintReference,
    content: ConstraintConstrainScope.() -> Unit,
): Modifier {
    return this
        .layoutId(ref.id)
        .then(
            ConstraintModifierElement(
                constraint = buildConstraintSpec(content),
                referenceId = ref.id,
            ),
        )
}

/**
 * Binds this modifier to string [id] and appends the child's complete constraint specification.
 *
 * @receiver modifier chain to extend
 * @param id layout-local child identity
 * @param content constraint builder evaluated immediately
 * @return a new modifier chain
 */
fun Modifier.constrain(
    id: String,
    content: ConstraintConstrainScope.() -> Unit,
): Modifier {
    return this
        .layoutId(id)
        .then(
            ConstraintModifierElement(
                constraint = buildConstraintSpec(content),
                referenceId = id,
            ),
        )
}

/**
 * Emits a node backed by AndroidX ConstraintLayout and collects inline virtual helpers.
 *
 * Helper calls are valid only while [content] is evaluated. A supplied [constraintSet] provides
 * reusable constraints; inline child modifiers and helpers are encoded alongside it for renderer
 * reconciliation. Nested ConstraintLayouts use independent thread-local collector frames.
 *
 * @sample com.viewcompose.widget.constraintlayout.samples.constraintLayoutSample
 * @param key optional sibling identity used during reconciliation
 * @param constraintSet reusable external constraints, or `null` for inline-only constraints
 * @param modifier layout, drawing, input, and semantics behavior for the native container
 * @param content children and inline helper declarations
 */
fun UiTreeBuilder.ConstraintLayout(
    key: Any? = null,
    constraintSet: ConstraintSetSpec? = null,
    modifier: Modifier = Modifier,
    content: ConstraintLayoutScope.() -> Unit,
) {
    val context = ConstraintLayoutDslContext(
        helpers = MutableConstraintHelpersCollector(),
    )
    ConstraintLayoutDslContextStack.push(context)
    try {
        emit(
            type = NodeType.ConstraintLayout,
            key = key,
            spec = ConstraintLayoutNodeProps(
                constraintSet = constraintSet,
                helpers = context.helpers.toSpec(),
            ),
            modifier = modifier,
            content = content,
        )
    } finally {
        ConstraintLayoutDslContextStack.pop()
    }
}

/**
 * Creates a reference usable by constraints and virtual-helper APIs in the current layout.
 * @receiver active ConstraintLayout content scope
 * @param id layout-local child/helper ID; uniqueness is the caller's responsibility
 * @return a reference retaining [id]
 */
fun ConstraintLayoutScope.createRef(id: String): ConstraintReference {
    return ConstraintReference(id = id)
}

/**
 * Creates references in the same order as [ids].
 * @receiver active ConstraintLayout content scope
 * @param ids layout-local IDs whose uniqueness is the caller's responsibility
 * @return newly allocated ordered reference array
 */
fun ConstraintLayoutScope.createRefs(vararg ids: String): Array<ConstraintReference> {
    return ids.map { id -> createRef(id) }.toTypedArray()
}

private fun ConstraintLayoutScope.allocHelperId(prefix: String): String {
    return requireConstraintContext().helpers.allocId(prefix)
}

/**
 * Creates a logical-start guideline at a fixed dp [offset] from the parent edge.
 * @receiver active ConstraintLayout content scope
 * @param offset distance from logical start in dp
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException when called outside [ConstraintLayout] content
 */
fun ConstraintLayoutScope.createGuidelineFromStart(
    offset: UiDp,
    id: String = allocHelperId("guideline-start"),
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.guidelines += ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromStart,
        position = ConstraintGuidelinePosition.Offset(offset),
    )
    return ConstraintReference(id)
}

/**
 * Creates a logical-start guideline at parent-width [fraction].
 * @receiver active ConstraintLayout content scope
 * @param fraction native ConstraintLayout fraction, normally from `0f` to `1f`
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException when called outside [ConstraintLayout] content
 */
fun ConstraintLayoutScope.createGuidelineFromStart(
    fraction: Float,
    id: String = allocHelperId("guideline-start"),
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.guidelines += ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromStart,
        position = ConstraintGuidelinePosition.Fraction(fraction),
    )
    return ConstraintReference(id)
}

/**
 * Creates a logical-end guideline at a fixed dp [offset] from the parent edge.
 * @receiver active ConstraintLayout content scope
 * @param offset distance from logical end in dp
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException when called outside [ConstraintLayout] content
 */
fun ConstraintLayoutScope.createGuidelineFromEnd(
    offset: UiDp,
    id: String = allocHelperId("guideline-end"),
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.guidelines += ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromEnd,
        position = ConstraintGuidelinePosition.Offset(offset),
    )
    return ConstraintReference(id)
}

/**
 * Creates a logical-end guideline at parent-width [fraction].
 * @receiver active ConstraintLayout content scope
 * @param fraction native ConstraintLayout fraction, normally from `0f` to `1f`
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException when called outside [ConstraintLayout] content
 */
fun ConstraintLayoutScope.createGuidelineFromEnd(
    fraction: Float,
    id: String = allocHelperId("guideline-end"),
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.guidelines += ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromEnd,
        position = ConstraintGuidelinePosition.Fraction(fraction),
    )
    return ConstraintReference(id)
}

/**
 * Creates a top guideline at a fixed dp [offset] from the parent edge.
 * @receiver active ConstraintLayout content scope
 * @param offset distance from top in dp
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException when called outside [ConstraintLayout] content
 */
fun ConstraintLayoutScope.createGuidelineFromTop(
    offset: UiDp,
    id: String = allocHelperId("guideline-top"),
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.guidelines += ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromTop,
        position = ConstraintGuidelinePosition.Offset(offset),
    )
    return ConstraintReference(id)
}

/**
 * Creates a top guideline at parent-height [fraction].
 * @receiver active ConstraintLayout content scope
 * @param fraction native ConstraintLayout fraction, normally from `0f` to `1f`
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException when called outside [ConstraintLayout] content
 */
fun ConstraintLayoutScope.createGuidelineFromTop(
    fraction: Float,
    id: String = allocHelperId("guideline-top"),
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.guidelines += ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromTop,
        position = ConstraintGuidelinePosition.Fraction(fraction),
    )
    return ConstraintReference(id)
}

/**
 * Creates a bottom guideline at a fixed dp [offset] from the parent edge.
 * @receiver active ConstraintLayout content scope
 * @param offset distance from bottom in dp
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException when called outside [ConstraintLayout] content
 */
fun ConstraintLayoutScope.createGuidelineFromBottom(
    offset: UiDp,
    id: String = allocHelperId("guideline-bottom"),
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.guidelines += ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromBottom,
        position = ConstraintGuidelinePosition.Offset(offset),
    )
    return ConstraintReference(id)
}

/**
 * Creates a bottom guideline at parent-height [fraction].
 * @receiver active ConstraintLayout content scope
 * @param fraction native ConstraintLayout fraction, normally from `0f` to `1f`
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException when called outside [ConstraintLayout] content
 */
fun ConstraintLayoutScope.createGuidelineFromBottom(
    fraction: Float,
    id: String = allocHelperId("guideline-bottom"),
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.guidelines += ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromBottom,
        position = ConstraintGuidelinePosition.Fraction(fraction),
    )
    return ConstraintReference(id)
}

/** Registers one inline barrier helper in the active layout context. */
private fun ConstraintLayoutScope.registerBarrier(
    id: String,
    direction: ConstraintBarrierDirection,
    refs: Array<out ConstraintReference>,
    margin: UiDp,
    allowsGoneWidgets: Boolean,
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.barriers += ConstraintBarrierSpec(
        id = id,
        direction = direction,
        referencedIds = refs.map { ref -> ref.id },
        margin = margin,
        allowsGoneWidgets = allowsGoneWidgets,
    )
    return ConstraintReference(id)
}

/**
 * Creates a logical-start barrier over [refs].
 * @param refs referenced children/helpers; an empty set is forwarded to the native helper
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin additional dp offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 */
fun ConstraintLayoutScope.createStartBarrier(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("barrier-start"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintReference {
    return registerBarrier(id, ConstraintBarrierDirection.Start, refs, margin, allowsGoneWidgets)
}

/**
 * Creates a logical-end barrier over [refs].
 * @param refs referenced children/helpers; an empty set is forwarded to the native helper
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin additional dp offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 */
fun ConstraintLayoutScope.createEndBarrier(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("barrier-end"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintReference {
    return registerBarrier(id, ConstraintBarrierDirection.End, refs, margin, allowsGoneWidgets)
}

/**
 * Creates a top barrier over [refs].
 * @param refs referenced children/helpers; an empty set is forwarded to the native helper
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin additional dp offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 */
fun ConstraintLayoutScope.createTopBarrier(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("barrier-top"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintReference {
    return registerBarrier(id, ConstraintBarrierDirection.Top, refs, margin, allowsGoneWidgets)
}

/**
 * Creates a bottom barrier over [refs].
 * @param refs referenced children/helpers; an empty set is forwarded to the native helper
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin additional dp offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 */
fun ConstraintLayoutScope.createBottomBarrier(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("barrier-bottom"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintReference {
    return registerBarrier(id, ConstraintBarrierDirection.Bottom, refs, margin, allowsGoneWidgets)
}

/**
 * Creates a virtual Flow that arranges referenced children in rows or columns.
 *
 * Style, bias, alignment, wrapping, gaps, and padding map to AndroidX Flow. Numeric values are
 * forwarded without DSL-level coercion; `maxElementsWrap = -1` keeps the native unlimited default.
 *
 * @sample com.viewcompose.widget.constraintlayout.samples.constraintHelpersSample
 * @param refs ordered referenced children; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param orientation primary placement axis
 * @param wrapMode row/column wrapping strategy
 * @param horizontalGap horizontal dp gap between elements
 * @param verticalGap vertical dp gap between elements
 * @param horizontalStyle default horizontal chain style
 * @param verticalStyle default vertical chain style
 * @param firstHorizontalStyle optional first-row horizontal override
 * @param firstVerticalStyle optional first-column vertical override
 * @param lastHorizontalStyle optional last-row horizontal override
 * @param lastVerticalStyle optional last-column vertical override
 * @param horizontalBias optional default horizontal chain bias
 * @param verticalBias optional default vertical chain bias
 * @param firstHorizontalBias optional first-row horizontal bias
 * @param firstVerticalBias optional first-column vertical bias
 * @param lastHorizontalBias optional last-row horizontal bias
 * @param lastVerticalBias optional last-column vertical bias
 * @param horizontalAlign cross-axis horizontal alignment
 * @param verticalAlign cross-axis vertical alignment
 * @param maxElementsWrap maximum elements per wrap line, or `-1` for unlimited
 * @param padding common dp padding applied before edge-specific values
 * @param paddingStart logical-start dp padding
 * @param paddingEnd logical-end dp padding
 * @param paddingTop top dp padding
 * @param paddingBottom bottom dp padding
 * @return reference to the virtual Flow
 * @throws IllegalArgumentException if [refs] is empty
 */
fun ConstraintLayoutScope.createFlow(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("flow"),
    orientation: ConstraintFlowOrientation = ConstraintFlowOrientation.Horizontal,
    wrapMode: ConstraintFlowWrapMode = ConstraintFlowWrapMode.None,
    horizontalGap: UiDp = UiDp.Zero,
    verticalGap: UiDp = UiDp.Zero,
    horizontalStyle: ConstraintChainStyle = ConstraintChainStyle.Spread,
    verticalStyle: ConstraintChainStyle = ConstraintChainStyle.Spread,
    firstHorizontalStyle: ConstraintChainStyle? = null,
    firstVerticalStyle: ConstraintChainStyle? = null,
    lastHorizontalStyle: ConstraintChainStyle? = null,
    lastVerticalStyle: ConstraintChainStyle? = null,
    horizontalBias: Float? = null,
    verticalBias: Float? = null,
    firstHorizontalBias: Float? = null,
    firstVerticalBias: Float? = null,
    lastHorizontalBias: Float? = null,
    lastVerticalBias: Float? = null,
    horizontalAlign: ConstraintFlowHorizontalAlign = ConstraintFlowHorizontalAlign.Center,
    verticalAlign: ConstraintFlowVerticalAlign = ConstraintFlowVerticalAlign.Center,
    maxElementsWrap: Int = -1,
    padding: UiDp = UiDp.Zero,
    paddingStart: UiDp = UiDp.Zero,
    paddingEnd: UiDp = UiDp.Zero,
    paddingTop: UiDp = UiDp.Zero,
    paddingBottom: UiDp = UiDp.Zero,
): ConstraintReference {
    require(refs.isNotEmpty()) {
        "Flow helper requires at least one referenced id."
    }
    val context = requireConstraintContext()
    context.helpers.flows += ConstraintFlowSpec(
        id = id,
        referencedIds = refs.map { ref -> ref.id },
        orientation = orientation,
        wrapMode = wrapMode,
        horizontalGap = horizontalGap,
        verticalGap = verticalGap,
        horizontalStyle = horizontalStyle,
        verticalStyle = verticalStyle,
        firstHorizontalStyle = firstHorizontalStyle,
        firstVerticalStyle = firstVerticalStyle,
        lastHorizontalStyle = lastHorizontalStyle,
        lastVerticalStyle = lastVerticalStyle,
        horizontalBias = horizontalBias,
        verticalBias = verticalBias,
        firstHorizontalBias = firstHorizontalBias,
        firstVerticalBias = firstVerticalBias,
        lastHorizontalBias = lastHorizontalBias,
        lastVerticalBias = lastVerticalBias,
        horizontalAlign = horizontalAlign,
        verticalAlign = verticalAlign,
        maxElementsWrap = maxElementsWrap,
        padding = padding,
        paddingStart = paddingStart,
        paddingEnd = paddingEnd,
        paddingTop = paddingTop,
        paddingBottom = paddingBottom,
    )
    return ConstraintReference(id)
}

/**
 * Creates a virtual Group that applies visibility and elevation to [refs].
 * @param refs referenced children; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param visibility visibility propagated by the native Group
 * @param elevation elevation in dp propagated by the native Group
 * @return reference to the virtual Group
 * @throws IllegalArgumentException if [refs] is empty
 */
fun ConstraintLayoutScope.createGroup(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("group"),
    visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
    elevation: UiDp = UiDp.Zero,
): ConstraintReference {
    require(refs.isNotEmpty()) {
        "Group helper requires at least one referenced id."
    }
    val context = requireConstraintContext()
    context.helpers.groups += ConstraintGroupSpec(
        id = id,
        referencedIds = refs.map { ref -> ref.id },
        visibility = visibility,
        elevation = elevation,
    )
    return ConstraintReference(id)
}

/**
 * Creates a virtual Layer that applies a shared transform to [refs].
 * @param refs referenced children; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param visibility visibility propagated by the native Layer
 * @param elevation elevation in dp propagated by the native Layer
 * @param rotation clockwise rotation in degrees
 * @param scaleX horizontal scale factor
 * @param scaleY vertical scale factor
 * @param translationX horizontal translation in dp
 * @param translationY vertical translation in dp
 * @param pivotX optional absolute pivot x in dp, or `null` for native computed center
 * @param pivotY optional absolute pivot y in dp, or `null` for native computed center
 * @return reference to the virtual Layer
 * @throws IllegalArgumentException if [refs] is empty
 */
fun ConstraintLayoutScope.createLayer(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("layer"),
    visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
    elevation: UiDp = UiDp.Zero,
    rotation: Float = 0f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    translationX: UiDp = UiDp.Zero,
    translationY: UiDp = UiDp.Zero,
    pivotX: UiDp? = null,
    pivotY: UiDp? = null,
): ConstraintReference {
    require(refs.isNotEmpty()) {
        "Layer helper requires at least one referenced id."
    }
    val context = requireConstraintContext()
    context.helpers.layers += ConstraintLayerSpec(
        id = id,
        referencedIds = refs.map { ref -> ref.id },
        visibility = visibility,
        elevation = elevation,
        rotation = rotation,
        scaleX = scaleX,
        scaleY = scaleY,
        translationX = translationX,
        translationY = translationY,
        pivotX = pivotX,
        pivotY = pivotY,
    )
    return ConstraintReference(id)
}

/**
 * Creates a virtual Placeholder that hosts [content] when available.
 * @param content referenced child to host, or `null` for an empty placeholder
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param emptyVisibility visibility used while no content is assigned
 * @return reference to the virtual Placeholder
 */
fun ConstraintLayoutScope.createPlaceholder(
    content: ConstraintReference? = null,
    id: String = allocHelperId("placeholder"),
    emptyVisibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Invisible,
): ConstraintReference {
    val context = requireConstraintContext()
    context.helpers.placeholders += ConstraintPlaceholderSpec(
        id = id,
        contentId = content?.id,
        emptyVisibility = emptyVisibility,
    )
    return ConstraintReference(id)
}

private fun ConstraintLayoutScope.registerChain(
    orientation: ConstraintChainOrientation,
    refs: Array<out ConstraintReference>,
    weights: List<Float>?,
    style: ConstraintChainStyle,
    bias: Float?,
) {
    validateChainWeights(weights, refs.size)
    val context = requireConstraintContext()
    context.helpers.chains += ConstraintChainSpec(
        orientation = orientation,
        referencedIds = refs.map { ref -> ref.id },
        weights = weights,
        style = style,
        bias = bias,
    )
}

/**
 * Adds an ordered horizontal chain.
 * @param refs ordered chain members; empty chains are forwarded to the renderer
 * @param weights optional member weights whose size must equal [refs]
 * @param style chain distribution policy
 * @param bias optional packed-chain bias
 * @throws IllegalArgumentException if [weights] size differs from [refs] size
 */
fun ConstraintLayoutScope.createHorizontalChain(
    vararg refs: ConstraintReference,
    weights: List<Float>? = null,
    style: ConstraintChainStyle = ConstraintChainStyle.Spread,
    bias: Float? = null,
) {
    registerChain(
        orientation = ConstraintChainOrientation.Horizontal,
        refs = refs,
        weights = weights,
        style = style,
        bias = bias,
    )
}

/**
 * Adds an ordered vertical chain.
 * @param refs ordered chain members; empty chains are forwarded to the renderer
 * @param weights optional member weights whose size must equal [refs]
 * @param style chain distribution policy
 * @param bias optional packed-chain bias
 * @throws IllegalArgumentException if [weights] size differs from [refs] size
 */
fun ConstraintLayoutScope.createVerticalChain(
    vararg refs: ConstraintReference,
    weights: List<Float>? = null,
    style: ConstraintChainStyle = ConstraintChainStyle.Spread,
    bias: Float? = null,
) {
    registerChain(
        orientation = ConstraintChainOrientation.Vertical,
        refs = refs,
        weights = weights,
        style = style,
        bias = bias,
    )
}

/**
 * Builds an immutable reusable [ConstraintSetSpec] without emitting UI nodes.
 *
 * Reusing IDs replaces earlier constraint entries; helpers remain declaration ordered. Builder
 * instances are created by [constraintSet], evaluated synchronously, and not retained afterward.
 */
class ConstraintSetBuilder internal constructor() {
    private val constraints = linkedMapOf<String, ConstraintItemSpec>()
    private val helpers = MutableConstraintHelpersCollector()

    /**
     * Creates a reference for a child/helper ID.
     * @param id set-local identity whose uniqueness is the caller's responsibility
     * @return a reference retaining [id]
     */
    fun createRef(id: String): ConstraintReference {
        return ConstraintReference(id = id)
    }

    /**
     * Creates references in the same order as [ids].
     * @param ids set-local identities
     * @return newly allocated ordered reference array
     */
    fun createRefs(vararg ids: String): Array<ConstraintReference> {
        return ids.map { id -> createRef(id) }.toTypedArray()
    }

    /**
     * Adds or replaces the complete constraint entry for [id].
     * @param id child identity used by a ConstraintLayout node
     * @param content constraint builder evaluated immediately
     */
    fun constrain(
        id: String,
        content: ConstraintConstrainScope.() -> Unit,
    ) {
        constraints[id] = buildConstraintSpec(content)
    }

    /**
     * Creates a logical-start guideline at fixed dp [offset].
     * @param offset distance from logical start
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     */
    fun createGuidelineFromStart(
        offset: UiDp,
        id: String = helpers.allocId("guideline-start"),
    ): ConstraintReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromStart,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a logical-start guideline at parent-width [fraction].
     * @param fraction native ConstraintLayout fraction, normally `0f..1f`
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     */
    fun createGuidelineFromStart(
        fraction: Float,
        id: String = helpers.allocId("guideline-start"),
    ): ConstraintReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromStart,
            position = ConstraintGuidelinePosition.Fraction(fraction),
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a logical-end guideline at fixed dp [offset].
     * @param offset distance from logical end
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     */
    fun createGuidelineFromEnd(
        offset: UiDp,
        id: String = helpers.allocId("guideline-end"),
    ): ConstraintReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromEnd,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a logical-end guideline at parent-width [fraction].
     * @param fraction native ConstraintLayout fraction, normally `0f..1f`
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     */
    fun createGuidelineFromEnd(
        fraction: Float,
        id: String = helpers.allocId("guideline-end"),
    ): ConstraintReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromEnd,
            position = ConstraintGuidelinePosition.Fraction(fraction),
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a top guideline at fixed dp [offset].
     * @param offset distance from the top edge
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     */
    fun createGuidelineFromTop(
        offset: UiDp,
        id: String = helpers.allocId("guideline-top"),
    ): ConstraintReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromTop,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a top guideline at parent-height [fraction].
     * @param fraction native ConstraintLayout fraction, normally `0f..1f`
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     */
    fun createGuidelineFromTop(
        fraction: Float,
        id: String = helpers.allocId("guideline-top"),
    ): ConstraintReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromTop,
            position = ConstraintGuidelinePosition.Fraction(fraction),
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a bottom guideline at fixed dp [offset].
     * @param offset distance from the bottom edge
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     */
    fun createGuidelineFromBottom(
        offset: UiDp,
        id: String = helpers.allocId("guideline-bottom"),
    ): ConstraintReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromBottom,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a bottom guideline at parent-height [fraction].
     * @param fraction native ConstraintLayout fraction, normally `0f..1f`
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     */
    fun createGuidelineFromBottom(
        fraction: Float,
        id: String = helpers.allocId("guideline-bottom"),
    ): ConstraintReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromBottom,
            position = ConstraintGuidelinePosition.Fraction(fraction),
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a logical-start barrier.
     * @param refs referenced children/helpers
     * @param id helper identity, auto-generated when omitted
     * @param margin dp offset from the computed extreme
     * @param allowsGoneWidgets whether gone references participate
     * @return reference to the virtual barrier
     */
    fun createStartBarrier(
        vararg refs: ConstraintReference,
        id: String = helpers.allocId("barrier-start"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintReference {
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.Start,
            referencedIds = refs.map { ref -> ref.id },
            margin = margin,
            allowsGoneWidgets = allowsGoneWidgets,
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a logical-end barrier.
     * @param refs referenced children/helpers
     * @param id helper identity, auto-generated when omitted
     * @param margin dp offset from the computed extreme
     * @param allowsGoneWidgets whether gone references participate
     * @return reference to the virtual barrier
     */
    fun createEndBarrier(
        vararg refs: ConstraintReference,
        id: String = helpers.allocId("barrier-end"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintReference {
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.End,
            referencedIds = refs.map { ref -> ref.id },
            margin = margin,
            allowsGoneWidgets = allowsGoneWidgets,
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a top barrier.
     * @param refs referenced children/helpers
     * @param id helper identity, auto-generated when omitted
     * @param margin dp offset from the computed extreme
     * @param allowsGoneWidgets whether gone references participate
     * @return reference to the virtual barrier
     */
    fun createTopBarrier(
        vararg refs: ConstraintReference,
        id: String = helpers.allocId("barrier-top"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintReference {
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.Top,
            referencedIds = refs.map { ref -> ref.id },
            margin = margin,
            allowsGoneWidgets = allowsGoneWidgets,
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a bottom barrier.
     * @param refs referenced children/helpers
     * @param id helper identity, auto-generated when omitted
     * @param margin dp offset from the computed extreme
     * @param allowsGoneWidgets whether gone references participate
     * @return reference to the virtual barrier
     */
    fun createBottomBarrier(
        vararg refs: ConstraintReference,
        id: String = helpers.allocId("barrier-bottom"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintReference {
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.Bottom,
            referencedIds = refs.map { ref -> ref.id },
            margin = margin,
            allowsGoneWidgets = allowsGoneWidgets,
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a reusable virtual Flow specification.
     *
     * @param refs ordered referenced children; must not be empty
     * @param id helper identity, auto-generated when omitted
     * @param orientation primary placement axis
     * @param wrapMode row/column wrapping strategy
     * @param horizontalGap horizontal dp gap
     * @param verticalGap vertical dp gap
     * @param horizontalStyle default horizontal chain style
     * @param verticalStyle default vertical chain style
     * @param firstHorizontalStyle optional first-row horizontal override
     * @param firstVerticalStyle optional first-column vertical override
     * @param lastHorizontalStyle optional last-row horizontal override
     * @param lastVerticalStyle optional last-column vertical override
     * @param horizontalBias optional default horizontal bias
     * @param verticalBias optional default vertical bias
     * @param firstHorizontalBias optional first-row horizontal bias
     * @param firstVerticalBias optional first-column vertical bias
     * @param lastHorizontalBias optional last-row horizontal bias
     * @param lastVerticalBias optional last-column vertical bias
     * @param horizontalAlign cross-axis horizontal alignment
     * @param verticalAlign cross-axis vertical alignment
     * @param maxElementsWrap maximum elements per line, or `-1` for unlimited
     * @param padding common dp padding applied before edge-specific values
     * @param paddingStart logical-start dp padding
     * @param paddingEnd logical-end dp padding
     * @param paddingTop top dp padding
     * @param paddingBottom bottom dp padding
     * @return reference to the virtual Flow
     * @throws IllegalArgumentException if [refs] is empty
     */
    fun createFlow(
        vararg refs: ConstraintReference,
        id: String = helpers.allocId("flow"),
        orientation: ConstraintFlowOrientation = ConstraintFlowOrientation.Horizontal,
        wrapMode: ConstraintFlowWrapMode = ConstraintFlowWrapMode.None,
        horizontalGap: UiDp = UiDp.Zero,
        verticalGap: UiDp = UiDp.Zero,
        horizontalStyle: ConstraintChainStyle = ConstraintChainStyle.Spread,
        verticalStyle: ConstraintChainStyle = ConstraintChainStyle.Spread,
        firstHorizontalStyle: ConstraintChainStyle? = null,
        firstVerticalStyle: ConstraintChainStyle? = null,
        lastHorizontalStyle: ConstraintChainStyle? = null,
        lastVerticalStyle: ConstraintChainStyle? = null,
        horizontalBias: Float? = null,
        verticalBias: Float? = null,
        firstHorizontalBias: Float? = null,
        firstVerticalBias: Float? = null,
        lastHorizontalBias: Float? = null,
        lastVerticalBias: Float? = null,
        horizontalAlign: ConstraintFlowHorizontalAlign = ConstraintFlowHorizontalAlign.Center,
        verticalAlign: ConstraintFlowVerticalAlign = ConstraintFlowVerticalAlign.Center,
        maxElementsWrap: Int = -1,
        padding: UiDp = UiDp.Zero,
        paddingStart: UiDp = UiDp.Zero,
        paddingEnd: UiDp = UiDp.Zero,
        paddingTop: UiDp = UiDp.Zero,
        paddingBottom: UiDp = UiDp.Zero,
    ): ConstraintReference {
        require(refs.isNotEmpty()) {
            "Flow helper requires at least one referenced id."
        }
        helpers.flows += ConstraintFlowSpec(
            id = id,
            referencedIds = refs.map { ref -> ref.id },
            orientation = orientation,
            wrapMode = wrapMode,
            horizontalGap = horizontalGap,
            verticalGap = verticalGap,
            horizontalStyle = horizontalStyle,
            verticalStyle = verticalStyle,
            firstHorizontalStyle = firstHorizontalStyle,
            firstVerticalStyle = firstVerticalStyle,
            lastHorizontalStyle = lastHorizontalStyle,
            lastVerticalStyle = lastVerticalStyle,
            horizontalBias = horizontalBias,
            verticalBias = verticalBias,
            firstHorizontalBias = firstHorizontalBias,
            firstVerticalBias = firstVerticalBias,
            lastHorizontalBias = lastHorizontalBias,
            lastVerticalBias = lastVerticalBias,
            horizontalAlign = horizontalAlign,
            verticalAlign = verticalAlign,
            maxElementsWrap = maxElementsWrap,
            padding = padding,
            paddingStart = paddingStart,
            paddingEnd = paddingEnd,
            paddingTop = paddingTop,
            paddingBottom = paddingBottom,
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a reusable virtual Group specification.
     * @param refs referenced children; must not be empty
     * @param id helper identity, auto-generated when omitted
     * @param visibility propagated native visibility
     * @param elevation propagated elevation in dp
     * @return reference to the virtual Group
     * @throws IllegalArgumentException if [refs] is empty
     */
    fun createGroup(
        vararg refs: ConstraintReference,
        id: String = helpers.allocId("group"),
        visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
        elevation: UiDp = UiDp.Zero,
    ): ConstraintReference {
        require(refs.isNotEmpty()) {
            "Group helper requires at least one referenced id."
        }
        helpers.groups += ConstraintGroupSpec(
            id = id,
            referencedIds = refs.map { ref -> ref.id },
            visibility = visibility,
            elevation = elevation,
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a reusable virtual Layer specification.
     * @param refs referenced children; must not be empty
     * @param id helper identity, auto-generated when omitted
     * @param visibility propagated native visibility
     * @param elevation propagated elevation in dp
     * @param rotation clockwise degrees
     * @param scaleX horizontal scale factor
     * @param scaleY vertical scale factor
     * @param translationX horizontal translation in dp
     * @param translationY vertical translation in dp
     * @param pivotX absolute pivot x in dp, or `null` for native computed center
     * @param pivotY absolute pivot y in dp, or `null` for native computed center
     * @return reference to the virtual Layer
     * @throws IllegalArgumentException if [refs] is empty
     */
    fun createLayer(
        vararg refs: ConstraintReference,
        id: String = helpers.allocId("layer"),
        visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
        elevation: UiDp = UiDp.Zero,
        rotation: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        translationX: UiDp = UiDp.Zero,
        translationY: UiDp = UiDp.Zero,
        pivotX: UiDp? = null,
        pivotY: UiDp? = null,
    ): ConstraintReference {
        require(refs.isNotEmpty()) {
            "Layer helper requires at least one referenced id."
        }
        helpers.layers += ConstraintLayerSpec(
            id = id,
            referencedIds = refs.map { ref -> ref.id },
            visibility = visibility,
            elevation = elevation,
            rotation = rotation,
            scaleX = scaleX,
            scaleY = scaleY,
            translationX = translationX,
            translationY = translationY,
            pivotX = pivotX,
            pivotY = pivotY,
        )
        return ConstraintReference(id)
    }

    /**
     * Creates a reusable virtual Placeholder specification.
     * @param content referenced child to host, or `null` for empty
     * @param id helper identity, auto-generated when omitted
     * @param emptyVisibility visibility used while content is absent
     * @return reference to the virtual Placeholder
     */
    fun createPlaceholder(
        content: ConstraintReference? = null,
        id: String = helpers.allocId("placeholder"),
        emptyVisibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Invisible,
    ): ConstraintReference {
        helpers.placeholders += ConstraintPlaceholderSpec(
            id = id,
            contentId = content?.id,
            emptyVisibility = emptyVisibility,
        )
        return ConstraintReference(id)
    }

    /**
     * Adds an ordered reusable horizontal chain.
     * @param refs ordered members
     * @param weights optional weights whose size must equal [refs]
     * @param style chain distribution policy
     * @param bias optional packed-chain bias
     * @throws IllegalArgumentException if [weights] size differs from [refs] size
     */
    fun createHorizontalChain(
        vararg refs: ConstraintReference,
        weights: List<Float>? = null,
        style: ConstraintChainStyle = ConstraintChainStyle.Spread,
        bias: Float? = null,
    ) {
        validateChainWeights(weights, refs.size)
        helpers.chains += ConstraintChainSpec(
            orientation = ConstraintChainOrientation.Horizontal,
            referencedIds = refs.map { ref -> ref.id },
            weights = weights,
            style = style,
            bias = bias,
        )
    }

    /**
     * Adds an ordered reusable vertical chain.
     * @param refs ordered members
     * @param weights optional weights whose size must equal [refs]
     * @param style chain distribution policy
     * @param bias optional packed-chain bias
     * @throws IllegalArgumentException if [weights] size differs from [refs] size
     */
    fun createVerticalChain(
        vararg refs: ConstraintReference,
        weights: List<Float>? = null,
        style: ConstraintChainStyle = ConstraintChainStyle.Spread,
        bias: Float? = null,
    ) {
        validateChainWeights(weights, refs.size)
        helpers.chains += ConstraintChainSpec(
            orientation = ConstraintChainOrientation.Vertical,
            referencedIds = refs.map { ref -> ref.id },
            weights = weights,
            style = style,
            bias = bias,
        )
    }

    internal fun build(): ConstraintSetSpec {
        return ConstraintSetSpec(
            constraints = constraints.toMap(),
            helpers = helpers.toSpec(),
        )
    }
}

/**
 * Builds an immutable standalone constraint set for reuse by [ConstraintLayout].
 *
 * [content] executes synchronously and its mutable builder is discarded. The returned maps/lists are
 * snapshots, so later calls build independent sets.
 *
 * @sample com.viewcompose.widget.constraintlayout.samples.constraintSetSample
 * @param content reusable constraint and helper declarations
 * @return immutable constraint-set specification
 */
fun constraintSet(content: ConstraintSetBuilder.() -> Unit): ConstraintSetSpec {
    return ConstraintSetBuilder()
        .apply(content)
        .build()
}
