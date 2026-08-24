package com.viewcompose.media3

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
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3UiR
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
class Media3PlayerViewTest {
    @Test
    fun `player attaches only while lifecycle is started and caller retains release ownership`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.CREATED) }
        val player = RecordingPlayer()

        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                Media3PlayerView(player = player, key = "player")
            }
        }
        val playerView = root.requireDescendant<PlayerView>()
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
                Media3PlayerView(player = player, key = "player")
            }
        }
        val mounted = root.requireDescendant<PlayerView>()
        assertTrue(first.videoOutput is SurfaceView)

        player = second
        session.render()

        assertSame(mounted, root.requireDescendant<PlayerView>())
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
        var surfaceType = Media3SurfaceType.SurfaceView
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                Media3PlayerView(
                    player = null,
                    surfaceType = surfaceType,
                    key = "player",
                )
            }
        }
        val surfacePlayerView = root.requireDescendant<PlayerView>()
        assertTrue(surfacePlayerView.videoSurfaceView is SurfaceView)

        surfaceType = Media3SurfaceType.TextureView
        session.render()
        val texturePlayerView = root.requireDescendant<PlayerView>()
        assertTrue(surfacePlayerView !== texturePlayerView)
        assertTrue(texturePlayerView.videoSurfaceView is TextureView)

        surfaceType = Media3SurfaceType.None
        session.render()
        val noSurfacePlayerView = root.requireDescendant<PlayerView>()
        assertTrue(texturePlayerView !== noSurfacePlayerView)
        assertNull(noSurfacePlayerView.videoSurfaceView)

        session.dispose()
    }

    @Test
    fun `complete configuration maps to native player view and validates timeout`() {
        val root = FrameLayout(RuntimeEnvironment.getApplication())
        val owner = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val configuration = Media3PlayerViewConfiguration(
            resizeMode = Media3ResizeMode.Zoom,
            useController = false,
            controllerShowTimeoutMillis = 0,
            controllerAutoShow = false,
            controllerHideOnTouch = false,
            showBuffering = Media3ShowBuffering.Always,
            keepContentOnPlayerReset = true,
            artworkDisplayMode = Media3ArtworkDisplayMode.Fill,
            shutterBackgroundColor = Color.MAGENTA,
            contentDescription = "Fixture player",
            keepScreenOn = true,
            customErrorMessage = "Fixture error",
        )
        val session = renderInto(root) {
            ProvideLifecycleOwner(owner) {
                Media3PlayerView(player = null, configuration = configuration)
            }
        }
        val view = root.requireDescendant<PlayerView>()

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, view.resizeMode)
        assertFalse(view.useController)
        assertEquals(0, view.controllerShowTimeoutMs)
        assertFalse(view.controllerAutoShow)
        assertFalse(view.controllerHideOnTouch)
        assertEquals(PlayerView.ARTWORK_DISPLAY_MODE_FILL, view.artworkDisplayMode)
        assertEquals("Fixture player", view.contentDescription)
        assertTrue(view.keepScreenOn)
        assertEquals(
            "Fixture error",
            view.findViewById<TextView>(Media3UiR.id.exo_error_message).text,
        )
        assertThrows(IllegalArgumentException::class.java) {
            Media3PlayerViewConfiguration(controllerShowTimeoutMillis = -1)
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
                Media3PlayerView(
                    player = player,
                    onRenderedFirstFrame = { callbackCount++ },
                )
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
