@file:Suppress("DEPRECATION")

package com.viewcompose.exoplayer2

import android.graphics.Color
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleBasePlayer
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.R as ExoPlayerUiR
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.viewcompose.host.android.renderInto
import com.viewcompose.lifecycle.ProvideLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ExoPlayerViewTest {
    @Test
    fun `player attaches only while lifecycle is started and caller retains release ownership`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.CREATED) }
        val player = RecordingPlayer()

        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                ExoPlayerView(player = player, key = "player")
            }
        }
        val playerView = root.requireDescendant<StyledPlayerView>()
        assertNull(playerView.player)
        assertNull(player.videoOutput)

        owner.moveTo(Lifecycle.State.STARTED)
        assertSame(player, playerView.player)
        assertTrue(player.videoOutput is SurfaceView)

        owner.moveTo(Lifecycle.State.CREATED)
        assertNull(playerView.player)
        assertNull(player.videoOutput)

        owner.moveTo(Lifecycle.State.RESUMED)
        assertSame(player, playerView.player)
        assertTrue(player.videoOutput is SurfaceView)

        session.dispose()
        assertNull(player.videoOutput)
        assertFalse(player.releaseRequested)
    }

    @Test
    fun `same owner player replacement reuses view and clears the old surface`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val first = RecordingPlayer()
        val second = RecordingPlayer()
        var player: Player = first
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                ExoPlayerView(player = player, key = "player")
            }
        }
        val mounted = root.requireDescendant<StyledPlayerView>()
        assertTrue(first.videoOutput is SurfaceView)

        player = second
        session.render()

        assertSame(mounted, root.requireDescendant<StyledPlayerView>())
        assertNull(first.videoOutput)
        assertTrue(first.clearedVideoOutputCount > 0)
        assertSame(second, mounted.player)
        assertTrue(second.videoOutput is SurfaceView)

        session.dispose()
    }

    @Test
    fun `surface type is construction identity and selects the exact native output`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        var surfaceType = ExoPlayerSurfaceType.SurfaceView
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                ExoPlayerView(player = null, surfaceType = surfaceType, key = "player")
            }
        }
        val surfacePlayerView = root.requireDescendant<StyledPlayerView>()
        assertTrue(surfacePlayerView.videoSurfaceView is SurfaceView)

        surfaceType = ExoPlayerSurfaceType.TextureView
        session.render()
        val texturePlayerView = root.requireDescendant<StyledPlayerView>()
        assertTrue(surfacePlayerView !== texturePlayerView)
        assertTrue(texturePlayerView.videoSurfaceView is TextureView)

        surfaceType = ExoPlayerSurfaceType.None
        session.render()
        val noSurfacePlayerView = root.requireDescendant<StyledPlayerView>()
        assertTrue(texturePlayerView !== noSurfacePlayerView)
        assertNull(noSurfacePlayerView.videoSurfaceView)

        session.dispose()
    }

    @Test
    fun `complete configuration maps to styled player view and validates timeout`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val configuration = ExoPlayerViewConfiguration(
            resizeMode = ExoPlayerResizeMode.Zoom,
            useController = false,
            controllerShowTimeoutMillis = 0,
            controllerAutoShow = false,
            controllerHideOnTouch = false,
            showBuffering = ExoPlayerShowBuffering.Always,
            keepContentOnPlayerReset = true,
            artworkDisplayMode = ExoPlayerArtworkDisplayMode.Fill,
            shutterBackgroundColor = Color.MAGENTA,
            contentDescription = "Legacy fixture player",
            keepScreenOn = true,
            customErrorMessage = "Legacy fixture error",
        )
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                ExoPlayerView(player = null, configuration = configuration)
            }
        }
        val view = root.requireDescendant<StyledPlayerView>()

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, view.resizeMode)
        assertFalse(view.useController)
        assertEquals(0, view.controllerShowTimeoutMs)
        assertFalse(view.controllerAutoShow)
        assertFalse(view.controllerHideOnTouch)
        assertEquals(StyledPlayerView.ARTWORK_DISPLAY_MODE_FILL, view.artworkDisplayMode)
        assertEquals("Legacy fixture player", view.contentDescription)
        assertTrue(view.keepScreenOn)
        assertEquals(
            "Legacy fixture error",
            view.findViewById<TextView>(ExoPlayerUiR.id.exo_error_message).text,
        )
        assertThrows(IllegalArgumentException::class.java) {
            ExoPlayerViewConfiguration(controllerShowTimeoutMillis = -1)
        }

        session.dispose()
    }

    @Test
    fun `first frame callback follows the active committed attachment`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val first = RecordingPlayer()
        val second = RecordingPlayer()
        var player: Player = first
        var callbackCount = 0
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                ExoPlayerView(player = player, onRenderedFirstFrame = { callbackCount++ })
            }
        }

        first.emitFirstFrame()
        assertEquals(1, callbackCount)

        player = second
        session.render()
        first.emitFirstFrame()
        assertEquals(1, callbackCount)
        second.emitFirstFrame()
        assertEquals(2, callbackCount)

        owner.moveTo(Lifecycle.State.CREATED)
        second.emitFirstFrame()
        assertEquals(2, callbackCount)

        session.dispose()
    }

    @Test
    fun `legacy artifact runtime does not contain Media3`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("androidx.media3.common.Player")
        }
    }

    private class RecordingPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        private var playerState = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .build()

        var videoOutput: Any? = null
            private set
        var clearedVideoOutputCount: Int = 0
            private set
        var releaseRequested: Boolean = false
            private set

        override fun getState(): State = playerState

        override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
            this.videoOutput = videoOutput
            return Futures.immediateVoidFuture()
        }

        override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> {
            if (this.videoOutput === videoOutput) this.videoOutput = null
            clearedVideoOutputCount++
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> {
            releaseRequested = true
            return Futures.immediateVoidFuture()
        }

        fun emitFirstFrame() {
            playerState = playerState.buildUpon().setNewlyRenderedFirstFrame(true).build()
            invalidateState()
            shadowOf(Looper.getMainLooper()).idle()
            playerState = playerState.buildUpon().setNewlyRenderedFirstFrame(false).build()
            invalidateState()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }
}

private inline fun <reified T : View> View.requireDescendant(): T =
    requireDescendant(T::class.java)

private fun <T : View> View.requireDescendant(type: Class<T>): T {
    if (type.isInstance(this)) return type.cast(this)
    if (this is ViewGroup) {
        repeat(childCount) { index ->
            val child = getChildAt(index)
            runCatching { child.requireDescendant(type) }.getOrNull()?.let { return it }
        }
    }
    error("Missing descendant ${type.name}")
}
