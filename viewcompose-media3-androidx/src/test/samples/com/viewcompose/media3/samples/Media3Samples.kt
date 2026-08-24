package com.viewcompose.media3.samples

import androidx.media3.common.Player
import com.viewcompose.media3.Media3PlayerView
import com.viewcompose.media3.Media3PlayerViewConfiguration
import com.viewcompose.media3.Media3ShowBuffering
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Hosts a caller-owned player without transferring playback or release ownership. */
fun UiTreeBuilder.media3PlayerViewSample(player: Player) {
    Media3PlayerView(
        player = player,
        configuration = Media3PlayerViewConfiguration(
            useController = true,
            showBuffering = Media3ShowBuffering.WhenPlaying,
            contentDescription = "Article video",
        ),
        onRenderedFirstFrame = {
            // Update caller-owned UI state or diagnostics here.
        },
    )
}
