# Third-Party Android View Integrations Plan

## Status

Active. Planning baseline only; no production implementation or publication input has started.
The target integrations are AndroidX Media3, legacy `com.google.android.exoplayer2` ExoPlayer,
Google Maps SDK for Android, and CameraX. Media3 and legacy ExoPlayer are separate compatibility
lines because their public namespaces, dependency graphs, and consumer migration constraints are
not interchangeable.

This plan is canonical English-only under the documentation-governance policy. Every durable API,
theme, lifecycle, saved-state, and ownership contract must move into active architecture, guide,
migration, and owning-module documentation before this plan is archived.

Last verified: 2026-08-18.

Next action: complete Phase 0 by pinning one reviewed dependency/version baseline for each target,
freezing the module and package names, and recording the exact lifecycle, saved-state, theme,
ownership, test-fixture, and supported-device matrix before adding production source.

## Maven release changesets

- None.

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

The artifact and package names below are the planning target. Phase 0 must confirm them against the
publication catalog and dependency baseline before production source is created.

| Integration line | Target SDK surface | Proposed artifact | Proposed package root | First-release ownership |
| --- | --- | --- | --- | --- |
| AndroidX Media3 | `androidx.media3.ui.PlayerView`, the Media3 `Player` contract, and Media3 ExoPlayer implementations supplied by callers | `viewcompose-media3-androidx` | `com.viewcompose.media3` | Caller owns the `Player`; the integration owns only View attachment, controller appearance, lifecycle policy, and listener cleanup |
| Legacy ExoPlayer | `com.google.android.exoplayer2.ui.PlayerView` and legacy `Player`/`ExoPlayer` instances | `viewcompose-exoplayer2-android` | `com.viewcompose.exoplayer2` | Caller owns the player; the integration is an explicitly legacy, independently removable compatibility line |
| Google Maps | `com.google.android.gms.maps.MapView`, map-ready delivery, camera/UI state, and SDK-owned saved state | `viewcompose-google-maps-android` | `com.viewcompose.maps.google` | Integration owns the `MapView` lifecycle; the application owns credentials, map policy, remote style/data, and business state |
| CameraX | `androidx.camera.view.PreviewView`, preview surface coordination, camera selection, and lifecycle-bound use cases | `viewcompose-camerax-androidx` | `com.viewcompose.camerax` | Application owns permissions and use-case policy; ownership of provider/use cases is explicit and never inferred from a View reference |

The Media3 line may accept an `androidx.media3.exoplayer.ExoPlayer` through the Media3 `Player`
contract. The separate ExoPlayer 2 line exists only for consumers whose source still uses the
legacy `com.google.android.exoplayer2` namespace; it must not blur the two dependency families or
silently adapt one into the other.

## Scope

### Common Android View adapter foundation

The common foundation may add high-risk Q3 API in `viewcompose-host-android` for:

- a typed `AndroidViewAdapter<V, S>` contract;
- immutable Android resource/environment input visible to adapter creation and replay-safe update;
- a separate `constructionKey` whose change creates a new View without changing application
  content identity;
- structured reset reasons that distinguish ordinary rebinding from cross-key mounted-tree reuse;
- commit and release callbacks with the existing renderer transaction guarantees;
- optional adapter diagnostics that identify the integration, lifecycle binding, construction
  generation, and fallback without retaining the native View; and
- compatibility delegation from the existing callback-based `AndroidView` API.

The target shape, subject to Phase 0 signature and dependency review, is:

