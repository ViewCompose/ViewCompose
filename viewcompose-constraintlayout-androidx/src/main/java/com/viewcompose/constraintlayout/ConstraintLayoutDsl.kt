package com.viewcompose.constraintlayout

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
import com.viewcompose.ui.node.spec.ConstraintRatio
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.unit.UiDp
import com.viewcompose.ui.foundation.UiDslMarker
import com.viewcompose.ui.foundation.UiTreeBuilder

/**
 * Identifies any child or virtual helper declared in one ConstraintLayout specification.
 *
 * This identity plane is accepted by helper membership APIs. Anchor APIs use the narrower
 * horizontal, vertical, or baseline target planes, so invalid cross-axis links fail at compile
 * time instead of reaching graph preflight.
 *
 * @property id non-blank layout-local identity
 */
sealed interface ConstraintLayoutReference {
    val id: String
}

/**
 * Target that exposes logical start/end anchors.
 *
 * @property id child/helper identity, or `null` for the owning ConstraintLayout parent
 */
sealed interface ConstraintHorizontalAnchorTarget {
    val id: String?
}

/**
 * Target that exposes physical top/bottom anchors.
 *
 * @property id child/helper identity, or `null` for the owning ConstraintLayout parent
 */
sealed interface ConstraintVerticalAnchorTarget {
    val id: String?
}

/**
 * Target that exposes a native text baseline.
 *
 * @property id non-blank child identity
 */
sealed interface ConstraintBaselineAnchorTarget {
    val id: String
}

/**
 * Identifies a constraint-capable child, Flow, or Placeholder.
 *
 * IDs are matched as strings by the renderer and must be unique within the owning layout. The
 * constructor rejects blank IDs; complete child/helper namespace uniqueness is validated before
 * native mutation. This full reference exposes horizontal, vertical, and baseline anchors.
 *
 * @property id stable local identifier shared by layoutId, constraints, and helpers
 * @throws IllegalArgumentException if [id] is blank
 */
data class ConstraintReference(
    override val id: String,
) : ConstraintLayoutReference,
    ConstraintHorizontalAnchorTarget,
    ConstraintVerticalAnchorTarget,
    ConstraintBaselineAnchorTarget {
    init {
        require(id.isNotBlank()) { "ConstraintReference.id must not be blank." }
    }
}

/** Reference returned by logical start/end Guidelines and Barriers. */
sealed interface ConstraintHorizontalAnchorReference :
    ConstraintLayoutReference,
    ConstraintHorizontalAnchorTarget

/** Reference returned by physical top/bottom Guidelines and Barriers. */
sealed interface ConstraintVerticalAnchorReference :
    ConstraintLayoutReference,
    ConstraintVerticalAnchorTarget

/** Identity-only reference returned by non-anchor Group and Layer helpers. */
sealed interface ConstraintHelperReference : ConstraintLayoutReference

private data class HorizontalAnchorReference(
    override val id: String,
) : ConstraintHorizontalAnchorReference {
    init {
        require(id.isNotBlank()) { "Constraint helper ID must not be blank." }
    }
}

private data class VerticalAnchorReference(
    override val id: String,
) : ConstraintVerticalAnchorReference {
    init {
        require(id.isNotBlank()) { "Constraint helper ID must not be blank." }
    }
}

private data class HelperReference(
    override val id: String,
) : ConstraintHelperReference {
    init {
        require(id.isNotBlank()) { "Constraint helper ID must not be blank." }
    }
}

/** Canonical anchor target for the owning ConstraintLayout rather than a child ID. */
data object ConstraintParentReference :
    ConstraintHorizontalAnchorTarget,
    ConstraintVerticalAnchorTarget {
    /** Always `null`, which the renderer interprets as the parent. */
    override val id: String? = null
}

/** Returns the canonical reference to the current ConstraintLayout parent. */
val parent: ConstraintParentReference
    get() = ConstraintParentReference

/** Collects helper specs created by one ConstraintLayout DSL evaluation. */
private class MutableConstraintHelpersCollector {
    private var nextAutoId = 0
    private val helperKinds = mutableMapOf<String, String>()
    val guidelines = uniqueHelperList("Guideline", ConstraintGuidelineSpec::id)
    val barriers = uniqueHelperList("Barrier", ConstraintBarrierSpec::id)
    val chains = mutableListOf<ConstraintChainSpec>()
    val flows = uniqueHelperList("Flow", ConstraintFlowSpec::id)
    val groups = uniqueHelperList("Group", ConstraintGroupSpec::id)
    val layers = uniqueHelperList("Layer", ConstraintLayerSpec::id)
    val placeholders = uniqueHelperList("Placeholder", ConstraintPlaceholderSpec::id)

