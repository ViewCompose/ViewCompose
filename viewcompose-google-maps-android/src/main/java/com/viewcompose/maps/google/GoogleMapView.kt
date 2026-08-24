package com.viewcompose.maps.google

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapColorScheme
import com.viewcompose.host.android.AndroidView
import com.viewcompose.host.android.AndroidViewCommitScope
import com.viewcompose.host.android.AndroidViewCreateScope
import com.viewcompose.host.android.AndroidViewReusePolicy
import com.viewcompose.host.android.AndroidViewUpdateScope
import com.viewcompose.lifecycle.AndroidViewLifecycleEventScope
import com.viewcompose.lifecycle.AndroidViewSavedStateBindResult
import com.viewcompose.lifecycle.LifecycleAndroidViewAdapter
import com.viewcompose.lifecycle.LocalLifecycleOwner
import com.viewcompose.lifecycle.LocalSavedStateRegistryOwner
import com.viewcompose.lifecycle.bindAndroidViewSavedState
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier

/** Selects the Google base-map tile presentation controlled by [GoogleMapView]. */
enum class GoogleMapType {
    /** Hides base-map tiles while retaining overlays and camera interaction. */
    None,

    /** Shows the standard road map. */
    Normal,

    /** Shows satellite imagery. */
    Satellite,

    /** Shows terrain data. */
    Terrain,

    /** Shows satellite imagery with road and label overlays. */
    Hybrid,
}

/** Selects the runtime color scheme for normal and terrain Google base maps. */
enum class GoogleMapColorScheme {
    /** Uses the light map scheme independently of the system theme. */
    Light,

    /** Uses the dark map scheme independently of the system theme. */
    Dark,

    /** Follows the current Android night-mode configuration. */
    FollowSystem,
}

/**
 * Defines constructor-owned [MapView] behavior that requires native View replacement when changed.
 *
 * @property mapId optional Google Cloud map ID; blank IDs are rejected
 * @property liteMode whether the SDK creates a non-interactive lite map
 * @property zOrderOnTop whether the map Surface is placed above its containing window
 * @property backgroundColor optional opaque color shown before map tiles are available
 */
data class GoogleMapViewOptions(
    val mapId: String? = null,
    val liteMode: Boolean = false,
    val zOrderOnTop: Boolean = false,
    @ColorInt val backgroundColor: Int? = null,
) {
    init {
        require(mapId == null || mapId.isNotBlank()) { "mapId must be null or non-blank." }
        require(backgroundColor == null || Color.alpha(backgroundColor) == 255) {
            "backgroundColor must be opaque because Maps SDK does not support transparency."
        }
    }

    /** Provides the stable constructor defaults used by [GoogleMapView]. */
    companion object {
        /** Stable options that create an ordinary interactive [MapView]. */
        @JvmField
        val Default: GoogleMapViewOptions = GoogleMapViewOptions()
    }
}

/**
 * Defines replay-safe map rendering state applied after the current map becomes ready.
 *
 * A non-null [cameraPosition] is controlled state and overrides an SDK-restored camera when a new
 * map generation becomes ready. `null` leaves the restored or gesture-driven camera untouched.
 * [styleJson] is parsed by Maps SDK on every changed value; an invalid value leaves the preceding
 * style active and is reported through `GoogleMapView.onMapStyleApplied`.
 *
 * @property mapType base-map tile presentation
 * @property colorScheme light, dark, or system-following runtime scheme
 * @property trafficEnabled whether live traffic data is requested from Maps SDK
 * @property transitEnabled whether public-transit data is requested from Maps SDK
 * @property buildingsEnabled whether 3D buildings are displayed
 * @property indoorEnabled whether indoor maps may be displayed when available
 * @property minZoomPreference optional minimum zoom level, or `null` for the SDK default
 * @property maxZoomPreference optional maximum zoom level, or `null` for the SDK default
 * @property cameraTargetBounds optional bounds constraining gesture and programmatic camera targets
 * @property cameraPosition optional controlled camera position
 * @property styleJson optional Google Maps JSON style, or `null` to clear local JSON styling
 * @property contentDescription optional accessibility description assigned to the native map
 * @property padding edge insets in physical pixels used by SDK controls, logo, and camera updates
 */
