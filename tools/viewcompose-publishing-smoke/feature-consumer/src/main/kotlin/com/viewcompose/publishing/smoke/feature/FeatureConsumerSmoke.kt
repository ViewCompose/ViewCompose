package com.viewcompose.publishing.smoke.feature

import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.animation.core.TweenSpec
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.gesture.core.LockedAxis
import com.viewcompose.graphics.Canvas
import com.viewcompose.graphics.core.PathModel
import com.viewcompose.navigation.core.NavGraph
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.widget.core.Text
import com.viewcompose.widget.core.UiTreeBuilder

/**
 * These core types must remain visible when a consumer declares only their feature artifacts.
 */
val transitivelyAvailableCoreTypes = listOf(
    NavGraph::class,
    TweenSpec::class,
    LockedAxis::class,
    PathModel::class,
)

/** Representative high-level APIs that must compile from the declared feature artifacts. */
fun UiTreeBuilder.compileAdvertisedFeatureSurfaces() {
    AnimatedVisibility(visible = true) {
        Text(
            text = "Feature consumer",
            modifier = Modifier.combinedClickable(onClick = {}),
        )
    }
    Canvas { drawContext ->
        check(drawContext.size.width >= 0f)
    }
}
