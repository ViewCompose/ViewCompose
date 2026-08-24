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

## Maps evidence

On 2026-08-24, 16 Maps module tests and 46 Demo tests passed, as did strict Maps Dokka generation,
AndroidTest compilation, documentation, translation, dependency-contract, release-intent,
`qaQuick`, and `qaPreview` checks. One Pixel 4 XL / API 33 credential-free device method passed in
0.874 seconds: no `MapView`
was created, scheme/city actions published visible state, reset restored the initial state, and
Activity recreation remained explicit. Two 1440 x 3040 screenshots were visually inspected before
and after both actions; controls, state, placeholder, and manual instructions remained visible with
no overlap. This is **improved** from having no Maps adapter or acceptance fixture. It is not a Maps
renderer, network, performance, power, or leak result.

The final credentialed Pixel 4 XL / API 33 method passed in 19.422 seconds using the LATEST renderer
and remote `maps_core` 260830204. A package- and certificate-restricted external key was supplied
only through local Gradle user properties. Real tiles loaded; `onMapLoaded` remained exactly one per
native generation; JSON style acceptance, camera/marker/polyline changes, same-View state updates,
background/resume reuse, Activity recreation, accessibility, UI Context, and released-binding
cleanup all passed. Two 1440 x 3040 credentialed screenshots were visually inspected in light
Shanghai and dark Hangzhou states; tiles, overlays, controls, status, and layout matched the Demo
contract.

The first strict run exposed four Host-owned main-thread disk reads from inactive development-tool
`ServiceLoader` discovery. The implementation was hard-cut across Host and Animation to
pre-`Application.onCreate` in-memory installation under ADR-0022; the final thread/VM `StrictMode`
run reported zero integration-owned violations. Google Maps itself reported 18
`IncorrectContextUseViolation` and five `UntaggedSocketViolation` events after the stack entered
Google code, despite receiving a UI Context. The result is **improved** and closes Maps integration
correctness. Limitations: no heap-wide leak profile, frame-performance, power, renderer comparison,
offline, permission, or location-layer claim; the network-dependent method duration is not a
performance measurement. CameraX is the next action.