data class GoogleMapProperties(
    val mapType: GoogleMapType = GoogleMapType.Normal,
    val colorScheme: GoogleMapColorScheme = GoogleMapColorScheme.Light,
    val trafficEnabled: Boolean = false,
    val transitEnabled: Boolean = false,
    val buildingsEnabled: Boolean = true,
    val indoorEnabled: Boolean = true,
    val minZoomPreference: Float? = null,
    val maxZoomPreference: Float? = null,
    val cameraTargetBounds: LatLngBounds? = null,
    val cameraPosition: CameraPosition? = null,
    val styleJson: String? = null,
    val contentDescription: String? = null,
    val padding: GoogleMapPadding = GoogleMapPadding.Zero,
) {
    init {
        require(minZoomPreference == null || minZoomPreference.isFinite()) {
            "minZoomPreference must be finite when present."
        }
        require(maxZoomPreference == null || maxZoomPreference.isFinite()) {
            "maxZoomPreference must be finite when present."
        }
        require(
            minZoomPreference == null ||
                maxZoomPreference == null ||
                minZoomPreference <= maxZoomPreference,
        ) { "minZoomPreference must not exceed maxZoomPreference." }
        require(styleJson == null || styleJson.isNotBlank()) {
            "styleJson must be null or non-blank."
        }
    }

    /** Provides the stable replay-safe defaults used by [GoogleMapView]. */
    companion object {
        /** Stable state matching ordinary Maps SDK runtime presentation. */
        @JvmField
        val Default: GoogleMapProperties = GoogleMapProperties()
    }
}

/**
 * Defines physical-pixel insets reserved around Google map content and built-in controls.
 *
 * @property left non-negative left inset in pixels
 * @property top non-negative top inset in pixels
 * @property right non-negative right inset in pixels
 * @property bottom non-negative bottom inset in pixels
 */
data class GoogleMapPadding(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) {
            "Google map padding values must be non-negative."
        }
    }

    /** Provides the zero-inset value used by [GoogleMapProperties]. */
    companion object {
        /** No reserved map-content inset. */
        @JvmField
        val Zero: GoogleMapPadding = GoogleMapPadding()
    }
}

/**
 * Defines replay-safe built-in control visibility and gesture policy for [GoogleMapView].
 *
 * @property zoomControlsEnabled whether the SDK zoom buttons are visible
 * @property compassEnabled whether the compass is visible when the camera is rotated
 * @property myLocationButtonEnabled whether the location button is visible when the caller enables
 * the location layer through its own permission and map policy
 * @property indoorLevelPickerEnabled whether the indoor floor picker is visible
 * @property mapToolbarEnabled whether the Google Maps toolbar is visible after marker selection
 * @property scrollGesturesEnabled whether one-finger camera scrolling is enabled
 * @property zoomGesturesEnabled whether zoom gestures are enabled
 * @property tiltGesturesEnabled whether camera tilt gestures are enabled
 * @property rotateGesturesEnabled whether camera rotation gestures are enabled
 * @property scrollGesturesEnabledDuringRotateOrZoom whether scrolling may accompany rotate or zoom
 */
data class GoogleMapUiSettings(
    val zoomControlsEnabled: Boolean = false,
    val compassEnabled: Boolean = true,
    val myLocationButtonEnabled: Boolean = true,
    val indoorLevelPickerEnabled: Boolean = true,
    val mapToolbarEnabled: Boolean = true,
    val scrollGesturesEnabled: Boolean = true,
    val zoomGesturesEnabled: Boolean = true,
    val tiltGesturesEnabled: Boolean = true,
    val rotateGesturesEnabled: Boolean = true,
    val scrollGesturesEnabledDuringRotateOrZoom: Boolean = true,
) {
    /** Provides the stable Maps SDK control and gesture defaults. */
    companion object {
        /** Stable UI policy matching an ordinary interactive map. */
        @JvmField
        val Default: GoogleMapUiSettings = GoogleMapUiSettings()
    }
}

