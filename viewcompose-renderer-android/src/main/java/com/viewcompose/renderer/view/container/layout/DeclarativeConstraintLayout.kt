package com.viewcompose.renderer.view.container

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.helper.widget.Layer
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline
import androidx.constraintlayout.widget.Group
import androidx.constraintlayout.widget.Placeholder
import com.viewcompose.renderer.R
import com.viewcompose.renderer.modifier.ResolvedModifiers
import com.viewcompose.renderer.view.requireUiEnvironment
import com.viewcompose.renderer.view.roundToPx
import com.viewcompose.renderer.view.toPx
import com.viewcompose.renderer.view.tree.LayoutPassTracker
import com.viewcompose.renderer.decoration.DecorationChildDrawingOrder
import com.viewcompose.renderer.decoration.DecorationDrawingOrderContainer
import com.viewcompose.renderer.decoration.ViewDecorationDrawing
import com.viewcompose.ui.environment.UiEnvironmentValues
import com.viewcompose.ui.node.spec.ConstraintAnchor
import com.viewcompose.ui.node.spec.ConstraintAnchorLink
import com.viewcompose.ui.node.spec.ConstraintAnchorTarget
import com.viewcompose.ui.node.spec.ConstraintBarrierDirection
import com.viewcompose.ui.node.spec.ConstraintBarrierSpec
import com.viewcompose.ui.node.spec.ConstraintChainOrientation
import com.viewcompose.ui.node.spec.ConstraintChainSpec
import com.viewcompose.ui.node.spec.ConstraintChainStyle
import com.viewcompose.ui.node.spec.ConstraintCircularFlowSpec
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
import com.viewcompose.ui.node.spec.ConstraintMatchMode
import com.viewcompose.ui.node.spec.ConstraintPlaceholderSpec
import com.viewcompose.ui.node.spec.ConstraintRatio
import com.viewcompose.ui.node.spec.ConstraintRatioSide
import com.viewcompose.ui.node.spec.ConstraintSetSpec
import com.viewcompose.ui.node.spec.ConstraintWrapBehavior

/** Android ConstraintLayout container for the ConstraintLayout DSL. */
internal class DeclarativeConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ConstraintLayout(context, attrs), DecorationDrawingOrderContainer {
    private val decorationDrawing = ViewDecorationDrawing(this)

    companion object {
        private const val WARNING_TAG = "UIConstraintLayout"
        private const val MAX_DIAGNOSTIC_KEYS = 64
    }

    var inlineHelpersSpec: ConstraintHelpersSpec = ConstraintHelpersSpec()
        set(value) {
            if (field == value) return
            field = value
            requestConstraintRebuild(ConstraintRebuildReason.ScalarInput)
        }

    var decoupledConstraintSetSpec: ConstraintSetSpec? = null
        set(value) {
            if (field == value) return
            field = value
            requestConstraintRebuild(ConstraintRebuildReason.ScalarInput)
        }

    private val referenceIdToViewId = mutableMapOf<String, Int>()
    private val helperIdToViewId = mutableMapOf<String, Int>()
    private val helperViews = mutableMapOf<String, View>()
    private val emittedDiagnostics = LinkedHashSet<ConstraintDiagnosticKey>()
    private var pendingConstraintRebuild = false
    private var pendingConstraintRebuildReasons = 0
    private var mutatingHelperViews = false
    private var reconciliationMetrics: ConstraintReconciliationMetrics? = null
    private var acceptedGraph: ResolvedConstraintGraph? = null
    private var acceptedEnvironment: UiEnvironmentValues? = null
    private var acceptedDecoupledConstraintSetSpec: ConstraintSetSpec? = null
    private var acceptedInlineHelpersSpec: ConstraintHelpersSpec? = null
    private var acceptedContentLayoutParams: Map<View, ViewGroup.LayoutParams?> = emptyMap()
    private var acceptedHelperRuntimeBase: Map<View, HelperRuntimeSnapshot> = emptyMap()
    private var acceptedRevision: Long = 0L
    private var attemptedRevision: Long = 0L
    private var lastRejection: ConstraintGraphRejection? = null
    private var pendingLayerTransformListener: ViewTreeObserver.OnPreDrawListener? = null
    private val rebuildRunnable = Runnable {
        if (!pendingConstraintRebuild) {
            return@Runnable
        }
        val rebuildReasons = pendingConstraintRebuildReasons
        pendingConstraintRebuild = false
        pendingConstraintRebuildReasons = 0
        applyConstraintsInternal(rebuildReasons)
    }

    private data class ChainResolvedItem(
        val viewId: Int,
        val weight: Float?,
    )

    private data class ConstraintDiagnosticKey(
        val revision: Long,
        val reason: ConstraintGraphRejectionReason,
        val identity: String?,
    )

    private data class ViewCommitSnapshot(
        val view: View,
        val childIndex: Int,
        val id: Int,
        val layoutParams: ViewGroup.LayoutParams?,
        val visibility: Int,
        val alpha: Float,
        val elevation: Float,
        val rotation: Float,
        val rotationX: Float,
        val rotationY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val translationX: Float,
        val translationY: Float,
        val translationZ: Float,
        val pivotX: Float,
        val pivotY: Float,
        val importantForAccessibility: Int,
        val contentDescription: CharSequence?,
    )

    /** Runtime properties owned by Group, Layer, or Placeholder while a graph is accepted. */
    private data class HelperRuntimeSnapshot(
        val view: View,
        val modifierSignature: RuntimeModifierSignature,
        val visibility: Int,
        val elevation: Float,
        val rotation: Float,
        val rotationX: Float,
        val rotationY: Float,
        val scaleX: Float,
        val scaleY: Float,
        val translationX: Float,
        val translationY: Float,
        val translationZ: Float,
        val pivotX: Float,
        val pivotY: Float,
    )

    /** Modifier subset whose change causes the binder to rewrite every helper-owned View field. */
    private data class RuntimeModifierSignature(
        val alpha: Any?,
        val visibility: Any?,
        val offset: Any?,
        val relativeOffset: Any?,
        val zIndex: Any?,
        val elevation: Any?,
        val graphicsLayer: Any?,
        val layoutId: Any?,
        val constraint: Any?,
    )

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
        DecorationChildDrawingOrder.getChildDrawingOrder(this, childCount, drawingPosition)

    override fun setDecorationDrawingOrderEnabled(enabled: Boolean) {
        isChildrenDrawingOrderEnabled = enabled
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

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long,
    ): Boolean {
        if (!decorationDrawing.hasDecoratedChildren) {
            return super.drawChild(canvas, child, drawingTime)
        }
        val decoration = decorationDrawing.decorationOrNull(child)
            ?: return super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawBehindChild(canvas, child, decoration)
        val drawn = super.drawChild(canvas, child, drawingTime)
        decorationDrawing.drawOverChild(canvas, child, decoration)
        return drawn
    }

    override fun onViewAdded(child: View) {
        if (!mutatingHelperViews && child.id == View.NO_ID) {
            // ConstraintLayout indexes child IDs inside super.onViewAdded. Assigning after that hook
            // leaves Barrier and other helper lookups unable to resolve an otherwise valid child.
            child.id = View.generateViewId()
        }
        super.onViewAdded(child)
        DecorationChildDrawingOrder.onViewAdded(this, child)
        decorationDrawing.onViewAdded(child)
        if (!mutatingHelperViews) {
            requestConstraintRebuild(ConstraintRebuildReason.TopologyInput)
        }
    }

