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
import com.viewcompose.ui.node.spec.ConstraintCircularFlowItemSpec
import com.viewcompose.ui.node.spec.ConstraintCircularFlowSpec
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
import com.viewcompose.ui.node.spec.ConstraintGridOrientation
import com.viewcompose.ui.node.spec.ConstraintGridSkipSpec
import com.viewcompose.ui.node.spec.ConstraintGridSpanSpec
import com.viewcompose.ui.node.spec.ConstraintGridSpec
import com.viewcompose.ui.node.spec.ConstraintGroupSpec
import com.viewcompose.ui.node.spec.ConstraintHelperVisibility
import com.viewcompose.ui.node.spec.ConstraintHelpersSpec
import com.viewcompose.ui.node.spec.ConstraintItemSpec
import com.viewcompose.ui.node.spec.ConstraintLayerSpec
import com.viewcompose.ui.node.spec.ConstraintLayoutNodeProps
import com.viewcompose.ui.node.spec.ConstraintPlaceholderSpec
import com.viewcompose.ui.node.spec.ConstraintRatio
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.node.spec.ConstraintWrapBehavior
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

/** Target side accepted by a horizontal chain boundary. */
enum class ConstraintHorizontalAnchorSide {
    /** Logical start side, mirrored in RTL. */
    Start,

    /** Logical end side, mirrored in RTL. */
    End,

    /** Physical left side, fixed in RTL. */
    Left,

    /** Physical right side, fixed in RTL. */
    Right,
}

/** Target side accepted by a vertical chain boundary. */
enum class ConstraintVerticalAnchorSide {
    /** Physical top side. */
    Top,

    /** Physical bottom side. */
    Bottom,
}

/**
 * Places [reference] at an explicit Grid cell and optionally spans adjacent cells.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintGridSample
 * @property reference member owned by the Grid
 * @property index zero-based row-major cell index
 * @property rowSpan number of occupied rows
 * @property columnSpan number of occupied columns
 * @throws IllegalArgumentException if [index] is negative or either span is not positive
 */
data class ConstraintGridSpan(
    val reference: ConstraintReference,
    val index: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
) {
    init {
        require(index >= 0) { "ConstraintGridSpan.index must be non-negative." }
        require(rowSpan > 0) { "ConstraintGridSpan.rowSpan must be positive." }
        require(columnSpan > 0) { "ConstraintGridSpan.columnSpan must be positive." }
    }
}

/**
 * Reserves a rectangular Grid area that automatic placement cannot use.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintGridSample
 * @property index zero-based row-major cell index
 * @property rowSpan number of reserved rows
 * @property columnSpan number of reserved columns
 * @throws IllegalArgumentException if [index] is negative or either span is not positive
 */
data class ConstraintGridSkip(
    val index: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
) {
    init {
        require(index >= 0) { "ConstraintGridSkip.index must be non-negative." }
        require(rowSpan > 0) { "ConstraintGridSkip.rowSpan must be positive." }
        require(columnSpan > 0) { "ConstraintGridSkip.columnSpan must be positive." }
    }
}

/**
 * Declares one explicit member of a declarative CircularFlow.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintCircularFlowSample
 * @property reference child positioned around the shared center
 * @property radius finite non-negative center-to-center distance
 * @property angle finite clockwise AndroidX angle in `0f..<360f`
 * @throws IllegalArgumentException if radius or angle is outside its documented range
 */
data class ConstraintCircularFlowItem(
    val reference: ConstraintReference,
    val radius: UiDp,
    val angle: Float,
) {
    init {
        require(radius.value.isFinite() && radius.value >= 0f) {
            "ConstraintCircularFlowItem.radius must be finite and non-negative."
        }
        require(angle.isFinite() && angle >= 0f && angle < 360f) {
            "ConstraintCircularFlowItem.angle must be finite and within 0f..<360f."
        }
    }
}

/** Collects helper specs created by one ConstraintLayout DSL evaluation. */
private class MutableConstraintHelpersCollector {
    private var nextAutoId = 0
    private val helperKinds = mutableMapOf<String, String>()
    val guidelines = uniqueHelperList("Guideline", ConstraintGuidelineSpec::id)
    val barriers = uniqueHelperList("Barrier", ConstraintBarrierSpec::id)
    val chains = mutableListOf<ConstraintChainSpec>()
    val grids = uniqueHelperList("Grid", ConstraintGridSpec::id)
    val circularFlows = uniqueHelperList("CircularFlow", ConstraintCircularFlowSpec::id)
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
            grids = grids.toList(),
            circularFlows = circularFlows.toList(),
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

    internal fun addGrid(spec: ConstraintGridSpec) {
        ensureActive()
        helpers.grids += spec
    }

