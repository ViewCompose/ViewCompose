package com.viewcompose.publishing.smoke.feature

import androidx.media3.common.Player as Media3Player
import com.google.android.exoplayer2.Player as LegacyPlayer
import com.viewcompose.animation.AnimatedVisibility
import com.viewcompose.animation.core.TweenSpec
import com.viewcompose.gesture.combinedClickable
import com.viewcompose.gesture.core.LockedAxis
import com.viewcompose.graphics.Canvas
import com.viewcompose.graphics.core.PathModel
import com.viewcompose.navigation.core.NavGraph
import com.viewcompose.media3.Media3PlayerView
import com.viewcompose.exoplayer2.ExoPlayerView
import com.viewcompose.maps.google.GoogleMapProperties
import com.viewcompose.maps.google.GoogleMapView
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.UiTreeBuilder

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

/** The independently published Media3 artifact exposes both its DSL and Media3 Player contract. */
fun UiTreeBuilder.compileMedia3FeatureSurface(player: Media3Player) {
    Media3PlayerView(player = player)
}

/** Media3 and legacy ExoPlayer compile together without aliases supplied by ViewCompose. */
@Suppress("DEPRECATION")
fun UiTreeBuilder.compileLegacyExoPlayerFeatureSurface(player: LegacyPlayer) {
    ExoPlayerView(player = player)
}

/** The independently published Maps artifact exposes its typed state and Maps SDK contracts. */
fun UiTreeBuilder.compileGoogleMapsFeatureSurface() {
    GoogleMapView(properties = GoogleMapProperties())
}
