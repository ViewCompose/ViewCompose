# CameraX AndroidX Integration

`viewcompose-camerax-androidx` hosts one CameraX 1.6.1 `Preview` in a native `PreviewView`. It
coordinates the preview Surface and the nearest AndroidX lifecycle without requesting permission,
choosing a process provider configuration, or disturbing unrelated CameraX use cases.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-camerax-androidx:0.1.0-alpha01")

    // The application, not the integration, selects the CameraX hardware backend.
    implementation("androidx.camera:camera-camera2:1.6.1")
}
```

- Stability: **Alpha**. `CameraXPreviewView` is a guided Q3 lifecycle/resource API;
  `CameraXPreviewConfiguration` and failure reports are Q2; closed policy enums are Q1.
- Platform: Android 7.0 (API 24) and newer; compile SDK 36; Java 11 bytecode.
- SDK line: AndroidX CameraX 1.6.1.
- Optional: this artifact is not included by `viewcompose-android` or a design-system aggregate.
- API-visible dependencies: `camera-core` supplies `Camera`, and `camera-lifecycle` supplies
  `ProcessCameraProvider`. `camera-view` and `viewcompose-lifecycle-androidx` remain implementation
  dependencies.

## Basic use

Resolve permission and the process provider in application code, then pass the provider as
controlled state:

```kotlin
CameraXPreviewView(
    cameraProvider = resolvedProvider,
    lensFacing = CameraXLensFacing.Back,
    configuration = CameraXPreviewConfiguration(
        contentDescription = "Document camera preview",
    ),
    onFailure = { failure -> showCameraFailure(failure.reason) },
)
```

`resolvedProvider` may be `null` while `ProcessCameraProvider.getInstance(context)` is pending. In
that state the component performs no camera work and reports `WaitingForProvider`. Configure the
process provider before resolving it if the application does not use CameraX defaults. The module
does not depend on `camera-camera2`; applications may select that backend or another compatible
CameraX configuration deliberately.

## Ownership and lifecycle

The application owns runtime permission, `ProcessCameraProvider`, global CameraX configuration,
camera policy, and all capture or analysis use cases. The integration creates and owns exactly one
`Preview` binding. It binds only after a successful ViewCompose commit and only while the nearest
`LifecycleOwner` is at least `STARTED`. It unbinds that exact `Preview` before stop, provider or
lens replacement, lifecycle-owner replacement, or final View release.

Before invoking the provider, the integration checks whether the application already holds
`android.permission.CAMERA`. A missing grant reports `PermissionDenied` without opening the camera
or launching permission UI. The integration never calls `unbindAll()`, `shutdownAsync()`, or a
permission API. It therefore cannot silently remove caller-owned `ImageCapture`, `ImageAnalysis`,
or video use cases. The first release intentionally does not assemble a combined multi-use-case
session: applications that need one atomic resolution/effect policy across preview and capture
should retain ownership of the whole CameraX session instead of mixing separately bound use cases.

`cameraProvider = null` is a waiting state, not an error. A rejected bind reports `Failed` followed
by `onFailure`, after bounded cleanup. Failure reasons distinguish missing permission, unavailable
camera selection, conflicting use cases, unsupported provider state, and unknown SDK failures
while retaining the original exception.

## Configuration, Surface, and callbacks

`CameraXPreviewConfiguration` is replay-safe state. Scale type changes update `PreviewView`
immediately. Rotation changes update the active `Preview` without rebinding; the default follows
the current display and refreshes after layout changes. Content description supplies the native
preview's accessibility label.

`CameraXPreviewImplementationMode.Compatible` is the safe default and selects CameraX's
transform-friendly `TextureView` path. The integration mounts it inside a dedicated clipping host,
so CameraX fill transforms cannot draw over adjacent declarative UI even when a surrounding
ViewCompose host permits visual overflow for decoration. `Performance` prefers CameraX's
lower-compositing-cost `SurfaceView` path, but the integration accepts it only with `FitCenter`,
`FitStart`, or `FitEnd`. A fill transform can enlarge the external Surface beyond the
`PreviewView`; Android does not guarantee that such a Surface is clipped to declarative bounds
across supported devices, so the unsafe combination fails before native construction instead of
drawing over adjacent UI. CameraX requires implementation mode to be selected before the Surface
provider is installed, so changing this parameter is construction identity and replaces the
native View. Lens changes retain `PreviewView` but replace and exactly unbind the owned `Preview`.

All callbacks run on the Android main thread and use the latest committed functions.
`onCameraBound` runs once for each successful binding and exposes the returned caller-commandable
`Camera`; do not use it after a later non-bound state. `onStreamStateChanged(Streaming)` means
CameraX is delivering frames to the native Surface, not that a particular scene or visual quality
has been recognized.

## Demo, Preview, and verification

The Demo route `camera.camerax-preview-view` keeps permission requests explicit, owns provider
initialization in its Activity, and exposes front/back lens and Surface-mode switches. Compatible
mode uses fill cropping; performance mode uses fit scaling so the Demo also makes the safe Surface
boundary visible. Static Preview supplies no provider and renders a bounded loading placeholder;
Layoutlib never claims physical frames.

Deterministic Robolectric tests cover waiting state, replay-safe configuration, construction
replacement, exact owned-use-case cleanup, lens replacement, rotation, permission classification,
callback failure, and hardware-backend isolation. Physical-device acceptance separately covers
denial, actual Surface streaming, both lenses, both implementation modes, background/foreground,
display rotation, Activity recreation, accessibility, and released binding tags.

### Accepted Pixel 4 XL evidence — 2026-08-24

Comparison context: Pixel 4 XL, Android 13, 1440 × 3040, the same Debug APK and CameraX 1.6.1.
The rejected pre-fix compatible-fill implementation exposed a `TextureView` at
`[83,1334][1357,3032]` around a declared target of `[84,1693][1356,2673]`: 359 px escaped above,
359 px below, and 1 px on each horizontal side. It visibly covered both the status and manual-check
text. After introducing the integration-owned clipping host, compatible-fill and performance-fit
each reported zero rendering descendants outside the same target; the geometric escape was reduced
from four affected edges to zero, a 100% elimination. Manual screenshots confirmed that both text
regions remain visible, compatible mode fills the target, performance mode letterboxes inside it,
and front/back frames differ.

The denied-permission lane passed 1/1 without opening the camera. The granted lane passed 1/1 and
covered streaming, both lenses, compatible-to-performance View replacement, exact old-binding
release, stop/resume, rotation, Activity recreation, accessibility, and render-surface containment.
Conclusion: **improved**. Limitations: this is one CameraX/OS/device combination in a dark stationary
scene; it is not a power, latency, image-quality, or broad-OEM benchmark. Next action: retain these
two physical lanes as release gates and add a second OEM before promoting the artifact beyond Alpha;
measure power and latency separately if `Performance` becomes a product requirement.

## Related documentation

- [Host Android module](../viewcompose-host-android/README.md)
- [Lifecycle AndroidX module](../viewcompose-lifecycle-androidx/README.md)
- [Android View tutorial](../../tutorials/android-view.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

Reference: [`viewcompose-camerax-androidx` API](https://docs.viewcompose.com/api/viewcompose-camerax-androidx/current/).
