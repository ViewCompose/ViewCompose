@file:Suppress("DEPRECATION")

package com.viewcompose.exoplayer2

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.AndroidViewCommitScope
import com.viewcompose.host.android.AndroidViewCreateScope
import com.viewcompose.host.android.AndroidViewResetReason
import com.viewcompose.host.android.AndroidViewResetScope
import com.viewcompose.host.android.AndroidViewReusePolicy
import com.viewcompose.host.android.AndroidViewUpdateScope
import com.viewcompose.lifecycle.AndroidViewLifecycleEventScope
import com.viewcompose.lifecycle.LifecycleAndroidViewAdapter
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier

/** Selects the constructor-owned video output hosted by [ExoPlayerView]. */
enum class ExoPlayerSurfaceType {
    /** Uses the legacy SDK's preferred [android.view.SurfaceView] output. */
    SurfaceView,

    /** Uses [android.view.TextureView] when transforms or animation require it. */
    TextureView,

    /** Creates no video output while retaining controls, artwork, and playback state. */
    None,
}

/** Selects how legacy ExoPlayer content is sized inside the native player bounds. */
enum class ExoPlayerResizeMode {
    /** Preserves aspect ratio and fits the whole video inside the bounds. */
    Fit,

    /** Preserves aspect ratio while fixing the content width to the bounds. */
    FixedWidth,

    /** Preserves aspect ratio while fixing the content height to the bounds. */
    FixedHeight,

    /** Stretches video to fill both dimensions. */
    Fill,

    /** Preserves aspect ratio and crops overflow to fill the bounds. */
    Zoom,
}

/** Selects when the legacy buffering indicator is visible. */
enum class ExoPlayerShowBuffering {
    /** Never shows the buffering indicator. */
    Never,

    /** Shows the indicator only while playback intends to advance. */
    WhenPlaying,

    /** Shows the indicator whenever the player reports buffering. */
    Always,
}

/** Selects how legacy ExoPlayer artwork is presented when video is unavailable. */
enum class ExoPlayerArtworkDisplayMode {
    /** Disables artwork presentation. */
    Off,

    /** Fits artwork inside the bounds while preserving its aspect ratio. */
    Fit,

    /** Fills the bounds with artwork and may crop overflow. */
    Fill,
}

/**
 * Defines complete replay-safe configuration for one legacy ExoPlayer View.
 *
 * Every property is reapplied during renderer rollback, so configuration never depends on the
 * preceding native View state. [player][ExoPlayerView] ownership is deliberately excluded: the
 * caller creates, commands, and releases it.
 *
 * @property resizeMode video sizing policy within the native player bounds
 * @property useController whether the legacy native playback controls are enabled
 * @property controllerShowTimeoutMillis controller auto-hide timeout; zero keeps it visible
 * @property controllerAutoShow whether controls appear automatically for eligible playback states
 * @property controllerHideOnTouch whether a touch hides visible controls
 * @property controllerHideDuringAds whether controls hide during ads
 * @property showBuffering buffering indicator policy
 * @property keepContentOnPlayerReset whether the last frame or artwork remains after player reset
 * @property artworkDisplayMode artwork policy when video is unavailable
 * @property defaultArtwork optional caller-owned fallback artwork
 * @property shutterBackgroundColor opaque or translucent Android color applied to the shutter
 * @property contentDescription accessibility description applied to the native player View
 * @property keepScreenOn whether the player View requests that the display remain awake
 * @property customErrorMessage optional caller-supplied error text replacing ExoPlayer error output
 */
