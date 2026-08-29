---
draft: true
schema_version: 2
document_id: plan.navigation-lifecycle-scene-evolution
doc_type: plan
owner:
  kind: module
  id: viewcompose-navigation-android
version_lane: version-agnostic
capability_ids:
  - lifecycle.effects
  - lifecycle.flow-collection
  - lifecycle.owner-boundaries
  - navigation.destination-context
  - navigation.host
  - navigation.presentation-retention
  - navigation.scene-projection
artifact_ids:
  - viewcompose-lifecycle-androidx
  - viewcompose-navigation-android
  - viewcompose-navigation-core
sample_ids:
  - module.navigation-android-destination-context
  - module.navigation-android-host-construction
  - module.navigation-android-presentation-retention
  - module.navigation-core-execution-plan
  - module.navigation-core-scene-projection
status: active
scope: Evolve navigation around one scene-derived destination lifecycle, separate retained entry ownership from native presentation lifetime, and stabilize one host-independent Lifecycle DSL consumption surface.
non_goals:
  - Replace AndroidX LifecycleOwner with a ViewCompose-specific public lifecycle type.
  - Add Activity-, Fragment-, and navigation-specific copies of the same Lifecycle DSL APIs.
  - Preserve defective Alpha transition, retention, or compatibility behavior through aliases, flags, or dual paths.
  - Copy every Navigation 2, Navigation 3, Compose, or Flutter API name without a ViewCompose use case.
  - Reopen the retained ViewModel scoped-owner design frozen by ADR-0023 and its completed implementation plan.
baseline: The 2026-08-29 audit found strong transactional navigation and destination ownership with 201 passing JVM or Robolectric tests, but entering destinations reach RESUMED before transition settlement, popped exiting entries remain STARTED, hidden destinations retain complete RenderSessions and native View trees without a bounded policy, general scene and overlay semantics are absent, and no navigation device or line/branch coverage gate exists.
ordered_work:
  - Freeze Lifecycle DSL, scene, entry, presentation, focus, transition, and ownership terminology and capability dispositions before production changes.
  - Stabilize one nearest-owner Lifecycle consumption surface for Activity, Fragment, navigation, graph, and custom-container boundaries.
  - Replace visible and interactive set projection with explicit scene-level and entry-level lifecycle caps in navigation core.
  - Correct committed, predictive, overlay, pane, and popped-exit lifecycle ordering in the Android host.
  - Separate retained entry ownership from RenderSession and native View-tree retention with explicit bounded presentation policies.
  - Add one stable destination context for navigation-specific presentation semantics without duplicating LifecycleOwner.
  - Converge navigation events, scene projection, lifecycle plans, presentation operations, focus, and transitions on one reducer-produced plan.
  - Complete unit, state-machine, device, restoration, memory, performance, public API, documentation, and deletion-guard evidence.
completion:
  - Activity, Fragment, navigation destination, graph, and custom-container content consume one Lifecycle DSL API family and always resolve the nearest intended owner.
  - Effective destination lifecycle is derived from host, scene, and entry caps; transition and overlay matrices match the accepted contract with no premature RESUMED state.
  - Retained entry state survives optional View-tree disposal and recreation, and every retention policy has bounded cleanup, restoration, and memory evidence.
  - Navigation-specific presentation state has one stable per-entry source, is not inferred from AndroidX Lifecycle, and cannot schedule frame-rate recomposition by default.
  - All affected capability, API, sample, module, architecture, migration, release-intent, documentation, unit, device, and performance gates pass before archival.
last_verified: 2026-08-29
next_action: Execute Phase 7 capability-gap closure, coverage, leak, memory, performance, and broader device evidence.
maven_release_changesets:
  - release/changes/20260829-navigation-destination-context.json
  - release/changes/20260829-navigation-execution-reducer.json
  - release/changes/20260829-navigation-presentation-retention.json
  - release/changes/20260829-navigation-scene-projection.json
  - release/changes/20260829-navigation-transition-lifecycle.json
---

# Navigation Lifecycle and Scene Evolution Plan

## Status

Active. The architecture and test audit, Phase 0 contract freeze, Phase 1 Lifecycle DSL
stabilization, Phase 2 Core scene projection, Phase 3 Android transition lifecycle correction,
Phase 4 entry/presentation lifetime separation, and Phase 5 stable destination context are
complete. Phase 6 reducer/executor convergence and acceptance are complete; Phase 7 is next.

Last verified: 2026-08-29.

Next action: execute Phase 7 capability-gap closure, coverage, leak, memory, performance, and
broader device evidence.

## Maven release changesets

- `release/changes/20260829-navigation-destination-context.json`
- `release/changes/20260829-navigation-execution-reducer.json`
- `release/changes/20260829-navigation-presentation-retention.json`
- `release/changes/20260829-navigation-scene-projection.json`
- `release/changes/20260829-navigation-transition-lifecycle.json`

## Release intent rationale

Phase 2 hard-cuts the published Navigation Core planner from visible/interactive ID sets to the
semantic `NavScene` input and adds the scene model plus its compiled sample. Its immutable Changeset
classifies Navigation Core as breaking. Navigation Android is explicitly ignored in that Changeset
because its internal adapter preserves the existing settled ownership policy; Phase 3 owns the
separate Android transition-behavior change. Release planning derives reverse-dependency
propagation.

Phase 3 classifies Navigation Android as a fix because public declarations remain unchanged while
ordinary and predictive destination-owner timing now follows the already-published scene contract.

Phase 5 classifies Navigation Android as a feature because it adds the public
`LocalNavDestinationContext`, `NavDestinationContext`, and `NavDestinationPresentation` names
without changing an existing public signature. The Android presentation name is a source alias for
the Core scene entry and therefore does not publish or duplicate a second artifact's value model.
The app debug host and instrumentation are unpublished evidence. Governance V2 detects no changed
application-facing declaration, so its one-impact-per-detected-change rule admits no public API
impact record; the Changeset and owning architecture, module, migration, and plan documents record
the behavior correction.

Phase 4 classifies Navigation Android as breaking because the Alpha `NavHost` declaration gains the
explicit `presentationRetentionPolicy` input and its previous implicit retain-all behavior is
hard-cut to the bounded `DisposeWhenHidden` default. The new Q3
`NavPresentationRetentionPolicy` capability, compiled sample, public-host impact disposition, and
immutable Changeset travel with the same slice. The app debug host and instrumentation remain
unpublished acceptance evidence; release planning derives reverse-dependency propagation.

