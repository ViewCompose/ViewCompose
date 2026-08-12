# Transactional Effect Runtime Convergence Plan

## Status

Implementation and migration are complete. Focused runtime, UI Foundation, lifecycle, API,
documentation, release-intent, sample, and repository quick gates pass. The plan remains active
because the final `qaFull` acceptance gate is not green: the main Demo instrumentation suite has 15
failures that reproduce with the same test names and assertions in a clean worktree at the
pre-implementation revision. The directly affected resource-configuration device test passes.

Last verified: 2026-08-12.

## Maven release changesets

- `release/changes/20260812-android-resource-environment.json` (combined branch changeset)

## Objective

Make composition effects a transactional, deterministic core capability rather than a collection
of API-shaped helpers. The completed runtime must guarantee that:

1. an aborted candidate cannot publish a value, start work, replace a subscription, or affect a
   previously committed effect;
2. remembered resources, disposable effects, launched coroutines, and committed value holders use
   one lifecycle state model;
3. outgoing work ends before replacement work starts, synchronous callbacks remain serialized, and
   one failure does not skip unrelated cleanup or publication;
4. `SideEffect` runs only after lifecycle callbacks for the same committed frame;
5. coroutine ownership, cancellation, error propagation, host lifetime, and Android lifecycle
   lifetime are explicit and independently selectable; and
6. public similarity to Jetpack Compose is claimed only where tests protect the same observable
   contract without relying on the Compose compiler plugin.

## Scope

The implementation may change:

- `viewcompose-runtime`: remember-slot lifecycle state, prepared commit/abort behavior, committed
  value publication, keyed one-shot scheduling support, `snapshotFlow`, effect diagnostics, and
  focused samples;
- `viewcompose-ui-foundation`: `SideEffect`, `DisposableEffect`, `LaunchedEffect`,
  `rememberCoroutineScope`, `rememberUpdatedState`, and `produceState` contracts and implementation;
- `viewcompose-lifecycle-androidx`: lifecycle-bound effect APIs and lifecycle state observation;
- first-party modules and Demo call sites that use the hard-cut disposable-effect API;
- architecture, migration, module manuals, compiled samples, API comments, Chinese mirrors, and
  immutable release changesets; and
- unit, Robolectric, failure-injection, and integration tests for every affected phase.

## Non-goals

This plan does not:

- introduce the Compose compiler plugin, `@Composable`, compiler-generated restart groups, changed
  flags, stability inference, or compiler-enforced call restrictions;
- promise source or binary compatibility for the current alpha effect APIs;
- make a retained but non-rendering `RenderSession` equivalent to an Android stopped lifecycle;
- capture and restore every `UiLocal` automatically around asynchronous callbacks;
- treat a resource `Context`, an `Activity`, a `Window`, and a `LifecycleOwner` as one capability;
- pause arbitrary coroutines when a View becomes invisible; or
- claim identical internal implementation, stack traces, dispatch context, or movable-group
  behavior to Jetpack Compose.

## Normative phase model

One successful rendered frame follows this order:

1. prepare a candidate composition in a pinned read snapshot;
2. build and transactionally commit the native View tree;
3. commit scope, observation, remember-slot, and committed-value state;
4. end outgoing remembered/disposable/launched lifecycles;
5. start incoming remembered/disposable/launched lifecycles;
6. run unkeyed and newly-keyed `SideEffect` callbacks in declaration order;
7. run renderer-owned native `onCommit` callbacks;
8. reconcile overlays and publish diagnostics.

Steps 3 through 6 are serialized by the owning composer. A failure after step 2 is a committed-frame
failure: it is reported, later independent operations are still attempted, and the runtime never
pretends the previous View tree is authoritative. An abort before step 2 restores the previous
composition and abandons candidate-only remembered values without starting them.

## Lifecycle state machine

Every remembered value that implements `RememberObserver` receives one runtime-owned state token:

- `Pending`: created by a candidate and not yet committed;
- `Active`: its remember callback was dispatched for a committed frame;
- `Terminal`: it was forgotten or abandoned and cannot receive another callback.

