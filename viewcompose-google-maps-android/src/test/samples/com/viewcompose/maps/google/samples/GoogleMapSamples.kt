package com.viewcompose.maps.google.samples

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.viewcompose.maps.google.GoogleMapColorScheme
import com.viewcompose.maps.google.GoogleMapMarkerStyle
import com.viewcompose.maps.google.GoogleMapScope
import com.viewcompose.maps.google.GoogleMapProperties
import com.viewcompose.maps.google.GoogleMapUiSettings
import com.viewcompose.maps.google.GoogleMapView
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.unit.dp

/** Shows controlled camera/UI state with keyed declarative map content and SDK restoration. */
// DOCS_REGION_START(google-map-view)
fun UiTreeBuilder.googleMapViewSample() {
    val office = LatLng(31.2304, 121.4737)
    GoogleMapView(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        properties = GoogleMapProperties(
            colorScheme = GoogleMapColorScheme.FollowSystem,
            cameraPosition = CameraPosition.fromLatLngZoom(office, 13f),
            contentDescription = "Office map",
        ),
        uiSettings = GoogleMapUiSettings(zoomControlsEnabled = true),
        saveableStateKey = "office-map",
    ) {
        googleMapContentSample()
    }
}
// DOCS_REGION_END(google-map-view)

/** Reconciles keyed overlays without transferring ownership of the native map. */
// DOCS_REGION_START(google-map-content)
fun GoogleMapScope.googleMapContentSample() {
    val office = LatLng(31.2304, 121.4737)
    Marker(
        key = "office",
        position = office,
        style = GoogleMapMarkerStyle(title = "Office"),
    )
    Polyline(
        key = "walking-route",
        points = listOf(office, LatLng(31.2320, 121.4770)),
    )
}
// DOCS_REGION_END(google-map-content)
