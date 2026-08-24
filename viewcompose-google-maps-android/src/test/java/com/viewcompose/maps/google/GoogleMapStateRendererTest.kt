package com.viewcompose.maps.google

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMapStateRendererTest {
    @Test
    fun `identical state only refreshes latest event callbacks`() {
        val operations = FakeGoogleMapOperations()
        val renderer = GoogleMapStateRenderer(operations)
        val state = mapState()
        renderer.render(previous = null, current = state)
        operations.events.clear()

        renderer.render(previous = state, current = state)

        assertEquals(listOf("callbackState"), operations.events)
    }

    @Test
    fun `changed controlled state updates exact fields and retained overlays`() {
        val operations = FakeGoogleMapOperations()
        val renderer = GoogleMapStateRenderer(operations)
        val first = mapState(
            properties = GoogleMapProperties(mapType = GoogleMapType.Normal),
            uiSettings = GoogleMapUiSettings.Default,
            markerPosition = LatLng(1.0, 2.0),
            polylineEnd = LatLng(3.0, 4.0),
        )
        renderer.render(null, first)
        operations.events.clear()
        val second = mapState(
            properties = GoogleMapProperties(
                mapType = GoogleMapType.Satellite,
                cameraPosition = CameraPosition.fromLatLngZoom(LatLng(5.0, 6.0), 10f),
            ),
            uiSettings = GoogleMapUiSettings(zoomControlsEnabled = true),
            markerPosition = LatLng(7.0, 8.0),
            polylineEnd = LatLng(9.0, 10.0),
        )

        renderer.render(first, second)

        assertEquals(
            listOf("callbackState", "mapType:Satellite", "camera:10.0", "ui", "updateMarker:marker", "updatePolyline:route"),
            operations.events,
        )
    }

    @Test
    fun `marker accessibility change recreates only that marker`() {
        val operations = FakeGoogleMapOperations()
        val renderer = GoogleMapStateRenderer(operations)
        val first = mapState(markerDescription = "first")
        renderer.render(null, first)
        operations.events.clear()
        val second = mapState(markerDescription = "second")

        renderer.render(first, second)

        assertEquals(listOf("callbackState", "removeMarker:marker", "addMarker:marker"), operations.events)
    }

    @Test
    fun `invalid JSON style result is reported without suppressing state application`() {
        val operations = FakeGoogleMapOperations(styleResult = false)
        val renderer = GoogleMapStateRenderer(operations)
        var styleApplied: Boolean? = null
        val state = mapState(
            properties = GoogleMapProperties(styleJson = "[invalid]"),
            callbacks = GoogleMapCallbacks(null, null, null, null) { styleApplied = it },
        )

        renderer.render(null, state)

        assertFalse(styleApplied!!)
        assertTrue("style:[invalid]" in operations.events)
    }

    @Test
    fun `release detaches callbacks and every managed overlay once`() {
        val operations = FakeGoogleMapOperations()
        val renderer = GoogleMapStateRenderer(operations)
        renderer.render(null, mapState())
        operations.events.clear()

        renderer.release()
        renderer.release()

        assertEquals(
            listOf("clearCallbacks", "removeMarker:marker", "removePolyline:route"),
            operations.events,
        )
    }

    @Test
    fun `callback dispatcher installs listeners once and routes events to latest state`() {
        val firstEvents = mutableListOf<String>()
        val secondEvents = mutableListOf<String>()
        val dispatcher = GoogleMapCallbackDispatcher()
        val first = mapState(
            callbacks = GoogleMapCallbacks(
                onMapReady = null,
                onMapLoaded = { firstEvents += "loaded" },
                onMapClick = { firstEvents += "map" },
                onCameraIdle = { firstEvents += "camera" },
                onMapStyleApplied = null,
            ),
        )
        val second = mapState(
            callbacks = GoogleMapCallbacks(
                onMapReady = null,
                onMapLoaded = { secondEvents += "loaded" },
                onMapClick = { secondEvents += "map" },
                onCameraIdle = { secondEvents += "camera" },
                onMapStyleApplied = null,
            ),
        )

        assertTrue(dispatcher.update(first))
        assertFalse(dispatcher.update(second))
        dispatcher.onMapLoaded()
        dispatcher.onMapClick(LatLng(1.0, 2.0))
        dispatcher.onCameraIdle(CameraPosition.fromLatLngZoom(LatLng(1.0, 2.0), 3f))

        assertTrue(firstEvents.isEmpty())
        assertEquals(listOf("loaded", "map", "camera"), secondEvents)
        dispatcher.clear()
        dispatcher.onMapLoaded()
        assertEquals(listOf("loaded", "map", "camera"), secondEvents)
    }

    @Test
    fun `fresh default state does not reapply disabled transit layer`() {
        val operations = FakeGoogleMapOperations()
        val renderer = GoogleMapStateRenderer(operations)

        renderer.render(null, mapState())

        assertFalse("transit:false" in operations.events)
    }

    private fun mapState(
        properties: GoogleMapProperties = GoogleMapProperties.Default,
        uiSettings: GoogleMapUiSettings = GoogleMapUiSettings.Default,
        markerPosition: LatLng = LatLng(1.0, 2.0),
        markerDescription: String? = null,
        polylineEnd: LatLng = LatLng(3.0, 4.0),
        callbacks: GoogleMapCallbacks = GoogleMapCallbacks(null, null, null, null, null),
    ): GoogleMapViewState {
        val content = GoogleMapScope().apply {
            Marker(
                key = "marker",
                position = markerPosition,
                style = GoogleMapMarkerStyle(contentDescription = markerDescription),
            )
            Polyline(
                key = "route",
                points = listOf(markerPosition, polylineEnd),
            )
        }.snapshot()
        return GoogleMapViewState(
            lifecycleOwner = RendererTestLifecycleOwner(),
            savedStateOwner = null,
            saveableStateKey = null,
            properties = properties,
            uiSettings = uiSettings,
            content = content,
            callbacks = callbacks,
        )
    }
}