The only legal transitions are `Pending -> Active -> Terminal` and `Pending -> Terminal`.
`Pending -> Terminal` dispatches abandonment, while `Active -> Terminal` dispatches forgetting.
The runtime marks a transition before invoking user code, making cleanup at-most-once even when the
callback throws. Repeated disposal and overlapping structural cleanup therefore cannot double-run
user cleanup.

`DisposableEffect` is implemented as a remembered lifecycle object, not a separate effect-slot
system. A successful setup owns exactly one disposal result. Setup failure leaves no active cleanup
and is not retried until identity changes or the call re-enters composition. Cleanup is cleared
before invocation and is never retried after throwing.

## Public API hard cut

### UI Foundation

1. `DisposableEffect` requires at least one key and receives a `DisposableEffectScope`. Its final
   expression must be `onDispose { ... }`; the lambda-return cleanup form is removed.
2. `LaunchedEffect` requires at least one key. One-, two-, three-, and vararg-key entry points share
   the same structural equality and restart contract.
3. Unkeyed `SideEffect` runs after every successful invocation. Keyed overloads run on initial
   commit and whenever their key list changes by structural equality.
4. `rememberCoroutineScope` returns one stable composition-owned scope whose job is a normal child
   of the session coroutine job. A supplied context containing `Job` produces a failed scope that
   cannot launch work; it does not detach ownership.
5. `rememberUpdatedState` exposes the candidate value to the composing call while publishing it to
   already-running committed effects only after commit. Abort discards the candidate value.
6. `produceState` retains the same state holder, restarts its producer by explicit keys, and runs an
   `awaitDispose` callback exactly once when cancellation reaches the suspension point.

All of these APIs are Q3 because they own state or resources, launch asynchronous work, or
participate in the composition transaction.

### Lifecycle integration

`LifecycleStartEffect` and `LifecycleResumeEffect` bind paired setup/cleanup work to `STARTED` and
`RESUMED` respectively. They require explicit keys, observe the nearest or supplied owner, restart
on key or lifecycle identity change, perform cleanup on the matching down event or composition
exit, and never conflate lifecycle inactivity with composition disposal.

`Lifecycle.currentStateAsState()` exposes lifecycle state through composition-owned observable
state and removes its observer on disposal. These APIs are Q3.

## Composition Local and host rules

Effect callbacks capture resolved values during composition. Reading `Theme.current`,
`Environment`, or another `UiLocal.current` from a callback after the provider stack has returned is
not a supported way to recover the declaring environment. Marked effect callbacks must fail clearly
for Local reads so neither a default nor an unrelated provider on the callback thread can hide the
mistake. Specialized deferred child composition continues to use the existing explicit
`UiLocalSnapshot` transport.

Android host operations resolve explicit capabilities from the host layer. Resource lookup remains
owned by `AndroidResourceEnvironment`; Activity, Window, and lifecycle capabilities remain distinct
and are not added to a design-system API.

## Implementation phases

### Phase 1: Freeze contracts and migration

1. Accept the effect-runtime ADR and add the current architecture page.
2. Record compiler-independent guarantees and unavoidable Compose differences.
3. Document the public hard cut and update English/Chinese module and migration pages.
4. Assign Q3 to every changed effect API and identify lifecycle, ordering, cancellation, rollback,
   failure, threading, and host contract fields.

### Phase 2: Transactional remember and committed values

1. Add explicit pending/active/terminal state to remember slots.
2. Make commit collect outgoing, abandoned, and incoming transitions before dispatch.
3. Dispatch all outgoing transitions before any incoming transition and aggregate failures.
4. Make abort abandon only candidate values and restore every previous slot unchanged.
5. Add a composer-owned committed-value holder for `rememberUpdatedState` with candidate-thread
   visibility and commit/abort publication.
6. Remove the independent disposable-effect slot list and queue.
7. Add cold `snapshotFlow` collection with per-collector read tracking, invalidation conflation,
   conditional dependency replacement, distinct emission, and cancellation cleanup.

### Phase 3: Public effect APIs and coroutine ownership