    override fun onViewRemoved(child: View) {
        decorationDrawing.onViewRemoved(child)
        super.onViewRemoved(child)
        DecorationChildDrawingOrder.onViewRemoved(this, child)
        if (!mutatingHelperViews) {
            requestConstraintRebuild(ConstraintRebuildReason.TopologyInput)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (pendingConstraintRebuild) {
            // Detach removes the posted callback while preserving the pending bit. Re-post it on
            // the new attachment so an update requested during the previous lifecycle is not lost.
            pendingConstraintRebuild = false
            requestConstraintRebuild()
        }
        val graph = acceptedGraph ?: return
        val environment = acceptedEnvironment ?: return
        scheduleLayerTransforms(graph.helpers.layers, environment, acceptedRevision)
    }

    override fun onDetachedFromWindow() {
        cancelPendingLayerTransforms()
        removeCallbacks(rebuildRunnable)
        super.onDetachedFromWindow()
    }

    fun requestConstraintRebuild(
        reason: ConstraintRebuildReason = ConstraintRebuildReason.Explicit,
    ) {
        pendingConstraintRebuildReasons = pendingConstraintRebuildReasons or reason.mask
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
        val rebuildReasons = pendingConstraintRebuildReasons
        pendingConstraintRebuild = false
        pendingConstraintRebuildReasons = 0
        applyConstraintsInternal(rebuildReasons)
    }

    private fun applyConstraintsInternal(rebuildReasons: Int) {
        reconciliationMetrics?.recordEnvironmentResolution()
        val environment = requireUiEnvironment()
        if (matchesAcceptedInputs(environment)) {
            val updateClass = if (
                rebuildReasons == ConstraintRebuildReason.ContentOnly.mask
            ) {
                ConstraintReconciliationClass.ContentOnly
            } else {
                ConstraintReconciliationClass.NoOp
            }
            reconciliationMetrics?.recordClassification(updateClass)
            return
        }
        attemptedRevision += 1L
        val candidateRevision = attemptedRevision
        reconciliationMetrics?.recordGraphCompilation()
        reconciliationMetrics?.recordAdapterAllocationBatch()
        val compilation = ConstraintGraphCompiler.compile(
            contentBindings = collectContentBindings(),
            decoupled = decoupledConstraintSetSpec,
            inlineHelpers = inlineHelpersSpec,
        )
        val graph = when (compilation) {
            is ConstraintGraphCompilation.Accepted -> compilation.graph
            is ConstraintGraphCompilation.Rejected -> {
                emitRejection(candidateRevision, compilation.rejection)
                return
            }
        }
        val updateClass = classifyReconciliation(graph, environment)
        reconciliationMetrics?.recordClassification(updateClass)
        val helperRuntimeBaseChanged = refreshAcceptedHelperRuntimeBaseFromBinder()
        val preserveAcceptedHelpers =
            updateClass == ConstraintReconciliationClass.Scalar &&
                graph.helpers == acceptedGraph?.helpers &&
                !helperRuntimeBaseChanged
        reconciliationMetrics?.recordAdapterAllocationBatch()
        val committedSnapshots = captureCommitSnapshots()
        val previousGraph = acceptedGraph
        val previousEnvironment = acceptedEnvironment
        val previousHelperRuntimeBase = acceptedHelperRuntimeBase
        val previousHelperViews = helperViews.toMap()
        val previousHelperIds = helperIdToViewId.toMap()
        val contentViewIds = resolveContentViewIds(graph)
        var rollbackSnapshots = committedSnapshots
        try {
            val releasedContentOverlay = if (preserveAcceptedHelpers) {
                false
            } else {
                releaseAcceptedHelperEffects()
            }
            rollbackSnapshots = if (releasedContentOverlay) {
                captureCommitSnapshots()
            } else {
                committedSnapshots
            }
            val helperPlan = stageHelperViews(graph)
            // Stale helpers must not participate in ConstraintSet.applyTo. In particular, a
            // removed Layer has no references and AndroidX cannot apply default transforms to it.
            pruneInactiveHelperViews(helperPlan.activeKeys)
            val resolvedReferenceIds = contentViewIds + helperPlan.referenceIds
            applyContentViewIds(graph, contentViewIds)
            if (!preserveAcceptedHelpers) {
                stageLayerReferences(graph.helpers.layers, resolvedReferenceIds)
            }
            val constraintSet = ConstraintSet()
            seedHelperConstraints(constraintSet, graph)
            applyChains(
                constraintSet = constraintSet,
                chains = graph.helpers.chains,
                referenceIds = resolvedReferenceIds,
                environment = environment,
            )
            applyGrids(
                constraintSet = constraintSet,
                grids = graph.resolvedGrids,
                referenceIds = resolvedReferenceIds,
                environment = environment,
            )
            applyItemConstraints(
                constraintSet = constraintSet,
                graph = graph,
                referenceIds = resolvedReferenceIds,
                environment = environment,
            )
            applyCircularFlows(
                constraintSet = constraintSet,
                circularFlows = graph.helpers.circularFlows,
                referenceIds = resolvedReferenceIds,
                environment = environment,
            )
            reconciliationMetrics?.recordNativeCommit()
            constraintSet.applyTo(this)
            restoreConstraintSetOmittedMargins(graph, resolvedReferenceIds, environment)
            restoreRuntimeProperties(rollbackSnapshots)
            val helperRuntimeBase = if (preserveAcceptedHelpers) {
                acceptedHelperRuntimeBase
            } else {
                captureHelperRuntimeBase(graph)
            }
            if (!preserveAcceptedHelpers) {
                configureHelpers(
                    graph = graph,
                    referenceIds = resolvedReferenceIds,
                    environment = environment,
                    revision = candidateRevision,
                )
            }
            referenceIdToViewId.clear()
            referenceIdToViewId.putAll(contentViewIds)
            helperIdToViewId.keys.retainAll(helperPlan.activeKeys)
            acceptedGraph = graph
            acceptedEnvironment = environment
            acceptedDecoupledConstraintSetSpec = decoupledConstraintSetSpec
            acceptedInlineHelpersSpec = inlineHelpersSpec
            acceptedContentLayoutParams = captureAcceptedContentLayoutParams(graph)
            acceptedHelperRuntimeBase = helperRuntimeBase
            acceptedRevision = candidateRevision
            lastRejection = null
            emittedDiagnostics.removeAll { key -> key.revision < acceptedRevision }
            if (updateClass == ConstraintReconciliationClass.Topology) {
                reconciliationMetrics?.recordTopologyPublish()
            }
            requestAdapterLayout()
        } catch (error: Throwable) {
            reconciliationMetrics?.recordRollback()
            cancelPendingLayerTransforms()
            restoreHelperRegistry(previousHelperViews, previousHelperIds, committedSnapshots)
            restoreCommitSnapshots(
                snapshots = rollbackSnapshots,
                helpersRestoredSeparately = previousHelperViews.values.toSet(),
            )
            acceptedGraph = previousGraph
            acceptedEnvironment = previousEnvironment
            acceptedContentLayoutParams = captureAcceptedContentLayoutParams(previousGraph)
            acceptedHelperRuntimeBase = previousHelperRuntimeBase
            restoreAcceptedHelperConfiguration()
            val failureOrigin = error.stackTrace.firstOrNull()?.let { frame ->
                " at ${frame.className}.${frame.methodName}:${frame.lineNumber}"
            }.orEmpty()
            emitRejection(
                candidateRevision,
                ConstraintGraphRejection(
                    reason = ConstraintGraphRejectionReason.NativeCommit,
                    identity = null,
                    detail = "Constraint graph native commit failed: " +
                        "${error.message ?: error::class.java.simpleName}$failureOrigin",
                ),
            )
        }
    }

    private fun requestAdapterLayout() {
        reconciliationMetrics?.recordAdapterLayoutRequest()
        requestLayout()
    }

    private fun classifyReconciliation(
        graph: ResolvedConstraintGraph,
        environment: UiEnvironmentValues,
    ): ConstraintReconciliationClass {
        val previous = acceptedGraph ?: return ConstraintReconciliationClass.Topology
        if (!graph.hasSameTopologyAs(previous)) return ConstraintReconciliationClass.Topology
        return if (graph == previous && environment != acceptedEnvironment) {
            ConstraintReconciliationClass.Environment
        } else {
            ConstraintReconciliationClass.Scalar
        }
    }

    /** Checks accepted inputs without constructing a candidate graph or adapter scratch objects. */
    private fun matchesAcceptedInputs(environment: UiEnvironmentValues): Boolean {
        val graph = acceptedGraph ?: return false
        if (environment != acceptedEnvironment) return false
        if (decoupledConstraintSetSpec != acceptedDecoupledConstraintSetSpec) return false
        if (inlineHelpersSpec != acceptedInlineHelpersSpec) return false
        if (acceptedContentLayoutParams.size != graph.contentBindings.size) return false
        if (helperViews.keys != graph.expectedNativeHelperKeys()) return false
        helperViews.forEach { (key, helper) ->
            if (helper.parent !== this) return false
            if (helperIdToViewId[key] != helper.id) return false
        }

        var bindingIndex = 0
        for (childIndex in 0 until childCount) {
            val child = getChildAt(childIndex)
            if (helperViews.containsValue(child)) continue
            if (bindingIndex >= graph.contentBindings.size) return false
            val binding = graph.contentBindings[bindingIndex++]
            if (binding.nativeIdentity !== child) return false
            if (binding.referenceId != child.getTag(R.id.viewcompose_constraint_layout_id)) return false
            if (binding.inlineSpec != child.getTag(R.id.viewcompose_constraint_item_spec)) return false
            if (acceptedContentLayoutParams[child] !== child.layoutParams) return false
            val referenceId = binding.referenceId ?: return false
            if (referenceIdToViewId[referenceId] != child.id) return false
        }
        return bindingIndex == graph.contentBindings.size
    }

    private fun captureAcceptedContentLayoutParams(
        graph: ResolvedConstraintGraph?,
    ): Map<View, ViewGroup.LayoutParams?> {
        if (graph == null) return emptyMap()
        return graph.contentBindings.associate { binding ->
            val view = binding.requireView()
            view to view.layoutParams
        }
    }

    private fun collectContentBindings(): List<ConstraintContentBinding> {
        val bindings = ArrayList<ConstraintContentBinding>(childCount)
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (helperViews.containsValue(child)) {
                continue
            }
            val referenceId = child.getTag(R.id.viewcompose_constraint_layout_id) as? String
            val inlineSpec = child.getTag(R.id.viewcompose_constraint_item_spec) as? ConstraintItemSpec
            bindings += ConstraintContentBinding(
                referenceId = referenceId,
                inlineSpec = inlineSpec,
                nativeIdentity = child,
            )
        }
        return bindings
    }

