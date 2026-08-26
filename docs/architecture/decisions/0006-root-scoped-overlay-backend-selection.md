---
schema_version: 2
document_id: architecture.overlay-root-selection
doc_type: architecture
owner:
  kind: capability
  id: overlay.android-transport
version_lane: released
capability_ids:
  - overlay.foundation
  - overlay.anchor
  - overlay.host-defaults
  - overlay.android-transport
  - overlay.material3
  - overlay.oneui7
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-ui-contract
  - viewcompose-host-android
  - viewcompose-overlay-android
  - viewcompose-overlay-material3-android
  - viewcompose-overlay-oneui7-android
sample_ids:
  - tutorial.overlays
  - module.overlay-android-dependency
  - module.overlay-material3-dependency
  - module.overlay-oneui7-dependency
invariants:
  - Overlay transport and presenter selection is explicit per attached render root and never depends on process-global classpath order.
  - UI Foundation owns requests, session identity, queue policy, and captured content without Android or design-system dependencies.
  - The neutral Android host owns Dialog, PopupWindow, Toast, nested sessions, anchor observation, and cleanup but never substitutes a design-system presenter.
  - Delayed overlay content retains the immutable design-system and integration snapshot captured by its declaring render session.
evidence:
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/overlay/OverlayHostTest.kt
  - viewcompose-ui-foundation/src/test/java/com/viewcompose/ui/foundation/overlay/TransientFeedbackOverlayHostTest.kt
  - viewcompose-host-android/src/test/java/com/viewcompose/host/android/overlay/AndroidOverlayHostDefaultsTest.kt
  - viewcompose-overlay-android/src/test/java/com/viewcompose/overlay/android/AndroidOverlayHostTest.kt
  - viewcompose-overlay-material3-android/src/test/java/com/viewcompose/overlay/material3/android/host/AndroidOverlayHostAttributionTest.kt
  - viewcompose-overlay-oneui7-android/src/test/java/com/viewcompose/overlay/oneui7/android/host/AndroidOverlayHostAttributionTest.kt
---

# ADR-0006: Root-scoped overlay backend selection

- Status: Accepted
- Date: 2026-08-09
- Supersedes: the `viewcompose-overlay-android` retirement/rename portions of ADR-0002 and ADR-0003

## Context

UI Foundation already separated declarative overlay requests, session identity, queue policy, and
captured surface content from Android presentation. The first Android backend nevertheless mixed
generic `Dialog`, `PopupWindow`, Toast, nested render containers, Material Snackbar, and Material
bottom sheet in one artifact.

The five-layer hard cut renamed that artifact to `viewcompose-overlay-material3-android`. This made
Material ownership visible, but it also assigned generic Android transport to a Material module.
Host Android then discovered an entire host through `ServiceLoader` and selected the first provider.
Consequently a neutral or One UI root could receive Material behavior merely because the Material
artifact was present on the runtime classpath.

Overlay selection must agree with the root's Context, token, recipe, and diagnostic snapshot. It
must also preserve Android window lifecycle and native behavior without adding design-system
branches to UI Foundation, Host Android, or Renderer.

## Decision

1. Reactivate `viewcompose-overlay-android` at `0.1.0-alpha04` as the only Material-free Android
   overlay transport. This is a breaking semantic restoration, not a compatibility facade.
2. The neutral artifact owns Android Dialog, PopupWindow, Toast, anchor observation, coordinate
   placement, nested render-container adaptation, and root/session cleanup.
3. Snackbar and modal bottom sheet are narrow presenter slots. Missing presenters are explicit
   `Unsupported` results; the neutral host never substitutes Material.
4. `viewcompose-overlay-material3-android` owns only Material Snackbar and
   `BottomSheetDialog` presenters plus a thin adapter that composes them with the neutral host.
5. Neutral `setUiContent` and navigation roots construct the neutral host explicitly.
   `setMaterial3UiContent` constructs the Material adapter explicitly. Classpath order does not
   select design behavior.
6. Host Android retains `ServiceLoader` only for custom low-level hosts discovering the single
   neutral provider. Zero providers returns no-op; multiple providers fail deterministically.
7. Design-system snapshots use `UiIntegrationAttribution` to report capability, transport,
   presenter, conformance, and fallback. Delayed overlay content captures that immutable snapshot.
8. A design system receives another Activity/Fragment entry module only when it must resolve a
   distinct Android Context before View construction. Token/recipe-only systems use the neutral
   root and do not duplicate `setUiContent` extensions.

## Consequences

- Neutral and One UI dependency graphs contain no Material Components through overlay defaults.
- Material applications retain a one-dependency, one-host-call path through
  `viewcompose-material3-android`.
- Custom Material hosts must construct the Material adapter explicitly; the old Material service
  provider is removed.
- Existing consumers of `viewcompose-overlay-android:0.1.0-alpha03` must migrate public package
  references and cannot assume Material Snackbar or bottom-sheet behavior from that coordinate.
- One UI Snackbar and modal bottom sheet remain explicitly unsupported until verified One UI
  recipes justify presenters. Silent Material fallback is forbidden.
- Historical tags and generated documentation remain immutable even though the Maven coordinate is
  active again.

## Rejected alternatives

### Keep the Material whole-host provider

Rejected because provider order is process-global and cannot prove which design-system snapshot
owns a root or delayed overlay.

### Publish one Activity/Fragment extension module per design system

Rejected because token-only systems do not need a different Android Context. Duplicating lifecycle,
saved-state, and render-session entry points would increase drift without adding capability.

### Put all overlay presenters in UI Foundation

Rejected because Android windows and Material widgets are platform/integration details. UI
Foundation must remain the request, queue, session, and captured-content owner.

### Keep `viewcompose-overlay-android` retired and introduce another neutral coordinate

Rejected by project decision. Restoring the clear generic coordinate is preferable to adding a
`platform` or `host` qualifier, provided the breaking semantic change is explicit and versioned.
