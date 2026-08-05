# Shadow Android

`viewcompose-shadow-android` is the optional Android backend for ViewCompose drop shadows and inner
shadows. It resolves declarative shadow modifiers into pixel-space specifications, rasterizes exact
multi-layer effects, and connects them to the renderer's parent drawing planes.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-shadow-android:0.1.0-alpha03")
}
```

- Stability: **Alpha**. Modifier contracts are stable for the alpha line; backend selection and
  raster fidelity may evolve with device evidence.
- Platform: Android 7.0 (API 24) and newer.
- Optional: core rendering compiles and runs without this artifact.
- Renderer and UI contract are exposed transitively because their decoration and drawing contract
  types appear in public backend APIs; no core module depends back on this artifact.

## Installation and dependency boundary

Packaging the artifact registers `ShadowViewDecorationBackend` through `ServiceLoader`. Discovery
is attempted at most once when a non-empty decoration request first needs a backend. Applications
may instead call `ShadowDecorationLayer.install()` during application initialization.

The backend uses the renderer's existing decoration host contract. It does not replace every root
layout and does not add a wrapper `View` around decorated children. Parents that already participate
in decoration drawing invoke the backend before and after the normal child draw only for children
whose resolved decoration presence is non-empty.

Without the artifact, shadow modifiers degrade to an absent optional backend while ordinary nodes,
layout, input, and rendering continue to work.

## Resolution and ordering

`ShadowSpecResolver` and `InnerShadowSpecResolver` convert dp dimensions to physical pixels once at
the Android binding boundary. An explicit shadow-group shape wins over the node's default shape;
otherwise a rectangular outline is used. Element and layer order are preserved.

Drop shadows draw immediately before the child's content. Inner shadows draw after the child's
background, content, subtree, and foreground. Both planes share the child's sibling/z ordering and
follow its matrix, alpha, scroll-relative position, and layout direction without changing layout or
input dispatch.

## Raster and memory model

Drop and inner shadows use separate 8 MiB LRU caches by default. One raster is rejected above 32 MiB
or when either bitmap dimension exceeds 8192 pixels. Valid entries larger than the cache budget may
be returned for the current draw but are not retained.

Cache identity includes content width/height, layout direction, and the full resolved specification.
Translation and ordinary invalidation reuse the bitmap; size, density, outline, or shadow changes
produce a different key. Rasterizers are UI-thread confined. Returned bitmaps are cache-owned and
must not be mutated or recycled.

Call `ShadowDecorationLayer.clearCache()` during explicit memory-pressure handling if the process
needs immediate eviction. This clears raster and display-list entries but retains diagnostics.

## Replay policies

- `Auto`: direct exact-bitmap replay while cross-device evidence is pending.
- `ExactBitmap`: explicitly uses `Canvas.drawBitmap`.
- `RenderNodeDisplayList`: caches bitmap replay in a RenderNode on Android 10+ hardware canvases.

RenderNode requests fall back to bitmap replay below API 29, on software canvases, or after a runtime
RenderNode failure. The policy affects bitmap replay only; it does not replace exact rasterization.
Use `backendStats()`, `cacheStats()`, and `innerCacheStats()` for diagnostics.

## Testing and operations

- Test resolver output independently from pixels.
- Use screenshot/device tests for blur, spread, offset, corner families, RTL, and clipping fidelity.
- Exercise software canvas and pre-29 fallbacks before selecting RenderNode explicitly.
- Monitor oversized skips and cached bytes for unusually large surfaces.
- Install the backend before the first decorated render if deterministic startup is required.

## Related documentation

- [Renderer module](../viewcompose-renderer/README.md)
- [UI Contract module](../viewcompose-ui-contract/README.md)
- [Graphics module](../viewcompose-graphics/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-shadow-android` API tree](https://docs.viewcompose.com/api/viewcompose-shadow-android/current/).

## Compatibility notes

The `0.1.0-alpha03` line establishes optional ServiceLoader discovery, wrapper-free decoration
planes, exact multi-layer rasterization, byte-bounded caches, and explicit RenderNode replay with
capability fallbacks. It does not promise native elevation equivalence or a general RenderEffect
pipeline.
