# General Image Loading Pipeline and Glide Adapter Plan

## Status

Active. The contract, renderer, Coil and Glide adapters, Demo, migration, and public documentation
are implemented and pass `qaQuick`. Glide publication metadata, strict documentation status, module
catalog entry, bilingual manual, and immutable documentation history now reference the truthful
source-freeze revision `79a78900fc33d629ca0de831c633529ec79947dd`. Strict API and complete-history
documentation assembly pass. Plan archival remains open while the separately reproduced Preview
snapshot baseline, legacy release-tag metadata, and the locked-device instrumentation limitation
are tracked explicitly.

## Scope

Generalize ViewCompose image loading from a remote-URL-only adapter boundary into one target-aware
image request pipeline that can process local and remote sources through Coil, Glide, or a future
loader integration.

The work includes:

1. replacing the public `RemoteImage*` protocol with a general `UiImage*` protocol;
2. routing Android resources through the configured loader so local images can use target-sized
   decoding, cropping, caching, transitions, and loader extensions;
3. adding explicit request replacement and disposal semantics owned by the renderer;
4. migrating the Coil adapter to the general protocol;
5. adding an optional `viewcompose-image-glide` adapter;
6. documenting the cross-module capability, alpha migration, and independent artifact contracts;
7. adding deterministic tests for source mapping, request replacement, rollback, View reuse, and
   disposal.

## Non-goals

- encoded-file compression, transcoding, export, or writing transformed images to storage;
- exposing the complete union of Coil and Glide request-builder APIs in `viewcompose-ui-contract`;
- adding Picasso or Fresco adapters in this cycle;
- replacing the renderer's Android `ImageView` target;
- making image loading a required dependency of the core render path;
- adding network-dependent unit, screenshot, or instrumentation tests;
- upgrading Coil while generalizing the protocol;
- supporting one-shot `InputStream` values as a first-class immutable `ImageSource`;
- applying the general loader to compound resource icons owned by unrelated nodes such as ordinary
  `Button` leading/trailing drawables in this cycle.

In this plan, "compression" means decoding/downsampling close to the requested display size. File
compression is a different capability and requires a separate API and ownership design.

## Current baseline

Verified on 2026-08-04:

- `ImageSource` is sealed to `Resource(resId)` and `Remote(url)`.
- `RemoteImageRequest` carries only a normalized URL plus placeholder, error, and fallback Android
  resource IDs.
- `RemoteImageLoader.load` returns no handle and exposes no explicit clear/dispose operation.
- `Image`, `Icon`, and `IconButton` capture `ImageLoading.current` into `ImageNodeSpec`.
- `MediaViewBinder` resolves `ImageSource.Resource` directly with `ImageView`; only a non-blank
  remote URL reaches the configured loader.
- `CoilRemoteImageLoader` enqueues into an `ImageView` but discards Coil's request handle.
- renderer disposal has no image-request cleanup operation.
- widget tests cover DSL emission and scoped loader inheritance; there is no direct renderer media
  binding suite and no behavioral Coil adapter suite.
- `viewcompose-image-coil` currently uses Coil `3.2.0`. Keep that version during this change unless
  a compile-blocking incompatibility is demonstrated independently.
- the repository is on the `0.1.0-alpha02` artifact line; this plan intentionally permits one
  documented alpha breaking migration instead of maintaining two image protocols indefinitely.

## Locked architecture decisions

These decisions are inputs to implementation. An implementation agent must not change them merely
to make one step easier. A change requires updating this plan and the architecture documentation
before code.

### 1. General names replace remote-only names

The final public protocol uses names in the following family:

- `UiImageLoader`
- `UiImageRequest`
- `UiImageTarget`
- `PlatformUiImageTarget`
- `UiImageLoadHandle`
- `UiImageRequestOptions`
- `ProvideImageLoader`

The `Ui` prefix avoids routine import collisions with `coil3.ImageLoader` and makes the ownership
of the integration contract explicit.

The final state does not keep a second runtime path based on `RemoteImageLoader`,
`RemoteImageRequest`, `RemoteImageTarget`, `PlatformRemoteImageTarget`, or
`ProvideRemoteImageLoader`. The old and new declarations may coexist in an intermediate local
commit to keep the branch compilable, but the completed change removes the old protocol and
provides a migration table. Do not add reflection or hidden compatibility maps.