Phase 6 classifies Navigation Core as a feature because it publishes the pure Q3
`NavExecutionReducer` and immutable `NavExecutionPlan` contract. Navigation Android is a fix:
public Android declarations are unchanged, while its coordinator now executes that plan as the
single lifecycle, presentation, interaction, Back, rollback, and cleanup policy source. Governance
V2 detects no application-facing DSL/component/host entry in this pure Core model, so its strict
one-impact-per-detected-change rule admits no immutable Core impact record. The device-discovered
Android host fix clarifies `NavHost` construction identity in public KDoc and therefore carries one
`navigation.host` impact plus a compiled custom-overlay construction sample. The capability
inventory, canonical KDoc, compiled samples, handwritten owners, generated Reference, impact, and
Changeset provide the structured disposition without claiming `No documentation impact`.

## Objective

Make ViewCompose navigation a scene-driven native-View navigation system whose destination owners,
rendered presentations, lifecycle-aware DSL work, focus, and transitions remain consistent through
forward navigation, pop, predictive Back, overlays, multiple panes, retained stacks, process
restoration, and render failure.

The finished design must provide:

1. one standard Lifecycle DSL consumption surface independent of whether content is hosted by an
   Activity, Fragment, navigation destination, graph, or explicit custom container;
2. one pure lifecycle projection in which the effective entry state is the minimum of host, scene,
   and entry caps;
3. a stable navigation destination context for visibility, interaction, transition phase, and pane
   role that does not overload AndroidX Lifecycle with navigation-only states;
4. independent logical-entry and native-presentation lifetimes so state can survive deliberate View
   disposal without retaining an unbounded hierarchy; and
5. one reducer-produced navigation plan that keeps stack, scene, lifecycle, presentation, focus,
   and transition effects on the same transactional boundary.

Equivalent capability and observable behavior are the goal. API-name parity with Compose,
Navigation 3, or Flutter is not required.

## Baseline and audit interpretation

### Accepted strengths

- Navigation core is platform-neutral and owns immutable stack snapshots, strict graph and deep-link
  validation, multi-stack state, two-phase prepared transactions, lifecycle planning, and pane
  projection.
- The Android host renders a candidate before committing core state and rolls back candidate owner,
  View, saveable state, and transaction state on pre-commit failure.
- Every destination and graph instance has a stable lifecycle, ViewModelStore, SavedStateRegistry,
  default arguments, and ViewCompose saveable-state namespace.
- Destination rendering installs the entry owner as the nearest `LocalLifecycleOwner`, saved-state
  owner, ViewModelStore owner, and saveable-state registry. Graph scopes deliberately replace the
  same four locals.
- Activity and Fragment hosts already install their intended Lifecycle owner, while delayed child
  sessions capture the nearest local rather than looking up a later global Activity.
- The generic lifecycle module already supplies `LocalLifecycleOwner`, `ProvideLifecycleOwner`,
  `Lifecycle.currentStateAsState()`, `LifecycleStartEffect`, `LifecycleResumeEffect`, and
  lifecycle-aware Flow collection.
- Main-thread serialization, retained identity, SavedStateHandle restoration, graph lifetime,
  predictive Back, motion, shared elements, and adaptive panes have focused JVM or Robolectric
  evidence.

### Defects and capability gaps

| Severity | Finding | Required disposition |
| --- | --- | --- |
| Critical | The active architecture says only an interactive settled destination is `RESUMED`, but ordinary push and pop tests require the target to be `RESUMED` before visual transition completion. | Cap every active transition scene at `STARTED`; promote an interactive entry only after terminal settlement. Replace the contradicting tests and update the active architecture in the same implementation slice. |
| High | A popped outgoing entry remains `STARTED` while its exit animation runs even though it is no longer in the committed back stack. | Add an entry-level cap and keep popped exiting entries at `CREATED` until presentation disposal and owner destruction complete. |
| High | Hidden destinations retain their complete child RenderSession, native View tree, composition scope, and effects; only frame rendering is disabled and the root View becomes `GONE`. There is no bounded policy across deep or multiple stacks. | Separate logical entry state from presentation lifetime and provide explicit dispose, retain, and bounded-retention policies with a safe default. |
| High | Visible and interactive ID sets cannot express nested overlays, partially covered scenes, transition roles, focus ownership, or why a destination is capped. | Introduce explicit scene and entry projections and make lifecycle, presentation, focus, and transition operations derive from them. |
| Medium | Lifecycle alone cannot distinguish hidden, covered, entering, settled, exiting, pane role, or predictive preview, and no stable destination-local presentation context exists. | Add one navigation-owned destination context while keeping Lifecycle APIs host-neutral and authoritative for resource thresholds. |
| Medium | The 1,364-line transaction coordinator and 1,201-line View transition driver coordinate overlapping state through command sequencing. | Move decision-making to a pure reducer and typed execution plan; split code only along state ownership and effect boundaries. |
| Medium | Routes and general scenes remain narrower than mature navigation: route values are closed rather than compiler-serialized, scenes are fixed rather than strategy-driven, and direct NavigationEvent integration is absent. | Close only use-case-backed gaps after lifecycle and presentation correctness; record intentional differences rather than copying surface area. |
| Medium | Navigation has no module-owned device suite or measurable line and branch coverage. Some green tests encode the rejected lifecycle behavior. | Add corrected lifecycle matrices, model/state-machine tests, real host/device tests, coverage reporting, and memory/performance evidence. |

The executable baseline command succeeded:

```text
./gradlew :viewcompose-navigation-core:test :viewcompose-navigation-android:testDebugUnitTest --console=plain
```

The current reports contain 53 core tests and 148 Android tests, 201 total, with zero failures or
errors. Core multi-stack stress executes 64 deterministic seeds with 2,000 operations per seed.
Conclusion: **mixed**. Stack, transaction, owner, and restoration breadth is strong, but green tests
do not establish correct transition lifecycle, bounded retention, device behavior, memory safety,
or branch coverage. The Gradle tasks were current rather than force-rerun; later acceptance must
record exact source revision and fresh execution context.

## Architecture invariants

### One Lifecycle DSL consumption surface

Application content uses the same APIs in every host:

| Consumer need | Canonical API |
| --- | --- |
| Optional nearest owner | `LocalLifecycleOwner.current` |
| Observable lifecycle state for declarative UI | `Lifecycle.currentStateAsState()` |
| Paired work while started | `LifecycleStartEffect(key) { ... }` |
| Paired work while resumed | `LifecycleResumeEffect(key) { ... }` |
| Lifecycle-gated Flow collection | `collectAsStateWithLifecycle(...)` |
| Explicit custom boundary | `ProvideLifecycleOwner(owner) { ... }` |

