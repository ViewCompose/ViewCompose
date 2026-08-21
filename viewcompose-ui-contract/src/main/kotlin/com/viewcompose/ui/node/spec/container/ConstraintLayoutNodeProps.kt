package com.viewcompose.ui.node.spec

import com.viewcompose.ui.unit.UiDp

/**
 * Immutable renderer properties for a constraint-layout container.
 *
 * @property constraintSet optional child constraints and set-local helpers
 * @property helpers container-level virtual helper declarations
 */
data class ConstraintLayoutNodeProps(
    val constraintSet: ConstraintSetSpec? = null,
    val helpers: ConstraintHelpersSpec = ConstraintHelpersSpec(),
) : NodeSpec

/**
 * Names child constraints and helper declarations for one constraint-layout render.
 *
 * Constraint keys and helper IDs share the renderer's ID namespace. This transport remains
 * Android-free and does not validate relationships that require mounted content. A compatible
 * renderer validates the complete merged graph before native mutation and rejects the whole
 * candidate when an ID, reference, anchor plane, ownership rule, or range is invalid.
 *
 * @property constraints constraints keyed by the semantic child reference used by the DSL
 * @property helpers helper declarations scoped to this set
 */
data class ConstraintSetSpec(
    val constraints: Map<String, ConstraintItemSpec> = emptyMap(),
    val helpers: ConstraintHelpersSpec = ConstraintHelpersSpec(),
)

/**
 * Groups all virtual ConstraintLayout helper declarations.
 *
 * @property guidelines virtual guideline definitions
 * @property barriers virtual barrier definitions
 * @property chains horizontal and vertical chain definitions
 * @property grids typed grid declarations expanded by the renderer without AndroidX Grid
 * @property circularFlows declarative groups compiled to per-child circular constraints
 * @property flows virtual flow helper definitions
 * @property groups visibility and elevation group definitions
 * @property layers transform group definitions
 * @property placeholders content placeholder definitions
 */
data class ConstraintHelpersSpec(
    val guidelines: List<ConstraintGuidelineSpec> = emptyList(),
    val barriers: List<ConstraintBarrierSpec> = emptyList(),
    val chains: List<ConstraintChainSpec> = emptyList(),
    val grids: List<ConstraintGridSpec> = emptyList(),
    val circularFlows: List<ConstraintCircularFlowSpec> = emptyList(),
    val flows: List<ConstraintFlowSpec> = emptyList(),
    val groups: List<ConstraintGroupSpec> = emptyList(),
    val layers: List<ConstraintLayerSpec> = emptyList(),
    val placeholders: List<ConstraintPlaceholderSpec> = emptyList(),
)

/**
 * Defines anchors, dimensions, bias, and optional circular positioning for one child.
 *
 * A `null` anchor leaves that edge unconstrained. The renderer validates the complete graph before
 * mutating native Views; an invalid transport value rejects the candidate graph as one unit.
 *
 * @property start logical start-edge link
 * @property end logical end-edge link
 * @property left physical left-edge link
 * @property right physical right-edge link
 * @property top top-edge link
 * @property bottom bottom-edge link
 * @property baseline baseline-to-baseline, baseline-to-top, or baseline-to-bottom link
 * @property width primary horizontal dimension behavior
 * @property height primary vertical dimension behavior
 * @property horizontalBias optional placement bias between horizontal anchors
 * @property verticalBias optional placement bias between vertical anchors
 * @property ratio optional typed width-to-height ratio
 * @property circle optional circular positioning, mutually exclusive with edge and baseline links
 * @property wrapBehaviorInParent axes on which this item contributes to a wrap-content parent
 */
data class ConstraintItemSpec(
    val start: ConstraintAnchorLink? = null,
    val end: ConstraintAnchorLink? = null,
    val left: ConstraintAnchorLink? = null,
    val right: ConstraintAnchorLink? = null,
    val top: ConstraintAnchorLink? = null,
    val bottom: ConstraintAnchorLink? = null,
    val baseline: ConstraintAnchorLink? = null,
    val width: ConstraintDimension = ConstraintDimension.WrapContent,
    val height: ConstraintDimension = ConstraintDimension.WrapContent,
    val horizontalBias: Float? = null,
    val verticalBias: Float? = null,
    val ratio: ConstraintRatio? = null,
    val circle: ConstraintCircleSpec? = null,
    val wrapBehaviorInParent: ConstraintWrapBehavior = ConstraintWrapBehavior.Included,
)