### 2. Source absence is separate from a valid source

`Image` and the image node contract accept `source: ImageSource?`.

- `null` means no primary data and selects `fallback` without invoking a loader.
- URL and URI source values are non-blank by construction.
- the old `ImageSource.Remote(null)` sentinel is removed.
- an invalid or unsupported non-null source is a load failure and selects `error`; it is not treated
  as absent data.

### 3. The first general source family is closed and typed

Keep `ImageSource` sealed for deterministic renderer and adapter handling. Replace/add variants so
the final family covers:

- `Resource(resId: Int)` for Android drawable resources;
- `Url(url: String)` for absolute HTTP/HTTPS URLs;
- `Uri(uri: String)` for absolute `content:`, `file:`, `asset:`, `android.resource:`, `data:`, or
  loader-supported schemes without importing `android.net.Uri` into the contract module;
- `File(file: java.io.File)` for local files;
- `Model(value: Any, stableKey: Any)` for an adapter/custom model, already-decoded object, byte
  container, video-frame model, authenticated URL model, or a future loader-specific source.

`Model` is a deliberate escape hatch, not a dynamic property system. It must:

- compare and hash by an immutable type discriminator plus `stableKey`, never by raw payload
  traversal;
- redact `value` from `toString` and diagnostics;
- document that `stableKey` must change whenever the bytes or load behavior represented by the
  model change;
- retain but never close, recycle, mutate, or otherwise take ownership of `value`;
- reject a missing key rather than silently use mutable byte contents or a one-shot stream as
  identity.

Do not add first-class `ByteArray` or `InputStream` variants in this cycle. They have mutation,
copying, equality, and close-ownership concerns and can use `Model` with an explicit stable key.

### 4. A configured loader handles local resources too

Renderer dispatch is fixed as follows:

1. dispose the request previously associated with the target when request identity changes;
2. if `source == null`, bind `fallback` directly and do not call a loader;
3. if a `UiImageLoader` exists, delegate every non-null source, including `Resource`, to it;
4. if no loader exists and the source is `Resource`, retain the current direct Android resource
   path;
5. if no loader exists and the source is not `Resource`, bind `error ?: placeholder ?: fallback`;
6. if the source itself has not changed, metadata-only patches must not restart the request unless a
   request option that affects loading changed.

This is the behavior that lets a local resource reuse Coil or Glide resizing, crop/downsampling,
cache, transition, and extension behavior without making either library a core dependency.

### 5. Renderer owns request handles; callers own loaders and source models

`UiImageLoader.load(target, request)` starts or replaces work synchronously on the Android main
thread and returns one `UiImageLoadHandle`.

`UiImageLoadHandle.dispose()` must be:

- idempotent;
- safe after asynchronous success or failure;
- responsible for cancelling/clearing target-associated work started by that call;
- invoked on the main thread before another request is installed and during mounted-node disposal.

The renderer stores exactly one current binding per `ImageView` in a dedicated resource-ID tag.
The stored binding contains the loader, normalized request identity, and handle. Do not store the
binding in a process-global registry or in `ImageNodeSpec`.

The renderer never shuts down a caller-supplied Coil loader, Glide singleton/configuration, custom
model, `Bitmap`, or `Drawable`. Adapter implementations must provide a strong start guarantee: once
`load` returns, the returned handle owns all started work; if `load` throws, the adapter leaves no
unowned request running.

Request start/replacement must be replay-safe for renderer rollback. A failed render may rebind the
previous VNode, which is allowed to restart the prior request. Add regression coverage rather than
assuming target-library behavior is transactionally reversible.

### 6. Keep common options small and semantic

Add an immutable `UiImageRequestOptions` value carried by `ImageNodeSpec` and normalized into
`UiImageRequest`. The first version contains only:

- decode size: target bounds (default), original size, or explicit positive `UiDp` bounds resolved
  with the captured renderer density;
- memory cache policy: `Default` or `Disabled`;
- disk cache policy: `Default` or `Disabled`;
- transition: `Default`, `None`, or crossfade with a validated non-negative duration;
- an ordered immutable list of `UiImageRequestExtension` values.