Do not add `ActivityLifecycleEffect`, `FragmentLifecycleEffect`, `NavPageLifecycleEffect`, a public
`PageLifecycleOwner`, or a navigation-local copy of generic Flow/effect APIs. Activity and Fragment
hosts pass through system-driven owners; navigation and other framework-owned virtual surfaces
create capped owners. Consumers resolve only the nearest boundary.

Reading `lifecycle.currentState` directly during declaration is a snapshot, not observation, and
must not control side-effect setup. Observable UI uses `currentStateAsState()`. Resource work uses
the paired Lifecycle effects. Flow collection uses `repeatOnLifecycle` through the existing API.
Observation begins after commit, reconciles any intervening state change, and is removed when the
call leaves composition.

### Separate Lifecycle from navigation presentation

AndroidX Lifecycle remains the resource-threshold contract. Navigation presentation separately
answers whether the entry is hidden, visible, covered, entering, settled, exiting, interactive,
or assigned to a pane.

A destination-local contract may have a shape equivalent to the following, but final names are
frozen only after Phase 0 capability review:

```text
NavDestinationContext
  entry: NavEntry
  presentation: observable NavDestinationPresentation

NavDestinationPresentation
  visibility: Hidden | Visible | Covered
  interaction: Interactive | NonInteractive
  transition: Prepared | Entering | Settled | Exiting | PredictivePreview
  paneRole: Single | Primary | Secondary | Tertiary | Overlay
```

The destination-context holder is created by stable entry identity, survives optional presentation
disposal and recreation, and is provided through the destination's local environment. A local
snapshot captures the holder, not a one-time enum value. No API looks up a global "current page",
because nested hosts, panes, and overlays can expose more than one current destination.

Frame-rate transition progress is not part of the default observable presentation state. A page
that explicitly animates from predictive or transition progress uses a dedicated motion API so
ordinary destination content cannot accidentally recompose every frame.

### Scene and entry lifecycle projection

The effective Android lifecycle is derived rather than commanded ad hoc:

```text
effective entry lifecycle = min(host cap, scene cap, entry cap)
```

The accepted target matrix is:

| Destination condition | Scene cap | Entry cap | Effective target before host cap |
| --- | --- | --- | --- |
| Prepared candidate before commit | `CREATED` | `CREATED` | `CREATED` |
| Retained hidden entry | `CREATED` | `RESUMED` | `CREATED` |
| Settled visible and interactive entry | `RESUMED` | `RESUMED` | `RESUMED` |
| Active entry participating in a forward or back transition | `STARTED` | `RESUMED` | `STARTED` |
| Active entry covered by an overlay | `STARTED` | `RESUMED` | `STARTED` |
| Top settled overlay | `RESUMED` | `RESUMED` | `RESUMED` |
| Underlying covered overlay | `STARTED` | `RESUMED` | `STARTED` |
| Popped entry still animating out | at most `STARTED` | `CREATED` | `CREATED` |
| Permanently removed entry | n/a | `DESTROYED` | `DESTROYED` |

Multiple entries may be `RESUMED` in a settled multi-pane scene only when they are simultaneously
interactive by accepted pane policy. Graph-owner targets derive from their retained, visible, and
interactive descendants while preserving child-down and parent-up ordering.

### Entry and presentation lifetime

One navigation entry record owns logical state independently of a rendered View:

```text
NavEntryRecord
  entry identity and arguments
  destination or graph owner
  saved and saveable state
  stable destination-context holder
  optional RenderSession and native View presentation
```

Entry retention preserves owner identity, ViewModel and saved-state namespaces, and the destination
context. Presentation retention is a separate policy. The target policy family must support:

- dispose when fully hidden, preserving restorable state;
- explicit retention for application-proven expensive surfaces; and
- bounded least-recently-used retention with deterministic eviction and no entry-owner destruction.

The safe default is selected only after device memory and restoration evidence. No policy may be
unbounded merely because multiple controller stacks retain logical entries. Temporary presentation
absence must not clear ViewModels or mark the entry `DESTROYED`; permanent entry removal must dispose
the presentation before owner destruction.

### One reducer-produced navigation plan

Every command or host event produces one immutable execution plan containing:

- committed or candidate stack mutation;
- scene and layer projection;
- entry and graph lifecycle targets;
- presentation create, refresh, retain, evict, or dispose operations;
- focus, input, accessibility, and system-Back ownership;
- visual transition or predictive-preview effects; and
- rollback or terminal cleanup actions.

The reducer is pure and platform-neutral where its inputs and outputs are navigation concepts. The
Android executor owns LifecycleRegistry, native Views, focus, back dispatch, and animations. A
failed pre-commit execution publishes no new committed stack or destination context. Post-commit
failure follows one documented terminal recovery path rather than reconstructing a hidden second
state machine.

## Module and ownership boundaries

| Module | Responsibility after completion |
| --- | --- |
| `viewcompose-lifecycle-androidx` | Host-neutral AndroidX Lifecycle locals, observable state, paired effects, Flow collection, and reusable low-level lifecycle observation mechanics. |
| `viewcompose-navigation-core` | Entry presence, scenes, overlays, panes, transition roles, lifecycle caps, and pure navigation plan projection without Android or View types. |
| `viewcompose-navigation-android` | Destination and graph owners, Android lifecycle driving, destination-context local, RenderSession policy, View hierarchy, focus, Back, and transition execution. |
| `viewcompose-android` | Activity and Fragment host-owner installation; it does not depend on navigation or create virtual destination owners. |

The individual `ProvideLifecycleOwner`, saved-state, ViewModelStore, and saveable-state locals remain
the shared composition mechanisms. An internal owner-environment helper may remove repeated nesting,
but a new aggregate public owner API is not justified solely to reduce implementation lines.

## Coordination with retained ViewModel architecture

The completed AndroidX ViewModel evolution and
[ADR-0023](../../architecture/decisions/0023-retained-viewmodel-scope-ownership.md) own the general
retained scoped-owner facility and ViewModelStore allocation policy. Navigation already consumes
that facility and allocates no parallel child store. This plan owns navigation entry lifecycle,
scene projection, destination context, RenderSession lifetime, and transition semantics.

The frozen boundary is:

1. Navigation must not introduce a parallel retained-store mechanism or reopen the completed
   provider design under a presentation-policy name.