data class ExoPlayerViewConfiguration(
    val resizeMode: ExoPlayerResizeMode = ExoPlayerResizeMode.Fit,
    val useController: Boolean = true,
    val controllerShowTimeoutMillis: Int = 5_000,
    val controllerAutoShow: Boolean = true,
    val controllerHideOnTouch: Boolean = true,
    val controllerHideDuringAds: Boolean = true,
    val showBuffering: ExoPlayerShowBuffering = ExoPlayerShowBuffering.Never,
    val keepContentOnPlayerReset: Boolean = false,
    val artworkDisplayMode: ExoPlayerArtworkDisplayMode = ExoPlayerArtworkDisplayMode.Fit,
    val defaultArtwork: Drawable? = null,
    @ColorInt val shutterBackgroundColor: Int = Color.BLACK,
    val contentDescription: CharSequence? = null,
    val keepScreenOn: Boolean = false,
    val customErrorMessage: CharSequence? = null,
) {
    init {
        require(controllerShowTimeoutMillis >= 0) {
            "controllerShowTimeoutMillis must be at least zero."
        }
    }

    /** Provides the stable default configuration. */
    companion object {
        /** Stable default configuration matching ordinary legacy playback presentation. */
        @JvmField
        val Default: ExoPlayerViewConfiguration = ExoPlayerViewConfiguration()
    }
}

/**
 * Hosts a legacy ExoPlayer 2 [Player] in a lifecycle-aware native [StyledPlayerView].
 *
 * This Q3 compatibility integration attaches [player] only while the nearest AndroidX lifecycle
 * is started and detaches it before stop, owner replacement, reuse, or release. The caller retains
 * exclusive ownership of playback commands and must release the player after its ViewCompose host
 * has ended. The player's application looper must be Android's main looper.
 *
 * [surfaceType] is construction identity: changing it atomically replaces the native player View.
 * Other values are complete replay-safe state and update the existing View. A nullable [player]
 * provides a deterministic Preview or loading placeholder without transferring ownership.
 * [onRenderedFirstFrame] runs on the Android main thread only for the currently committed and
 * started attachment; it must not retain framework callback scopes.
 *
 * @sample com.viewcompose.exoplayer2.samples.exoPlayerViewSample
 * @receiver tree builder receiving the legacy native player node
 * @param player caller-owned legacy ExoPlayer 2 player, or `null` for an unattached placeholder
 * @param modifier declarative layout, input, semantics, and native configuration
 * @param surfaceType constructor-owned video output strategy
 * @param configuration complete replay-safe native player View configuration
 * @param onRenderedFirstFrame optional notification for the active committed attachment
 * @param key optional stable logical identity used for keyed reconciliation
 */
fun UiTreeBuilder.ExoPlayerView(
    player: Player?,
    modifier: Modifier = Modifier,
    surfaceType: ExoPlayerSurfaceType = ExoPlayerSurfaceType.SurfaceView,
    configuration: ExoPlayerViewConfiguration = ExoPlayerViewConfiguration.Default,
    onRenderedFirstFrame: (() -> Unit)? = null,
    key: Any? = null,
) {
    val owner = checkNotNull(LocalLifecycleOwner.current) {
        "ExoPlayerView requires a LifecycleOwner from the Android host or ProvideLifecycleOwner."
    }
    AndroidView(
        adapter = ExoPlayerViewAdapter(surfaceType),
        state = ExoPlayerViewState(
            lifecycleOwner = owner,
            player = player,
            configuration = configuration,
            onRenderedFirstFrame = onRenderedFirstFrame,
        ),
        key = key,
        constructionKey = surfaceType,
        modifier = modifier,
    )
}

private data class ExoPlayerViewState(
    val lifecycleOwner: LifecycleOwner,
    val player: Player?,
    val configuration: ExoPlayerViewConfiguration,
    val onRenderedFirstFrame: (() -> Unit)?,
)