    internal fun addCircularFlow(spec: ConstraintCircularFlowSpec) {
        ensureActive()
        helpers.circularFlows += spec
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
    private var left: ConstraintAnchorLink? = null
    private var right: ConstraintAnchorLink? = null
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
    /**
     * Selects the axes on which this child expands a wrap-content [ConstraintLayout].
     *
     * The owning constraint block snapshots the assigned value when it completes. The default
     * [ConstraintWrapBehavior.Included] preserves normal two-axis participation; excluding an axis
     * does not remove the child from solving or placement.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintChainEndpointsAndWrapSample
     */
    var wrapBehaviorInParent: ConstraintWrapBehavior = ConstraintWrapBehavior.Included
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
     * Connects this child's physical left to [target]'s physical left.
     *
     * Physical links never mirror in RTL and cannot be combined with logical start/end links.
     * The active constraint block validates all margins before it publishes parent data.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
     * @param target parent, child, or horizontal helper target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     * @throws IllegalArgumentException if a margin is negative or non-finite, or if the completed
     * constraint block mixes physical and logical horizontal links
     */
    fun leftToLeft(
        target: ConstraintHorizontalAnchorTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        left = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Left),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's physical left to [target]'s physical right without RTL mirroring.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
     * @param target child or horizontal helper target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     * @throws IllegalArgumentException if a margin is negative or non-finite, or if the completed
     * constraint block mixes physical and logical horizontal links
     */
    fun leftToRight(
        target: ConstraintHorizontalAnchorTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        left = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Right),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's physical right to [target]'s physical left without RTL mirroring.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
     * @param target child or horizontal helper target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     * @throws IllegalArgumentException if a margin is negative or non-finite, or if the completed
     * constraint block mixes physical and logical horizontal links
     */
    fun rightToLeft(
        target: ConstraintHorizontalAnchorTarget,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        right = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Left),
            margin = margin,
            goneMargin = goneMargin,
        )
    }

    /**
     * Connects this child's physical right to [target]'s physical right.
     *
     * Physical links never mirror in RTL and cannot be combined with logical start/end links.
     * The active constraint block validates all margins before it publishes parent data.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
     * @param target parent, child, or horizontal helper target
     * @param margin normal spacing in dp
     * @param goneMargin spacing used when the target is gone, or `null` for the native default
     * @throws IllegalArgumentException if a margin is negative or non-finite, or if the completed
     * constraint block mixes physical and logical horizontal links
     */
    fun rightToRight(
        target: ConstraintHorizontalAnchorTarget = parent,
        margin: UiDp = UiDp.Zero,
        goneMargin: UiDp? = null,
    ) {
        right = ConstraintAnchorLink(
            target = anchorTarget(target.id, ConstraintAnchor.Right),
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
        listOfNotNull(start, end, left, right, top, bottom, baseline).forEach { link ->
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
        require((start == null && end == null) || (left == null && right == null)) {
            "Logical start/end links cannot be combined with physical left/right links."
        }
        val hasEdgeOrBaselineLink = listOf(start, end, left, right, top, bottom, baseline).any { it != null }
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
            left = left,
            right = right,
            top = top,
            bottom = bottom,
            baseline = baseline,
            width = width,
            height = height,
            horizontalBias = horizontalBias,
            verticalBias = verticalBias,
            ratio = ratio,
            circle = circle,
            wrapBehaviorInParent = wrapBehaviorInParent,
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
 * Creates a physical-left guideline at a fixed [offset] that never mirrors in RTL.
 *
 * The complete graph validates [offset] before native mutation and retains the previously accepted
 * layout when it is invalid.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
 * @receiver active ConstraintLayout content scope
 * @param offset finite non-negative distance from the physical left edge
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates another helper identity
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromLeft(
    offset: UiDp,
    id: String = allocHelperId("guideline-left"),
): ConstraintHorizontalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromLeft,
        position = ConstraintGuidelinePosition.Offset(offset),
    ))
    return HorizontalAnchorReference(id)
}

