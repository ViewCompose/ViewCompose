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
import com.viewcompose.ui.node.LazyListItem
import com.viewcompose.ui.node.LazyListItemSession
import com.viewcompose.ui.node.LazyListItemSessionFactory
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
        listOf(lazyItem("account"), lazyItem("account"), lazyItem(null)),
    )

    check(!analysis.supportsKeyedDiff)
    check(analysis.duplicateKeys == listOf("account"))
    check(analysis.missingKeyIndexes == listOf(2))
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
    var mounted = ViewTreeRenderer.renderInto(
        container = container,
        previous = emptyList(),
        nodes = nextNodes,
    ).mountedNodes

    mounted = ViewTreeRenderer.renderInto(
        container = container,
        previous = mounted,
        nodes = nextNodes,
    ).mountedNodes

    ViewTreeRenderer.disposeMounted(container, mounted)
}

private fun vnode(key: Any): VNode = VNode(
    type = NodeType.Text,
    key = key,
    spec = EmptyNodeSpec,
)

private fun lazyItem(key: Any?): LazyListItem = LazyListItem(
    key = key,
    contentToken = key,
    sessionFactory = LazyListItemSessionFactory {
        object : LazyListItemSession {
            override fun render() = Unit

            override fun dispose() = Unit
        }
    },
)
