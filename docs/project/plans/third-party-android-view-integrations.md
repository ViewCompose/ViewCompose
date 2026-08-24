# Third-Party Android View Integrations Plan

## Status

Active; phases 0–5 are complete in owning manuals. Maps implementation and credentialed acceptance
are complete; CameraX is the next implementation phase. Verified 2026-08-24.

## Remaining work

- **CameraX 6:** `viewcompose-camerax-androidx` / `com.viewcompose.camerax`, `PreviewView` 1.6.1.
  Request no permission; never `unbindAll`; own one Preview binding. Pass denial/fake and physical
  lifecycle, lens, rotation, navigation, Surface/frame, accessibility, and leak gates.
- **Closeout 7–9:** classify configuration/reuse/failure/restore; finish docs, samples, Demo/Preview,
  notices, consumer/Changeset and gates; interpret device/performance/leak evidence; archive. Keep
  minSdk 24, compileSdk 36, Java 11, post-commit exact cleanup, and no Renderer dependency. Missing
  capability never passes.

## Completed integration ownership

Media3, legacy ExoPlayer 2, and Google Maps implementation and acceptance details now live in their
owning module manuals. The [Google Maps manual](../../modules/viewcompose-google-maps-android/README.md)
is the canonical API, credential, lifecycle, device, visual, and limitation record. The development-
tooling startup defect found by its strict device lane, the hard cut, and the before/after evidence
are canonical in [ADR-0022](../../architecture/decisions/0022-in-memory-development-tooling-installation.md).
This active plan retains only CameraX delivery and final cross-integration closeout status.
