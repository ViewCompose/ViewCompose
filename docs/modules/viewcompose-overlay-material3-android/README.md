# Material 3 Android Overlay Adapter

`viewcompose-overlay-material3-android` is the narrow Material presentation adapter for
ViewCompose overlays. It supplies Material Components Snackbar and modal-bottom-sheet presenters to
the neutral [`viewcompose-overlay-android`](../viewcompose-overlay-android/README.md) transport.
It does not own generic Android Dialog, PopupWindow, Toast, anchor positioning, nested render
containers, or service-provider selection.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**.
- API dependencies: UI Contract and UI Foundation because `AndroidOverlayHost` exposes their
  overlay contracts.
- Implementation dependencies: the neutral Android overlay transport, Host Android, AppCompat,
  and Material Components.
- Normal Material applications receive this artifact transitively from
  `viewcompose-material3-android`.

API quality: the Material `AndroidOverlayHost` remains a Q3 root-integration API. Its public
attribution snapshot is diagnostic evidence, not a mutable presenter registry.

## Explicit Material assembly

`com.viewcompose.overlay.material3.android.host.AndroidOverlayHost(rootView)` composes:

- neutral Android Dialog, PopupWindow, Toast, nested-session, and cleanup behavior;
- Material `Snackbar` presentation and terminal callback mapping;
- Material `BottomSheetDialog` presentation and behavior; and
- the accepted 24dp dialog window inset used by the Material adapter.

The adapter registers no `AndroidOverlayHostFactoryProvider`. Material selection is explicit in
`setMaterial3UiContent`, so merely placing this artifact on a One UI or neutral application's
classpath cannot change its overlay behavior.

`integrationAttribution` reports the neutral transport separately from the Material presenter for
each capability. Dialog and popup content keep the Material token/recipe snapshot captured at
declaration. Snackbar and modal bottom sheet report their Material Components backends as
`Equivalent`; Android Toast remains an explicit degraded platform fallback.

## Material presenter behavior

Material Snackbar maps action, timeout, swipe, replacement, and generic platform terminal events
to the UI Foundation transient queue. An explicit framework dismissal reason wins over a later
Material callback.

Material modal bottom sheet preserves its platform dialog and nested surface for same-key updates.
It applies dismissal policy, scrim opacity, expansion policy, and navigation-bar treatment without
moving session ownership into this adapter.

All root, window, presenter, callback, and cleanup work is confined to the Android main thread.
The adapter must not outlive its root View's window.

## Related documentation

- [Neutral Android overlay integration](../viewcompose-overlay-android/README.md)
- [Material 3 Android aggregate](../viewcompose-material3-android/README.md)
- [Overlay architecture decision](../../architecture/decisions/0006-root-scoped-overlay-backend-selection.md)
- [Overlay guide](../../guides/overlays.md)

The generated reference is available in the
[`viewcompose-overlay-material3-android` API tree](https://docs.viewcompose.com/api/viewcompose-overlay-material3-android/current/).

## Compatibility notes

The initial alpha registered a whole Material host through `ServiceLoader` and also implemented
generic Android overlay transport. The current hard cut removes that registration and moves all
generic transport to the reactivated `viewcompose-overlay-android` coordinate. Custom hosts that
need Material behavior must construct this adapter explicitly.