    private fun resolveContentViewIds(graph: ResolvedConstraintGraph): Map<String, Int> {
        return graph.contentById.keys.associateWithTo(linkedMapOf()) { id ->
            val binding = requireNotNull(graph.contentById[id])
            val view = binding.requireView()
            val acceptedBinding = acceptedGraph?.contentById?.get(id)
            if (acceptedBinding?.nativeIdentity === view) {
                referenceIdToViewId[id] ?: view.id
            } else {
                view.id.takeUnless { value -> value == View.NO_ID } ?: View.generateViewId()
            }
        }
    }

    private fun applyContentViewIds(
        graph: ResolvedConstraintGraph,
        contentViewIds: Map<String, Int>,
    ) {
        graph.contentById.forEach { (id, binding) ->
            val targetId = requireNotNull(contentViewIds[id])
            val view = binding.requireView()
            if (view.id != targetId) {
                view.id = targetId
            }
        }
    }

    /**
     * AndroidX 2.2.2 stores baseline and physical gone margins in ConstraintSet.Layout but omits
     * them from Constraint.applyTo(LayoutParams). Repair those fields before the solver measures;
     * resetting absent values also prevents a removed declaration from leaking into the next graph.
     */
    private fun restoreConstraintSetOmittedMargins(
        graph: ResolvedConstraintGraph,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        graph.constrainableIds.forEach { referenceId ->
            val viewId = requireNotNull(referenceIds[referenceId])
            val view = findViewById<View>(viewId) ?: return@forEach
            val params = view.layoutParams as? LayoutParams ?: return@forEach
            val item = graph.constraints[referenceId] ?: ConstraintItemSpec()
            params.baselineMargin = item.baseline?.let { environment.roundToPx(it.margin) } ?: 0
            params.goneBaselineMargin = item.baseline?.goneMargin?.let(environment::roundToPx)
                ?: LayoutParams.GONE_UNSET
            params.goneLeftMargin = item.left?.goneMargin?.let(environment::roundToPx)
                ?: LayoutParams.GONE_UNSET
            params.goneRightMargin = item.right?.goneMargin?.let(environment::roundToPx)
                ?: LayoutParams.GONE_UNSET
        }
    }

    private fun ConstraintContentBinding.requireView(): View {
        return nativeIdentity as? View
            ?: error("Constraint content identity is not an Android View.")
    }

    private fun captureCommitSnapshots(): List<ViewCommitSnapshot> {
        return List(childCount) { childIndex ->
            val view = getChildAt(childIndex)
            ViewCommitSnapshot(
                view = view,
                childIndex = childIndex,
                id = view.id,
                layoutParams = view.layoutParams?.copyForConstraintRollback(),
                visibility = view.visibility,
                alpha = view.alpha,
                elevation = view.elevation,
                rotation = view.rotation,
                rotationX = view.rotationX,
                rotationY = view.rotationY,
                scaleX = view.scaleX,
                scaleY = view.scaleY,
                translationX = view.translationX,
                translationY = view.translationY,
                translationZ = view.translationZ,
                pivotX = view.pivotX,
                pivotY = view.pivotY,
                importantForAccessibility = view.importantForAccessibility,
                contentDescription = view.contentDescription,
            )
        }
    }

    private fun ViewGroup.LayoutParams.copyForConstraintRollback(): ViewGroup.LayoutParams {
        return when (this) {
            is LayoutParams -> LayoutParams(this)
            is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(this)
            else -> ViewGroup.LayoutParams(this)
        }
    }

    private fun restoreRuntimeProperties(snapshots: List<ViewCommitSnapshot>) {
        snapshots.forEach { snapshot ->
            // A stale helper can be pruned before native apply. Restoring its View properties after
            // removal may re-enter helper-specific setters with no references (Layer in particular).
            if (snapshot.view.parent === this) {
                snapshot.restoreRuntimeProperties()
            }
        }
    }

    private fun restoreCommitSnapshots(
        snapshots: List<ViewCommitSnapshot>,
        helpersRestoredSeparately: Set<View>,
    ) {
        snapshots.forEach { snapshot ->
            snapshot.view.id = snapshot.id
            snapshot.layoutParams?.let { params -> snapshot.view.layoutParams = params }
            if (snapshot.view !in helpersRestoredSeparately) {
                snapshot.restoreRuntimeProperties()
            }
        }
        requestAdapterLayout()
    }

    private fun ViewCommitSnapshot.restoreRuntimeProperties() {
        view.visibility = visibility
        view.alpha = alpha
        view.elevation = elevation
        view.rotation = rotation
        view.rotationX = rotationX
        view.rotationY = rotationY
        view.scaleX = scaleX
        view.scaleY = scaleY
        view.translationX = translationX
        view.translationY = translationY
        view.translationZ = translationZ
        view.pivotX = pivotX
        view.pivotY = pivotY
        view.importantForAccessibility = importantForAccessibility
        view.contentDescription = contentDescription
    }

    private fun captureHelperRuntimeBase(
        graph: ResolvedConstraintGraph,
    ): Map<View, HelperRuntimeSnapshot> {
        val referencedContentIds = linkedSetOf<String>()
        graph.helpers.groups.forEach { spec -> referencedContentIds += spec.referencedIds }
        graph.helpers.layers.forEach { spec -> referencedContentIds += spec.referencedIds }
        graph.helpers.placeholders.mapNotNullTo(referencedContentIds) { spec -> spec.contentId }
        return referencedContentIds.mapNotNull { id ->
            val view = graph.contentById[id]?.requireView() ?: return@mapNotNull null
            view to view.captureHelperRuntimeSnapshot()
        }.toMap()
    }