`UiImageRequest` also receives the already-declared `ImageContentScale` so an adapter can select
the appropriate decode/transform behavior instead of observing only the final `ImageView` state.

Do not model Glide `DiskCacheStrategy`, priority, thumbnail/error request chains, Coil `Extras`,
decoder internals, hardware bitmap flags, network clients, authentication, or every transformation
as core fields.

`UiImageRequestExtension` is the typed escape seam for future adapter-owned immutable options. Its
identity is the concrete runtime type plus `stableKey`; it must not contain an unlocked mutable
builder or an unkeyed function capture. Adapters consume extension types they own and ignore every
other type. This avoids a string namespace registry and lets a future adapter use exhaustive type
checks without creating a second dynamic property system.

The initial Coil and Glide adapters do not need to publish an adapter-specific extension just to
complete this plan. Their application-wide networking, decoding, and cache configuration remains
on the injected Coil `ImageLoader` or Glide `AppGlideModule`. Add adapter-specific request options
later only for a demonstrated use case.

### 7. Display effects remain renderer semantics

`contentScale`, tint, alpha, shape clipping, and other visual modifiers remain renderer/UI
semantics. Do not duplicate all of them as image-library transformations.

Adapters receive `contentScale` and decode size so they can avoid decoding a needlessly large
image and can apply the loader's equivalent center-crop/fit behavior. Exact visual clipping and tint
remain the renderer's responsibility. A future cached transformation API must define cross-loader
pixel and cache-key semantics before entering the common contract.

Placeholder and error remain immediate `ImageSource.Resource?` request values in this cycle.
Fallback remains node state and is resolved before request creation when `source == null`. General
asynchronous fallback request chains differ materially between libraries and are deferred.

### 8. Optional module direction remains one-way

- `viewcompose-ui-contract` remains Kotlin/JVM and imports no Android, Coil, or Glide types.
- `viewcompose-widget-core` continues to depend only on contract types and never on the renderer.
- `viewcompose-renderer` owns Android target binding and request disposal but no Coil/Glide types.
- `viewcompose-image-coil` and `viewcompose-image-glide` are optional capabilities depending on the
  contract and renderer; no foundation module depends back on either adapter.
- absence of both adapter artifacts preserves direct Android resource loading.

## Target request flow

```text
Image / Icon / IconButton DSL
    -> ImageNodeSpec(source + loader + common request options)
    -> MediaViewBinder normalizes UiImageRequest
    -> ImageRequestBindingController replaces the target binding
        -> source == null: direct fallback
        -> loader installed: delegate Resource/Url/Uri/File/Model
        -> no loader + Resource: direct setImageResource
        -> no loader + other source: direct error/placeholder/fallback
    -> UiImageLoadHandle stored on ImageView
    -> patch replacement or mounted-node disposal calls dispose()
```

## Public API quality classification

Assign and review these levels before implementation:

| API family | Level | Required contract |
| --- | --- | --- |
| `ImageSource` variants | Q2; `Model` is Q3 | validation, equality, payload ownership, redacted diagnostics, stable-key sample |
| `UiImageRequest` and common option types | Q2/Q3 | units, defaults, option ordering, cache/transition meaning, compiled request sample |
| `UiImageLoader` | Q3 | main-thread start, async completion, strong failure guarantee, replacement and ownership sample |
| `UiImageLoadHandle` | Q3 | idempotent disposal, thread, completion/disposal behavior |
| target interfaces | Q2 | opacity, platform target ownership, accepted native object behavior |
| `Image`/`Icon` request option changes | Q3 | loader selection, fallback rules, display/decode distinction, compiled DSL sample |
| Coil and Glide adapter classes | Q3 | accepted sources/targets, lifecycle, cache ownership, cancellation, configuration, samples |

Every changed public/protected declaration must receive canonical-English KDoc in the same pull
request. No placeholder comments and no copied bilingual source comments are permitted.

## Ordered implementation

Complete one stage at a time. Keep at most one stage in progress, update this plan after validation,
and create a small commit for each independently passing stage. Do not start the next stage while a
required test from the current stage fails.

### Stage 0 — record the plan

- **Done:** Establish the current source, renderer, adapter, documentation, and release baseline.
- **Done:** Record locked decisions, test matrix, module onboarding, and completion criteria.
- **Done:** Run `./gradlew verifyDocumentationStructure` for the plan/index change.
- **Pending:** Commit the plan and index as one documentation-only step if the user requests commits.

