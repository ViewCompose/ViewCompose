---
schema_version: 2
document_id: guide.image-loading
doc_type: guide
owner:
  kind: capability
  id: image.foundation
version_lane: released
capability_ids:
  - image.foundation
  - image.coil
  - image.glide
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-ui-contract
  - viewcompose-renderer-android
  - viewcompose-image-coil
  - viewcompose-image-glide
sample_ids:
  - guide.image-loader-install
  - guide.image-custom-loader
  - guide.image-coil-uri
  - guide.image-glide-file
task: Install the smallest image-loading integration, preserve caller ownership, and make each target request disposable.
success_checks:
  - Resource-only content renders without an optional loader.
  - Remote, URI, file, and model sources run through one explicitly scoped loader.
  - Every started request returns an idempotent per-target handle and never shuts down the caller-owned decoder.
  - Placeholder, error, fallback, decode-size, cache, transition, and resource-revision behavior is verified separately.
failure_checks:
  - A decoder singleton or target View is treated as renderer-owned.
  - Global cancellation is used instead of the handle for the exact started request.
  - A decoder-specific request type leaks into UI Contract or Renderer.
  - Model payload equality or an unchanged stableKey hides a source-content change.
---

# Image loading

ViewCompose keeps image declaration, Android View binding, and image decoding in separate layers.
Use `ImageSource` and `UiImageRequest` in the UI contract, install an optional `UiImageLoader` at a
widget boundary, and let the renderer own the lifetime of the operation attached to each `ImageView`.

## Choose the smallest integration

| Need | Configuration |
| --- | --- |
| Bundled drawable or resource | Use `ImageSource.Resource`; no loader is required. |
| A custom decoder or test fake | Implement `UiImageLoader` and return a disposable `UiImageLoadHandle`. |
| Coil 3 networking, caching, and decoding | Add `viewcompose-image-coil` and install `CoilImageLoaderAdapter`. |
| Glide 5 networking, caching, and decoding | Add `viewcompose-image-glide` and install `GlideImageLoaderAdapter`. |
| Another platform decoder | Keep the adapter in its own optional module and map the portable request at the Android boundary. |

The core modules do not assume a network, cache, or decoder. A missing loader is therefore a valid
configuration, not an error.

## Install a loader

Install a loader around the smallest subtree that needs it. The provider is read while `Image` or
`Icon` emits its `NodeSpec`:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ImageLoadingGuideSamples.kt" region="image-loader-install" sample_id="guide.image-loader-install" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
val imageLoader = CoilImageLoaderAdapter(applicationCoilImageLoader)

ProvideImageLoader(imageLoader) {
    Image(
        source = ImageSource.Url("https://example.test/banner.png"),
        contentDescription = "Banner",
        placeholder = ImageSource.Resource(android.R.drawable.ic_menu_gallery),
        error = ImageSource.Resource(android.R.drawable.ic_dialog_alert),
        fallback = ImageSource.Resource(android.R.drawable.ic_menu_report_image),
        requestOptions = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Target,
            memoryCachePolicy = UiImageCachePolicy.Default,
            diskCachePolicy = UiImageCachePolicy.Default,
            transition = UiImageTransition.Crossfade(durationMillis = 180),
        ),
    )
}
```

`ImageSource.Resource` can be used with or without a loader. `Url`, `Uri`, `File`, and `Model`
sources require a loader to produce a decoded result. When no loader is installed, the renderer
clears any previous operation and applies the direct fallback, error, or placeholder resource.

## Source and request policy

`ImageSource` is intentionally small and serializes no decoder-specific state:

- `Resource` identifies an Android drawable resource.
- `Url` stores an absolute HTTP or HTTPS URL.
- `Uri` stores an absolute URI for any loader-supported scheme.
- `File` stores a non-empty file path.
- `Model` stores an arbitrary adapter payload plus an explicit stable key.

`UiImageRequestOptions` carries policy that can be shared by adapters without exposing their
implementation types:

- `UiImageDecodeSize.Target`, `Original`, or positive logical `Fixed` bounds expressed with `UiDp`;
- independent default or disabled memory and disk cache policy;
- default, none, or crossfade transition; and
- an immutable list of typed extensions whose identity is their concrete type plus `stableKey`.

Adapters ignore extension types they do not own. An extension's `stableKey` must change whenever an
option that affects loading changes. Placeholder and error values are resource IDs rather than
drawable instances. Fallback is deliberately node state, not request state: the renderer applies it
only when `source == null` and never starts a loader for that case. This keeps the request portable
and prevents a node spec from owning a View or Drawable. The renderer copies the subtree's captured
`UiDensity` into each `UiImageRequest`; adapters must use it when converting `Fixed` decode bounds to
the physical pixels expected by their decoder.

The renderer also copies the subtree's captured `resourceRevision` when any source, placeholder,
error, or fallback is resource-backed. Equal integer resource IDs can therefore reload after a
locale, night, density, or theme-resource change. First-party Coil and Glide adapters include that
revision in primary resource cache identity while leaving remote-only cache identity unchanged.

## Lifetime and recycled Views

The renderer runs image binding on the UI thread and stores the returned handle on the mounted
`ImageView`. An identical request leaves both the loaded result and current handle untouched. For a
changed source, loader, or request option it:

1. clears the binding tag and disposes the old handle;
2. applies the new placeholder state;
3. starts the new request; and
4. stores the new handle only after the loader starts successfully.

Removing a node, disposing a mounted tree, or rolling back an uncommitted candidate clears the tag
before disposal. A loader must make its handle idempotent and must stop callbacks that could write
to a disposed or recycled View. The injected loader itself remains caller-owned; renderer disposal
must never shut it down.

This ordering is the protection against out-of-order work in lazy lists and rapidly reused rows.
Do not retain an `ImageView` from a loader after its handle has been disposed, and do not use a
decoder's global cancellation API as a substitute for the per-request handle.

## Implement an adapter

An adapter validates its target, maps every supported `ImageSource` subtype, forwards portable
request options, starts decoder work, and returns a handle that cancels only that work:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ImageLoadingGuideSamples.kt" region="image-custom-loader" sample_id="guide.image-custom-loader" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
class TestImageLoader : UiImageLoader {
    override fun load(target: UiImageTarget, request: UiImageRequest): UiImageLoadHandle {
        val imageView = (target as PlatformUiImageTarget).target as ImageView
        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        return UiImageLoadHandle { /* cancel only this request */ }
    }
}
```