    /** Removes accepted helper overlays before the next graph snapshots its source-of-truth state. */
    private fun releaseAcceptedHelperEffects(): Boolean {
        cancelPendingLayerTransforms()
        acceptedGraph?.helpers?.placeholders?.forEach { spec ->
            reconciliationMetrics?.recordHelperWrite()
            requireHelperView<Placeholder>(NativeConstraintHelperKind.Placeholder, spec.id)
                .setContentId(ConstraintLayout.LayoutParams.UNSET)
        }
        acceptedGraph?.helpers?.groups?.forEach { spec ->
            reconciliationMetrics?.recordHelperWrite()
            requireHelperView<Group>(NativeConstraintHelperKind.Group, spec.id)
                .setReferencedIds(intArrayOf())
        }
        acceptedGraph?.helpers?.layers?.forEach { spec ->
            reconciliationMetrics?.recordHelperWrite()
            requireHelperView<Layer>(NativeConstraintHelperKind.Layer, spec.id)
                .setReferencedIds(intArrayOf())
        }
        acceptedHelperRuntimeBase.values.forEach { snapshot -> snapshot.restore() }
        return acceptedHelperRuntimeBase.isNotEmpty()
    }

    private fun View.captureHelperRuntimeSnapshot(): HelperRuntimeSnapshot {
        return HelperRuntimeSnapshot(
            view = this,
            modifierSignature = runtimeModifierSignature(),
            visibility = visibility,
            elevation = elevation,
            rotation = rotation,
            rotationX = rotationX,
            rotationY = rotationY,
            scaleX = scaleX,
            scaleY = scaleY,
            translationX = translationX,
            translationY = translationY,
            translationZ = translationZ,
            pivotX = pivotX,
            pivotY = pivotY,
        )
    }

    /** Keeps a newly rebound declarative child state when replacing an older helper overlay. */
    private fun refreshAcceptedHelperRuntimeBaseFromBinder(): Boolean {
        val changed = acceptedHelperRuntimeBase.values.any { snapshot ->
            snapshot.modifierSignature != snapshot.view.runtimeModifierSignature()
        }
        if (!changed) return false
        acceptedHelperRuntimeBase = acceptedHelperRuntimeBase.mapValues { (_, snapshot) ->
            if (snapshot.modifierSignature != snapshot.view.runtimeModifierSignature()) {
                snapshot.view.captureHelperRuntimeSnapshot()
            } else {
                snapshot
            }
        }
        return true
    }

    private fun View.runtimeModifierSignature(): RuntimeModifierSignature {
        val resolved = getTag(R.id.viewcompose_resolved_modifiers) as? ResolvedModifiers
        return RuntimeModifierSignature(
            alpha = resolved?.alpha,
            visibility = resolved?.visibility,
            offset = resolved?.offset,
            relativeOffset = resolved?.relativeOffset,
            zIndex = resolved?.zIndex,
            elevation = resolved?.elevation,
            graphicsLayer = resolved?.graphicsLayer,
            layoutId = resolved?.layoutId,
            constraint = resolved?.constraint,
        )
    }

    private fun HelperRuntimeSnapshot.restore() {
        view.visibility = visibility
        view.elevation = elevation
        view.rotation = rotation
        view.rotationX = rotationX
        view.rotationY = rotationY
        view.scaleX = scaleX
        view.scaleY = scaleY
        view.translationX = translationX
        view.translationY = translationY
        view.translationZ = translationZ
        view.pivotX = pivotX
        view.pivotY = pivotY
    }

    private fun restoreHelperRegistry(
        previousViews: Map<String, View>,
        previousIds: Map<String, Int>,
        snapshots: List<ViewCommitSnapshot>,
    ) {
        mutatingHelperViews = true
        try {
            helperViews.filterValues { view -> view !in previousViews.values }.forEach { (_, view) ->
                if (view.parent === this) {
                    reconciliationMetrics?.recordHelperRemove()
                    releaseHelperView(view)
                    removeView(view)
                }
            }
            val previousIndices = snapshots.associate { snapshot -> snapshot.view to snapshot.childIndex }
            previousViews.values.sortedBy { view -> previousIndices[view] ?: Int.MAX_VALUE }.forEach { view ->
                if (view.parent !== this) {
                    addView(view, (previousIndices[view] ?: childCount).coerceAtMost(childCount))
                }
            }
            helperViews.clear()
            helperViews.putAll(previousViews)
            helperIdToViewId.clear()
            helperIdToViewId.putAll(previousIds)
        } finally {
            mutatingHelperViews = false
        }
    }

    private fun restoreAcceptedHelperConfiguration() {
        val graph = acceptedGraph ?: return
        val helperReferences = graph.helperKinds.mapNotNull { (id, kind) ->
            if (kind == NativeConstraintHelperKind.Grid || kind == NativeConstraintHelperKind.CircularFlow) {
                null
            } else {
                id to requireNotNull(helperIdToViewId[helperKey(kind.prefix(), id)])
            }
        }.toMap()
        val references = referenceIdToViewId + helperReferences
        try {
            configureHelpers(
                graph = graph,
                referenceIds = references,
                environment = requireNotNull(acceptedEnvironment),
                revision = acceptedRevision,
            )
        } catch (restoreError: Throwable) {
            Log.e(WARNING_TAG, "Accepted helper graph restore failed.", restoreError)
        }
    }

    private fun emitRejection(
        revision: Long,
        rejection: ConstraintGraphRejection,
    ) {
        lastRejection = rejection
        val key = ConstraintDiagnosticKey(
            revision = revision,
            reason = rejection.reason,
            identity = rejection.identity,
        )
        if (!emittedDiagnostics.add(key)) return
        while (emittedDiagnostics.size > MAX_DIAGNOSTIC_KEYS) {
            emittedDiagnostics.remove(emittedDiagnostics.first())
        }
        Log.w(
            WARNING_TAG,
            "Rejected constraint graph revision=$revision reason=${rejection.reason} " +
                "identity=${rejection.identity ?: "container"}: ${rejection.detail}",
        )
    }

    internal val acceptedRevisionForTest: Long
        get() = acceptedRevision

    internal val managedHelperCountForTest: Int
        get() = helperViews.size

    internal val diagnosticCountForTest: Int
        get() = emittedDiagnostics.size

    internal val hasPendingLayerTransformForTest: Boolean
        get() = pendingLayerTransformListener != null

    internal val lastRejectionForTest: ConstraintGraphRejection?
        get() = lastRejection

    internal val acceptedTopologyFingerprintForTest: Long?
        get() = acceptedGraph?.topologyFingerprint

    internal val acceptedScalarFingerprintForTest: Long?
        get() = acceptedGraph?.scalarFingerprint

    internal val acceptedGraphForTest: ResolvedConstraintGraph?
        get() = acceptedGraph

    internal fun startReconciliationMetricsForTest(): ConstraintReconciliationMetrics {
        return ConstraintReconciliationMetrics().also { metrics ->
            reconciliationMetrics = metrics
        }
    }

    internal fun stopReconciliationMetricsForTest() {
        reconciliationMetrics = null
    }

    private data class HelperCommitPlan(
        val referenceIds: Map<String, Int>,
        val activeKeys: Set<String>,
    )

    private fun ResolvedConstraintGraph.expectedNativeHelperKeys(): Set<String> = buildSet {
        helperKinds.forEach { (id, kind) ->
            if (kind != NativeConstraintHelperKind.Grid && kind != NativeConstraintHelperKind.CircularFlow) {
                add(helperKey(kind.prefix(), id))
            }
        }
        resolvedGrids.forEach { grid ->
            repeat(grid.rows) { row -> add(gridRowKey(grid.spec.id, row)) }
            repeat(grid.columns) { column -> add(gridColumnKey(grid.spec.id, column)) }
        }
    }

    private fun gridRowKey(gridId: String, row: Int): String = "grid:$gridId:row:$row"

    private fun gridColumnKey(gridId: String, column: Int): String = "grid:$gridId:column:$column"

