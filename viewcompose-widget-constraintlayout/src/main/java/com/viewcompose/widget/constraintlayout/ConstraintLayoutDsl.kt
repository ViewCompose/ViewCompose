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

/**
 * ConstraintLayout content 中可使用的构建 scope。
 * Builder scope available inside ConstraintLayout content.
 */
typealias ConstraintLayoutScope = UiTreeBuilder

/**
 * 业务侧引用一个带 layoutId 的子节点。
 * App-side reference to a child node with a layoutId.
 */
data class ConstraintReference(
    override val id: String,
) : ConstraintReferenceTarget

/**
 * constraint anchor 可连接的目标。
 * Target that a constraint anchor can connect to.
 */
sealed interface ConstraintReferenceTarget {
    val id: String?
}

/**
 * ConstraintLayout 父容器自身的引用。
 * Reference to the ConstraintLayout parent itself.
 */
data object ConstraintParentReference : ConstraintReferenceTarget {
    override val id: String? = null
}

val parent: ConstraintReferenceTarget
    get() = ConstraintParentReference

/**
 * 收集 ConstraintLayout helper DSL 生成的 guideline/barrier/chain 等辅助规格。
 * Collects helper specs such as guidelines, barriers, and chains created by the ConstraintLayout DSL.
 */
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

/**
 * 跟踪嵌套 ConstraintLayout DSL 调用的线程局部上下文。
 * Thread-local context stack that tracks nested ConstraintLayout DSL calls.
 */
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
 * 单个子节点的 constraint 配置 scope。
 * Constraint configuration scope for one child node.
 */
class ConstraintConstrainScope internal constructor() {
    private var start: ConstraintAnchorLink? = null
    private var end: ConstraintAnchorLink? = null
    private var top: ConstraintAnchorLink? = null
    private var bottom: ConstraintAnchorLink? = null
    private var baseline: ConstraintAnchorTarget? = null
    private var baselineToTop: ConstraintAnchorLink? = null
    private var baselineToBottom: ConstraintAnchorLink? = null
    var width: ConstraintDimension = ConstraintDimension.WrapContent
    var height: ConstraintDimension = ConstraintDimension.WrapContent
    var widthMin: UiDp? = null
    var widthMax: UiDp? = null
    var widthPercent: Float? = null
    var heightMin: UiDp? = null
    var heightMax: UiDp? = null
    var heightPercent: Float? = null
    var constrainedWidth: Boolean = false
    var constrainedHeight: Boolean = false
    var horizontalBias: Float? = null
    var verticalBias: Float? = null
    var dimensionRatio: String? = null
    private var circle: ConstraintCircleSpec? = null

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

    fun baselineToBaseline(target: ConstraintReference) {
        baseline = ConstraintAnchorTarget.ref(
            id = target.id,
            anchor = ConstraintAnchor.Baseline,
        )
    }

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

    fun centerHorizontallyTo(target: ConstraintReferenceTarget = parent) {
        startToStart(target)
        endToEnd(target)
    }

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
 * 将 modifier 绑定到 [ref]，并声明该子节点的 constraints。
 * Binds a modifier to [ref] and declares constraints for that child.
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
 * 发射 ConstraintLayout 节点，并收集 content 中声明的 helper。
 * Emits a ConstraintLayout node and collects helpers declared inside [content].
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
 * 创建一个可用于 constrain/helper API 的引用。
 * Creates a reference usable by constrain and helper APIs.
 */
fun ConstraintLayoutScope.createRef(id: String): ConstraintReference {
    return ConstraintReference(id = id)
}

fun ConstraintLayoutScope.createRefs(vararg ids: String): Array<ConstraintReference> {
    return ids.map { id -> createRef(id) }.toTypedArray()
}

private fun ConstraintLayoutScope.allocHelperId(prefix: String): String {
    return requireConstraintContext().helpers.allocId(prefix)
}

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

/**
 * 注册 barrier helper 的共享实现。
 * Shared implementation for registering barrier helpers.
 */
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

fun ConstraintLayoutScope.createStartBarrier(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("barrier-start"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintReference {
    return registerBarrier(id, ConstraintBarrierDirection.Start, refs, margin, allowsGoneWidgets)
}

fun ConstraintLayoutScope.createEndBarrier(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("barrier-end"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintReference {
    return registerBarrier(id, ConstraintBarrierDirection.End, refs, margin, allowsGoneWidgets)
}

fun ConstraintLayoutScope.createTopBarrier(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("barrier-top"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintReference {
    return registerBarrier(id, ConstraintBarrierDirection.Top, refs, margin, allowsGoneWidgets)
}

fun ConstraintLayoutScope.createBottomBarrier(
    vararg refs: ConstraintReference,
    id: String = allocHelperId("barrier-bottom"),
    margin: UiDp = UiDp.Zero,
    allowsGoneWidgets: Boolean = true,
): ConstraintReference {
    return registerBarrier(id, ConstraintBarrierDirection.Bottom, refs, margin, allowsGoneWidgets)
}

/**
 * 创建 Flow helper，用于把多个引用按行/列自动换行排布。
 * Creates a Flow helper that lays out multiple references with row/column wrapping.
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
 * 构建可复用 [ConstraintSetSpec] 的 DSL builder。
 * DSL builder for reusable [ConstraintSetSpec].
 */
class ConstraintSetBuilder internal constructor() {
    private val constraints = linkedMapOf<String, ConstraintItemSpec>()
    private val helpers = MutableConstraintHelpersCollector()

    fun createRef(id: String): ConstraintReference {
        return ConstraintReference(id = id)
    }

    fun createRefs(vararg ids: String): Array<ConstraintReference> {
        return ids.map { id -> createRef(id) }.toTypedArray()
    }

    fun constrain(
        id: String,
        content: ConstraintConstrainScope.() -> Unit,
    ) {
        constraints[id] = buildConstraintSpec(content)
    }

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
 * 构建独立 constraint set，可传给 [ConstraintLayout] 复用。
 * Builds a standalone constraint set that can be reused by [ConstraintLayout].
 */
fun constraintSet(content: ConstraintSetBuilder.() -> Unit): ConstraintSetSpec {
    return ConstraintSetBuilder()
        .apply(content)
        .build()
}
