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
  - navigation.host
artifact_ids:
  - viewcompose-lifecycle-androidx
  - viewcompose-navigation-android
  - viewcompose-navigation-core
sample_ids: []
status: active
scope: Evolve navigation around one scene-derived destination lifecycle, separate retained entry ownership from native presentation lifetime, and stabilize one host-independent Lifecycle DSL consumption surface.
non_goals:
  - Replace AndroidX LifecycleOwner with a ViewCompose-specific public lifecycle type.
  - Add Activity-, Fragment-, and navigation-specific copies of the same Lifecycle DSL APIs.
  - Preserve defective Alpha transition, retention, or compatibility behavior through aliases, flags, or dual paths.
  - Copy every Navigation 2, Navigation 3, Compose, or Flutter API name without a ViewCompose use case.
  - Reopen the general retained ViewModel scoped-owner design owned by the active AndroidX ViewModel plan.
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
next_action: Complete Phase 0 by accepting the lifecycle and presentation state tables, resolving the stable capability identity and Q3 contracts for destination context and retention policy, coordinating the owner boundary with the active ViewModel plan, and recording any required ADR before production changes begin.
maven_release_changesets: []
---

# Navigation Lifecycle and Scene Evolution Plan

## Status

Active. The architecture and test audit is complete. Phase 0 contract freeze is next; no production
source or public API has changed under this plan.

Last verified: 2026-08-29.

Next action: accept the target lifecycle and presentation matrices, resolve stable capability
identities and Q3 contracts for any new public destination-context or retention-policy API,
coordinate retained owner responsibilities with the active AndroidX ViewModel plan, and decide
whether the scene/reducer boundary requires an ADR before implementation.

## Maven release changesets

- None.

## Release intent rationale

This initial change creates a repository-only execution plan and updates the active-plan index. It
does not change production source, publication inputs, or compiled API samples. The first
implementation pull request that affects a published artifact must add one immutable
`release/changes/<unique>.json`, classify every directly affected artifact, and list that file in
the front matter and this section. Release planning derives reverse-dependency propagation.

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

## Coordination with the active ViewModel plan

The active AndroidX ViewModel optimal-architecture plan owns the general retained scoped-owner
facility, ViewModelStore allocation policy, and migration away from duplicate navigation-owned
store retention. This plan owns navigation entry lifecycle, scene projection, destination context,
RenderSession lifetime, and transition semantics.

The plans coordinate as follows:

1. Phase 0 freezes one shared owner boundary and forbids either plan from introducing a parallel
   retained-store mechanism.
2. Navigation lifecycle and scene-core work may proceed without changing ViewModelStore allocation.
3. Presentation disposal and recreation may use the existing owner until the shared scoped-owner
   facility lands, but its final acceptance must run after navigation consumes that facility.
4. A pull request touching the same owner/store files declares which plan owns the slice and updates
   both plans when it changes their next action or evidence.
5. Neither plan can archive while current architecture or module documentation describes a
   superseded split-owner model.

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
| 0 | Contract, capability, ownership, and ADR freeze | State matrices, hard cuts, Q3/API impacts, ViewModel-plan boundary, and ADR disposition accepted | Next |
| 1 | Generic Lifecycle DSL stabilization | One consumption surface passes host, race, replacement, failure, effect, and Flow contracts | Pending |
| 2 | Core scene and lifecycle projection | Pure scene/entry caps and model tests replace visible/interactive-only decisions | Pending |
| 3 | Android transition lifecycle correction | Ordinary and predictive transitions, overlays, panes, and host caps match the matrix | Pending |
| 4 | Entry/presentation lifetime separation | Dispose, retain, and bounded policies pass restoration, cleanup, and memory gates | Pending |
| 5 | Destination context DSL | Stable per-entry context, compiled Q3 sample, and non-frame-rate observation contracts pass | Pending |
| 6 | Reducer and executor convergence | One typed plan owns stack, scene, lifecycle, presentation, focus, and effects; obsolete paths are absent | Pending |
| 7 | Capability and test closure | Typed routes and ecosystem gaps have accepted dispositions; unit, device, coverage, memory, and performance gates pass | Pending |
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

### Phase 1: stabilize Lifecycle DSL consumption

1. Characterize the existing nearest-owner local, observable lifecycle state, effects, and Flow
   collection across Activity, Fragment, navigation, graph, preview, and custom hosts.
2. Close declaration-to-commit, owner replacement, rapid transition, failed frame, and disposal
   gaps without adding host-specific APIs.
3. Add API only when the existing surface cannot express an accepted use case; hard-cut redundant
   overloads or diagnostics in the same slice.
4. Update lifecycle architecture, module documentation, compiled samples, and locale mirrors for
   every durable contract change.

### Phase 2: introduce scene and entry caps in core

1. Define platform-neutral scene, overlay, entry-presence, transition-role, pane-role, visibility,
   and interaction models.
2. Replace boolean-set lifecycle decisions with `min(host, scene, entry)` projection.
3. Preserve multi-pane multiple-resumed semantics only for settled simultaneously interactive
   entries.
4. Add exhaustive tables, graph-owner aggregation, invalid-scene rejection, and property tests.

### Phase 3: correct Android host transitions

1. Drive destination and graph owners from the new projection during ordinary and predictive
   transitions.
2. Keep incoming and active outgoing entries at `STARTED`; keep popped exiting entries at `CREATED`;
   promote only after terminal settlement.
3. Integrate overlay coverage, pane changes, host lifecycle caps, focus transfer, and terminal
   cleanup.
4. Delete tests and code that encode premature resume or visible-set inference.

### Phase 4: split entry and presentation lifetime

1. Move RenderSession and View ownership behind an explicit presentation policy while retaining the
   entry owner and destination context independently.
2. Implement and test dispose-when-hidden, explicit retain, and bounded retention without parallel
   legacy caching.
3. Restore hidden presentations transactionally before they become visible or interactive.
4. Select the default using accepted device memory, recreation, and frame evidence; interpret the
   result in active performance and navigation documentation.

### Phase 5: expose destination presentation context

1. Add one stable per-entry context local with coarse visibility, interaction, transition, and pane
   state.
2. Keep standard AndroidX Lifecycle as the only resource-threshold API and keep frame progress on a
   dedicated opt-in motion surface.
3. Prove delayed local capture, View disposal/recreation, nested hosts, multiple panes, overlays,
   and permanent removal.
4. Land canonical KDoc, compiled Q3 sample, module docs, guide or migration disposition, capability
   reference update, and locale mirrors in the same public API slice.

### Phase 6: converge reducer and executor

1. Introduce one reducer output for stack, scene, lifecycle, presentation, focus, transition, and
   rollback effects.
2. Move platform operations to typed Android executors and preserve main-thread and child/parent
   lifecycle ordering.
3. Delete the superseded command sequencing and any state reconstructed independently by the
   coordinator, driver, or session store.
4. Add model equivalence, re-entrancy, failure, cancellation, and terminal-state guards.

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