```kotlin
interface AndroidViewAdapter<V : View, S> {
    fun create(scope: AndroidViewCreateScope): V
    fun update(scope: AndroidViewUpdateScope<V>, state: S)
    fun onReset(view: V, reason: AndroidViewResetReason) = Unit
    fun onCommit(view: V, state: S) = Unit
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
Runtime-restylable values belong in immutable adapter state; context identity, SDK options, or
styles that are read only during construction belong in `constructionKey`. A resource revision
must never force recreation by default when replay-safe update is sufficient.

### Lifecycle and saved state

The plan may add downstream helpers in `viewcompose-lifecycle-androidx` or the SDK-specific
integrations for:

- registering a lifecycle observer only after the Android View transaction commits;
- catching up to the nearest current `LifecycleOwner` state in Android lifecycle order;
- serial owner replacement without overlapping active observers;
- unregistering before final View cleanup;
- keeping retained navigation destinations capped by their destination owner rather than the
  Activity owner;
- restoring SDK state before first visible use and saving from the committed View only; and
- isolating corrupt or incompatible SDK state without corrupting the surrounding ViewCompose
  saveable registry.

The common Host contract must not depend upward on AndroidX Lifecycle or an individual SDK. A
reusable lifecycle decorator belongs in an AndroidX integration layer; SDK-specific lifecycle and
Bundle behavior stays with the SDK integration when it cannot be expressed generically.

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

## Current baseline

Verified from the worktree on 2026-08-18:

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

## Locked architectural rules

1. SDK types appear only in their named integration artifact, compiled samples, Demo fixtures, and
   integration-specific tests.
2. The common adapter contract uses Android/ViewCompose types and resolved values, never Media3,
   Maps, CameraX, or legacy ExoPlayer types.
3. Adapter `update` and `onReset` replace complete replay-safe View configuration and may be called
   again during rollback.
4. Network calls, playback publication, camera binding, map listener publication, analytics, and
   other irreversible effects begin only after commit or in composition-scoped work triggered by
   committed state.
5. `onRelease` detaches listeners, surfaces, player references, map callbacks, and camera bindings
   exactly once. It releases the underlying SDK resource only when the public integration
   explicitly created and owns it.
6. Theme/configuration changes prefer replay-safe state update. Recreation is explicit through a
   stable construction key and cannot be inferred from SDK class names.
7. A hidden retained destination follows its destination lifecycle and cannot keep video playback,
   map location work, or camera capture active merely because the Activity remains resumed.
8. Preview and unit tests use deterministic stand-ins; missing hardware, credentials, Google Play
   services, codecs, or network connectivity yields a structured unsupported/skipped result rather
   than a false pass.
9. Each published artifact is independently removable and adds no recurring work when no matching
   integration component is mounted.
10. No new public contract is retained without its Q level, applicable contract fields,
    canonical-English KDoc, compiled Q3 sample, module manual, Chinese public-document mirrors, and
    immutable release Changeset.

## Execution plan

| Phase | Status | Deliverable | Exit gate |
| --- | --- | --- | --- |
| 0. Dependency and contract freeze | Not started | Pin reviewed SDK versions and repositories; freeze module/package names, supported API/device matrix, license/notice impact, ownership table, deterministic fixtures, and rollback strategy | Written baseline agrees with settings, publication metadata, module architecture, and every target SDK contract |
| 1. Typed AndroidView adapter | Not started | Q3 typed adapter, environment scopes, separate construction identity, reset reason, compatibility delegation, diagnostics, and renderer-neutral tests | Factory/update/reset/commit/release ordering, rollback, replacement, keyed reuse, and zero-adapter inactive cost pass |
| 2. Lifecycle and saved-state coordination | Not started | AndroidX lifecycle decorator plus reusable saved-state boundary where evidence supports it | Owner catch-up/replacement, retained destination visibility, corrupt restore, process recreation, and one-shot cleanup pass |
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
2. `PlayerView` attachment is replay-safe, old listeners and player references are cleared on reset,
   and permanent release detaches the View without releasing caller state.
3. Controller visibility, artwork/shutter/background appearance, content description, resize mode,
   and constructor-sensitive Surface choice have explicit state or construction ownership.
4. Playback activation follows the scoped lifecycle and visible navigation owner. A hidden retained
   page cannot remain active solely because its Activity is resumed.
5. A repository-owned local media fixture validates first frame, pause/resume, replacement, error,
   and cleanup without network variability.

### Legacy ExoPlayer 2

1. The public artifact and package make the legacy namespace explicit and never expose Media3
   types as aliases or transitive requirements.
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
./gradlew :viewcompose-lifecycle-androidx:testDebugUnitTest --no-configuration-cache
./gradlew verifyDocumentationStructure
./gradlew verifyDevelopmentToolingIsolation
./gradlew verifyViewComposeReleaseIntent
./gradlew qaQuick
./gradlew qaPreview
```

Phase 0 must replace or extend this list with the exact module test and consumer tasks after module
names are frozen. `qaFull` is required after the first integration mounts real device resources.
Credentialed Maps and physical CameraX evidence are additional scoped gates, not replacements for
the complete repository device suite.

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

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-08-18 | Keep all SDK identities out of Android Renderer and neutral Host | Named optional integrations can be removed independently and cannot impose dependency, policy, or hot-path work on lower layers |
| 2026-08-18 | Treat Media3 and legacy ExoPlayer as separate integration lines | The namespace and dependency contracts differ; one ambiguous ExoPlayer adapter would conceal compatibility and migration behavior |
| 2026-08-18 | Separate logical `key` from constructor-sensitive `constructionKey` | A theme/style/SDK option may require View replacement without changing application content identity or abusing cross-key reuse |
| 2026-08-18 | Default player ownership to the caller | Attaching a player to a View does not authorize the integration to release application-owned playback state |
| 2026-08-18 | Keep Maps credentials and CameraX permissions in application policy | Repository integrations may coordinate committed Views and lifecycle but cannot own product credentials, consent, or permission UX |
| 2026-08-18 | Use deterministic local/fake fixtures before credentialed or physical evidence | Network, credentials, codecs, and hardware availability must not make ordinary CI nondeterministic or produce false implementation claims |
