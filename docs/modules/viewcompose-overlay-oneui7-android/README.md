---
schema_version: 2
document_id: module.viewcompose-overlay-oneui7-android
doc_type: module
owner:
  kind: module
  id: viewcompose-overlay-oneui7-android
version_lane: released
capability_ids:
  - overlay.oneui7
artifact_ids:
  - viewcompose-overlay-oneui7-android
sample_ids:
  - module.overlay-oneui7-dependency
coordinate: com.viewcompose:viewcompose-overlay-oneui7-android:0.1.0-alpha01
minimal_usage_sample_id: module.overlay-oneui7-dependency
---

# One UI 7 Android Overlay Adapter

`viewcompose-overlay-oneui7-android` is the explicit, Material-free Android presentation adapter
for One UI roots. It composes the neutral
[`viewcompose-overlay-android`](../viewcompose-overlay-android/README.md) transport with
ViewCompose-owned One UI Snackbar and bottom-dialog presenters. It does not add a separate
`setOneUi7UiContent` API and does not change Android root-context construction.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="overlay-oneui7-dependency" sample_id="module.overlay-oneui7-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-oneui7:0.1.0-alpha01")
    implementation("com.viewcompose:viewcompose-overlay-oneui7-android:0.1.0-alpha01")
}
```

The explicit assembly below calls `OneUi7Theme`, `OneUi7ThemeDefaults`, and One UI components, so
application source declares `viewcompose-oneui7` directly. The overlay adapter keeps that module as
a runtime implementation dependency and exposes only shared UI Foundation token types from its
host constructor; code that constructs the host with its default token snapshot does not need to
name One UI APIs.

- Stability: **Alpha**.
- Platform: Android library, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- API dependencies: UI Contract and UI Foundation because `AndroidOverlayHost` exposes the shared
  overlay contracts and attribution model.
- Implementation dependencies: Host Android, the neutral Android overlay transport, Android
  Renderer shape bridge, and `viewcompose-oneui7`. Google Material Components and AppCompat are
  forbidden dependencies.

API quality: `AndroidOverlayHost` is a Q3 root-integration API. Its canonical KDoc and compiled
sample define window ownership, main-thread confinement, cleanup, token snapshot, and attribution
contracts.

## Explicit One UI root assembly

One UI does not require a themed Android `Context` before native View construction. Applications
therefore keep the neutral `setUiContent` host and pass an explicit `overlayHostFactory` that
constructs this module's `AndroidOverlayHost` with the current `OneUi7ThemeDefaults` token snapshot.
Feed the host's `integrationAttribution` back into `OneUi7Theme`; without the adapter, the theme
reports overlay capabilities as `Unsupported`, and installing the adapter upgrades only the
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

The bottom dialog applies the complete Foundation-resolved appearance snapshot on show and every
changed same-key update. Container color and logical shape replace the initial One UI fallback
chrome, scrim zero clears the obsolete dim flag, and navigation-bar policy distinguishes an exact
color from restoring the captured platform default and Android Q+ contrast enforcement. The
adapter's margin, drag handle, and gesture remain One UI-owned presentation details.

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