    private fun <T> uniqueHelperList(
        kind: String,
        id: (T) -> String,
    ): MutableList<T> = object : AbstractMutableList<T>() {
        private val values = mutableListOf<T>()

        override val size: Int
            get() = values.size

        override fun get(index: Int): T = values[index]

        override fun add(index: Int, element: T) {
            val helperId = id(element)
            require(helperId.isNotBlank()) { "$kind helper ID must not be blank." }
            val previousKind = helperKinds.putIfAbsent(helperId, kind)
            require(previousKind == null) {
                "Helper ID '$helperId' is already declared as $previousKind."
            }
            values.add(index, element)
        }

        override fun removeAt(index: Int): T = error("Constraint helper declarations are append-only.")

        override fun set(index: Int, element: T): T = error("Constraint helper declarations are append-only.")
    }

    fun allocId(prefix: String): String {
        val id = "$prefix-${nextAutoId}"
        nextAutoId += 1
        return id
    }

    fun toSpec(): ConstraintHelpersSpec {
        return ConstraintHelpersSpec(
            guidelines = guidelines.toList(),
            barriers = barriers.toList(),
            chains = chains.toList(),
            flows = flows.toList(),
            groups = groups.toList(),
            layers = layers.toList(),
            placeholders = placeholders.toList(),
        )
    }
}

/**
 * Dedicated receiver for one [ConstraintLayout] content block.
 *
 * The scope remains a full [UiTreeBuilder], so every ordinary ViewCompose widget is available.
 * Constraint references and virtual helpers are owned directly by this receiver, preventing their
 * use through an unrelated builder and preventing an outer layout's helpers from leaking into a
 * nested layout DSL. The scope is created and consumed synchronously by [ConstraintLayout].
 * Retaining it is unsupported; reference/helper calls after content completes fail immediately.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintLayoutSample
 */
@UiDslMarker
class ConstraintLayoutScope internal constructor() : UiTreeBuilder() {
    private val helpers = MutableConstraintHelpersCollector()
    private var active = true

    internal fun ensureActive() {
        check(active) {
            "ConstraintLayoutScope is no longer active; declare references and helpers during ConstraintLayout content."
        }
    }

    internal fun allocHelperId(prefix: String): String {
        ensureActive()
        return helpers.allocId(prefix)
    }

    internal fun addGuideline(spec: ConstraintGuidelineSpec) {
        ensureActive()
        helpers.guidelines += spec
    }

    internal fun addBarrier(spec: ConstraintBarrierSpec) {
        ensureActive()
        helpers.barriers += spec
    }

    internal fun addChain(spec: ConstraintChainSpec) {
        ensureActive()
        helpers.chains += spec
    }

    internal fun addFlow(spec: ConstraintFlowSpec) {
        ensureActive()
        helpers.flows += spec
    }

    internal fun addGroup(spec: ConstraintGroupSpec) {
        ensureActive()
        helpers.groups += spec
    }

    internal fun addLayer(spec: ConstraintLayerSpec) {
        ensureActive()
        helpers.layers += spec
    }

    internal fun addPlaceholder(spec: ConstraintPlaceholderSpec) {
        ensureActive()
        helpers.placeholders += spec
    }

    internal fun buildHelpers(): ConstraintHelpersSpec {
        ensureActive()
        active = false
        return helpers.toSpec()
    }
}

private fun anchorTarget(
    id: String?,
    anchor: ConstraintAnchor,
): ConstraintAnchorTarget {
    return ConstraintAnchorTarget(
        id = id,
        anchor = anchor,
    )
}

/**
 * Builds the complete constraint specification for one child ID.
 *
 * Repeated calls targeting the same source anchor replace the previous link. Local ranges and
 * mutually exclusive circle/edge states fail when the block completes; the renderer validates the
 * resulting complete graph again before native mutation.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintLayoutSample
 */
@UiDslMarker
class ConstraintConstrainScope internal constructor() {
    private var start: ConstraintAnchorLink? = null
    private var end: ConstraintAnchorLink? = null
    private var top: ConstraintAnchorLink? = null
    private var bottom: ConstraintAnchorLink? = null
    private var baseline: ConstraintAnchorLink? = null
    /** Width mode read when the constraint block completes; defaults to native wrap content. */
    var width: ConstraintDimension = ConstraintDimension.WrapContent
    /** Height mode read when the constraint block completes; defaults to native wrap content. */
    var height: ConstraintDimension = ConstraintDimension.WrapContent
    /** Optional finite horizontal bias in `0f..1f`, validated when the block completes. */
    var horizontalBias: Float? = null
    /** Optional finite vertical bias in `0f..1f`, validated when the block completes. */
    var verticalBias: Float? = null
    /** Optional positive typed ratio requiring at least one match-constraint dimension. */
    var ratio: ConstraintRatio? = null
    private var circle: ConstraintCircleSpec? = null