Suggested commit: `docs: plan general image loading pipeline`

### Stage 1 — add the general contract beside the old contract

Owning module: `viewcompose-ui-contract`.

- **Done:** Add the new `UiImageLoader`, request, target, handle, source-option, decode-size, cache-policy,
  transition, and extension declarations under the existing `node/media` area.
- **Done:** Keep Android/AndroidX, Coil, and Glide imports out of production source.
- **Done:** Add constructor validation and deterministic equality/redacted string tests, especially for
  `ImageSource.Model`.
- **Done:** Add compiled Q3 samples showing a custom loader, handle disposal, local `Resource`, URI/file,
  and keyed `Model` requests.
- **Done:** Keep old declarations temporarily so all existing modules still compile at the end of this
  stage. Marking them deprecated is optional here because they are removed in Stage 4; do not
  implement a second long-lived bridge.
- **Done:** Add the pull request's immutable Changeset once the final artifact set is known; never edit
  the existing backfill Changeset.

Validation:

```bash
./gradlew :viewcompose-ui-contract:test
./gradlew auditViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-ui-contract
```

Suggested commit: `feat(ui-contract): add general image loading contracts`

### Stage 2 — add managed renderer request bindings

Owning module: `viewcompose-renderer`.

- **Done:** Add one image-request binding controller in the media binder area.
- **Done:** Add one `ids.xml` tag ID dedicated to the current image request binding.
- **Done:** Implement replace, clear, and dispose operations with idempotent handle disposal.
- **Done:** Wire mounted-node disposal so `Image` and `IconButton` targets are cleared even when removal
  occurs through subtree disposal, failed insertion cleanup, a lazy-item session, or root disposal.
- **Done:** Preserve renderer transaction behavior: a failed bind must not leave an unowned request and a
  rollback rebind may restart the previous request.
- **Done:** Add focused Robolectric tests before migrating production binding. Use a fake loader/handle;
  no network and no real Coil/Glide dependency belongs in renderer tests.

Required renderer tests:

- first request stores one handle;
- equivalent request does not restart;
- changed source/options/loader disposes once before starting the replacement;
- metadata-only description/tint changes do not restart;
- remote/general source to direct resource after loader removal clears stale work;
- any source to `null` clears stale work and displays fallback;
- synchronous loader failure leaves no stored handle;
- rollback can rebind the previous request;
- mounted-node and lazy-session disposal clear exactly once;
- repeated disposal is harmless.

Validation:

```bash
./gradlew :viewcompose-renderer:testDebugUnitTest
```

Suggested commit: `feat(renderer): manage image request replacement and disposal`

### Stage 3 — add and certify the general Coil adapter beside the old adapter

Owning module: `viewcompose-image-coil`.

- **Done:** Add `CoilImageLoaderAdapter` against the general protocol while the old
  `CoilRemoteImageLoader` remains temporarily available for existing consumers.
- **Done:** Accept only `PlatformUiImageTarget` values whose native target is an Android `ImageView`.
- **Done:** Map `Resource` to its resource ID, `Url` to its string, `Uri` through Android URI parsing,
  `File` to `java.io.File`, and `Model` to its retained raw value.
- **Done:** Normalize placeholder/error resources and common request options into one Coil
  `ImageRequest`.
- **Done:** Wrap Coil's returned disposable in `UiImageLoadHandle`; do not discard it.
- **Done:** Keep a supplied Coil `ImageLoader` caller-owned and never call `shutdown` from request
  disposal.
- **Done:** Remove the old `Context` convenience construction path so loader creation and shutdown have
  one explicit application owner.
- **Done:** Keep the existing Coil `3.2.0` version unless a separate compatibility finding is recorded.
- **Done:** Add pure source/option mapping tests plus a target replacement/disposal test with a
  deterministic fake Coil component or test loader.
- **Done:** Add a compiled general resource/URL request sample with explicit handle disposal; keep the old
  sample only until Stage 4 removes the old protocol.

Validation:

```bash
./gradlew :viewcompose-image-coil:testDebugUnitTest
./gradlew auditViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-image-coil
```

Suggested commit: `feat(image-coil): adopt general image requests`

