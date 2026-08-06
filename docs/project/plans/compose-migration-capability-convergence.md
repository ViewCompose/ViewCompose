# Compose Migration Capability Convergence Plan

## Status

Active and ready for scheduling. This plan records the high-value capability work identified by
the current Compose migration comparison, the evidence required before risky implementation, and
the Compose behaviors that ViewCompose deliberately will not reproduce.

No implementation phase has started. The plan is ordered by product and correctness value rather
than by Compose API count. Compiler plugins, compiler-generated restart groups, stability inference,
and other compiler-owned optimizations are explicitly outside the comparison.

Last verified: 2026-08-05.

Next action: schedule Phase 0, freeze the unresolved public contracts, and capture the required
host, navigation, identity, inset, and restoration baselines before selecting the first production
release slice.

## Maven release changesets

- None.

## Objective

Close the migration gaps that materially affect correctness, Android ecosystem integration, or
common application architecture while preserving ViewCompose's native Android View engine and its
existing transactional boundaries.

The plan has five outcomes:

1. lifecycle, session disposal, saveable state, and Android View callback contracts agree across
   source, tests, and documentation;
2. navigation owners interoperate with parent ViewModel factories and `CreationExtras`, and common
   stack rewrites remain atomic;
3. ordinary keyed composition identity, RTL edges, and WindowInsets have explicit, tested behavior;
4. existing View hierarchies gain an optional safe owner/disposal integration without weakening the
   low-level `renderInto` contract; and
5. high-risk or low-value Compose parity work stays rejected unless a new product requirement and
   new evidence change the decision.

This is not a goal to make ViewCompose a drop-in implementation of Compose Runtime, Compose UI,
Navigation 2, or Navigation 3.

## Scope

The required and conditional work can affect:

- `viewcompose-android`: Activity/Fragment hosting and automatic owner integration;
- `viewcompose-host-android`: low-level ViewTree owner integration, frame/session terminal behavior,
  and Android View public callback documentation;
- `viewcompose-runtime` and `viewcompose-ui-foundation`: keyed composition identity, remember/effect
  movement, saveable-state claim recovery, and focused snapshot/derived-state correctness tests;
- `viewcompose-navigation-core` and `viewcompose-navigation-android`: entry and graph owners, atomic stack
  commands, deep-link query policy, retained destination diagnostics, and process restoration;
- `viewcompose-ui-contract`, `viewcompose-renderer-android`, and layout-owning widget modules: logical edge
  contracts, scoped Box parent data, WindowInsets application and consumption, and renderer
  diagnostics;
- `viewcompose-benchmark`, Demo/device certification surfaces, and owning module tests where a
  performance, memory, Android lifecycle, or mixed-View claim requires executable evidence;
- active migration, architecture, guide, tooling, module, and localized public documentation when
  an implemented phase changes current behavior or public API.

The image-loading protocol migration is not part of this plan. Its migration page compares old and
new ViewCompose APIs rather than ViewCompose and Compose engine capabilities, and it remains owned
by the separate image-loading plan.

## Non-goals

This plan does not include:

- a Compose compiler plugin, compiler-generated groups, change masks, stability inference, strong
  skipping, or automatic lambda memoization;
- a general Compose-style `Layout`, `MeasurePolicy`, measurable/placeable protocol, or intrinsic
  measurement engine;
- a public application-defined `Modifier.Node` lifecycle or capability-dispatch system;
- Compose constraint-chain parity, fractional fill parity, required-size parity, or general
  intrinsic-size parity;
- `SnapshotStateList`, `SnapshotStateMap`, `SnapshotStateSet`, or `snapshotFlow` without a separate
  product use case;
- tracked and static `UiLocal` variants that turn local lookup into implicit observation;
- arbitrary `Parcelable`, `Serializable`, or application-object navigation arguments;
- a complete `NavOptions` clone, Navigation3 scene/metadata engine, Activity or Fragment
  destinations, or Fragment-in-render-tree support;
