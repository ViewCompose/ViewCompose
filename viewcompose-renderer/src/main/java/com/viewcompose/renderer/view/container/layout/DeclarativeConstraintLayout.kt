package com.viewcompose.renderer.view.container

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.view.doOnLayout
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.helper.widget.Layer
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Group
import androidx.constraintlayout.widget.Placeholder
import com.viewcompose.renderer.R
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.roundToPx
import com.viewcompose.renderer.view.toPx
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintBarrierDirection
import com.viewcompose.ui.node.spec.ConstraintBarrierSpec
import com.viewcompose.ui.node.spec.ConstraintChainOrientation
import com.viewcompose.ui.node.spec.ConstraintChainSpec
import com.viewcompose.ui.node.spec.ConstraintChainStyle
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
import com.viewcompose.ui.node.spec.ConstraintPlaceholderSpec
import com.viewcompose.ui.node.spec.ConstraintSetSpec

/**
 * ConstraintLayout DSL 对应的 Android ConstraintLayout 容器。
 * Android ConstraintLayout container for the ConstraintLayout DSL.
 */
internal class DeclarativeConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ConstraintLayout(context, attrs) {
    companion object {
        private const val WARNING_TAG = "UIConstraintLayout"
    }

    var inlineHelpersSpec: ConstraintHelpersSpec = ConstraintHelpersSpec()
        set(value) {
            if (field == value) return
            field = value
            requestConstraintRebuild()
        }

    var decoupledConstraintSetSpec: ConstraintSetSpec? = null
        set(value) {
            if (field == value) return
            field = value
            requestConstraintRebuild()
        }

    private val referenceIdToViewId = mutableMapOf<String, Int>()
    private val helperIdToViewId = mutableMapOf<String, Int>()
    private val helperViews = mutableMapOf<String, View>()
    private val emittedWarnings = mutableSetOf<String>()
    private var pendingConstraintRebuild = false
    private var mutatingHelperViews = false
    private val rebuildRunnable = Runnable {
        if (!pendingConstraintRebuild) {
            return@Runnable
        }
        pendingConstraintRebuild = false
        applyConstraintsInternal()
    }

    private data class ChainResolvedItem(
        val viewId: Int,
        val weight: Float?,
    )

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        flushPendingConstraintRebuild()
        val startNs = LayoutPassTracker.beginTiming()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        LayoutPassTracker.recordMeasureSince(javaClass, startNs)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        flushPendingConstraintRebuild()
        val startNs = LayoutPassTracker.beginTiming()
        super.onLayout(changed, left, top, right, bottom)
        LayoutPassTracker.recordLayoutSince(javaClass, startNs)
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (!mutatingHelperViews) {
            requestConstraintRebuild()
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (!mutatingHelperViews) {
            requestConstraintRebuild()
        }
    }

    fun requestConstraintRebuild() {
        if (pendingConstraintRebuild) {
            return
        }
        pendingConstraintRebuild = true
        post(rebuildRunnable)
    }

    fun applyConstraintsNow() {
        flushPendingConstraintRebuild()
    }

    private fun flushPendingConstraintRebuild() {
        if (!pendingConstraintRebuild) {
            return
        }
        removeCallbacks(rebuildRunnable)
        pendingConstraintRebuild = false
        applyConstraintsInternal()
    }

    private fun applyConstraintsInternal() {
        val inlineConstraints = collectInlineConstraints()
        val mergedConstraints = mergeConstraints(
            decoupled = decoupledConstraintSetSpec?.constraints.orEmpty(),
            inline = inlineConstraints,
        )
        if (hasCircularDependency(mergedConstraints)) {
            warnOnce(
                "Constraint graph contains circular references. Layout falls back to ConstraintLayout runtime resolution.",
            )
        }
        val mergedHelpers = mergeHelpers(
            decoupled = decoupledConstraintSetSpec?.helpers,
            inline = inlineHelpersSpec,
        )
        syncReferenceViewIds()
        val constraintSet = ConstraintSet()
        constraintSet.clone(this)
        clearChildConstraints(constraintSet)
        val helperReferenceIds = applyHelpers(
            constraintSet = constraintSet,
            helpers = mergedHelpers,
        )
        applyChains(
            constraintSet = constraintSet,
            chains = mergedHelpers.chains,
            helperReferenceIds = helperReferenceIds,
        )
        applyItemConstraints(
            constraintSet = constraintSet,
            constraints = mergedConstraints,
            helperReferenceIds = helperReferenceIds,
        )
        try {
            constraintSet.applyTo(this)
        } catch (error: Throwable) {
            warnOnce("ConstraintSet apply failed: ${error.message}")
        }
    }

