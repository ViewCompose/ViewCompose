# Compose Migration Capability Convergence Plan

## Status

Active after a 2026-08-14 implementation and contract re-audit. The retained plan now owns proven
ViewCompose correctness and Android-ecosystem compatibility work, not general Compose API parity.
Diagnostics are supporting test infrastructure only; they are not a product goal or an independent
delivery phase. The Phase 0 evidence and Phase 1-3 runtime, host, and navigation correctness slices
are implemented; Phase 4 RTL and restoration certification remain independently schedulable.

The first production slice is recorded by
`release/changes/20260814-composition-runtime-correctness.json`. It adds retry-safe remember and
saveable activation, complete ordinary keyed-scope movement with duplicate rejection, fail-fast
public session termination, and Fragment View-lifecycle ownership. Earlier independent work added
transactional composition effects, `snapshotFlow`, deferred lazy session activation, and stronger
Android View reuse/release behavior.

Last verified: 2026-08-15.

Next action: implement the Phase 4 logical-edge public API slice, then run the general Activity-root
process-death certification. Do not begin a convenience API or conditional protocol while a
retained correctness defect remains open.

## Maven release changesets

- `release/changes/20260814-composition-runtime-correctness.json`

## Objective

Resolve migration-relevant defects that can cause lifecycle leaks, incomplete state persistence,
ambiguous runtime identity, incorrect RTL output, or broken Android ViewModel integration while
preserving ViewCompose's native Android View engine and transactional render boundaries.

This plan is complete without matching the number, names, or internal implementation of Compose
APIs. A difference is retained when ViewCompose's behavior is correct, documented, and better
aligned with Android View ownership.

## Re-audit conclusion

The original plan mixed four different kinds of work:

1. proven defects in current lifecycle or transaction behavior;
2. partially implemented behavior that now needs contract hardening;
3. valuable but independently schedulable migration capabilities; and
4. speculative parity work with no demonstrated product requirement.

Only the first two categories remain immediate work. Required Android correctness items stay in
this plan at a lower priority. Convenience features and speculative protocols are explicitly
deferred or rejected so they cannot silently become release blockers.

## Scope

Retained work may affect:

- `viewcompose-runtime` and `viewcompose-ui-foundation`: commit-callback failure recovery, restored
  saveable-state claims, keyed composition identity, duplicate-key policy, and abort behavior;
- `viewcompose-host-android` and `viewcompose-android`: public session terminal behavior, Fragment
  View-lifecycle ownership, and Android View release documentation;
- `viewcompose-navigation-core` and `viewcompose-navigation-android`: deep-link contract alignment,
  parent ViewModel Factory and `CreationExtras` inheritance, and cross-stack owner evidence;
- `viewcompose-ui-contract` and `viewcompose-renderer-android`: logical start/end edge APIs and
  direction-aware renderer resolution; and
- focused tests, owning module manuals, migration guides, compiled samples, localized public
  documentation, and release intent required by each implemented slice.

The plan does not own the developer preview locator, render-tree inspector, general diagnostics
UI, Material 3 fidelity, lazy-list physical reuse, or configuration-aware Android resources.

## Audited decisions