/**
 * Creates a physical-left guideline at parent-width [fraction] that never mirrors in RTL.
 *
 * The complete graph validates [fraction] before native mutation and retains the previously
 * accepted layout when it is invalid.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
 * @receiver active ConstraintLayout content scope
 * @param fraction finite parent-width fraction in `0f..1f`
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates another helper identity
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromLeft(
    fraction: Float,
    id: String = allocHelperId("guideline-left"),
): ConstraintHorizontalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromLeft,
        position = ConstraintGuidelinePosition.Fraction(fraction),
    ))
    return HorizontalAnchorReference(id)
}

/**
 * Creates a physical-right guideline at a fixed [offset] that never mirrors in RTL.
 *
 * The complete graph validates [offset] before native mutation and retains the previously accepted
 * layout when it is invalid.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
 * @receiver active ConstraintLayout content scope
 * @param offset finite non-negative distance from the physical right edge
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates another helper identity
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromRight(
    offset: UiDp,
    id: String = allocHelperId("guideline-right"),
): ConstraintHorizontalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromRight,
        position = ConstraintGuidelinePosition.Offset(offset),
    ))
    return HorizontalAnchorReference(id)
}

/**
 * Creates a physical-right guideline at parent-width [fraction] that never mirrors in RTL.
 *
 * The complete graph validates [fraction] before native mutation and retains the previously
 * accepted layout when it is invalid.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
 * @receiver active ConstraintLayout content scope
 * @param fraction finite parent-width fraction in `0f..1f`
 * @param id helper identity, auto-generated by declaration order when omitted
 * @return reference to the virtual guideline
 * @throws IllegalArgumentException if [id] is blank or duplicates another helper identity
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGuidelineFromRight(
    fraction: Float,
    id: String = allocHelperId("guideline-right"),
): ConstraintHorizontalAnchorReference {
    addGuideline(ConstraintGuidelineSpec(
        id = id,
        direction = ConstraintGuidelineDirection.FromRight,
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
 * Creates a physical-left barrier over [refs] that never mirrors in RTL.
 *
 * The complete graph validates references and [margin] before native mutation.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
 * @receiver active ConstraintLayout content scope
 * @param refs referenced children or anchor helpers; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin finite non-negative physical offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 * @throws IllegalArgumentException if [refs] is empty or [id] is blank or duplicated
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createLeftBarrier(
    vararg refs: ConstraintLayoutReference,
    id: String = allocHelperId("barrier-left"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintHorizontalAnchorReference {
    registerBarrier(id, ConstraintBarrierDirection.Left, refs, margin, allowsGoneWidgets)
    return HorizontalAnchorReference(id)
}

/**
 * Creates a physical-right barrier over [refs] that never mirrors in RTL.
 *
 * The complete graph validates references and [margin] before native mutation.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintPhysicalEdgesSample
 * @receiver active ConstraintLayout content scope
 * @param refs referenced children or anchor helpers; must not be empty
 * @param id helper identity, auto-generated by declaration order when omitted
 * @param margin finite non-negative physical offset from the computed extreme
 * @param allowsGoneWidgets whether gone referenced children participate
 * @return reference to the virtual barrier
 * @throws IllegalArgumentException if [refs] is empty or [id] is blank or duplicated
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createRightBarrier(
    vararg refs: ConstraintLayoutReference,
    id: String = allocHelperId("barrier-right"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintHorizontalAnchorReference {
    registerBarrier(id, ConstraintBarrierDirection.Right, refs, margin, allowsGoneWidgets)
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

private fun buildGridSpec(
    id: String,
    refs: Array<out ConstraintReference>,
    rows: Int,
    columns: Int,
    orientation: ConstraintGridOrientation,
    rowWeights: List<Float>,
    columnWeights: List<Float>,
    horizontalGap: UiDp,
    verticalGap: UiDp,
    spans: List<ConstraintGridSpan>,
    skips: List<ConstraintGridSkip>,
): ConstraintGridSpec {
    require(id.isNotBlank()) { "Grid helper ID must not be blank." }
    require(refs.isNotEmpty()) { "Grid requires at least one referenced child." }
    require(refs.map { it.id }.toSet().size == refs.size) {
        "Grid referenced children must be unique."
    }
    require(rows in 0..50 && columns in 0..50) {
        "Grid rows and columns must use 0 for auto or be within 1..50."
    }
    require(rowWeights.all { it.isFinite() && it > 0f } && columnWeights.all { it.isFinite() && it > 0f }) {
        "Grid weights must be finite and positive."
    }
    require(horizontalGap.value.isFinite() && horizontalGap.value >= 0f) {
        "Grid horizontalGap must be finite and non-negative."
    }
    require(verticalGap.value.isFinite() && verticalGap.value >= 0f) {
        "Grid verticalGap must be finite and non-negative."
    }
    validateGridTopology(
        refs = refs,
        rows = rows,
        columns = columns,
        orientation = orientation,
        rowWeights = rowWeights,
        columnWeights = columnWeights,
        spans = spans,
        skips = skips,
    )
    return ConstraintGridSpec(
        id = id,
        referencedIds = refs.map { it.id },
        rows = rows,
        columns = columns,
        orientation = orientation,
        rowWeights = rowWeights.toList(),
        columnWeights = columnWeights.toList(),
        horizontalGap = horizontalGap,
        verticalGap = verticalGap,
        spans = spans.map { span ->
            ConstraintGridSpanSpec(
                referenceId = span.reference.id,
                index = span.index,
                rowSpan = span.rowSpan,
                columnSpan = span.columnSpan,
            )
        },
        skips = skips.map { skip ->
            ConstraintGridSkipSpec(
                index = skip.index,
                rowSpan = skip.rowSpan,
                columnSpan = skip.columnSpan,
            )
        },
    )
}

private fun validateGridTopology(
    refs: Array<out ConstraintReference>,
    rows: Int,
    columns: Int,
    orientation: ConstraintGridOrientation,
    rowWeights: List<Float>,
    columnWeights: List<Float>,
    spans: List<ConstraintGridSpan>,
    skips: List<ConstraintGridSkip>,
) {
    val refIds = refs.map { it.id }.toSet()
    require(spans.map { it.reference.id }.toSet().size == spans.size) {
        "Grid spans must reference unique children."
    }
    require(spans.all { it.reference.id in refIds }) {
        "Every Grid span must reference a Grid member."
    }
    val rowCandidates = when {
        rows > 0 -> listOf(rows)
        rowWeights.isNotEmpty() -> listOf(rowWeights.size)
        else -> (1..50).toList()
    }
    val columnCandidates = when {
        columns > 0 -> listOf(columns)
        columnWeights.isNotEmpty() -> listOf(columnWeights.size)
        else -> (1..50).toList()
    }
    require(rowWeights.isEmpty() || rows == 0 || rowWeights.size == rows) {
        "Grid rowWeights size must match the resolved row count."
    }
    require(columnWeights.isEmpty() || columns == 0 || columnWeights.size == columns) {
        "Grid columnWeights size must match the resolved column count."
    }
    val fits = rowCandidates.any { resolvedRows ->
        columnCandidates.any { resolvedColumns ->
            gridPlacementFits(
                refs = refs,
                rows = resolvedRows,
                columns = resolvedColumns,
                orientation = orientation,
                spans = spans,
                skips = skips,
            )
        }
    }
    require(fits) {
        "Grid spans, skips, and members do not fit within the resolved 50x50 bounds."
    }
}

private fun gridPlacementFits(
    refs: Array<out ConstraintReference>,
    rows: Int,
    columns: Int,
    orientation: ConstraintGridOrientation,
    spans: List<ConstraintGridSpan>,
    skips: List<ConstraintGridSkip>,
): Boolean {
    val occupied = BooleanArray(rows * columns)
    fun occupy(index: Int, rowSpan: Int, columnSpan: Int): Boolean {
        if (index !in occupied.indices) return false
        val startRow = index / columns
        val startColumn = index % columns
        if (startRow + rowSpan > rows || startColumn + columnSpan > columns) return false
        for (row in startRow until startRow + rowSpan) {
            for (column in startColumn until startColumn + columnSpan) {
                val cell = row * columns + column
                if (occupied[cell]) return false
            }
        }
        for (row in startRow until startRow + rowSpan) {
            for (column in startColumn until startColumn + columnSpan) {
                occupied[row * columns + column] = true
            }
        }
        return true
    }
    if (skips.any { !occupy(it.index, it.rowSpan, it.columnSpan) }) return false
    if (spans.any { !occupy(it.index, it.rowSpan, it.columnSpan) }) return false
    val explicitIds = spans.mapTo(mutableSetOf()) { it.reference.id }
    val cellOrder = when (orientation) {
        ConstraintGridOrientation.Horizontal -> occupied.indices.toList()
        ConstraintGridOrientation.Vertical -> buildList {
            for (column in 0 until columns) {
                for (row in 0 until rows) add(row * columns + column)
            }
        }
    }
    return refs.asSequence()
        .filterNot { it.id in explicitIds }
        .all { cellOrder.firstOrNull { cell -> !occupied[cell] }?.let { cell -> occupied[cell] = true } != null }
}

/**
 * Creates a typed Grid whose members are expanded transactionally by the renderer.
 *
 * Auto axes use `0`; fixed axes use `1..50`. If both axes are automatic, the smallest fitting
 * bounded rectangle is selected by shortest maximum axis, then fewest cells, then closest axes,
 * with [orientation] as the final tie-breaker.
 * Empty weight lists mean equal weights. Grid owns member positioning on both axes, while each
 * child's dimension remains declared normally. It creates bounded renderer-owned row and column
 * anchors but no addressable semantic helper View. Reference existence and competing helper
 * ownership are validated during complete-graph preflight; rejection preserves the accepted graph.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintGridSample
 * @receiver active ConstraintLayout content scope
 * @param refs unique children positioned by the Grid; must not be empty
 * @param id unique Grid identity, auto-generated by declaration order when omitted
 * @param rows explicit row count in `1..50`, or `0` for automatic resolution
 * @param columns explicit column count in `1..50`, or `0` for automatic resolution
 * @param orientation order used to place members without an explicit [ConstraintGridSpan]
 * @param rowWeights positive finite weights matching the resolved row count, or empty for equal rows
 * @param columnWeights positive finite weights matching the resolved column count, or empty for equal columns
 * @param horizontalGap finite non-negative distance between adjacent columns
 * @param verticalGap finite non-negative distance between adjacent rows
 * @param spans explicit unique member placements using zero-based row-major indexes
 * @param skips reserved zero-based row-major rectangles unavailable to automatic placement
 * @return identity-only reference for helper ownership and collision checks; it is not an anchor
 * @throws IllegalArgumentException if local identity, dimensions, weights, gaps, spans, skips, or
 * bounded placement capacity are invalid
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createGrid(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("grid"),
    rows: Int = 0,
    columns: Int = 0,
    orientation: ConstraintGridOrientation = ConstraintGridOrientation.Horizontal,
    rowWeights: List<Float> = emptyList(),
    columnWeights: List<Float> = emptyList(),
    horizontalGap: UiDp = UiDp.Zero,
    verticalGap: UiDp = UiDp.Zero,
    spans: List<ConstraintGridSpan> = emptyList(),
    skips: List<ConstraintGridSkip> = emptyList(),
): ConstraintHelperReference {
    addGrid(buildGridSpec(
        id,
        refs,
        rows,
        columns,
        orientation,
        rowWeights,
        columnWeights,
        horizontalGap,
        verticalGap,
        spans,
        skips,
    ))
    return HelperReference(id)
}

/**
 * Creates a declarative CircularFlow compiled to ordinary per-child circle constraints.
 *
 * The declaration creates no helper View. Every item owns its circle positioning atomically and
 * therefore cannot also participate in edge, baseline, chain, Grid, or direct circle positioning.
 * Angles follow AndroidX coordinates: `0f` is above the center and values advance clockwise.
 * Reference existence and competing ownership are validated during complete-graph preflight;
 * rejection preserves the accepted graph.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintCircularFlowSample
 * @receiver active ConstraintLayout content scope
 * @param center existing child used as the center for every item
 * @param items non-empty unique members with explicit non-negative radii and angles in `0f..<360f`
 * @param id unique semantic group identity, auto-generated by declaration order when omitted
 * @return identity-only reference for helper ownership and collision checks; it is not an anchor
 * @throws IllegalArgumentException if local identity, members, radius, or angle values are invalid
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createCircularFlow(
    center: ConstraintReference,
    vararg items: ConstraintCircularFlowItem,
    id: String = allocHelperId("circular-flow"),
): ConstraintHelperReference {
    addCircularFlow(buildCircularFlowSpec(id, center, items))
    return HelperReference(id)
}

private fun buildCircularFlowSpec(
    id: String,
    center: ConstraintReference,
    items: Array<out ConstraintCircularFlowItem>,
): ConstraintCircularFlowSpec {
    require(id.isNotBlank()) { "CircularFlow helper ID must not be blank." }
    require(items.isNotEmpty()) { "CircularFlow requires at least one item." }
    require(items.map { it.reference.id }.toSet().size == items.size) {
        "CircularFlow items must reference unique children."
    }
    require(items.none { it.reference.id == center.id }) {
        "CircularFlow center cannot also be a positioned item."
    }
    return ConstraintCircularFlowSpec(
        id = id,
        centerId = center.id,
        items = items.map { item ->
            ConstraintCircularFlowItemSpec(
                referenceId = item.reference.id,
                radius = item.radius,
                angle = item.angle,
            )
        },
    )
}

private fun ConstraintLayoutScope.registerChain(
    orientation: ConstraintChainOrientation,
    refs: Array<out ConstraintReference>,
    weights: List<Float>?,
    style: ConstraintChainStyle,
    bias: Float?,
    startTarget: ConstraintAnchorTarget,
    endTarget: ConstraintAnchorTarget,
    startMargin: UiDp,
    endMargin: UiDp,
) {
    validateChainWeights(weights, refs.size)
    validateChainReferences(refs, bias)
    listOf(startMargin, endMargin).forEach { margin ->
        require(margin.value.isFinite() && margin.value >= 0f) {
            "Constraint chain boundary margins must be finite and non-negative."
        }
    }
    addChain(ConstraintChainSpec(
        orientation = orientation,
        referencedIds = refs.map { ref -> ref.id },
        weights = weights,
        style = style,
        bias = bias,
        startTarget = startTarget,
        endTarget = endTarget,
        startMargin = startMargin,
        endMargin = endMargin,
    ))
}

private fun horizontalChainTarget(
    target: ConstraintHorizontalAnchorTarget,
    side: ConstraintHorizontalAnchorSide,
): ConstraintAnchorTarget = anchorTarget(
    id = target.id,
    anchor = when (side) {
        ConstraintHorizontalAnchorSide.Start -> ConstraintAnchor.Start
        ConstraintHorizontalAnchorSide.End -> ConstraintAnchor.End
        ConstraintHorizontalAnchorSide.Left -> ConstraintAnchor.Left
        ConstraintHorizontalAnchorSide.Right -> ConstraintAnchor.Right
    },
)

private fun verticalChainTarget(
    target: ConstraintVerticalAnchorTarget,
    side: ConstraintVerticalAnchorSide,
): ConstraintAnchorTarget = anchorTarget(
    id = target.id,
    anchor = when (side) {
        ConstraintVerticalAnchorSide.Top -> ConstraintAnchor.Top
        ConstraintVerticalAnchorSide.Bottom -> ConstraintAnchor.Bottom
    },
)

private fun validateHorizontalChainPlane(
    startSide: ConstraintHorizontalAnchorSide,
    endSide: ConstraintHorizontalAnchorSide,
) {
    val startLogical = startSide == ConstraintHorizontalAnchorSide.Start ||
        startSide == ConstraintHorizontalAnchorSide.End
    val endLogical = endSide == ConstraintHorizontalAnchorSide.Start ||
        endSide == ConstraintHorizontalAnchorSide.End
    require(startLogical == endLogical) {
        "Horizontal chain endpoints cannot mix logical start/end with physical left/right sides."
    }
}

/**
 * Adds an ordered horizontal chain between explicit logical or physical boundaries.
 *
 * Logical boundaries mirror in RTL; physical boundaries do not. Both boundary sides must use the
 * same plane. The chain owns horizontal positioning for every member and is validated before the
 * complete graph replaces the previous native layout. Endpoint existence and competing helper
 * ownership are complete-graph checks whose rejection preserves the accepted graph.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintChainEndpointsAndWrapSample
 * @receiver active ConstraintLayout content scope
 * @param refs ordered chain members; at least two unique references are required
 * @param weights optional positive member weights whose size must equal [refs]
 * @param style chain distribution policy
 * @param bias optional finite packed-chain bias in `0f..1f`
 * @param startTarget parent, child, guideline, or barrier used by the first boundary
 * @param startTargetSide target side used by the first boundary
 * @param startMargin finite non-negative spacing at the first boundary
 * @param endTarget parent, child, guideline, or barrier used by the last boundary
 * @param endTargetSide target side used by the last boundary
 * @param endMargin finite non-negative spacing at the last boundary
 * @throws IllegalArgumentException if local references, weights, bias, endpoint planes, or margins
 * are invalid
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createHorizontalChain(
    vararg refs: ConstraintReference,
    weights: List<Float>? = null,
    style: ConstraintChainStyle = ConstraintChainStyle.Spread,
    bias: Float? = null,
    startTarget: ConstraintHorizontalAnchorTarget = parent,
    startTargetSide: ConstraintHorizontalAnchorSide = ConstraintHorizontalAnchorSide.Start,
    startMargin: UiDp = UiDp.Zero,
    endTarget: ConstraintHorizontalAnchorTarget = parent,
    endTargetSide: ConstraintHorizontalAnchorSide = ConstraintHorizontalAnchorSide.End,
    endMargin: UiDp = UiDp.Zero,
) {
    validateHorizontalChainPlane(startTargetSide, endTargetSide)
    registerChain(
        orientation = ConstraintChainOrientation.Horizontal,
        refs = refs,
        weights = weights,
        style = style,
        bias = bias,
        startTarget = horizontalChainTarget(startTarget, startTargetSide),
        endTarget = horizontalChainTarget(endTarget, endTargetSide),
        startMargin = startMargin,
        endMargin = endMargin,
    )
}

/**
 * Adds an ordered vertical chain between explicit top or bottom boundaries.
 *
 * The chain owns vertical positioning for every member and is validated before the complete graph
 * replaces the previous native layout. Endpoint existence and competing helper ownership are
 * complete-graph checks whose rejection preserves the accepted graph.
 *
 * @sample com.viewcompose.constraintlayout.samples.constraintChainEndpointsAndWrapSample
 * @receiver active ConstraintLayout content scope
 * @param refs ordered chain members; at least two unique references are required
 * @param weights optional positive member weights whose size must equal [refs]
 * @param style chain distribution policy
 * @param bias optional finite packed-chain bias in `0f..1f`
 * @param topTarget parent, child, or vertical guideline used by the first boundary
 * @param topTargetSide target side used by the first boundary
 * @param topMargin finite non-negative spacing at the first boundary
 * @param bottomTarget parent, child, or vertical guideline used by the last boundary
 * @param bottomTargetSide target side used by the last boundary
 * @param bottomMargin finite non-negative spacing at the last boundary
 * @throws IllegalArgumentException if local references, weights, bias, or margins are invalid
 * @throws IllegalStateException if a retained scope is used after content evaluation completes
 */
