package com.viewcompose.renderer.samples

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.decoration.AndroidViewDecorationBackend
import com.viewcompose.renderer.decoration.AndroidViewDecorationPresence
import com.viewcompose.renderer.decoration.AndroidViewDecorationRequest
import com.viewcompose.renderer.decoration.AndroidViewDecorationRuntime
import com.viewcompose.renderer.reconcile.ChildReconciler
import com.viewcompose.renderer.reconcile.LazyListDiff
import com.viewcompose.renderer.reconcile.LazyListIdentityInspector
import com.viewcompose.renderer.reconcile.LazyListUpdate
import com.viewcompose.renderer.reconcile.ReconcileNode
import com.viewcompose.renderer.reconcile.ReusePatch
import com.viewcompose.renderer.view.tree.ViewTreeRenderer
import com.viewcompose.renderer.view.tree.RenderTreeTimingCollector
import com.viewcompose.renderer.view.tree.RenderTreeTimingSpan
import com.viewcompose.renderer.view.tree.ViewTreeObservedPropertyPatch
import com.viewcompose.renderer.view.tree.MountedNode
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.lazyListItemSessionStrategy
import com.viewcompose.ui.node.NodeType
import com.viewcompose.ui.node.VNode
import com.viewcompose.ui.node.spec.EmptyNodeSpec

fun childReconciliationSample() {
    val previous = listOf(
        ReconcileNode(vnode("profile"), payload = "profile-view"),
        ReconcileNode(vnode("settings"), payload = "settings-view"),
    )

    val result = ChildReconciler.reconcile(
        previous = previous,
        nodes = listOf(vnode("settings"), vnode("profile")),
    )

    val retainedPayloads = result.patches.map { (it as ReusePatch).payload }
    check(retainedPayloads == listOf("settings-view", "profile-view"))
    check(result.removals.isEmpty())
}

fun lazyListIdentitySample() {
    val analysis = LazyListIdentityInspector.analyze(
        listOf(lazyItem("account"), lazyItem("account")),
    )

    check(!analysis.supportsKeyedDiff)
    check(analysis.duplicateKeys == listOf("account"))
}

fun lazyListDiffSample() {
    val result = LazyListDiff.calculate(
        previous = listOf(lazyItem("A"), lazyItem("B")),
        next = listOf(lazyItem("B"), lazyItem("A"), lazyItem("C")),
    )

    check(result.items.map(LazyListItem::key) == listOf("B", "A", "C"))
    check(result.updates.none { it is LazyListUpdate.ReloadAll })
}

fun installDecorationBackendSample() {
    AndroidViewDecorationRuntime.install(
        object : AndroidViewDecorationBackend {
            override fun update(
                view: View,
                request: AndroidViewDecorationRequest,
            ): AndroidViewDecorationPresence = AndroidViewDecorationPresence(
                behindChild = request.dropShadows.isNotEmpty(),
                overChild = request.innerShadows.isNotEmpty(),
            )

            override fun clear(view: View) = Unit

            override fun drawBehindChild(canvas: Canvas, parent: ViewGroup, child: View) = Unit

            override fun drawOverChild(canvas: Canvas, parent: ViewGroup, child: View) = Unit
        },
    )
}

fun renderIntoViewGroupSample(
    container: ViewGroup,
    nextNodes: List<VNode>,
) {
    val initial = ViewTreeRenderer.renderInto(
        container = container,
        previous = emptyList(),
        nodes = nextNodes,
    )
    initial.commitEffects.forEach { effect -> effect.commit() }
    var mounted = initial.mountedNodes

    val updated = ViewTreeRenderer.renderInto(
        container = container,
        previous = mounted,
        nodes = nextNodes,
    )
    updated.commitEffects.forEach { effect -> effect.commit() }
    mounted = updated.mountedNodes

    ViewTreeRenderer.disposeMounted(container, mounted)
}

fun renderTreeTimingCollectorSample(
    container: ViewGroup,
    nextNodes: List<VNode>,
) {
    val visitedPhases = mutableListOf<String>()
    val result = ViewTreeRenderer.renderIntoWithTiming(
        container = container,
        previous = emptyList(),
        nodes = nextNodes,
        timingCollector = RenderTreeTimingCollector { _, phase ->
            visitedPhases += phase.name
            RenderTreeTimingSpan { }
        },
    )
    result.commitEffects.forEach { effect -> effect.commit() }
    check(visitedPhases.isNotEmpty())
}

fun patchObservedPropertySample(
    mountedNode: MountedNode,
    nextNode: VNode,
) {
    val previousNode = mountedNode.vnode
    val result = ViewTreeRenderer.patchObservedProperties(
        patches = listOf(
            ViewTreeObservedPropertyPatch(
                id = checkNotNull(previousNode.observedPropertyId),
                mountedNode = mountedNode,
                previous = previousNode,
                next = nextNode,
            ),
        ),
    )
    result.commitEffects.forEach { effect -> effect.commit() }
}

private fun vnode(key: Any): VNode = VNode(
    type = NodeType.Text,
    key = key,
    spec = EmptyNodeSpec,
)

private fun lazyItem(key: Any): LazyListItem = LazyListItem(
    key = key,
    contentRevision = key,
    sessionStrategy = lazyListItemSessionStrategy(
        create = {
            object : LazyListItemSession {
                override fun render() = true

                override fun dispose() = Unit
            }
        },
        update = {},
    ),
)