/**
 * Connects one child anchor to a [target] with normal and gone margins.
 *
 * @property target target ID and anchor
 * @property margin normal spacing between connected anchors
 * @property goneMargin spacing used when the target is gone, or `null` for renderer default
 */
data class ConstraintAnchorLink(
    val target: ConstraintAnchorTarget,
    val margin: UiDp = UiDp.Zero,
    val goneMargin: UiDp? = null,
)

/**
 * Identifies an anchor on the parent or another referenced child/helper.
 *
 * @property id referenced ID, or `null` for the constraint-layout parent
 * @property anchor target anchor
 */
data class ConstraintAnchorTarget(
    val id: String?,
    val anchor: ConstraintAnchor,
) {
    /** Creates parent and referenced-child targets. */
    companion object {
        /** Creates a target for [anchor] on the constraint-layout parent. */
        fun parent(anchor: ConstraintAnchor): ConstraintAnchorTarget = ConstraintAnchorTarget(
            id = null,
            anchor = anchor,
        )

        /**
         * Creates a target for [anchor] on [id].
         *
         * @param id child or helper reference in the current constraint namespace
         */
        fun ref(
            id: String,
            anchor: ConstraintAnchor,
        ): ConstraintAnchorTarget = ConstraintAnchorTarget(
            id = id,
            anchor = anchor,
        )
    }
}

/**
 * Selects a target edge in the owning constraint graph.
 *
 * [Start] and [End] are logical and mirror with layout direction. [Left] and [Right] are physical
 * and remain fixed. [Top], [Bottom], and [Baseline] are physical vertical anchors.
 */
enum class ConstraintAnchor {
    Start,
    End,
    Left,
    Right,
    Top,
    Bottom,
    Baseline,
}

/**
 * Selects the axes on which a child contributes to a wrap-content ConstraintLayout parent.
 *
 * The default [Included] behavior preserves the released layout contract. Axis-specific values
 * keep the child in the solver while excluding it from the opposite wrap-content calculation.
 */
enum class ConstraintWrapBehavior {
    /** Contributes to both horizontal and vertical parent wrap-content measurement. */
    Included,

    /** Contributes only to horizontal parent wrap-content measurement. */
    HorizontalOnly,

    /** Contributes only to vertical parent wrap-content measurement. */
    VerticalOnly,

    /** Does not contribute to either parent wrap-content measurement axis. */
    Skipped,
}

/**
 * Positions a child around another referenced node.
 *
 * @property targetId center target ID
 * @property radius finite non-negative center-to-center distance
 * @property angle finite clockwise angle in `0f..<360f`; `0f` is above the center
 */
data class ConstraintCircleSpec(
    val targetId: String,
    val radius: UiDp,
    val angle: Float,
)

/**
 * Selects one mutually exclusive dimension contract for a constrained child.
 *
 * Consumers should handle this sealed hierarchy exhaustively. All logical sizes must be finite and
 * non-negative. Match constraints own their mode and bounds together so contradictory percent,
 * min/max, and constrained-wrap states cannot be represented.
 *
 * @sample com.viewcompose.ui.samples.constraintDimensionsSample
 */
sealed interface ConstraintDimension {
    /** Measures the child's desired content size. */
    data object WrapContent : ConstraintDimension

    /** Measures desired content while allowing opposing constraints to reduce the result. */
    data object ConstrainedWrapContent : ConstraintDimension

