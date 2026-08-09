# One UI 7 Android Overlay Adapter

`viewcompose-overlay-oneui7-android` is the explicit, Material-free Android presentation adapter
for One UI roots. It composes the neutral
[`viewcompose-overlay-android`](../viewcompose-overlay-android/README.md) transport with
ViewCompose-owned One UI Snackbar and bottom-dialog presenters. It does not add a separate
`setOneUi7UiContent` API and does not change Android root-context construction.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-oneui7-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- API dependencies: UI Contract and UI Foundation because `AndroidOverlayHost` exposes the shared
  overlay contracts and attribution model.
- Implementation dependencies: Host Android, the neutral Android overlay transport, and
  `viewcompose-oneui7`. Google Material Components and AppCompat are forbidden dependencies.

API quality: `AndroidOverlayHost` is a Q3 root-integration API. Its canonical KDoc and compiled
sample define window ownership, main-thread confinement, cleanup, token snapshot, and attribution
contracts.

## Explicit One UI root assembly

One UI does not require a themed Android `Context` before native View construction. Applications
therefore keep the neutral `setUiContent` host and select only the overlay adapter explicitly:

```kotlin
private lateinit var overlayIntegrations: List<UiIntegrationAttribution>

val tokens = OneUi7ThemeDefaults.light()
setUiContent(
    overlayHostFactory = { root ->
        AndroidOverlayHost(root, tokens).also { host ->
            overlayIntegrations = host.integrationAttribution
        }
    },
) {
    OneUi7Theme(tokens, integrations = overlayIntegrations) {
        AppContent()
    }
}
```

Passing the host's `integrationAttribution` into `OneUi7Theme` is intentional. Without the adapter,
the theme reports overlay capabilities as `Unsupported`; installing the adapter upgrades only the
capabilities that the active root actually owns. Classpath presence alone never changes behavior.

## Presenter behavior

The adapter retains neutral Android Dialog, PopupWindow, Toast, nested render sessions, and
session cleanup. It adds:

- a queued native Snackbar with a full-height pill outline, One UI token snapshot, 24dp window
  margin, action target, accessibility-aware timeout, and exactly-once terminal callbacks; and
- a bottom-gravity dialog with One UI surface geometry, scrim, system-bar handling, nested
  ViewCompose content, outside/back dismissal policy, and drag-handle dismissal.

The initial bottom-dialog presenter has one intrinsic expanded state. It accepts
`skipPartiallyExpanded` for protocol compatibility but exposes no intermediate partial state, so
the option cannot create behavior that the presenter does not own.

`integrationAttribution` reports Dialog and Popup as captured One UI content, Snackbar and modal
bottom sheet as `Equivalent`, and Android Toast as a documented `Degraded` platform fallback. All
root, window, callback, and cleanup work is Android-main-thread confined, and the host must not
outlive its attached render root.

## Related documentation

- [One UI 7 design-system module](../viewcompose-oneui7/README.md)
- [Neutral Android overlay integration](../viewcompose-overlay-android/README.md)
- [Multi-design-system architecture](../../architecture/design-systems.md)
- [Overlay guide](../../guides/overlays.md)

The generated reference is available in the
[`viewcompose-overlay-oneui7-android` API tree](https://docs.viewcompose.com/api/viewcompose-overlay-oneui7-android/current/).

## Compatibility notes

This is the first release line for the adapter. Existing One UI roots that use only neutral
Dialog, Popup, and Toast behavior remain valid. Add this artifact and the explicit host assembly
only when One UI Snackbar or bottom-dialog presentation is required.