- action- or MIME-based deep-link matching inside the navigation core;
- a dedicated ViewBinding renderer node when `AndroidView` inflation is sufficient;
- changing the transaction-aware `AndroidView` callback phases to match Compose callback timing;
- making direct NavigationEvent APIs a prerequisite while Activity's compatible
  `OnBackPressedDispatcher` path still covers system Back and Predictive Back;
- automatically disposing every hidden navigation destination by default;
- derived-state equal-result suppression, nullable mutation-policy merge redesign, or another
  performance-only runtime change without a separately measured trigger; or
- retaining an experimental implementation whose correctness, targeted operation reduction, or
  representative end-to-end benefit cannot be demonstrated.

## Current baseline

The baseline is the implementation and migration documentation reviewed on 2026-08-05.

| Area | Current behavior | Consequence |
| --- | --- | --- |
| Fragment lifecycle | `Fragment.setUiContent` installs the Fragment as `LocalLifecycleOwner`, while session disposal follows the current `viewLifecycleOwner`. | View-bound collection and cleanup can outlive `onDestroyView`. |
| Session terminal behavior | `RenderSession.dispose()` is idempotent, but some public render/activation calls silently no-op after disposal while documentation describes fail-fast behavior. | Use-after-dispose can remain hidden and the public contract is not deterministic. |
| Android View release | Renderer rollback releases an uncommitted candidate, while public `AndroidView.onRelease` wording names only committed removal and session disposal. | Implementation and KDoc disagree even though rollback cleanup is required. |
| Deep-link query matching | Tests accept additional unregistered query keys while the navigation guide describes exact query-key matching. | Security and compatibility behavior is unresolved. |
| Navigation owners | Destination and graph owners create saved-state-aware factories from Application and route defaults but do not establish complete parent factory and `CreationExtras` inheritance. | Custom DI/factory inputs are not reliably available in page scopes. |
| Multiple stacks | Entry identities isolate retained owners, but same-route/same-key cross-stack isolation lacks a focused parity test. | Store isolation is expected but not fully certified. |
| Stack commands | Push, `SingleTop`, pop, replace, reset, stack selection, and deep-link commands are transactional. There is no atomic pop-to-existing-entry command. | Applications cannot express common `popUpTo` results without multiple non-atomic pops. |
| Ordinary keyed identity | `key` isolates group identity at the current position, but ordinary sibling reorder does not move remember, observation, and effect scopes. Lazy item keys do support item movement. | The public key wording is stronger than ordinary composition behavior. |
| Layout direction | Direction and locales reach VNodes and native Views, but general padding, margin, offset, and inset selectors use physical left/right edges. | Compose start/end migrations can be wrong in RTL. |
| WindowInsets | System bars and IME can add physical-edge padding, but nested ViewCompose nodes do not exchange consumed state and mixed View/ViewCompose behavior lacks end-to-end evidence. | Ancestors and descendants can apply the same inset more than once. |
| Existing View hierarchy | `renderInto` deliberately installs no owner and requires explicit disposal. | The low-level boundary is clear, but ordinary ViewTree embedding remains easy to leak or misconfigure. |
| Saveable restoration | Restored values use claim/commit/release, but provider-registration failure during composition commit lacks focused recovery coverage. | A committed-frame failure may leave a claim/provider lifecycle in an unclear state. |
| Process restoration | Navigation has broad restore coverage; a general non-navigation Activity-root process-kill certification is still missing. | Recreation evidence is narrower than the documented host surface. |
| Hidden destinations | Non-visible stacks retain RenderSessions, native Views, composition state, and effects while frame rendering is inactive. | Fast switching is preserved at a potentially significant memory and background-work cost. |

Authoritative comparison pages:

- [Migration overview](../../migration/README.md)
- [State, recomposition, and restoration](../../migration/compose-state-recomposition-and-restoration.md)
- [Layout, Modifier, and environment](../../migration/compose-layout-modifier-and-environment.md)
- [Host lifecycle and Android interop](../../migration/compose-host-lifecycle-and-android-interop.md)
- [Navigation](../../migration/compose-navigation.md)

## Locked decision principles

### 1. Correctness and Android ownership outrank API parity

A lifecycle owner mismatch, lost restoration opportunity, ambiguous deep-link rule, or non-atomic
stack rewrite is higher priority than a missing Compose-named layout or modifier API.

### 2. Preserve the native Android View engine

New behavior should reuse View measurement, `LayoutParams`, ViewTree owners,
`WindowInsetsCompat`, AndroidX lifecycle, and native interop. Do not create a second general layout
or modifier runtime merely to match Compose concepts.

### 3. Preserve prepare, commit, rollback, and explicit ownership boundaries

No phase may publish irreversible work before native-tree commit, make rollback release less
complete, retain a View beyond its owner, merge independently disposable RenderSessions, or hide
session ownership behind a process-global registry.

### 4. Add narrow primitives instead of broad compatibility surfaces

Prefer one atomic pop-to command over a `NavOptions` clone, logical edge values over Compose layout
constraints, `BoxScope.matchParentSize` over a general measure policy, and a high-level ViewTree host
over implicit behavior in the low-level `renderInto` API.

### 5. High-complexity behavior starts with tests and diagnostics

Keyed sibling movement, nested inset consumption, and hidden-session retention changes must begin
with current-behavior tests, failure-path tests, and representative performance or memory evidence.
Production changes must be separately revertible. Useful tests and diagnostics remain if an
experiment is rejected.

### 6. Public contracts become deterministic before optimization

When KDoc, implementation, migration guidance, and tests disagree, freeze the intended contract
first. A performance improvement cannot justify leaving lifecycle, cleanup, persistence, or
security behavior ambiguous.

## Priority and scheduling decision

| Priority | Work item | Expected value | Complexity | Scheduling decision |
| --- | --- | --- | --- | --- |
| P0 | Fragment view-lifecycle alignment | Very high lifecycle and leak correctness | Medium to high | Required; test-first correctness work |
| P0 | RenderSession terminal contract | High ownership correctness and diagnostics | Low | Required |
| P0 | AndroidView release and deep-link query contract convergence | High contract confidence for low cost | Low to medium | Required |
| P0 | Navigation parent Factory/`CreationExtras` inheritance and cross-stack isolation evidence | Very high ViewModel/DI compatibility | Medium | Required |
| P1 | Ordinary keyed-sibling scope movement | High state/effect correctness for dynamic trees | High | Baseline and rollback gated |
| P1 | Logical start/end edges | High RTL correctness | Low to medium | Required after P0 |
| P1 | Atomic pop-to-existing-entry command | High navigation correctness and ergonomics | Medium | Required after owner work |
| P1 | Nested WindowInsets consumption | High edge-to-edge correctness | High | Baseline and rollback gated |
| P2 | ViewTree-aware high-level render host | Medium to high embedding safety | Medium | Required without changing raw `renderInto` |
| P2 | Saveable/snapshot/process-death hardening | Medium correctness and certification value | Low to medium | Required evidence; behavior change only if proven |
| P2 | `BoxScope.matchParentSize` | Medium layout ergonomics with a narrow boundary | Medium | Schedule after core correctness work |
| Conditional | Hidden destination retention policy | Potentially high memory value | High | Diagnostics first; no default change |
| Conditional | Arbitrary subtree ViewModel scope provider | Product-specific ownership value | Medium to high | Do not schedule before parent inheritance is complete |
| Deferred | Direct NavigationEvent surface | Future integration value | Medium | Revisit only for a direct nested/forward/Preview requirement |
| Deferred | Derived-state equal-result suppression | Workload-specific performance value | High | Keep outside this plan unless separately measured |

## Release slicing policy

The work is intentionally separable so maintainers can schedule it across release windows.