2. Navigation lifecycle and scene-core work may proceed without changing ViewModelStore allocation.
3. The shared scoped-owner facility has landed and Navigation consumes it; presentation disposal
   and recreation must preserve that entry lease while changing only presentation lifetime.
4. A pull request touching shared owner/store files must attribute any provider-contract change to
   a new ViewModel capability impact rather than silently expanding this navigation plan.
5. Permanent entry removal disposes presentation before closing the lease and requesting terminal
   clear; temporary presentation absence closes neither logical ownership nor retained state.

## Hard-cut policy

The navigation artifacts are Alpha. Confirmed defective or redundant behavior is removed in the
same slice that establishes its replacement:

1. Do not preserve premature `RESUMED`, popped-`STARTED`, or unbounded hidden-session behavior behind
   compatibility flags.
2. Do not run old visible/interactive-set projection and new scene projection in parallel.
3. Do not expose both a navigation lifecycle façade and standard AndroidX Lifecycle APIs.
4. Do not expose transition progress through a general destination state that invalidates every
   page at frame rate.
5. Do not tie entry destruction to View disposal or View retention to ViewModel retention.
6. Do not add aliases, dual reads/writes, feature flags, or fallback reconstruction for removed
   Alpha contracts unless an accepted release policy explicitly requires them.
7. Remove obsolete code, tests, samples, prose, capability records, and diagnostics in the same
   hard cut.
8. Every public or protected API slice resolves capability impact, Q level, contract fields,
   canonical-English KDoc/Javadoc, compiled Q3 sample, module documentation, locale mirrors, and
   immutable Changeset before merge.

## Test and evidence plan

### Generic Lifecycle DSL contracts

- Activity, Fragment View, destination, graph, nested destination, preview, and explicit custom
  container resolve the nearest intended owner using the same DSL component.
- Owner identity replacement detaches the old lifecycle before observing the new one.
- `currentStateAsState()` is stable at its call site, reconciles a state change between declaration
  and commit, and stops after disposal or failed composition.
- start/resume effects enter once, clean up once, serialize rapid changes, detach on key or owner
  replacement, and never publish work from a failed frame.
- Flow collection starts, cancels, and restarts without overlapping collectors and retains the last
  accepted value while inactive.

### Navigation lifecycle and state-machine contracts

- Push, pop, replace, reset, stack selection, deep link, transition cancel, transition completion,
  predictive start/progress/cancel/commit, and host lifecycle changes satisfy the complete target
  matrix.
- Incoming and outgoing active entries remain `STARTED` until settled; popped exiting entries remain
  `CREATED`; a target reaches `RESUMED` exactly once after settlement.
- Overlay, nested overlay, pane expansion/collapse, and multiple interactive panes derive lifecycle,
  focus, and layer order from one scene.
- Downward transitions run child before parent; upward transitions run parent before child; no
  destroyed owner resurrects.
- Re-entrant commands and terminal transition callbacks cannot create two committed stacks, two
  active plans, or inconsistent context/lifecycle state.
- A model-based or property test exercises Android coordinator event interleavings in addition to
  the existing pure-core deterministic stress test.

### Presentation and restoration contracts

- Hidden disposal preserves entry owner, ViewModel, SavedStateRegistry, rememberSaveable values,
  route arguments, destination context, and graph scope across presentation recreation.
- Explicit retain and bounded-LRU policies preserve and evict exactly the intended sessions; stack
  reorder and multiple stacks cannot bypass the bound.
- Permanent pop disposes View and session work before owner destruction and clears ViewModels once.
- Configuration recreation and process restoration recreate no live View, effect, animation, or
  candidate transaction, but restore every committed logical entry and accepted saveable value.
- Render, owner, lifecycle observer, focus, animation, and disposal failures preserve the documented
  pre-commit rollback or post-commit terminal state.

### Device, memory, and performance evidence

- A real Activity host covers configuration change, background/foreground, multi-window visibility,
  process recreation, nested Fragment host, predictive Back, overlay focus, and IME interaction.
- A deep single stack and retained multiple stacks record absolute live View count, RenderSession
  count, Java/native heap, recreation time, and settled frame cost for each retention policy.
- Evidence records comparison context, absolute values, normalized change, conclusion, limitations,
  and next action in the owning active documentation; raw reports do not close a phase.
- Leak checks prove that popped entries, evicted presentations, transition overlays, observers,
  coroutines, ViewModels, and saved-state providers become unreachable at their terminal boundary.

### Structural, API, and documentation guards

- No per-host Lifecycle DSL copy, public PageLifecycleOwner, legacy visible/interactive projection,
  duplicate presentation policy, unbounded default cache, or compatibility alias remains.
- Public destination-context and policy APIs have stable capability IDs, Q levels, complete contract
  fields, canonical KDoc, compiled samples, and API/reference ownership.
- Active architecture, module, guide, migration, and locale documents describe the accepted behavior
  and do not claim parity unsupported by device or performance evidence.
- Coverage reports include line and branch results for the lifecycle projection, reducer,
  coordinator/executor, owner store, session policy, and public NavHost paths.

## Execution phases

| Phase | Scope | Completion gate | Status |
| --- | --- | --- | --- |
| 0 | Contract, capability, ownership, and ADR freeze | State matrices, hard cuts, Q3/API impacts, ViewModel-plan boundary, and ADR disposition accepted | Complete |
| 1 | Generic Lifecycle DSL stabilization | One consumption surface passes host, race, replacement, failure, effect, and Flow contracts | Complete |
| 2 | Core scene and lifecycle projection | Pure scene/entry caps and model tests replace visible/interactive-only decisions | Complete |
| 3 | Android transition lifecycle correction | Ordinary, predictive, and pane transitions match the matrix; unsupported general overlay execution has an explicit disposition | Complete |
| 4 | Entry/presentation lifetime separation | Dispose, retain, and bounded policies pass restoration, cleanup, and memory gates | Complete |
| 5 | Destination context DSL | Stable per-entry context, compiled Q3 sample, and non-frame-rate observation contracts pass | Complete |
| 6 | Reducer and executor convergence | One typed plan owns stack, scene, lifecycle, presentation, focus, and effects; obsolete paths are absent | Complete |
| 7 | Capability and test closure | Typed routes and ecosystem gaps have accepted dispositions; unit, device, coverage, memory, and performance gates pass | Next |
| 8 | Documentation, release, and archive | Durable conclusions are current, Changesets are released or accepted, all gates pass, and the plan is archived | Pending |