    private fun stageHelperViews(graph: ResolvedConstraintGraph): HelperCommitPlan {
        val referenceIds = linkedMapOf<String, Int>()
        val activeKeys = linkedSetOf<String>()
        graph.helperKinds.forEach { (id, kind) ->
            if (kind == NativeConstraintHelperKind.Grid || kind == NativeConstraintHelperKind.CircularFlow) {
                return@forEach
            }
            val prefix = kind.prefix()
            val key = helperKey(prefix, id)
            val viewId = helperIdToViewId.getOrPut(key) { View.generateViewId() }
            val helper = when (kind) {
                NativeConstraintHelperKind.Guideline -> ensureHelperView(
                    key,
                    viewId,
                    Guideline::class.java,
                ) { Guideline(context) }

                NativeConstraintHelperKind.Barrier -> ensureHelperView(
                    key,
                    viewId,
                    Barrier::class.java,
                ) { Barrier(context) }

                NativeConstraintHelperKind.Flow -> ensureHelperView(
                    key,
                    viewId,
                    Flow::class.java,
                ) { Flow(context) }

                NativeConstraintHelperKind.Group -> ensureHelperView(
                    key,
                    viewId,
                    Group::class.java,
                ) { Group(context) }

                NativeConstraintHelperKind.Layer -> ensureHelperView(
                    key,
                    viewId,
                    SafeLayer::class.java,
                ) { SafeLayer(context) }

                NativeConstraintHelperKind.Placeholder -> ensureHelperView(
                    key,
                    viewId,
                    Placeholder::class.java,
                ) { Placeholder(context) }

                NativeConstraintHelperKind.Grid,
                NativeConstraintHelperKind.CircularFlow,
                -> error("Identity-only helper $kind must not create a semantic native View.")
            }
            // Retained programmatic helpers can keep their previously resolved direction on older
            // Android releases, which prevents AndroidX from resolving logical constraints in RTL.
            if (helper.layoutDirection != layoutDirection) {
                helper.layoutDirection = layoutDirection
            }
            activeKeys += key
            referenceIds[id] = viewId
        }
        graph.resolvedGrids.forEach { grid ->
            // AndroidX Grid accepts unchecked span/skip strings and creates internal structures
            // outside this registry. Zero-thickness row/column proxies keep the typed expansion,
            // generated identities, replacement, and rollback under the same transaction owner.
            repeat(grid.rows) { row ->
                val key = gridRowKey(grid.spec.id, row)
                val viewId = helperIdToViewId.getOrPut(key) { View.generateViewId() }
                val proxy = ensureHelperView(key, viewId, View::class.java) { View(context) }
                proxy.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                activeKeys += key
            }
            repeat(grid.columns) { column ->
                val key = gridColumnKey(grid.spec.id, column)
                val viewId = helperIdToViewId.getOrPut(key) { View.generateViewId() }
                val proxy = ensureHelperView(key, viewId, View::class.java) { View(context) }
                proxy.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                activeKeys += key
            }
        }
        return HelperCommitPlan(
            referenceIds = referenceIds.toMap(),
            activeKeys = activeKeys,
        )
    }

    private fun configureHelpers(
        graph: ResolvedConstraintGraph,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
        revision: Long,
    ) {
        reconciliationMetrics?.recordHelperWrite(
            graph.helpers.guidelines.size +
                graph.helpers.barriers.size +
                graph.helpers.flows.size +
                graph.helpers.groups.size +
                graph.helpers.layers.size +
                graph.helpers.placeholders.size,
        )
        graph.helpers.guidelines.forEach { spec -> applyGuidelineHelper(spec, environment) }
        graph.helpers.barriers.forEach { spec -> applyBarrierHelper(spec, referenceIds, environment) }
        graph.helpers.flows.forEach { spec -> applyFlowHelper(spec, referenceIds, environment) }
        graph.helpers.groups.forEach { spec -> applyGroupHelper(spec, referenceIds, environment) }
        graph.helpers.layers.forEach { spec ->
            applyLayerHelper(spec, referenceIds, environment)
        }
        graph.helpers.placeholders.forEach { spec -> applyPlaceholderHelper(spec, referenceIds) }
        scheduleLayerTransforms(graph.helpers.layers, environment, revision)
    }

    /**
     * AndroidX applies default transform fields while applying a ConstraintSet. A newly attached
     * Layer must already have references at that point or its internal center calculation indexes
     * an empty array. Runtime properties remain configured after native apply and snapshot restore.
     */
    private fun stageLayerReferences(
        layers: List<ConstraintLayerSpec>,
        referenceIds: Map<String, Int>,
    ) {
        layers.forEach { spec ->
            reconciliationMetrics?.recordHelperWrite()
            val layer = requireHelperView<Layer>(NativeConstraintHelperKind.Layer, spec.id)
            val resolvedIds = resolveReferencedIds(spec.referencedIds, referenceIds)
            check(resolvedIds.isNotEmpty()) {
                "Layer '${spec.id}' reached native staging without references."
            }
            layer.setReferencedIds(resolvedIds)
        }
    }

    private fun seedHelperConstraints(
        constraintSet: ConstraintSet,
        graph: ResolvedConstraintGraph,
    ) {
        graph.helperKinds.forEach { (id, kind) ->
            if (kind == NativeConstraintHelperKind.Grid || kind == NativeConstraintHelperKind.CircularFlow) {
                return@forEach
            }
            val helperId = requireNotNull(helperIdToViewId[helperKey(kind.prefix(), id)])
            val dimension = when (kind) {
                NativeConstraintHelperKind.Flow,
                NativeConstraintHelperKind.Placeholder,
                -> LayoutParams.WRAP_CONTENT

                NativeConstraintHelperKind.Guideline,
                NativeConstraintHelperKind.Barrier,
                NativeConstraintHelperKind.Group,
                NativeConstraintHelperKind.Layer,
                -> 0

                NativeConstraintHelperKind.Grid,
                NativeConstraintHelperKind.CircularFlow,
                -> error("Identity-only helpers are seeded by their declarative expansion.")
            }
            constraintSet.constrainWidth(helperId, dimension)
            constraintSet.constrainHeight(helperId, dimension)
        }
    }

    private fun NativeConstraintHelperKind.prefix(): String = name.lowercase()

    private fun helperKey(
        prefix: String,
        id: String,
    ): String = "$prefix:$id"

    private fun resolveReferencedIds(
        referenceIds: List<String>,
        resolvedReferenceIds: Map<String, Int>,
    ): IntArray {
        return referenceIds.map { referenceId ->
            requireNotNull(resolvedReferenceIds[referenceId]) {
                "Validated reference '$referenceId' disappeared before native commit."
            }
        }.toIntArray()
    }

    private fun applyFlowHelper(
        spec: ConstraintFlowSpec,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        val flowView = requireHelperView<Flow>(NativeConstraintHelperKind.Flow, spec.id)
        val referencedIds = resolveReferencedIds(
            referenceIds = spec.referencedIds,
            resolvedReferenceIds = referenceIds,
        )
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
        spec: ConstraintGroupSpec,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        val groupView = requireHelperView<Group>(NativeConstraintHelperKind.Group, spec.id)
        val referencedIds = resolveReferencedIds(
            referenceIds = spec.referencedIds,
            resolvedReferenceIds = referenceIds,
        )
        groupView.setReferencedIds(referencedIds)
        val visibility = spec.visibility.toViewVisibility()
        val elevation = environment.toPx(spec.elevation)
        groupView.visibility = visibility
        groupView.elevation = elevation
        // Apply in declaration order so the last overlapping group owns the committed value.
        referencedIds.forEach { referencedId ->
            findViewById<View>(referencedId)?.visibility = visibility
        }
    }

    private fun applyLayerHelper(
        spec: ConstraintLayerSpec,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        val layerView = requireHelperView<Layer>(NativeConstraintHelperKind.Layer, spec.id)
        val referencedIds = resolveReferencedIds(
            referenceIds = spec.referencedIds,
            resolvedReferenceIds = referenceIds,
        )
        check(referencedIds.isNotEmpty()) {
            "Layer '${spec.id}' reached native commit without references."
        }
        applyLayerNativeStep(spec, "reference install") {
            layerView.setReferencedIds(referencedIds)
        }
        check(layerView.referencedIds.contentEquals(referencedIds)) {
            "Layer '${spec.id}' did not retain its validated native references."
        }
        applyLayerNativeStep(spec, "visibility install") {
            layerView.visibility = spec.visibility.toViewVisibility()
        }
        applyLayerNativeStep(spec, "elevation install") {
            layerView.elevation = environment.toPx(spec.elevation)
        }
        // Layer setters silently return until AndroidX has recorded their owning container. A
        // helper can be created before attachment or between native layout phases, so establish
        // that public lifecycle state explicitly. Transform setters are deferred until pre-draw:
        // AndroidX calculates the default pivot from laid-out children and can fail before then.
        applyLayerNativeStep(spec, "container initialization") {
            layerView.updatePreDraw(this)
        }
    }