    /**
     * Uses the space established by opposing constraints under one explicit [mode].
     *
     * [min] and [max] apply after the selected match-constraint mode. Both are optional; when both
     * are present, [min] must not exceed [max].
     *
     * @property mode spread, wrap, or parent-percent resolution
     * @property min optional finite non-negative lower bound
     * @property max optional finite non-negative upper bound
     * @throws IllegalArgumentException if a bound is non-finite, negative, or ordered incorrectly
     */
    data class MatchConstraints(
        val mode: ConstraintMatchMode = ConstraintMatchMode.Spread,
        val min: UiDp? = null,
        val max: UiDp? = null,
    ) : ConstraintDimension {
        init {
            min?.requireConstraintDimension("ConstraintDimension.MatchConstraints.min")
            max?.requireConstraintDimension("ConstraintDimension.MatchConstraints.max")
            require(min == null || max == null || min <= max) {
                "ConstraintDimension.MatchConstraints.min must not exceed max."
            }
        }
    }

    /**
     * Fixed logical dimension.
     *
     * @property value requested width or height
     * @throws IllegalArgumentException if [value] is non-finite or negative
     */
    data class Fixed(
        val value: UiDp,
    ) : ConstraintDimension {
        init {
            value.requireConstraintDimension("ConstraintDimension.Fixed.value")
        }
    }
}

/**
 * Selects how a [ConstraintDimension.MatchConstraints] value uses its available axis.
 *
 * @sample com.viewcompose.ui.samples.constraintDimensionsSample
 */
sealed interface ConstraintMatchMode {
    /** Expands between opposing anchors subject to optional min/max bounds. */
    data object Spread : ConstraintMatchMode

    /** Uses desired content as the match-constraint target subject to anchors and optional bounds. */
    data object Wrap : ConstraintMatchMode

    /**
     * Uses a fraction of the ConstraintLayout parent's corresponding dimension.
     *
     * @property fraction finite inclusive fraction from `0f` through `1f`
     * @throws IllegalArgumentException if [fraction] is non-finite or outside `0f..1f`
     */
    data class Percent(
        val fraction: Float,
    ) : ConstraintMatchMode {
        init {
            require(fraction.isFinite() && fraction in 0f..1f) {
                "ConstraintMatchMode.Percent.fraction must be finite and within 0f..1f."
            }
        }
    }
}

/** Selects which axis AndroidX constrains when resolving a [ConstraintRatio]. */
enum class ConstraintRatioSide {
    /** Derives width from the resolved height. */
    Width,

    /** Derives height from the resolved width. */
    Height,
}

/**
 * Defines a positive width-to-height ratio without exposing AndroidX's raw string grammar.
 *
 * At least one item dimension must use [ConstraintDimension.MatchConstraints]. The renderer
 * validates that relationship with the complete item before native mutation.
 *
 * @sample com.viewcompose.ui.samples.constraintDimensionsSample
 * @property width positive finite width term
 * @property height positive finite height term
 * @property constrainedSide optional axis AndroidX derives from the other resolved axis
 * @throws IllegalArgumentException if either ratio term is non-finite or not greater than zero
 */
data class ConstraintRatio(
    val width: Float,
    val height: Float,
    val constrainedSide: ConstraintRatioSide? = null,
) {
    init {
        require(width.isFinite() && width > 0f) {
            "ConstraintRatio.width must be finite and greater than zero."
        }
        require(height.isFinite() && height > 0f) {
            "ConstraintRatio.height must be finite and greater than zero."
        }
    }
}

private fun UiDp.requireConstraintDimension(field: String) {
    require(value.isFinite() && value >= 0f) {
        "$field must be finite and non-negative."
    }
}

/**
 * Defines one virtual guideline.
 *
 * @property id unique helper ID
 * @property direction edge from which the position is resolved
 * @property position absolute offset or parent-relative fraction
 */
data class ConstraintGuidelineSpec(
    val id: String,
    val direction: ConstraintGuidelineDirection,
    val position: ConstraintGuidelinePosition,
)

/**
 * Selects the parent edge from which a virtual guideline is measured.
 *
 * [FromStart] and [FromEnd] mirror with layout direction. [FromLeft] and [FromRight] remain
 * physically fixed. Top and bottom are physical vertical edges.
 */
enum class ConstraintGuidelineDirection {
    FromStart,
    FromEnd,
    FromLeft,
    FromRight,
    FromTop,
    FromBottom,
}

