# Third-Party Android View Integrations Plan

## Status

Active; phases 0–4 are complete in owning manuals. Next: Maps. Verified 2026-08-24.

## Remaining work

- **Maps 5:** `viewcompose-google-maps-android` / `com.viewcompose.maps.google`, `MapView` 20.0.0.
  Own committed lifecycle/keyed state; app owns credentials/data/network/policy. Pass fake/no-key and
  API 31+ credentialed restore, navigation, update, renderer, accessibility, StrictMode, and leaks.
- **CameraX 6:** `viewcompose-camerax-androidx` / `com.viewcompose.camerax`, `PreviewView` 1.6.1.
  Request no permission; never `unbindAll`; own one Preview binding. Pass denial/fake and physical
  lifecycle, lens, rotation, navigation, Surface/frame, accessibility, and leak gates.
- **Closeout 7–9:** classify configuration/reuse/failure/restore; finish docs, samples, Demo/Preview,
  notices, consumer/Changeset and gates; interpret device/performance/leak evidence; archive. Keep
  minSdk 24, compileSdk 36, Java 11, post-commit exact cleanup, and no Renderer dependency. Missing
  capability never passes.