fun ConstraintLayoutScope.createVerticalChain(
    vararg refs: ConstraintReference,
    weights: List<Float>? = null,
    style: ConstraintChainStyle = ConstraintChainStyle.Spread,
    bias: Float? = null,
    topTarget: ConstraintVerticalAnchorTarget = parent,
    topTargetSide: ConstraintVerticalAnchorSide = ConstraintVerticalAnchorSide.Top,
    topMargin: UiDp = UiDp.Zero,
    bottomTarget: ConstraintVerticalAnchorTarget = parent,
    bottomTargetSide: ConstraintVerticalAnchorSide = ConstraintVerticalAnchorSide.Bottom,
    bottomMargin: UiDp = UiDp.Zero,
) {
    registerChain(
        orientation = ConstraintChainOrientation.Vertical,
        refs = refs,
        weights = weights,
        style = style,
        bias = bias,
        startTarget = verticalChainTarget(topTarget, topTargetSide),
        endTarget = verticalChainTarget(bottomTarget, bottomTargetSide),
        startMargin = topMargin,
        endMargin = bottomMargin,
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
     * Creates a reusable physical-left guideline at fixed [offset] without RTL mirroring.
     *
     * The complete graph validates [offset] when this set is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param offset finite non-negative distance from the physical left edge
     * @param id helper identity, auto-generated by declaration order when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates another helper identity
     */
    fun createGuidelineFromLeft(
        offset: UiDp,
        id: String = helpers.allocId("guideline-left"),
    ): ConstraintHorizontalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromLeft,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a reusable physical-left guideline at parent-width [fraction] without RTL mirroring.
     *
     * The complete graph validates [fraction] when this set is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param fraction finite parent-width fraction in `0f..1f`
     * @param id helper identity, auto-generated by declaration order when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates another helper identity
     */
    fun createGuidelineFromLeft(
        fraction: Float,
        id: String = helpers.allocId("guideline-left"),
    ): ConstraintHorizontalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromLeft,
            position = ConstraintGuidelinePosition.Fraction(fraction),
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a reusable physical-right guideline at fixed [offset] without RTL mirroring.
     *
     * The complete graph validates [offset] when this set is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param offset finite non-negative distance from the physical right edge
     * @param id helper identity, auto-generated by declaration order when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates another helper identity
     */
    fun createGuidelineFromRight(
        offset: UiDp,
        id: String = helpers.allocId("guideline-right"),
    ): ConstraintHorizontalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromRight,
            position = ConstraintGuidelinePosition.Offset(offset),
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a reusable physical-right guideline at parent-width [fraction] without RTL mirroring.
     *
     * The complete graph validates [fraction] when this set is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param fraction finite parent-width fraction in `0f..1f`
     * @param id helper identity, auto-generated by declaration order when omitted
     * @return reference to the virtual guideline
     * @throws IllegalArgumentException if [id] is blank or duplicates another helper identity
     */
    fun createGuidelineFromRight(
        fraction: Float,
        id: String = helpers.allocId("guideline-right"),
    ): ConstraintHorizontalAnchorReference {
        helpers.guidelines += ConstraintGuidelineSpec(
            id = id,
            direction = ConstraintGuidelineDirection.FromRight,
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
     * Creates a reusable physical-left barrier that never mirrors in RTL.
     *
     * The complete graph validates references and [margin] when this set is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param refs referenced children or anchor helpers; must not be empty
     * @param id helper identity, auto-generated by declaration order when omitted
     * @param margin finite non-negative physical offset from the computed extreme
     * @param allowsGoneWidgets whether gone referenced children participate
     * @return reference to the virtual barrier
     * @throws IllegalArgumentException if [refs] is empty or [id] is blank or duplicated
     */
    fun createLeftBarrier(
        vararg refs: ConstraintLayoutReference,
        id: String = helpers.allocId("barrier-left"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintHorizontalAnchorReference {
        require(refs.isNotEmpty()) { "Barrier helper requires at least one referenced id." }
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.Left,
            referencedIds = refs.map { ref -> ref.id },
            margin = margin,
            allowsGoneWidgets = allowsGoneWidgets,
        )
        return HorizontalAnchorReference(id)
    }

    /**
     * Creates a reusable physical-right barrier that never mirrors in RTL.
     *
     * The complete graph validates references and [margin] when this set is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param refs referenced children or anchor helpers; must not be empty
     * @param id helper identity, auto-generated by declaration order when omitted
     * @param margin finite non-negative physical offset from the computed extreme
     * @param allowsGoneWidgets whether gone referenced children participate
     * @return reference to the virtual barrier
     * @throws IllegalArgumentException if [refs] is empty or [id] is blank or duplicated
     */
    fun createRightBarrier(
        vararg refs: ConstraintLayoutReference,
        id: String = helpers.allocId("barrier-right"),
        margin: UiDp = UiDp.Zero,
        allowsGoneWidgets: Boolean = true,
    ): ConstraintHorizontalAnchorReference {
        require(refs.isNotEmpty()) { "Barrier helper requires at least one referenced id." }
        helpers.barriers += ConstraintBarrierSpec(
            id = id,
            direction = ConstraintBarrierDirection.Right,
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
     * Adds a typed Grid to this reusable constraint set.
     *
     * Axis inference, ownership, native expansion, and transactional failure behavior match the
     * inline [ConstraintLayoutScope.createGrid] contract. Reference existence and competing helper
     * ownership are complete-graph checks performed when the set is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param refs unique children positioned by the Grid; must not be empty
     * @param id unique Grid identity, auto-generated by declaration order when omitted
     * @param rows explicit row count in `1..50`, or `0` for automatic resolution
     * @param columns explicit column count in `1..50`, or `0` for automatic resolution
     * @param orientation automatic placement order
     * @param rowWeights positive finite weights matching the resolved row count, or empty for equal rows
     * @param columnWeights positive finite weights matching the resolved column count, or empty for equal columns
     * @param horizontalGap finite non-negative distance between adjacent columns
     * @param verticalGap finite non-negative distance between adjacent rows
     * @param spans explicit unique member placements using zero-based row-major indexes
     * @param skips reserved zero-based row-major rectangles unavailable to automatic placement
     * @return identity-only reference for helper ownership and collision checks; it is not an anchor
     * @throws IllegalArgumentException if local identity, dimensions, weights, gaps, spans, skips,
     * or bounded placement capacity are invalid
     */
    fun createGrid(
        vararg refs: ConstraintReference,
        id: String = helpers.allocId("grid"),
        rows: Int = 0,
        columns: Int = 0,
        orientation: ConstraintGridOrientation = ConstraintGridOrientation.Horizontal,
        rowWeights: List<Float> = emptyList(),
        columnWeights: List<Float> = emptyList(),
        horizontalGap: UiDp = UiDp.Zero,
        verticalGap: UiDp = UiDp.Zero,
        spans: List<ConstraintGridSpan> = emptyList(),
        skips: List<ConstraintGridSkip> = emptyList(),
    ): ConstraintHelperReference {
        helpers.grids += buildGridSpec(
            id,
            refs,
            rows,
            columns,
            orientation,
            rowWeights,
            columnWeights,
            horizontalGap,
            verticalGap,
            spans,
            skips,
        )
        return HelperReference(id)
    }

    /**
     * Adds a no-View CircularFlow to this reusable constraint set.
     *
     * Item coordinates, ownership, and transactional failure behavior match the inline
     * [ConstraintLayoutScope.createCircularFlow] contract. Reference existence and competing
     * ownership are complete-graph checks performed when the set is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param center existing child used as the center for every item
     * @param items non-empty unique members with explicit non-negative radii and angles in `0f..<360f`
     * @param id unique semantic group identity, auto-generated by declaration order when omitted
     * @return identity-only reference for helper ownership and collision checks; it is not an anchor
     * @throws IllegalArgumentException if local identity, members, radius, or angle values are invalid
     */
    fun createCircularFlow(
        center: ConstraintReference,
        vararg items: ConstraintCircularFlowItem,
        id: String = helpers.allocId("circular-flow"),
    ): ConstraintHelperReference {
        helpers.circularFlows += buildCircularFlowSpec(id, center, items)
        return HelperReference(id)
    }

    /**
     * Adds an ordered reusable horizontal chain between logical or physical boundaries.
     *
     * Logical boundaries mirror in RTL; physical boundaries do not. Both sides must use the same
     * coordinate plane. Endpoint existence and competing ownership are complete-graph checks
     * performed when the set is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param refs ordered members; at least two unique references are required
     * @param weights optional positive weights whose size must equal [refs]
     * @param style chain distribution policy
     * @param bias optional finite packed-chain bias in `0f..1f`
     * @param startTarget parent, child, guideline, or barrier used by the first boundary
     * @param startTargetSide target side used by the first boundary
     * @param startMargin finite non-negative first-boundary spacing
     * @param endTarget parent, child, guideline, or barrier used by the last boundary
     * @param endTargetSide target side used by the last boundary
     * @param endMargin finite non-negative last-boundary spacing
     * @throws IllegalArgumentException if local references, weights, bias, endpoint planes, or
     * margins are invalid
     */
    fun createHorizontalChain(
        vararg refs: ConstraintReference,
        weights: List<Float>? = null,
        style: ConstraintChainStyle = ConstraintChainStyle.Spread,
        bias: Float? = null,
        startTarget: ConstraintHorizontalAnchorTarget = parent,
        startTargetSide: ConstraintHorizontalAnchorSide = ConstraintHorizontalAnchorSide.Start,
        startMargin: UiDp = UiDp.Zero,
        endTarget: ConstraintHorizontalAnchorTarget = parent,
        endTargetSide: ConstraintHorizontalAnchorSide = ConstraintHorizontalAnchorSide.End,
        endMargin: UiDp = UiDp.Zero,
    ) {
        validateChainWeights(weights, refs.size)
        validateChainReferences(refs, bias)
        validateHorizontalChainPlane(startTargetSide, endTargetSide)
        listOf(startMargin, endMargin).forEach { margin ->
            require(margin.value.isFinite() && margin.value >= 0f) {
                "Constraint chain boundary margins must be finite and non-negative."
            }
        }
        helpers.chains += ConstraintChainSpec(
            orientation = ConstraintChainOrientation.Horizontal,
            referencedIds = refs.map { ref -> ref.id },
            weights = weights,
            style = style,
            bias = bias,
            startTarget = horizontalChainTarget(startTarget, startTargetSide),
            endTarget = horizontalChainTarget(endTarget, endTargetSide),
            startMargin = startMargin,
            endMargin = endMargin,
        )
    }

    /**
     * Adds an ordered reusable vertical chain between top or bottom boundaries.
     *
     * Endpoint existence and competing ownership are complete-graph checks performed when the set
     * is applied.
     *
     * @sample com.viewcompose.constraintlayout.samples.constraintSetPhaseTwoSample
     * @param refs ordered members; at least two unique references are required
     * @param weights optional positive weights whose size must equal [refs]
     * @param style chain distribution policy
     * @param bias optional finite packed-chain bias in `0f..1f`
     * @param topTarget parent, child, or vertical guideline used by the first boundary
     * @param topTargetSide target side used by the first boundary
     * @param topMargin finite non-negative first-boundary spacing
     * @param bottomTarget parent, child, or vertical guideline used by the last boundary
     * @param bottomTargetSide target side used by the last boundary
     * @param bottomMargin finite non-negative last-boundary spacing
     * @throws IllegalArgumentException if local references, weights, bias, or margins are invalid
     */
    fun createVerticalChain(
        vararg refs: ConstraintReference,
        weights: List<Float>? = null,
        style: ConstraintChainStyle = ConstraintChainStyle.Spread,
        bias: Float? = null,
        topTarget: ConstraintVerticalAnchorTarget = parent,
        topTargetSide: ConstraintVerticalAnchorSide = ConstraintVerticalAnchorSide.Top,
        topMargin: UiDp = UiDp.Zero,
        bottomTarget: ConstraintVerticalAnchorTarget = parent,
        bottomTargetSide: ConstraintVerticalAnchorSide = ConstraintVerticalAnchorSide.Bottom,
        bottomMargin: UiDp = UiDp.Zero,
    ) {
        validateChainWeights(weights, refs.size)
        validateChainReferences(refs, bias)
        listOf(topMargin, bottomMargin).forEach { margin ->
            require(margin.value.isFinite() && margin.value >= 0f) {
                "Constraint chain boundary margins must be finite and non-negative."
            }
        }
        helpers.chains += ConstraintChainSpec(
            orientation = ConstraintChainOrientation.Vertical,
            referencedIds = refs.map { ref -> ref.id },
            weights = weights,
            style = style,
            bias = bias,
            startTarget = verticalChainTarget(topTarget, topTargetSide),
            endTarget = verticalChainTarget(bottomTarget, bottomTargetSide),
            startMargin = topMargin,
            endMargin = bottomMargin,
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
