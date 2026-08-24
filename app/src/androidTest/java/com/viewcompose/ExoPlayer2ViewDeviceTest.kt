@file:Suppress("DEPRECATION")

package com.viewcompose

import android.os.SystemClock
import android.view.SurfaceView
import android.view.TextureView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.exoplayer2.ui.StyledPlayerView
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExoPlayer2ViewDeviceTest {
    @Test
    fun localFixture_survivesPlayerSurfaceAndLifecycleReplacement() {
        launchDemoScenarioActivity(
            activityClass = ExoPlayer2Activity::class.java,
            scenarioId = "media.exoplayer2-player-view",
        ).use { scenario ->
            waitForUiIdle()
            waitUntil("the legacy local fixture renders its first frame") {
                scenario.readLegacyStatusCount() > 0 &&
                    scenario.readLegacyPlayerView().player != null
            }

            val surfaceViewPlayerView = scenario.readLegacyPlayerView()
            val firstPlayer = requireNotNull(surfaceViewPlayerView.player)
            assertTrue(surfaceViewPlayerView.videoSurfaceView is SurfaceView)

            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_media_exoplayer2_player_view_primary_action)
            }
            waitForUiIdle()
            waitUntil("the second legacy player replaces the first") {
                val mounted = scenario.readLegacyPlayerView()
                mounted === surfaceViewPlayerView &&
                    mounted.player != null &&
                    mounted.player !== firstPlayer
            }
            val secondPlayer = requireNotNull(scenario.readLegacyPlayerView().player)

            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_media_exoplayer2_player_view_secondary_action)
            }
            waitForUiIdle()
            waitUntil("TextureView construction replaces the legacy native player View") {
                val mounted = scenario.readLegacyPlayerView()
                mounted !== surfaceViewPlayerView &&
                    mounted.videoSurfaceView is TextureView &&
                    mounted.player === secondPlayer
            }
            assertNull(surfaceViewPlayerView.player)

            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil("the stopped Activity detaches its legacy player") {
                scenario.readLegacyPlayerView().player == null
            }

            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForUiIdle()
            waitUntil("the resumed Activity reattaches the same legacy player") {
                scenario.readLegacyPlayerView().player === secondPlayer
            }
            assertSame(secondPlayer, scenario.readLegacyPlayerView().player)
        }
    }
}

private fun ActivityScenario<ExoPlayer2Activity>.readLegacyPlayerView(): StyledPlayerView {
    var value: StyledPlayerView? = null
    onActivity { activity ->
        value = activity.requireScenarioViewById(R.id.demo_media_exoplayer2_player_view_target)
    }
    return requireNotNull(value)
}

private fun ActivityScenario<ExoPlayer2Activity>.readLegacyStatusCount(): Int {
    var text = ""
    onActivity { activity ->
        text = activity.requireScenarioViewById<TextView>(
            R.id.demo_media_exoplayer2_player_view_state,
        ).text.toString()
    }
    return LEGACY_COUNT_AT_END.find(text)?.value?.toIntOrNull() ?: 0
}

private fun waitUntil(
    description: String,
    timeoutMillis: Long = 8_000L,
    condition: () -> Boolean,
) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (SystemClock.uptimeMillis() < deadline) {
        if (condition()) return
        SystemClock.sleep(50L)
    }
    assertTrue("Timed out waiting for $description", condition())
}

private val LEGACY_COUNT_AT_END = Regex("(\\d+)\\s*$")
