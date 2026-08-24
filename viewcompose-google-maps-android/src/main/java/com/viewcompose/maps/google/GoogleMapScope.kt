package com.viewcompose.maps.google

import android.graphics.Color
import androidx.annotation.ColorInt
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.Cap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.PatternItem
import com.google.android.gms.maps.model.RoundCap
import com.viewcompose.ui.foundation.UiDslMarker

/**
 * Defines immutable presentation for one keyed marker declared in [GoogleMapScope].
 *
 * @property title optional primary info-window text
 * @property snippet optional secondary info-window text
 * @property contentDescription optional marker-specific accessibility description
 * @property icon optional SDK bitmap descriptor; `null` selects the default marker icon
 * @property alpha marker opacity in the inclusive `0f..1f` range
 * @property anchorU horizontal icon anchor in the inclusive `0f..1f` range
 * @property anchorV vertical icon anchor in the inclusive `0f..1f` range
 * @property infoWindowAnchorU horizontal info-window anchor in the inclusive `0f..1f` range
 * @property infoWindowAnchorV vertical info-window anchor in the inclusive `0f..1f` range
 * @property draggable whether drag gestures may reposition the marker
 * @property flat whether the icon remains flat against the map plane
 * @property visible whether the marker is rendered
 * @property rotationDegrees clockwise icon rotation in degrees
 * @property zIndex marker draw-order priority
 */
data class GoogleMapMarkerStyle(
    val title: String? = null,
    val snippet: String? = null,
    val contentDescription: String? = null,
    val icon: BitmapDescriptor? = null,
    val alpha: Float = 1f,
    val anchorU: Float = 0.5f,
    val anchorV: Float = 1f,
    val infoWindowAnchorU: Float = 0.5f,
    val infoWindowAnchorV: Float = 0f,
    val draggable: Boolean = false,
    val flat: Boolean = false,
    val visible: Boolean = true,
    val rotationDegrees: Float = 0f,
    val zIndex: Float = 0f,
) {
    init {
        require(alpha in 0f..1f) { "Marker alpha must be in 0f..1f." }
        require(anchorU in 0f..1f && anchorV in 0f..1f) {
            "Marker anchor coordinates must be in 0f..1f."
        }
        require(infoWindowAnchorU in 0f..1f && infoWindowAnchorV in 0f..1f) {
            "Marker info-window anchor coordinates must be in 0f..1f."
        }
        require(rotationDegrees.isFinite()) { "Marker rotationDegrees must be finite." }
        require(zIndex.isFinite()) { "Marker zIndex must be finite." }
    }

    /** Provides the stable ordinary marker presentation. */
    companion object {
        /** Stable default marker presentation. */
        @JvmField
        val Default: GoogleMapMarkerStyle = GoogleMapMarkerStyle()
    }
}

/**
 * Defines immutable presentation for one keyed polyline declared in [GoogleMapScope].
 *
 * Width uses physical screen pixels to match Maps SDK. Lists are defensively copied when declared.
 *
 * @property widthPixels positive line width in physical pixels
 * @property color packed Android ARGB color
 * @property visible whether the line is rendered
 * @property geodesic whether segments follow the Earth's curvature
 * @property clickable whether the line participates in the latest `onClick` callback
 * @property zIndex line draw-order priority
 * @property startCap SDK cap applied to the first point
 * @property endCap SDK cap applied to the last point
 * @property jointType SDK `JointType` integer used between adjacent segments
 * @property pattern optional repeated SDK stroke pattern
 */
data class GoogleMapPolylineStyle(
    val widthPixels: Float = 10f,
    @ColorInt val color: Int = Color.BLACK,
    val visible: Boolean = true,
    val geodesic: Boolean = false,
    val clickable: Boolean = false,
    val zIndex: Float = 0f,
    val startCap: Cap = RoundCap(),
    val endCap: Cap = RoundCap(),
    val jointType: Int = JointType.DEFAULT,
    val pattern: List<PatternItem>? = null,
) {
    init {
        require(widthPixels.isFinite() && widthPixels > 0f) {
            "Polyline widthPixels must be finite and greater than zero."
        }
        require(zIndex.isFinite()) { "Polyline zIndex must be finite." }
    }

    /** Provides the stable ordinary polyline presentation. */
    companion object {
        /** Stable default polyline presentation. */
        @JvmField
        val Default: GoogleMapPolylineStyle = GoogleMapPolylineStyle()
    }
}

/** Records keyed declarative overlays for one [GoogleMapView] commit. */
@UiDslMarker
class GoogleMapScope internal constructor() {
    private val markers = LinkedHashMap<Any, GoogleMapMarkerSpec>()
    private val polylines = LinkedHashMap<Any, GoogleMapPolylineSpec>()

    /**
     * Declares one marker whose native identity is retained while [key] remains in this map.
     *
     * [onClick] runs on the Android main thread with the latest committed callback. Returning
     * `true` consumes the SDK click and suppresses its default info-window behavior.
     *
     * @sample com.viewcompose.maps.google.samples.googleMapViewSample
     * @param key non-null identity unique among markers in this content block
     * @param position geographical marker position
     * @param style complete immutable marker presentation
     * @param onClick optional latest click callback returning whether the event was consumed
     * @throws IllegalArgumentException when [key] is already used by another marker
     */
    fun Marker(
        key: Any,
        position: LatLng,
        style: GoogleMapMarkerStyle = GoogleMapMarkerStyle.Default,
        onClick: (() -> Boolean)? = null,
    ) {
        require(!markers.containsKey(key)) { "Duplicate Google map marker key: $key" }
        markers[key] = GoogleMapMarkerSpec(
            key = key,
            position = position,
            style = style,
            onClick = onClick,
        )
    }

    /**
     * Declares one polyline whose native identity is retained while [key] remains in this map.
     *
     * [onClick] runs on the Android main thread only when [GoogleMapPolylineStyle.clickable] is
     * enabled and always uses the latest committed callback.
     *
     * @sample com.viewcompose.maps.google.samples.googleMapViewSample
     * @param key non-null identity unique among polylines in this content block
     * @param points ordered geographical vertices; at least two are required
     * @param style complete immutable polyline presentation
     * @param onClick optional latest click callback
     * @throws IllegalArgumentException when [key] is duplicated or fewer than two points exist
     */
    fun Polyline(
        key: Any,
        points: List<LatLng>,
        style: GoogleMapPolylineStyle = GoogleMapPolylineStyle.Default,
        onClick: (() -> Unit)? = null,
    ) {
        require(points.size >= 2) { "Google map polyline requires at least two points." }
        require(!polylines.containsKey(key)) { "Duplicate Google map polyline key: $key" }
        polylines[key] = GoogleMapPolylineSpec(
            key = key,
            points = points.toList(),
            style = style.copy(pattern = style.pattern?.toList()),
            onClick = onClick,
        )
    }

    internal fun snapshot(): GoogleMapContentSnapshot = GoogleMapContentSnapshot(
        markers = LinkedHashMap(markers),
        polylines = LinkedHashMap(polylines),
    )
}

internal data class GoogleMapContentSnapshot(
    val markers: Map<Any, GoogleMapMarkerSpec>,
    val polylines: Map<Any, GoogleMapPolylineSpec>,
)

internal data class GoogleMapMarkerSpec(
    val key: Any,
    val position: LatLng,
    val style: GoogleMapMarkerStyle,
    val onClick: (() -> Boolean)?,
)

internal data class GoogleMapPolylineSpec(
    val key: Any,
    val points: List<LatLng>,
    val style: GoogleMapPolylineStyle,
    val onClick: (() -> Unit)?,
)