### Phase 0: freeze contracts before implementation

1. Accept or revise every lifecycle, presentation, and module-ownership invariant in this plan.
2. Resolve stable capability identities for destination context, presentation retention, general
   scenes, and any changed Lifecycle API. Reuse existing capability IDs only when the user decision
   remains the same.
3. Assign Q levels and contract fields, identify breaking removals, and record structured capability
   impacts before public/protected declarations change.
4. Decide whether scene/reducer ownership and entry/presentation separation require an ADR. Record
   the decision before implementation; temporary sequencing remains in this plan.
5. Freeze the coordination boundary with the active AndroidX ViewModel plan and assign overlapping
   source files to one implementation slice at a time.
6. Replace this plan's `- None.` release declaration when the first published production slice
   begins.

#### Phase 0 acceptance

The lifecycle, presentation, module-ownership, hard-cut, and test invariants in this plan are
accepted without revision. [ADR-0024](../../architecture/decisions/0024-scene-derived-navigation-lifecycle-and-presentation-ownership.md)
records the cross-module decision: effective entry lifecycle is `min(host cap, scene cap, entry
cap)`; coarse presentation is separate from Lifecycle; logical entry ownership is independent of
an optional native presentation; and one reducer-produced plan owns navigation decisions.

Three future capability identities are frozen: `navigation.scene-projection` in Navigation Core,
plus `navigation.presentation-retention` and `navigation.destination-context` in Navigation
Android. All three are Q3. The ADR assigns their applicable behavior, input/output, state,
lifecycle, concurrency, failure, Android, performance, and compatibility fields. Capability and
impact records will land with the first compiled declarations because Governance V2 records current
inventory and forbids pre-created impacts.

The existing `lifecycle.owner-boundaries`, `lifecycle.effects`, and
`lifecycle.flow-collection` identities remain the only generic Lifecycle DSL capabilities. Phase 1
adds no host-specific façade and adds no API unless its executable host/race matrix proves a real
expressiveness gap. The completed ViewModel plan and ADR-0023 remain authoritative for retained
stores; presentation disposal must preserve the already-shared navigation entry lease.

The Phase 0 repository gate ran `qaQuick` against main revision `69d7e533` and completed all 2,268
actionable tasks: 171 executed and 2,097 were up to date. The integration result is **no material
change**: no production declaration or compiled sample changed, and the gate validates document,
dependency, publication, API, and existing test consistency rather than a new runtime behavior.
Its mixed cache state is not performance evidence.

Conclusion: **improved** architectural certainty. Phase 0 changes no production declaration,
publishing input, or compiled sample and therefore owns no Maven Changeset. Its evidence is design
and governance acceptance rather than runtime validation, so transition correctness, lifecycle
races, retention defaults, device memory, leaks, and performance remain **inconclusive**. Next
action: execute Phase 1's fresh host-neutral Lifecycle DSL matrix.

### Phase 1: stabilize Lifecycle DSL consumption

1. Characterize the existing nearest-owner local, observable lifecycle state, effects, and Flow
   collection across Activity, Fragment, navigation, graph, preview, and custom hosts.
2. Close declaration-to-commit, owner replacement, rapid transition, failed frame, and disposal
   gaps without adding host-specific APIs.
3. Add API only when the existing surface cannot express an accepted use case; hard-cut redundant
   overloads or diagnostics in the same slice.
4. Update lifecycle architecture, module documentation, compiled samples, and locale mirrors for
   every durable contract change.

#### Phase 1 acceptance

The existing API family is sufficient and remains the single consumption surface:
`LocalLifecycleOwner`, `ProvideLifecycleOwner`, `Lifecycle.currentStateAsState()`,
`LifecycleStartEffect`, `LifecycleResumeEffect`, and `collectAsStateWithLifecycle(...)`. Activity,
Fragment, navigation destination, navigation graph, Preview, and explicit custom-container hosts
provide different owner boundaries, but DSL code consumes the same nearest-owner contract. No
host-specific façade or new public declaration is justified.

The fresh lifecycle-module suite passed 43 of 43 JVM and Robolectric tests, compared with the prior
35-test baseline: eight additional tests, a 22.9% increase. The additions directly exercise
activation and state changes between declaration and commit, lifecycle-owner replacement, aborted
replacement, failed composition, aborted state observation, and nested local restoration after a
declaration failure. The existing host suites continue to own Activity ViewTree, Fragment recreated
View owner, navigation entry and graph locals, Preview owners, and explicit custom-provider
integration.

The fresh cross-host regression passed 227 of 227 tests: 43 lifecycle, 21 Android aggregate, 151
Navigation Android, and 12 Preview Runner tests. This verifies the consumption surface across the
currently supported host adapters without introducing a parallel host-specific API.

The full `qaQuick` gate also passed all 2,268 actionable tasks: 170 executed and 2,098 were up to
date. Documentation Governance V2 reported zero issues against the Phase 0 base, translation checks
reported 126 current and zero stale required Chinese pages, and release-intent verification found
zero affected artifacts. The mixed cache state is not performance evidence.

Conclusion: **improved** contract coverage and **no material change** in runtime behavior. The
evidence confirms the current commit-aware implementation rather than exposing a production defect;
therefore this phase changes no production source, publication input, public API, or compiled sample
and owns no Maven Changeset. JVM/Robolectric evidence does not establish physical-device lifecycle
or performance behavior, but no new Android runtime path was introduced, so device validation is
deferred to the first changed Android navigation behavior. Next action: execute Phase 2's pure
scene/entry lifecycle projection and model tests.

### Phase 2: introduce scene and entry caps in core

1. Define platform-neutral scene, overlay, entry-presence, transition-role, pane-role, visibility,
   and interaction models.
2. Replace boolean-set lifecycle decisions with `min(host, scene, entry)` projection.
3. Preserve multi-pane multiple-resumed semantics only for settled simultaneously interactive
   entries.
4. Add exhaustive tables, graph-owner aggregation, invalid-scene rejection, and property tests.

#### Phase 2 acceptance

Navigation Core now owns one immutable `NavScene` and one validated `NavSceneEntry` per destination.
Entry presence, visibility, interaction, transition phase, content/overlay layer role, and pane role
are explicit rather than reconstructed from unrelated ID sets. Construction rejects duplicate
identities, contradictory roles, content above an overlay, or an interactive destination in an
active-transition scene. `NavLifecyclePlanner.plan(...)` hard-cuts its Alpha visible/interactive
set overloads and derives every destination target as `min(host cap, scene cap, entry cap)`. Graph
owners take the maximum effective descendant target, terminal owners cannot resurrect, and
downward or destroy transitions remain ordered before upward transitions.