/** Position of a virtual guideline relative to its configured parent edge. */
sealed interface ConstraintGuidelinePosition {
    /**
     * Absolute offset from the configured edge.
     *
     * @property value requested edge offset
     */
    data class Offset(
        val value: UiDp,
    ) : ConstraintGuidelinePosition

    /**
     * Parent-relative guideline position.
     *
     * @property value parent-dimension fraction, normally in the inclusive `0f..1f` range
     */
    data class Fraction(
        val value: Float,
    ) : ConstraintGuidelinePosition
}

/**
 * Defines a virtual edge derived from a group of referenced nodes.
 *
 * @property id unique helper ID
 * @property direction edge selected from each referenced node
 * @property referencedIds child or helper IDs contributing to the barrier
 * @property margin additional offset beyond the derived edge
 * @property allowsGoneWidgets whether gone references still contribute
 */
data class ConstraintBarrierSpec(
    val id: String,
    val direction: ConstraintBarrierDirection,
    val referencedIds: List<String>,
    val margin: UiDp = UiDp.Zero,
    val allowsGoneWidgets: Boolean = true,
)

/**
 * Selects the referenced extreme exposed by a Barrier.
 *
 * [Start] and [End] mirror with layout direction. [Left] and [Right] remain physically fixed. Top
 * and bottom are physical vertical extremes.
 */
enum class ConstraintBarrierDirection {
    Start,
    End,
    Left,
    Right,
    Top,
    Bottom,
}

/**
 * Defines distribution for an ordered group of constrained children.
 *
 * @property orientation chain axis
 * @property referencedIds ordered child IDs in the chain
 * @property weights optional per-child weights in the same order as [referencedIds]
 * @property style distribution style
 * @property bias optional packed-chain bias along the chain axis
 * @property startTarget optional first-boundary target; `null` selects the orientation default
 * @property endTarget optional last-boundary target; `null` selects the orientation default
 * @property startMargin finite non-negative first-boundary spacing
 * @property endMargin finite non-negative last-boundary spacing
 */
data class ConstraintChainSpec(
    val orientation: ConstraintChainOrientation,
    val referencedIds: List<String>,
    val weights: List<Float>? = null,
    val style: ConstraintChainStyle = ConstraintChainStyle.Spread,
    val bias: Float? = null,
    val startTarget: ConstraintAnchorTarget? = null,
    val endTarget: ConstraintAnchorTarget? = null,
    val startMargin: UiDp = UiDp.Zero,
    val endMargin: UiDp = UiDp.Zero,
)

/** Axis on which a constraint chain is formed. */
enum class ConstraintChainOrientation {
    Horizontal,
    Vertical,
}

/** Distribution strategy for children in a constraint chain. */
enum class ConstraintChainStyle {
    Spread,
    SpreadInside,
    Packed,
}

/** Fill order used when a [ConstraintGridSpec] assigns unspanned children to cells. */
enum class ConstraintGridOrientation {
    /** Fills each row from logical start to end before advancing vertically. */
    Horizontal,

    /** Fills each column from top to bottom before advancing toward logical end. */
    Vertical,
}

/**
 * Places one Grid member at an explicit cell and lets it occupy multiple rows or columns.
 *
 * @property referenceId member identity owned by the Grid
 * @property index zero-based row-major cell index
 * @property rowSpan number of occupied rows
 * @property columnSpan number of occupied columns
 */
data class ConstraintGridSpanSpec(
    val referenceId: String,
    val index: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
)

/**
 * Reserves an unoccupied rectangular Grid area.
 *
 * @property index zero-based row-major cell index
 * @property rowSpan number of reserved rows
 * @property columnSpan number of reserved columns
 */
data class ConstraintGridSkipSpec(
    val index: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
)

