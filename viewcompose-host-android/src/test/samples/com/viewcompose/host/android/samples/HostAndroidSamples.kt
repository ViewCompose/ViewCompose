package com.viewcompose.host.android.samples

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.AndroidViewAdapter
import com.viewcompose.host.android.AndroidViewCreateScope
import com.viewcompose.host.android.AndroidViewResetReason
import com.viewcompose.host.android.AndroidViewResetScope
import com.viewcompose.host.android.AndroidViewReusePolicy
import com.viewcompose.host.android.AndroidViewUpdateScope
import com.viewcompose.host.android.renderInto
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

fun androidViewInteropSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        factory = { context -> TextView(context) },
        update = { view -> (view as TextView).text = "Native TextView" },
        onRelease = { view -> (view as TextView).text = null },
    )
}

fun typedAndroidViewAdapterSample(builder: UiTreeBuilder) {
    builder.AndroidView(
        adapter = NativeLabelAdapter,
        state = "Typed native label",
        key = "label",
        constructionKey = "default-text-appearance",
    )
}

private object NativeLabelAdapter : AndroidViewAdapter<TextView, String> {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Resettable

    override fun create(scope: AndroidViewCreateScope): TextView = TextView(scope.context)

    override fun update(scope: AndroidViewUpdateScope<TextView>, state: String) {
        scope.view.text = state
    }

    override fun onReset(
        scope: AndroidViewResetScope<TextView>,
        reason: AndroidViewResetReason,
    ) {
        scope.view.text = null
    }
}

fun androidResourceEnvironmentSample(
    builder: UiTreeBuilder,
    context: Context,
    titleResource: Int,
) {
    builder.AndroidResourceEnvironment(context) {
        Text(stringResource(titleResource))
    }
}

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
