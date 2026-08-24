package com.viewcompose.maps.google

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import java.util.IdentityHashMap

internal object GoogleMapBindingStore {
    fun bindingFor(view: MapView): GoogleMapBinding {
        val existing = view.getTag(R.id.viewcompose_google_map_binding)
        check(existing == null || existing is GoogleMapBinding) {
            "Google Maps binding tag is owned by an incompatible value."
        }
        return (existing as? GoogleMapBinding)
            ?: GoogleMapBinding(RealMapViewPort(view)).also { binding ->
                view.setTag(R.id.viewcompose_google_map_binding, binding)
            }
    }

    fun remove(view: MapView): GoogleMapBinding? {
        val existing = view.getTag(R.id.viewcompose_google_map_binding)
        check(existing == null || existing is GoogleMapBinding) {
            "Google Maps binding tag is owned by an incompatible value."
        }
        view.setTag(R.id.viewcompose_google_map_binding, null)
        return existing as? GoogleMapBinding
    }
}

/** Owns one MapView lifecycle, async map generation, controlled state, and SDK state snapshot. */
internal class GoogleMapBinding(
    private val view: MapViewPort,
) {
    private var state: GoogleMapViewState? = null
    private var map: GoogleMapHandle? = null
    private var generation = 0L
    private var created = false
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var lowMemoryRegistered = false

    fun commit(
        state: GoogleMapViewState,
        restoredState: Bundle?,
    ) {
        check(!destroyed) { "A destroyed Google MapView cannot be committed again." }
        val previous = this.state
        this.state = state
        if (!created) {
            created = true
            generation++
            val activeGeneration = generation
            view.onCreate(restoredState?.let(::Bundle))
            view.registerLowMemory {
                if (!destroyed && activeGeneration == generation) {
                    view.onLowMemory()
                }
            }
            lowMemoryRegistered = true
            view.getMapAsync { readyMap ->
                if (destroyed || activeGeneration != generation) {
                    readyMap.release()
                    return@getMapAsync
                }
                map?.release()
                map = readyMap
                val committed = this.state ?: return@getMapAsync
                readyMap.render(previous = null, current = committed)
                readyMap.nativeMap?.let { nativeMap ->
                    committed.callbacks.onMapReady?.invoke(nativeMap)
                }
            }
        } else {
            map?.render(previous = previous, current = state)
        }
    }

    fun start() {
        if (destroyed || started) return
        check(created) { "Google MapView must be created before start." }
        view.onStart()
        started = true
    }

    fun resume() {
        if (destroyed || resumed) return
        check(started) { "Google MapView must be started before resume." }
        view.onResume()
        resumed = true
    }

    fun pause() {
        if (destroyed || !resumed) return
        view.onPause()
        resumed = false
    }

    fun stop() {
        if (destroyed || !started) return
        pause()
        view.onStop()
        started = false
    }

    fun saveState(): Bundle {
        check(created && !destroyed) { "Google MapView state is unavailable after destruction." }
        return Bundle().also(view::onSaveInstanceState)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        generation++
        var failure: Throwable? = null
        failure = captureGoogleMapFailure(failure) { pauseAfterDestroyFlag() }
        failure = captureGoogleMapFailure(failure) { stopAfterDestroyFlag() }
        if (lowMemoryRegistered) {
            lowMemoryRegistered = false
            failure = captureGoogleMapFailure(failure) { view.unregisterLowMemory() }
        }
        failure = captureGoogleMapFailure(failure) { map?.release() }
        map = null
        if (created) {
            created = false
            failure = captureGoogleMapFailure(failure) { view.onDestroy() }
        }
        state = null
        failure?.let { throw it }
    }

    fun release() {
        destroy()
    }

    private fun pauseAfterDestroyFlag() {
        if (!resumed) return
        view.onPause()
        resumed = false
    }

    private fun stopAfterDestroyFlag() {
        if (!started) return
        view.onStop()
        started = false
    }
}

internal interface MapViewPort {
    fun onCreate(restoredState: Bundle?)
    fun onStart()
    fun onResume()
    fun onPause()
    fun onStop()
    fun onDestroy()
    fun onLowMemory()
    fun onSaveInstanceState(outState: Bundle)
    fun getMapAsync(callback: (GoogleMapHandle) -> Unit)
    fun registerLowMemory(callback: () -> Unit)
    fun unregisterLowMemory()
}