The capability remains Q3. `NavScene`, `NavSceneEntry`, and `NavLifecyclePlanner.plan(...)` carry
Q3 KDoc and the compiled `module.navigation-core-scene-projection` sample; the five closed role
vocabularies are Q1 declarations documented by their owning types and the same sample. Applicable
behavior, input/output, state, lifecycle, concurrency, failure, performance, and compatibility
contracts are current in the source, Core module manual, navigation architecture, ADR-0024,
Compose migration comparison, generated Reference input, and required Simplified Chinese mirrors.
The Governance V2 structural detector reported zero application-facing DSL/component/host entry
changes because this pure Core model is outside that detector's catalog surface; its strict
one-impact-to-one-detected-change rule therefore admits no immutable impact record for these
symbols. The capability, sample, release, KDoc, and handwritten owner records provide the required
manual disposition without claiming `No documentation impact`.

Fresh JVM execution passed 60 of 60 Navigation Core tests, compared with the 53-test Phase 0
baseline: seven additional tests, a 13.2% increase. The additions cover the complete host-cap
property matrix, scene and entry caps, invalid semantic combinations, immutable collections,
active-transition caps, popped-exit `CREATED`, graph aggregation, identity conflicts, and
downward-before-upward ordering. A fresh Navigation Android regression passed 151 of 151 tests,
unchanged from the Phase 1 baseline, and `compileDebugKotlin` passed. The selected Core API audit
also generated strict Dokka output without warnings after correcting parameter-link markup.

The full `qaQuick` gate passed all 2,268 actionable tasks: 203 executed and 2,065 were up to date.
Documentation Governance V2 reported zero issues against base `af4f145c`, translation verification
reported 126 current and zero stale required Chinese pages, and release-intent verification found
one breaking Navigation Core artifact plus one explicitly ignored Navigation Android adapter.
The `qaPreview` gate also passed all 1,209 actionable tasks: 140 executed and 1,069 were up to date,
including Paparazzi verification and both Preview host suites. Its result is **no material change**
because this phase introduces no preview or visual behavior. Both mixed cache states are integration
evidence, not performance evidence.

Conclusion: **improved** Core lifecycle expressiveness, invalid-state prevention, and model-test
coverage, with **no material change** in Android runtime behavior. The Android adapter deliberately
maps its existing ownership policy to settled scenes so this slice does not silently change
transition timing. Consequently a device run would exercise an unchanged runtime path and is
deferred to Phase 3. Ordinary and predictive transition scenes, overlay coverage, focus transfer,
physical-device behavior, memory, leaks, and performance remain **inconclusive**. Next action:
execute Phase 3's Android transition lifecycle correction against this single semantic model.

### Phase 3: correct Android host transitions

1. Drive destination and graph owners from the new projection during ordinary and predictive
   transitions.
2. Keep incoming and active outgoing entries at `STARTED`; keep popped exiting entries at `CREATED`;
   promote only after terminal settlement.
3. Integrate pane changes, host lifecycle caps, and terminal cleanup while preserving the existing
   transition-driver focus transfer. Do not claim overlay execution until Navigation Android
   exposes a general overlay-navigation surface.
4. Delete tests and code that encode premature resume or visible-set inference.

#### Phase 3 acceptance

Every ordinary transition and predictive preview now freezes exactly one `NavScene`. Owner
reconciliation and later host-lifecycle changes consume that stored scene instead of re-inferring
interactive IDs. Push, replace, reset, stack selection, deep-link, predictive preview, cancellation,
commit, redirection, and adaptive panes therefore share one rule: all visible entries are
non-interactive and no higher than `STARTED` during motion; a removed outgoing entry that still owns
an exit presentation is `Exiting` and `CREATED`; terminal settlement alone resumes interactive
entries. Reset disposes removed hidden sessions before owner teardown because they have no transition
presentation to preserve.

The Android host has no general overlay-navigation surface. Core overlay roles remain valid model
vocabulary, but neither the unrelated overlay transport nor a model-only test is accepted as host
execution evidence. Overlay scene execution and the one-plan focus boundary remain assigned to the
destination-context and reducer phases rather than blocking this correction for every currently
supported host scene.

The fresh Navigation Android run passed 151 of 151 JVM/Robolectric tests with zero failures, errors,
or skips. The absolute test count is unchanged from the Phase 2 Android baseline, a 0% count change,
because this hard cut replaces ten assertions that encoded the rejected timing instead of keeping
both behaviors. The 20-test transition/adaptive subset passed 20 of 20 and directly covers push,
pop, reset hidden cleanup, predictive cancel/commit, redirection, repeated host-cap changes, and up
to three visible panes.

The app debug and androidTest sources compiled, then two selected instrumentation cases passed on a
physical Pixel 4 XL running API 33. One new case reads the nearest `LocalLifecycleOwner` captured
inside destination DSL and verifies `RESUMED -> STARTED -> CREATED/RESUMED`, predictive preview,
cancellation, committed popped exit, destruction, and terminal resume. The companion existing case
revalidates predictive transforms and cancellation on real native Views. Lifecycle-specific device
coverage moved from zero cases to one, so a percentage change from the zero baseline is not defined;
the accepted absolute result is 2/2 for this slice.

The full `qaQuick` gate passed all 2,268 actionable tasks: 201 executed and 2,067 were up to date.
Documentation Governance V2 reported zero issues against base `4e4b538c`, translation verification
reported 126 current and zero stale required Chinese pages, release-intent verification classified
exactly one Navigation Android fix, and development-tooling isolation passed. `qaPreview` passed all
1,209 actionable tasks: 140 executed and 1,069 were up to date, including Paparazzi verification and
both Preview host suites. This adds **improved** repository-integration confidence and **no material
change** in Preview behavior. Both mixed cache states are verification context, not performance
evidence.

Conclusion: **improved** lifecycle correctness with no premature `RESUMED` state in supported
ordinary, predictive, or adaptive-pane scenes. The physical-device result closes the owner-state
claim for the exercised API-33 dispatcher path. API-34 platform edge-gesture delivery, general
navigation overlays, memory, leaks, and performance remain **inconclusive**. Next action: execute
Phase 4's entry/presentation separation, retention-policy restoration matrix, and comparative device
memory/frame measurements.

### Phase 4: split entry and presentation lifetime

1. Move RenderSession and View ownership behind an explicit presentation policy while retaining the
   entry owner and destination context independently.