### Stage 4 — migrate the vertical image slice and remove the remote protocol

Owning modules: `viewcompose-ui-contract`, `viewcompose-widget-core`, `viewcompose-renderer`, and
`viewcompose-image-coil`.

This stage is one focused atomic migration. The new contract, renderer binding controller, and
general Coil adapter already exist, so the branch must compile after the old path is removed.

- **Done:** Change `ImageNodeSpec`, `ImageNodeProps`, and `IconButtonNodeProps` to the general loader and
  request-options contracts.
- **Done:** Update `Image`, `Icon`, and `IconButton` DSL emission; add one `requestOptions` parameter rather
  than a long list of unrelated loader parameters.
- **Done:** Replace `ProvideRemoteImageLoader` with `ProvideImageLoader` and update `ImageLoading.current`.
- **Done:** Make `source` nullable and replace nullable remote URL fallback tests with `source = null`.
- **Done:** Route all non-null sources through the configured loader in `MediaViewBinder`, including
  `Resource`.
- **Done:** Keep the direct resource path only when no loader is installed.
- **Done:** Include source, common options, and loader identity in patch restart decisions.
- **Done:** Remove `CoilRemoteImageLoader`, the old `RemoteImage*` declarations, and every framework, app,
  test, and sample reference. Do not leave deprecated duplicate protocols in the final tree.
- **Done:** Add a repository search guard/test that rejects new production references to the removed
  protocol names.

Required widget/renderer assertions:

- scoped general loader reaches `Image`, `Icon`, and `IconButton` specs;
- `Resource` is delegated when a loader exists;
- `Resource` remains directly renderable without a loader;
- URL, URI, file, and model sources without a loader select the documented immediate error path;
- `null` selects fallback without invoking the loader;
- request-option changes produce an image patch/restart; identical options preserve the request.

Validation:

```bash
./gradlew :viewcompose-ui-contract:test
./gradlew :viewcompose-widget-core:testDebugUnitTest
./gradlew :viewcompose-renderer:testDebugUnitTest
./gradlew :viewcompose-image-coil:testDebugUnitTest
```

Suggested commit: `feat(image): route all sources through the general loader`

### Stage 5 — add the Glide adapter module

Initial module status: optional runtime capability, not yet published until the source freeze and
publication-onboarding step below.

- **Done:** Add `viewcompose-image-glide` with package root `com.viewcompose.image.glide`, Android namespace
  of the same value, `minSdk 24`, `compileSdk 36`, and Java 11 bytecode.
- **Done:** Pin Glide `5.0.7` in the version catalog for this implementation baseline. Do not add the
  annotation processor unless generated Glide APIs are actually used.
- **Done:** Depend only on `viewcompose-ui-contract`, `viewcompose-renderer`, and Glide.
- **Done:** Register the project in `settings.gradle.kts`, `modulePackageRoots`,
  `optionalCapabilityModules`, and the compile/test portions of `qaQuick`.
- **Done:** Add `GlideImageLoaderAdapter` using the target `ImageView` to obtain the lifecycle-associated
  Glide request manager. Application-wide customization remains in `AppGlideModule`.
- **Done:** Map Resource, URL, URI, File, and Model sources directly into Glide's model load.
- **Done:** Apply placeholder/error, decode size, cache-disable, transition, and content-scale
  semantics supported by the common request.
- **Done:** Return a handle that clears the exact target/request through Glide; clearing after success and
  repeated clearing must be harmless.
- **Done:** Add deterministic mapping, replacement, and disposal tests and a compiled Q3 sample.
- **Done:** Run dependency-boundary and package-root guards before proceeding.

Validation:

```bash
./gradlew :viewcompose-image-glide:testDebugUnitTest
./gradlew verifyModulePackageRoots verifyAndroidModuleNamespaces verifyModuleDependencyBoundaries
```

Suggested commit: `feat(image-glide): add optional Glide adapter`

### Stage 6 — add Demo and lifecycle regression evidence

- **Done:** Add one deterministic Demo section that switches among a local resource, fallback, and a fake
  delayed model while a loader is installed.
- **Done:** Demonstrate target-size/crop behavior for a local resource without a network dependency.
- **Done:** Add a rapid-rebind/recycled-list regression using a fake loader whose completions can be
  delivered out of order; assert the newest binding remains visible.