/**
 * Hosts one lifecycle-aware Google Maps SDK [MapView] with declarative state and keyed overlays.
 *
 * This Q3 integration creates no credentials, network policy, location permission, or global
 * renderer preference. The nearest [LifecycleOwner] drives the exact native lifecycle after a
 * successful ViewCompose commit. [saveableStateKey] opts into one versioned SDK Bundle owned by the
 * nearest [SavedStateRegistryOwner]; changing the key, owner, or [options] replaces the native View.
 * Without a key the SDK receives no process-restoration payload.
 *
 * Map-ready and event callbacks run on the Android main thread and always use the latest committed
 * callback. A map-ready callback from a released or replaced generation is ignored. [onMapReady]
 * runs once for each native map generation after controlled state and overlays are installed; do
 * not retain that [GoogleMap] beyond component release. [onMapLoaded] follows the SDK's one-shot
 * contract and is not re-armed by recomposition; the latest committed callback receives that event.
 * Properties, UI settings, and content passed here remain integration-owned controlled fields;
 * direct mutations of those fields may be replaced by a later commit.
 *
 * @sample com.viewcompose.maps.google.samples.googleMapViewSample
 * @receiver ViewCompose tree receiving the native map node
 * @param modifier declarative layout, input, semantics, and native View configuration
 * @param options constructor-owned MapView options; a changed value replaces the native View
 * @param properties complete replay-safe map properties applied after map readiness
 * @param uiSettings complete replay-safe control and gesture policy
 * @param saveableStateKey optional stable process-restoration namespace unique within the owner
 * @param onMapReady optional one-shot notification for the current committed native map generation
 * @param onMapLoaded optional latest one-shot callback for renderer completion in this map generation
 * @param onMapClick optional latest callback for an unconsumed map tap
 * @param onCameraIdle optional latest callback with the camera position after movement stops
 * @param onMapStyleApplied optional callback receiving the parse result for each changed JSON style
 * @param key optional stable logical identity used for keyed ViewCompose reconciliation
 * @param content declarative keyed marker and polyline content for the map
 * @throws IllegalStateException when no usable lifecycle owner exists, or when restoration is
 * requested without a saved-state owner
 * @throws IllegalArgumentException for a blank restoration key or invalid/duplicate map content
 */
fun UiTreeBuilder.GoogleMapView(
    modifier: Modifier = Modifier,
    options: GoogleMapViewOptions = GoogleMapViewOptions.Default,
    properties: GoogleMapProperties = GoogleMapProperties.Default,
    uiSettings: GoogleMapUiSettings = GoogleMapUiSettings.Default,
    saveableStateKey: String? = null,
    onMapReady: ((GoogleMap) -> Unit)? = null,
    onMapLoaded: (() -> Unit)? = null,
    onMapClick: ((LatLng) -> Unit)? = null,
    onCameraIdle: ((CameraPosition) -> Unit)? = null,
    onMapStyleApplied: ((Boolean) -> Unit)? = null,
    key: Any? = null,
    content: GoogleMapScope.() -> Unit = {},
) {
    require(saveableStateKey == null || saveableStateKey.isNotBlank()) {
        "saveableStateKey must be null or non-blank."
    }
    val lifecycleOwner = checkNotNull(LocalLifecycleOwner.current) {
        "GoogleMapView requires a LifecycleOwner from the Android host or ProvideLifecycleOwner."
    }
    val savedStateOwner = if (saveableStateKey == null) {
        null
    } else {
        checkNotNull(LocalSavedStateRegistryOwner.current) {
            "GoogleMapView with saveableStateKey requires a SavedStateRegistryOwner from the " +
                "Android host or ProvideSavedStateRegistryOwner."
        }
    }
    val mapContent = GoogleMapScope().apply(content).snapshot()
    AndroidView(
        adapter = GoogleMapViewAdapter(options),
        state = GoogleMapViewState(
            lifecycleOwner = lifecycleOwner,
            savedStateOwner = savedStateOwner,
            saveableStateKey = saveableStateKey,
            properties = properties,
            uiSettings = uiSettings,
            content = mapContent,
            callbacks = GoogleMapCallbacks(
                onMapReady = onMapReady,
                onMapLoaded = onMapLoaded,
                onMapClick = onMapClick,
                onCameraIdle = onCameraIdle,
                onMapStyleApplied = onMapStyleApplied,
            ),
        ),
        key = key,
        constructionKey = GoogleMapConstructionKey(
            options = options,
            lifecycleOwner = lifecycleOwner,
            savedStateOwner = savedStateOwner,
            saveableStateKey = saveableStateKey,
        ),
        modifier = modifier,
    )
}

internal data class GoogleMapViewState(
    val lifecycleOwner: LifecycleOwner,
    val savedStateOwner: SavedStateRegistryOwner?,
    val saveableStateKey: String?,
    val properties: GoogleMapProperties,
    val uiSettings: GoogleMapUiSettings,
    val content: GoogleMapContentSnapshot,
    val callbacks: GoogleMapCallbacks,
)

internal data class GoogleMapCallbacks(
    val onMapReady: ((GoogleMap) -> Unit)?,
    val onMapLoaded: (() -> Unit)?,
    val onMapClick: ((LatLng) -> Unit)?,
    val onCameraIdle: ((CameraPosition) -> Unit)?,
    val onMapStyleApplied: ((Boolean) -> Unit)?,
)

