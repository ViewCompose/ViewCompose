# Image Coil

`viewcompose-image-coil` is the optional Coil 3 adapter for ViewCompose remote image nodes. It
translates the platform-neutral loading contract into Android `ImageView` requests without making
the renderer or widget modules depend on a networking or image-loading implementation.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-image-coil:0.1.0-alpha01")
}
```

- Stability: **Alpha**. The adapter boundary is established; request policy follows Coil 3.
- Platform: Android 7.0 (API 24) and newer.
- Optional: local resources and the core renderer work without this artifact.
- It depends on `viewcompose-ui-contract` and `viewcompose-renderer`; neither depends back on it.

## Installation

Create one `CoilRemoteImageLoader` from an application-scoped Coil `ImageLoader` and supply it to
the ViewCompose host or renderer configuration that owns remote image loading. Sharing the loader
preserves application networking configuration and maximizes memory and disk cache reuse.

The convenience `Context` constructor builds a dedicated loader. It is useful for small integrations,
but callers that need centralized lifecycle or cache management should inject their own loader.

## Request and target model

The renderer invokes the adapter only for non-blank remote URLs. Each request carries a URL and
optional Android placeholder, error, and fallback resource IDs. The adapter accepts only renderer
targets that expose an Android `ImageView`; other platform objects are ignored safely.

Loading is asynchronous. Coil writes results directly into the target view and owns target-aware
request replacement and cancellation. Rebinding the same `ImageView` therefore delegates stale-work
protection to Coil. The ViewCompose contract intentionally exposes no request handle.

## Caching and ownership

Memory cache, disk cache, network behavior, transformations, and URL interpretation are Coil
policies. This adapter adds no second cache and does not synthesize cache keys. A caller-supplied
`ImageLoader` remains caller-owned and is never shut down by `CoilRemoteImageLoader`.

Resource IDs are forwarded unchanged. Invalid resources and request failures therefore follow
normal Android and Coil error/fallback behavior.

## Testing and operations

- Reuse one application loader to make cache behavior deterministic across screens.
- Test fast rebinding or recycled list rows to verify that stale requests never replace newer data.
- Exercise placeholder, error, fallback, blank-URL, and offline paths separately.
- Configure authentication, interceptors, cache budgets, and observability on the injected Coil
  loader rather than in ViewCompose.

## Related documentation

- [UI Contract module](../viewcompose-ui-contract/README.md)
- [Renderer module](../viewcompose-renderer/README.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-image-coil` API tree](https://docs.viewcompose.com/api/viewcompose-image-coil/current/).

## Compatibility notes

The `0.1.0-alpha01` line forwards the portable request directly to Coil 3. It does not expose Coil
transformations in the declarative image contract, manage a global loader, or promise cache policy
independent of the configured Coil version.