private class FakeGoogleMapOperations(
    private val styleResult: Boolean = true,
) : GoogleMapOperations {
    override val nativeMap: GoogleMap? = null
    val events = mutableListOf<String>()

    override fun updateCallbacks(state: GoogleMapViewState) { events += "callbackState" }
    override fun clearCallbacks() { events += "clearCallbacks" }
    override fun setMapType(type: GoogleMapType) { events += "mapType:$type" }
    override fun setColorScheme(scheme: GoogleMapColorScheme) { events += "scheme:$scheme" }
    override fun setTrafficEnabled(enabled: Boolean) { events += "traffic:$enabled" }
    override fun setTransitEnabled(enabled: Boolean) { events += "transit:$enabled" }
    override fun setBuildingsEnabled(enabled: Boolean) { events += "buildings:$enabled" }
    override fun setIndoorEnabled(enabled: Boolean) { events += "indoor:$enabled" }
    override fun setZoomPreferences(minimum: Float?, maximum: Float?) {
        events += "zoom:$minimum:$maximum"
    }

    override fun setCameraTargetBounds(bounds: LatLngBounds?) { events += "bounds:$bounds" }
    override fun setContentDescription(description: String?) { events += "description:$description" }
    override fun setPadding(padding: GoogleMapPadding) { events += "padding:$padding" }
    override fun setStyleJson(styleJson: String?): Boolean {
        events += "style:$styleJson"
        return styleResult
    }

    override fun moveCamera(position: CameraPosition) { events += "camera:${position.zoom}" }
    override fun setUiSettings(settings: GoogleMapUiSettings) { events += "ui" }

    override fun addMarker(spec: GoogleMapMarkerSpec): GoogleMapMarkerHandle {
        events += "addMarker:${spec.key}"
        return object : GoogleMapMarkerHandle {
            override fun update(spec: GoogleMapMarkerSpec) { events += "updateMarker:${spec.key}" }
            override fun remove() { events += "removeMarker:${spec.key}" }
        }
    }

    override fun addPolyline(spec: GoogleMapPolylineSpec): GoogleMapPolylineHandle {
        events += "addPolyline:${spec.key}"
        return object : GoogleMapPolylineHandle {
            override fun update(spec: GoogleMapPolylineSpec) { events += "updatePolyline:${spec.key}" }
            override fun remove() { events += "removePolyline:${spec.key}" }
        }
    }
}

private class RendererTestLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry(this)
}
