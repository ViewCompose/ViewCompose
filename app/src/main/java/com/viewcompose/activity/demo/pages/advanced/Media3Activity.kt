package com.viewcompose

import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.viewcompose.ui.foundation.UiTreeBuilder

/** Dedicated owner for the Media3 players exercised by the integration fixture. */
class Media3Activity : DemoRenderActivity() {
    override val demoTitleRes: Int = R.string.demo_activity_media3_title

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
        builder.Media3DemoPage(
            scenario = checkNotNull(currentScenario()) {
                "Media3Activity requires the registered Media3 scenario"
            },
            firstPlayer = firstPlayer,
            secondPlayer = secondPlayer,
        )
    }

    override fun onDestroy() {
        // End the ViewCompose session first so it detaches every listener and video output before
        // the Activity exercises its exclusive caller-owned release responsibility.
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
