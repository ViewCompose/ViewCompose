# Third-Party Android View Integrations Plan

## Status

Active. Phases 0 through 2 are complete. The common typed adapter, construction identity,
cross-key-only reset hard cut, callback delegation, environment scopes, atomic candidate
replacement, rollback, bounded diagnostics, lifecycle-owner serialization, and committed SDK
saved-state boundary are implemented and verified. No target SDK module has started. The target
integrations are AndroidX Media3, legacy
`com.google.android.exoplayer2` ExoPlayer, Google Maps SDK for Android, and CameraX. Media3 and
legacy ExoPlayer remain separate compatibility lines because their public namespaces, dependency
graphs, support status, and consumer migration constraints are not interchangeable.

This plan is canonical English-only under the documentation-governance policy. Every durable API,
theme, lifecycle, saved-state, and ownership contract must move into active architecture, guide,
migration, and owning-module documentation before this plan is archived.

Last verified: 2026-08-24.

Next action: begin Phase 3 with the independent AndroidX Media3 module and deterministic local-media
fixture.

## Maven release changesets

- `release/changes/20260824-typed-android-view-adapter.json`
- `release/changes/20260824-android-view-lifecycle-saved-state.json`

## Objective

Make complex Android and third-party SDK Views first-class, transaction-aware ViewCompose
integrations without teaching Android Renderer about SDK identities. The completed work must:

1. add a typed reusable Android View adapter contract with explicit logical identity,
   construction-sensitive identity, replay-safe state binding, commit publication, and one-shot
   cleanup;
2. coordinate Android resource/configuration changes without claiming that arbitrary SDK Views can
   be restyled automatically;
3. bind SDK View activity to the nearest ViewCompose `LifecycleOwner` and saveable-state boundary
   where the SDK exposes a compatible contract;
4. ship independently removable integrations for AndroidX Media3, legacy ExoPlayer, Google Maps,
   and CameraX;
5. keep player, map, camera-provider, API-key, permission, network, and application policy ownership
   explicit; and
6. validate rollback, retained navigation, lazy reuse, configuration changes, process recreation,
   accessibility, input, release, and leak behavior with deterministic fixtures and scoped device
   evidence.

## Target integration matrix

The artifact and package names below are the Phase 0 frozen baseline. They were checked against the
publication catalog, dependency policy, and existing namespace layout before production source.

| Integration line | Target SDK surface | Proposed artifact | Proposed package root | First-release ownership |
| --- | --- | --- | --- | --- |
| AndroidX Media3 | `androidx.media3.ui.PlayerView`, the Media3 `Player` contract, and Media3 ExoPlayer implementations supplied by callers | `viewcompose-media3-androidx` | `com.viewcompose.media3` | Caller owns the `Player`; the integration owns only View attachment, controller appearance, lifecycle policy, and listener cleanup |
| Legacy ExoPlayer | `com.google.android.exoplayer2.ui.StyledPlayerView` and legacy `Player`/`ExoPlayer` instances | `viewcompose-exoplayer2-android` | `com.viewcompose.exoplayer2` | Caller owns the player; the integration is an explicitly legacy, independently removable compatibility line |
| Google Maps | `com.google.android.gms.maps.MapView`, map-ready delivery, camera/UI state, and SDK-owned saved state | `viewcompose-google-maps-android` | `com.viewcompose.maps.google` | Integration owns the `MapView` lifecycle; the application owns credentials, map policy, remote style/data, and business state |
| CameraX | `androidx.camera.view.PreviewView`, preview surface coordination, camera selection, and lifecycle-bound use cases | `viewcompose-camerax-androidx` | `com.viewcompose.camerax` | Application owns permissions and use-case policy; ownership of provider/use cases is explicit and never inferred from a View reference |

The Media3 line may accept an `androidx.media3.exoplayer.ExoPlayer` through the Media3 `Player`
contract. The separate ExoPlayer 2 line exists only for consumers whose source still uses the
legacy `com.google.android.exoplayer2` namespace; it must not blur the two dependency families or
silently adapt one into the other.

## Frozen dependency and publication baseline

All four integration artifacts use the existing `google()` repository. Repository probes on
2026-08-24 resolved every POM below from Google Maven. The legacy ExoPlayer core and UI POMs were
not present on Maven Central, so removing `google()` or claiming Maven-Central-only resolution is
outside the support contract.

| Integration | Frozen SDK line | Integration dependency exposure | Demo/sample-only dependency | Reason |
| --- | --- | --- | --- | --- |
| AndroidX Media3 | `androidx.media3:media3-*:1.11.0` stable | `api(media3-common)` for public `Player`; `implementation(media3-ui)` for `PlayerView` | `media3-exoplayer` constructs the caller-owned local-fixture player | Keeps the player contract public without making one player implementation part of the integration API |
| Legacy ExoPlayer 2 | `com.google.android.exoplayer:exoplayer-*:2.19.1` final | `api(exoplayer-core)` for legacy `Player`; `implementation(exoplayer-ui)` for `StyledPlayerView` | None beyond the same frozen line | Publishes an explicit frozen compatibility artifact for the discontinued namespace without aliasing Media3 |
| Google Maps | `com.google.android.gms:play-services-maps:20.0.0` | `api(play-services-maps)` because `GoogleMap`, camera, and geometry types are valid integration API | Credentialed smoke configuration only; no repository key plugin | One fixed current major avoids dynamic resolution and exposes the SDK types intentionally used by map callbacks and state |
| CameraX | `androidx.camera:camera-*:1.6.1` stable | `api(camera-core)` and `api(camera-lifecycle)` for caller-owned `Preview`, selector, and provider types; `implementation(camera-view)` for `PreviewView` | `camera-camera2` supplies the Demo/device backend | The integration coordinates a caller-supplied provider and dedicated Preview use case without selecting the application's CameraX backend |