private class GoogleMapConstructionKey(
    private val options: GoogleMapViewOptions,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateOwner: SavedStateRegistryOwner?,
    private val saveableStateKey: String?,
) {
    override fun equals(other: Any?): Boolean =
        other is GoogleMapConstructionKey &&
            options == other.options &&
            lifecycleOwner === other.lifecycleOwner &&
            savedStateOwner === other.savedStateOwner &&
            saveableStateKey == other.saveableStateKey

    override fun hashCode(): Int {
        var result = options.hashCode()
        result = 31 * result + System.identityHashCode(lifecycleOwner)
        result = 31 * result + System.identityHashCode(savedStateOwner)
        return 31 * result + (saveableStateKey?.hashCode() ?: 0)
    }
}

internal class GoogleMapViewAdapter(
    private val options: GoogleMapViewOptions,
) : LifecycleAndroidViewAdapter<MapView, GoogleMapViewState>() {
    override val reusePolicy: AndroidViewReusePolicy = AndroidViewReusePolicy.Never

    override fun lifecycleOwner(state: GoogleMapViewState): LifecycleOwner = state.lifecycleOwner

    override fun create(scope: AndroidViewCreateScope): MapView =
        MapView(scope.context, options.toNativeOptions())

    override fun update(
        scope: AndroidViewUpdateScope<MapView>,
        state: GoogleMapViewState,
    ) {
        // Map state is published only after commit because SDK updates are externally observable.
    }

    override fun onViewCommit(
        scope: AndroidViewCommitScope<MapView>,
        state: GoogleMapViewState,
    ) {
        val binding = GoogleMapBindingStore.bindingFor(scope.view)
        try {
            val restore = state.saveableStateKey?.let { saveableStateKey ->
                val owner = checkNotNull(state.savedStateOwner)
                scope.bindAndroidViewSavedState(
                    owner = owner,
                    key = saveableStateKey,
                    formatVersion = MAP_SAVED_STATE_FORMAT_VERSION,
                ) {
                    binding.saveState()
                }
            }
            binding.commit(
                state = state,
                restoredState = (restore as? AndroidViewSavedStateBindResult.Initial)?.restoredState,
            )
        } catch (error: Throwable) {
            val cleanupFailure = runCatching {
                GoogleMapBindingStore.remove(scope.view)?.release()
            }.exceptionOrNull()
            cleanupFailure?.let(error::addSuppressed)
            throw error
        }
    }

    override fun onLifecycleEvent(
        scope: AndroidViewLifecycleEventScope<MapView>,
        state: GoogleMapViewState,
        event: Lifecycle.Event,
    ) {
        val binding = GoogleMapBindingStore.bindingFor(scope.view)
        when (event) {
            Lifecycle.Event.ON_CREATE -> Unit
            Lifecycle.Event.ON_START -> binding.start()
            Lifecycle.Event.ON_RESUME -> binding.resume()
            Lifecycle.Event.ON_PAUSE -> binding.pause()
            Lifecycle.Event.ON_STOP -> binding.stop()
            Lifecycle.Event.ON_DESTROY -> binding.destroy()
            Lifecycle.Event.ON_ANY -> Unit
        }
    }

    override fun onViewRelease(view: MapView) {
        GoogleMapBindingStore.remove(view)?.release()
    }
}

private fun GoogleMapViewOptions.toNativeOptions(): GoogleMapOptions =
    GoogleMapOptions()
        .liteMode(liteMode)
        .zOrderOnTop(zOrderOnTop)
        .also { native ->
            mapId?.let(native::mapId)
            backgroundColor?.let(native::backgroundColor)
        }

internal fun GoogleMapType.toNativeMapType(): Int = when (this) {
    GoogleMapType.None -> GoogleMap.MAP_TYPE_NONE
    GoogleMapType.Normal -> GoogleMap.MAP_TYPE_NORMAL
    GoogleMapType.Satellite -> GoogleMap.MAP_TYPE_SATELLITE
    GoogleMapType.Terrain -> GoogleMap.MAP_TYPE_TERRAIN
    GoogleMapType.Hybrid -> GoogleMap.MAP_TYPE_HYBRID
}

internal fun GoogleMapColorScheme.toNativeColorScheme(): Int = when (this) {
    GoogleMapColorScheme.Light -> MapColorScheme.LIGHT
    GoogleMapColorScheme.Dark -> MapColorScheme.DARK
    GoogleMapColorScheme.FollowSystem -> MapColorScheme.FOLLOW_SYSTEM
}

private const val MAP_SAVED_STATE_FORMAT_VERSION = 1
