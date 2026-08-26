---
schema_version: 2
document_id: module.viewcompose-image-glide
doc_type: module
owner:
  kind: module
  id: viewcompose-image-glide
version_lane: released
capability_ids:
  - image.glide
artifact_ids:
  - viewcompose-image-glide
sample_ids:
  - module.image-glide-dependency
coordinate: com.viewcompose:viewcompose-image-glide:0.1.0-alpha02
minimal_usage_sample_id: module.image-glide-dependency
---

# Image Glide

`viewcompose-image-glide` is the optional Glide 5 adapter for ViewCompose image nodes. It translates
the platform-neutral source and request contract into Android `ImageView` requests without making
the renderer or widget modules depend on Glide.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="image-glide-dependency" sample_id="module.image-glide-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-image-glide:0.1.0-alpha02")
}
```

- Stability: **Alpha**. The adapter boundary is established; request execution follows Glide 5.
- Platform: Android 7.0 (API 24) and newer.
- Optional: local resources and the core renderer work without this artifact.
- UI contract is exposed transitively because portable image request types appear in public adapter
  APIs. Renderer remains an implementation dependency; neither module depends back on this artifact.
- Glide remains an implementation dependency because no Glide type appears in the adapter's public
  API. The application still configures Glide through its normal generated API and `AppGlideModule`.

## Installation

Create one `GlideImageLoaderAdapter` and supply it to `ProvideImageLoader` or the host configuration
that owns image loading. The copy-ready file-loading example lives in the
[image loading guide](../../guides/image-loading.md), so the integration setup has one compiled
source owner instead of a duplicated module-local snippet.

The adapter resolves `Glide.with(imageView)` for every request. This preserves the lifecycle scope
selected by Glide for the mounted target while retaining application-owned registry, networking,
cache, and default-request configuration.

## Source, request, and target model

The adapter accepts `ImageSource.Resource`, `Url`, `Uri`, `File`, and keyed `Model` values. Each
`UiImageRequest` carries optional placeholder and error resource IDs plus portable decode-size,
cache, transition, content-scale, and typed-extension options. Null-source fallback is resolved by
the renderer before a request exists. The adapter accepts only renderer targets wrapping an Android
`ImageView` and ignores extension types it does not own.

`UiImageDecodeSize.Target` preserves Glide's target-size resolution, `Original` maps to
`Target.SIZE_ORIGINAL`, and fixed `UiDp` bounds are converted with the renderer-captured request
density before Glide receives physical pixel dimensions. Content scale maps to Glide's crop, fit,
inside, or no-transform request options.

## Caching, transitions, and ownership

Default cache and transition policies preserve the application's Glide configuration. Disabled
memory cache maps to `skipMemoryCache(true)`, disabled disk cache maps to `DiskCacheStrategy.NONE`,
and explicit `None` or `Crossfade` transitions override the configured default for that request.
Primary Android resources receive an `ObjectKey` signature containing the captured resource
revision, so configuration-qualified drawables cannot reuse a stale cache entry. Remote-only
requests retain Glide's normal model/cache identity; resource fallback changes still restart the
mounted request through renderer request equality.

The adapter returns a disposable handle that clears the exact Glide target request. The renderer
disposes it before replacement or mounted-node removal. The adapter does not own the target
`ImageView`, Glide singleton, application cache, or `AppGlideModule`.

## Testing and operations

- Configure authentication, model loaders, decoders, cache budgets, and observability in Glide.
- Test fast rebinding or recycled rows so disposed requests cannot replace newer content.
- Exercise resource, URL, URI, file, keyed-model, placeholder, error, cache, and transition paths.
- Keep adapter-specific options in immutable typed request extensions with stable identities.

## Related documentation

- [UI Contract module](../viewcompose-ui-contract/README.md)
- [Renderer module](../viewcompose-renderer-android/README.md)
- [Image loading guide](../../guides/image-loading.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-image-glide` API tree](https://docs.viewcompose.com/api/viewcompose-image-glide/current/).

## Compatibility notes

The `0.1.0-alpha01` line targets Glide 5.0.7. It does not expose Glide request builders in the
declarative contract, create a second cache, or replace application-level Glide configuration.