private class RealMapViewPort(
    private val view: MapView,
) : MapViewPort {
    private var componentCallbacks: ComponentCallbacks2? = null
    private val callbackContext: Context = view.context.applicationContext ?: view.context

    override fun onCreate(restoredState: Bundle?) = view.onCreate(restoredState)
    override fun onStart() = view.onStart()
    override fun onResume() = view.onResume()
    override fun onPause() = view.onPause()
    override fun onStop() = view.onStop()
    override fun onDestroy() = view.onDestroy()
    override fun onLowMemory() = view.onLowMemory()
    override fun onSaveInstanceState(outState: Bundle) = view.onSaveInstanceState(outState)

    override fun getMapAsync(callback: (GoogleMapHandle) -> Unit) {
        view.getMapAsync { map -> callback(RealGoogleMapHandle(map)) }
    }

    override fun registerLowMemory(callback: () -> Unit) {
        check(componentCallbacks == null) { "Google MapView low-memory forwarding is already active." }
        val registered = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = callback()
            override fun onTrimMemory(level: Int) = Unit
        }
        callbackContext.registerComponentCallbacks(registered)
        componentCallbacks = registered
    }

    override fun unregisterLowMemory() {
        val registered = componentCallbacks ?: return
        componentCallbacks = null
        callbackContext.unregisterComponentCallbacks(registered)
    }
}

internal interface GoogleMapHandle {
    val nativeMap: GoogleMap?
    fun render(previous: GoogleMapViewState?, current: GoogleMapViewState)
    fun release()
}

private class RealGoogleMapHandle(
    override val nativeMap: GoogleMap,
) : GoogleMapHandle {
    private val renderer = GoogleMapStateRenderer(RealGoogleMapOperations(nativeMap))

    override fun render(previous: GoogleMapViewState?, current: GoogleMapViewState) {
        renderer.render(previous, current)
    }

    override fun release() = renderer.release()
}