/**
 * Defines a bounded solver Grid without exposing AndroidX Grid's unchecked string grammar.
 *
 * A renderer validates and expands this declaration transactionally. Generated native identities
 * are implementation details owned by [id]; application code addresses only [referencedIds].
 * Construction performs no graph validation; callers must treat supplied lists as immutable, and
 * renderers must reject invalid topology without partially replacing the committed layout.
 *
 * @property id unique semantic Grid identity
 * @property referencedIds ordered child identities owned on both positioning axes
 * @property rows explicit row count, or `0` to infer it
 * @property columns explicit column count, or `0` to infer it
 * @property orientation automatic cell fill order
 * @property rowWeights optional positive weights, empty for equal rows
 * @property columnWeights optional positive weights, empty for equal columns
 * @property horizontalGap fixed gap between adjacent columns
 * @property verticalGap fixed gap between adjacent rows
 * @property spans explicit member placements and occupied rectangles
 * @property skips reserved rectangles that automatic placement cannot use
 */
data class ConstraintGridSpec(
    val id: String,
    val referencedIds: List<String>,
    val rows: Int = 0,
    val columns: Int = 0,
    val orientation: ConstraintGridOrientation = ConstraintGridOrientation.Horizontal,
    val rowWeights: List<Float> = emptyList(),
    val columnWeights: List<Float> = emptyList(),
    val horizontalGap: UiDp = UiDp.Zero,
    val verticalGap: UiDp = UiDp.Zero,
    val spans: List<ConstraintGridSpanSpec> = emptyList(),
    val skips: List<ConstraintGridSkipSpec> = emptyList(),
)

/**
 * Stores one explicit circular member value in [ConstraintCircularFlowSpec].
 *
 * @property referenceId child positioned around the shared center
 * @property radius finite non-negative center-to-center distance
 * @property angle finite clockwise angle in `0f..<360f`; `0f` is above the center
 */
data class ConstraintCircularFlowItemSpec(
    val referenceId: String,
    val radius: UiDp,
    val angle: Float,
)

/**
 * Groups explicit circle constraints under one semantic declaration.
 *
 * This transport creates no Android helper View. The renderer validates ownership for every item
 * and commits the complete group as ordinary circular constraints.
 *
 * @property id unique semantic group identity
 * @property centerId child used as every item's circle center
 * @property items non-empty unique member declarations
 */
data class ConstraintCircularFlowSpec(
    val id: String,
    val centerId: String,
    val items: List<ConstraintCircularFlowItemSpec>,
)

/**
 * Defines a virtual flow helper that lays out referenced nodes into one or more chains.
 *
 * Style and bias overrides follow native ConstraintLayout Flow semantics. A `null` override keeps
 * the corresponding general style or bias. [maxElementsWrap] uses `-1` for no explicit limit.
 *
 * @property id unique helper ID
 * @property referencedIds ordered child IDs managed by the flow
 * @property orientation primary placement axis
 * @property wrapMode strategy used when items wrap
 * @property horizontalGap horizontal gap between managed items
 * @property verticalGap vertical gap between managed items
 * @property horizontalStyle default horizontal chain style
 * @property verticalStyle default vertical chain style
 * @property firstHorizontalStyle optional first horizontal chain override
 * @property firstVerticalStyle optional first vertical chain override
 * @property lastHorizontalStyle optional last horizontal chain override
 * @property lastVerticalStyle optional last vertical chain override
 * @property horizontalBias default horizontal chain bias
 * @property verticalBias default vertical chain bias
 * @property firstHorizontalBias optional first horizontal chain bias override
 * @property firstVerticalBias optional first vertical chain bias override
 * @property lastHorizontalBias optional last horizontal chain bias override
 * @property lastVerticalBias optional last vertical chain bias override
 * @property horizontalAlign horizontal alignment inside flow cells
 * @property verticalAlign vertical alignment inside flow cells
 * @property maxElementsWrap maximum items per generated chain, or `-1` for renderer default
 * @property padding fallback padding applied on every edge
 * @property paddingStart logical start padding override
 * @property paddingEnd logical end padding override
 * @property paddingTop top padding override
 * @property paddingBottom bottom padding override
 */
