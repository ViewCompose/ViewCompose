package com.viewcompose

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.viewcompose.demo.automation.demoAutomationTarget
import com.viewcompose.demo.contract.DemoAutomationRole
import com.viewcompose.demo.contract.DemoScenarioSpec
import com.viewcompose.host.android.resources.stringResource
import com.viewcompose.maps.google.GoogleMapColorScheme
import com.viewcompose.maps.google.GoogleMapMarkerStyle
import com.viewcompose.maps.google.GoogleMapProperties
import com.viewcompose.maps.google.GoogleMapUiSettings
import com.viewcompose.maps.google.GoogleMapView
import com.viewcompose.preview.tooling.ViewComposePreview
import com.viewcompose.runtime.mutableStateOf
import com.viewcompose.ui.foundation.Button
import com.viewcompose.ui.foundation.Row
import com.viewcompose.ui.foundation.Surface
import com.viewcompose.ui.foundation.SurfaceVariant
import com.viewcompose.ui.foundation.Text
import com.viewcompose.ui.foundation.TextDefaults
import com.viewcompose.ui.foundation.UiTextStyle
import com.viewcompose.ui.foundation.UiTreeBuilder
import com.viewcompose.ui.foundation.remember
import com.viewcompose.ui.modifier.Modifier
import com.viewcompose.ui.modifier.fillMaxWidth
import com.viewcompose.ui.modifier.height
import com.viewcompose.ui.modifier.margin
import com.viewcompose.ui.modifier.padding
import com.viewcompose.ui.unit.dp
import com.viewcompose.ui.unit.sp

@ViewComposePreview(name = "Interop · Google Maps", group = "Demo/Pages")
internal fun UiTreeBuilder.PreviewGoogleMapsView() {
    GoogleMapsDemoPage(credentialsConfigured = false)
}

/** Credential-free contract fixture and optional externally credentialed renderer fixture. */
internal fun UiTreeBuilder.GoogleMapsDemoPage(
    scenario: DemoScenarioSpec? = null,
    credentialsConfigured: Boolean,
) {
    val darkMap = remember { mutableStateOf(false) }
    val alternateLocation = remember { mutableStateOf(false) }
    val readyCount = remember { mutableStateOf(0) }
    val loadedCount = remember { mutableStateOf(0) }
    val clickCount = remember { mutableStateOf(0) }
    val styleAccepted = remember { mutableStateOf<Boolean?>(null) }
    val location = if (alternateLocation.value) HANGZHOU else SHANGHAI
    val destination = if (alternateLocation.value) SHANGHAI else HANGZHOU
    val schemeLabel = if (darkMap.value) {
        stringResource(R.string.demo_google_maps_scheme_dark)
    } else {
        stringResource(R.string.demo_google_maps_scheme_light)
    }
    val locationLabel = if (alternateLocation.value) {
        stringResource(R.string.demo_google_maps_city_hangzhou)
    } else {
        stringResource(R.string.demo_google_maps_city_shanghai)
    }
    val styleLabel = when {
        !darkMap.value -> stringResource(R.string.demo_google_maps_style_default)
        styleAccepted.value == true -> stringResource(R.string.demo_google_maps_style_accepted)
        styleAccepted.value == false -> stringResource(R.string.demo_google_maps_style_rejected)
        else -> stringResource(R.string.demo_google_maps_style_pending)
    }

    ScenarioSection(
        kind = ScenarioKind.Core,
        title = stringResource(R.string.demo_google_maps_section_title),
        subtitle = stringResource(R.string.demo_google_maps_section_summary),
    ) {
        Row(
            spacing = 8.dp,
            modifier = Modifier.fillMaxWidth().margin(bottom = 8.dp),
        ) {
            Button(
                text = stringResource(R.string.demo_google_maps_toggle_scheme),
                onClick = {
                    styleAccepted.value = null
                    darkMap.value = !darkMap.value
                },
                modifier = Modifier
                    .weight(1f)
                    .googleMapsScenarioTarget(scenario, DemoAutomationRole.PrimaryAction),
            )
            Button(
                text = stringResource(R.string.demo_google_maps_toggle_location),
                onClick = { alternateLocation.value = !alternateLocation.value },
                modifier = Modifier
                    .weight(1f)
                    .googleMapsScenarioTarget(scenario, DemoAutomationRole.SecondaryAction),
            )
        }
        Button(
            text = stringResource(R.string.demo_google_maps_reset),
            onClick = {
                darkMap.value = false
                alternateLocation.value = false
                clickCount.value = 0
                styleAccepted.value = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 8.dp)
                .googleMapsScenarioTarget(scenario, DemoAutomationRole.Reset),
        )
        Text(
            text = if (credentialsConfigured) {
                stringResource(
                    R.string.demo_google_maps_status_configured,
                    schemeLabel,
                    locationLabel,
                    styleLabel,
                    readyCount.value,
                    loadedCount.value,
                    clickCount.value,
                )
            } else {
                stringResource(
                    R.string.demo_google_maps_status_unconfigured,
                    schemeLabel,
                    locationLabel,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .margin(bottom = 8.dp)
                .googleMapsScenarioTarget(scenario, DemoAutomationRole.State),
        )
        if (credentialsConfigured) {
            GoogleMapView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .googleMapsScenarioTarget(scenario, DemoAutomationRole.Target),
                properties = GoogleMapProperties(
                    colorScheme = if (darkMap.value) {
                        GoogleMapColorScheme.Dark
                    } else {
                        GoogleMapColorScheme.Light
                    },
                    cameraPosition = CameraPosition.fromLatLngZoom(location, 6f),
                    styleJson = if (darkMap.value) DEMO_DARK_MAP_STYLE_JSON else null,
                    contentDescription = stringResource(R.string.demo_google_maps_content_description),
                ),
                uiSettings = GoogleMapUiSettings(zoomControlsEnabled = true),
                saveableStateKey = "demo-google-map",
                onMapReady = { readyCount.value++ },
                onMapLoaded = { loadedCount.value++ },
                onMapStyleApplied = { accepted -> styleAccepted.value = accepted },
                onMapClick = { clickCount.value++ },
                key = "demo-google-map",
            ) {
                Marker(
                    key = "city",
                    position = location,
                    style = GoogleMapMarkerStyle(
                        title = stringResource(R.string.demo_google_maps_marker_title),
                        contentDescription = stringResource(
                            R.string.demo_google_maps_marker_content_description,
                        ),
                    ),
                    onClick = {
                        clickCount.value++
                        false
                    },
                )
                Polyline(
                    key = "route",
                    points = listOf(location, destination),
                )
            }
        } else {
            Surface(
                variant = SurfaceVariant.Default,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .googleMapsScenarioTarget(scenario, DemoAutomationRole.Target),
            ) {
                Text(
                    text = stringResource(R.string.demo_google_maps_placeholder),
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.demo_google_maps_manual_check),
            style = UiTextStyle(fontSizeSp = 13.sp),
            color = TextDefaults.secondaryColor(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private fun Modifier.googleMapsScenarioTarget(
    scenario: DemoScenarioSpec?,
    role: DemoAutomationRole,
): Modifier = scenario?.automation?.get(role)?.let(::demoAutomationTarget) ?: this

private val SHANGHAI = LatLng(31.2304, 121.4737)
private val HANGZHOU = LatLng(30.2741, 120.1551)

private const val DEMO_DARK_MAP_STYLE_JSON =
    "[{\"elementType\":\"geometry\",\"stylers\":[{\"color\":\"#242f3e\"}]}]"