/** Computes exact controlled-state and keyed-overlay mutations against a replaceable map port. */
internal class GoogleMapStateRenderer(
    private val map: GoogleMapOperations,
) {
    private val markers = LinkedHashMap<Any, ManagedMarker>()
    private val polylines = LinkedHashMap<Any, ManagedPolyline>()
    private var released = false

    fun render(previous: GoogleMapViewState?, current: GoogleMapViewState) {
        check(!released) { "A released Google map renderer cannot accept state." }
        map.updateCallbacks(current)
        applyProperties(previous?.properties, current.properties, current.callbacks)
        if (previous?.uiSettings != current.uiSettings) {
            map.setUiSettings(current.uiSettings)
        }
        reconcileMarkers(current.content.markers)
        reconcilePolylines(current.content.polylines)
    }

    fun release() {
        if (released) return
        released = true
        var failure: Throwable? = null
        failure = captureGoogleMapFailure(failure) { map.clearCallbacks() }
        markers.values.forEach { managed ->
            failure = captureGoogleMapFailure(failure) { managed.marker.remove() }
        }
        polylines.values.forEach { managed ->
            failure = captureGoogleMapFailure(failure) { managed.polyline.remove() }
        }
        markers.clear()
        polylines.clear()
        failure?.let { throw it }
    }

    private fun applyProperties(
        previous: GoogleMapProperties?,
        current: GoogleMapProperties,
        callbacks: GoogleMapCallbacks,
    ) {
        if (previous?.mapType != current.mapType) {
            map.setMapType(current.mapType)
        }
        if (previous?.colorScheme != current.colorScheme) {
            map.setColorScheme(current.colorScheme)
        }
        if (previous?.trafficEnabled != current.trafficEnabled) {
            map.setTrafficEnabled(current.trafficEnabled)
        }
        if (
            previous?.transitEnabled != current.transitEnabled &&
            (previous != null || current.transitEnabled)
        ) {
            // A fresh GoogleMap is already transit-disabled. Avoid asking renderers that have not
            // rolled out transit support to re-apply that default and emit a misleading error.
            map.setTransitEnabled(current.transitEnabled)
        }
        if (previous?.buildingsEnabled != current.buildingsEnabled) {
            map.setBuildingsEnabled(current.buildingsEnabled)
        }
        if (previous?.indoorEnabled != current.indoorEnabled) {
            map.setIndoorEnabled(current.indoorEnabled)
        }
        if (
            previous?.minZoomPreference != current.minZoomPreference ||
            previous?.maxZoomPreference != current.maxZoomPreference
        ) {
            map.setZoomPreferences(current.minZoomPreference, current.maxZoomPreference)
        }
        if (previous?.cameraTargetBounds != current.cameraTargetBounds) {
            map.setCameraTargetBounds(current.cameraTargetBounds)
        }
        if (previous?.contentDescription != current.contentDescription) {
            map.setContentDescription(current.contentDescription)
        }
        if (previous?.padding != current.padding) {
            map.setPadding(current.padding)
        }
        if (previous?.styleJson != current.styleJson) {
            val applied = map.setStyleJson(current.styleJson)
            callbacks.onMapStyleApplied?.invoke(applied)
        }
        if (current.cameraPosition != null && previous?.cameraPosition != current.cameraPosition) {
            map.moveCamera(current.cameraPosition)
        }
    }

    private fun reconcileMarkers(desired: Map<Any, GoogleMapMarkerSpec>) {
        val removedKeys = markers.keys - desired.keys
        removedKeys.forEach(::removeMarker)
        desired.forEach { (key, spec) ->
            val existing = markers[key]
            if (existing == null || existing.spec.style.contentDescription != spec.style.contentDescription) {
                if (existing != null) removeMarker(key)
                addMarker(spec)
            } else if (existing.spec != spec) {
                existing.marker.update(spec)
                markers[key] = existing.copy(spec = spec)
            }
        }
    }

    private fun addMarker(spec: GoogleMapMarkerSpec) {
        markers[spec.key] = ManagedMarker(map.addMarker(spec), spec)
    }

    private fun removeMarker(key: Any) {
        val removed = markers.remove(key) ?: return
        removed.marker.remove()
    }

    private fun reconcilePolylines(desired: Map<Any, GoogleMapPolylineSpec>) {
        val removedKeys = polylines.keys - desired.keys
        removedKeys.forEach(::removePolyline)
        desired.forEach { (key, spec) ->
            val existing = polylines[key]
            if (existing == null) {
                addPolyline(spec)
            } else if (existing.spec != spec) {
                existing.polyline.update(spec)
                polylines[key] = existing.copy(spec = spec)
            }
        }
    }

    private fun addPolyline(spec: GoogleMapPolylineSpec) {
        polylines[spec.key] = ManagedPolyline(map.addPolyline(spec), spec)
    }

    private fun removePolyline(key: Any) {
        val removed = polylines.remove(key) ?: return
        removed.polyline.remove()
    }
}

private data class ManagedMarker(
    val marker: GoogleMapMarkerHandle,
    val spec: GoogleMapMarkerSpec,
)

private data class ManagedPolyline(
    val polyline: GoogleMapPolylineHandle,
    val spec: GoogleMapPolylineSpec,
)

internal interface GoogleMapOperations {
    val nativeMap: GoogleMap?
    fun updateCallbacks(state: GoogleMapViewState)
    fun clearCallbacks()
    fun setMapType(type: GoogleMapType)
    fun setColorScheme(scheme: GoogleMapColorScheme)
    fun setTrafficEnabled(enabled: Boolean)
    fun setTransitEnabled(enabled: Boolean)
    fun setBuildingsEnabled(enabled: Boolean)
    fun setIndoorEnabled(enabled: Boolean)
    fun setZoomPreferences(minimum: Float?, maximum: Float?)
    fun setCameraTargetBounds(bounds: com.google.android.gms.maps.model.LatLngBounds?)
    fun setContentDescription(description: String?)
    fun setPadding(padding: GoogleMapPadding)
    fun setStyleJson(styleJson: String?): Boolean
    fun moveCamera(position: CameraPosition)
    fun setUiSettings(settings: GoogleMapUiSettings)
    fun addMarker(spec: GoogleMapMarkerSpec): GoogleMapMarkerHandle
    fun addPolyline(spec: GoogleMapPolylineSpec): GoogleMapPolylineHandle
}

internal interface GoogleMapMarkerHandle {
    fun update(spec: GoogleMapMarkerSpec)
    fun remove()
}

internal interface GoogleMapPolylineHandle {
    fun update(spec: GoogleMapPolylineSpec)
    fun remove()
}

