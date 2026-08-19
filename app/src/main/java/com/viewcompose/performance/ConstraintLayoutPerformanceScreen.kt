package com.viewcompose.performance

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout as AndroidXConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.viewcompose.constraintlayout.ConstraintLayout
import com.viewcompose.constraintlayout.constrainAs
import com.viewcompose.constraintlayout.createEndBarrier
import com.viewcompose.constraintlayout.createRef
import com.viewcompose.constraintlayout.parent
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.runtime.State
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Spacer
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.observedValue
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.layout.VerticalAlignment
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.backgroundColor
import com.viewcompose.ui.modifier.cornerRadius
import com.viewcompose.ui.modifier.fillMaxSize
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.node.spec.ConstraintDimension
import com.viewcompose.ui.unit.dp

private const val ConstraintColumns = 10
private const val ConstraintCellDp = 24
private const val ConstraintCellStepDp = 28

/** ViewCompose side of the dedicated ConstraintLayout scale/workload protocol. */
internal fun UiTreeBuilder.ViewComposeConstraintLayoutPerformanceScreen(
    scenario: DemoScenarioSpec,
    profile: ConstraintLayoutPerformanceProfile,
    copy: PerformanceCopy,
) {
    val revisionState = remember { mutableStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .backgroundColor(PERFORMANCE_BACKGROUND_COLOR)
            .constraintScenarioTarget(scenario, DemoAutomationRole.Root),
    ) {
        ConstraintLayoutPerformanceHeader(
            engineName = PerformanceEngine.ViewCompose.displayName,
            profile = profile,
            revisionState = revisionState,
            onUpdate = { revisionState.value += 1 },
            onReset = { revisionState.value = 0 },
            scenario = scenario,
            copy = copy,
        )
        val targetModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(8.dp)
            .constraintScenarioTarget(scenario, DemoAutomationRole.Target)
        RecomposeBoundary(
            key = "constraint-layout-${profile.nodeCount}-${profile.workload.wireValue}",
            inputs = listOf(revisionState),
        ) {
            val revision = revisionState.value
            ConstraintLayout(
                modifier = targetModifier,
            ) {
                val refs = (0 until profile.nodeCount).map { index ->
                    createRef("constraint-node-$index")
                }
                if (profile.workload == ConstraintLayoutPerformanceWorkload.Helper) {
                    refs.chunked(ConstraintColumns).forEachIndexed { row, rowRefs ->
                        createEndBarrier(
                            *rowRefs.toTypedArray(),
                            id = "constraint-barrier-$row",
                            margin = if (revision % 2 == 0) 0.dp else 3.dp,
                            allowsGoneWidgets = revision % 2 == 0,
                        )
                    }
                }
                refs.forEachIndexed { index, ref ->
                    val column = index % ConstraintColumns
                    val row = index / ConstraintColumns
                    val scalarDelta = if (
                        profile.workload == ConstraintLayoutPerformanceWorkload.Scalar &&
                        revision % 2 != 0
                    ) {
                        2
                    } else {
                        0
                    }
                    val topologyFlipped =
                        profile.workload == ConstraintLayoutPerformanceWorkload.Topology &&
                            revision % 2 != 0
                    val color = if (
                        profile.workload == ConstraintLayoutPerformanceWorkload.Stable &&
                        revision % 2 != 0
                    ) {
                        PERFORMANCE_PRIMARY_COLOR
                    } else {
                        PERFORMANCE_BADGE_COLOR
                    }
                    Spacer(
                        key = index,
                        modifier = Modifier
                            .constrainAs(ref) {
                                if (topologyFlipped) {
                                    endToEnd(
                                        parent,
                                        margin = (column * ConstraintCellStepDp + scalarDelta).dp,
                                    )
                                    bottomToBottom(
                                        parent,
                                        margin = (row * ConstraintCellStepDp + scalarDelta).dp,
                                    )
                                } else {
                                    startToStart(
                                        parent,
                                        margin = (column * ConstraintCellStepDp + scalarDelta).dp,
                                    )
                                    topToTop(
                                        parent,
                                        margin = (row * ConstraintCellStepDp + scalarDelta).dp,
                                    )
                                }
                                width = ConstraintDimension.Fixed(ConstraintCellDp.dp)
                                height = ConstraintDimension.Fixed(ConstraintCellDp.dp)
                            }
                            .backgroundColor(color),
                    )
                }
            }
        }
    }
}

