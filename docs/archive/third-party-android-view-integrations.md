# Third-Party Android View Integrations Plan

## Status

Complete and archived. Phases 0–9 delivered the typed transaction-aware Android View adapter,
AndroidX lifecycle and saved-state coordination, AndroidX Media3, legacy ExoPlayer 2, Google Maps,
and CameraX. Each integration is independently published and keeps permission, SDK configuration,
and unrelated resources with the application while owning only the exact native resource it
creates.

The final CameraX phase added an exact lifecycle-bound `Preview`, explicit permission and provider
ownership, bounded failure classification, front/back lens and implementation-mode replacement,
compiled Q3 samples, Demo and Preview routes, consumer coverage, notices, and deterministic plus
physical-device acceptance. Pixel 4 XL verification rejected the original unbounded preview host:
its transformed renderer escaped a `[84,1693][1356,2673]` target by 359 px above and below and 1 px
on each side. The hard-cut design now gives compatible fill rendering an integration-owned clipping
boundary and permits performance mode only with fit scaling. The denied and granted physical lanes
both passed 1/1; the latter covered streaming, both lenses, View replacement, exact cleanup,
stop/resume, rotation, recreation, accessibility, and zero rendering descendants outside the
declared target. The owning CameraX manual records the interpreted evidence and limitations.

Durable contracts now live in the owning module manuals. Further third-party integrations, combined
CameraX multi-use-case sessions, additional OEM qualification, or power and latency optimization
require a new attributed plan. This archived page is historical evidence, not current requirements.

Last verified: 2026-08-24.

## Maven release changesets

- `release/changes/20260824-typed-android-view-adapter.json`
- `release/changes/20260824-android-view-lifecycle-saved-state.json`
- `release/changes/20260824-media3-player-view.json`
- `release/changes/20260824-exoplayer2-player-view.json`
- `release/changes/20260824-google-map-view.json`
- `release/changes/20260824-camerax-preview-view.json`

## Canonical ownership after closeout

- [Host Android](../modules/viewcompose-host-android/README.md) owns the typed adapter transaction,
  construction identity, reset, commit, release, and generic Android View escape-hatch contracts.
- [Lifecycle AndroidX](../modules/viewcompose-lifecycle-androidx/README.md) owns lifecycle and
  hierarchical saved-state coordination for adapter-backed Views.
- [Media3](../modules/viewcompose-media3-androidx/README.md),
  [legacy ExoPlayer 2](../modules/viewcompose-exoplayer2-android/README.md),
  [Google Maps](../modules/viewcompose-google-maps-android/README.md), and
  [CameraX](../modules/viewcompose-camerax-androidx/README.md) own their dependency, API, lifecycle,
  resource, device-evidence, and migration contracts.
- [ADR-0022](../architecture/decisions/0022-in-memory-development-tooling-installation.md) owns the
  development-tooling isolation correction discovered by the strict Google Maps device lane.

## Closed invariants

1. Published integrations depend on the Host and optional coordination modules, never Renderer.
2. Constructor-sensitive changes replace the native View atomically; replay-safe updates do not
   publish external work before commit.
3. Every integration releases exactly what it owns and never uses global cleanup such as
   `unbindAll()` to hide ambiguous ownership.
4. Permission, credentials, process-wide SDK configuration, and unrelated use cases remain
   application-owned and explicit in Demo fixtures.
5. Missing physical capability, missing credentials, a placeholder, or a status label never counts
   as successful device acceptance.
6. minSdk 24, compileSdk 36, Java 11, bilingual public manuals, strict API documentation, compiled
   Q3 samples, immutable release changesets, and Maven consumer verification remain required.
