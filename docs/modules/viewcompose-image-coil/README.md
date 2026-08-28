---
schema_version: 2
document_id: module.viewcompose-image-coil
doc_type: module
owner:
  kind: module
  id: viewcompose-image-coil
version_lane: released
capability_ids:
  - image.coil
artifact_ids:
  - viewcompose-image-coil
sample_ids:
  - module.image-coil-dependency
coordinate: com.viewcompose:viewcompose-image-coil:0.1.0-alpha05
minimal_usage_sample_id: module.image-coil-dependency
---

# Image Coil

`viewcompose-image-coil` is the optional Coil 3 adapter for ViewCompose image nodes. It translates
the platform-neutral source and request contract into Android `ImageView` requests without making
the renderer or widget modules depend on a networking or image-loading implementation.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="image-coil-dependency" sample_id="module.image-coil-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-image-coil:0.1.0-alpha05")
}
```

- Stability: **Alpha**. The adapter boundary is established; request policy follows Coil 3.
- Platform: Android 7.0 (API 24) and newer.
- Optional: local resources and the core renderer work without this artifact.
- UI contract is exposed transitively because portable image request types appear in public adapter
  APIs. Renderer remains an implementation dependency; neither module depends back on this artifact.
- Coil Core is an API dependency because `CoilImageLoaderAdapter` accepts `coil3.ImageLoader` in
  its public constructor. The OkHttp fetcher remains an implementation dependency.

## Installation

Create one `CoilImageLoaderAdapter` from an application-scoped Coil `ImageLoader` and supply it to
`ProvideImageLoader` or the host configuration that owns image loading. Sharing the loader
preserves application networking configuration and maximizes memory and disk cache reuse.
The adapter never constructs or shuts down a loader, so creation and application-lifecycle cleanup
remain explicit at the integration boundary.

## Source, request, and target model

The adapter accepts `ImageSource.Resource`, `Url`, `Uri`, `File`, and keyed `Model` values. Each
`UiImageRequest` carries optional placeholder and error resource IDs plus portable decode-size,
cache, transition, content-scale, and typed-extension options. Null-source fallback is resolved by
the renderer before a request exists. The adapter accepts only renderer targets wrapping an Android
`ImageView` and ignores extension types it does not own. Fixed `UiDp` decode bounds are converted
with the renderer-captured request density before Coil receives physical pixel dimensions.

Loading is asynchronous. The adapter returns a disposable handle for the started Coil request;
the renderer owns that handle and disposes it before a replacement request or mounted-node removal.
The supplied Coil `ImageLoader` remains independent from individual View lifecycles.

## Caching and ownership

Memory cache, disk cache, network behavior, transformations, and URL interpretation are Coil
policies. This adapter adds no second cache. For a primary Android resource it supplies a stable
memory-cache identity containing the captured resource revision, preventing a night/locale/density
variant from reusing a stale decoded entry. Remote-only requests keep Coil's normal identity; a
resource placeholder may restart binding without discarding the remote primary cache. A caller-supplied
`ImageLoader` remains caller-owned and is never shut down by `CoilImageLoaderAdapter`.

Resource IDs are forwarded unchanged. Invalid resources and request failures therefore follow
normal Android and Coil error behavior.

## Testing and operations

- Reuse one application loader to make cache behavior deterministic across screens.
- Test fast rebinding or recycled list rows to verify that stale requests never replace newer data.
- Exercise placeholder, error, resource, keyed-model, cache-policy, and offline paths separately.
- Configure authentication, interceptors, cache budgets, and observability on the injected Coil
  loader rather than in ViewCompose.

## Related documentation

- [UI Contract module](../viewcompose-ui-contract/README.md)
- [Renderer module](../viewcompose-renderer-android/README.md)
- [Image loading guide](../../guides/image-loading.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-image-coil` API tree](https://docs.viewcompose.com/api/viewcompose-image-coil/current/).

## Compatibility notes

The `0.1.0-alpha03` line forwards the portable request directly to Coil 3. It does not expose Coil
transformations in the declarative image contract, manage a global loader, or promise cache policy
independent of the configured Coil version.