1. A release slice should contain one coherent ownership or behavior change plus its tests,
   documentation, and immutable Changeset.
2. The first production change selected directly from this plan must replace `- None.` in
   `Maven release changesets` with every Changeset owned by this plan.
3. If maintainers need to publish one slice while later slices remain unscheduled, extract the
   selected slice into a narrowly scoped child execution plan before production implementation.
   The child plan owns its Changeset and implementation evidence; this plan retains only the shared
   ranking and handoff status. Do not duplicate the architecture rationale.
4. A plan that owns a Changeset must complete, move durable conclusions into active documents, and
   archive before Maven Central accepts the affected direct or dependency-propagated artifacts.
5. Conditional and deferred items do not block completion when their trigger was not met and the
   decision is recorded in the evidence ledger.

## Phase 0: Contract freeze, diagnostics, and current-behavior baselines

### Goal

Make every later change observable and establish the exact current behavior before changing a
public or lifecycle contract.

### Required baselines

Add focused evidence for:

1. Fragment `onCreateView`, `onViewCreated`, `onDestroyView`, view recreation, and Fragment
   destruction, recording the owner visible through `LocalLifecycleOwner` and the exact disposal
   reason;
2. public `RenderSession.render`, activation, and repeated disposal before and after terminal
   disposal, separated from already-queued internal callbacks;
3. Android View candidate creation, failed-frame rollback release, committed removal, and session
   disposal, asserting exactly-once `onRelease`;
4. deep-link query matching with exact keys, missing keys, duplicate keys, additional keys, malformed
   encodings, and security-sensitive values;
5. ordinary keyed siblings under insertion, deletion, head/tail movement, arbitrary reorder,
   duplicate keys, prepared-composition abort, and committed removal;
6. navigation entry/graph Factory and `CreationExtras` provenance plus same-route entries in
   different stacks;
7. raw, consumed, and applied Insets per type and physical edge in nested and mixed View trees;
8. hidden navigation session, native View, active effect, and owner counts by stack; and
9. restored claims, active providers, failed registrations, released claims, and the value included
   by `performSave` after each failure path.

Diagnostics must be test-visible or debug-only and must not retain owners, Views, or unbounded event
history. Release paths with diagnostics disabled must remain allocation-free or use an already
approved diagnostics mechanism.

### Contracts to freeze

Before Phase 1 implementation, record executable expectations for these decisions:

- Fragment content lifecycle follows the Fragment View lifecycle. ViewModel and saved-state owners
  are selected independently and follow the documented Android Fragment ViewTree contract.
- `RenderSession.dispose()` stays idempotent; public render/activation calls after disposal fail
  fast; queued internal callbacks may safely no-op.
- `AndroidView.onRelease` covers permanent abandonment of every created candidate, including
  rollback, committed removal, and session disposal.
- Unknown deep-link query keys are rejected by default. A future opt-in compatibility policy
  requires an explicit API and separate security tests.
- Ordinary `key` is intended to move a complete composition scope among siblings under one parent.
  If the Phase 3 experiment cannot make that safe, the implementation is reverted and public
  wording is narrowed to positional isolation.

### Phase 0 completion gate

- Every current behavior above is protected or intentionally recorded by a focused test.
- No production behavior changes.
- The first release slice and its owning plan are selected.
- Baseline results and unresolved blockers are recorded in the evidence ledger.

## Phase 1: Host and public-contract correctness

### Fragment owner alignment

Do not directly access `viewLifecycleOwner` before Fragment view creation completes. Implement a
two-stage binding that:

1. creates and returns the root without publishing a false long-lived lifecycle boundary;
2. binds or rebinds the content lifecycle when the current View lifecycle owner becomes available;
3. disposes the session exactly once at `onDestroyView` or earlier permanent teardown;
4. does not keep an observer or View from a previous Fragment view generation; and
5. preserves the intended Fragment-scoped ViewModel and saved-state ownership independently from
   the View lifecycle owner.