1. Hard-cut `DisposableEffect` to `DisposableEffectScope.onDispose` and mandatory keys.
2. Add fixed-arity and vararg key overloads with shared internal implementations.
3. Add keyed `SideEffect` scheduling while preserving unkeyed every-commit behavior.
4. Change remembered coroutine scopes from local `SupervisorJob` to a normal child `Job` and return
   a failed scope for invalid supplied job contexts.
5. Update every first-party consumer and compiled Q3 sample.

### Phase 4: Lifecycle effects and diagnostics

1. Add start/resume paired lifecycle effects and `currentStateAsState` to
   `viewcompose-lifecycle-androidx`.
2. Include effect kind, structural scope/slot, key summary, frame, and failure operation in debug
   diagnostics without retaining arbitrary key objects after disposal.
3. Add slow synchronous-effect warnings behind existing debug diagnostics; production behavior
   remains synchronous and unchanged.
4. Add focused out-of-context Local diagnostics without changing delayed-session snapshot transport.

### Phase 5: Test matrix and gates

Protect at least these cases at runtime, UI Foundation, lifecycle, and `RenderSession` levels:

- initial enter, equal-key reuse, changed-key replacement, structural removal, and session disposal;
- candidate abort during composition and during native View rendering;
- nested scopes, multiple effects, explicit `key` groups, skipped groups, and structural drift;
- setup failure, cleanup failure, multiple simultaneous failures, and later successful frames;
- exact callback ordering across committed-value publication, outgoing lifecycle, incoming lifecycle,
  unkeyed/keyed side effects, native commit, and overlay commit;
- launched coroutine start/cancel/error ownership and remembered-scope child failure;
- `rememberUpdatedState` visibility before commit, after commit, and after abort, including an
  already-running coroutine reading the holder;
- `snapshotFlow` initial/distinct emission, invalidation conflation, conditional dependencies,
  collection cancellation, calculation failure, and observer release;
- lifecycle stop/start/resume/pause/destroy, key replacement, owner replacement, and composition
  exit; and
- repeated disposal, callback re-entry attempts, thread confinement, and no retained observers.

Then run:

```bash
./gradlew :viewcompose-runtime:test
./gradlew :viewcompose-ui-foundation:testDebugUnitTest
./gradlew :viewcompose-lifecycle-androidx:testDebugUnitTest
./gradlew verifyDocumentationStructure
./gradlew qaQuick
./gradlew qaFull
```

## Validation record

Verified on 2026-08-12:

- `:viewcompose-runtime:test`, `:viewcompose-ui-foundation:testDebugUnitTest`, and
  `:viewcompose-lifecycle-androidx:testDebugUnitTest` pass;
- `auditViewComposeApiDocs` passes for the three affected published modules;
- `verifyDocumentationStructure`, `verifyViewComposeReleaseIntent`, compiled samples, and
  `:app:compileDebugKotlin` pass;
- `qaQuick` passes all 1,615 tasks, including repository unit tests, documentation structure and
  language gates, API documentation, local publication, release intent, and sample builds;
- Counter and Tutorials instrumentation suites pass under `qaFull`;
- `ResourceConfigurationDeviceTest` passes independently on the connected Android 13 device,
  protecting same-Activity and same-root language, night mode, font scale, density, layout
  direction, revision, resource-value, and system-bar appearance updates; and
- the complete main Demo instrumentation suite reports 15 failures. A clean detached worktree at
  the pre-implementation revision reports the same 15 test names and assertions, establishing that
  this change introduces no new device-suite failure while not claiming that `qaFull` passes.

## Next action

Restore the pre-existing main Demo instrumentation baseline in its owning work, rerun `qaFull`,
record the green result here, then move this plan to `docs/archive/` and update both plan indexes.

## Completion conditions

This plan is complete only when:

1. every normative phase and lifecycle transition above is protected by focused tests;
2. no first-party call site uses a removed API shape;
3. API dumps, KDoc, compiled Q3 samples, module manuals, migration guidance, and Chinese mirrors are
   current;
4. all affected published artifacts are classified in immutable release changesets;
5. focused tests, documentation checks, `qaQuick`, and `qaFull` pass; and
6. durable conclusions have moved to active architecture/module/migration documents before this
   plan moves to `docs/archive/`.