private class RealGoogleMapOperations(
    override val nativeMap: GoogleMap,
) : GoogleMapOperations {
    private val markerKeys = IdentityHashMap<Marker, Any>()
    private val polylineKeys = IdentityHashMap<Polyline, Any>()
    private val callbacks = GoogleMapCallbackDispatcher()

    override fun updateCallbacks(state: GoogleMapViewState) {
        if (!callbacks.update(state)) return
        nativeMap.setOnMapLoadedCallback(callbacks::onMapLoaded)
        nativeMap.setOnMapClickListener(callbacks::onMapClick)
        nativeMap.setOnCameraIdleListener {
            callbacks.onCameraIdle(nativeMap.cameraPosition)
        }
        nativeMap.setOnMarkerClickListener { marker ->
            val key = markerKeys[marker] ?: return@setOnMarkerClickListener false
            callbacks.onMarkerClick(key)
        }
        nativeMap.setOnPolylineClickListener { polyline ->
            val key = polylineKeys[polyline] ?: return@setOnPolylineClickListener
            callbacks.onPolylineClick(key)
        }
    }

    override fun clearCallbacks() {
        callbacks.clear()
        nativeMap.setOnMapLoadedCallback(null)
        nativeMap.setOnMapClickListener(null)
        nativeMap.setOnCameraIdleListener(null)
        nativeMap.setOnMarkerClickListener(null)
        nativeMap.setOnPolylineClickListener(null)
    }

    override fun setMapType(type: GoogleMapType) {
        nativeMap.mapType = type.toNativeMapType()
    }

    override fun setColorScheme(scheme: GoogleMapColorScheme) {
        nativeMap.setMapColorScheme(scheme.toNativeColorScheme())
    }

    override fun setTrafficEnabled(enabled: Boolean) {
        nativeMap.isTrafficEnabled = enabled
    }

    override fun setTransitEnabled(enabled: Boolean) {
        nativeMap.isTransitEnabled = enabled
    }

    override fun setBuildingsEnabled(enabled: Boolean) {
        nativeMap.isBuildingsEnabled = enabled
    }

    override fun setIndoorEnabled(enabled: Boolean) {
        nativeMap.isIndoorEnabled = enabled
    }

    override fun setZoomPreferences(minimum: Float?, maximum: Float?) {
        nativeMap.resetMinMaxZoomPreference()
        minimum?.let(nativeMap::setMinZoomPreference)
        maximum?.let(nativeMap::setMaxZoomPreference)
    }

    override fun setCameraTargetBounds(bounds: com.google.android.gms.maps.model.LatLngBounds?) {
        nativeMap.setLatLngBoundsForCameraTarget(bounds)
    }

    override fun setContentDescription(description: String?) {
        nativeMap.setContentDescription(description)
    }

    override fun setPadding(padding: GoogleMapPadding) {
        nativeMap.setPadding(padding.left, padding.top, padding.right, padding.bottom)
    }

    override fun setStyleJson(styleJson: String?): Boolean =
        nativeMap.setMapStyle(styleJson?.let(::MapStyleOptions))

    override fun moveCamera(position: CameraPosition) {
        nativeMap.moveCamera(CameraUpdateFactory.newCameraPosition(position))
    }

    override fun setUiSettings(settings: GoogleMapUiSettings) {
        nativeMap.uiSettings.apply {
            isZoomControlsEnabled = settings.zoomControlsEnabled
            isCompassEnabled = settings.compassEnabled
            isMyLocationButtonEnabled = settings.myLocationButtonEnabled
            isIndoorLevelPickerEnabled = settings.indoorLevelPickerEnabled
            isMapToolbarEnabled = settings.mapToolbarEnabled
            isScrollGesturesEnabled = settings.scrollGesturesEnabled
            isZoomGesturesEnabled = settings.zoomGesturesEnabled
            isTiltGesturesEnabled = settings.tiltGesturesEnabled
            isRotateGesturesEnabled = settings.rotateGesturesEnabled
            isScrollGesturesEnabledDuringRotateOrZoom =
                settings.scrollGesturesEnabledDuringRotateOrZoom
        }
    }

    override fun addMarker(spec: GoogleMapMarkerSpec): GoogleMapMarkerHandle {
        val marker = checkNotNull(nativeMap.addMarker(spec.toNativeOptions())) {
            "Maps SDK returned no Marker for a valid marker declaration."
        }
        markerKeys[marker] = spec.key
        return object : GoogleMapMarkerHandle {
            override fun update(spec: GoogleMapMarkerSpec) = marker.apply(spec)
            override fun remove() {
                markerKeys.remove(marker)
                marker.remove()
            }
        }
    }

    override fun addPolyline(spec: GoogleMapPolylineSpec): GoogleMapPolylineHandle {
        val polyline = nativeMap.addPolyline(spec.toNativeOptions())
        polylineKeys[polyline] = spec.key
        return object : GoogleMapPolylineHandle {
            override fun update(spec: GoogleMapPolylineSpec) = polyline.apply(spec)
            override fun remove() {
                polylineKeys.remove(polyline)
                polyline.remove()
            }
        }
    }
}