If preserving a synchronous fully composed first frame conflicts with truthful owner identity, the
implementation must document and test the selected ordering. Do not introduce a proxy owner whose
lifecycle events can diverge from AndroidX unless an ADR establishes that contract.

### RenderSession terminal behavior

- keep `dispose()` idempotent and best-effort across cleanup failures;
- fail fast on caller-initiated render or activation after terminal disposal;
- keep internal scheduled invalidations race-safe and non-rendering after disposal; and
- add diagnostics that name the disposed session and rejected operation without retaining the host.

### Android View release wording

Update canonical-English KDoc, compiled samples if affected, host/renderer module manuals, migration
pages, and Chinese mirrors so rollback-candidate release is part of the public contract. Preserve
the current transaction-aware callback phases; do not remove rollback cleanup to imitate Compose.

### Deep-link query policy

Align resolver tests, navigation guide, module manuals, and migration pages on strict unknown-query
rejection. If compatibility data demonstrates a need for extra tracking parameters, design a
separate explicit declaration rather than silently accepting them.

### Phase 1 completion gate

- Fragment view recreation cannot leave old collection/effect/View ownership alive.
- Public and internal session operations have deterministic post-disposal behavior.
- Every created Android View candidate is released exactly once on every permanent-abandon path.
- Deep-link resolver, guide, migration page, and tests state the same query policy.
- Applicable API quality, sample, documentation, and Changeset gates pass.

## Phase 2: Navigation ownership and atomic stack operations

### Parent Factory and CreationExtras inheritance

For both destination and graph owners:

1. capture the nearest intended parent `HasDefaultViewModelProviderFactory` contract at host
   attachment;
2. inherit the parent default Factory and immutable starting `CreationExtras`;
3. override current owner keys, saved-state owner keys, and route/graph default arguments without
   losing unrelated application extras;
4. preserve stable inputs across host recreation and retained stacks; and
5. fail with an actionable diagnostic when a required parent extra cannot be represented safely.

Tests must cover custom factories, Application extras, default arguments, `SavedStateHandle`, graph
scope, destination scope, process recreation, and same-route entries in separate retained stacks.

### Atomic pop-to command

Add one controller command that expresses the result of popping to an existing entry or route,
with an explicit inclusive flag. It must use the existing prepare, render, commit, and rollback
transaction. Define behavior for:

- missing target;
- repeated routes and entry identity selection;
- root targets and inclusive root removal;
- nested graphs;
- adaptive pane transitions;
- retained stack selection; and
- render failure after the target set is prepared.

Do not add a broad `NavOptions` property bag or implement pop-to as a loop of public pop calls.

### Phase 2 completion gate

- parent Factory/Extras inheritance is demonstrated for entry and graph owners;
- cross-stack owner isolation and clearing are deterministic;
- pop-to is one rollback-safe navigation transaction; and
- navigation migration and module documentation describe only the supported command surface.

## Phase 3: Logical edges and ordinary keyed identity

### Logical start/end edges

Add logical-edge contracts without changing the meaning of existing physical APIs:

- logical padding and margin;
- logical horizontal offset or an explicitly direction-aware offset form;
- logical system-bar and IME edge selection; and
- renderer resolution using the captured `UiLayoutDirection`.

Physical `left` and `right` declarations remain physical. Do not reinterpret existing serialized or
source-compatible calls. Tests must cover LTR, RTL, runtime direction changes, nested delayed
sessions, ConstraintLayout anchors where applicable, and mixed logical/physical declarations.

### Keyed sibling movement experiment

Move a keyed group only as one complete ownership unit:

- remember slots and `RememberObserver` lifecycle;
- DisposableEffect and coroutine-effect identity;
- observations and invalidation queues;
- child groups and cached results;
- saveable-key paths; and
- transaction checkpoints needed for abort and rollback.

