package com.viewcompose.maps.google

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GoogleMapBindingTest {
    @Test
    fun `commit creates before lifecycle catch up and release reverses exactly once`() {
        val view = FakeMapViewPort()
        val binding = GoogleMapBinding(view)

        binding.commit(state(), restoredState = Bundle().apply { putString("camera", "restored") })
        binding.start()
        binding.resume()
        binding.release()
        binding.release()

        assertEquals(
            listOf("create:restored", "registerLowMemory", "getMapAsync", "start", "resume", "pause", "stop", "unregisterLowMemory", "destroy"),
            view.events,
        )
    }

    @Test
    fun `map ready uses latest committed state and later updates are diff inputs`() {
        val view = FakeMapViewPort()
        val binding = GoogleMapBinding(view)
        val first = state(properties = GoogleMapProperties(mapType = GoogleMapType.Normal))
        val second = state(properties = GoogleMapProperties(mapType = GoogleMapType.Satellite))

        binding.commit(first, restoredState = null)
        binding.commit(second, restoredState = null)
        val map = FakeGoogleMapHandle()
        view.deliverMap(map)
        val third = state(properties = GoogleMapProperties(mapType = GoogleMapType.Terrain))
        binding.commit(third, restoredState = null)

        assertEquals(listOf(null to second, second to third), map.renders)
    }

    @Test
    fun `late map ready from released generation is ignored and cleaned`() {
        val view = FakeMapViewPort()
        val binding = GoogleMapBinding(view)
        binding.commit(state(), restoredState = null)
        binding.release()
        val stale = FakeGoogleMapHandle()

        view.deliverMap(stale)

        assertTrue(stale.released)
        assertTrue(stale.renders.isEmpty())
    }

    @Test
    fun `low memory forwards only while binding is alive`() {
        val view = FakeMapViewPort()
        val binding = GoogleMapBinding(view)
        binding.commit(state(), restoredState = null)

        view.dispatchLowMemory()
        binding.release()
        view.dispatchLowMemory()

        assertEquals(1, view.events.count { it == "lowMemory" })
    }

    @Test
    fun `saved state delegates to current map view and is a fresh bundle`() {
        val view = FakeMapViewPort()
        val binding = GoogleMapBinding(view)
        binding.commit(state(), restoredState = null)

        val first = binding.saveState()
        val second = binding.saveState()

        assertEquals(1, first.getInt("saveCount"))
        assertEquals(2, second.getInt("saveCount"))
        assertNotSame(first, second)
    }

    @Test
    fun `invalid public values fail before a native map exists`() {
        assertThrows(IllegalArgumentException::class.java) {
            GoogleMapViewOptions(mapId = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            GoogleMapProperties(minZoomPreference = 8f, maxZoomPreference = 7f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GoogleMapMarkerStyle(alpha = 1.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GoogleMapPolylineStyle(widthPixels = 0f)
        }
    }

    @Test
    fun `content keys are scoped by overlay kind and caller lists are copied`() {
        val points = mutableListOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0))
        val scope = GoogleMapScope().apply {
            Marker(key = "shared", position = LatLng(1.0, 2.0))
            Polyline(key = "shared", points = points)
        }
        val snapshot = scope.snapshot()
        points += LatLng(5.0, 6.0)

        assertEquals(1, snapshot.markers.size)
        assertEquals(1, snapshot.polylines.size)
        assertEquals(2, snapshot.polylines.getValue("shared").points.size)
        assertThrows(IllegalArgumentException::class.java) {
            GoogleMapScope().apply {
                Marker(key = "duplicate", position = LatLng(1.0, 2.0))
                Marker(key = "duplicate", position = LatLng(3.0, 4.0))
            }
        }
    }

    @Test
    fun `released binding rejects another commit`() {
        val binding = GoogleMapBinding(FakeMapViewPort())
        binding.commit(state(), restoredState = null)
        binding.release()

        assertThrows(IllegalStateException::class.java) {
            binding.commit(state(), restoredState = null)
        }
    }

    private fun state(
        properties: GoogleMapProperties = GoogleMapProperties.Default,
    ): GoogleMapViewState = GoogleMapViewState(
        lifecycleOwner = TestLifecycleOwner(),
        savedStateOwner = null,
        saveableStateKey = null,
        properties = properties,
        uiSettings = GoogleMapUiSettings.Default,
        content = GoogleMapScope().snapshot(),
        callbacks = GoogleMapCallbacks(null, null, null, null, null),
    )
}

private class FakeMapViewPort : MapViewPort {
    val events = mutableListOf<String>()
    private var mapCallback: ((GoogleMapHandle) -> Unit)? = null
    private var lowMemoryCallback: (() -> Unit)? = null
    private var saveCount = 0

    override fun onCreate(restoredState: Bundle?) {
        events += "create:${restoredState?.getString("camera")}"
    }

    override fun onStart() { events += "start" }
    override fun onResume() { events += "resume" }
    override fun onPause() { events += "pause" }
    override fun onStop() { events += "stop" }
    override fun onDestroy() { events += "destroy" }
    override fun onLowMemory() { events += "lowMemory" }

    override fun onSaveInstanceState(outState: Bundle) {
        saveCount++
        outState.putInt("saveCount", saveCount)
    }

    override fun getMapAsync(callback: (GoogleMapHandle) -> Unit) {
        events += "getMapAsync"
        mapCallback = callback
    }

    override fun registerLowMemory(callback: () -> Unit) {
        events += "registerLowMemory"
        lowMemoryCallback = callback
    }

    override fun unregisterLowMemory() {
        events += "unregisterLowMemory"
        lowMemoryCallback = null
    }

    fun deliverMap(map: GoogleMapHandle) {
        checkNotNull(mapCallback).invoke(map)
    }

    fun dispatchLowMemory() {
        lowMemoryCallback?.invoke()
    }
}

private class FakeGoogleMapHandle : GoogleMapHandle {
    override val nativeMap = null
    val renders = mutableListOf<Pair<GoogleMapViewState?, GoogleMapViewState>>()
    var released = false

    override fun render(previous: GoogleMapViewState?, current: GoogleMapViewState) {
        renders += previous to current
    }

    override fun release() {
        released = true
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry
}