data class ConstraintFlowSpec(
    val id: String,
    val referencedIds: List<String>,
    val orientation: ConstraintFlowOrientation = ConstraintFlowOrientation.Horizontal,
    val wrapMode: ConstraintFlowWrapMode = ConstraintFlowWrapMode.None,
    val horizontalGap: UiDp = UiDp.Zero,
    val verticalGap: UiDp = UiDp.Zero,
    val horizontalStyle: ConstraintChainStyle = ConstraintChainStyle.Spread,
    val verticalStyle: ConstraintChainStyle = ConstraintChainStyle.Spread,
    val firstHorizontalStyle: ConstraintChainStyle? = null,
    val firstVerticalStyle: ConstraintChainStyle? = null,
    val lastHorizontalStyle: ConstraintChainStyle? = null,
    val lastVerticalStyle: ConstraintChainStyle? = null,
    val horizontalBias: Float? = null,
    val verticalBias: Float? = null,
    val firstHorizontalBias: Float? = null,
    val firstVerticalBias: Float? = null,
    val lastHorizontalBias: Float? = null,
    val lastVerticalBias: Float? = null,
    val horizontalAlign: ConstraintFlowHorizontalAlign = ConstraintFlowHorizontalAlign.Center,
    val verticalAlign: ConstraintFlowVerticalAlign = ConstraintFlowVerticalAlign.Center,
    val maxElementsWrap: Int = -1,
    val padding: UiDp = UiDp.Zero,
    val paddingStart: UiDp = UiDp.Zero,
    val paddingEnd: UiDp = UiDp.Zero,
    val paddingTop: UiDp = UiDp.Zero,
    val paddingBottom: UiDp = UiDp.Zero,
)

/** Primary placement axis of a constraint flow. */
enum class ConstraintFlowOrientation {
    Horizontal,
    Vertical,
}

/** Wrapping strategy for a constraint flow. */
enum class ConstraintFlowWrapMode {
    None,
    Chain,
    Aligned,
}

/** Horizontal alignment of referenced nodes inside flow cells. */
enum class ConstraintFlowHorizontalAlign {
    Start,
    End,
    Center,
}

/** Vertical alignment of referenced nodes inside flow cells. */
enum class ConstraintFlowVerticalAlign {
    Top,
    Bottom,
    Center,
    Baseline,
}

/**
 * Applies shared visibility and elevation to a group of referenced nodes.
 *
 * @property id unique helper ID
 * @property referencedIds child IDs controlled by the group
 * @property visibility visibility propagated to referenced children
 * @property elevation elevation propagated to referenced children
 */
data class ConstraintGroupSpec(
    val id: String,
    val referencedIds: List<String>,
    val visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
    val elevation: UiDp = UiDp.Zero,
)

/**
 * Applies a shared transform, visibility, and elevation to referenced nodes.
 *
 * The renderer derives a pivot from the referenced bounds when [pivotX] or [pivotY] is `null`.
 *
 * @property id unique helper ID
 * @property referencedIds child IDs controlled by the layer
 * @property visibility visibility propagated to referenced children
 * @property elevation elevation propagated to referenced children
 * @property rotation clockwise rotation in degrees
 * @property scaleX horizontal scale factor
 * @property scaleY vertical scale factor
 * @property translationX horizontal translation
 * @property translationY vertical translation
 * @property pivotX optional horizontal transform pivot
 * @property pivotY optional vertical transform pivot
 */
data class ConstraintLayerSpec(
    val id: String,
    val referencedIds: List<String>,
    val visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
    val elevation: UiDp = UiDp.Zero,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationX: UiDp = UiDp.Zero,
    val translationY: UiDp = UiDp.Zero,
    val pivotX: UiDp? = null,
    val pivotY: UiDp? = null,
)

/**
 * Assigns one referenced child as the content of a virtual placeholder.
 *
 * @property id unique placeholder ID
 * @property contentId child ID displayed by the placeholder, or `null` for no content
 * @property emptyVisibility placeholder visibility when [contentId] is absent or unresolved
 */
data class ConstraintPlaceholderSpec(
    val id: String,
    val contentId: String? = null,
    val emptyVisibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Invisible,
)

/** Visibility value propagated by ConstraintLayout virtual helpers. */
enum class ConstraintHelperVisibility {
    Visible,
    Invisible,
    Gone,
}