private class ExoPlayerViewAdapter(
    private val surfaceType: ExoPlayerSurfaceType,
) : LifecycleAndroidViewAdapter<StyledPlayerView, ExoPlayerViewState>() {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Resettable

    override fun lifecycleOwner(state: ExoPlayerViewState): LifecycleOwner = state.lifecycleOwner

    override fun create(scope: AndroidViewCreateScope): StyledPlayerView {
        val layout = when (surfaceType) {
            ExoPlayerSurfaceType.SurfaceView -> R.layout.viewcompose_exoplayer2_surface_view
            ExoPlayerSurfaceType.TextureView -> R.layout.viewcompose_exoplayer2_texture_view
            ExoPlayerSurfaceType.None -> R.layout.viewcompose_exoplayer2_no_surface
        }
        return LayoutInflater.from(scope.context).inflate(layout, null, false) as StyledPlayerView
    }

    override fun update(
        scope: AndroidViewUpdateScope<StyledPlayerView>,
        state: ExoPlayerViewState,
    ) {
        scope.view.applyConfiguration(state.configuration)
    }

    override fun onViewCommit(
        scope: AndroidViewCommitScope<StyledPlayerView>,
        state: ExoPlayerViewState,
    ) {
        val binding = ExoPlayerBindingStore.bindingFor(scope.view)
        try {
            binding.commit(scope.view, state.player, state.onRenderedFirstFrame)
        } catch (error: Throwable) {
            val cleanupFailure = runCatching {
                ExoPlayerBindingStore.remove(scope.view)?.clear(scope.view)
            }.exceptionOrNull()
            cleanupFailure?.let(error::addSuppressed)
            throw error
        }
    }

    override fun onLifecycleEvent(
        scope: AndroidViewLifecycleEventScope<StyledPlayerView>,
        state: ExoPlayerViewState,
        event: Lifecycle.Event,
    ) {
        val binding = ExoPlayerBindingStore.bindingFor(scope.view)
        when (event) {
            Lifecycle.Event.ON_CREATE -> Unit
            Lifecycle.Event.ON_START -> binding.start(
                view = scope.view,
                player = state.player,
                callback = state.onRenderedFirstFrame,
            )

            Lifecycle.Event.ON_RESUME -> scope.view.onResume()
            Lifecycle.Event.ON_PAUSE -> scope.view.onPause()
            Lifecycle.Event.ON_STOP,
            Lifecycle.Event.ON_DESTROY,
            -> binding.stop(scope.view)

            Lifecycle.Event.ON_ANY -> Unit
        }
    }

    override fun onViewReset(
        scope: AndroidViewResetScope<StyledPlayerView>,
        reason: AndroidViewResetReason,
    ) {
        ExoPlayerBindingStore.remove(scope.view)?.clear(scope.view)
    }

    override fun onViewRelease(view: StyledPlayerView) {
        ExoPlayerBindingStore.remove(view)?.clear(view)
    }
}