Production adapters should also test target validation, every source mapping, decode-size mapping,
cache and transition policy, idempotent disposal, and ownership of the injected decoder. The adapter
must not add a second framework cache or change the meaning of `ImageSource.Model` equality.

## Coil 3

`viewcompose-image-coil` is the published optional adapter. Use an application-scoped Coil
`ImageLoader` when the app owns networking and cache policy:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ImageLoadingGuideSamples.kt" region="image-coil-uri" sample_id="guide.image-coil-uri" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
val imageLoader = CoilImageLoaderAdapter(applicationCoilImageLoader)
ProvideImageLoader(imageLoader) {
    Image(source = ImageSource.Uri(contentUri), contentDescription = "Content")
}
```

The adapter forwards placeholder and error resources, size, cache policy, transitions, and content
scale to Coil. Null-source fallback remains renderer-owned. The adapter never shuts down the
supplied `ImageLoader`.

For module-specific compatibility and operational guidance, see the
[Image Coil manual](../modules/viewcompose-image-coil/README.md).

## Glide 5

`viewcompose-image-glide` provides `GlideImageLoaderAdapter`. It resolves a lifecycle-associated
`RequestManager` from each target `ImageView`, while requests inherit the app's `AppGlideModule`,
registry, cache, and default request configuration:

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ImageLoadingGuideSamples.kt" region="image-glide-file" sample_id="guide.image-glide-file" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
val imageLoader = GlideImageLoaderAdapter()
ProvideImageLoader(imageLoader) {
    Image(
        source = ImageSource.File(file),
        contentDescription = "Downloaded image",
        requestOptions = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Fixed(width = 640.dp, height = 360.dp),
            transition = UiImageTransition.None,
        ),
    )
}
```

`Default` transition preserves Glide's configured default; `None` and `Crossfade` explicitly
override it. Target, original, and density-resolved fixed decode sizes map to Glide without
introducing a framework cache. The adapter clears only the request target represented by its
returned handle and does not own the target `ImageView` or Glide singleton.

For compatibility, ownership, and operational guidance, see the
[Image Glide manual](../modules/viewcompose-image-glide/README.md).

## Verification checklist

- Verify resource-only rendering with no loader installed.
- Verify a custom loader receives a platform target and a complete portable request.
- Rebind a row from one source to another before the first operation completes.
- Dispose a mounted tree and assert that no delayed callback writes to its old `ImageView`.
- Test placeholder, error, fallback, disabled cache, explicit size, and transition behavior.
- Keep adapter dependencies optional; UI Contract, UI Foundation, and Renderer must compile without them.

## Related documentation

- [UI Contract module](../modules/viewcompose-ui-contract/README.md)
- [UI Foundation module](../modules/viewcompose-ui-foundation/README.md)
- [Renderer module](../modules/viewcompose-renderer-android/README.md)
- [Migrate image loading](../migration/image-loading.md)
