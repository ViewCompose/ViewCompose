# Neutral Android Overlay Integration

`viewcompose-overlay-android` is the Material-free Android transport for ViewCompose overlays. It
maps UI Foundation requests to Android `Dialog`, `PopupWindow`, and `Toast`, owns nested overlay
render containers and root/session cleanup, and exposes narrow presenter slots for behavior that a
design system must own.

The artifact does not depend on Material Components, AppCompat, `viewcompose-material3`, or
`viewcompose-oneui7`. It is the default overlay runtime of `viewcompose-android` and
`viewcompose-navigation-android`.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-android:0.1.0-alpha04")
}
```

- Stability: **Alpha**. Version `0.1.0-alpha04` intentionally reactivates this coordinate after
  `0.1.0-alpha03` with breaking, neutral transport semantics.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- API dependencies: UI Contract and UI Foundation because their container and overlay contracts
  appear in public APIs.
- Implementation dependency: Host Android. No named design-system dependency is permitted.

Applications normally receive this artifact transitively from `viewcompose-android`. Depend on it
directly only for a custom low-level host or custom design-system adapter.

API quality: `AndroidOverlayHost` and its factory provider are Q3 root-integration APIs;
`asOverlayRenderContainerHandle` is a Q2 low-level container adapter. Their canonical KDoc and
compiled samples define lifetime, threading, ownership, and fallback contracts.

## Root-scoped host

`AndroidOverlayHost(rootView)` owns one root-scoped transport:

- Android `Dialog` for declarative dialog windows;
- `PopupWindow` plus anchor observation and overflow positioning for popups;
- Android `Toast` plus approximate queue completion;
- explicit unsupported presenters for Snackbar and modal bottom sheet; and
- optional presenter parameters that a named design integration can supply.

The host never discovers a design system and never substitutes Material widgets. Missing Snackbar
or modal-sheet presenters complete or retain their request using the documented unsupported path.
`integrationAttribution` reports transport, presenter, conformance, and fallback for every overlay
type; custom presenters are marked unverified until their owning design adapter publishes a more
specific attribution snapshot.

`PopupWindow` is a transport and positioning boundary, not a visual surface. Its platform elevation
remains zero so popup content such as `DropdownMenu` owns the declared shape and elevation exactly
once. Generic `Popup` content that declares no elevation therefore receives no implicit rectangular
window shadow.

Create one host per attached render root. Commit and clear calls are main-thread work. Clearing a
session dismisses only that session's surfaces, removes listeners, and disposes nested render
sessions before their platform windows are released.

## Nested render containers

`asOverlayRenderContainerHandle()` adapts an overlay-owned Android `ViewGroup` for a nested
ViewCompose render session without exposing Android types to UI Foundation. The overlay owner must
dispose the nested session before permanently detaching the container.

Dialog and popup content retain the composition-local snapshot captured at declaration. A delayed
surface therefore keeps its token producer, recipe set, and design-system attribution instead of
reading a later process-global identity.

## Low-level discovery

`AndroidOverlayHostDefaults.androidOrNoOp(rootView)` remains available in Host Android for custom
hosts. Java `ServiceLoader` may discover exactly one provider from this artifact. Zero providers
returns the UI Foundation no-op host with a diagnostic; multiple providers fail deterministically.

Normal Activity, Fragment, and navigation roots do not use discovery to select design behavior.
They construct this neutral transport explicitly. A named integration such as Material 3 also
constructs its adapter explicitly.

## Related documentation

- [Overlay architecture decision](../../architecture/decisions/0006-root-scoped-overlay-backend-selection.md)
- [Overlay guide](../../guides/overlays.md)
- [UI Foundation module](../viewcompose-ui-foundation/README.md)
- [Material 3 overlay adapter](../viewcompose-overlay-material3-android/README.md)
- [One UI 7 overlay adapter](../viewcompose-overlay-oneui7-android/README.md)

The generated reference is available in the
[`viewcompose-overlay-android` API tree](https://docs.viewcompose.com/api/viewcompose-overlay-android/current/).

## Compatibility notes

Versions through `0.1.0-alpha03` mixed generic Android transport and Material presentation under
this coordinate. The five-layer migration temporarily retired it in favor of
`viewcompose-overlay-material3-android`. Version `0.1.0-alpha04` restores the coordinate as the
single neutral Android transport without a forwarding facade. Material Snackbar and bottom-sheet
behavior now requires the explicit Material adapter or `viewcompose-material3-android` aggregate.
