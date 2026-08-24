# Third-Party Android View Integrations Plan

## Status

Active. Phases 0–3 are complete; durable contracts/evidence live in Host, Lifecycle, and Media3
manuals. Remaining: separate legacy ExoPlayer, Maps, CameraX, cross-integration validation, and
closeout. This plan is canonical English-only; durable results move to owning active docs before
archival.

Last verified: 2026-08-24.

Next action: begin Phase 4 with the independent legacy ExoPlayer 2 compatibility module.

## Maven release changesets

- `release/changes/20260824-typed-android-view-adapter.json`
- `release/changes/20260824-android-view-lifecycle-saved-state.json`
- `release/changes/20260824-media3-player-view.json`

## Objective

Ship independently removable, transaction-aware SDK Views without SDK identities in Renderer.
Preserve typed identity/update/commit/cleanup; coordinate lifecycle/configuration/state with
explicit ownership; validate rollback, retention, reuse, recreation, accessibility, and leaks.

## Frozen integrations and dependencies

Artifacts resolve from `google()`, start at `0.1.0-alpha01`, use Host/Lifecycle as needed, and
independently enter publication/docs/consumer/release intent without aggregates or sibling SDKs.

| Line | Artifact / package / View | Frozen SDK and exposure | Ownership and constraint |
| --- | --- | --- | --- |
| Media3 | `viewcompose-media3-androidx`; `com.viewcompose.media3`; `PlayerView` | 1.10.1; common API/UI impl; Demo ExoPlayer | Caller player; integration attachment/UI. 1.11.0 needs Kotlin 2.2. |
| Legacy | `viewcompose-exoplayer2-android`; `com.viewcompose.exoplayer2`; `StyledPlayerView` | final 2.19.1; core API/UI impl | Caller player; deprecated, no Media3 alias; Google Maven required. |
| Maps | `viewcompose-google-maps-android`; `com.viewcompose.maps.google`; `MapView` | 20.0.0 API | Integration View lifecycle/state; app credentials/data/policy/StrictMode. |
| CameraX | `viewcompose-camerax-androidx`; `com.viewcompose.camerax`; `PreviewView` | 1.6.1 core/lifecycle API, view impl; Demo camera2 | App permission/backend/policy; integration exact Preview binding. |

