# Material 3 Android Overlay Integration

`viewcompose-overlay-material3-android` is the optional Android presentation backend for ViewCompose dialogs,
anchored popups, modal bottom sheets, snackbars, and toasts. The declarative overlay protocol, DSL,
queue policy, positioning model, and nested surface sessions live in `viewcompose-ui-foundation`; this
artifact maps those contracts to Android and Material platform windows.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-material3-android:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Window presentation and Material integration may evolve between alphas.
- Platform: Android library with a minimum SDK inherited from the repository Android policy.
- UI Contract and UI Foundation are exposed transitively because their environment, builder, and
  overlay contracts appear in public APIs. Host Android remains an implementation dependency, and
  core rendering does not depend on this artifact.
- Omitting the artifact keeps the renderer operational: overlay requests use the core no-op host and
  emit a one-time diagnostic message instead of failing application startup.

## Installation and discovery

The artifact registers `com.viewcompose.host.android.overlay.AndroidOverlayHostFactoryProvider`
through Java `ServiceLoader`. Normal ViewCompose hosts call
`AndroidOverlayHostDefaults.androidOrNoOp(rootView)`, so adding the dependency is
enough; no Application initialization or manifest component is required.

The backend exclusively owns `com.viewcompose.overlay.material3.android`; the package name keeps
the Material 3 presentation policy visible instead of presenting it as a generic Android overlay.

Custom render hosts can construct `AndroidOverlayHost(rootView)` explicitly and install it with
`ProvideOverlayHost`. Create one host per attached render root, clear each render session during
teardown, and do not retain the host beyond the root View's window lifetime.

All commits, dismissal calls, and platform callbacks run on the Android main thread.

## Session ownership and reconciliation

Every overlay identity combines a render-session ID with the DSL request key. A commit is the
complete desired request set for one session:

- adding a key creates its platform handle;
- changing a same-key request updates the existing handle when that overlay type supports updates;
- omitting a previous key dismisses only that session's handle;
- clearing a session dismisses its surfaces and removes its pending transient feedback;
- requests from another session are never removed as a side effect.

Dialog, popup, and bottom-sheet content is rendered by a nested `OverlaySurfaceSession`. The session
captures ViewCompose locals when content is declared and owns its renderer until the platform handle
is dismissed. Programmatic host cleanup suppresses `onDismissRequest`; only user or platform window
dismissal asks application state to remove the declarative request.

## Dialogs

Dialogs use a transparent Android `Dialog` and a nested ViewCompose surface. Dismiss-on-Back and
dismiss-on-outside-touch are mapped to platform cancelability. Logical top, center, and bottom
positions map to window gravity, and scrim opacity is clamped to Android's `0f..1f` range.

Changing content, position, dismissal policy, or scrim opacity under the same key updates the
existing window and render session. Removing the request disposes the nested session before closing
the window.

## Anchored popups

Popup requests resolve `anchorId` against the current native View tree. The renderer marks the
matching DSL anchor with an internal tag; the Android backend finds that View and calculates physical
coordinates from anchor bounds, popup size, visible window bounds, layout direction, logical
alignment, offsets, margin, and overflow policy.

The handle observes attachment, global layout, and scrolling. It remeasures after content changes
and updates `PopupWindow` only when position or size actually changes. If an anchor temporarily
disappears or has no geometry, the popup hides without reporting dismissal and reappears after the
anchor returns. This covers lazy-item recycling, scrolling, IME changes, and window resizing.

Use stable, unique anchor IDs within one render root. A depth-first search selects the first matching
native View, so duplicate IDs make placement depend on View hierarchy order.

## Modal bottom sheets

Modal sheets use Material `BottomSheetDialog`. Same-key updates preserve the dialog and nested
surface while applying dismissal policy, scrim opacity, expansion policy, and content changes.

`skipPartiallyExpanded` maps to an immediately expanded Material behavior with the collapsed
intermediate state skipped. An explicit navigation-bar color is applied to the sheet window. Without
an override, the backend restores the dialog default and keeps Android's contrast enforcement.

## Snackbar and toast queues

UI Foundation owns the shared snackbar/toast queue; this module supplies only platform presenters.
Queue policies, replacement, dropping, and session removal therefore behave identically in tests and
on Android.

Material Snackbar provides a real terminal callback. Action clicks invoke the application action,
then the platform callback reports `Action`, `Timeout`, `Gesture`, `Replaced`, or a generic platform
reason to the queue. An explicit framework dismissal reason wins over Material's callback event.

Android Toast has no reliable completion callback. The backend uses application context and a main-
looper timeout approximating the platform short or long duration. This keeps the queue moving without
retaining an Activity, but timeout completion should not be treated as a frame-accurate signal that a
system-rendered toast has vanished.

## Resource and lifecycle boundaries

- Dialogs, popups, and bottom sheets retain their root View context because they belong to its
  window; clearing their render session releases the platform window and nested renderer.
- Toasts retain only application context.
- Popup attach, layout, and scroll listeners are removed when their handle is dismissed.
- A hidden popup remains logically active while waiting for its anchor; removal or session cleanup
  still disposes it immediately.
- The backend intentionally owns no Activity, Fragment, ViewModel, or saved-state namespace.

## Testing and custom hosts

Most application tests should exercise declarative requests through the UI Foundation recording hosts
and queue snapshots. Use Robolectric or device tests when validating Android window flags, Material
callbacks, popup geometry, or system-bar appearance.

Custom platform backends can implement the UI Foundation presenter and handle interfaces without
depending on this artifact. Keep the same session isolation, idempotent update, single terminal
dismissal, and resource-disposal guarantees.

## Related documentation

- [UI Foundation module](../viewcompose-ui-foundation/README.md)
- [Android host module](../viewcompose-host-android/README.md)
- [Overlay guide](../../guides/overlays.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-overlay-material3-android` API tree](https://docs.viewcompose.com/api/viewcompose-overlay-material3-android/current/).

## Compatibility notes

The `0.1.0-alpha01` line establishes optional service-provider discovery, session-isolated platform
handles, nested surface rendering, anchored popup recovery, Material bottom sheets and snackbars, and
application-context toasts. Treat Android window objects as backend implementation details and keep
application state authoritative through declarative overlay requests.
