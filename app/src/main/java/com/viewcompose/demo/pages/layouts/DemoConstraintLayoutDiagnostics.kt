package com.viewcompose

import android.view.View
import android.view.ViewGroup
import com.viewcompose.renderer.R as RendererR
import com.viewcompose.runtime.MutableState
import com.viewcompose.ui.foundation.LaunchedEffect
import com.viewcompose.ui.foundation.UiTreeBuilder
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * App-owned, explicitly activated diagnostic projection for ConstraintLayout Demo fixtures.
 *
 * The renderer intentionally keeps reconciliation state internal. Demo acceptance still needs to
 * display the state of the mounted native instance rather than echoing the requested DSL state, so
 * this downstream fixture reads that internal state reflectively after two animation frames. No
 * observer, listener, or recurring renderer hot-path work exists while a diagnostic scene is not
 * mounted.
 */
internal sealed interface DemoConstraintLayoutDiagnostics {
    data class Ready(
        val acceptedRevision: Long,
        val attemptedRevision: Long,
        val managedHelperCount: Int,
        val nativeChildCount: Int,
        val topologyFingerprint: Long?,
        val scalarFingerprint: Long?,
        val updateClass: DemoConstraintUpdateClass,
        val rejection: DemoConstraintRejection?,
    ) : DemoConstraintLayoutDiagnostics

    data class Unavailable(
        val reason: String,
    ) : DemoConstraintLayoutDiagnostics
}

internal data class DemoConstraintRejection(
    val reason: String,
    val identity: String?,
    val detail: String,
)

internal enum class DemoConstraintUpdateClass {
    Initial,
    NoOp,
    Scalar,
    Topology,
    Environment,
    Rejected,
}

internal fun UiTreeBuilder.ObserveDemoConstraintLayoutDiagnostics(
    hostRoot: ViewGroup?,
    testTag: String,
    trigger: Any?,
    state: MutableState<DemoConstraintLayoutDiagnostics>,
) {
    LaunchedEffect(hostRoot, testTag, trigger) {
        if (hostRoot == null) {
            state.value = DemoConstraintLayoutDiagnostics.Unavailable("preview")
            return@LaunchedEffect
        }
        hostRoot.awaitAnimationFrame()
        hostRoot.awaitAnimationFrame()
        state.value = inspectDemoConstraintLayout(
            root = hostRoot,
            testTag = testTag,
            previous = state.value as? DemoConstraintLayoutDiagnostics.Ready,
        )
    }
}

private suspend fun View.awaitAnimationFrame() {
    suspendCancellableCoroutine { continuation ->
        val callback = Runnable {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
        postOnAnimation(callback)
        continuation.invokeOnCancellation { removeCallbacks(callback) }
    }
}

private fun inspectDemoConstraintLayout(
    root: ViewGroup,
    testTag: String,
    previous: DemoConstraintLayoutDiagnostics.Ready?,
): DemoConstraintLayoutDiagnostics {
    val layout = root.findViewByTestTag(testTag)
        ?: return DemoConstraintLayoutDiagnostics.Unavailable("not-mounted")
    if (layout.javaClass.name != DECLARATIVE_CONSTRAINT_LAYOUT_CLASS) {
        return DemoConstraintLayoutDiagnostics.Unavailable("unexpected-native-type")
    }
    return runCatching {
        val acceptedGraph = layout.readPrivateField("acceptedGraph")
        val rejection = layout.readPrivateField("lastRejection")?.let { value ->
            DemoConstraintRejection(
                reason = value.readPrivateField("reason").toString(),
                identity = value.readPrivateField("identity") as? String,
                detail = (value.readPrivateField("detail") as String).take(MAX_FAILURE_DETAIL_LENGTH),
            )
        }
        val acceptedRevision = layout.readPrivateField("acceptedRevision") as Long
        val topologyFingerprint = acceptedGraph?.readPrivateField("topologyFingerprint") as? Long
        val scalarFingerprint = acceptedGraph?.readPrivateField("scalarFingerprint") as? Long
        DemoConstraintLayoutDiagnostics.Ready(
            acceptedRevision = acceptedRevision,
            attemptedRevision = layout.readPrivateField("attemptedRevision") as Long,
            managedHelperCount = (layout.readPrivateField("helperViews") as Map<*, *>).size,
            nativeChildCount = (layout as ViewGroup).childCount,
            topologyFingerprint = topologyFingerprint,
            scalarFingerprint = scalarFingerprint,
            updateClass = classifyUpdate(
                previous = previous,
                acceptedRevision = acceptedRevision,
                topologyFingerprint = topologyFingerprint,
                scalarFingerprint = scalarFingerprint,
                rejection = rejection,
            ),
            rejection = rejection,
        )
    }.getOrElse { error ->
        DemoConstraintLayoutDiagnostics.Unavailable(
            reason = error.javaClass.simpleName.ifBlank { "inspection-failed" },
        )
    }
}

private fun classifyUpdate(
    previous: DemoConstraintLayoutDiagnostics.Ready?,
    acceptedRevision: Long,
    topologyFingerprint: Long?,
    scalarFingerprint: Long?,
    rejection: DemoConstraintRejection?,
): DemoConstraintUpdateClass {
    if (rejection != null) return DemoConstraintUpdateClass.Rejected
    if (previous == null || previous.acceptedRevision == 0L) return DemoConstraintUpdateClass.Initial
    if (acceptedRevision == previous.acceptedRevision) return DemoConstraintUpdateClass.NoOp
    if (topologyFingerprint != previous.topologyFingerprint) return DemoConstraintUpdateClass.Topology
    if (scalarFingerprint != previous.scalarFingerprint) return DemoConstraintUpdateClass.Scalar
    return DemoConstraintUpdateClass.Environment
}

private fun View.findViewByTestTag(testTag: String): View? {
    if (getTag(RendererR.id.viewcompose_test_tag) == testTag) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findViewByTestTag(testTag)?.let { return it }
    }
    return null
}

private fun Any.readPrivateField(name: String): Any? {
    var type: Class<*>? = javaClass
    while (type != null) {
        val field = runCatching { type.getDeclaredField(name) }.getOrNull()
        if (field != null) {
            field.isAccessible = true
            return field.get(this)
        }
        type = type.superclass
    }
    error("Missing diagnostic field '$name' on ${javaClass.name}.")
}

private const val DECLARATIVE_CONSTRAINT_LAYOUT_CLASS =
    "com.viewcompose.renderer.view.container.DeclarativeConstraintLayout"
private const val MAX_FAILURE_DETAIL_LENGTH = 180
