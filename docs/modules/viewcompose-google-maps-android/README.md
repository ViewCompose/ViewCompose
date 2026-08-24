# Google Maps Android Integration

`viewcompose-google-maps-android` hosts Google Maps SDK 20.0.0 `MapView` inside ViewCompose. It owns
the native View lifecycle, saved-state bridge, replay-safe map configuration, and keyed marker and
polyline reconciliation. The application still owns credentials, data, network and privacy policy.

## Artifact and stability

```kotlin
implementation("com.viewcompose:viewcompose-google-maps-android:0.1.0-alpha01")
```

- Stability: **Alpha**. `GoogleMapView` and its scope are Q3; state types are Q2 and closed enums
  are Q1.
- Platform: Android 7.0 (API 24)+; compile SDK 36; Google Maps SDK 20.0.0.
- Dependency shape: `viewcompose-host-android` and Maps SDK model types are API-visible;
  `viewcompose-lifecycle-androidx` is implementation-only. This optional integration is not in an
  aggregate artifact.

## Setup and use

Configure the Maps SDK API key in the application manifest according to Google's credential and
restriction guidance. The library never reads, stores, or initializes credentials.

```kotlin
GoogleMapView(
    properties = GoogleMapProperties(
        cameraPosition = CameraPosition.fromLatLngZoom(LatLng(31.23, 121.47), 10f),
        contentDescription = "Delivery map",
    ),
    saveableStateKey = "delivery-map",
) {
    Marker(key = "destination", position = LatLng(31.23, 121.47))
}
```

A nearest `LocalLifecycleOwner` is required. `saveableStateKey` additionally requires a nearest
`LocalSavedStateRegistryOwner`; keys must be unique within that owner. The adapter forwards
create/start/resume/pause/stop/destroy, low-memory, and save-state events exactly once. Owner,
save-key, or `GoogleMapViewOptions` changes replace the native View. Ordinary property, UI-setting,
callback, marker, and polyline changes reuse it.

## Ownership and update contract

`GoogleMapProperties` and `GoogleMapUiSettings` are controlled inputs. The current values are
replayed when the asynchronous map becomes ready and diffed afterward. A non-null camera position
is moved without animation; keep transient gesture position in application state if it must remain
controlled. `styleJson` reports SDK acceptance through `onMapStyleApplied`.

Scoped markers and polylines are owned by the adapter, keyed, updated individually, and removed
when absent. Duplicate keys fail during composition. `onMapReady` exposes the native `GoogleMap`
for unsupported SDK features, but callers must not retain it beyond View release or replace the
listeners and managed overlays owned by this adapter. Location permission and the my-location
layer remain application policy.

The module does not select a renderer, call `MapsInitializer`, request permission, perform network
fallback, or provide Wear ambient-mode events. Configure those application-wide concerns before
mounting the View. The manifest declares `org.apache.http.legacy` as optional for compatibility
with older Google Play services, matching Maps SDK 20.0.0 guidance.

## Verification

Credential-free unit and Robolectric tests cover lifecycle ordering, stale async callbacks,
low-memory, saved state, View replacement, controlled diffs, callback identity, keyed overlay
cleanup, validation, and release. On 2026-08-24, all 16 module and 46 Demo tests passed. One Pixel 4
XL / API 33 no-key device method passed in 0.874 seconds. Two 1440 x 3040 screenshots were visually
inspected before and after the actions: scheme/city state, reset, recreation, controls,
placeholder, and instructions were correct.

The credentialed Pixel lane used a package- and certificate-restricted external key supplied only
through the local Gradle user properties. The final device method passed in 19.422 seconds with the
LATEST renderer and remote `maps_core` 260830204. It proved real tile loading, one `onMapLoaded`
callback per native generation, accepted JSON styling, camera/marker/polyline changes on the same
`MapView`, background/resume reuse, a new View after Activity recreation, cleared binding state on
the released View, a UI Context, and the map content description. Light Shanghai and dark Hangzhou
screenshots at 1440 x 3040 were visually inspected; tiles, overlays, controls, state labels, and
layout were correct.

Thread and VM `StrictMode` covered first composition and all tested updates. The final run recorded
zero integration-owned violations. Google Maps itself recorded 18
`IncorrectContextUseViolation` and five `UntaggedSocketViolation` events after control entered the
SDK, despite the adapter supplying a UI Context; these are a documented third-party limitation,
not silently counted as ViewCompose success. The result is **improved** from no adapter. It proves
integration correctness and bounded release cleanup, but includes no heap-wide leak profile,
frame-performance, power, renderer comparison, offline, permission, or location-layer claim. The
network-dependent method duration is not a performance measurement.

The Demo enables the real map only when the app build property `viewComposeMapsApiKey` is
configured. Credentials remain application-owned and are never committed by this module.

Reference: [`viewcompose-google-maps-android` API](https://docs.viewcompose.com/api/viewcompose-google-maps-android/current/).