2. Implement and test dispose-when-hidden, explicit retain, and bounded retention without parallel
   legacy caching.
3. Restore hidden presentations transactionally before they become visible or interactive.
4. Select the default using accepted device memory, recreation, and frame evidence; interpret the
   result in active performance and navigation documentation.

#### Phase 4 acceptance

Navigation Android now stores retained entry ownership independently from its optional native
presentation. `DisposeWhenHidden` is the bounded default, `RetainAll` is an explicit unbounded
opt-in, and `Bounded(maxHiddenPresentations)` applies a positive deterministic
least-recently-hidden limit. Visible panes and ordinary or predictive transition participants are
never eviction candidates. Initial and restored attachment materializes only visible entries;
revealing an entry without a presentation transactionally renders and stages every candidate before
publishing the new stack and scene. Candidate failure disposes new presentations while preserving
the old scene, entry owner, ViewModel, and saved state. Permanent removal disposes presentation
before owner destruction.

The public policy and changed `NavHost` contract remain Q3 and carry canonical KDoc, complete
capability-impact dispositions, one compiled sample, module/architecture/guide/migration updates,
required Simplified Chinese mirrors, and one breaking Navigation Android Changeset. No legacy
implicit retain-all path or compatibility flag remains.

A fresh Navigation Android JVM/Robolectric run passed 157 of 157 tests with zero failures, errors,
or skips, six more than the 151-test Phase 3 baseline (4.0%). New focused coverage proves hidden
disposal and rebuild, stable owner/ViewModel/rememberSaveable/SavedStateHandle identity, failed
rebuild rollback and retry, deterministic bounded eviction, permanent removal after prior disposal,
invalid bounds, restored attachment, and public-host behavior. This improves contract coverage; it
does not measure reachability leaks or OEM behavior.

Two instrumentation invocations passed 4 of 4 selected cases on a physical Pixel 4 XL running API
33. The ownership case proves disposal and recreation preserve the same owner and ViewModel plus
saveable and SavedStateHandle values; the bounded case proves the exact hidden-presentation limit.
The comparative synthetic heavy 13-entry stack retained 1 presentation under `DisposeWhenHidden`
and 13 under `RetainAll`, while process PSS was 185,510 KiB versus 191,953 KiB. This is 12 fewer
presentations (92.3%) and 6,443 KiB lower PSS (3.4%). Synchronous pop-and-rebuild median time was
49,573 us versus 13,318 us, a 36,255 us or 272.2% increase. The separate animated comparison
captured 252 frames per policy at 90 Hz; both reported 9 ms P95 and zero frames above 32 ms.

Conclusion: **mixed** performance and **improved** bounded resource ownership. The default accepts
rebuild work in exchange for eliminating unbounded hidden View/RenderSession retention; measured
settled-frame behavior showed **no material change**, and applications can select `Bounded` or
`RetainAll` for measured expensive surfaces. The evidence is limited to one API-33 device,
synthetic content, process-wide PSS, short runs, and in-process recreation rather than process-kill.
Leak reachability, broader devices, multiple retained stacks, and representative workloads remain
**inconclusive** and stay assigned to Phase 7. Next action: execute Phase 5's stable destination
presentation context.

The full `qaQuick` gate then passed all 2,268 actionable tasks: 206 executed and 2,062 were up to
date. Governance V2 reported zero issues against base `a29780d4`, translation verification reported
126 current and zero stale required Chinese pages, release-intent verification classified exactly
one breaking Navigation Android artifact, and development-tooling isolation passed. `qaPreview`
passed all 1,209 actionable tasks: 140 executed and 1,069 were up to date, including Paparazzi and
both Preview host suites. This is **improved** integration confidence and **no material change** in
Preview behavior; the mixed cache state is verification context rather than performance evidence.

### Phase 5: expose destination presentation context

1. Add one stable per-entry context local with coarse visibility, interaction, transition, and pane
   state.
2. Keep standard AndroidX Lifecycle as the only resource-threshold API and keep frame progress on a
   dedicated opt-in motion surface.
3. Prove delayed local capture, View disposal/recreation, nested hosts, multiple panes, overlays,
   and permanent removal.
4. Land canonical KDoc, compiled Q3 sample, module docs, guide or migration disposition, capability
   reference update, and locale mirrors in the same public API slice.

#### Phase 5 acceptance

Navigation Android now provides one `NavDestinationContext` from
`LocalNavDestinationContext.current` while declaring destination content. The holder belongs to
the stable `NavEntryOwner`, so local snapshots capture the holder and the same identity survives
optional child `RenderSession` and native View disposal or recreation. Nested hosts override only
their child destination subtree and restore the parent context. Permanent entry removal stops
updates and destroys the standard AndroidX Lifecycle; no global current-page registry or
navigation-specific Lifecycle callback family was added.

The observable `NavDestinationPresentation` is a source alias for the exact Core `NavSceneEntry`
published during owner reconciliation. It exposes presence, visibility, interaction, coarse
transition phase, pane role, and content/overlay layer role without reconstructing a second Android
enum model. Standard AndroidX Lifecycle remains the only resource-threshold API. Ordinary and
predictive frame progress never enters the state, so only semantic scene changes can invalidate
content that reads it.

A fresh Navigation Android JVM/Robolectric run passed 162 of 162 tests with zero failures, errors,
or skips, five more than the 157-test Phase 4 baseline (3.2%). New focused coverage proves the
prepared local, delayed local capture with later state observation, exact multi-pane and overlay
Core projections, permanent-removal freezing and owner destruction, holder identity across
dispose/rebuild, nested-host override, and stable presentation identity across repeated predictive
progress. The overlay case proves the context/executor vocabulary boundary; it does not claim the
still-missing general Android overlay-navigation surface.

One selected instrumentation case passed on the physical Pixel 4 XL running API 33. It observed
the real destination DSL and View hierarchy through settled push, hidden-presentation disposal,
predictive reveal/rebuild, three progress values, commit, and permanent removal. The home context
and entry identities remained stable; both preview presentation objects and both DSL render counts
remained unchanged across the three frame-progress updates; and the removed details Lifecycle
reached `DESTROYED`. This is **improved** end-to-end confidence and **no material change** in
frame-rate invalidation because the new state publishes no continuous progress. It is not a timing
benchmark and remains limited to one API-33 device, single-pane content, and synthetic routes.