Duplicate effective keys under the same matching parent must fail or produce one deterministic
diagnostic policy; they must never silently alias state. Matching must remain bounded for realistic
sibling counts and must not degrade unchanged positional trees.

### Keyed movement keep or revert rule

Keep the behavior only when:

1. insertion, removal, reorder, and duplicate-key semantics are exhaustive and deterministic;
2. aborted composition restores the previous scope tree and effects exactly;
3. no remembered value, effect, observation, or saveable path crosses business identity;
4. unchanged and positional compositions do not regress beyond the repository policy; and
5. implementation complexity remains local to composition identity rather than leaking into the
   renderer or native View reconciliation contract.

If any condition fails, revert the movement implementation, keep the tests and diagnostics, and
narrow `key` KDoc and migration guidance to the proven positional behavior.

## Phase 4: WindowInsets consumption experiment

### Goal

Prevent duplicate system-bar and IME application in nested and mixed Android View/ViewCompose trees
without reproducing the complete Compose Insets model.

### Required design boundaries

- use AndroidX `WindowInsetsCompat` and Android View dispatch as the platform source;
- represent raw, consumed, and remaining values per inset type and edge;
- make one node's consumption visible to participating descendants;
- preserve non-participating ordinary View behavior;
- define system-bars-plus-IME behavior without blindly summing overlapping physical space;
- avoid combining `adjustResize` and IME padding into duplicate ownership; and
- keep listener replacement, View reuse, rollback, and disposal idempotent.

### Required scenarios

- ancestor and descendant request the same system-bar edge;
- system bars and IME target the same View and separate Views;
- ordinary View ancestor with ViewCompose descendant, and the reverse;
- edge-to-edge Activity with gesture and three-button navigation;
- IME open, close, progress/animation, cancellation, and configuration change;
- LTR/RTL logical edges; and
- recycled or rollback-restored native Views.

### Insets keep or revert rule

Keep a general nested protocol only if the final ownership is deterministic in all mixed-tree
scenarios and it does not require a hidden second layout engine. Otherwise revert the protocol,
retain raw/applied diagnostics and tests, and keep the documented single-owner-per-edge rule.

## Phase 5: Safer existing-View hosting and narrow layout completion

### ViewTree-aware host

Keep raw `renderInto` unchanged. Add a separate high-level host entry point only if it can:

1. discover the intended ViewTree lifecycle, ViewModel, and saved-state owners;
2. fail clearly when a required owner is absent rather than installing a partial environment;
3. bind disposal to an explicit documented strategy;
4. preserve caller control over theme, environment, overlay, frame clock, and diagnostics; and
5. avoid retaining the container or owner after disposal.

The API must not silently infer an Activity or Fragment destination and must not hide the returned
session when explicit disposal remains caller-owned.

### BoxScope.matchParentSize

Add a Box-scoped parent-data operation only if the native Box implementation can preserve the key
semantic: the matching child fills the final Box without participating in the Box's own desired
size. It must not be an alias for `fillMaxSize`.

Cover wrap-content Box measurement, multiple matching children, alignment precedence, invalid
parent use, RTL, and View reuse. Do not generalize the solution into a public measure policy.

## Phase 6: Restoration and snapshot certification

### Required evidence

Add focused tests for:

- provider-registration failure after a restored value was claimed;
- claim availability, active provider state, `performSave`, later retry, and session disposal after
  that failure;
- mutable-snapshot creation while a read-only snapshot is active, with one explicit supported or
  rejected contract;
- nested derived state, equal derived results, dependency switching, and calculation failure;
- non-navigation Activity-root configuration recreation and real process-kill restoration; and
- predictive-back device certification when navigation or host lifecycle work changes its inputs.

### Change boundary

Correct a proven state-loss, owner leak, or read-only-boundary violation with the smallest internal
change. Do not add snapshot collections, `snapshotFlow`, derived-state mutation-policy overloads,
or equal-result suppression as part of certification.