- **Done:** Add disposal coverage for page/session removal.
- **Done:** Do not make a live Internet request part of `qaQuick`, `qaPreview`, or `qaFull`.

Validation:

```bash
./gradlew :app:testDebugUnitTest
./gradlew qaPreview
./gradlew qaFull
```

If no device is available, record the exact missing device validation in this plan; do not report
the capability complete.

Suggested commit: `test(image): cover local loader reuse and stale request disposal`

### Stage 7 — synchronize public documentation and migration

- **Done:** Add a cross-module image loading guide under `docs/guides/` covering source types, loader
  installation, Resource delegation, fallback, common options, custom Model stable keys, request
  disposal, and Coil/Glide selection.
- **Done:** Link the guide from `docs/README.md` and add its reviewed Simplified Chinese mirror and
  translation-policy entry.
- **Done:** Update the English and Chinese architecture overviews so image loading is a general optional
  pipeline rather than a remote-only bridge.
- **Done:** Update the English and Chinese manuals for UI Contract, Widget Core, Renderer, and Image Coil.
- **Done:** Add the Image Glide module manual and Chinese mirror when the artifact enters the published
  catalog; do not create an unreachable public manual earlier.
- **Done:** Add a source/target-version migration page covering every removed/renamed symbol and the
  `Remote(null) -> source = null` change; link it from the migration index in both locales.
- **Done:** Update module compatibility notes and dependency examples with the independently released
  versions actually selected by release planning.
- **Done:** Recompute translation fingerprints only after reviewing the Chinese meaning.
- **Done:** Update the active architecture/API comments instead of copying historical archive claims.

Validation:

```bash
./gradlew verifyDocumentationStructure
cd website
npm run verify:translations
npm run typecheck
npm run build
```

Suggested commit: `docs(image): document the general loading pipeline and migration`

### Stage 8 — classify release intent and onboard the new artifact

The implementation pull request changes production source in existing published artifacts. Add one
new immutable Changeset classifying at least:

- `viewcompose-ui-contract`: `breaking`;
- `viewcompose-widget-core`: `breaking`;
- `viewcompose-renderer`: `feature`;
- `viewcompose-image-coil`: `breaking`.

Do not write reverse-dependency impact. The release planner derives it.

`viewcompose-image-glide` cannot receive a truthful immutable source revision before its source
commit exists. Therefore:

1. keep it classified as an optional but unpublished module while its source is being implemented;
2. freeze and review the complete source commit;
3. use the documented release-owner workflow to register its first `0.1.0-alpha01` publication,
   strict Dokka status, module catalog row, English/Chinese manual, translation policy, and immutable
   documentation-history entry;
4. never invent a source revision, point the new artifact at a commit where its source did not
   exist, or hand-edit an already recorded release entry.

If the release tooling cannot onboard a new artifact from a frozen commit without manual metadata,
stop and update the publishing workflow/tooling in a separately reviewed step. Do not weaken the
publishing verifier.

Validation:

```bash
./gradlew verifyViewComposeReleaseIntent
./gradlew verifyViewComposePublishingConfiguration
./gradlew planViewComposeRelease
```

Suggested commit after the source freeze: `build: onboard viewcompose image glide publication`

### Stage 9 — final certification and archival

- **Done:** Run all focused module tests again.
- **Done:** Run selected strict API documentation audits for every changed published artifact.
- **Done:** Run the strict API documentation audit for the new Glide artifact after onboarding.
- **Done:** Run `qaQuick`.
- **Done:** Run `qaPreview` and `qaFull`; record any verified baseline or device-only limitation.
- **Done:** Confirm the worktree contains no old `RemoteImage*` production references and no unkeyed raw
  stream/byte-array source path.
- **Done:** Confirm direct resource loading works with no optional adapter installed.
- **Done:** Confirm the final Changeset covers every automatically detected artifact.
- **Done:** Update the plan status, validation evidence, last-verified date, and next action.
- **Pending:** Move this file to `docs/archive/`, update the archive index, and restore
  `docs/project/plans/README.md` to its no-active-plan state only after every completion criterion is
  satisfied.

Final validation:

