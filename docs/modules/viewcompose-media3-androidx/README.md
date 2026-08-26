---
schema_version: 2
document_id: module.viewcompose-media3-androidx
doc_type: module
owner:
  kind: module
  id: viewcompose-media3-androidx
version_lane: released
capability_ids:
  - media3.player
artifact_ids:
  - viewcompose-media3-androidx
sample_ids:
  - module.media3-dependency
  - module.media3-player
coordinate: com.viewcompose:viewcompose-media3-androidx:0.1.0-alpha01
minimal_usage_sample_id: module.media3-dependency
---

# Media3 AndroidX Integration

`viewcompose-media3-androidx` hosts a caller-owned AndroidX Media3 `Player` in a native
`PlayerView`. It coordinates View attachment, listeners, video Surface ownership, and the nearest
AndroidX lifecycle without taking ownership of playback or player release.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="media3-dependency" sample_id="module.media3-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-media3-androidx:0.1.0-alpha01")
}
```

- Stability: **Alpha**. `Media3PlayerView` is a guided Q3 lifecycle/resource API;
  `Media3PlayerViewConfiguration` is Q2; the closed policy enums are Q1.
- Platform: Android 7.0 (API 24) and newer.
- SDK line: AndroidX Media3 1.10.1. Media3 1.11.0 is compiled with Kotlin 2.2 metadata and is not
  consumable by this repository's Kotlin 2.0 compiler. Changing that line requires an explicit
  project toolchain compatibility review.
- Optional: the artifact is not included by `viewcompose-android` or a design-system aggregate.
- `media3-common` is API-visible because `Player` appears in the component signature.
  `media3-ui` and `viewcompose-lifecycle-androidx` remain implementation dependencies.

## Basic use

{/* compiled-region source="viewcompose-media3-androidx/src/test/samples/com/viewcompose/media3/samples/Media3Samples.kt" region="media3-player" sample_id="module.media3-player" build_target=":viewcompose-media3-androidx:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.media3PlayerViewSample(player: Player) {
    Media3PlayerView(
        player = player,
        surfaceType = Media3SurfaceType.SurfaceView,
        configuration = Media3PlayerViewConfiguration(
            useController = true,
            showBuffering = Media3ShowBuffering.WhenPlaying,
            contentDescription = "Episode video",
        ),
        onRenderedFirstFrame = {
            // Update caller-owned UI state or diagnostics here.
        },
    )
}
```

The caller creates, configures, commands, and eventually releases the `Player`. The integration
never calls `play`, `pause`, `stop`, or `release`. Release the player only after the Activity,
Fragment View, or nested ViewCompose host has ended so the integration can first detach its
listener and Surface. The player's application looper must be Android's main looper, as required by
`PlayerView`.

## Lifecycle and identity

The nearest `LocalLifecycleOwner` is mandatory. The integration attaches the committed player at
`ON_START`, forwards `PlayerView.onResume()` and `onPause()`, and removes the listener, player
reference, and video output at `ON_STOP`, owner replacement, mounted-tree reset, or permanent View
release. A hidden retained navigation destination therefore cannot keep a video Surface solely
because its Activity remains resumed. Background audio and service/session policy stay with the
caller.

`surfaceType` is native construction identity. Switching among `SurfaceView`, `TextureView`, and
`None` atomically replaces `PlayerView`; it is not simulated through reset or a mutable SDK field.
`SurfaceView` is the default because Media3 recommends it for lower power, frame timing, HDR, and
secure output. Choose `TextureView` only when transforms or animation need it. Player replacement
with the same Surface type reuses the native View and detaches the preceding player first.

## Replay-safe configuration and callbacks

`Media3PlayerViewConfiguration` is complete replay-safe state. It controls resize mode, controller
enablement and timeout/visibility policy, buffering display, artwork, shutter color, retained
content, accessibility description, screen-on behavior, and a custom error message. Constructor
validation rejects negative controller timeouts before View work begins.

`onRenderedFirstFrame` is installed only for the committed, started attachment and runs on the
Android main thread. Replacement, stop, reset, and release invalidate the old listener before any
new attachment. The callback may update caller state but must not block dispatch or retain
framework scopes.

## Demo, Preview, and verification

The Demo route `media.media3-player-view` uses two Activity-owned ExoPlayers and a repository-owned
two-second MP4 asset. Its metadata records generation, codecs, ownership, and SHA-256. The same page
has a player-free static Preview placeholder; Preview never starts decoding or network work.

Robolectric coverage verifies started-only attachment, player replacement, exact native Surface
selection, complete configuration, first-frame callback invalidation, cleanup, and caller release
ownership. Physical-device acceptance additionally covers local first frame, both Surface types,
background/foreground reattachment, and visible video after each transition.

## Related documentation

- [Host Android module](../viewcompose-host-android/README.md)
- [Lifecycle AndroidX module](../viewcompose-lifecycle-androidx/README.md)
- [Android View tutorial](../../tutorials/android-view.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-media3-androidx` API tree](https://docs.viewcompose.com/api/viewcompose-media3-androidx/current/).