/** Keeps SDK listeners stable while routing every event to the latest committed callback state. */
internal class GoogleMapCallbackDispatcher {
    private var state: GoogleMapViewState? = null

    /** Returns true only when the native listeners must be installed for this map generation. */
    fun update(state: GoogleMapViewState): Boolean {
        val install = this.state == null
        this.state = state
        return install
    }

    fun clear() {
        state = null
    }

    fun onMapLoaded() {
        state?.callbacks?.onMapLoaded?.invoke()
    }

    fun onMapClick(position: LatLng) {
        state?.callbacks?.onMapClick?.invoke(position)
    }

    fun onCameraIdle(position: CameraPosition) {
        state?.callbacks?.onCameraIdle?.invoke(position)
    }

    fun onMarkerClick(key: Any): Boolean =
        state?.content?.markers?.get(key)?.onClick?.invoke() ?: false

    fun onPolylineClick(key: Any) {
        state?.content?.polylines?.get(key)?.onClick?.invoke()
    }
}

private fun GoogleMapMarkerSpec.toNativeOptions(): MarkerOptions =
    MarkerOptions()
        .position(position)
        .title(style.title)
        .snippet(style.snippet)
        .icon(style.icon)
        .alpha(style.alpha)
        .anchor(style.anchorU, style.anchorV)
        .infoWindowAnchor(style.infoWindowAnchorU, style.infoWindowAnchorV)
        .draggable(style.draggable)
        .flat(style.flat)
        .visible(style.visible)
        .rotation(style.rotationDegrees)
        .zIndex(style.zIndex)
        .also { options ->
            style.contentDescription?.let(options::contentDescription)
        }

private fun Marker.apply(spec: GoogleMapMarkerSpec) {
    position = spec.position
    title = spec.style.title
    snippet = spec.style.snippet
    setIcon(spec.style.icon)
    alpha = spec.style.alpha
    setAnchor(spec.style.anchorU, spec.style.anchorV)
    setInfoWindowAnchor(spec.style.infoWindowAnchorU, spec.style.infoWindowAnchorV)
    isDraggable = spec.style.draggable
    isFlat = spec.style.flat
    isVisible = spec.style.visible
    rotation = spec.style.rotationDegrees
    zIndex = spec.style.zIndex
}

private fun GoogleMapPolylineSpec.toNativeOptions(): PolylineOptions =
    PolylineOptions()
        .addAll(points)
        .width(style.widthPixels)
        .color(style.color)
        .visible(style.visible)
        .geodesic(style.geodesic)
        .clickable(style.clickable)
        .zIndex(style.zIndex)
        .startCap(style.startCap)
        .endCap(style.endCap)
        .jointType(style.jointType)
        .pattern(style.pattern)

private fun Polyline.apply(spec: GoogleMapPolylineSpec) {
    points = spec.points
    width = spec.style.widthPixels
    color = spec.style.color
    isVisible = spec.style.visible
    isGeodesic = spec.style.geodesic
    isClickable = spec.style.clickable
    zIndex = spec.style.zIndex
    startCap = spec.style.startCap
    endCap = spec.style.endCap
    jointType = spec.style.jointType
    pattern = spec.style.pattern
}

private inline fun captureGoogleMapFailure(
    current: Throwable?,
    block: () -> Unit,
): Throwable? = try {
    block()
    current
} catch (error: Throwable) {
    if (current == null) {
        error
    } else {
        if (error !== current) current.addSuppressed(error)
        current
    }
}
