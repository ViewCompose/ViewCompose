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
 * @property flows virtual flow helper definitions
 * @property groups visibility and elevation group definitions
 * @property layers transform group definitions
 * @property placeholders content placeholder definitions
 */
data class ConstraintHelpersSpec(
    val guidelines: List<ConstraintGuidelineSpec> = emptyList(),
    val barriers: List<ConstraintBarrierSpec> = emptyList(),
    val chains: List<ConstraintChainSpec> = emptyList(),
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
 * @property top top-edge link
 * @property bottom bottom-edge link
 * @property baseline baseline-to-baseline, baseline-to-top, or baseline-to-bottom link
 * @property width primary horizontal dimension behavior
 * @property height primary vertical dimension behavior
 * @property horizontalBias optional placement bias between horizontal anchors
 * @property verticalBias optional placement bias between vertical anchors
 * @property ratio optional typed width-to-height ratio
 * @property circle optional circular positioning, mutually exclusive with edge and baseline links
 */
data class ConstraintItemSpec(
    val start: ConstraintAnchorLink? = null,
    val end: ConstraintAnchorLink? = null,
    val top: ConstraintAnchorLink? = null,
    val bottom: ConstraintAnchorLink? = null,
    val baseline: ConstraintAnchorLink? = null,
    val width: ConstraintDimension = ConstraintDimension.WrapContent,
    val height: ConstraintDimension = ConstraintDimension.WrapContent,
    val horizontalBias: Float? = null,
    val verticalBias: Float? = null,
    val ratio: ConstraintRatio? = null,
    val circle: ConstraintCircleSpec? = null,
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

/** Anchor exposed by a constraint target. */
enum class ConstraintAnchor {
    Start,
    End,
    Top,
    Bottom,
    Baseline,
}

/**
 * Positions a child around another referenced node.
 *
 * @property targetId center target ID
 * @property radius distance from the target center
 * @property angle clockwise angle in degrees using native ConstraintLayout coordinates
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

/** Parent edge from which a guideline position is resolved. */
enum class ConstraintGuidelineDirection {
    FromStart,
    FromEnd,
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

/** Direction in which a barrier derives its extreme edge. */
enum class ConstraintBarrierDirection {
    Start,
    End,
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
 */
data class ConstraintChainSpec(
    val orientation: ConstraintChainOrientation,
    val referencedIds: List<String>,
    val weights: List<Float>? = null,
    val style: ConstraintChainStyle = ConstraintChainStyle.Spread,
    val bias: Float? = null,
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
