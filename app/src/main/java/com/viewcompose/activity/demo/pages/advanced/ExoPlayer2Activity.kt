@file:Suppress("DEPRECATION")

package com.viewcompose

import android.view.ViewGroup
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Dedicated caller owner for the legacy ExoPlayer compatibility fixture. */
class ExoPlayer2Activity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_exoplayer2_title

    private lateinit var firstPlayer: ExoPlayer
    private lateinit var secondPlayer: ExoPlayer

    override fun installDemoContent() {
        firstPlayer = createFixturePlayer()
        secondPlayer = createFixturePlayer()
        super.installDemoContent()
    }

    override fun buildDemoContent(
        root: ViewGroup,
        builder: UiTreeBuilder,
    ) {
        builder.ExoPlayer2DemoPage(
            scenario = checkNotNull(currentScenario()) {
                "ExoPlayer2Activity requires the registered legacy ExoPlayer scenario"
            },
            firstPlayer = firstPlayer,
            secondPlayer = secondPlayer,
        )
    }

    override fun onDestroy() {
        // Dispose ViewCompose first so the compatibility layer clears listener and Surface state
        // before the Activity exercises its exclusive caller-owned release responsibility.
        super.onDestroy()
        firstPlayer.release()
        secondPlayer.release()
    }

    private fun createFixturePlayer(): ExoPlayer {
        return ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(FIXTURE_URI))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
}

private const val FIXTURE_URI = "asset:///media3/viewcompose_media3_fixture.mp4"