private fun UiTreeBuilder.ConstraintLayoutPerformanceHeader(
    engineName: String,
    profile: ConstraintLayoutPerformanceProfile,
    revisionState: State<Int>,
    onUpdate: () -> Unit,
    onReset: () -> Unit,
    scenario: DemoScenarioSpec,
    copy: PerformanceCopy,
) {
    Column(
        spacing = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .backgroundColor(PERFORMANCE_SURFACE_COLOR)
            .padding(12.dp),
    ) {
        Text(
            text = copy.constraintReady(
                engineName,
                profile.nodeCount,
                profile.workload.wireValue,
            ),
            style = TextDefaults.titleMediumStyle(),
            color = PERFORMANCE_PRIMARY_TEXT_COLOR,
            modifier = Modifier.constraintScenarioTarget(scenario, DemoAutomationRole.Ready),
        )
        Text(
            text = observedValue { copy.constraintRevision(revisionState.value) },
            color = PERFORMANCE_SECONDARY_TEXT_COLOR,
            modifier = Modifier.constraintScenarioTarget(scenario, DemoAutomationRole.State),
        )
        Row(
            spacing = 8.dp,
            verticalAlignment = VerticalAlignment.Center,
        ) {
            ConstraintLayoutPerformanceAction(
                text = copy.constraintUpdate,
                onClick = onUpdate,
                modifier = Modifier.constraintScenarioTarget(
                    scenario,
                    DemoAutomationRole.PrimaryAction,
                ),
            )
            ConstraintLayoutPerformanceAction(
                text = copy.constraintUpdate,
                onClick = onUpdate,
                modifier = Modifier.constraintScenarioTarget(
                    scenario,
                    DemoAutomationRole.SecondaryAction,
                ),
            )
            ConstraintLayoutPerformanceAction(
                text = copy.constraintReset,
                onClick = onReset,
                modifier = Modifier.constraintScenarioTarget(scenario, DemoAutomationRole.Reset),
            )
        }
    }
}

private fun UiTreeBuilder.ConstraintLayoutPerformanceAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        onClick = onClick,
        contentColor = 0xFFFFFFFF.toInt(),
        modifier = modifier
            .backgroundColor(PERFORMANCE_PRIMARY_COLOR)
            .cornerRadius(8.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = 0xFFFFFFFF.toInt())
    }
}