The public Q3 slice includes canonical KDoc, a compiled sample, one capability record, three
per-symbol impact records, the generated Reference input, module/architecture/guide/migration and
ADR updates, reviewed Simplified Chinese mirrors, and one additive Navigation Android Changeset.
The full `qaQuick` gate passed all 2,268 actionable tasks: 169 executed and 2,099 were up to date.
Governance V2 reported zero issues against base `ef6be56e`, translation verification reported 126
current and zero stale required Chinese pages, release-intent verification classified exactly one
Navigation Android feature, and development-tooling isolation passed. `qaPreview` passed all 1,209
actionable tasks: 140 executed and 1,069 were up to date, including Paparazzi and both Preview host
suites. This is **improved** integration confidence and **no material change** in Preview behavior;
the mixed cache state is verification context rather than performance evidence.

Next action: execute Phase 6's single reducer plan and typed Android executors; general overlay
navigation remains assigned to Phase 7 rather than being hidden behind the context vocabulary.

### Phase 6: converge reducer and executor

1. Introduce one reducer output for stack, scene, lifecycle, presentation, focus, transition, and
   rollback effects.
2. Move platform operations to typed Android executors and preserve main-thread and child/parent
   lifecycle ordering.
3. Delete the superseded command sequencing and any state reconstructed independently by the
   coordinator, driver, or session store.
4. Add model equivalence, re-entrancy, failure, cancellation, and terminal-state guards.

Phase 6 acceptance compared fresh results with the completed Phase 5 baseline. Navigation Core
passed 71/71 tests versus 60/60, an absolute increase of 11 and a normalized increase of 18.3%.
Navigation Android passed 165/165 versus 162/162, an absolute increase of three and a normalized
increase of 1.9%. The new contracts cover reducer terminal states, deterministic retention and
rollback, typed interaction execution, dynamic Back enablement, and explicit-key host replacement.

The full `NavigationBackDeviceTest` suite passed 15/15 on the physical Pixel 4 XL running API 33.
An earlier 14/15 run exposed an unstable function-identity dependency: an inline
`overlayHostFactory` could replace the host during ordinary recomposition and lose Back ownership.
The factory is now captured at host creation, excluded from host reconciliation identity, and
explicit `key` replacement with the same controller is covered separately. The coordinator fell
from 1,597 to 1,176 lines, an absolute reduction of 421 lines or 26.4%; structural inspection found
no production-side lifecycle planner call, scene reconstruction, direct owner reconciliation, or
presentation disposal outside the typed executor path.

The conclusion is **improved**: one pure plan is now the policy source for stack, scene, lifecycle,
presentation, interaction, Back, rollback, and terminal cleanup, and Android applies it through one
typed executor. Evidence is limited to deterministic unit/Robolectric suites and one API-33 device;
line/branch coverage, representative leaks, memory, performance, general overlay navigation,
typed-route serialization, direct NavigationEvent integration, navigation results, diagnostics,
and testing utilities remain **inconclusive** and explicitly advance to Phase 7.

### Phase 7: close mature-navigation gaps and evidence

1. Re-audit typed route serialization, action/MIME deep links, general scene strategies, direct
   NavigationEvent integration, navigation results, diagnostics, and testing utilities.
2. Implement only accepted material gaps; mark deliberate differences with evidence and migration
   guidance.
3. Run fresh unit, device, restoration, coverage, leak, memory, and performance matrices.
4. Hard-cut all obsolete production, test, sample, diagnostic, and documentation paths.

### Phase 8: document, release, and archive

1. Move durable generic lifecycle conclusions to `docs/architecture/lifecycle-and-saved-state.md`
   and the lifecycle module manual.
2. Move durable navigation conclusions to `docs/architecture/navigation.md`, both navigation module
   manuals, the production guide, and Compose migration comparison.
3. Update every required Simplified Chinese mirror and generated capability-reference input/output.
4. Run complete documentation, API, development-tooling, release-intent, unit, device, and project
   acceptance gates.
5. Record interpreted evidence and final next action, move this plan to `docs/archive/`, update both
   plan indexes, and release only after active-plan Changeset ownership is cleared.

## Verification commands

The exact affected-task plan may expand these commands. At minimum, implementation phases run:

```text
./gradlew :viewcompose-lifecycle-androidx:testDebugUnitTest
./gradlew :viewcompose-navigation-core:test
./gradlew :viewcompose-navigation-android:testDebugUnitTest
./gradlew :viewcompose-navigation-android:compileDebugKotlin
./gradlew verifyDocumentationStructure
./gradlew verifyDevelopmentToolingIsolation
./gradlew verifyViewComposeReleaseIntent
./gradlew qaQuick
```

Device and performance phases additionally run the accepted navigation instrumentation, process
restoration, leak, benchmark, and `qaFull` targets recorded when their harness contracts are frozen.
If no device target exists at Phase 0, creating the target and its deterministic fixtures is part of
the plan rather than a reason to claim device acceptance from Robolectric.

## Documentation ownership during execution

This plan owns temporary sequencing, phase status, audit evidence, completion gates, and next
action. It is not the permanent source of truth for shipped behavior.

Durable generic lifecycle contracts update:

- `docs/architecture/lifecycle-and-saved-state.md` and its Simplified Chinese mirror;
- `docs/modules/viewcompose-lifecycle-androidx/README.md` and its mirror; and
- applicable host, migration, guide, sample, API-reference, and capability records.

Durable navigation contracts update:

- `docs/architecture/navigation.md` and its Simplified Chinese mirror;
- the `viewcompose-navigation-core` and `viewcompose-navigation-android` module manuals and mirrors;
- the navigation guide, tutorial, Compose migration comparison, and capability records when their
  behavior changes.

Every accepted test or benchmark result is interpreted in the active document that owns the claim.
This plan may retain commands and evidence pointers, but it cannot close a phase while the durable
conclusion exists only here, in a pull-request comment, or in raw output.

## Completion and archival criteria

This plan completes only when:

1. every phase is complete with fresh, interpreted evidence and no unresolved partial or dual path;
2. public API, capability, Q-level, contract-field, KDoc, sample, module, architecture, migration,
   locale, and generated-reference obligations are current;
3. transition, overlay, pane, host, entry, presentation, restoration, focus, and failure matrices
   pass unit and applicable device tests;
4. the selected retention default has accepted absolute and comparative memory/performance evidence;
5. all obsolete semantics and infrastructure are absent under structural guards;
6. every owned Changeset is listed here and accepted by deterministic release planning; and
7. durable conclusions and next actions are moved to active documentation before this plan and its
   final evidence move to the archive indexes.