    /**
     * Connects this child's logical start to [target]'s logical start.
     * @param target parent, child, or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun startToStart(
        target: ConstraintHorizontalAnchorTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        start = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Start),
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
        target: ConstraintHorizontalAnchorTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        start = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.End),
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
        target: ConstraintHorizontalAnchorTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        end = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Start),
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
        target: ConstraintHorizontalAnchorTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        end = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.End),
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
        target: ConstraintVerticalAnchorTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        top = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Top),
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
        target: ConstraintVerticalAnchorTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        top = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Bottom),
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
        target: ConstraintVerticalAnchorTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        bottom = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Top),
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
        target: ConstraintVerticalAnchorTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        bottom = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Bottom),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's text baseline to [target]'s baseline.
     * @param target child whose native baseline is the destination
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun baselineToBaseline(
        target: ConstraintBaselineAnchorTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        baseline = ConstraintAnchorLink(
            target = ConstraintAnchorTarget.ref(
                id = target.id,
                anchor = ConstraintAnchor.Baseline,
            ),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's baseline to [target]'s top.
     * @param target parent, child, or helper anchor target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     */
    fun baselineToTop(
        target: ConstraintVerticalAnchorTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        baseline = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Top),
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
        target: ConstraintVerticalAnchorTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        baseline = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Bottom),
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
    fun centerHorizontallyTo(target: ConstraintHorizontalAnchorTarget = parent) {
        startToStart(target)
        endToEnd(target)
    }

    /**
     * Replaces top/bottom links so this child is vertically centered on [target].
     * @param target parent, child, or helper to center against
     */
    fun centerVerticallyTo(target: ConstraintVerticalAnchorTarget = parent) {
        topToTop(target)
        bottomToBottom(target)
    }

    internal fun build(): ConstraintItemSpec {
        validateBias(horizontalBias, "horizontalBias")
        validateBias(verticalBias, "verticalBias")
        listOfNotNull(start, end, top, bottom, baseline).forEach { link ->
            require(link.margin.value.isFinite() && link.margin.value >= 0f) {
                "Constraint margin must be finite and non-negative."
            }
            val goneMargin = link.goneMargin
            require(goneMargin == null || goneMargin.value.isFinite() && goneMargin.value >= 0f) {
                "Constraint goneMargin must be finite and non-negative."
            }
        }
        require(baseline == null || top == null && bottom == null) {
            "A baseline link is mutually exclusive with top and bottom positioning."
        }
        val hasEdgeOrBaselineLink = listOf(start, end, top, bottom, baseline).any { it != null }
        require(circle == null || !hasEdgeOrBaselineLink) {
            "Circular positioning is mutually exclusive with edge and baseline links."
        }
        circle?.let { circleSpec ->
            require(circleSpec.radius.value.isFinite() && circleSpec.radius.value >= 0f) {
                "Circular radius must be finite and non-negative."
            }
            require(circleSpec.angle.isFinite() && circleSpec.angle >= 0f && circleSpec.angle < 360f) {
                "Circular angle must be finite and within 0f..<360f."
            }
        }
        require(ratio == null || width is ConstraintDimension.MatchConstraints || height is ConstraintDimension.MatchConstraints) {
            "Constraint ratio requires width or height to use ConstraintDimension.MatchConstraints."
        }
        return ConstraintItemSpec(
            start = start,
            end = end,
            top = top,
            bottom = bottom,
            baseline = baseline,
            width = width,
            height = height,
            horizontalBias = horizontalBias,
            verticalBias = verticalBias,
            ratio = ratio,
            circle = circle,
        )
    }
}

