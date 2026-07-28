package com.viewcompose.ui.node.spec

/**
 * ConstraintLayout 节点的约束集合属性。
 * Constraint-set properties for a ConstraintLayout node.
 */
data class ConstraintLayoutNodeProps(
    val constraintSet: ConstraintSetSpec? = null,
    val helpers: ConstraintHelpersSpec = ConstraintHelpersSpec(),
) : NodeSpec

data class ConstraintSetSpec(
    val constraints: Map<String, ConstraintItemSpec> = emptyMap(),
    val helpers: ConstraintHelpersSpec = ConstraintHelpersSpec(),
)

data class ConstraintHelpersSpec(
    val guidelines: List<ConstraintGuidelineSpec> = emptyList(),
    val barriers: List<ConstraintBarrierSpec> = emptyList(),
    val chains: List<ConstraintChainSpec> = emptyList(),
    val flows: List<ConstraintFlowSpec> = emptyList(),
    val groups: List<ConstraintGroupSpec> = emptyList(),
    val layers: List<ConstraintLayerSpec> = emptyList(),
    val placeholders: List<ConstraintPlaceholderSpec> = emptyList(),
)

data class ConstraintItemSpec(
    val start: ConstraintAnchorLink? = null,
    val end: ConstraintAnchorLink? = null,
    val top: ConstraintAnchorLink? = null,
    val bottom: ConstraintAnchorLink? = null,
    val baseline: ConstraintAnchorTarget? = null,
    val baselineToTop: ConstraintAnchorLink? = null,
    val baselineToBottom: ConstraintAnchorLink? = null,
    val width: ConstraintDimension = ConstraintDimension.WrapContent,
    val height: ConstraintDimension = ConstraintDimension.WrapContent,
    val widthMin: Int? = null,
    val widthMax: Int? = null,
    val widthPercent: Float? = null,
    val heightMin: Int? = null,
    val heightMax: Int? = null,
    val heightPercent: Float? = null,
    val constrainedWidth: Boolean = false,
    val constrainedHeight: Boolean = false,
    val horizontalBias: Float? = null,
    val verticalBias: Float? = null,
    val dimensionRatio: String? = null,
    val circle: ConstraintCircleSpec? = null,
)

data class ConstraintAnchorLink(
    val target: ConstraintAnchorTarget,
    val margin: Int = 0,
    val goneMargin: Int? = null,
)

data class ConstraintAnchorTarget(
    val id: String?,
    val anchor: ConstraintAnchor,
) {
    companion object {
        fun parent(anchor: ConstraintAnchor): ConstraintAnchorTarget = ConstraintAnchorTarget(
            id = null,
            anchor = anchor,
        )

        fun ref(
            id: String,
            anchor: ConstraintAnchor,
        ): ConstraintAnchorTarget = ConstraintAnchorTarget(
            id = id,
            anchor = anchor,
        )
    }
}

enum class ConstraintAnchor {
    Start,
    End,
    Top,
    Bottom,
    Baseline,
}

data class ConstraintCircleSpec(
    val targetId: String,
    val radius: Int,
    val angle: Float,
)

sealed interface ConstraintDimension {
    data object WrapContent : ConstraintDimension

    data object FillToConstraints : ConstraintDimension

    data object MatchParent : ConstraintDimension

    data class Fixed(
        val value: Int,
    ) : ConstraintDimension
}

data class ConstraintGuidelineSpec(
    val id: String,
    val direction: ConstraintGuidelineDirection,
    val position: ConstraintGuidelinePosition,
)

enum class ConstraintGuidelineDirection {
    FromStart,
    FromEnd,
    FromTop,
    FromBottom,
}

sealed interface ConstraintGuidelinePosition {
    data class Offset(
        val value: Int,
    ) : ConstraintGuidelinePosition

    data class Fraction(
        val value: Float,
    ) : ConstraintGuidelinePosition
}

data class ConstraintBarrierSpec(
    val id: String,
    val direction: ConstraintBarrierDirection,
    val referencedIds: List<String>,
    val margin: Int = 0,
    val allowsGoneWidgets: Boolean = true,
)

enum class ConstraintBarrierDirection {
    Start,
    End,
    Top,
    Bottom,
}

data class ConstraintChainSpec(
    val orientation: ConstraintChainOrientation,
    val referencedIds: List<String>,
    val weights: List<Float>? = null,
    val style: ConstraintChainStyle = ConstraintChainStyle.Spread,
    val bias: Float? = null,
)

enum class ConstraintChainOrientation {
    Horizontal,
    Vertical,
}

enum class ConstraintChainStyle {
    Spread,
    SpreadInside,
    Packed,
}

data class ConstraintFlowSpec(
    val id: String,
    val referencedIds: List<String>,
    val orientation: ConstraintFlowOrientation = ConstraintFlowOrientation.Horizontal,
    val wrapMode: ConstraintFlowWrapMode = ConstraintFlowWrapMode.None,
    val horizontalGap: Int = 0,
    val verticalGap: Int = 0,
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
    val padding: Int = 0,
    val paddingStart: Int = 0,
    val paddingEnd: Int = 0,
    val paddingTop: Int = 0,
    val paddingBottom: Int = 0,
)

enum class ConstraintFlowOrientation {
    Horizontal,
    Vertical,
}

enum class ConstraintFlowWrapMode {
    None,
    Chain,
    Aligned,
}

enum class ConstraintFlowHorizontalAlign {
    Start,
    End,
    Center,
}

enum class ConstraintFlowVerticalAlign {
    Top,
    Bottom,
    Center,
    Baseline,
}

data class ConstraintGroupSpec(
    val id: String,
    val referencedIds: List<String>,
    val visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
    val elevation: Float = 0f,
)

data class ConstraintLayerSpec(
    val id: String,
    val referencedIds: List<String>,
    val visibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Visible,
    val elevation: Float = 0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val pivotX: Float? = null,
    val pivotY: Float? = null,
)

data class ConstraintPlaceholderSpec(
    val id: String,
    val contentId: String? = null,
    val emptyVisibility: ConstraintHelperVisibility = ConstraintHelperVisibility.Invisible,
)

enum class ConstraintHelperVisibility {
    Visible,
    Invisible,
    Gone,
}