| Area | Current evidence | Necessity and priority | Decision |
| --- | --- | --- | --- |
| Fragment content lifecycle | The session follows the current Fragment View lifecycle, but content still receives the Fragment as `LocalLifecycleOwner`. | P0: proven owner mismatch can keep View-bound collection and effects active past `onDestroyView`. | Required correctness fix. |
| RenderSession terminal contract | Public host documentation says fail-fast, while Android runtime `render`, invalidation, and activation paths can silently return after disposal. | P0: caller misuse must be deterministic; queued internal callbacks still need race-safe no-op behavior. | Required contract and implementation fix. |
| Commit-callback and saveable registration failure | `SaveableHolder.onRemembered` can fail registering a provider after its lifecycle has become active; the frame records the failure but does not restore registration or retry state. | P0: proven transaction hole can leave a committed composition unsaveable. | Required architecture fix with focused failure recovery. |
| Ordinary keyed sibling identity | The 2026-08-14 lazy-ownership hard cut added keyed `RecomposeScope` movement and remember/saveable tests. Duplicate logical keys, effect/observation ownership, arbitrary edits, and abort restoration are not fully proved. | P0: behavior is already on the general runtime path, so an ambiguous contract is riskier than a missing feature. | Keep only after exhaustive hardening; otherwise revert movement and narrow the API contract. |
| Android View release | Renderer rollback, committed removal, session disposal, and final reuse-cache eviction release abandoned Views; public `AndroidViewNodeProps` wording still omits rollback candidates. | P1 and low cost: implementation is retained; public contract is stale. | Documentation/KDoc correction only unless tests find a behavior defect. |
| Unknown deep-link query keys | Resolver accepts extra keys and ignores them; some ViewCompose documentation previously suggested exact key equality. AndroidX Navigation also treats extraneous query parameters as irrelevant to URI matching. | P1 and low risk: current behavior is useful for tracking parameters and is not a routing security issue when unknown values never enter route arguments or launch decisions. | Retain permissive matching, add explicit non-influence tests, and correct documentation. Do not implement strict rejection by default. |
| Navigation parent Factory and extras | Entry and graph owners construct a `SavedStateViewModelFactory` and fresh extras from Application/route defaults rather than inheriting the intended parent owner. | P1: concrete DI and Android ViewModel ecosystem compatibility gap. | Required after P0 runtime/host work. |
| Cross-stack owner isolation | Core IDs are globally isolated and collisions are rejected, but same-route/same-key Android ViewModelStore isolation lacks a focused test. | P1 evidence: expected implementation is plausible but the public claim is under-certified. | Add focused evidence with the Factory/Extras slice; change behavior only if the test fails. |
| Logical start/end edges | ConstraintLayout and some collection padding are logical, while general padding, margin, offset, and inset selectors remain physical. | P1: real RTL correctness gap, independent of Compose parity. Android Views already expose native direction-aware primitives. | Required as a separate public-API slice after P0 work. Preserve existing physical APIs. |
| Non-navigation process restoration | Activity recreation is tested; real process-death evidence exists for Navigation but not for a general Activity root. | P1 certification: public saveable-state claims should include the platform-owned restoration path. | Add device evidence; production changes require a reproduced failure. |
| Atomic pop-to-existing-entry | No single rollback-safe command exists. | P2 feature: valuable and common, but not a defect in an existing command and not required for lifecycle/state correctness. | Move to a separately scheduled Navigation enhancement plan when product demand exists. |
| Snapshot and derived-state certification | Core snapshot, nested mutable snapshot, derived state, and `snapshotFlow` exist; some nested derived/dependency-switch/failure scenarios remain untested. | P2 evidence: no current failure demonstrates a release-blocking defect. | Add opportunistically with Runtime work; do not authorize a new optimization or collection API. |
| Nested WindowInsets consumption | Current modifiers apply physical padding and return the original `WindowInsetsCompat`; nested owners can therefore apply the same inset twice. | Conditional: a full Compose-style consumed-inset environment would duplicate platform machinery and complicate mixed View trees. | Retain and document explicit single-owner-per-type/edge behavior. Add a general protocol only after a reproduced mixed-tree requirement and a separate design review. |
| ViewTree-aware high-level host | Raw `renderInto` intentionally installs no owners; Activity and Fragment integrations cover first-party common hosts. | Deferred convenience API: no current leak proves another public host is necessary. | Require a concrete embedding use case before a separate plan. Keep raw `renderInto` explicit. |
| `BoxScope.matchParentSize` | No equivalent exists. | Deferred layout convenience: useful but not a correctness defect and unrelated to ownership convergence. | Schedule only from a layout product requirement; never alias it to `fillMaxSize`. |
| Hidden navigation session disposal | Hidden destinations retain state and Views while frame rendering is inactive. | Conditional optimization: current behavior preserves fast switching and page identity; no representative memory regression is recorded. | Keep current default. Require same-device memory evidence before an opt-in policy experiment. |

## External behavior decisions

The re-audit intentionally corrects two assumptions from the original plan:

1. AndroidX Navigation documents that extraneous query parameters do not affect URI matching.
   ViewCompose therefore retains extra-query tolerance while guaranteeing that undeclared values do
   not become route arguments, alter specificity, select a stack, or choose a launch mode.