private fun StyledPlayerView.applyConfiguration(configuration: ExoPlayerViewConfiguration) {
    resizeMode = when (configuration.resizeMode) {
        ExoPlayerResizeMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        ExoPlayerResizeMode.FixedWidth -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
        ExoPlayerResizeMode.FixedHeight -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        ExoPlayerResizeMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        ExoPlayerResizeMode.Zoom -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
    useController = configuration.useController
    controllerShowTimeoutMs = configuration.controllerShowTimeoutMillis
    controllerAutoShow = configuration.controllerAutoShow
    controllerHideOnTouch = configuration.controllerHideOnTouch
    setControllerHideDuringAds(configuration.controllerHideDuringAds)
    setShowBuffering(
        when (configuration.showBuffering) {
            ExoPlayerShowBuffering.Never -> StyledPlayerView.SHOW_BUFFERING_NEVER
            ExoPlayerShowBuffering.WhenPlaying -> StyledPlayerView.SHOW_BUFFERING_WHEN_PLAYING
            ExoPlayerShowBuffering.Always -> StyledPlayerView.SHOW_BUFFERING_ALWAYS
        },
    )
    setKeepContentOnPlayerReset(configuration.keepContentOnPlayerReset)
    setArtworkDisplayMode(
        when (configuration.artworkDisplayMode) {
            ExoPlayerArtworkDisplayMode.Off -> StyledPlayerView.ARTWORK_DISPLAY_MODE_OFF
            ExoPlayerArtworkDisplayMode.Fit -> StyledPlayerView.ARTWORK_DISPLAY_MODE_FIT
            ExoPlayerArtworkDisplayMode.Fill -> StyledPlayerView.ARTWORK_DISPLAY_MODE_FILL
        },
    )
    defaultArtwork = configuration.defaultArtwork
    setShutterBackgroundColor(configuration.shutterBackgroundColor)
    contentDescription = configuration.contentDescription
    keepScreenOn = configuration.keepScreenOn
    setCustomErrorMessage(configuration.customErrorMessage)
}

private object ExoPlayerBindingStore {
    fun bindingFor(view: StyledPlayerView): ExoPlayerBinding {
        val existing = view.getTag(R.id.viewcompose_exoplayer2_player_binding)
        check(existing == null || existing is ExoPlayerBinding) {
            "Legacy ExoPlayer binding tag is owned by an incompatible value."
        }
        return (existing as? ExoPlayerBinding) ?: ExoPlayerBinding().also { binding ->
            view.setTag(R.id.viewcompose_exoplayer2_player_binding, binding)
        }
    }

    fun remove(view: StyledPlayerView): ExoPlayerBinding? {
        val existing = view.getTag(R.id.viewcompose_exoplayer2_player_binding)
        check(existing == null || existing is ExoPlayerBinding) {
            "Legacy ExoPlayer binding tag is owned by an incompatible value."
        }
        view.setTag(R.id.viewcompose_exoplayer2_player_binding, null)
        return existing as? ExoPlayerBinding
    }
}

private class ExoPlayerBinding {
    private var started = false
    private var committedPlayer: Player? = null
    private var committedCallback: (() -> Unit)? = null
    private var attachedPlayer: Player? = null
    private var attachedListener: Player.Listener? = null
    private var generation = 0L

    fun commit(
        view: StyledPlayerView,
        player: Player?,
        callback: (() -> Unit)?,
    ) {
        committedPlayer = player
        committedCallback = callback
        if (started) sync(view)
    }

    fun start(
        view: StyledPlayerView,
        player: Player?,
        callback: (() -> Unit)?,
    ) {
        committedPlayer = player
        committedCallback = callback
        started = true
        sync(view)
    }

    fun stop(view: StyledPlayerView) {
        started = false
        detach(view)
    }

    fun clear(view: StyledPlayerView) {
        started = false
        committedPlayer = null
        committedCallback = null
        detach(view)
    }

    private fun sync(view: StyledPlayerView) {
        val targetPlayer = committedPlayer
        if (!started || targetPlayer == null) {
            detach(view)
            return
        }
        if (attachedPlayer !== targetPlayer) {
            detach(view)
            attach(view, targetPlayer)
            return
        }
        syncListener(targetPlayer)
    }

    private fun attach(view: StyledPlayerView, player: Player) {
        val nextGeneration = ++generation
        attachedPlayer = player
        try {
            if (committedCallback != null) {
                val listener = firstFrameListener(nextGeneration)
                attachedListener = listener
                player.addListener(listener)
            }
            view.player = player
        } catch (error: Throwable) {
            val listener = attachedListener
            attachedPlayer = null
            attachedListener = null
            generation++
            listener?.let { current ->
                runCatching { player.removeListener(current) }
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
            }
            runCatching {
                if (view.player === player) view.player = null
            }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun syncListener(player: Player) {
        val listener = attachedListener
        when {
            committedCallback == null && listener != null -> {
                attachedListener = null
                generation++
                player.removeListener(listener)
            }

            committedCallback != null && listener == null -> {
                val nextGeneration = ++generation
                firstFrameListener(nextGeneration).also { next ->
                    attachedListener = next
                    player.addListener(next)
                }
            }
        }
    }

    private fun detach(view: StyledPlayerView) {
        val player = attachedPlayer ?: run {
            if (view.player != null) view.player = null
            return
        }
        val listener = attachedListener
        attachedPlayer = null
        attachedListener = null
        generation++
        var failure: Throwable? = null
        if (listener != null) {
            failure = captureFailure(failure) { player.removeListener(listener) }
        }
        failure = captureFailure(failure) {
            if (view.player != null) view.player = null
        }
        failure?.let { throw it }
    }

    private fun firstFrameListener(listenerGeneration: Long): Player.Listener {
        return object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (
                    started &&
                    attachedListener === this &&
                    generation == listenerGeneration &&
                    attachedPlayer === committedPlayer
                ) {
                    committedCallback?.invoke()
                }
            }
        }
    }
}

private fun captureFailure(
    previous: Throwable?,
    block: () -> Unit,
): Throwable? {
    return try {
        block()
        previous
    } catch (error: Throwable) {
        if (previous == null) error else previous.apply { addSuppressed(error) }
    }
}
