@file:Suppress("DEPRECATION")

package com.viewcompose.exoplayer2.samples

import com.google.android.exoplayer2.Player
import com.viewcompose.exoplayer2.ExoPlayerShowBuffering
import com.viewcompose.exoplayer2.ExoPlayerSurfaceType
import com.viewcompose.exoplayer2.ExoPlayerView
import com.viewcompose.exoplayer2.ExoPlayerViewConfiguration
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Demonstrates the Q3 caller-owned legacy player contract. */
// DOCS_REGION_START(exoplayer2-player)
fun UiTreeBuilder.exoPlayerViewSample(player: Player) {
    ExoPlayerView(
        player = player,
        surfaceType = ExoPlayerSurfaceType.SurfaceView,
        configuration = ExoPlayerViewConfiguration(
            showBuffering = ExoPlayerShowBuffering.WhenPlaying,
            contentDescription = "Legacy episode video",
        ),
    )
}
// DOCS_REGION_END(exoplayer2-player)