The four artifact IDs and package roots in the target matrix are final for first implementation.
Each starts at `0.1.0-alpha01`, enters the strict API-documentation registry, module catalog,
dependency-contract registry, and unpublished-module registry independently, and owns a separate
consumer fixture and immutable Changeset. None is added to `viewcompose-android`,
`viewcompose-material3-android`, or another aggregate. Installing one integration must not install
another target SDK.

The common Phase 1 API remains in `viewcompose-host-android` under
`com.viewcompose.host.android`. The SDK modules use `api(viewcompose-host-android)` as their
supported Android interop entry point. Media, Maps, and CameraX lifecycle coordination additionally
uses the existing `viewcompose-lifecycle-androidx` integration; SDK identities still cannot enter
that module or lower layers.

The frozen upstream constraints are:

- Media3 1.11.0 and CameraX 1.6.1 require at least API 23; the ViewCompose modules retain the
  repository-wide `minSdk 24`, `compileSdk 36`, and Java 11 contract.
- ExoPlayer 2.19.1 is the final planned legacy release and is deprecated upstream. The ViewCompose
  artifact receives compatibility fixes but never promises a newer legacy SDK line; migration to
  the separate Media3 artifact is the supported long-term path.
- Maps 20.0.0 requires API 23. The first integration never selects the deprecated legacy renderer.
  It records the upstream Android 12+ `IncorrectContextUseViolation` as an SDK limitation and does
  not globally weaken or replace an application's StrictMode policy. The credentialed API 31+
  lane reports that upstream violation separately from ViewCompose failures.
- All versions are exact. Dynamic versions, rich ranges, dependency substitution between Media3
  and legacy ExoPlayer, and classpath-selected adapters are forbidden.