private fun validateBias(value: Float?, field: String) {
    require(value == null || value.isFinite() && value in 0f..1f) {
        "Constraint $field must be finite and within 0f..1f."
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
    require(expectedSize >= 2) {
        "Constraint chain requires at least two referenced ids."
    }
    if (weights == null) {
        return
    }
    require(weights.size == expectedSize) {
        "Constraint chain weights size must match referenced ids size. expected=$expectedSize actual=${weights.size}"
    }
    require(weights.all { weight -> weight.isFinite() && weight > 0f }) {
        "Constraint chain weights must be finite and greater than zero."
    }
}

private fun validateChainReferences(
    refs: Array<out ConstraintReference>,
    bias: Float?,
) {
    require(refs.map { ref -> ref.id }.toSet().size == refs.size) {
        "Constraint chain referenced ids must be unique."
    }
    validateBias(bias, "chain bias")
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
 * @throws IllegalArgumentException if the completed constraint has an invalid range or mutually
 * exclusive positioning modes
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
 * @throws IllegalArgumentException if [id] is blank or the completed constraint has an invalid
 * range or mutually exclusive positioning modes
 */
fun Modifier.constrain(
    id: String,
    content: ConstraintConstrainScope.() -> Unit,
): Modifier {
    require(id.isNotBlank()) { "Constraint ID must not be blank." }
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
 * A supplied [constraintSet] provides reusable constraints; inline child modifiers and helpers are
 * encoded alongside it for renderer reconciliation. [content] executes on one dedicated
 * [ConstraintLayoutScope], then its helper declarations are frozen into the emitted NodeSpec.
 * Nested ConstraintLayouts therefore have statically isolated receivers and independent helper
 * ownership without thread-local state.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintLayoutSample
 * @param key optional sibling identity used during reconciliation
 * @param constraintSet reusable external constraints, or `null` for inline-only constraints
 * @param modifier layout, drawing, input, and semantics behavior for the native container
 * @param content children and inline helper declarations
 * @throws IllegalArgumentException if [content] declares an invalid local constraint/helper value
 * or duplicate helper ID
 */
fun UiTreeBuilder.ConstraintLayout(
    key: Any? = null,
    constraintSet: ConstraintSetSpec? = null,
    modifier: Modifier = Modifier,
    content: ConstraintLayoutScope.() -> Unit,
) {
    emitScoped(
        type = NodeType.ConstraintLayout,
        key = key,
        inputs = listOf(constraintSet),
        modifier = modifier,
        scopeFactory = ::ConstraintLayoutScope,
        spec = {
            ConstraintLayoutNodeProps(
                constraintSet = constraintSet,
                helpers = buildHelpers(),
            )
        },
        content = content,
    )
}

/**
 * Creates a reference usable by constraints and virtual-helper APIs in the current layout.
 * @receiver active ConstraintLayout content scope
 * @param id layout-local child/helper ID; uniqueness is the caller's responsibility
 * @return a reference retaining [id]
 * @throws IllegalArgumentException if [id] is blank
 * @throws IllegalStateException if the receiver was retained after its content completed
 */
fun ConstraintLayoutScope.createRef(id: String): ConstraintReference {
    ensureActive()
    return ConstraintReference(id = id)
}

/**
 * Creates references in the same order as [ids].
 * @receiver active ConstraintLayout content scope
 * @param ids layout-local IDs whose uniqueness is the caller's responsibility
 * @return newly allocated ordered reference array
 * @throws IllegalArgumentException if any ID is blank
 */
fun ConstraintLayoutScope.createRefs(vararg ids: String): Array<ConstraintReference> {
    return ids.map { id -> createRef(id) }.toTypedArray()
}

/**
 * Creates a logical-start guideline at a fixed dp [offset] from the parent edge.
 * @receiver active ConstraintLayout content scope
 * @param offset distance from logical start in dp
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this layout
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromStart(
    offset: UiDp,
    id: String = allocHelperId("guideline-start"),
): ConstraintHorizontalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromStart,
        position = ConstraintGuidelinePosition.Offset(offset),
    ))
    return HorizontalAnchorReference(id)
}

/**
 * Creates a logical-start guideline at parent-width [fraction].
 * @receiver active ConstraintLayout content scope
 * @param fraction finite parent-width fraction in `0f..1f`, validated with the complete graph
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this layout
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromStart(
    fraction: Float,
    id: String = allocHelperId("guideline-start"),
): ConstraintHorizontalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromStart,
        position = ConstraintGuidelinePosition.Fraction(fraction),
    ))
    return HorizontalAnchorReference(id)
}

/**
 * Creates a logical-end guideline at a fixed dp [offset] from the parent edge.
 * @receiver active ConstraintLayout content scope
 * @param offset distance from logical end in dp
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this layout
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromEnd(
    offset: UiDp,
    id: String = allocHelperId("guideline-end"),
): ConstraintHorizontalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromEnd,
        position = ConstraintGuidelinePosition.Offset(offset),
    ))
    return HorizontalAnchorReference(id)
}

/**
 * Creates a logical-end guideline at parent-width [fraction].
 * @receiver active ConstraintLayout content scope
 * @param fraction finite parent-width fraction in `0f..1f`, validated with the complete graph
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this layout
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromEnd(
    fraction: Float,
    id: String = allocHelperId("guideline-end"),
): ConstraintHorizontalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromEnd,
        position = ConstraintGuidelinePosition.Fraction(fraction),
    ))
    return HorizontalAnchorReference(id)
}

/**
 * Creates a top guideline at a fixed dp [offset] from the parent edge.
 * @receiver active ConstraintLayout content scope
 * @param offset distance from top in dp
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this layout
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromTop(
    offset: UiDp,
    id: String = allocHelperId("guideline-top"),
): ConstraintVerticalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromTop,
        position = ConstraintGuidelinePosition.Offset(offset),
    ))
    return VerticalAnchorReference(id)
}

/**
 * Creates a top guideline at parent-height [fraction].
 * @receiver active ConstraintLayout content scope
 * @param fraction finite parent-height fraction in `0f..1f`, validated with the complete graph
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this layout
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromTop(
    fraction: Float,
    id: String = allocHelperId("guideline-top"),
): ConstraintVerticalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromTop,
        position = ConstraintGuidelinePosition.Fraction(fraction),
    ))
    return VerticalAnchorReference(id)
}

/**
 * Creates a bottom guideline at a fixed dp [offset] from the parent edge.
 * @receiver active ConstraintLayout content scope
 * @param offset distance from bottom in dp
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this layout
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromBottom(
    offset: UiDp,
    id: String = allocHelperId("guideline-bottom"),
): ConstraintVerticalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromBottom,
        position = ConstraintGuidelinePosition.Offset(offset),
    ))
    return VerticalAnchorReference(id)
}

/**
 * Creates a bottom guideline at parent-height [fraction].
 * @receiver active ConstraintLayout content scope
 * @param fraction finite parent-height fraction in `0f..1f`, validated with the complete graph
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this layout
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromBottom(
    fraction: Float,
    id: String = allocHelperId("guideline-bottom"),
): ConstraintVerticalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromBottom,
        position = ConstraintGuidelinePosition.Fraction(fraction),
    ))
    return VerticalAnchorReference(id)
}

/** Registers one inline barrier helper in the active layout context. */
private fun ConstraintLayoutScope.registerBarrier(
    id: String,
    direction: ConstraintBarrierDirection,
    refs: Array<out ConstraintLayoutReference>,
    margin: UiDp,
    allowsGoneWidgets: Boolean,
) {
    require(refs.isNotEmpty()) {
        "Barrier helper requires at least one referenced id."
    }
    addBarrier(ConstraintBarrierSpec(
        id = id,
        direction = direction,
        referencedIds = refs.map { ref -> ref.id },
        margin = margin,
        allowsGoneWidgets = allowsGoneWidgets,
    ))
}

/**
 * Creates a logical-start barrier over [refs].
 * @param refs referenced children/helpers; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin additional dp offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any helper
 * kind in the same inline declaration source
 */
fun ConstraintLayoutScope.createStartBarrier(
    vararg refs: ConstraintLayoutReference,
    id: String = allocHelperId("barrier-start"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintHorizontalAnchorReference {
    registerBarrier(id, ConstraintBarrierDirection.Start, refs, margin, allowsGoneWidgets)
    return HorizontalAnchorReference(id)
}

/**
 * Creates a logical-end barrier over [refs].
 * @param refs referenced children/helpers; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin additional dp offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any helper
 * kind in the same inline declaration source
 */
fun ConstraintLayoutScope.createEndBarrier(
    vararg refs: ConstraintLayoutReference,
    id: String = allocHelperId("barrier-end"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintHorizontalAnchorReference {
    registerBarrier(id, ConstraintBarrierDirection.End, refs, margin, allowsGoneWidgets)
    return HorizontalAnchorReference(id)
}

/**
 * Creates a top barrier over [refs].
 * @param refs referenced children/helpers; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin additional dp offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any helper
 * kind in the same inline declaration source
 */
fun ConstraintLayoutScope.createTopBarrier(
    vararg refs: ConstraintLayoutReference,
    id: String = allocHelperId("barrier-top"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintVerticalAnchorReference {
    registerBarrier(id, ConstraintBarrierDirection.Top, refs, margin, allowsGoneWidgets)
    return VerticalAnchorReference(id)
}

/**
 * Creates a bottom barrier over [refs].
 * @param refs referenced children/helpers; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin additional dp offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any helper
 * kind in the same inline declaration source
 */
fun ConstraintLayoutScope.createBottomBarrier(
    vararg refs: ConstraintLayoutReference,
    id: String = allocHelperId("barrier-bottom"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintVerticalAnchorReference {
    registerBarrier(id, ConstraintBarrierDirection.Bottom, refs, margin, allowsGoneWidgets)
    return VerticalAnchorReference(id)
}

/**
 * Creates a virtual Flow that arranges referenced children in rows or columns.
 *
 * Style, bias, alignment, wrapping, gaps, and padding map to AndroidX Flow. Numeric values are
 * forwarded without DSL-level coercion and are validated during complete-graph preflight;
 * `maxElementsWrap = -1` keeps the native unlimited default.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintHelpersSample
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
 * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any helper
 * kind in the same inline declaration source
 */
fun ConstraintLayoutScope.createFlow(
    vararg refs: ConstraintLayoutReference,
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
    addFlow(ConstraintFlowSpec(
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
    ))
    return ConstraintReference(id)
}

/**
 * Creates a virtual Group that applies visibility and elevation to [refs].
 * @param refs referenced children; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param visibility visibility propagated by the native Group
 * @param elevation elevation in dp propagated by the native Group
 * @return reference to the virtual Group
 * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any helper
 * kind in the same inline declaration source
 */
fun ConstraintLayoutScope.createGroup(
    vararg refs: ConstraintLayoutReference,
    id: String = allocHelperId("group"),
    visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
    elevation: UiDp = UiDp.Zero,
): ConstraintHelperReference {
    require(refs.isNotEmpty()) {
        "Group helper requires at least one referenced id."
    }
    addGroup(ConstraintGroupSpec(
        id = id,
        referencedIds = refs.map { ref -> ref.id },
        visibility = visibility,
        elevation = elevation,
    ))
    return HelperReference(id)
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
 * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any helper
 * kind in the same inline declaration source
 */
fun ConstraintLayoutScope.createLayer(
    vararg refs: ConstraintLayoutReference,
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
): ConstraintHelperReference {
    require(refs.isNotEmpty()) {
        "Layer helper requires at least one referenced id."
    }
    addLayer(ConstraintLayerSpec(
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
    ))
    return HelperReference(id)
}

/**
 * Creates a virtual Placeholder that hosts [content] when available.
 * @param content referenced child to host, or `null` for an empty placeholder
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param emptyVisibility visibility used while no content is assigned
 * @return reference to the virtual Placeholder
 * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in the same
 * inline declaration source
 */
fun ConstraintLayoutScope.createPlaceholder(
    content: ConstraintReference? = null,
    id: String = allocHelperId("placeholder"),
    emptyVisibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Invisible,
): ConstraintReference {
    addPlaceholder(ConstraintPlaceholderSpec(
        id = id,
        contentId = content?.id,
        emptyVisibility = emptyVisibility,
    ))
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
    validateChainReferences(refs, bias)
    addChain(ConstraintChainSpec(
        orientation = orientation,
        referencedIds = refs.map { ref -> ref.id },
        weights = weights,
        style = style,
        bias = bias,
    ))
}

/**
 * Adds an ordered horizontal chain.
 * @param refs ordered chain members; at least two unique references are required
 * @param weights optional member weights whose size must equal [refs]
 * @param style chain distribution policy
 * @param bias optional packed-chain bias
 * @throws IllegalArgumentException if references, weights, or bias are invalid
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
 * @param refs ordered chain members; at least two unique references are required
 * @param weights optional member weights whose size must equal [refs]
 * @param style chain distribution policy
 * @param bias optional packed-chain bias
 * @throws IllegalArgumentException if references, weights, or bias are invalid
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
 * Constraint and helper IDs must be unique within their declaration source. Builder instances are
 * created by [constraintSet], evaluated synchronously, and not retained afterward.
 */
@UiDslMarker
class ConstraintSetBuilder internal constructor() {
    private val constraints = linkedMapOf<String, ConstraintItemSpec>()
    private val helpers = MutableConstraintHelpersCollector()

    /**
     * Creates a reference for a child/helper ID.
     * @param id set-local identity whose uniqueness is the caller's responsibility
     * @return a reference retaining [id]
     * @throws IllegalArgumentException if [id] is blank
     */
    fun createRef(id: String): ConstraintReference {
        return ConstraintReference(id = id)
    }

    /**
     * Creates references in the same order as [ids].
     * @param ids set-local identities
     * @return newly allocated ordered reference array
     * @throws IllegalArgumentException if any ID is blank
     */
    fun createRefs(vararg ids: String): Array<ConstraintReference> {
        return ids.map { id -> createRef(id) }.toTypedArray()
    }

    /**
     * Adds the complete constraint entry for [ref].
     * @param ref child, Flow, or Placeholder reference used by a ConstraintLayout node
     * @param content constraint builder evaluated immediately
     * @throws IllegalArgumentException if [ref] is already constrained, or [content]
     * completes with an invalid range or mutually exclusive positioning modes
     */
    fun constrain(
        ref: ConstraintReference,
        content: ConstraintConstrainScope.() -> Unit,
    ) {
        require(ref.id !in constraints) { "Constraint ID '${ref.id}' is already declared in this builder." }
        constraints[ref.id] = buildConstraintSpec(content)
    }

    /**
     * Creates a logical-start guideline at fixed dp [offset].
     * @param offset distance from logical start
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this set
     */
    fun createGuidelineFromStart(
        offset: UiDp,
        id: String = helpers.allocId("guideline-start"),
    ): ConstraintHorizontalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromStart,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a logical-start guideline at parent-width [fraction].
     * @param fraction finite parent-width fraction in `0f..1f`, validated with the complete graph
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this set
     */
    fun createGuidelineFromStart(
        fraction: Float,
        id: String = helpers.allocId("guideline-start"),
    ): ConstraintHorizontalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromStart,
            position = ConstraintGuidelinePosition.Fraction(fraction),
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a logical-end guideline at fixed dp [offset].
     * @param offset distance from logical end
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this set
     */
    fun createGuidelineFromEnd(
        offset: UiDp,
        id: String = helpers.allocId("guideline-end"),
    ): ConstraintHorizontalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromEnd,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a logical-end guideline at parent-width [fraction].
     * @param fraction finite parent-width fraction in `0f..1f`, validated with the complete graph
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this set
     */
    fun createGuidelineFromEnd(
        fraction: Float,
        id: String = helpers.allocId("guideline-end"),
    ): ConstraintHorizontalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromEnd,
            position = ConstraintGuidelinePosition.Fraction(fraction),
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a top guideline at fixed dp [offset].
     * @param offset distance from the top edge
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this set
     */
    fun createGuidelineFromTop(
        offset: UiDp,
        id: String = helpers.allocId("guideline-top"),
    ): ConstraintVerticalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromTop,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return VerticalAnchorReference(id)
    }

    /**
     * Creates a top guideline at parent-height [fraction].
     * @param fraction finite parent-height fraction in `0f..1f`, validated with the complete graph
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this set
     */
    fun createGuidelineFromTop(
        fraction: Float,
        id: String = helpers.allocId("guideline-top"),
    ): ConstraintVerticalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromTop,
            position = ConstraintGuidelinePosition.Fraction(fraction),
        )
        return VerticalAnchorReference(id)
    }

    /**
     * Creates a bottom guideline at fixed dp [offset].
     * @param offset distance from the bottom edge
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this set
     */
    fun createGuidelineFromBottom(
        offset: UiDp,
        id: String = helpers.allocId("guideline-bottom"),
    ): ConstraintVerticalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromBottom,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return VerticalAnchorReference(id)
    }

    /**
     * Creates a bottom guideline at parent-height [fraction].
     * @param fraction finite parent-height fraction in `0f..1f`, validated with the complete graph
     * @param id helper identity, auto-generated when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this set
     */
    fun createGuidelineFromBottom(
        fraction: Float,
        id: String = helpers.allocId("guideline-bottom"),
    ): ConstraintVerticalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromBottom,
            position = ConstraintGuidelinePosition.Fraction(fraction),
        )
        return VerticalAnchorReference(id)
    }

    /**
     * Creates a logical-start barrier.
     * @param refs referenced children/helpers
     * @param id helper identity, auto-generated when omitted
     * @param margin dp offset from the computed extreme
     * @param allowsGoneWidgets whether gone references participate
     * @return reference to the virtual barrier
     * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any
     * helper kind in this set
     */
    fun createStartBarrier(
        vararg refs: ConstraintLayoutReference,
        id: String = helpers.allocId("barrier-start"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintHorizontalAnchorReference {
        require(refs.isNotEmpty()) {
            "Barrier helper requires at least one referenced id."
        }
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.Start,
            referencedIds = refs.map { ref -> ref.id },
            margin = margin,
            allowsGoneWidgets = allowsGoneWidgets,
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a logical-end barrier.
     * @param refs referenced children/helpers
     * @param id helper identity, auto-generated when omitted
     * @param margin dp offset from the computed extreme
     * @param allowsGoneWidgets whether gone references participate
     * @return reference to the virtual barrier
     * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any
     * helper kind in this set
     */
    fun createEndBarrier(
        vararg refs: ConstraintLayoutReference,
        id: String = helpers.allocId("barrier-end"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintHorizontalAnchorReference {
        require(refs.isNotEmpty()) {
            "Barrier helper requires at least one referenced id."
        }
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.End,
            referencedIds = refs.map { ref -> ref.id },
            margin = margin,
            allowsGoneWidgets = allowsGoneWidgets,
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a top barrier.
     * @param refs referenced children/helpers
     * @param id helper identity, auto-generated when omitted
     * @param margin dp offset from the computed extreme
     * @param allowsGoneWidgets whether gone references participate
     * @return reference to the virtual barrier
     * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any
     * helper kind in this set
     */
    fun createTopBarrier(
        vararg refs: ConstraintLayoutReference,
        id: String = helpers.allocId("barrier-top"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintVerticalAnchorReference {
        require(refs.isNotEmpty()) {
            "Barrier helper requires at least one referenced id."
        }
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.Top,
            referencedIds = refs.map { ref -> ref.id },
            margin = margin,
            allowsGoneWidgets = allowsGoneWidgets,
        )
        return VerticalAnchorReference(id)
    }

    /**
     * Creates a bottom barrier.
     * @param refs referenced children/helpers
     * @param id helper identity, auto-generated when omitted
     * @param margin dp offset from the computed extreme
     * @param allowsGoneWidgets whether gone references participate
     * @return reference to the virtual barrier
     * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any
     * helper kind in this set
     */
    fun createBottomBarrier(
        vararg refs: ConstraintLayoutReference,
        id: String = helpers.allocId("barrier-bottom"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintVerticalAnchorReference {
        require(refs.isNotEmpty()) {
            "Barrier helper requires at least one referenced id."
        }
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.Bottom,
            referencedIds = refs.map { ref -> ref.id },
            margin = margin,
            allowsGoneWidgets = allowsGoneWidgets,
        )
        return VerticalAnchorReference(id)
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
     * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any
     * helper kind in this set
     */
    fun createFlow(
        vararg refs: ConstraintLayoutReference,
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
     * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any
     * helper kind in this set
     */
    fun createGroup(
        vararg refs: ConstraintLayoutReference,
        id: String = helpers.allocId("group"),
        visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
        elevation: UiDp = UiDp.Zero,
    ): ConstraintHelperReference {
        require(refs.isNotEmpty()) {
            "Group helper requires at least one referenced id."
        }
        helpers.groups += ConstraintGroupSpec(
            id = id,
            referencedIds = refs.map { ref -> ref.id },
            visibility = visibility,
            elevation = elevation,
        )
        return HelperReference(id)
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
     * @throws IllegalArgumentException if [refs] is empty, or [id] is blank or duplicates any
     * helper kind in this set
     */
    fun createLayer(
        vararg refs: ConstraintLayoutReference,
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
    ): ConstraintHelperReference {
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
        return HelperReference(id)
    }

    /**
     * Creates a reusable virtual Placeholder specification.
     * @param content referenced child to host, or `null` for empty
     * @param id helper identity, auto-generated when omitted
     * @param emptyVisibility visibility used while content is absent
     * @return reference to the virtual Placeholder
     * @throws IllegalArgumentException if [id] is blank or duplicates any helper kind in this set
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
     * @throws IllegalArgumentException if references, weights, or bias are invalid
     */
    fun createHorizontalChain(
        vararg refs: ConstraintReference,
        weights: List<Float>? = null,
        style: ConstraintChainStyle = ConstraintChainStyle.Spread,
        bias: Float? = null,
    ) {
        validateChainWeights(weights, refs.size)
        validateChainReferences(refs, bias)
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
     * @throws IllegalArgumentException if references, weights, or bias are invalid
     */
    fun createVerticalChain(
        vararg refs: ConstraintReference,
        weights: List<Float>? = null,
        style: ConstraintChainStyle = ConstraintChainStyle.Spread,
        bias: Float? = null,
    ) {
        validateChainWeights(weights, refs.size)
        validateChainReferences(refs, bias)
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
 * @sample com.viewcompose.constraintlayout.samples.constraintSetSample
 * @param content reusable constraint and helper declarations
 * @return immutable constraint-set specification
 * @throws IllegalArgumentException if [content] declares a blank/duplicate identity or invalid
 * local constraint/chain combination
 */
fun constraintSet(content: ConstraintSetBuilder.() -> Unit): ConstraintSetSpec {
    return ConstraintSetBuilder()
        .apply(content)
        .build()
}
