---
title: Migrate image loading
slug: /migration/image-loading
---

# Migrate image loading

The generalized image pipeline replaces the old remote-only protocol with a portable source and
request contract. This is a source- and binary-breaking change for applications that implemented
the old loader or stored the old request type.

## API mapping

| Previous API | Current API | Migration action |
| --- | --- | --- |
| `RemoteImageLoader` | `UiImageLoader` | Implement `load(UiImageTarget, UiImageRequest)` and return a disposable handle. |
| `RemoteImageRequest` | `UiImageRequest` plus node fallback | Map source, placeholder, error, content scale, and `UiImageRequestOptions`; keep no-source fallback on `Image`, `Icon`, or `IconButton`. |
| `RemoteImageTarget` | `UiImageTarget` | Accept the portable target and validate the platform object in the adapter. |
| `PlatformRemoteImageTarget` | `PlatformUiImageTarget` | Use the general platform target wrapper. |
| `ProvideRemoteImageLoader` | `ProvideImageLoader` | Install the loader around the smallest image subtree. |
| `CoilRemoteImageLoader` | `CoilImageLoaderAdapter` | Replace the adapter and keep the injected Coil `ImageLoader` caller-owned. |
| `ImageSource.Remote(url)` | `ImageSource.Url(url)` | Use `Url` for a URL; use `Uri`, `File`, `Resource`, or keyed `Model` for other sources. |

## Before and after

Old code conceptually looked like this:

```kotlin
ProvideRemoteImageLoader(CoilRemoteImageLoader(imageLoader)) {
    Image(source = ImageSource.Remote(url))
}
```

The generalized form is:

```kotlin
ProvideImageLoader(CoilImageLoaderAdapter(imageLoader)) {
    Image(
        source = ImageSource.Url(url),
        requestOptions = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Target,
        ),
    )
}
```

`CoilImageLoaderAdapter` intentionally has no `Context` constructor. If old code used
`CoilRemoteImageLoader(context)`, create or obtain one application-scoped Coil `ImageLoader`, pass
it to the adapter, and shut it down only when that application owner ends.

For a non-URL source, select the matching type rather than encoding it as a URL:

```kotlin
Image(source = ImageSource.Model(value = model, stableKey = modelId))
```

`ImageSource.Url` now validates an absolute HTTP(S) URL. Use `ImageSource.Uri` for another absolute
scheme. Explicit decode-size bounds use `UiImageDecodeSize.Fixed(width, height)` with `UiDp`; the
renderer carries its captured density into `UiImageRequest`, and adapters convert those bounds to
platform pixels. Fallback is intentionally absent from `UiImageRequest`, because a null source never
starts a request.

## Adapter obligations

An adapter must map all source types it claims to support and must return a handle for the exact
operation it starts. Make disposal idempotent. Do not close an injected decoder, retain a mounted
View after disposal, or compare arbitrary model payloads as framework identity. Consume only
extension types the adapter owns and ignore other extension types.

If the application has no decoder for a source, keep the source nullable or provide a resource
fallback. Resource-only images continue to work without an adapter.

## Rollout order

1. Update the UI contract and widget imports together.
2. Replace the provider and adapter names.
3. Convert `ImageSource.Remote` call sites to the most specific current source type.
4. Add request options where the old adapter relied on implicit size, cache, or transition behavior.
5. Run renderer lifecycle tests and a recycled-row/manual verification path.
6. Remove old protocol declarations only after repository-wide production references are gone.

The [image loading guide](../guides/image-loading.md) describes the ownership and disposal rules in
more detail. The [Image Coil manual](../modules/viewcompose-image-coil/README.md) documents the
published adapter's compatibility boundary.
