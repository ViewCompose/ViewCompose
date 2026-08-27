---
schema_version: 2
document_id: module.viewcompose-exoplayer2-android
doc_type: module
owner:
  kind: module
  id: viewcompose-exoplayer2-android
version_lane: released
capability_ids:
  - exoplayer2.player
artifact_ids:
  - viewcompose-exoplayer2-android
sample_ids:
  - module.exoplayer2-dependency
  - module.exoplayer2-player
coordinate: com.viewcompose:viewcompose-exoplayer2-android:0.1.0-alpha01
minimal_usage_sample_id: module.exoplayer2-dependency
---

# Legacy ExoPlayer 2 Integration

`viewcompose-exoplayer2-android` hosts a caller-owned legacy
`com.google.android.exoplayer2.Player` in `StyledPlayerView`; it is frozen compatibility, not a
Media3 alias.

## Artifact

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="exoplayer2-dependency" sample_id="module.exoplayer2-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-exoplayer2-android:0.1.0-alpha01")
}
```

Alpha: Q3 `ExoPlayerView`, Q2 configuration, Q1 enums; API 24+; discontinued SDK 2.19.1. Core is
API-visible; UI/Lifecycle are implementation-only. The optional, non-aggregate artifact has no
Media3 dependency/alias; Apache-2.0 is in `THIRD_PARTY_NOTICES.md`. Prefer Media3 for new work.

## Contract

{/* compiled-region source="viewcompose-exoplayer2-android/src/test/samples/com/viewcompose/exoplayer2/samples/ExoPlayerSamples.kt" region="exoplayer2-player" sample_id="module.exoplayer2-player" build_target=":viewcompose-exoplayer2-android:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.exoPlayerViewSample(player: Player) {
    ExoPlayerView(
        player = player,
        surfaceType = ExoPlayerSurfaceType.SurfaceView,
        configuration = ExoPlayerViewConfiguration(
            showBuffering = ExoPlayerShowBuffering.WhenPlaying,
            contentDescription = "Legacy episode video",
        ),
    )
}
```

The caller owns Player commands/state/release. A nearest `LocalLifecycleOwner` is required;
attachment starts at `ON_START`, follows resume/pause, and ends on stop/owner change/reset/release.
Release after detachment; background audio is app policy.

`surfaceType` is construction identity (`SurfaceView`, `TextureView`, `None`). Same-Surface player
replacement clears old output and reuses the View; other state is replay-safe. No unsupported
Media3 controller-animation switch is faked. First-frame generations invalidate before cleanup.

## Evidence and migration

Media3/legacy coexist; isolation tests reject cross-namespace runtime classes. On 2026-08-24, six
module and 46 Demo tests passed. One API 31 device test passed in 2.016 seconds; player and Surface
replacement plus background/return kept frames visible. **Improved** from no adapter, limited to one
muted local H.264/AAC fixture/device with no codec, feature, performance, or power claim. Next: Maps.

Use the [upstream migration guide](https://developer.android.com/media/media3/exoplayer/migration-guide)
before replacing `ExoPlayerView` with `Media3PlayerView`; no runtime conversion is provided.
Reference: [`viewcompose-exoplayer2-android` API](https://docs.viewcompose.com/api/viewcompose-exoplayer2-android/current/).