## Phase 7: Conditional navigation retention work

Do not implement a new retention policy until diagnostics demonstrate a representative problem.
The trigger requires all of:

1. a real multi-stack or adaptive-pane scenario retains materially expensive hidden Views or work;
2. heap, RSS, retained-View counts, or background-work counters identify hidden sessions as the
   cause;
3. lifecycle-aware collection alone does not address the cost; and
4. a page-level policy can define which state survives recreation.

If triggered, compare the current keep-alive behavior with an explicit opt-in dispose-when-hidden
policy. The default remains unchanged unless same-device memory and switching evidence shows a
clear net benefit and the state-loss contract is acceptable. Plain `remember` state and active
effects must never be implied to survive a disposed session.

## Explicitly deferred or rejected work

The following decisions are historical inputs to scheduling, not unassigned backlog:

### Keep View measurement and LayoutParams semantics

Do not add a Compose constraint engine, general custom measure policy, intrinsic-measurement parity,
or fractional fill surface. Use built-in containers, AndroidX ConstraintLayout, or a lifecycle-owned
custom Android `ViewGroup`.

### Keep Modifier as immutable renderer input

Do not expose application-defined `Modifier.Node` attach/detach, invalidation, local-read, layout,
draw, input, or semantics capability interfaces. Add reviewed contract elements and renderer
support only for demonstrated framework features.

### Keep UiLocal lookup separate from observation

Changing local values remain backed by ViewCompose State or another explicit host invalidation
source. Do not make all local reads tracked merely to resemble `CompositionLocal`.

### Keep navigation state closed and saveable

Do not accept arbitrary object routes, Fragment/Activity destinations, general Navigation3 scenes,
action/MIME matching, or a complete `NavOptions` clone. Complex domain objects are loaded by stable
identifier after navigation.

### Keep AndroidView transaction phases

Replay-safe update/reset, post-tree-commit work, and one-shot permanent release remain separate.
Compose callback parity is not a reason to remove `onCommit` or weaken rollback cleanup.

### Keep hidden-session disposal explicit and conditional

The current keep-alive model remains the default. A memory optimization must not silently reset
plain remember state, effects, native widget state, or page-local resources.

### Defer direct NavigationEvent and arbitrary subtree ViewModel scopes

Revisit direct NavigationEvent only when nested dispatch, forward events, official test fakes, or
Preview inspection is a product requirement not served by the Activity compatibility path. Revisit
arbitrary subtree ViewModel scope only after parent factory/extras inheritance is complete and a
non-navigation scope has a concrete lifecycle owner.

### Defer derived-state notification optimization

Equal-result suppression remains a performance experiment owned by a separate benchmark-triggered
plan. Nested and failure correctness tests in this plan do not authorize that optimization.

## Validation matrix

| Area | Minimum evidence before completion |
| --- | --- |
| Fragment host | JVM/Robolectric lifecycle tests plus Fragment view recreation instrumentation or equivalent device evidence |
| RenderSession | runtime tests for caller calls, queued callbacks, repeated disposal, cleanup failures, and diagnostics |
| AndroidView | renderer transaction tests for factory/update/reset/commit/release across commit, rollback, replacement, and disposal |
| Deep links | navigation-core resolver tests, public host tests, guide/migration agreement, and malformed-input coverage |
| Navigation owners | entry and graph tests for custom Factory, extras, SavedStateHandle, process recreation, and cross-stack isolation |
| Atomic pop-to | controller mutation tests, host rollback tests, lifecycle/transition ordering, and adaptive-pane coverage |
| Logical edges | renderer tests for LTR/RTL and direction changes plus representative device/layout certification |
| Keyed identity | remember/effect/observation/saveable movement tests, abort tests, duplicate-key diagnostics, and unchanged-tree performance |
| WindowInsets | nested/mixed tree instrumentation, IME and system-bar device coverage, listener rollback/reuse/disposal tests |
| Existing ViewTree host | owner discovery, missing-owner failure, disposal, detach/recreate, and leak-safe cleanup tests |
| Box match-parent | native measurement and parent-data tests proving the child does not determine wrap-content Box size |
| Restoration | registry failure tests and non-navigation process-kill certification |
| Retention | same-device heap/RSS/View/effect counters and switching-time comparison before any policy decision |
| Documentation | migration matrix, owning module manuals, guides, KDoc/Javadoc, compiled samples, and Chinese mirrors updated with implemented behavior |

