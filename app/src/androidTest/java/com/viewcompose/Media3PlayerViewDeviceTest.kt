package com.viewcompose

import android.os.SystemClock
import android.view.SurfaceView
import android.view.TextureView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3PlayerViewDeviceTest {
    @Test
    fun localFixture_survivesPlayerSurfaceAndLifecycleReplacement() {
        launchDemoScenarioActivity(
            activityClass = Media3Activity::class.java,
            scenarioId = "media.media3-player-view",
        ).use { scenario ->
            waitForUiIdle()
            waitUntil("the local fixture renders its first frame") {
                scenario.readStatusCount() > 0 &&
                    scenario.readPlayerView().player != null
            }

            val surfaceViewPlayerView = scenario.readPlayerView()
            val firstPlayer = requireNotNull(surfaceViewPlayerView.player)
            assertTrue(surfaceViewPlayerView.videoSurfaceView is SurfaceView)

            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_media_media3_player_view_primary_action)
            }
            waitForUiIdle()
            waitUntil("the second Activity-owned player replaces the first") {
                val mounted = scenario.readPlayerView()
                mounted === surfaceViewPlayerView &&
                    mounted.player != null &&
                    mounted.player !== firstPlayer
            }
            val secondPlayer = requireNotNull(scenario.readPlayerView().player)

            scenario.onActivity { activity ->
                activity.clickScenarioViewById(R.id.demo_media_media3_player_view_secondary_action)
            }
            waitForUiIdle()
            waitUntil("TextureView construction replaces the native player View") {
                val mounted = scenario.readPlayerView()
                mounted !== surfaceViewPlayerView &&
                    mounted.videoSurfaceView is TextureView &&
                    mounted.player === secondPlayer
            }
            assertNull(surfaceViewPlayerView.player)

            scenario.moveToState(Lifecycle.State.CREATED)
            waitUntil("the stopped Activity detaches its player") {
                scenario.readPlayerView().player == null
            }

            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForUiIdle()
            waitUntil("the resumed Activity reattaches the same caller-owned player") {
                scenario.readPlayerView().player === secondPlayer
            }
            assertSame(secondPlayer, scenario.readPlayerView().player)
        }
    }
}

private fun ActivityScenario<Media3Activity>.readPlayerView(): PlayerView {
    var value: PlayerView? = null
    onActivity { activity ->
        value = activity.requireScenarioViewById(R.id.demo_media_media3_player_view_target)
    }
    return requireNotNull(value)
}

private fun ActivityScenario<Media3Activity>.readStatusCount(): Int {
    var text = ""
    onActivity { activity ->
        text = activity.requireScenarioViewById<TextView>(
            R.id.demo_media_media3_player_view_state,
        ).text.toString()
    }
    return COUNT_AT_END.find(text)?.value?.toIntOrNull() ?: 0
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

private val COUNT_AT_END = Regex("(\\d+)\\s*$")