```bash
./gradlew qaQuick
./gradlew qaPreview
./gradlew qaFull
./gradlew auditViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-ui-contract,viewcompose-widget-core,viewcompose-renderer,viewcompose-image-coil,viewcompose-image-glide
./gradlew verifyAssembledViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-ui-contract,viewcompose-widget-core,viewcompose-renderer,viewcompose-image-coil,viewcompose-image-glide
```

## Test matrix

| Scenario | Contract | Widget | Renderer | Coil | Glide | Device/Demo |
| --- | --- | --- | --- | --- | --- | --- |
| Resource without loader | source validation | spec | direct resource | n/a | n/a | visible |
| Resource with loader | request | local capture | delegated/replaced | resource mapping | resource mapping | crop/downsample |
| URL | validation | emission | delegated | string data | string model | optional manual |
| content/file/data URI | validation | emission | delegated | URI mapping | URI mapping | picker/manual |
| File | equality | emission | delegated | File data | File model | local file manual |
| keyed Model | redaction/key | emission | delegated | raw model | raw model | fake delayed model |
| null source | n/a | nullable DSL | fallback/no load | no call | no call | visible fallback |
| unsupported source/no loader | n/a | emission | error path | n/a | n/a | visible error |
| request option change | equality | spec change | restart once | mapping | mapping | visible size/crop |
| metadata-only change | n/a | spec change | no restart | no extra call | no extra call | n/a |
| rapid target reuse | handle | n/a | latest wins | dispose old | clear old | fake out-of-order |
| node/session disposal | handle | n/a | dispose once | disposable | clear target | page removal |
| synchronous start failure | failure contract | n/a | no orphan/rollback | strong guarantee | strong guarantee | n/a |

## Completion criteria

The plan is complete only when:

1. no production API or implementation retains the remote-only image protocol;
2. `ImageSource` supports Resource, URL, URI, File, and stable-key Model inputs;
3. an installed loader receives local resources as well as remote/custom sources;
4. direct Resource rendering still works without Coil or Glide on the classpath;
5. renderer-owned handles are disposed on replacement, rollback cleanup, lazy reuse, and node/session
   disposal;
6. Coil and Glide pass the same common request and lifecycle contract matrix;
7. local resources demonstrate loader-backed target-size/crop/downsample behavior;
8. public APIs meet their assigned Q2/Q3 level with compiled samples;
9. active English documentation and reviewed Chinese mirrors describe the final behavior;
10. release intent is classified and the Glide artifact is onboarded only from a truthful frozen
    source revision;
11. `qaQuick`, `qaPreview`, `qaFull`, strict API docs, documentation structure, translations, and the
    production site build pass, or an allowed device-only limitation is explicitly recorded;
12. the durable conclusions are in active documentation and this completed plan is archived.

## Low-context implementation protocol

For a lower-reasoning or token-constrained implementation session:

1. read `AGENTS.md`, `docs/README.md`, this plan, and only the owning module manual for the current
   stage;
2. inspect the current diff and plan checkbox before editing;
3. implement exactly one stage; do not redesign later stages pre-emptively;
4. run the stage's focused validation before broader gates;
5. update the checkbox, execution log, and any discovered blocker in this file;
6. commit only when the stage is independently passing and the user requested commits;
7. stop rather than invent semantics when a locked decision conflicts with implementation evidence.

Avoid asking a lower-context model to implement Stages 1 through 9 in one prompt. The renderer
lifecycle stage and public-contract migration deserve separate turns even when the plan is present.

## Execution log

