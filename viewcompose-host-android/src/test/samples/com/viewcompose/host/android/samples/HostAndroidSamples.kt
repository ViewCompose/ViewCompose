package com.viewcompose.host.android.samples

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.motion.widget.MotionLayout
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.AndroidViewAdapter
import com.viewcompose.host.android.AndroidViewCreateScope
import com.viewcompose.host.android.AndroidViewResetReason
import com.viewcompose.host.android.AndroidViewResetScope
import com.viewcompose.host.android.AndroidViewReusePolicy
import com.viewcompose.host.android.AndroidViewUpdateScope
import com.viewcompose.host.android.renderInto
import com.viewcompose.host.android.installRenderSessionInspectionTooling
import com.viewcompose.host.android.animation.AndroidAnimationInterop
import com.viewcompose.host.android.animation.MotionLayoutView
import com.viewcompose.host.android.animation.androidAnimation
import com.viewcompose.host.android.graphics.AndroidGraphicsInterop
import com.viewcompose.host.android.graphics.androidGraphics
import com.viewcompose.host.android.resources.AndroidResourceEnvironment
import com.viewcompose.host.android.resources.AndroidResourceRefreshController
import com.viewcompose.host.android.resources.booleanResource
import com.viewcompose.host.android.resources.colorResource
import com.viewcompose.host.android.resources.dimensionPixelSizeResource
import com.viewcompose.host.android.resources.dimensionResource
import com.viewcompose.host.android.resources.integerArrayResource
import com.viewcompose.host.android.resources.integerResource
import com.viewcompose.host.android.resources.pluralStringResource
import com.viewcompose.host.android.resources.stringArrayResource
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.ui.foundation.Column
import com.viewcompose.ui.foundation.RenderDiagnosticCollection
import com.viewcompose.ui.foundation.RenderDiagnostics
import com.viewcompose.ui.foundation.RenderFrameDiagnosticLevel
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.RenderSessionInspectionTooling
import com.viewcompose.ui.modifier.Modifier

/** Installs an optional downstream inspection port before the first render session starts. */
fun installRenderSessionInspectionToolingSample(tooling: RenderSessionInspectionTooling) {
    installRenderSessionInspectionTooling(tooling)
}

// DOCS_REGION_START(host-render-into)
fun renderIntoSample(container: ViewGroup) {
    val diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            frameLevel = RenderFrameDiagnosticLevel.Stats,
        ),
        sink = { event -> println(event) },
    )
    val session = renderInto(container, diagnostics = diagnostics) {
        Text("Custom host")
    }
    session.setRenderingActive(false)
    session.render()
    session.dispose()
    check(runCatching(session::render).exceptionOrNull() is IllegalStateException)
}
// DOCS_REGION_END(host-render-into)

fun androidViewInteropSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        factory = { context -> TextView(context) },
        update = { view -> (view as TextView).text = "Native TextView" },
        onRelease = { view -> (view as TextView).text = null },
    )
}

// DOCS_REGION_START(host-android-view-adapter)
fun typedAndroidViewAdapterSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        adapter = NativeLabelAdapter,
        state = NativeLabelState(
            text = "Typed native label",
            enabled = true,
        ),
        key = "label",
        constructionKey = "default-text-appearance",
    )
}

private data class NativeLabelState(
    val text: String,
    val enabled: Boolean,
)

private object NativeLabelAdapter : AndroidViewAdapter<TextView, NativeLabelState> {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Resettable

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: NativeLabelState) {
        scope.view.text = state.text
        scope.view.isEnabled = state.enabled
    }

    override fun onReset(
        scope: AndroidViewResetScope<TextView>,
        reason: AndroidViewResetReason,
    ) {
        scope.view.text = null
        scope.view.isEnabled = false
    }
}
// DOCS_REGION_END(host-android-view-adapter)

// DOCS_REGION_START(host-android-resources)
fun androidResourceEnvironmentSample(
    builder: UiTreeBuilder,
    context: Context,
    titleResource: Int,
) {
    builder.AndroidResourceEnvironment(context) {
        Text(stringResource(titleResource))
    }
}
// DOCS_REGION_END(host-android-resources)

// DOCS_REGION_START(host-android-animation)
fun platformAnimationInteropSample(target: View) =
    AndroidAnimationInterop.startObjectAnimator(
        target,
        "alpha",
        0f,
        1f,
        durationMillis = 180L,
    )

fun UiTreeBuilder.motionLayoutInteropSample() {
    MotionLayoutView(
        factory = { context -> MotionLayout(context) },
        update = { layout -> layout.progress = 0f },
        modifier = Modifier.androidAnimation(key = "settled-alpha") { view ->
            view.alpha = 1f
        },
    )
}
// DOCS_REGION_END(host-android-animation)

// DOCS_REGION_START(host-android-graphics)
fun platformGraphicsInteropSample(target: View): Modifier {
    val effect = AndroidGraphicsInterop.createBlurEffect(radiusX = 12f, radiusY = 12f)
    AndroidGraphicsInterop.applyRenderEffect(target, effect)
    return Modifier.androidGraphics(key = "native-graphics") { view ->
        view.alpha = 1f
    }
}
// DOCS_REGION_END(host-android-graphics)

fun androidResourceRefreshSample(controller: AndroidResourceRefreshController) {
    // Update the stable root Context wrapper first when the mutation replaces its base context.
    controller.refresh()
}

fun UiTreeBuilder.androidResourceLookupSample(
    titleResource: Int,
    formattedResource: Int,
    pluralResource: Int,
    colorResourceId: Int,
    dimensionResourceId: Int,
    booleanResourceId: Int,
    integerResourceId: Int,
    stringArrayResourceId: Int,
    integerArrayResourceId: Int,
) {
    val count = integerResource(integerResourceId)
    Column {
        Text(stringResource(titleResource))
        Text(stringResource(formattedResource, count))
        Text(pluralStringResource(pluralResource, count, count))
        Text("argb=${colorResource(colorResourceId)}")
        Text("dp=${dimensionResource(dimensionResourceId).value}")
        Text("px=${dimensionPixelSizeResource(dimensionResourceId)}")
        Text("enabled=${booleanResource(booleanResourceId)}")
        Text(stringArrayResource(stringArrayResourceId).joinToString())
        Text(integerArrayResource(integerArrayResourceId).joinToString())
    }
}