    private inline fun applyLayerNativeStep(
        spec: ConstraintLayerSpec,
        operation: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (error: RuntimeException) {
            throw IllegalStateException("Layer '${spec.id}' $operation failed.", error)
        }
    }

    private fun applyLayerTransformsSafely(
        layerView: Layer,
        spec: ConstraintLayerSpec,
        environment: UiEnvironmentValues,
    ) {
        try {
            layerView.rotation = spec.rotation
            layerView.scaleX = spec.scaleX
            layerView.scaleY = spec.scaleY
            layerView.translationX = environment.toPx(spec.translationX)
            layerView.translationY = environment.toPx(spec.translationY)
            layerView.pivotX = spec.pivotX?.let(environment::toPx) ?: Float.NaN
            layerView.pivotY = spec.pivotY?.let(environment::toPx) ?: Float.NaN
        } catch (error: RuntimeException) {
            throw IllegalStateException("Layer '${spec.id}' transform apply failed.", error)
        }
    }

    private fun applyPlaceholderHelper(
        spec: ConstraintPlaceholderSpec,
        referenceIds: Map<String, Int>,
    ) {
        val placeholderView = requireHelperView<Placeholder>(
            NativeConstraintHelperKind.Placeholder,
            spec.id,
        )
        placeholderView.emptyVisibility = spec.emptyVisibility.toViewVisibility()
        val contentViewId = spec.contentId?.let { contentId ->
            requireNotNull(referenceIds[contentId]) {
                "Validated placeholder content '$contentId' disappeared before native commit."
            }
        } ?: ConstraintLayout.LayoutParams.UNSET
        placeholderView.setContentId(contentViewId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> requireHelperView(
        kind: NativeConstraintHelperKind,
        id: String,
    ): T {
        return requireNotNull(helperViews[helperKey(kind.prefix(), id)] as? T) {
            "Validated $kind helper '$id' disappeared before native commit."
        }
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
            reconciliationMetrics?.recordHelperRemove()
            mutatingHelperViews = true
            try {
                removeView(current)
            } finally {
                mutatingHelperViews = false
            }
        }
        val created = factory()
        reconciliationMetrics?.recordHelperCreate()
        created.id = viewId
        if (created.layoutParams == null) {
            val helperSize = when (created) {
                is Flow,
                is Placeholder,
                -> LayoutParams.WRAP_CONTENT

                else -> 0
            }
            created.layoutParams = LayoutParams(helperSize, helperSize)
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
                    reconciliationMetrics?.recordHelperRemove()
                    releaseHelperView(helperView)
                    removeView(helperView)
                }
            }
        } finally {
            mutatingHelperViews = false
        }
    }

    private fun releaseHelperView(view: View) {
        when (view) {
            is Placeholder -> view.setContentId(ConstraintLayout.LayoutParams.UNSET)
            is androidx.constraintlayout.widget.ConstraintHelper -> view.setReferencedIds(intArrayOf())
        }
    }

    private fun applyGuidelineHelper(
        spec: ConstraintGuidelineSpec,
        environment: UiEnvironmentValues,
    ) {
        val guideline = requireHelperView<Guideline>(NativeConstraintHelperKind.Guideline, spec.id)
        val orientation = when (spec.direction) {
            ConstraintGuidelineDirection.FromStart,
            ConstraintGuidelineDirection.FromEnd,
            ConstraintGuidelineDirection.FromLeft,
            ConstraintGuidelineDirection.FromRight,
            -> ConstraintSet.VERTICAL_GUIDELINE

            ConstraintGuidelineDirection.FromTop,
            ConstraintGuidelineDirection.FromBottom,
            -> ConstraintSet.HORIZONTAL_GUIDELINE
        }
        val params = LayoutParams(0, 0).apply {
            this.orientation = orientation
            guidelineUseRtl = spec.direction == ConstraintGuidelineDirection.FromStart ||
                spec.direction == ConstraintGuidelineDirection.FromEnd
        }
        when (val position = spec.position) {
            is ConstraintGuidelinePosition.Offset -> {
                when (spec.direction) {
                    ConstraintGuidelineDirection.FromStart,
                    ConstraintGuidelineDirection.FromLeft,
                    ConstraintGuidelineDirection.FromTop,
                    -> params.guideBegin = environment.roundToPx(position.value)

                    ConstraintGuidelineDirection.FromEnd,
                    ConstraintGuidelineDirection.FromRight,
                    ConstraintGuidelineDirection.FromBottom,
                    -> params.guideEnd = environment.roundToPx(position.value)
                }
            }

            is ConstraintGuidelinePosition.Fraction -> {
                val percent = when (spec.direction) {
                    ConstraintGuidelineDirection.FromStart,
                    ConstraintGuidelineDirection.FromLeft,
                    ConstraintGuidelineDirection.FromTop,
                    -> position.value

                    ConstraintGuidelineDirection.FromEnd,
                    ConstraintGuidelineDirection.FromRight,
                    ConstraintGuidelineDirection.FromBottom,
                    -> 1f - position.value
                }
                params.guidePercent = percent
            }
        }
        params.validate()
        guideline.layoutParams = params
    }

    private fun applyBarrierHelper(
        spec: ConstraintBarrierSpec,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        val barrier = requireHelperView<Barrier>(NativeConstraintHelperKind.Barrier, spec.id)
        barrier.setType(spec.direction.toBarrierDirection())
        barrier.setMargin(environment.roundToPx(spec.margin))
        barrier.setAllowsGoneWidget(spec.allowsGoneWidgets)
        barrier.setReferencedIds(
            resolveReferencedIds(
                referenceIds = spec.referencedIds,
                resolvedReferenceIds = referenceIds,
            ),
        )
    }

    private fun scheduleLayerTransforms(
        layers: List<ConstraintLayerSpec>,
        environment: UiEnvironmentValues,
        revision: Long,
    ) {
        cancelPendingLayerTransforms()
        if (layers.isEmpty()) return
        lateinit var listener: ViewTreeObserver.OnPreDrawListener
        listener = ViewTreeObserver.OnPreDrawListener {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnPreDrawListener(listener)
            }
            if (pendingLayerTransformListener === listener) {
                pendingLayerTransformListener = null
            }
            if (acceptedRevision == revision) {
                try {
                    layers.forEach { spec ->
                        val layer = requireHelperView<Layer>(NativeConstraintHelperKind.Layer, spec.id)
                        applyLayerTransformsSafely(layer, spec, environment)
                    }
                } catch (error: Throwable) {
                    emitPostCommitLayerFailure(revision, error)
                }
            }
            true
        }
        pendingLayerTransformListener = listener
        viewTreeObserver.addOnPreDrawListener(listener)
    }

    /** A committed graph cannot be rolled back safely from its post-layout transform boundary. */
    private fun emitPostCommitLayerFailure(
        revision: Long,
        error: Throwable,
    ) {
        val key = ConstraintDiagnosticKey(
            revision = revision,
            reason = ConstraintGraphRejectionReason.NativeCommit,
            identity = "layer-post-layout",
        )
        if (!emittedDiagnostics.add(key)) return
        while (emittedDiagnostics.size > MAX_DIAGNOSTIC_KEYS) {
            emittedDiagnostics.remove(emittedDiagnostics.first())
        }
        Log.e(
            WARNING_TAG,
            "Committed Layer post-layout transform failed at revision=$revision; " +
                "the accepted graph remains active and children retain their last valid state.",
            error,
        )
    }

    private fun cancelPendingLayerTransforms() {
        val listener = pendingLayerTransformListener ?: return
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnPreDrawListener(listener)
        }
        pendingLayerTransformListener = null
    }

    private fun applyChains(
        constraintSet: ConstraintSet,
        chains: List<ConstraintChainSpec>,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        chains.forEach { chain ->
            val chainWeights = chain.weights
            val resolvedItems = chain.referencedIds.mapIndexed { refIndex, referenceId ->
                val resolvedId = requireNotNull(referenceIds[referenceId]) {
                    "Validated chain reference '$referenceId' disappeared before native commit."
                }
                ChainResolvedItem(
                    viewId = resolvedId,
                    weight = chainWeights?.getOrNull(refIndex),
                )
            }
            when (chain.orientation) {
                ConstraintChainOrientation.Horizontal -> {
                    applyHorizontalChain(constraintSet, resolvedItems, chain, referenceIds, environment)
                }

                ConstraintChainOrientation.Vertical -> {
                    applyVerticalChain(constraintSet, resolvedItems, chain, referenceIds, environment)
                }
            }
        }
    }

    private fun applyHorizontalChain(
        constraintSet: ConstraintSet,
        resolvedItems: List<ChainResolvedItem>,
        chain: ConstraintChainSpec,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        val resolvedIds = resolvedItems.map { item -> item.viewId }
        val first = resolvedIds.first()
        val last = resolvedIds.last()
        val startTarget = chain.startTarget
            ?: com.viewcompose.ui.node.spec.ConstraintAnchorTarget.parent(ConstraintAnchor.Start)
        val endTarget = chain.endTarget
            ?: com.viewcompose.ui.node.spec.ConstraintAnchorTarget.parent(ConstraintAnchor.End)
        val logical = startTarget.anchor == ConstraintAnchor.Start || startTarget.anchor == ConstraintAnchor.End
        val sourceStart = if (logical) ConstraintSet.START else ConstraintSet.LEFT
        val sourceEnd = if (logical) ConstraintSet.END else ConstraintSet.RIGHT
        constraintSet.connect(
            first,
            sourceStart,
            startTarget.resolveViewId(referenceIds),
            startTarget.anchor.toConstraintSetSide(),
            environment.roundToPx(chain.startMargin),
        )
        constraintSet.connect(
            last,
            sourceEnd,
            endTarget.resolveViewId(referenceIds),
            endTarget.anchor.toConstraintSetSide(),
            environment.roundToPx(chain.endMargin),
        )
        for (index in resolvedIds.indices) {
            val viewId = resolvedIds[index]
            if (index > 0) {
                constraintSet.connect(
                    viewId,
                    sourceStart,
                    resolvedIds[index - 1],
                    sourceEnd,
                )
            }
            if (index < resolvedIds.lastIndex) {
                constraintSet.connect(
                    viewId,
                    sourceEnd,
                    resolvedIds[index + 1],
                    sourceStart,
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
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        val resolvedIds = resolvedItems.map { item -> item.viewId }
        val first = resolvedIds.first()
        val last = resolvedIds.last()
        val topTarget = chain.startTarget
            ?: com.viewcompose.ui.node.spec.ConstraintAnchorTarget.parent(ConstraintAnchor.Top)
        val bottomTarget = chain.endTarget
            ?: com.viewcompose.ui.node.spec.ConstraintAnchorTarget.parent(ConstraintAnchor.Bottom)
        constraintSet.connect(
            first,
            ConstraintSet.TOP,
            topTarget.resolveViewId(referenceIds),
            topTarget.anchor.toConstraintSetSide(),
            environment.roundToPx(chain.startMargin),
        )
        constraintSet.connect(
            last,
            ConstraintSet.BOTTOM,
            bottomTarget.resolveViewId(referenceIds),
            bottomTarget.anchor.toConstraintSetSide(),
            environment.roundToPx(chain.endMargin),
        )
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

    private fun applyGrids(
        constraintSet: ConstraintSet,
        grids: List<ResolvedConstraintGrid>,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        grids.forEach { grid ->
            val rowIds = List(grid.rows) { row ->
                requireNotNull(helperIdToViewId[gridRowKey(grid.spec.id, row)])
            }
            val columnIds = List(grid.columns) { column ->
                requireNotNull(helperIdToViewId[gridColumnKey(grid.spec.id, column)])
            }
            applyGridColumns(constraintSet, grid, columnIds, environment)
            applyGridRows(constraintSet, grid, rowIds, environment)
            grid.placements.forEach { placement ->
                val childId = requireNotNull(referenceIds[placement.referenceId])
                constraintSet.connect(
                    childId,
                    ConstraintSet.START,
                    columnIds[placement.column],
                    ConstraintSet.START,
                )
                constraintSet.connect(
                    childId,
                    ConstraintSet.END,
                    columnIds[placement.column + placement.columnSpan - 1],
                    ConstraintSet.END,
                )
                constraintSet.connect(
                    childId,
                    ConstraintSet.TOP,
                    rowIds[placement.row],
                    ConstraintSet.TOP,
                )
                constraintSet.connect(
                    childId,
                    ConstraintSet.BOTTOM,
                    rowIds[placement.row + placement.rowSpan - 1],
                    ConstraintSet.BOTTOM,
                )
            }
        }
    }

    private fun applyGridColumns(
        constraintSet: ConstraintSet,
        grid: ResolvedConstraintGrid,
        ids: List<Int>,
        environment: UiEnvironmentValues,
    ) {
        val gap = environment.roundToPx(grid.spec.horizontalGap)
        val leadingGap = gap - gap / 2
        val trailingGap = gap / 2
        ids.forEachIndexed { index, id ->
            constraintSet.constrainWidth(id, ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.constrainHeight(id, 0)
            constraintSet.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            if (index == 0) {
                constraintSet.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            } else {
                constraintSet.connect(id, ConstraintSet.START, ids[index - 1], ConstraintSet.END, leadingGap)
            }
            if (index == ids.lastIndex) {
                constraintSet.connect(id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            } else {
                constraintSet.connect(id, ConstraintSet.END, ids[index + 1], ConstraintSet.START, trailingGap)
            }
            constraintSet.setHorizontalWeight(id, grid.spec.columnWeights.getOrElse(index) { 1f })
        }
        constraintSet.setHorizontalChainStyle(ids.first(), ConstraintSet.CHAIN_SPREAD)
    }

    private fun applyGridRows(
        constraintSet: ConstraintSet,
        grid: ResolvedConstraintGrid,
        ids: List<Int>,
        environment: UiEnvironmentValues,
    ) {
        val gap = environment.roundToPx(grid.spec.verticalGap)
        val leadingGap = gap - gap / 2
        val trailingGap = gap / 2
        ids.forEachIndexed { index, id ->
            constraintSet.constrainWidth(id, 0)
            constraintSet.constrainHeight(id, ConstraintSet.MATCH_CONSTRAINT)
            constraintSet.connect(id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            if (index == 0) {
                constraintSet.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            } else {
                constraintSet.connect(id, ConstraintSet.TOP, ids[index - 1], ConstraintSet.BOTTOM, leadingGap)
            }
            if (index == ids.lastIndex) {
                constraintSet.connect(id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            } else {
                constraintSet.connect(id, ConstraintSet.BOTTOM, ids[index + 1], ConstraintSet.TOP, trailingGap)
            }
            constraintSet.setVerticalWeight(id, grid.spec.rowWeights.getOrElse(index) { 1f })
        }
        constraintSet.setVerticalChainStyle(ids.first(), ConstraintSet.CHAIN_SPREAD)
    }

    private fun applyCircularFlows(
        constraintSet: ConstraintSet,
        circularFlows: List<ConstraintCircularFlowSpec>,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        circularFlows.forEach { flow ->
            val centerId = requireNotNull(referenceIds[flow.centerId])
            flow.items.forEach { item ->
                constraintSet.constrainCircle(
                    requireNotNull(referenceIds[item.referenceId]),
                    centerId,
                    environment.roundToPx(item.radius),
                    item.angle,
                )
            }
        }
    }

    private fun applyItemConstraints(
        constraintSet: ConstraintSet,
        graph: ResolvedConstraintGraph,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        graph.constrainableIds.forEach { referenceId ->
            val item = graph.constraints[referenceId] ?: ConstraintItemSpec()
            val viewId = requireNotNull(referenceIds[referenceId]) {
                "Validated layout node '$referenceId' disappeared before native commit."
            }
            applyDimension(
                constraintSet = constraintSet,
                viewId = viewId,
                dimension = item.width,
                horizontal = true,
                environment = environment,
            )
            applyDimension(
                constraintSet = constraintSet,
                viewId = viewId,
                dimension = item.height,
                horizontal = false,
                environment = environment,
            )
            item.start?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.Start,
                referenceIds = referenceIds,
                environment = environment,
            )
            item.end?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.End,
                referenceIds = referenceIds,
                environment = environment,
            )
            item.left?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.Left,
                referenceIds = referenceIds,
                environment = environment,
            )
            item.right?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.Right,
                referenceIds = referenceIds,
                environment = environment,
            )
            item.top?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.Top,
                referenceIds = referenceIds,
                environment = environment,
            )
            item.bottom?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.Bottom,
                referenceIds = referenceIds,
                environment = environment,
            )
            item.baseline?.applyTo(
                constraintSet = constraintSet,
                sourceViewId = viewId,
                sourceAnchor = ConstraintAnchor.Baseline,
                referenceIds = referenceIds,
                environment = environment,
            )
            item.circle?.let { circle ->
                constraintSet.constrainCircle(
                    viewId,
                    requireNotNull(referenceIds[circle.targetId]),
                    environment.roundToPx(circle.radius),
                    circle.angle,
                )
            }
            item.horizontalBias?.let { bias -> constraintSet.setHorizontalBias(viewId, bias) }
            item.verticalBias?.let { bias -> constraintSet.setVerticalBias(viewId, bias) }
            item.ratio?.let { ratio -> constraintSet.setDimensionRatio(viewId, ratio.toNativeRatio()) }
            constraintSet.setLayoutWrapBehavior(viewId, item.wrapBehaviorInParent.toNativeWrapBehavior())
        }
    }

    private fun applyDimension(
        constraintSet: ConstraintSet,
        viewId: Int,
        dimension: ConstraintDimension,
        horizontal: Boolean,
        environment: UiEnvironmentValues,
    ) {
        val size = when (dimension) {
            ConstraintDimension.WrapContent,
            ConstraintDimension.ConstrainedWrapContent,
            -> LayoutParams.WRAP_CONTENT

            is ConstraintDimension.Fixed -> environment.roundToPx(dimension.value)
            is ConstraintDimension.MatchConstraints -> ConstraintSet.MATCH_CONSTRAINT
        }
        if (horizontal) {
            constraintSet.constrainWidth(viewId, size)
            constraintSet.constrainedWidth(
                viewId,
                dimension == ConstraintDimension.ConstrainedWrapContent,
            )
        } else {
            constraintSet.constrainHeight(viewId, size)
            constraintSet.constrainedHeight(
                viewId,
                dimension == ConstraintDimension.ConstrainedWrapContent,
            )
        }
        if (dimension !is ConstraintDimension.MatchConstraints) return

        val defaultMode = when (dimension.mode) {
            ConstraintMatchMode.Spread -> ConstraintSet.MATCH_CONSTRAINT_SPREAD
            ConstraintMatchMode.Wrap -> ConstraintSet.MATCH_CONSTRAINT_WRAP
            is ConstraintMatchMode.Percent -> ConstraintSet.MATCH_CONSTRAINT_PERCENT
        }
        if (horizontal) {
            constraintSet.constrainDefaultWidth(viewId, defaultMode)
            dimension.min?.let { constraintSet.constrainMinWidth(viewId, environment.roundToPx(it)) }
            dimension.max?.let { constraintSet.constrainMaxWidth(viewId, environment.roundToPx(it)) }
            (dimension.mode as? ConstraintMatchMode.Percent)?.let { mode ->
                constraintSet.constrainPercentWidth(viewId, mode.fraction)
            }
        } else {
            constraintSet.constrainDefaultHeight(viewId, defaultMode)
            dimension.min?.let { constraintSet.constrainMinHeight(viewId, environment.roundToPx(it)) }
            dimension.max?.let { constraintSet.constrainMaxHeight(viewId, environment.roundToPx(it)) }
            (dimension.mode as? ConstraintMatchMode.Percent)?.let { mode ->
                constraintSet.constrainPercentHeight(viewId, mode.fraction)
            }
        }
    }

    private fun ConstraintAnchorLink.applyTo(
        constraintSet: ConstraintSet,
        sourceViewId: Int,
        sourceAnchor: ConstraintAnchor,
        referenceIds: Map<String, Int>,
        environment: UiEnvironmentValues,
    ) {
        val targetId = target.id?.let { id -> requireNotNull(referenceIds[id]) }
            ?: ConstraintSet.PARENT_ID
        val sourceSide = sourceAnchor.toConstraintSetSide()
        val targetSide = target.anchor.toConstraintSetSide()
        val marginPx = environment.roundToPx(margin)
        constraintSet.connect(
            sourceViewId,
            sourceSide,
            targetId,
            targetSide,
            marginPx,
        )
        // ConstraintSet.connect intentionally ignores the margin argument for BASELINE. Apply the
        // dedicated field after the link so baseline-to-baseline/top/bottom preserves DSL geometry.
        if (sourceSide == ConstraintSet.BASELINE) {
            constraintSet.setMargin(sourceViewId, sourceSide, marginPx)
        }
        goneMargin?.let { marginValue ->
            constraintSet.setGoneMargin(
                sourceViewId,
                sourceSide,
                environment.roundToPx(marginValue),
            )
        }
    }

    private fun ConstraintAnchorTarget.resolveViewId(referenceIds: Map<String, Int>): Int {
        return id?.let { referenceId -> requireNotNull(referenceIds[referenceId]) }
            ?: ConstraintSet.PARENT_ID
    }

    private fun ConstraintRatio.toNativeRatio(): String {
        val prefix = when (constrainedSide) {
            null -> ""
            ConstraintRatioSide.Width -> "W,"
            ConstraintRatioSide.Height -> "H,"
        }
        return "$prefix$width:$height"
    }

    private fun ConstraintAnchor.toConstraintSetSide(): Int {
        return when (this) {
            ConstraintAnchor.Start -> ConstraintSet.START
            ConstraintAnchor.End -> ConstraintSet.END
            ConstraintAnchor.Left -> ConstraintSet.LEFT
            ConstraintAnchor.Right -> ConstraintSet.RIGHT
            ConstraintAnchor.Top -> ConstraintSet.TOP
            ConstraintAnchor.Bottom -> ConstraintSet.BOTTOM
            ConstraintAnchor.Baseline -> ConstraintSet.BASELINE
        }
    }

    private fun ConstraintBarrierDirection.toBarrierDirection(): Int {
        return when (this) {
            ConstraintBarrierDirection.Start -> Barrier.START
            ConstraintBarrierDirection.End -> Barrier.END
            ConstraintBarrierDirection.Left -> Barrier.LEFT
            ConstraintBarrierDirection.Right -> Barrier.RIGHT
            ConstraintBarrierDirection.Top -> Barrier.TOP
            ConstraintBarrierDirection.Bottom -> Barrier.BOTTOM
        }
    }

    private fun ConstraintWrapBehavior.toNativeWrapBehavior(): Int {
        return when (this) {
            ConstraintWrapBehavior.Included -> LayoutParams.WRAP_BEHAVIOR_INCLUDED
            ConstraintWrapBehavior.HorizontalOnly -> LayoutParams.WRAP_BEHAVIOR_HORIZONTAL_ONLY
            ConstraintWrapBehavior.VerticalOnly -> LayoutParams.WRAP_BEHAVIOR_VERTICAL_ONLY
            ConstraintWrapBehavior.Skipped -> LayoutParams.WRAP_BEHAVIOR_SKIPPED
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