The reviewed upstream contract sources are the official
[Media3 release notes](https://developer.android.com/jetpack/androidx/releases/media3),
[Media3 PlayerView reference](https://developer.android.com/reference/androidx/media3/ui/PlayerView),
[Media3 Surface guidance](https://developer.android.com/media/media3/ui/surface),
[legacy migration guide](https://developer.android.com/media/media3/exoplayer/migration-guide),
[legacy ExoPlayer releases](https://github.com/google/ExoPlayer/releases),
[Maps release notes](https://developers.google.com/maps/documentation/android-sdk/release-notes),
[MapView lifecycle reference](https://developers.google.com/android/reference/com/google/android/gms/maps/MapView),
[CameraX release notes](https://developer.android.com/jetpack/androidx/releases/camera), and
[CameraX architecture guide](https://developer.android.com/media/camera/camerax/architecture).
Future version changes reopen Phase 0 decisions in a new attributed Changeset; they are not routine
dependency-bot merges.

## Frozen support and validation matrix

| Lane | Common adapter | Media3 / legacy ExoPlayer | Google Maps | CameraX |
| --- | --- | --- | --- | --- |
| JVM and deterministic fixture | Transaction order, replacement, rollback, reset, diagnostics, and leak ownership | Fake player/listener plus local-file state transitions | Fake lifecycle/map-ready port; no credentials or renderer claim | Fake provider/binding lease; no camera-frame claim |
| API 24 minimum lane | Host, lazy reuse, configuration, focus/input, and release | Inflate/configure/detach without remote media | Credential-free unsupported/availability result | Permission-denied and unavailable-provider result |
| Xiaomi MI 6 / API 28 physical lane | Real View lifecycle and leak checks | Repository-owned local clip, first frame, replacement, pause/resume surface, and cleanup | Optional credentialed smoke when a key is supplied externally | Permission, front/back lens, rotation, foreground/background, hidden destination, and final unbind |
| API 31+ Google Play lane | StrictMode and modern Surface behavior | SurfaceView/TextureView replacement and navigation retention | Required credentialed renderer smoke plus separately attributed upstream StrictMode evidence | Permission and lifecycle behavior; virtual-camera evidence is never accepted as physical-camera parity |
| API 36 latest lane | Compile, host, configuration, accessibility, and release regression | Controller/accessibility and Surface regression | Compile/lifecycle/state regression; credentialed when configured | Compile/permission/configuration regression |
| Static Preview | Bounded adapter diagnostic | Deterministic placeholder | No-key placeholder | No-device placeholder |

API 24, 31+, and 36 lanes may use managed devices where their behavior is deterministic. Codec,
Google renderer, and physical-camera claims require the corresponding real capability. Phase 9
cannot close from the Xiaomi API 28 device alone: it also requires one API 31+ Google Play target
for Maps and one API 31+ physical camera target. A missing target is recorded as unexecuted, not
converted into a pass or silently removed from the support matrix.

The matrix is pairwise for locale, Light/Dark, LTR/RTL, font scale, and density, with targeted
single-variable cases for constructor replacement and process restoration. It is not an
unbounded Cartesian product. Every accepted device or performance batch records build identity,
SDK line, OS/API, hardware capability, absolute result, conclusion, limitation, and next action in
the owning active document.

## License, notice, and deterministic-fixture baseline

- Media3 and legacy ExoPlayer POMs declare Apache License 2.0. CameraX 1.6.1 POMs declare Apache
  License 2.0 and BSD-3-Clause. Maps 20.0.0 declares the Android SDK License and remains subject to
  Google Maps Platform terms and credential/billing policy.
- No SDK source, native binary, map data, codec binary, API key, or third-party media is vendored.
  Before the first SDK dependency lands, the owning pull request creates or updates the allowed
  root `THIRD_PARTY_NOTICES.md` with the exact direct coordinates, license identifiers, and stable
  upstream terms links. Each module manual repeats its consumer-relevant terms and support limits.
- The media fixture is a repository-produced, two-second color/motion clip with silence, stored
  locally with generation command, ownership statement, codec/container metadata, and SHA-256.
  Tests never fetch a stream. Advertisements, DRM, downloads, casting, and background services are
  not part of the first release.
- Maps uses fake contract ports for ordinary CI and an externally supplied key only for the scoped
  device smoke. CameraX uses a fake provider/binding port for ordinary CI and explicitly requested
  runtime permission for physical evidence. A missing key, Play services, codec, permission, or
  camera produces a structured unsupported/unexecuted result rather than a false renderer pass.

## Scope

### Common Android View adapter foundation

The common foundation adds high-risk Q3 API in `viewcompose-host-android` for:

- a typed `AndroidViewAdapter<V, S>` contract;
- immutable `UiEnvironmentValues` visible to adapter creation, replay-safe update, reset, and
  commit scopes, while creation also receives the renderer-owned Android `Context`;
- a separate `constructionKey` whose change creates a new View without changing application
  content identity;
- an explicit reuse policy and structured reset reason; reset is reserved for cross-logical-key
  mounted-tree reuse and is never part of an ordinary same-identity update;
- commit and release callbacks with the existing renderer transaction guarantees;
- bounded adapter diagnostics that identify adapter class, construction generation, reuse policy,
  lifecycle binding, and fallback without retaining the native View; and
- typed delegation from the existing callback-based `AndroidView` API, whose `onReset` semantics
  are hard-cut to the same cross-key-only contract rather than preserved as a second behavior.

The Phase 1 target shape is frozen as follows. Scope constructors remain internal; their public
properties and the adapter callbacks are Android-main-thread-only.

```kotlin
enum class AndroidViewReusePolicy {
    Never,
    Resettable,
}

enum class AndroidViewResetReason {
    MountedTreeReuse,
}

enum class AndroidViewLifecycleMode {
    None,
    AdapterManaged,
}

interface AndroidViewAdapter<V : View, S> {
    val reusePolicy: AndroidViewReusePolicy
        get() = AndroidViewReusePolicy.Never
    val lifecycleMode: AndroidViewLifecycleMode
        get() = AndroidViewLifecycleMode.None

    fun create(scope: AndroidViewCreateScope): V
    fun update(scope: AndroidViewUpdateScope<V>, state: S)
    fun onReset(scope: AndroidViewResetScope<V>, reason: AndroidViewResetReason) = Unit
    fun onCommit(scope: AndroidViewCommitScope<V>, state: S) = Unit
    fun onRelease(view: V) = Unit
}

fun <V : View, S> UiTreeBuilder.AndroidView(
    adapter: AndroidViewAdapter<V, S>,
    state: S,
    key: Any? = null,
    constructionKey: Any? = Unit,
    modifier: Modifier = Modifier,
)
```

`key` owns logical content identity. `constructionKey` owns constructor-sensitive View identity.
The renderer's actual construction identity is the adapter implementation class plus
`constructionKey`; recreating an equivalent adapter object during composition does not replace the
View, while changing adapter family always does. Runtime-restylable values belong in immutable
adapter state. Context wrappers, SDK options, surface implementation, or styles read only during
construction belong in `constructionKey`. A resource revision must never force recreation by
default when replay-safe update is sufficient. Both keys must have stable equality and hash
behavior for the lifetime of the VNode; mutating a key object in place is unsupported.

`AndroidViewCreateScope` exposes the renderer-supplied `Context` and current immutable
`UiEnvironmentValues`. Update, reset, and commit scopes expose the typed View and the current
environment snapshot. They do not expose a mutable transaction, renderer, session, application
scope, or SDK registry. Adapter state remains caller-owned; ViewCompose retains only the state
captured by the current and rollback VNodes and never clones arbitrary mutable state.

The exact reconciliation contract is:

1. Same logical key, adapter class, and construction key reuse the View. `update` replaces complete
   replay-safe configuration. `onReset` is not invoked.
2. A later same-identity update failure replays the previously committed adapter and state through
   `update`; it does not guess how to undo hidden SDK state.
3. A changed adapter class or construction key creates and updates a candidate View without
   releasing the committed View. Candidate failure releases only the candidate and preserves the
   previous View. Success structurally commits the candidate and releases the displaced View
   exactly once. The candidate's `onCommit` remains a later composition-commit effect; native
   resource release cannot depend on whether a low-level Renderer caller executes returned effects.
4. Cross-logical-key mounted-tree reuse is allowed only when `reusePolicy` is `Resettable`.
   `onReset(..., MountedTreeReuse)` runs exactly once before the next key's `update`. `Never`
   prevents the containing mounted tree from crossing keys.
5. `onCommit` runs at most once for each successful insert or rebind, never for a skipped or
   rolled-back binding, and may run again after later successful state. Implementations therefore
   perform serial transition or publication work rather than assuming a one-call lifetime.
6. `onRelease` runs exactly once for every created View after candidate rollback, committed
   replacement/removal, reuse-cache eviction, or session disposal. It releases only resources
   explicitly owned by the adapter.

The callback-based overload remains a supported low-level escape hatch. Phase 1 appends a named
`constructionKey` parameter without shifting the existing positional parameters and implements the
overload with one internal typed adapter. There is no deprecated forwarding facade and no second
renderer path.

### Lifecycle and saved state

Phase 2 adds the reusable owner coordination boundary to `viewcompose-lifecycle-androidx` for:

- registering a lifecycle observer only after the Android View transaction commits;
- catching up to the nearest current `LifecycleOwner` state in Android lifecycle order;
- serial owner replacement without overlapping active observers;
- unregistering before final View cleanup;
- keeping retained navigation destinations capped by their destination owner rather than the
  Activity owner;
- restoring SDK state before first visible use and saving from the committed View only; and
- isolating corrupt or incompatible SDK state without corrupting the surrounding ViewCompose
  saveable registry.

The common Host contract does not depend upward on AndroidX Lifecycle or an individual SDK. Its
`lifecycleMode` is diagnostic metadata only. The reusable lifecycle adapter, saved-state-owner
local, and provider binding live in the AndroidX integration layer. SDK-specific event mapping and
Bundle payload schemas remain with each SDK integration.

The frozen Phase 2 shape is:

```kotlin
abstract class LifecycleAndroidViewAdapter<V : View, S> : AndroidViewAdapter<V, S> {
    final override val lifecycleMode: AndroidViewLifecycleMode
        get() = AndroidViewLifecycleMode.AdapterManaged

    protected abstract fun lifecycleOwner(state: S): LifecycleOwner
    protected open fun onViewCommit(scope: AndroidViewCommitScope<V>, state: S) = Unit
    protected abstract fun onLifecycleEvent(
        scope: AndroidViewLifecycleEventScope<V>,
        state: S,
        event: Lifecycle.Event,
    )
    protected open fun onViewReset(
        scope: AndroidViewResetScope<V>,
        reason: AndroidViewResetReason,
    ) = Unit
    protected open fun onViewRelease(view: V) = Unit
}

object LocalSavedStateRegistryOwner {
    val current: SavedStateRegistryOwner?
}

fun UiTreeBuilder.ProvideSavedStateRegistryOwner(
    owner: SavedStateRegistryOwner,
    content: UiTreeBuilder.() -> Unit,
)

sealed interface AndroidViewSavedStateBindResult {
    class Initial internal constructor(
        val restoredState: Bundle?,
    ) : AndroidViewSavedStateBindResult

    data object Retained : AndroidViewSavedStateBindResult
}

class AndroidViewSaveStateScope<V : View> internal constructor(
    val view: V,
)

fun <V : View> AndroidViewCommitScope<V>.bindAndroidViewSavedState(
    owner: SavedStateRegistryOwner,
    key: String,
    formatVersion: Int,
    saveState: AndroidViewSaveStateScope<V>.() -> Bundle,
): AndroidViewSavedStateBindResult

fun View.clearAndroidViewSavedStateBinding()
```

Activity, Fragment, navigation destination/graph, and Preview hosts install the nearest saved-state
owner automatically. Fragment content intentionally uses its View owner for lifecycle and the
Fragment for saved state. Binding occurs only after commit; the first bind returns one compatible
defensive restored Bundle, later commits replace only the saver, and format mismatch or corrupt
nested state is isolated. Owner replacement installs the new provider before removing the old.
Lifecycle adapter reset/release automatically remove both lifecycle and saved-state bindings before
the SDK hook runs. Raw adapters use the explicit clear function.

The first-release ownership is frozen as follows:

| Integration | Commit/start behavior | Hidden, replaced, and released behavior | Saved-state owner |
| --- | --- | --- | --- |
| Media3 and legacy ExoPlayer | Configure the View replay-safely, then attach the caller-owned player only after commit and while the nearest destination owner is at least started | Detach the player, controller listeners, and Surface relationship when the destination stops, owner changes, reset occurs, or the View is released; never call `play`, `pause`, `stop`, or `release` on the player | Caller owns playlist, position, playback, service/session, and process restoration; the integration saves no player state |
| Google Maps | After commit, call `MapView.onCreate(restoredBundle)`, register the map-ready generation, then catch up through `onStart`/`onResume` in Android order | Serialize owner replacement; forward pause/stop/destroy in reverse order, unregister low-memory forwarding before final cleanup, and ignore late map-ready callbacks from an obsolete generation | Integration owns one versioned SDK Bundle under an explicit stable `saveableStateKey`; absent key means no process restoration, and corrupt/incompatible state is dropped without affecting surrounding saveable state |
| CameraX | After commit, set the dedicated caller-owned `Preview` surface provider and bind that exact Preview with the caller-owned provider/selector to the nearest owner | Lifecycle state controls camera activity; owner replacement, reset, release, or failure unbinds only the exact Preview owned by this component and clears its surface provider; `unbindAll` is forbidden | Caller owns permission, provider/backend, selector, Preview configuration, other use cases, and restoration; the integration saves no camera or permission state |

Owner catch-up and replacement are serial on the main thread. A destroyed owner cannot be bound.
Retained destinations use their capped destination owner, not the Activity owner. Player
attachment, `MapView.onCreate`, map-ready publication, and CameraX binding are commit work because
performing them during replay-safe update would publish a rolled-back candidate. Commit failure is
reported as a post-commit integration failure and triggers bounded cleanup; it cannot pretend the
whole Android View transaction was never committed.

### Theme and configuration coordination

The completed integrations must distinguish three cases:

1. **Replay-safe runtime update:** resolved colors, drawables, controller visibility, scale type,
   map style, layout direction, or other supported properties are included in adapter state and
   reapplied after resource revision changes.
2. **Constructor-sensitive change:** a stable construction input changes and therefore advances
   `constructionKey`, permanently releases the old View, and creates the replacement from the
   newly resolved root Context.
3. **Unsupported restyling:** the SDK does not expose a safe update or reconstruction contract. The
   integration preserves behavior, reports the limitation, and does not pretend that a token-only
   update reached hidden SDK state.

SDK-neutral Host and Renderer APIs expose Android environment facts only. Material 3, One UI, or
application theme adapters resolve named policy into integration-specific immutable appearance
state before crossing the adapter boundary.

### Deterministic samples and Demo coverage

Each integration owns one directly launchable Demo scenario with stable automation roles and a
compiled public sample. Media samples use a repository-controlled local asset and no remote
stream, advertisement, DRM, download, cast, or account dependency. Google Maps tests separate
offline contract coverage from credentialed visual smoke evidence and never store an API key in
the repository. CameraX tests separate fake/provider contract coverage from physical-camera
evidence and never assume permission is already granted.

## Non-goals

This plan does not:

- add Media3, legacy ExoPlayer, Maps, or CameraX branches to `viewcompose-ui-contract`, Android
  Renderer, or the neutral host runtime;
- publish one universal third-party SDK plugin registry or use classpath discovery to select an
  integration;
- make Material, One UI, or application tokens capable of automatically skinning an arbitrary
  third-party View;
- make ViewCompose own a caller-supplied player, camera provider, map business state, API key,
  runtime permission, network client, account, DRM session, or analytics pipeline;
- implement media playback, maps, camera capture, or codec behavior already owned by the selected
  SDK;
- provide a Fragment-in-render-tree or `AndroidViewBinding` compatibility layer;
- make Preview/Layoutlib emulate a real video decoder, Google map renderer, camera device, Surface,
  or protected content pipeline;
- bundle network credentials, restricted media, or third-party binary test assets whose
  redistribution terms are not verified; or
- retain a legacy ExoPlayer artifact through an undocumented alias once its explicit support line
  is retired.

## Phase 0 baseline and Phase 1 delta

The following Phase 0 baseline was verified from
`54151a09f082518c7e49146caf6853b24ffc54ba` on 2026-08-24. Items 2, 3, and 9 are retained as
historical inputs and are superseded by the Phase 1 delta immediately below:

1. The repository has no Media3, legacy ExoPlayer, Google Maps, or CameraX dependency declaration,
   source adapter, published artifact, Demo route, or module manual.
2. `UiTreeBuilder.AndroidView` accepts untyped `View` callbacks for factory, replay-safe update,
   reset, post-transaction commit, and one-shot release.
3. A single `key` currently represents AndroidView reconciliation identity; there is no separate
   public construction-sensitive identity.
4. `AndroidResourceEnvironment` supplies the resolved root Context and advances
   `Environment.resourceRevision` after Android configuration callbacks or an explicit
   `AndroidResourceRefreshController.refresh()` request.
5. Standard Activity, Fragment, navigation, and preview hosts provide a scoped
   `LocalLifecycleOwner`, but AndroidView has no reusable typed lifecycle adapter.
6. The renderer can replay framework-visible View configuration after failure but cannot clone or
   restore arbitrary hidden state inside a third-party View.
7. Lazy mounted-tree reuse crosses logical keys only when every contained AndroidView declares a
   replay-safe reset contract; final abandonment invokes release exactly once.
8. Direct `AndroidViewBinding` and Fragment-in-tree integration remain unsupported.
9. The observed-property patch path currently invokes `onReset` when an AndroidView spec changes
   under the same logical identity, even though the public Host manual defines reset as cross-key
   reuse preparation. Phase 1 treats this implementation/documentation conflict as a rejected
   foundation design and removes the same-identity reset call rather than documenting two meanings.
10. Repository and coordinate searches still find no target SDK declaration, adapter, Demo route,
    module manual, publication entry, or current Changeset. Google Maven already exists in the
    centralized repository policy, so Phase 0 requires no repository mutation.

Phase 1 now adds `AndroidViewAdapter<V, S>`, typed environment scopes, explicit reuse policy and
reset reason, and construction identity composed from adapter implementation class plus
`constructionKey`. The callback overload delegates the same path. Same-identity binding and
rollback no longer call reset. Construction changes create and bind a detached candidate, preserve
the committed View on failure, swap on structural commit, and release each abandoned View exactly
once. Renderer diagnostics report a bounded adapter name, construction generation, reuse policy,
and replacement flag.

## Locked architectural rules

1. SDK types appear only in their named integration artifact, compiled samples, Demo fixtures, and
   integration-specific tests.
2. The common adapter contract uses Android/ViewCompose types and resolved values, never Media3,
   Maps, CameraX, or legacy ExoPlayer types.
3. Adapter `update` replaces complete replay-safe View configuration and may be called again during
   rollback. `onReset` runs only for opted-in cross-logical-key mounted-tree reuse; normal update and
   rollback never call it.
4. Network calls, playback publication, camera binding, map listener publication, analytics, and
   other irreversible effects begin only after commit or in composition-scoped work triggered by
   committed state.
5. `onRelease` detaches listeners, surfaces, player references, map callbacks, and camera bindings
   exactly once. It releases the underlying SDK resource only when the public integration
   explicitly created and owns it.
6. Theme/configuration changes prefer replay-safe state update. Recreation is explicit through a
   stable construction key and cannot be inferred from SDK class names.
7. A hidden retained destination follows its destination lifecycle and cannot keep a player View
   or Surface attachment, map lifecycle/location work, or camera capture active merely because the
   Activity remains resumed. A caller-owned media player may continue background audio only through
   explicit application playback/service policy; the integration never pauses or releases it.
8. Preview and unit tests use deterministic stand-ins; missing hardware, credentials, Google Play
   services, codecs, or network connectivity yields a structured unsupported/skipped result rather
   than a false pass.
9. Each published artifact is independently removable and adds no recurring work when no matching
   integration component is mounted.
10. No new public contract is retained without its Q level, applicable contract fields,
    canonical-English KDoc, compiled Q3 sample, module manual, Chinese public-document mirrors, and
    immutable release Changeset.
11. Same-identity updates, construction replacement, and cross-key reuse are three distinct
    renderer operations. No SDK adapter may overload `key`, resource revision, or reset callbacks
    to simulate construction identity.
12. Maps 20.0.0 upstream StrictMode and legacy-renderer limitations remain attributed to Maps. The
    integration cannot modify process-global StrictMode, choose the legacy renderer, or label an
    upstream violation as a ViewCompose regression.

## Execution plan

| Phase | Status | Deliverable | Exit gate |
| --- | --- | --- | --- |
| 0. Dependency and contract freeze | Complete | Pinned reviewed SDK versions and repositories; froze module/package names, dependency exposure, supported API/device matrix, license/notice impact, ownership table, deterministic fixtures, lifecycle/saved-state behavior, and rollback strategy | Worktree, Google Maven, and official SDK contracts agree; no production/publication mutation was required |
| 1. Typed AndroidView adapter | Complete | Q3 typed adapter, environment scopes, separate construction identity, explicit reuse policy/reset reason, callback delegation, same-identity reset hard cut, diagnostics, and renderer-neutral tests | Factory/update/reset/commit/release ordering, rollback, replacement, keyed reuse, raw-overload parity, and zero-adapter inactive cost pass |
| 2. Lifecycle and saved-state coordination | Complete | Q3 AndroidX lifecycle adapter, independent saved-state-owner local, committed versioned SDK Bundle provider boundary, automatic host/navigation/Preview propagation, diagnostics, samples, and owning documentation | Owner catch-up/replacement, retained destination visibility, callback failure, corrupt restore, process recreation, and one-shot cleanup pass |
| 3. AndroidX Media3 | Not started | Optional Media3 module, caller-owned `Player` component, controller/appearance state, local-media Demo/sample, tests, docs, and release intent | Playback attachment/detachment, theme/configuration, navigation retention, Surface cleanup, accessibility, and leak gates pass |
| 4. Legacy ExoPlayer 2 | Not started | Separate legacy namespace module and sample with no Media3 dependency or type aliasing | Dependency isolation, caller ownership, lifecycle parity, theme/configuration, release cleanup, and migration guidance pass |
| 5. Google Maps | Not started | Optional Maps module with MapView lifecycle, map-ready state delivery, camera/UI binding, explicit style/recreation behavior, and saved state | No-key deterministic tests, credentialed smoke evidence, lifecycle/process restore, navigation retention, theme/style, and leak gates pass |
| 6. CameraX | Not started | Optional CameraX module with PreviewView configuration, lifecycle-bound preview coordination, explicit provider/use-case ownership, and physical-device path | Permission denial, fake-provider, foreground/background, destination visibility, rotation, front/back camera, Surface cleanup, and physical-camera gates pass |
| 7. Cross-integration configuration matrix | Not started | Shared Light/Dark, locale, RTL, density, font-scale, multi-window/configuration, navigation, lazy reuse, and failure matrix | Every integration records supported runtime update, required reconstruction, and unsupported styling behavior without hidden fallback |
| 8. Documentation, samples, and tooling | Not started | Module catalog/manuals, API reference, tutorials/guides/migration, Chinese mirrors, compiled Q3 samples, Demo routes, Preview diagnostics/fallbacks, and notices | Documentation structure/localization, sample compilation, Preview fallback, consumer builds, and release-intent gates pass |
| 9. Device, performance, and release closeout | Not started | Same-build timing/allocation/leak evidence, supported-device matrix, final Changesets, Maven consumer verification, and durable conclusions | No accepted regression or leak; all required gates pass; conclusions move to active docs before archival |

## Integration-specific acceptance

### AndroidX Media3

1. The first public component accepts a caller-owned Media3 `Player`; it does not construct or
   release the player implicitly.
2. `PlayerView` configuration is replay-safe, while player attachment happens only after commit.
   Old listeners, player references, and Surface relationships are cleared on reset/stop/release,
   and permanent release never releases caller state.
3. Controller visibility, artwork/shutter/background appearance, content description, resize mode,
   and constructor-sensitive Surface choice have explicit state or construction ownership.
4. Player-to-View and Surface attachment follows the scoped lifecycle and visible navigation owner.
   A hidden retained page cannot keep those View resources active solely because its Activity is
   resumed; caller-owned playback and background audio policy remain untouched.
5. A repository-owned local media fixture validates first frame, pause/resume, replacement, error,
   and cleanup without network variability.

### Legacy ExoPlayer 2

1. The public artifact and package make the legacy namespace explicit, mount
   `StyledPlayerView`, and never expose Media3 types as aliases or transitive requirements.
2. The initial component accepts a caller-owned legacy `Player`/`ExoPlayer` and preserves the same
   reset, commit, lifecycle, construction-key, and release semantics as the Media3 line where the
   SDK contract permits.
3. Module documentation states the exact frozen dependency line, compatibility limits, and the
   relationship to the separate Media3 integration without promising automatic migration.
4. Independent consumer tests prove an application can depend on either integration alone and that
   installing both does not select one by classpath order.

### Google Maps

1. The integration owns one `MapView` lifecycle per committed component and isolates its saved
   Bundle by stable component key.
2. Map-ready delivery cannot publish from a rolled-back candidate or invoke stale callbacks after
   replacement/release.
3. Camera position, UI settings, markers/overlays selected for the first scope, and supported map
   styling use explicit immutable inputs with documented ownership and diff behavior.
4. Credentials remain an application concern. Credential-free tests cover contract ordering; a
   separately configured device smoke test records real renderer evidence without committing keys.
5. Theme/style changes identify whether runtime style update is exact, requires map recreation, or
   is unsupported by the frozen SDK baseline.

### CameraX

1. The first public component mounts `PreviewView` and defines whether the caller supplies a
   controller/provider/use-case bundle. It never requests runtime permission automatically.
2. Camera binding begins only after commit and targets the nearest lifecycle owner; owner changes,
   navigation hiding, removal, and failure unbind the exact owned use cases without disturbing
   caller-owned unrelated bindings.
3. Implementation mode, scale type, lens selection, rotation, and Surface reconstruction have
   explicit update or construction-key semantics.
4. Fake/provider tests cover deterministic ordering. Physical-device evidence covers permission,
   foreground/background, rotation, front/back selection, navigation retention, and final release.
5. Preview/Layoutlib reports a bounded unsupported placeholder and never claims that camera frames
   were rendered.

## Validation matrix

### Common focused gates

```bash
./gradlew :viewcompose-host-android:testDebugUnitTest --no-configuration-cache
./gradlew :viewcompose-renderer-android:testDebugUnitTest --no-configuration-cache
./gradlew :viewcompose-lifecycle-androidx:testDebugUnitTest --no-configuration-cache
./gradlew verifyDocumentationStructure
./gradlew verifyDevelopmentToolingIsolation
./gradlew verifyViewComposeReleaseIntent
./gradlew qaQuick
./gradlew qaPreview
```

After their corresponding phase creates them, the exact focused module tasks are
`:viewcompose-media3-androidx:testDebugUnitTest`,
`:viewcompose-exoplayer2-android:testDebugUnitTest`,
`:viewcompose-google-maps-android:testDebugUnitTest`, and
`:viewcompose-camerax-androidx:testDebugUnitTest`, plus each module's compiled sample and minimal
published-consumer fixture. Phase 1 adds the Host and Renderer API/transaction tests before any of
those tasks exist. `qaFull` is required after the first integration mounts real device resources.
Credentialed Maps and physical CameraX evidence are additional scoped gates, not replacements for
the complete repository device suite. Phase 0 itself runs documentation structure, release intent,
and repository cleanliness because it intentionally owns no production artifact.

### Required scenario dimensions

| Dimension | Common adapter | Media3 | Legacy ExoPlayer | Google Maps | CameraX |
| --- | --- | --- | --- | --- | --- |
| Factory candidate rollback | Required | Required | Required | Required | Required |
| Replay-safe update rollback | Required | Required | Required | Required | Required |
| Same-key update | Required | Required | Required | Required | Required |
| Construction-key replacement | Required | Surface/style | Surface/style | Options/style | Implementation mode/Surface |
| Lazy cross-key reuse | Required | Detach/reset | Detach/reset | Reset or explicitly reject reuse | Reset or explicitly reject reuse |
| Navigation hidden/visible lifecycle | Required | Required | Required | Required | Required |
| Configuration/theme refresh | Required | Required | Required | Required | Required surrounding/UI policy |
| Process recreation | Contract | Player/app state separation | Player/app state separation | SDK Bundle plus app state | Permission/use-case state separation |
| Accessibility/focus/input | Required | Controller actions | Controller actions | Map gestures/description | Preview semantics/controls |
| Final release/leak | Required | Player/listeners/Surface | Player/listeners/Surface | Map callbacks/resources | Provider/use cases/Surface |
| Static Preview | Diagnostic fallback | Placeholder | Placeholder | Placeholder | Placeholder |

## Documentation and API impact

This plan is expected to add and change public/protected APIs. The planning classification is:

- typed AndroidView adapter family, construction identity, lifecycle/saved-state decorators, and
  integration component functions: Q3 because they cross Android host boundaries, own resources,
  expose callbacks, participate in transactions, and have non-obvious failure and performance
  behavior;
- immutable appearance, options, diagnostic, and state value objects: Q2 unless their ownership or
  lifecycle behavior makes guided usage necessary;
- applicable contract fields: behavior, identity, state ownership, lifecycle, threading,
  callbacks, commit/reset/release ordering, rollback/failure, Android API/device/configuration,
  accessibility, performance, compatibility, and resource cleanup;
- every new published artifact: publication metadata, module catalog/manual, generated API tree,
  compatibility/consumer fixture, independent version/source revision, and release Changeset;
- framework documentation: Android interop tutorial/migration, theming, lifecycle, render-failure,
  session-container, Preview, performance, and architecture boundaries as affected; and
- localization: update canonical English first, then every required Simplified Chinese mirror and
  reviewed source fingerprint in the same change.

The first production pull request replaces this plan's `- None.` entry with its immutable
`release/changes/<unique>.json` path. Each later production Changeset owned by this plan is added as
its own bullet. Reverse-dependency release impact remains release-planner owned.

## Completion criteria

This plan is complete only when:

1. the typed adapter and construction-identity contract preserve current AndroidView transaction,
   rollback, keyed reuse, and one-shot release behavior;
2. lifecycle and saved-state coordination follow Activity, Fragment, retained navigation, and
   process-recreation ownership without leaking or overlapping observers;
3. Media3, legacy ExoPlayer 2, Google Maps, and CameraX each ship as an independently removable,
   documented, tested integration with no SDK identity in lower layers;
4. applications can install Media3 and legacy ExoPlayer together without ambiguous APIs,
   dependency substitution, classpath selection, or shared mutable global policy;
5. every integration records exact runtime-theme update, constructor-sensitive recreation, and
   unsupported restyling behavior;
6. deterministic unit/fixture tests and required Preview, repository, credentialed Maps, and
   physical CameraX/device gates pass on the frozen support matrix;
7. accepted performance and leak evidence is interpreted in the owning active documentation with
   absolute results, normalized comparison where valid, conclusion, limitations, and next action;
8. public KDoc, compiled Q3 samples, module manuals, cross-module docs, Chinese mirrors, publication
   metadata, consumer tests, notices, and immutable Changesets are complete; and
9. durable conclusions move into active documentation, this plan moves to `docs/archive/`, both
   plan indexes are updated, and the release-time archival gate passes before Maven Central upload.

## Evidence ledger

| Date | Revision | Phase | Command or evidence | Result | Decision and next action |
| --- | --- | --- | --- | --- | --- |
| 2026-08-18 | Working tree | Planning baseline | Documentation governance, active roadmap/plan index, CodeGraph AndroidView impact analysis, dependency/source search | Existing transaction/resource/lifecycle foundation confirmed; no target SDK dependency or adapter exists | Land the independent plan, then freeze SDK dependency and module baselines in Phase 0 |
| 2026-08-24 | `54151a09f082518c7e49146caf6853b24ffc54ba` | Phase 0 repository audit | CodeGraph AndroidView callback/renderer/reuse paths; `settings.gradle.kts`, publishing registry, dependency contracts, module catalog/manuals, and coordinate search | Existing Google Maven policy and module architecture can host all four independent integrations; same-identity observed-property binding incorrectly shares `onReset` with cross-key reuse | Hard-cut reset semantics and construction identity in Phase 1 before creating SDK modules |
| 2026-08-24 | External fixed sources | Phase 0 SDK audit | Official release/API/lifecycle documentation plus direct fixed-POM probes in Google Maven and Maven Central | Media3 1.11.0, CameraX 1.6.1, Maps 20.0.0, and legacy ExoPlayer 2.19.1 resolve from Google Maven; legacy core/UI are absent from Maven Central; lifecycle, Surface, deprecation, minSdk, license, and Maps known-issue contracts recorded | Freeze exact lines, Google Maven, ownership, notices, and device lanes; begin common adapter implementation only |
| 2026-08-24 | Working tree from `54151a09f082518c7e49146caf6853b24ffc54ba` | Phase 0 closeout | `./gradlew verifyDocumentationStructure verifyViewComposeReleaseIntent --console=plain`; `git diff --check` | Documentation structure passed for 113 canonical and 109 current Chinese pages; release intent reported 0 release artifacts, 0 ignored artifacts, and 0 shared-path classifications; diff check passed | Phase 0 is complete without production or publication changes; start the Phase 1 typed-adapter and reset-semantics hard cut |
| 2026-08-24 | Working tree from `718220e6178f8368646c59a7119671e188d16799` | Phase 1 implementation and closeout | Focused Host/Renderer JVM tests; Android 9 Xiaomi MI 6 `AndroidInteropRenderingUiTest`; selected Q3 API audit; documentation/release/tooling gates; `./gradlew qaQuick`; `./gradlew qaPreview` | Focused tests passed; device instrumentation passed 3/3 in 70.283 s; API/docs/release/tooling gates passed; `qaQuick` passed 1,945 tasks in 6 min 45 s; `qaPreview` passed 1,115 tasks in 24 s; release intent reported exactly 3 artifacts; no inactive registration, poller, SDK dependency, or recurring work was introduced | Comparison baseline had one reset on an ordinary same-key update and no construction identity; Phase 1 records zero such resets, a 100% removal for that asserted transition, and atomic replacement. Conclusion: improved. Gate timings are not normalized because cache state differs and support no performance claim. Device evidence is limited to one Android 9 model and used root installation only to bypass MIUI USB confirmation; SDK-specific devices remain Phase 3–7 work. Begin Phase 2 lifecycle and saved-state coordination. |
| 2026-08-24 | Working tree from `eb02abc5d95bf92d03ebf565e6268e379ac3cba5` | Phase 2 implementation and closeout | Lifecycle module JVM/Robolectric tests; affected Host/Renderer/Android/navigation/Preview tests; selected Q3 API audit; documentation/dependency/release/tooling gates; `./gradlew qaQuick`; `./gradlew qaPreview` | All 35 lifecycle-module tests passed, including 6 lifecycle-adapter and 3 SDK saved-state cases; affected tests and API/docs/dependency/tooling gates passed; `qaQuick` passed 1,954 tasks in 6 min 35 s; `qaPreview` passed 1,115 tasks in 22 s; release intent reported exactly 7 artifacts | The baseline had composition-scoped lifecycle effects but no transaction-bound native View owner or SDK Bundle provider. Phase 2 adds post-commit serial ownership, retained-destination capping, failure cleanup, process recreation, and one-shot provider removal. Conclusion: improved. Gate timings are not cache-normalized and support no performance claim. No SDK or visual surface was added, so device UI evidence would not test new behavior; the connected Xiaomi lane is reserved for Phase 3 Media3 Surface and foreground/background validation. Begin Phase 3. |

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-08-18 | Keep all SDK identities out of Android Renderer and neutral Host | Named optional integrations can be removed independently and cannot impose dependency, policy, or hot-path work on lower layers |
| 2026-08-18 | Treat Media3 and legacy ExoPlayer as separate integration lines | The namespace and dependency contracts differ; one ambiguous ExoPlayer adapter would conceal compatibility and migration behavior |
| 2026-08-18 | Separate logical `key` from constructor-sensitive `constructionKey` | A theme/style/SDK option may require View replacement without changing application content identity or abusing cross-key reuse |
| 2026-08-18 | Default player ownership to the caller | Attaching a player to a View does not authorize the integration to release application-owned playback state |
| 2026-08-18 | Keep Maps credentials and CameraX permissions in application policy | Repository integrations may coordinate committed Views and lifecycle but cannot own product credentials, consent, or permission UX |
| 2026-08-18 | Use deterministic local/fake fixtures before credentialed or physical evidence | Network, credentials, codecs, and hardware availability must not make ordinary CI nondeterministic or produce false implementation claims |
| 2026-08-24 | Freeze stable Media3 1.11.0, CameraX 1.6.1, Maps 20.0.0, and final legacy ExoPlayer 2.19.1 from Google Maven | Stable fixed lines match the repository's API 24 floor; the legacy line is intentionally frozen and cannot be substituted with Media3 |
| 2026-08-24 | Use legacy `StyledPlayerView`, not legacy `PlayerView` | The final upstream migration path explicitly prepares `StyledPlayerView` for Media3 `PlayerView`; adding the older legacy widget would create avoidable migration debt |
| 2026-08-24 | Make reset cross-key-only and add an explicit reuse policy | Same-identity state replacement already has replay-safe update and rollback; invoking reset there contradicts the public contract and lets incomplete update implementations survive |
| 2026-08-24 | Include adapter implementation class in construction identity | Switching SDK adapter families with the same logical/construction key must replace the native View, while recreating an equivalent adapter object must not |
| 2026-08-24 | Release a displaced View at structural commit and keep candidate `onCommit` after composition commit | Permanent native ownership cleanup cannot depend on a low-level Renderer caller executing returned commit effects; SDK publication/attachment work still cannot run for a rolled-back composition |
| 2026-08-24 | Scope media lifecycle to View/player attachment, not playback commands | Caller ownership permits background audio or service playback; ViewCompose owns controller, listener, and Surface cleanup but cannot pause or release the supplied player |
| 2026-08-24 | Require an explicit Maps saveable-state key and exact CameraX Preview ownership | Arbitrary logical keys cannot become durable process-state namespaces, and `unbindAll` would exceed authority over caller-owned unrelated camera use cases |
| 2026-08-24 | Keep CameraX backend selection and Maps StrictMode policy application-owned | The supplied provider already represents backend policy, while a process-global StrictMode relaxation would hide an attributed upstream Maps 20.0.0 issue |
| 2026-08-24 | Put typed View lifecycle coordination in the existing AndroidX lifecycle integration | Composition-scoped effects have no native View transaction boundary; an AndroidX adapter can reuse the committed Host contract and retained destination owner without teaching neutral Host about Lifecycle |
| 2026-08-24 | Separate lifecycle and saved-state owner locals and keep SDK Bundle schemas downstream | Fragment View work must end at `onDestroyView` while Fragment SavedState can survive View recreation; only provider registration, version isolation, replacement, and cleanup are generic across SDKs |
| 2026-08-24 | Make lifecycle adapter terminal hooks automatically clear both owner bindings | Requiring every SDK adapter to remember provider cleanup would make reset/release safety convention-based; the base class can enforce one-shot cleanup without SDK knowledge |