2. Android View Insets consumption is a platform dispatch decision. Mixed View hierarchies require
   an explicit owner, and consuming at one View can affect descendants and, on older Android
   versions, sibling dispatch. ViewCompose will not create an implicit per-node Compose Insets
   runtime without a demonstrated requirement.

Authoritative references:

- [Android Navigation deep-link matching](https://developer.android.com/guide/navigation/design/deep-link)
- [Compose lifecycle and keyed identity](https://developer.android.com/develop/ui/compose/lifecycle)
- [Insets in mixed Views and Compose trees](https://developer.android.com/develop/ui/compose/system/insets-views-compose)
- [Edge-to-edge Insets handling for Android Views](https://developer.android.com/develop/ui/views/layout/edge-to-edge)

## Locked principles

### 1. Fix ViewCompose correctness, not API-count differences

A current lifecycle, transaction, state-identity, RTL, or Android owner defect can justify work.
An absent Compose-named convenience API cannot justify work by itself.

### 2. Reproduction precedes production change

Every retained defect begins with one focused failing test that demonstrates current behavior. Use
existing structured failure reports where sufficient. Do not build a broad diagnostics subsystem
as a prerequisite.

### 3. Preserve Android View and transaction ownership

Keep native View measurement, `LayoutParams`, ViewTree owners, `WindowInsetsCompat`, and explicit
RenderSession ownership. Preserve prepare, native-tree commit, composition/effect commit, rollback,
and permanent release boundaries.

### 4. Caller errors and internal races are different contracts

Public caller-initiated work after terminal disposal fails fast. Already queued internal frame or
invalidation callbacks may no-op after proving that they cannot render, retain work, or publish
effects.

### 5. Key moves one complete logical scope or does not move

If ordinary `key` movement remains, remember values, effects, observations, child scopes,
saveable paths, and rollback checkpoints move together. Duplicate effective keys under one parent
must fail before state can alias. A partial identity move is not an acceptable optimization.

### 6. Existing physical edges remain physical

Logical start/end APIs are additive. Existing left/right padding, margin, offset, and inset calls
must never silently change meaning under RTL.

## Phase 0: Focused reproductions and contract freeze — completed 2026-08-14

Phase 0 is a test-first gate, not a diagnostics feature phase. Add the smallest executable evidence
for:

1. Fragment content observing the current View lifecycle across `onCreateView`, `onDestroyView`, and
   View recreation without retaining the previous View generation;
2. public `RenderSession.render` and rendering-activation calls after disposal, separated from
   already queued internal invalidations;
3. restored saveable state whose provider registration fails during composition commit, including
   claim visibility in `performSave`, later retry, successful registration, and disposal; and
4. keyed siblings under insertion, deletion, head/tail movement, arbitrary reorder, duplicate keys,
   effect/observation ownership, saveable paths, prepared-composition abort, and committed removal.

Contract freeze:

- Fragment content lifecycle follows the Fragment View lifecycle. ViewModel and saved-state owners
  remain independently selected according to the documented host contract.
- `dispose()` remains idempotent; public work after disposal fails fast; queued internal work is
  safely ignored.
- A failed remember activation cannot leave an attached remembered object that is neither fully
  active nor safely retryable. Restored state remains saveable until provider ownership commits.
- Ordinary `key` movement is retained only as a complete logical-scope operation with duplicate-key
  rejection.

Phase 0 completes when every current behavior is reproduced, the intended contract is executable,
and the first production changeset is registered. No broad logging or inspector dependency is
allowed.

## Phase 1: Runtime transaction and key-identity hardening — completed 2026-08-14

### Commit-callback recovery

Design commit callback state so a throwing `RememberObserver.onRemembered` cannot leave a false
Active lifecycle. The solution must define:

- whether the failing candidate is compensated, abandoned, or retained for a deterministic retry;
- how already successful callbacks in the same commit remain exactly-once;
- how restored claims, provider entries, `performSave`, later recomposition, and disposal behave;
- how a frame already committed to the native tree reports recovery; and
- how multiple callback failures preserve the first cause and bounded structured diagnostics.

Do not special-case away the failure solely in `rememberSaveable` if the general RememberObserver
lifecycle can still enter an impossible state.

### Ordinary keyed identity

Audit the current `RecomposeScope` movement implementation rather than adding a second key system.
Retain it only if tests prove:

1. duplicate effective keys fail deterministically before any state or effect can alias;
2. remember, `RememberObserver`, DisposableEffect, coroutine effects, observations, children, and
   saveable namespaces remain bound to business identity;
3. insertion, deletion, reorder, and removal publish balanced lifecycle callbacks;
4. prepared composition abort restores ordering, observations, effects, and invalidation queues;
5. unchanged positional trees do not regress beyond repository performance policy; and
6. implementation remains local to composition identity rather than leaking into renderer-native
   reconciliation.

If any invariant cannot be made reliable, revert ordinary movement, keep the tests, and narrow
`key` KDoc to positional isolation. Lazy item identity remains owned by the collection Session
architecture and is not reverted with ordinary composition movement.

## Phase 2: Host lifecycle and terminal ownership — completed 2026-08-14

### Fragment View lifecycle

Use a two-stage Fragment binding that does not read `viewLifecycleOwner` before it exists and does
not publish the Fragment lifecycle as the content lifecycle:

1. create the returned root without retaining a previous View generation;
2. compose or bind content when the current View owner is available;
3. dispose exactly once at `onDestroyView` or earlier permanent teardown;
4. remove observers and references from the previous View generation; and
5. preserve the documented Fragment-scoped ViewModel and saved-state ownership independently.

Do not invent a proxy LifecycleOwner unless a separate ADR proves that it cannot diverge from
AndroidX lifecycle events.

### RenderSession terminal behavior

- keep disposal idempotent and best-effort across cleanup failures;
- fail fast for caller-initiated render or activation after disposal;
- keep scheduled invalidations and frame callbacks race-safe and non-rendering after disposal; and
- make public KDoc, host manual, migration guidance, and tests state the same boundary.

### Android View release wording

Correct canonical-English KDoc, owning module manuals, migration pages, compiled samples when
affected, and Chinese mirrors. `onRelease` is one-shot permanent-abandonment cleanup, including an
uncommitted rollback candidate, committed removal, final reuse-cache eviction, and session
disposal. Preserve the stronger transaction-aware behavior instead of imitating Compose callback
timing.

## Phase 3: Navigation contract and owner compatibility — completed 2026-08-15

### Extra query parameters

Retain current permissive matching and add tests proving unknown query values:

- do not enter `NavRoute.arguments`;
- do not increase match specificity or resolve an otherwise ambiguous destination;
- do not select a retained stack or launch mode; and
- remain inert even when their names resemble registered placeholders.

Update the navigation guide, migration matrix, and module manuals to remove exact-query-key wording.
An application that needs signed or exact URLs validates them before routing; a future strict mode
requires an explicit API and separate compatibility contract.

### Parent Factory and CreationExtras inheritance

For destination and graph owners:

1. capture the intended parent `HasDefaultViewModelProviderFactory` at host attachment;
2. inherit its Factory and immutable starting `CreationExtras`;
3. override only the current ViewModelStore owner, saved-state owner, and route/graph default args;
4. preserve unrelated application and DI extras across recreation and retained stacks; and
5. provide actionable failure diagnostics when required parent inputs cannot be represented.

Cover custom factories, Application extras, default args, `SavedStateHandle`, destination and graph
scope, process recreation, and same-route entries in separate retained stacks.

Atomic pop-to is not part of this phase. It requires a separate Navigation enhancement plan when
scheduled.

## Phase 4: RTL correctness and restoration certification

### Logical edges

Add narrowly scoped logical start/end forms for general padding, margin, direction-aware horizontal
offset, and inset edge selection. Resolve them from the captured `UiLayoutDirection`; preserve
existing physical forms. Cover LTR, RTL, runtime direction changes, delayed child Sessions,
applicable ConstraintLayout integration, and mixed logical/physical declarations.

This is a public API slice and must complete its Q-level, KDoc, sample, module manual, migration,
localized documentation, compatibility, and changeset requirements in the same change.

### General Activity-root restoration

Add one real process-death certification path for ordinary Activity-root `rememberSaveable` state.
Keep existing recreation tests. Change production code only if the device path reproduces state
loss, owner leakage, or an invalid persistence boundary.

Focused snapshot and derived-state tests may accompany this phase when they exercise an existing
contract, but they do not authorize new snapshot collections, mutation policies, or equal-result
suppression.

## Conditional and separately scheduled work

The following items do not block this plan:

| Candidate | Trigger required before a new plan | Retained boundary |
| --- | --- | --- |
| Atomic pop-to-existing-entry | A product navigation flow needs one rollback-safe inclusive/exclusive stack rewrite | One typed transactional command, not a `NavOptions` property bag |
| Nested WindowInsets protocol | A reproducible nested or mixed View tree cannot express correct ownership with one explicit owner per type/edge | AndroidX dispatch remains the platform source; no hidden second layout engine |
| ViewTree-aware high-level render host | A concrete non-Activity/non-Fragment embedding repeatedly leaks or misconfigures explicit ownership | Raw `renderInto` remains owner-free and explicitly disposable |
| `BoxScope.matchParentSize` | A real layout requires a child to fill final Box bounds without determining wrap-content Box size | Never alias to `fillMaxSize`; do not create a general measure policy |
| Hidden destination dispose policy | Same-device heap/RSS/View/effect evidence identifies hidden Sessions as a material cost | Current keep-alive default remains; any disposal policy is explicit opt-in |
| Direct NavigationEvent integration | Nested dispatch, forward events, official test fakes, or Preview inspection cannot use the Activity compatibility path | Do not make it a prerequisite without that use case |
| Arbitrary subtree ViewModel scope | A non-navigation scope has a concrete owner and cannot be modeled after parent Factory inheritance | No ownerless or process-global scope registry |

Untriggered candidates are decisions, not an unassigned backlog.

## Explicitly rejected work

This plan does not authorize:

- a Compose compiler plugin, restart groups, change masks, stability inference, strong skipping, or
  automatic lambda memoization;
- a general Compose-style `Layout`, `MeasurePolicy`, measurable/placeable, or intrinsic-measurement
  engine;
- application-defined `Modifier.Node` lifecycle or capability dispatch;
- reinterpretation of physical left/right APIs as logical start/end;
- tracked `UiLocal` lookup that turns every read into implicit observation;
- arbitrary object routes, Fragment/Activity destinations, general Navigation3 scenes, action/MIME
  matching, or a full `NavOptions` clone;
- weakening `AndroidView` prepare/commit/rollback/release behavior to match Compose timing;
- automatic hidden-page disposal without an explicit state-loss contract;
- a general nested Insets runtime without the conditional trigger above; or
- derived-state equal-result suppression or another performance optimization without a separate
  measured trigger.

`snapshotFlow` is no longer listed as rejected: it was implemented and documented by the
transactional-effects work. Snapshot collection types remain outside scope without a separate
product requirement.

## Validation matrix

| Area | Minimum evidence |
| --- | --- |
| Commit callback and saveable state | Failed registration, claim/save/retry/dispose, multiple callback failure, and committed-frame reporting tests |
| Ordinary key identity | Remember/effect/observation/saveable movement, duplicate keys, insert/remove/reorder, abort, and unchanged-tree performance |
| Fragment host | Robolectric lifecycle tests plus Fragment View recreation instrumentation or equivalent device evidence |
| RenderSession | Caller calls, queued callbacks, repeated disposal, cleanup failures, and terminal diagnostics tests |
| Android View release | Commit, rollback, removal, cross-key reuse, cache eviction, and session disposal exactly-once tests plus corrected public docs |
| Deep links | Extra, duplicate, malformed, and placeholder-like query tests plus guide/migration agreement |
| Navigation owners | Parent Factory/Extras, SavedStateHandle, graph/destination scope, recreation, and same-route cross-stack isolation |
| Logical edges | LTR/RTL and runtime direction tests plus representative renderer/device certification |
| Restoration | Activity recreation plus real non-navigation process-death certification |
| Documentation | KDoc/Javadoc, compiled samples, module manuals, migration pages, active guides, and Chinese mirrors for implemented public behavior |

Minimum gates for every implementation slice:

```bash
./gradlew verifyDocumentationStructure
./gradlew qaQuick
```

Run `./gradlew qaFull` and the applicable device procedure for Fragment lifecycle, process death,
Insets, IME, Predictive Back, or other platform-owned behavior. Record unavailable device evidence;
do not silently replace it with JVM-only assertions.

## Documentation and API quality impact

Every changed public or protected API assigns its Q level and completes the applicable lifecycle,
ownership, error, threading, persistence, layout-unit, performance, and compatibility fields.
Canonical-English KDoc/Javadoc, compiled Q3 samples, owning module documentation, migration updates,
and Chinese mirrors ship in the same slice.

Documentation routing:

- runtime transaction, key, remember, and saveable behavior updates Runtime/UI Foundation manuals,
  the effects or state architecture page, and state migration guidance;
- Fragment and terminal-session behavior updates Android aggregate and Host manuals plus host
  lifecycle architecture and migration pages;
- Android View release updates UI Contract, Renderer/Host manuals, interop guidance, and migration;
- deep-link or owner behavior updates both Navigation manuals, navigation guide, and migration;
- logical edges update UI Contract/Renderer manuals and layout migration guidance; and
- a costly cross-module ownership decision receives an ADR.

Every publication-relevant production change adds one immutable `release/changes/*.json` file and
lists it under exactly one active implementation plan. Dependency propagation remains release
planner output.

## Completion criteria

This plan is complete when:

1. every P0 defect has a focused reproduction, deterministic contract, implementation decision,
   and passing validation;
2. keyed movement is either fully retained with exhaustive evidence or reverted with narrowed KDoc;
3. Fragment content ownership, public Session terminal behavior, and Android View release wording
   agree across source, tests, and documentation;
4. deep-link extra-query behavior and Navigation parent Factory/Extras inheritance are explicit and
   verified;
5. logical start/end edges and general Activity-root process restoration are implemented/certified,
   or handed to separately indexed plans without duplicate ownership;
6. conditional candidates record their untriggered or handed-off decision and do not remain
   ambiguous backlog;
7. all plan-owned changesets and required quick/full/device gates pass; and
8. durable conclusions move into active documents and this file moves to `docs/archive/` before the
   affected Maven Central upload.

## Evidence ledger

| Date | Area | Evidence | Decision |
| --- | --- | --- | --- |
| 2026-08-05 | Initial planning | Migration documents, source contracts, and existing focused tests | Broad capability plan created; no implementation selected |
| 2026-08-14 | Full re-audit | Current source/tests, commits for transactional effects, lazy activation, and lazy three-layer ownership, plus AndroidX behavior references | Retain proven core defects; recognize partial keyed implementation; correct Deep Link policy; reject broad automatic Insets protocol; defer convenience APIs |
| 2026-08-14 | Existing test baseline | Runtime, UI Foundation, Host, Android aggregate, Navigation Core/Android, and Renderer unit tests | Seven relevant module test tasks pass; gaps remain uncovered behavior, not existing red tests |
| 2026-08-15 | Navigation contract and ownership | Core resolver tests, public-host destination/graph Factory tests, SavedStateHandle recreation, same-route retained-stack isolation, compiled sample, module/migration docs, `verifyDocumentationStructure`, and `qaQuick` | Unknown query values are contractually inert; destination and graph owners inherit parent Factory/extras while replacing child ownership inputs; Phase 3 complete |

## Decision history

- 2026-08-05: prioritize lifecycle, ownership, identity, RTL, Insets, and Navigation gaps over API
  count parity.
- 2026-08-14: narrow the active plan to current correctness and Android compatibility; diagnostics
  become focused supporting evidence only.
- 2026-08-14: treat commit-callback/saveable recovery, Fragment View ownership, Session terminal
  behavior, and already-live ordinary key movement as the immediate P0 set.
- 2026-08-14: retain permissive extra-query deep-link matching because it matches AndroidX behavior;
  unknown values must remain inert rather than being rejected by default.
- 2026-08-14: require additive logical edges for RTL correctness while keeping physical APIs stable.
- 2026-08-14: reject an untriggered general nested Insets runtime and defer atomic pop-to,
  ViewTree-aware hosting, `matchParentSize`, and hidden-session disposal to independent requirements.
- 2026-08-15: complete navigation owner compatibility by capturing parent provider defaults once
  per native host, recreating on parent-owner identity change, and keeping extra query values inert.