/** Direct AndroidX control side of the dedicated ConstraintLayout scale/workload protocol. */
internal fun createAndroidViewsConstraintLayoutPerformanceScreen(
    context: Context,
    scenario: DemoScenarioSpec,
    profile: ConstraintLayoutPerformanceProfile,
    copy: PerformanceCopy,
): View {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(PERFORMANCE_BACKGROUND_COLOR)
        performanceScenarioTarget(scenario, DemoAutomationRole.Root)
    }
    val target = AndroidXConstraintLayout(context).apply {
        performanceScenarioTarget(scenario, DemoAutomationRole.Target)
        setPadding(
            context.performanceDp(8),
            context.performanceDp(8),
            context.performanceDp(8),
            context.performanceDp(8),
        )
    }
    val nodes = List(profile.nodeCount) {
        View(context).apply {
            id = View.generateViewId()
            target.addView(this)
        }
    }
    val barriers = if (profile.workload == ConstraintLayoutPerformanceWorkload.Helper) {
        nodes.chunked(ConstraintColumns).map { rowNodes ->
            Barrier(context).apply {
                id = View.generateViewId()
                referencedIds = rowNodes.map(View::getId).toIntArray()
                target.addView(this)
            }
        }
    } else {
        emptyList()
    }
    var revision = 0
    var header: AndroidViewsPerformanceHeader? = null

    fun applyRevision(nextRevision: Int) {
        when (profile.workload) {
            ConstraintLayoutPerformanceWorkload.Stable -> {
                val color = if (nextRevision % 2 == 0) {
                    PERFORMANCE_BADGE_COLOR
                } else {
                    PERFORMANCE_PRIMARY_COLOR
                }
                nodes.forEach { it.setBackgroundColor(color) }
            }
            ConstraintLayoutPerformanceWorkload.Helper -> {
                barriers.forEach { barrier ->
                    barrier.type = Barrier.END
                    barrier.margin = context.performanceDp(if (nextRevision % 2 == 0) 0 else 3)
                    barrier.allowsGoneWidget = nextRevision % 2 == 0
                }
                target.requestLayout()
            }
            ConstraintLayoutPerformanceWorkload.Scalar,
            ConstraintLayoutPerformanceWorkload.Topology,
            -> applyAndroidXNodeConstraints(
                context = context,
                target = target,
                nodes = nodes,
                workload = profile.workload,
                revision = nextRevision,
            )
        }
        revision = nextRevision
        header?.stateView?.text = copy.constraintRevision(revision)
    }

    applyAndroidXNodeConstraints(
        context = context,
        target = target,
        nodes = nodes,
        workload = ConstraintLayoutPerformanceWorkload.Scalar,
        revision = 0,
    )
    applyRevision(0)
    val builtHeader = createAndroidViewsPerformanceHeader(
        context = context,
        readyText = copy.constraintReady(
            PerformanceEngine.AndroidViews.displayName,
            profile.nodeCount,
            profile.workload.wireValue,
        ),
        stateText = copy.constraintRevision(0),
        primaryActionText = copy.constraintUpdate,
        secondaryActionText = copy.constraintUpdate,
        resetText = copy.constraintReset,
        scenario = scenario,
        onPrimaryAction = { applyRevision(revision + 1) },
        onSecondaryAction = { applyRevision(revision + 1) },
        onReset = { applyRevision(0) },
    )
    header = builtHeader
    root.addView(
        builtHeader.view,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    root.addView(
        target,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ),
    )
    return root
}

private fun applyAndroidXNodeConstraints(
    context: Context,
    target: AndroidXConstraintLayout,
    nodes: List<View>,
    workload: ConstraintLayoutPerformanceWorkload,
    revision: Int,
) {
    val set = ConstraintSet()
    set.clone(target)
    val scalarDelta = if (
        workload == ConstraintLayoutPerformanceWorkload.Scalar && revision % 2 != 0
    ) {
        2
    } else {
        0
    }
    val topologyFlipped =
        workload == ConstraintLayoutPerformanceWorkload.Topology && revision % 2 != 0
    nodes.forEachIndexed { index, node ->
        val column = index % ConstraintColumns
        val row = index / ConstraintColumns
        set.clear(node.id)
        set.constrainWidth(node.id, context.performanceDp(ConstraintCellDp))
        set.constrainHeight(node.id, context.performanceDp(ConstraintCellDp))
        if (topologyFlipped) {
            set.connect(
                node.id,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END,
                context.performanceDp(column * ConstraintCellStepDp + scalarDelta),
            )
            set.connect(
                node.id,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM,
                context.performanceDp(row * ConstraintCellStepDp + scalarDelta),
            )
        } else {
            set.connect(
                node.id,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                context.performanceDp(column * ConstraintCellStepDp + scalarDelta),
            )
            set.connect(
                node.id,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                context.performanceDp(row * ConstraintCellStepDp + scalarDelta),
            )
        }
    }
    set.applyTo(target)
}

private fun Modifier.constraintScenarioTarget(
    scenario: DemoScenarioSpec,
    role: DemoAutomationRole,
): Modifier = demoAutomationTarget(scenario.automation.require(role))