Versions are exact; minSdk 24, compileSdk 36, Java 11 remain. Changes reopen Phase 0. Sources:
[Media3](https://developer.android.com/jetpack/androidx/releases/media3),
[legacy](https://developer.android.com/media/media3/exoplayer/migration-guide),
[Maps](https://developers.google.com/maps/documentation/android-sdk/release-notes), and
[CameraX](https://developer.android.com/jetpack/androidx/releases/camera).

## Frozen support and validation matrix

| Lane | Required evidence |
| --- | --- |
| JVM/local | Transactions/leaks; fake SDK ports; local media; no renderer/frame claim. |
| API 24 | Reuse/configuration/input/release; unavailable/denied paths. |
| Xiaomi API 28 | View lifecycle/leaks; media transitions; optional Maps; physical CameraX matrix. |
| API 31+ Play | Surface/navigation; credentialed Maps + attributed StrictMode; CameraX lifecycle. |
| API 36 | Compile/configuration/accessibility/release. |
| Preview | Bounded placeholder; no decoder/map/camera claim. |

Managed devices cover deterministic behavior only. Codec/Maps/camera claims need real capability;
Phase 9 needs API 31+ Google Play and physical camera, with missing lanes unexecuted. Use pairwise
theme/locale/RTL/font/density plus targeted reconstruction/restore. Evidence records build, SDK,
device, absolute result, conclusion, limitation, and next action.

## License, notice, and deterministic-fixture baseline

- Media3/legacy: Apache-2.0; CameraX: Apache-2.0/BSD-3-Clause; Maps: Android SDK/Platform terms.
  Coordinates/links live in `THIRD_PARTY_NOTICES.md` and manuals.
- Vendor no SDK/native/codec/map binary, key, or third-party media. Local media records provenance,
  metadata, and SHA-256; Maps/CameraX ordinary CI uses fakes and external capability for smoke.

## Scope

Phases 1–2 are frozen in the Host/Lifecycle manuals and
`docs/architecture/lifecycle-and-saved-state.md`; future modules consume those contracts unchanged.

| Integration | Commit/lifecycle and saved-state ownership |
| --- | --- |
| Players | Replay-safe configure; post-commit/start attach; stop/reset/release detaches View resources. Never command/release caller player or save player state. |
| Maps | Post-commit restore/ready/lifecycle; reverse cleanup; generation-safe callbacks. Explicit `saveableStateKey` owns an isolated versioned Bundle. |
| CameraX | Post-commit exact Preview/provider/selector binding; lifecycle cleanup unbinds only that Preview, never `unbindAll`. App owns permission/backend/other use cases. |

Ownership is main-thread serial, rejects destroyed owners, follows destination caps, and publishes
SDK work only after commit. Post-commit failure cleans up without pretending tree rollback.

Each module classifies configuration as replay-safe update, explicit `constructionKey`
replacement, or unsupported restyling. It owns a launchable Demo, automation roles, and compiled
sample; media stays local, Maps credentials external, and CameraX hardware evidence physical.

## Non-goals

- No SDK branch in UI contract/Renderer/neutral Host, universal plugin registry, classpath adapter
  selection, Fragment-in-tree, or `AndroidViewBinding` layer.
- No automatic skinning of arbitrary SDK Views or Preview emulation of decoder/map/camera/Surface.
- No ownership of caller player/camera/map state, credentials, permission, network, account, DRM,
  analytics, or SDK behavior.
- No bundled credentials/restricted assets or undocumented legacy alias after retirement.

## Locked architectural rules

1. SDK types stay in the named module, its sample/Demo, and tests; foundations use only
   Android/ViewCompose types and resolved values.
2. Complete replay-safe `update` supports rollback. `onReset` is cross-key reuse only; logical key,
   construction replacement, and reuse remain distinct.
3. Irreversible SDK work starts after commit. `onRelease` detaches owned references exactly once and
   releases an SDK resource only when the integration explicitly created it.
4. Configuration prefers complete update; reconstruction uses stable `constructionKey`.
5. Retained destinations follow their capped owner. Integration detaches hidden player/map/camera
   View work but never commands caller-owned background playback.
6. Missing hardware, credentials, services, codecs, or network is unsupported/skipped, never a
   false pass. An inactive independently removable artifact owns no recurring work.
7. Public contracts require Q classification, contract fields, English KDoc, compiled sample,
   module manual/mirror, and Changeset.
8. Maps StrictMode/legacy-renderer limits remain upstream; ViewCompose changes no global policy.

## Execution plan

| Phase | Status | Deliverable | Exit gate |
| --- | --- | --- | --- |
| 0–3. Foundation + Media3 | Complete | Typed transaction/lifecycle/state and independent Media3 | Owning gates/evidence passed. |
| 4. Legacy ExoPlayer | Not started | Separate legacy module, no Media3 alias | Isolation, ownership, lifecycle/theme, cleanup, migration. |
| 5. Maps | Not started | Lifecycle/ready/camera/UI/style/state | Fake/no-key + credentialed restore/navigation/leak. |
| 6. CameraX | Not started | Exact Preview/provider/use-case coordination | Denial/fake + physical lens/rotation/navigation/cleanup. |
| 7. Configuration | Not started | Pairwise environment/reuse/failure | Record update, reconstruction, or unsupported. |
| 8. Docs/tooling | Not started | API/manuals/mirrors/samples/Demo/Preview/notices | Docs/sample/consumer/release gates. |
| 9. Closeout | Not started | Device/performance/leaks/Changesets | No regression/leak; durable conclusions; archive. |

## Integration-specific acceptance

### AndroidX Media3

Complete; owning contract/evidence is in `docs/modules/viewcompose-media3-androidx/README.md`.

### Legacy ExoPlayer 2

Mount `StyledPlayerView` with caller legacy player; no Media3 alias/dependency. Match common
transaction/lifecycle/identity/cleanup, document frozen migration limits, and prove both modules
work alone/together without classpath selection.

### Google Maps

Own one committed lifecycle and versioned Bundle per explicit key. Ready callbacks are
generation-safe; immutable camera/UI/overlay/style inputs have defined diffs. Fake tests prove
ordering; external-key smoke proves renderer; styling declares update/recreate/unsupported.

### CameraX

Mount `PreviewView` without requesting permission. Post-commit binding follows nearest owner and
unbinds only its Preview. Mode/scale/lens/rotation/Surface identity are explicit. Fake tests prove
order; physical evidence proves permission/lifecycle/lenses/rotation/navigation/release. Preview
never claims frames.

## Validation matrix

Each phase runs its focused module tests and compiled sample, publication consumer, documentation,
development-tooling, release-intent, `qaQuick`, and `qaPreview`. `qaFull` covers real resources;
credentialed Maps and physical CameraX smoke are additional, not substitutes.

### Required scenario dimensions

All modules cover factory/update rollback, same-key update, construction replacement, reuse or
explicit rejection, hidden/visible lifecycle, configuration, state ownership, accessibility/input,
final release/leaks, and Preview fallback. Player modules additionally prove Surface/style and
controller behavior; Maps proves options/style, Bundle/app-state separation, gestures/callbacks;
CameraX proves implementation mode/Surface, permission/use-case separation, and exact unbind.

## Documentation and API impact

Adapters/decorators/components are Q3; immutable state is Q2 unless lifecycle/ownership raises it.
Cover identity, ownership, ordering/failure, device/configuration, accessibility, performance,
compatibility, and cleanup. Each artifact ships KDoc, compiled sample, manual/mirror, API,
publication/dependency metadata, consumer smoke, Changeset, and affected framework docs.

## Completion criteria

Complete when all four removable modules preserve foundation contracts; Media3/legacy coexist;
ownership and styling limits are explicit; deterministic, Preview, credentialed Maps, physical
CameraX, device/performance/leak gates have interpreted results; and all API/docs/publication/
consumer/notice/Changeset work lands before archival.

## Evidence ledger

| Date | Revision | Phase | Command or evidence | Result | Decision and next action |
| --- | --- | --- | --- | --- | --- |
| 2026-08-24 | `54151a09` | 0 | Repository/Maven/upstream and docs/release audit | Four lines resolved; legacy needs Google Maven; same-key reset conflict found; docs 113 EN/109 ZH. | Hard-cut foundation. Baseline only; no runtime comparison. |
| 2026-08-24 | `718220e6` | 1 | Focused gates, Android 9, `qaQuick`/`qaPreview` | Device 3/3/70.283s; quick 1,945/6m45s; Preview 1,115/24s. Same-key reset 1→0 (100%); atomic replacement. | **Improved.** Cache-unnormalized; one device, no SDK claim. |
| 2026-08-24 | `eb02abc5` | 2 | Lifecycle/affected/repository gates | 35 module tests (6 adapter, 3 SDK state); quick 1,954/6m35s; Preview 1,115/22s. Added serial post-commit ownership/cleanup. | **Improved.** Cache-unnormalized; no visual change. |
| 2026-08-24 | `2b7769a7` | 3 compatibility | Media3 1.11.0 compile/insight vs 1.10.1 POM | Kotlin 2.0.21 rejects 1.11.0's Kotlin 2.2 metadata; 1.10.1 compiles. | Pin 1.10.1; runtime **inconclusive**. Revisit after toolchain upgrade. |
| 2026-08-24 | `2b7769a7` | 3 device | Pixel 4 XL/API 31 local fixture and manual screenshots | 1/1/2.519s; first frame, player/Surface replacement, clearing, detach/reattach, motion, unclipped UI passed. | **Improved** vs no adapter. One muted H.264/AAC device; no broad media/power parity claim. |
| 2026-08-24 | `2b7769a7` | 3 closeout | Module/App tests; release/docs/tooling gates; published consumer; `qaQuick`; `qaPreview`; full API/site builds | 5 module and 46 App tests passed; quick 2,029/6m32s; consumer 892/27s; Preview 1,147/22s; API 13m56s; site 34.2s and non-API 45.9/46.0 MiB. | **No material regression** in repository gates; Phase 3 accepted. Timings are cache/scope-unnormalized and make no performance claim. Begin Phase 4. |

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-08-18 | SDKs stay out of Renderer/neutral Host; integrations stay separate/removable | No lower-layer policy/hot work or ambiguous Media3/legacy graph. |
| 2026-08-18 | Caller owns players; app owns Maps credentials and CameraX permission/backend | View lifecycle grants no authority over product state, consent, or playback. |
| 2026-08-18 | Deterministic local/fake tests precede capability smoke | Ordinary CI cannot depend on network, credentials, codec, or hardware. |
| 2026-08-24 | Pin CameraX 1.6.1, Maps 20.0.0, legacy 2.19.1, Media3 1.10.1 | Fixed API-24 lines; Media3 1.11.0 requires unsupported Kotlin metadata. |
| 2026-08-24 | Legacy uses `StyledPlayerView` | It is the final migration counterpart to Media3 `PlayerView`. |
| 2026-08-24 | Separate key/construction/reuse; reset cross-key only | Same identity uses replay-safe update; adapter-family changes reconstruct. |
| 2026-08-24 | Structural commit releases displacement; composition commit publishes SDK work | Ownership is deterministic and rolled-back candidates never publish. |
| 2026-08-24 | Media owns attachment only; Maps uses explicit state key; CameraX unbinds exact Preview | Prevent commands/releases, unstable namespaces, and over-broad `unbindAll`. |
| 2026-08-24 | AndroidX lifecycle module owns typed coordination and terminal binding cleanup | Neutral Host stays clean; View/state owners remain distinct and cleanup enforced. |
| 2026-08-24 | Maps StrictMode stays application/upstream policy | Never hide the Maps 20.0.0 issue through a global relaxation. |
