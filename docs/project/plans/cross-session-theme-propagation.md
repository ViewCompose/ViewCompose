# Cross-Session Theme Propagation Plan

## Status

Completed; awaiting linked Maven changeset publication and release-time archival. Pull request
[#88](https://github.com/ViewCompose/ViewCompose/pull/88) merged the Demo and navigation
implementation into `main` at `7b1f5f5a`. Scoped automated and manual validation passed, and the
formerly unrelated 15-failure Demo device baseline was subsequently repaired in
[#90](https://github.com/ViewCompose/ViewCompose/pull/90); the complete repository `qaFull` gate is
green.

This plan owns the bounded correction for application theme changes that cross Activity root
sessions and retained `NavHost` destination sessions. It remains canonical English-only under the
documentation-governance policy. Durable contracts are recorded in the active theming, navigation,
architecture, and module documents. This file remains active only because the linked Maven
changeset has not yet been published.

Last verified: 2026-08-14.

Next action: include the linked immutable changeset in the intended Maven release and archive this
plan immediately before Maven Central upload.

## Maven release changesets

- `release/changes/20260811-cross-session-theme-navigation-refresh.json`

## Objective

Make one application theme choice converge predictably across every mounted ViewCompose surface
without merging their ownership:

1. separate Activity roots remain separate `RenderSession` instances that observe one
   application-owned theme source;
2. a theme change made in a secondary Activity is visible when the user returns to the first
   Activity;
3. a `NavHost` destination that becomes visible again renders with the latest inherited
   `UiLocalSnapshot` and destination content before its View is presented;
4. a failed retained-destination refresh leaves the previous navigation stack and visible scene
   intact; and
5. the Demo provides a reproducible secondary-Activity switch-and-return path for manual and
   connected-device verification.

## Scope

The implementation may change these areas:

- `app`: replace the plain Demo theme field with one observable application-session source, add a
  secondary Activity that changes Light/Dark/System mode, and expose stable verification tags and
  instructions;
- `viewcompose-navigation-android`: refresh retained destinations before they newly enter the
  visible pane set during programmatic navigation, stack selection, predictive Back, or adaptive
  pane changes;
- navigation unit and connected-device tests: cover latest Local propagation, retained owner and
  session identity, refresh failure rollback, and switch-and-return behavior;
- active theming and navigation documentation, the navigation Android module manual, their
  Simplified Chinese mirrors, and release intent for the published navigation artifact.

## Non-goals

This plan does not:

- merge Activity or destination render sessions;
- add a process-global framework-owned theme singleton or a public application state container;
- make every `UiLocal` read observable across session boundaries;
- recreate every retained navigation destination on a token-only theme change;
- change destination, graph, ViewModel, lifecycle, or saved-state ownership;
- change the design-system root-switching contract: replacing a design system or a
  constructor-sensitive root Context still replaces the root/session;
- introduce a second navigation invalidation API when the current `contentKey`, Local snapshot,
  and explicit destination render contracts can express the behavior; or
- treat Activity-local `Context.setTheme` as an application-wide theme mutation.

## Current baseline

Verified from the current worktree on 2026-08-11:

1. Every `ComponentActivity.setUiContent` call creates an independent root `RenderSession`. The
   Activity registry disposes it on `onDestroy` and does not suspend it on `onStop`.
2. `Material3ThemeTokenLifecycle` registers one `ComponentCallbacks` listener per active provider,
   rereads Android-backed tokens after configuration changes, and supports a host-scoped
   `Material3ThemeRefreshController` for imperative theme-resource changes.
3. The Demo stores its selected mode in the plain `DemoThemeSession.mode` field. Each Activity
   copies that field into a separate remembered `MutableState`, so changing one Activity does not
   invalidate another Activity's session.
4. Every committed `NavEntry` owns an independent child `RenderSession` and an opaque captured
   `UiLocalSnapshot`.
5. A committed parent `NavHost` update writes the latest Local snapshot and content closure into
   every destination environment. It synchronously renders visible destinations only when
   `contentKey` changes.
6. Hidden destination sessions suspend frame-driven rendering. Direct observable-state
   invalidations are retained until reactivation, but replacing an inherited Local snapshot does
   not itself invalidate the child composition.
7. Programmatic `Pop` and retained stack selection currently reuse the old destination View
   without a synchronous destination render. The unit test
   `pop reuses retained page without a synchronous destination refresh` protects that behavior.
8. Active navigation documentation instead states that a revealed page refreshes before Back
   commits. The implementation and durable contract are therefore inconsistent.
9. Focused Material theme lifecycle, Material Android host integration, navigation coordinator,
   destination-session, and public NavHost tests pass at this baseline.

## Required behavior

### Application theme ownership

1. The Demo theme mode is held by one application-session observable state object.
2. Every Activity root reads that same observable value instead of copying a plain field into an
   independent remembered state.
3. Writes are main-thread confined by the Demo UI path and equivalent mode assignments do not
   create redundant render work.
4. System mode continues to resolve the current root Context configuration. Explicit Light and
   Dark modes select deterministic Demo token snapshots.
5. The source remains Demo/application policy. No framework module depends on it.

### Activity roots

1. A secondary Activity may update the shared Demo theme source and finish.
2. The first Activity's independent root session observes the same source and converges without a
   direct reference to the second Activity or its session.
3. Activity-local Android style mutation remains local unless Android dispatches an application
   configuration change or that host explicitly refreshes/recreates its root.
4. The Demo displays the resolved mode and a stable theme-derived visual value so manual and
   connected-device checks can distinguish a real token refresh from a label-only update.

### Navigation destination refresh

1. Before a committed retained destination newly enters the visible pane set, the coordinator
   synchronously renders it with the latest `UiLocalSnapshot` and content closure.
2. Refresh applies to programmatic `Pop`, `PopStackHistory`, retained stack selection, predictive
   Back preview, and adaptive-pane expansion when those operations reveal retained destinations.
3. Newly prepared destinations are not rendered twice; their preparation already uses the latest
   environment.
4. Destinations that remain visible are not refreshed solely because another pane becomes visible.
   Ordinary parent changes continue to use `contentKey` to request a visible-page refresh.
5. A refresh occurs before visibility, transition start, lifecycle promotion to interactive state,
   or pure-stack publication can expose stale UI.
6. Refresh preserves destination container, render-session, lifecycle owner, ViewModel store, and
   saved-state identity.
7. Failure returns a structured `DestinationRefresh` failure, discards queued commands owned by
   the failed operation, and preserves the previously committed stack, scene, lifecycle, and
   visible Views.
8. Hidden destinations continue to receive updated render environments without eagerly rendering
   every retained page.

## Implementation phases

### Phase 1: Observable Demo theme source

1. Replace the plain `DemoThemeSession.mode` field with a stable observable state and a narrow
   setter/reset contract suitable for tests.
2. Make home and secondary Activity scaffolds read the shared state directly.
3. Keep system-dark resolution rooted in each Activity Context while explicit modes use the same
   deterministic token producer.
4. Add unit coverage for cross-reader invalidation and equivalent-write behavior where practical.

### Phase 2: Secondary-Activity verification surface

1. Add a focused Demo Activity opened from Settings or the catalog.
2. Show the originating/resolved mode, Light/Dark/System controls, a theme-derived background or
   semantic-color fact, and an explicit finish/back path.
3. Add manifest registration, catalog/navigation wiring, stable test tags, and concise manual
   verification steps.
4. Add connected-device coverage that changes mode in the secondary Activity, returns, and asserts
   the first Activity's mode and theme-derived visual state.

### Phase 3: Retained-destination pre-presentation refresh

1. Centralize calculation of retained entries newly entering the visible pane set.
2. Render those entries with the coordinator's latest environment before applying their new scene.
3. Integrate the operation into programmatic navigation, stack-history selection, predictive Back,
   and pane-strategy changes without duplicating preparation or render code.
4. Reuse the existing structured navigation failure model and keep refresh rollback before stack or
   scene publication.
5. Replace the test that protects stale retained-page reuse with success, identity-retention, and
   failure-rollback coverage.

### Phase 4: Documentation and release intent

1. Correct active navigation documentation so implementation, ordinary Back, predictive Back, and
   pane reveal paths describe one refresh contract.
2. Document application-scoped observable theme ownership and distinguish it from Activity-local
   imperative style refresh.
3. Update the `viewcompose-navigation-android` module manual for the behavior change.
4. Update every required Simplified Chinese mirror and reviewed translation fingerprint.
5. Add one immutable `release/changes/<unique>.json` entry classifying
   `viewcompose-navigation-android` as a fix; do not hand-write propagated dependencies.
6. Replace this plan's `- None.` changeset entry with the exact repository-relative file path.

## Validation

### Focused tests

- Demo/application unit tests for the observable theme source.
- Material 3 lifecycle and Android host integration tests to prevent configuration-refresh
  regression.
- `NavDestinationSessionStoreTest` for latest Local and content closure without session recreation.
- `TransactionalNavHostCoordinatorTest` for programmatic pop, stack-history/selection reveal,
  identity retention, and refresh-failure rollback.
- predictive-Back and adaptive-pane coordinator/driver tests for pre-presentation refresh.
- public `NavHost` tests proving `contentKey` refreshes current visible pages while retained reveal
  consumes the latest environment.

### Device and repository gates

```bash
./gradlew :viewcompose-navigation-android:testDebugUnitTest --no-configuration-cache
./gradlew :viewcompose-material3:testDebugUnitTest --no-configuration-cache
./gradlew :viewcompose-material3-android:testDebugUnitTest --no-configuration-cache
./gradlew verifyDocumentationStructure
./gradlew qaQuick
./gradlew qaFull
```

`qaFull` requires one unlocked online device. If the device gate cannot run, record the exact
preflight blocker and do not mark the plan complete.

### Manual Demo path

1. Launch the Demo home Activity in Light or Dark mode and record the visible mode plus background
   or semantic-color fact.
2. Open the secondary theme Activity.
3. select the opposite explicit mode and return.
4. Verify that the original Activity updates without being manually recreated.
5. Enter a `NavHost` flow, retain a lower destination, change the theme while another destination
   is visible, then navigate Back.
6. Verify that the revealed destination's first visible frame uses the new mode and that its local
   state, ViewModel, and navigation entry identity remain intact.

## Documentation and API impact

The navigation change modifies the high-risk `NavHost` behavioral contract without adding or
changing a public/protected signature. Its documentation classification is:

- public API Q level: `NavHost` remains Q3 because it crosses Android host/session boundaries,
  owns retained resources, participates in transactions, and exposes failure callbacks;
- applicable contract fields: behavior and ordering, inherited environment state, retained owner
  lifecycle/identity, callback timing, rollback/failure, Android host boundaries, and eager-render
  performance limits;
- canonical KDoc and the compiled `retainedDestinationThemeSample` describe the changed contract;
- behavior/default/lifecycle row: update navigation guide and owning module manual in both active
  locales;
- architecture consistency: correct current navigation/session statements that promise refresh
  before reveal;
- Demo-only theme source: no Maven release impact;
- navigation production source: one immutable fix Changeset is required.

## Completion criteria

All completion criteria are satisfied. The plan remains in the active directory only until its
release-time archival gate:

1. separate Activity sessions observe one Demo application theme source and the secondary-Activity
   return path passes manual and connected-device verification;
2. every retained-destination reveal path renders the latest Local snapshot before presentation;
3. refresh failure preserves the previous stack, visible scene, lifecycle state, and session/owner
   identities;
4. current visible destinations still refresh through changed `contentKey` without eagerly
   rendering all hidden entries;
5. focused unit tests, `verifyDocumentationStructure`, `qaQuick`, and `qaFull` pass;
6. navigation and theming documents, the module manual, Chinese mirrors, and translation
   fingerprints are current;
7. the immutable navigation fix Changeset is recorded here; and
8. durable conclusions move to active documentation before this plan moves to `docs/archive/` and
   both plan indexes are updated.

## Evidence ledger

| Date | Revision | Phase | Command or evidence | Result | Decision and next action |
| --- | --- | --- | --- | --- | --- |
| 2026-08-11 | Working tree | Planning baseline | CodeGraph impact analysis; source/document review; focused Material and navigation unit tests | Baseline confirmed; tests passed; implementation/document mismatch recorded | Land this plan, then begin Phase 1 |
| 2026-08-11 | `b21bcb4b` | Plan landing | Independent plan and plan-index commit | Passed `verifyDocumentationStructure` | Implement the application-owned Demo theme source |
| 2026-08-11 | `e2fecfa5` | Demo implementation | Shared observable theme state, secondary Activity, stable tags, connected-device test | App Kotlin, Android-test Kotlin, and unit-test compilation passed | Implement retained-destination pre-presentation refresh |
| 2026-08-11 | `7a9617a0` | Navigation implementation | Programmatic pop, stack selection, predictive Back, and adaptive-pane focused coordinator/driver tests | Focused test group passed | Complete documentation, release intent, and repository gates |
| 2026-08-11 | `7a9617a0` | Repository validation | Full navigation, Material theme, and app unit tests; `verifyDocumentationStructure`; `verifyViewComposeReleaseIntent`; `qaQuick` | All passed; compiled Q3 sample and locale freshness verified | Run connected-device validation |
| 2026-08-11 | `7a9617a0` | Connected-device validation | `qaFull` on unlocked Android 13 SM-G991B; isolated `secondaryActivityThemeSwitch_refreshesOriginalActivitySession` rerun | New cross-Activity test passed twice, including the 95-test run; aggregate `qaFull` failed because 15 unrelated UI/device tests outside the changed paths failed | Keep plan active and record the aggregate device-suite failures for separate triage |
| 2026-08-12 | User-confirmed build | Manual validation | Switch theme in the secondary Activity, return to the primary Activity, and inspect the retained navigation page | Passed; both Activity roots and the revealed retained destination showed the selected theme | Publish the pull request and track it through merge to `main` |
| 2026-08-12 | `7b1f5f5a` | Main integration | GitHub pull request #88 | Merged into `main`; the Demo source, retained-destination refresh, durable documentation, tests, and immutable changeset landed together | Retain the plan until its Maven release-time archival gate |
| 2026-08-12 | `4b233adc` | Aggregate device follow-up | Complete repository `qaFull` recorded by the transactional-effect and Android-resource closeout | Passed 1,756 tasks, including all 96 Demo device tests, one Counter test, and two Tutorials tests | The unrelated 15-failure baseline is closed; no implementation or validation work remains in this plan |

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-08-11 | Keep every Activity and destination session independently owned | Theme convergence requires shared observable data, not shared composition or View ownership |
| 2026-08-11 | Keep the application theme source outside framework modules | Theme-mode persistence and product policy belong to the application; the framework only propagates resolved values |
| 2026-08-11 | Refresh only destinations newly becoming visible | It prevents stale first frames without eagerly rendering every retained page after each parent update |
| 2026-08-11 | Refresh before stack/scene publication | A failed new-theme render must not expose a half-updated destination or commit navigation that cannot be presented |
| 2026-08-11 | Preserve `contentKey` for visible-page parent dependency changes | It remains the explicit invalidation key for non-observable parent values and complements retained-page reveal refresh |