    private fun collectInlineConstraints(): Map<String, ConstraintItemSpec> {
        val constraints = linkedMapOf<String, ConstraintItemSpec>()
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val referenceId = child.getTag(R.id.viewcompose_constraint_layout_id) as? String
            val inlineSpec = child.getTag(R.id.viewcompose_constraint_item_spec) as? ConstraintItemSpec
            if (referenceId == null || inlineSpec == null) {
                continue
            }
            val previous = constraints.put(referenceId, inlineSpec)
            if (previous != null && previous != inlineSpec) {
                warnOnce("Duplicate inline constraint id '$referenceId'. Last child wins.")
            }
        }
        return constraints
    }

    private fun mergeConstraints(
        decoupled: Map<String, ConstraintItemSpec>,
        inline: Map<String, ConstraintItemSpec>,
    ): Map<String, ConstraintItemSpec> {
        val merged = linkedMapOf<String, ConstraintItemSpec>()
        merged.putAll(decoupled)
        inline.forEach { (id, spec) ->
            if (merged.containsKey(id) && decoupled.containsKey(id)) {
                warnOnce("Inline constraint overrides decoupled ConstraintSet item '$id'.")
            }
            merged[id] = spec
        }
        return merged
    }

    private fun mergeHelpers(
        decoupled: ConstraintHelpersSpec?,
        inline: ConstraintHelpersSpec,
    ): ConstraintHelpersSpec {
        val guidelineMap = linkedMapOf<String, ConstraintGuidelineSpec>()
        decoupled?.guidelines.orEmpty().forEach { spec ->
            guidelineMap[spec.id] = spec
        }
        inline.guidelines.forEach { spec ->
            if (guidelineMap.containsKey(spec.id)) {
                warnOnce("Inline guideline '${spec.id}' overrides decoupled helper with same id.")
            }
            guidelineMap[spec.id] = spec
        }

        val barrierMap = linkedMapOf<String, ConstraintBarrierSpec>()
        decoupled?.barriers.orEmpty().forEach { spec ->
            barrierMap[spec.id] = spec
        }
        inline.barriers.forEach { spec ->
            if (barrierMap.containsKey(spec.id)) {
                warnOnce("Inline barrier '${spec.id}' overrides decoupled helper with same id.")
            }
            barrierMap[spec.id] = spec
        }

        val flowMap = linkedMapOf<String, ConstraintFlowSpec>()
        decoupled?.flows.orEmpty().forEach { spec ->
            flowMap[spec.id] = spec
        }
        inline.flows.forEach { spec ->
            if (flowMap.containsKey(spec.id)) {
                warnOnce("Inline flow '${spec.id}' overrides decoupled helper with same id.")
            }
            flowMap[spec.id] = spec
        }

        val groupMap = linkedMapOf<String, ConstraintGroupSpec>()
        decoupled?.groups.orEmpty().forEach { spec ->
            groupMap[spec.id] = spec
        }
        inline.groups.forEach { spec ->
            if (groupMap.containsKey(spec.id)) {
                warnOnce("Inline group '${spec.id}' overrides decoupled helper with same id.")
            }
            groupMap[spec.id] = spec
        }

        val layerMap = linkedMapOf<String, ConstraintLayerSpec>()
        decoupled?.layers.orEmpty().forEach { spec ->
            layerMap[spec.id] = spec
        }
        inline.layers.forEach { spec ->
            if (layerMap.containsKey(spec.id)) {
                warnOnce("Inline layer '${spec.id}' overrides decoupled helper with same id.")
            }
            layerMap[spec.id] = spec
        }

        val placeholderMap = linkedMapOf<String, ConstraintPlaceholderSpec>()
        decoupled?.placeholders.orEmpty().forEach { spec ->
            placeholderMap[spec.id] = spec
        }
        inline.placeholders.forEach { spec ->
            if (placeholderMap.containsKey(spec.id)) {
                warnOnce("Inline placeholder '${spec.id}' overrides decoupled helper with same id.")
            }
            placeholderMap[spec.id] = spec
        }

        return ConstraintHelpersSpec(
            guidelines = guidelineMap.values.toList(),
            barriers = barrierMap.values.toList(),
            chains = decoupled?.chains.orEmpty() + inline.chains,
            flows = flowMap.values.toList(),
            groups = groupMap.values.toList(),
            layers = layerMap.values.toList(),
            placeholders = placeholderMap.values.toList(),
        )
    }

    private fun syncReferenceViewIds() {
        val activeReferenceIds = mutableSetOf<String>()
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val referenceId = child.getTag(R.id.viewcompose_constraint_layout_id) as? String ?: continue
            activeReferenceIds += referenceId
            val resolvedViewId = referenceIdToViewId.getOrPut(referenceId) { View.generateViewId() }
            if (child.id != resolvedViewId) {
                child.id = resolvedViewId
            }
        }
        referenceIdToViewId.keys.retainAll(activeReferenceIds)
    }

    private fun clearChildConstraints(constraintSet: ConstraintSet) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.id != View.NO_ID) {
                constraintSet.clear(child.id)
            }
        }
    }

    private fun applyHelpers(
        constraintSet: ConstraintSet,
        helpers: ConstraintHelpersSpec,
    ): Map<String, Int> {
        val activeHelperKeys = mutableSetOf<String>()
        val helperReferenceIds = mutableMapOf<String, Int>()

        helpers.guidelines.forEach { guideline ->
            registerHelperReference(
                prefix = "guideline",
                helperId = guideline.id,
                activeHelperKeys = activeHelperKeys,
                helperReferenceIds = helperReferenceIds,
            )
        }
        helpers.barriers.forEach { barrier ->
            registerHelperReference(
                prefix = "barrier",
                helperId = barrier.id,
                activeHelperKeys = activeHelperKeys,
                helperReferenceIds = helperReferenceIds,
            )
        }
        helpers.flows.forEach { flow ->
            registerHelperReference(
                prefix = "flow",
                helperId = flow.id,
                activeHelperKeys = activeHelperKeys,
                helperReferenceIds = helperReferenceIds,
            )
        }
        helpers.groups.forEach { group ->
            registerHelperReference(
                prefix = "group",
                helperId = group.id,
                activeHelperKeys = activeHelperKeys,
                helperReferenceIds = helperReferenceIds,
            )
        }
        helpers.layers.forEach { layer ->
            registerHelperReference(
                prefix = "layer",
                helperId = layer.id,
                activeHelperKeys = activeHelperKeys,
                helperReferenceIds = helperReferenceIds,
            )
        }
        helpers.placeholders.forEach { placeholder ->
            registerHelperReference(
                prefix = "placeholder",
                helperId = placeholder.id,
                activeHelperKeys = activeHelperKeys,
                helperReferenceIds = helperReferenceIds,
            )
        }

        helpers.guidelines.forEach { guideline ->
            val key = helperKey("guideline", guideline.id)
            val helperId = requireNotNull(helperIdToViewId[key])
            applyGuideline(constraintSet, helperId, guideline)
        }

        helpers.barriers.forEach { barrier ->
            val key = helperKey("barrier", barrier.id)
            val helperId = requireNotNull(helperIdToViewId[key])
            val references = resolveReferencedIds(
                referenceIds = barrier.referencedIds,
                helperReferenceIds = helperReferenceIds,
                warningPrefix = "Barrier '${barrier.id}'",
            )
            if (references.isEmpty()) {
                warnOnce("Barrier '${barrier.id}' has no valid referenced ids and will be ignored.")
                return@forEach
            }
            constraintSet.createBarrier(
                helperId,
                barrier.direction.toConstraintSetDirection(),
                requireUiEnvironment().roundToPx(barrier.margin),
                *references,
            )
            constraintSet.getConstraint(helperId)?.layout?.mBarrierAllowsGoneWidgets = barrier.allowsGoneWidgets
        }

        helpers.flows.forEach { flow ->
            val key = helperKey("flow", flow.id)
            val helperId = requireNotNull(helperIdToViewId[key])
            applyFlowHelper(
                key = key,
                helperId = helperId,
                spec = flow,
                helperReferenceIds = helperReferenceIds,
            )
        }

        helpers.groups.forEach { group ->
            val key = helperKey("group", group.id)
            val helperId = requireNotNull(helperIdToViewId[key])
            applyGroupHelper(
                key = key,
                helperId = helperId,
                spec = group,
                helperReferenceIds = helperReferenceIds,
            )
        }

        helpers.layers.forEach { layer ->
            val key = helperKey("layer", layer.id)
            val helperId = requireNotNull(helperIdToViewId[key])
            applyLayerHelper(
                key = key,
                helperId = helperId,
                spec = layer,
                helperReferenceIds = helperReferenceIds,
            )
        }

        helpers.placeholders.forEach { placeholder ->
            val key = helperKey("placeholder", placeholder.id)
            val helperId = requireNotNull(helperIdToViewId[key])
            applyPlaceholderHelper(
                key = key,
                helperId = helperId,
                spec = placeholder,
                helperReferenceIds = helperReferenceIds,
            )
        }

        pruneInactiveHelperViews(activeHelperKeys)
        helperIdToViewId.keys.retainAll(activeHelperKeys)
        return helperReferenceIds
    }

    private fun registerHelperReference(
        prefix: String,
        helperId: String,
        activeHelperKeys: MutableSet<String>,
        helperReferenceIds: MutableMap<String, Int>,
    ) {
        val key = helperKey(prefix, helperId)
        val resolvedId = helperIdToViewId.getOrPut(key) { View.generateViewId() }
        activeHelperKeys += key
        helperReferenceIds[helperId] = resolvedId
    }

    private fun helperKey(
        prefix: String,
        id: String,
    ): String = "$prefix:$id"

    private fun resolveReferencedIds(
        referenceIds: List<String>,
        helperReferenceIds: Map<String, Int>,
        warningPrefix: String,
    ): IntArray {
        return referenceIds.mapNotNull { referenceId ->
            resolveReferenceId(
                referenceId = referenceId,
                helperReferenceIds = helperReferenceIds,
                warningPrefix = warningPrefix,
            )
        }.toIntArray()
    }

    private fun applyFlowHelper(
        key: String,
        helperId: Int,
        spec: ConstraintFlowSpec,
        helperReferenceIds: Map<String, Int>,
    ) {
        val environment = requireUiEnvironment()
        val flowView = ensureHelperView(
            key = key,
            viewId = helperId,
            viewClass = Flow::class.java,
        ) { Flow(context) }
        val referencedIds = resolveReferencedIds(
            referenceIds = spec.referencedIds,
            helperReferenceIds = helperReferenceIds,
            warningPrefix = "Flow '${spec.id}'",
        )
        if (referencedIds.isEmpty()) {
            warnOnce("Flow '${spec.id}' has no valid referenced ids.")
        }
        flowView.setReferencedIds(referencedIds)
        flowView.setOrientation(spec.orientation.toFlowOrientation())
        flowView.setWrapMode(spec.wrapMode.toFlowWrapMode())
        flowView.setHorizontalGap(environment.roundToPx(spec.horizontalGap))
        flowView.setVerticalGap(environment.roundToPx(spec.verticalGap))
        flowView.setHorizontalStyle(spec.horizontalStyle.toConstraintSetChainStyle())
        flowView.setVerticalStyle(spec.verticalStyle.toConstraintSetChainStyle())
        flowView.setFirstHorizontalStyle(
            (spec.firstHorizontalStyle ?: spec.horizontalStyle).toConstraintSetChainStyle(),
        )
        flowView.setFirstVerticalStyle(
            (spec.firstVerticalStyle ?: spec.verticalStyle).toConstraintSetChainStyle(),
        )
        flowView.setLastHorizontalStyle(
            (spec.lastHorizontalStyle ?: spec.horizontalStyle).toConstraintSetChainStyle(),
        )
        flowView.setLastVerticalStyle(
            (spec.lastVerticalStyle ?: spec.verticalStyle).toConstraintSetChainStyle(),
        )
        val defaultHorizontalBias = spec.horizontalBias ?: 0.5f
        val defaultVerticalBias = spec.verticalBias ?: 0.5f
        flowView.setHorizontalBias(defaultHorizontalBias)
        flowView.setVerticalBias(defaultVerticalBias)
        flowView.setFirstHorizontalBias(spec.firstHorizontalBias ?: defaultHorizontalBias)
        flowView.setFirstVerticalBias(spec.firstVerticalBias ?: defaultVerticalBias)
        flowView.setLastHorizontalBias(spec.lastHorizontalBias ?: defaultHorizontalBias)
        flowView.setLastVerticalBias(spec.lastVerticalBias ?: defaultVerticalBias)
        flowView.setHorizontalAlign(spec.horizontalAlign.toFlowHorizontalAlign())
        flowView.setVerticalAlign(spec.verticalAlign.toFlowVerticalAlign())
        flowView.setMaxElementsWrap(spec.maxElementsWrap)
        flowView.setPadding(environment.roundToPx(spec.padding))
        if (flowView.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            flowView.setPaddingLeft(environment.roundToPx(spec.paddingEnd))
            flowView.setPaddingRight(environment.roundToPx(spec.paddingStart))
        } else {
            flowView.setPaddingLeft(environment.roundToPx(spec.paddingStart))
            flowView.setPaddingRight(environment.roundToPx(spec.paddingEnd))
        }
        flowView.setPaddingTop(environment.roundToPx(spec.paddingTop))
        flowView.setPaddingBottom(environment.roundToPx(spec.paddingBottom))
    }

    private fun applyGroupHelper(
        key: String,
        helperId: Int,
        spec: ConstraintGroupSpec,
        helperReferenceIds: Map<String, Int>,
    ) {
        val groupView = ensureHelperView(
            key = key,
            viewId = helperId,
            viewClass = Group::class.java,
        ) { Group(context) }
        val referencedIds = resolveReferencedIds(
            referenceIds = spec.referencedIds,
            helperReferenceIds = helperReferenceIds,
            warningPrefix = "Group '${spec.id}'",
        )
        if (referencedIds.isEmpty()) {
            warnOnce("Group '${spec.id}' has no valid referenced ids.")
        }
        groupView.setReferencedIds(referencedIds)
        groupView.visibility = spec.visibility.toViewVisibility()
        groupView.elevation = requireUiEnvironment().toPx(spec.elevation)
    }

    private fun applyLayerHelper(
        key: String,
        helperId: Int,
        spec: ConstraintLayerSpec,
        helperReferenceIds: Map<String, Int>,
    ) {
        val layerView = ensureHelperView(
            key = key,
            viewId = helperId,
            viewClass = SafeLayer::class.java,
        ) { SafeLayer(context) }
        val referencedIds = resolveReferencedIds(
            referenceIds = spec.referencedIds,
            helperReferenceIds = helperReferenceIds,
            warningPrefix = "Layer '${spec.id}'",
        )
        if (referencedIds.isEmpty()) {
            warnOnce("Layer '${spec.id}' has no valid referenced ids.")
        }
        layerView.setReferencedIds(referencedIds)
        layerView.visibility = spec.visibility.toViewVisibility()
        layerView.elevation = requireUiEnvironment().toPx(spec.elevation)
        if (referencedIds.isEmpty()) {
            return
        }
        layerView.doOnLayout { view ->
            applyLayerTransformsSafely(
                layerView = view as Layer,
                spec = spec,
            )
        }
    }

    private fun applyLayerTransformsSafely(
        layerView: Layer,
        spec: ConstraintLayerSpec,
    ) {
        val environment = requireUiEnvironment()
        try {
            layerView.rotation = spec.rotation
            layerView.scaleX = spec.scaleX
            layerView.scaleY = spec.scaleY
            layerView.translationX = environment.toPx(spec.translationX)
            layerView.translationY = environment.toPx(spec.translationY)
            layerView.pivotX = spec.pivotX?.let(environment::toPx) ?: Float.NaN
            layerView.pivotY = spec.pivotY?.let(environment::toPx) ?: Float.NaN
        } catch (error: RuntimeException) {
            warnOnce("Layer '${spec.id}' transform apply failed after layout: ${error.message}")
        }
    }

    private fun applyPlaceholderHelper(
        key: String,
        helperId: Int,
        spec: ConstraintPlaceholderSpec,
        helperReferenceIds: Map<String, Int>,
    ) {
        val placeholderView = ensureHelperView(
            key = key,
            viewId = helperId,
            viewClass = Placeholder::class.java,
        ) { Placeholder(context) }
        placeholderView.emptyVisibility = spec.emptyVisibility.toViewVisibility()
        val contentViewId = spec.contentId?.let { contentId ->
            resolveReferenceId(
                referenceId = contentId,
                helperReferenceIds = helperReferenceIds,
                warningPrefix = "Placeholder '${spec.id}' content",
            )
        } ?: ConstraintLayout.LayoutParams.UNSET
        placeholderView.setContentId(contentViewId ?: ConstraintLayout.LayoutParams.UNSET)
    }

    private fun <T : View> ensureHelperView(
        key: String,
        viewId: Int,
        viewClass: Class<T>,
        factory: () -> T,
    ): T {
        val current = helperViews[key]
        if (viewClass.isInstance(current)) {
            val typed = requireNotNull(viewClass.cast(current))
            if (typed.id != viewId) {
                typed.id = viewId
            }
            return typed
        }
        if (current != null) {
            mutatingHelperViews = true
            try {
                removeView(current)
            } finally {
                mutatingHelperViews = false
            }
        }
        val created = factory()
        created.id = viewId
        if (created.layoutParams == null) {
            created.layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            )
        }
        mutatingHelperViews = true
        try {
            addView(created)
        } finally {
            mutatingHelperViews = false
        }
        helperViews[key] = created
        return created
    }

    private fun pruneInactiveHelperViews(activeHelperKeys: Set<String>) {
        val staleKeys = helperViews.keys.filterNot { key -> activeHelperKeys.contains(key) }
        if (staleKeys.isEmpty()) {
            return
        }
        mutatingHelperViews = true
        try {
            staleKeys.forEach { key ->
                helperViews.remove(key)?.let { helperView ->
                    removeView(helperView)
                }
            }
        } finally {
            mutatingHelperViews = false
        }
    }

    private fun applyGuideline(
        constraintSet: ConstraintSet,
        helperId: Int,
        spec: ConstraintGuidelineSpec,
    ) {
        val orientation = when (spec.direction) {
            ConstraintGuidelineDirection.FromStart,
            ConstraintGuidelineDirection.FromEnd,
            -> ConstraintSet.VERTICAL_GUIDELINE

            ConstraintGuidelineDirection.FromTop,
            ConstraintGuidelineDirection.FromBottom,
            -> ConstraintSet.HORIZONTAL_GUIDELINE
        }
        constraintSet.create(helperId, orientation)
        when (val position = spec.position) {
            is ConstraintGuidelinePosition.Offset -> {
                when (spec.direction) {
                    ConstraintGuidelineDirection.FromStart,
                    ConstraintGuidelineDirection.FromTop,
                    -> constraintSet.setGuidelineBegin(
                        helperId,
                        requireUiEnvironment().roundToPx(position.value),
                    )

                    ConstraintGuidelineDirection.FromEnd,
                    ConstraintGuidelineDirection.FromBottom,
                    -> constraintSet.setGuidelineEnd(
                        helperId,
                        requireUiEnvironment().roundToPx(position.value),
                    )
                }
            }

            is ConstraintGuidelinePosition.Fraction -> {
                val percent = when (spec.direction) {
                    ConstraintGuidelineDirection.FromStart,
                    ConstraintGuidelineDirection.FromTop,
                    -> position.value

                    ConstraintGuidelineDirection.FromEnd,
                    ConstraintGuidelineDirection.FromBottom,
                    -> 1f - position.value
                }
                constraintSet.setGuidelinePercent(helperId, percent.coerceIn(0f, 1f))
            }
        }
    }

    private fun applyChains(
        constraintSet: ConstraintSet,
        chains: List<ConstraintChainSpec>,
        helperReferenceIds: Map<String, Int>,
    ) {
        chains.forEachIndexed { index, chain ->
            val chainWeights = chain.weights
            if (chainWeights != null && chainWeights.size != chain.referencedIds.size) {
                warnOnce(
                    "Chain[$index] weights size (${chainWeights.size}) does not match referenced ids size " +
                        "(${chain.referencedIds.size}). Extra values are ignored.",
                )
            }
            val resolvedItems = chain.referencedIds.mapIndexedNotNull { refIndex, referenceId ->
                val resolvedId = resolveReferenceId(
                    referenceId = referenceId,
                    helperReferenceIds = helperReferenceIds,
                    warningPrefix = "Chain[$index]",
                ) ?: return@mapIndexedNotNull null
                ChainResolvedItem(
                    viewId = resolvedId,
                    weight = chainWeights?.getOrNull(refIndex),
                )
            }
            if (resolvedItems.size < 2) {
                warnOnce("Chain[$index] requires at least two valid references.")
                return@forEachIndexed
            }
            when (chain.orientation) {
                ConstraintChainOrientation.Horizontal -> {
                    applyHorizontalChain(constraintSet, resolvedItems, chain)
                }

                ConstraintChainOrientation.Vertical -> {
                    applyVerticalChain(constraintSet, resolvedItems, chain)
                }
            }
        }
    }

    private fun applyHorizontalChain(
        constraintSet: ConstraintSet,
        resolvedItems: List<ChainResolvedItem>,
        chain: ConstraintChainSpec,
    ) {
        val resolvedIds = resolvedItems.map { item -> item.viewId }
        val first = resolvedIds.first()
        val last = resolvedIds.last()
        constraintSet.connect(first, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(last, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        for (index in resolvedIds.indices) {
            val viewId = resolvedIds[index]
            if (index > 0) {
                constraintSet.connect(
                    viewId,
                    ConstraintSet.START,
                    resolvedIds[index - 1],
                    ConstraintSet.END,
                )
            }
            if (index < resolvedIds.lastIndex) {
                constraintSet.connect(
                    viewId,
                    ConstraintSet.END,
                    resolvedIds[index + 1],
                    ConstraintSet.START,
                )
            }
        }
        constraintSet.setHorizontalChainStyle(first, chain.style.toConstraintSetChainStyle())
        chain.bias?.let { bias ->
            constraintSet.setHorizontalBias(first, bias)
        }
        resolvedItems.forEach { item ->
            item.weight?.let { weight ->
                constraintSet.setHorizontalWeight(item.viewId, weight)
            }
        }
    }

    private fun applyVerticalChain(
        constraintSet: ConstraintSet,
        resolvedItems: List<ChainResolvedItem>,
        chain: ConstraintChainSpec,
    ) {
        val resolvedIds = resolvedItems.map { item -> item.viewId }
        val first = resolvedIds.first()
        val last = resolvedIds.last()
        constraintSet.connect(first, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(last, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        for (index in resolvedIds.indices) {
            val viewId = resolvedIds[index]
            if (index > 0) {
                constraintSet.connect(
                    viewId,
                    ConstraintSet.TOP,
                    resolvedIds[index - 1],
                    ConstraintSet.BOTTOM,
                )
            }
            if (index < resolvedIds.lastIndex) {
                constraintSet.connect(
                    viewId,
                    ConstraintSet.BOTTOM,
                    resolvedIds[index + 1],
                    ConstraintSet.TOP,
                )
            }
        }
        constraintSet.setVerticalChainStyle(first, chain.style.toConstraintSetChainStyle())
        chain.bias?.let { bias ->
            constraintSet.setVerticalBias(first, bias)
        }
        resolvedItems.forEach { item ->
            item.weight?.let { weight ->
                constraintSet.setVerticalWeight(item.viewId, weight)
            }
        }
    }

    private fun applyItemConstraints(
        constraintSet: ConstraintSet,
        constraints: Map<String, ConstraintItemSpec>,
        helperReferenceIds: Map<String, Int>,
    ) {
        val environment = requireUiEnvironment()
        constraints.forEach { (referenceId, item) ->
            val viewId = referenceIdToViewId[referenceId] ?: helperReferenceIds[referenceId]
            if (viewId == null) {
                warnOnce("Constraint item '$referenceId' has no matching child/helper layoutId.")
                return@forEach
            }
            constraintSet.constrainWidth(viewId, item.width.toLayoutParam(environment))
            constraintSet.constrainHeight(viewId, item.height.toLayoutParam(environment))
            item.widthMin?.let { minWidth ->
                constraintSet.constrainMinWidth(viewId, environment.roundToPx(minWidth))
            }
            item.widthMax?.let { maxWidth ->
                constraintSet.constrainMaxWidth(viewId, environment.roundToPx(maxWidth))
            }
            item.widthPercent?.let { percent ->
                constraintSet.constrainPercentWidth(viewId, percent.coerceIn(0f, 1f))
            }
            item.heightMin?.let { minHeight ->
                constraintSet.constrainMinHeight(viewId, environment.roundToPx(minHeight))
            }
            item.heightMax?.let { maxHeight ->
                constraintSet.constrainMaxHeight(viewId, environment.roundToPx(maxHeight))
            }
            item.heightPercent?.let { percent ->
                constraintSet.constrainPercentHeight(viewId, percent.coerceIn(0f, 1f))
            }
            constraintSet.constrainedWidth(viewId, item.constrainedWidth)
            constraintSet.constrainedHeight(viewId, item.constrainedHeight)
            item.start?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.Start,
                helperReferenceIds = helperReferenceIds,
                referenceId = referenceId,
            )
            item.end?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.End,
                helperReferenceIds = helperReferenceIds,
                referenceId = referenceId,
            )
            item.top?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.Top,
                helperReferenceIds = helperReferenceIds,
                referenceId = referenceId,
            )
            item.bottom?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.Bottom,
                helperReferenceIds = helperReferenceIds,
                referenceId = referenceId,
            )
            applyBaselineConstraints(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                referenceId = referenceId,
                item = item,
                helperReferenceIds = helperReferenceIds,
            )
            item.circle?.let { circle ->
                val targetId = referenceIdToViewId[circle.targetId] ?: helperReferenceIds[circle.targetId]
                if (targetId == null) {
                    warnOnce("Constraint item '$referenceId' circle target '${circle.targetId}' was not found.")
                } else {
                    constraintSet.constrainCircle(
                        viewId,
                        targetId,
                        environment.roundToPx(circle.radius),
                        circle.angle,
                    )
                }
            }
            item.horizontalBias?.let { bias -> constraintSet.setHorizontalBias(viewId, bias) }
            item.verticalBias?.let { bias -> constraintSet.setVerticalBias(viewId, bias) }
            item.dimensionRatio?.let { ratio -> constraintSet.setDimensionRatio(viewId, ratio) }
        }
    }

    private fun applyBaselineConstraints(
        constraintSet: ConstraintSet,
        sourceViewId: Int,
        referenceId: String,
        item: ConstraintItemSpec,
        helperReferenceIds: Map<String, Int>,
    ) {
        val baselineLinks = listOfNotNull(
            item.baseline?.let { target ->
                ConstraintAnchorLink(
                    target = target,
                )
            },
            item.baselineToTop,
            item.baselineToBottom,
        )
        if (baselineLinks.size > 1) {
            warnOnce(
                "Constraint item '$referenceId' declares multiple baseline links. " +
                    "Only the first declaration is applied.",
            )
        }
        val selected = baselineLinks.firstOrNull() ?: return
        selected.applyTo(
            constraintSet = constraintSet,
            sourceViewId = sourceViewId,
            sourceAnchor = ConstraintAnchor.Baseline,
            helperReferenceIds = helperReferenceIds,
            referenceId = referenceId,
        )
    }

    private fun ConstraintAnchorLink.applyTo(
        constraintSet: ConstraintSet,
        sourceViewId: Int,
        sourceAnchor: ConstraintAnchor,
        helperReferenceIds: Map<String, Int>,
        referenceId: String,
    ) {
        val targetId = resolveTargetId(
            target = target,
            helperReferenceIds = helperReferenceIds,
        )
        if (targetId == null) {
            warnOnce("Constraint item '$referenceId' target '${target.id}' was not found.")
            return
        }
        val sourceSide = sourceAnchor.toConstraintSetSide()
        val targetSide = target.anchor.toConstraintSetSide()
        if (
            sourceAnchor == ConstraintAnchor.Baseline &&
            target.anchor !in setOf(
                ConstraintAnchor.Baseline,
                ConstraintAnchor.Top,
                ConstraintAnchor.Bottom,
            )
        ) {
            warnOnce("Constraint item '$referenceId' baseline can only connect to baseline/top/bottom.")
            return
        }
        if (sourceAnchor != ConstraintAnchor.Baseline && target.anchor == ConstraintAnchor.Baseline) {
            warnOnce("Constraint item '$referenceId' non-baseline anchors cannot connect to baseline.")
            return
        }
        constraintSet.connect(
            sourceViewId,
            sourceSide,
            targetId,
            targetSide,
            requireUiEnvironment().roundToPx(margin),
        )
        goneMargin?.let { marginValue ->
            constraintSet.setGoneMargin(
                sourceViewId,
                sourceSide,
                requireUiEnvironment().roundToPx(marginValue),
            )
        }
    }

    private fun resolveTargetId(
        target: ConstraintAnchorTarget,
        helperReferenceIds: Map<String, Int>,
    ): Int? {
        val targetRef = target.id ?: return ConstraintSet.PARENT_ID
        return referenceIdToViewId[targetRef] ?: helperReferenceIds[targetRef]
    }

    private fun resolveReferenceId(
        referenceId: String,
        helperReferenceIds: Map<String, Int>,
        warningPrefix: String,
    ): Int? {
        val target = referenceIdToViewId[referenceId] ?: helperReferenceIds[referenceId]
        if (target == null) {
            warnOnce("$warningPrefix references missing id '$referenceId'.")
        }
        return target
    }

    private fun hasCircularDependency(constraints: Map<String, ConstraintItemSpec>): Boolean {
        val graph = mutableMapOf<String, MutableSet<String>>()
        constraints.forEach { (sourceId, spec) ->
            val dependencies = mutableSetOf<String>()
            listOfNotNull(
                spec.start?.target?.id,
                spec.end?.target?.id,
                spec.top?.target?.id,
                spec.bottom?.target?.id,
                spec.baseline?.id,
                spec.baselineToTop?.target?.id,
                spec.baselineToBottom?.target?.id,
                spec.circle?.targetId,
            ).forEach { targetId ->
                if (constraints.containsKey(targetId)) {
                    dependencies += targetId
                }
            }
            graph[sourceId] = dependencies
        }
        val visitState = mutableMapOf<String, Int>() // 0=unvisited, 1=visiting, 2=visited
        fun dfs(node: String): Boolean {
            when (visitState[node]) {
                1 -> return true
                2 -> return false
            }
            visitState[node] = 1
            for (next in graph[node].orEmpty()) {
                if (dfs(next)) {
                    return true
                }
            }
            visitState[node] = 2
            return false
        }
        return graph.keys.any { node -> dfs(node) }
    }

    private fun ConstraintAnchor.toConstraintSetSide(): Int {
        return when (this) {
            ConstraintAnchor.Start -> ConstraintSet.START
            ConstraintAnchor.End -> ConstraintSet.END
            ConstraintAnchor.Top -> ConstraintSet.TOP
            ConstraintAnchor.Bottom -> ConstraintSet.BOTTOM
            ConstraintAnchor.Baseline -> ConstraintSet.BASELINE
        }
    }

    private fun ConstraintDimension.toLayoutParam(environment: UiEnvironmentValues): Int {
        return when (this) {
            ConstraintDimension.WrapContent -> LayoutParams.WRAP_CONTENT
            ConstraintDimension.FillToConstraints -> 0
            ConstraintDimension.MatchParent -> LayoutParams.MATCH_PARENT
            is ConstraintDimension.Fixed -> environment.roundToPx(value)
        }
    }

    private fun ConstraintBarrierDirection.toConstraintSetDirection(): Int {
        return when (this) {
            ConstraintBarrierDirection.Start -> ConstraintSet.START
            ConstraintBarrierDirection.End -> ConstraintSet.END
            ConstraintBarrierDirection.Top -> ConstraintSet.TOP
            ConstraintBarrierDirection.Bottom -> ConstraintSet.BOTTOM
        }
    }

    private fun ConstraintChainStyle.toConstraintSetChainStyle(): Int {
        return when (this) {
            ConstraintChainStyle.Spread -> ConstraintSet.CHAIN_SPREAD
            ConstraintChainStyle.SpreadInside -> ConstraintSet.CHAIN_SPREAD_INSIDE
            ConstraintChainStyle.Packed -> ConstraintSet.CHAIN_PACKED
        }
    }

    private fun ConstraintFlowOrientation.toFlowOrientation(): Int {
        return when (this) {
            ConstraintFlowOrientation.Horizontal -> Flow.HORIZONTAL
            ConstraintFlowOrientation.Vertical -> Flow.VERTICAL
        }
    }

    private fun ConstraintFlowWrapMode.toFlowWrapMode(): Int {
        return when (this) {
            ConstraintFlowWrapMode.None -> Flow.WRAP_NONE
            ConstraintFlowWrapMode.Chain -> Flow.WRAP_CHAIN
            ConstraintFlowWrapMode.Aligned -> Flow.WRAP_ALIGNED
        }
    }

    private fun ConstraintFlowHorizontalAlign.toFlowHorizontalAlign(): Int {
        return when (this) {
            ConstraintFlowHorizontalAlign.Start -> Flow.HORIZONTAL_ALIGN_START
            ConstraintFlowHorizontalAlign.End -> Flow.HORIZONTAL_ALIGN_END
            ConstraintFlowHorizontalAlign.Center -> Flow.HORIZONTAL_ALIGN_CENTER
        }
    }

    private fun ConstraintFlowVerticalAlign.toFlowVerticalAlign(): Int {
        return when (this) {
            ConstraintFlowVerticalAlign.Top -> Flow.VERTICAL_ALIGN_TOP
            ConstraintFlowVerticalAlign.Bottom -> Flow.VERTICAL_ALIGN_BOTTOM
            ConstraintFlowVerticalAlign.Center -> Flow.VERTICAL_ALIGN_CENTER
            ConstraintFlowVerticalAlign.Baseline -> Flow.VERTICAL_ALIGN_BASELINE
        }
    }

    private fun ConstraintHelperVisibility.toViewVisibility(): Int {
        return when (this) {
            ConstraintHelperVisibility.Visible -> View.VISIBLE
            ConstraintHelperVisibility.Invisible -> View.INVISIBLE
            ConstraintHelperVisibility.Gone -> View.GONE
        }
    }

    private fun warnOnce(message: String) {
        if (emittedWarnings.add(message)) {
            Log.w(WARNING_TAG, message)
        }
    }
}

private class SafeLayer(
    context: Context,
) : Layer(context) {
    override fun updatePostLayout(container: ConstraintLayout) {
        val referencedIds = referencedIds
        if (referencedIds.isEmpty()) {
            return
        }
        val validReferencedIds = referencedIds.filter { id ->
            container.getViewById(id) != null
        }.toIntArray()
        if (validReferencedIds.size != referencedIds.size) {
            setReferencedIds(validReferencedIds)
        }
        if (validReferencedIds.isEmpty()) {
            return
        }
        try {
            super.updatePostLayout(container)
        } catch (error: NullPointerException) {
            Log.w(
                "UIConstraintLayout",
                "SafeLayer skipped updatePostLayout due to transient null referenced view.",
                error,
            )
        }
    }
}