Minimum repository gates for every implementation slice:

```bash
./gradlew verifyDocumentationStructure
./gradlew qaQuick
```

Run `./gradlew qaFull` and the applicable connected/device procedures whenever a phase changes
Fragment lifecycle, process restoration, WindowInsets, IME, Predictive Back, or other device-owned
behavior. Record device readiness and any test that could not run; do not silently replace device
evidence with JVM-only assertions.

## Documentation and API quality impact

Before changing a public or protected API, the implementation slice must assign its Q level and
identify every applicable lifecycle, ownership, error, threading, persistence, layout-unit, and
performance contract. The same slice includes canonical-English KDoc/Javadoc, compiled Q3 samples
where required, owning-module documentation, migration updates, and Chinese mirrors for active
public pages.

Use the documentation impact matrix as follows:

- Fragment entry behavior updates the Android aggregate manual; low-level session behavior updates
  the host-engine manual. Both update the host migration page and applicable architecture/guide
  pages;
- key, snapshot, remember, or saveable behavior updates Runtime/UI Foundation manuals and the state
  migration page;
- logical edges, Box parent data, or Insets update contract/renderer module manuals and the layout
  migration page;
- navigation owner, command, deep-link, or retention behavior updates both navigation module
  manuals, navigation guide, and navigation migration page; and
- a durable ownership decision that crosses modules receives an ADR when the implementation makes
  it costly to reverse.

Every publication-relevant production change adds one immutable `release/changes/*.json` file and
lists it under exactly one active implementation plan. Dependency propagation remains release
planner output and must not be hand-written.

## Completion criteria

This plan is complete when all of the following are true:

1. every P0 and required P1/P2 item is implemented and verified, or handed off to a separately
   indexed child plan with no duplicated source of truth;
2. keyed identity and Insets experiments each have an explicit keep, simplify, or revert decision
   with retained evidence;
3. conditional retention and deferred items have their trigger result recorded and do not remain
   ambiguous backlog;
4. source contracts, tests, migration pages, architecture/guide pages, module manuals, samples, and
   localized mirrors agree on every changed capability;
5. all plan-owned Changesets are listed and the applicable quick, full, benchmark, and device gates
   pass or have an explicit blocker;
6. durable decisions and final measured results have moved into active documentation; and
7. this file moves to `docs/archive/`, both plan indexes are updated, and release-plan validation
   succeeds before the affected Maven Central upload.

## Evidence ledger

Record each scheduled slice here before implementation and update it after validation.

| Date | Phase or slice | Baseline/evidence | Decision | Changeset or follow-up plan |
| --- | --- | --- | --- | --- |
| 2026-08-05 | Initial planning | Migration documents, current source contracts, and existing focused tests reviewed | Plan created; no implementation selected | None |

## Decision history

- 2026-08-05: prioritize Android lifecycle, ownership, state identity, RTL, Insets, and atomic
  navigation semantics over Compose API-count parity.
- 2026-08-05: preserve native View measurement, immutable Modifier/VNode inputs, explicit
  RenderSession ownership, and prepare/commit/rollback boundaries.
- 2026-08-05: require baseline-first, separately revertible experiments for keyed sibling movement,
  nested Insets consumption, and hidden destination retention.
- 2026-08-05: reject compiler work and record custom measurement, `Modifier.Node`, snapshot
  collections/flow, general scenes, Fragment-in-tree, and broad route/NavOptions parity as non-goals.