| Date | Stage | Commit | Validation | Notes |
| --- | --- | --- | --- | --- |
| 2026-08-04 | 0 | Uncommitted | `verifyDocumentationStructure` passed | Plan created; no production code changed. |
| 2026-08-04 | 1–4 | Uncommitted | UI contract, Widget, Renderer, and Coil focused tests; strict API audits passed | General `UiImage*` contract, renderer-owned binding/disposal, Coil adapter, vertical migration, and legacy-reference guard implemented. |
| 2026-08-04 | 5–6 | Uncommitted | Glide tests; package/namespace/dependency-boundary checks; app debug compilation; out-of-order renderer regression | Optional unpublished Glide adapter and deterministic local/fake-loader Demo section implemented. |
| 2026-08-04 | 7–8 | Uncommitted | `verifyDocumentationStructure`, `verifyMigrationPairedSamples`, language/translation checks, `verifyViewComposeReleaseIntent`, strict API audits | English/Chinese guide, module manuals, architecture, migration page, and immutable release intent added. |
| 2026-08-05 | 1–9 review | Uncommitted | Focused contract/widget/renderer/Coil/Glide tests, app debug compilation, strict published-module API audits, translation/type checks, release/documentation/module guards, and `qaQuick` passed | Removed the dead request fallback and unsafe Coil `Context` constructor; changed explicit decode bounds to `UiDp` resolved with the captured node density; made extension identity explicit; fixed equivalent rebinds, loader reference identity across composition and rendering, Glide default behavior, lazy-session disposal coverage, and image self-clipping inside overflow-permitting layout hosts. The media Demo was regrouped for narrow-screen manual verification. |
| 2026-08-05 | 8 publication | `79a78900` source freeze | Publication verification pending | Registered `viewcompose-image-glide:0.1.0-alpha01` from the frozen implementation revision and added its catalog entry, strict API-doc status, bilingual module manual, and immutable documentation history. |
| 2026-08-05 | 8 publication | `79a78900` source freeze | Publishing configuration, release intent, five-module strict API audit, assembled API verification, and complete-history API assembly passed | Amended the unpushed source freeze to include the immutable Glide module manual, then updated publication history to the resulting truthful revision. |
| 2026-08-05 | 9 certification | Pending metadata commit | `qaFull` reached connected tests after all local `qaQuick` work; `qaPreview` and the Counter connected test recorded external limitations | `qaPreview` reproduces the identical `input-text-fields` golden mismatch on base commit `1848f0d2`; no baseline image was changed in this image-loading PR. The physical Android 13 device was in Doze with the keyguard showing, so Counter instrumentation could not launch a resumed Activity. |
| 2026-08-05 | 9 certification | Metadata commit | Clean-tree `qaQuick` passed; `planViewComposeRelease` reproduced a base-revision tag limitation | Release planning stops on both this branch and base revision `1848f0d2` because the existing `maven/viewcompose-animation/0.1.0-alpha02` tag does not declare `sourceRevision=<full SHA>`. This PR does not rewrite immutable release tags. |

## Blockers

No image-loading implementation blocker remains. Two pre-existing or environmental certification
limitations are recorded without broadening this pull request:

- `qaPreview` fails on `input-text-fields` with the same expected/actual image at both this branch
  and base revision `1848f0d2`; the unrelated golden is intentionally unchanged.
- `qaFull` passes its complete local `qaQuick` dependency graph, then the connected Counter test
  cannot launch an Activity while the attached Android 13 device is in Doze with keyguard showing.
- `planViewComposeRelease` fails identically on this branch and base revision `1848f0d2` because the
  existing `maven/viewcompose-animation/0.1.0-alpha02` tag lacks its required `sourceRevision`
  declaration; immutable release-tag repair is outside this image-loading change.

The production website build now passes after replacing this active plan's inaccessible Markdown
task controls with non-interactive status text.

## Last verified

2026-08-05: focused module tests, app compilation, `qaQuick` (634 tasks), documentation
structure/language/translation checks, website type checking, migration sample pairing, module
boundary checks, publishing configuration, release intent, five-module strict API audits, assembled
API verification, complete-history API assembly, and the production website build passed. Fixed
`UiDp` decode bounds use the
renderer-captured density for Coil and Glide. `qaPreview` reproduced the repository baseline's
`input-text-fields` golden mismatch exactly at revision `1848f0d2`. `qaFull` completed its local gate
and reached connected tests, where the attached Android 13 phone was in Doze behind keyguard and the
Counter Activity could not enter `RESUMED`. Earlier physical validation confirmed the regrouped
media Demo, all three replacement states, remote/fallback presentation, and drawable clipping before
the decode-unit API adjustment; the rebuilt Demo remains for user verification. Clean-tree
`qaQuick` passed after both commits. `planViewComposeRelease` reproduces the same missing
`sourceRevision` failure for an existing release tag at base revision `1848f0d2`.

## Next action

Submit the Draft pull request. Resolve the unrelated Preview golden, repair the legacy release-tag
metadata through the release-owner workflow, and rerun device instrumentation with an unlocked
device before archiving this plan.
