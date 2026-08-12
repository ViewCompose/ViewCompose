# ADR-0008: Transactional effect lifecycle

- Status: Accepted
- Date: 2026-08-12

## Context

ViewCompose uses Android View as its rendering engine and `ComposerLite` as a compiler-independent
composition runtime. Its first effect APIs resemble Jetpack Compose, but their internal guarantees
are uneven:

- `RememberObserver` callbacks run during prepared-composition commit while `DisposableEffect` uses
  a separate slot list and later queue;
- `rememberUpdatedState` writes ordinary snapshot state during candidate composition, so an aborted
  candidate can change the value observed by a previously committed effect;
- disposable cleanup is cleared before invocation, but a throwing cleanup can leave the old slot
  keyed and inactive without an explicit lifecycle state;
- a candidate remembered value created in an existing scope that is later detached can receive the
  wrong terminal callback;
- `rememberCoroutineScope` inserts a local `SupervisorJob`, changing child-failure propagation from
  the structured scope contract its name implies; and
- effect callbacks can read a composition Local after its provider stack has returned and silently
  receive a default unrelated to their declaration site.

These are runtime design defects, not Demo or Material-specific behavior. They affect resource
observation, lifecycle adapters, animation, design systems, saveable state, overlays, and every
future integration that owns work across frames.

The Compose compiler cannot be adopted as an implementation detail. ViewCompose therefore cannot
rely on generated restart groups, call-site identities, changed flags, stability inference,
composable call restrictions, or movable-group behavior. Matching API names without defining an
independent transactional contract would hide rather than solve this boundary.

## Decision

1. One remember-slot lifecycle state machine owns `RememberObserver`, `DisposableEffect`, and
   `LaunchedEffect` transitions. The legal states are pending, active, and terminal; the runtime
   transitions state before user code so terminal callbacks are at-most-once even when they throw.
2. `DisposableEffect` is a remembered lifecycle object. The independent disposable-effect slot
   list and commit queue are removed. Its public API requires one or more keys and returns cleanup
   only through `DisposableEffectScope.onDispose`.
3. Candidate composition remains isolated. Candidate-only remembered values are abandoned, not
   forgotten, and committed values remain active after abort. `rememberUpdatedState` uses a
   composer-owned committed holder: composition can read its candidate value, while existing
   effects receive that value only after successful commit.
4. A committed frame publishes remembered values and committed holders before lifecycle callbacks.
   It then dispatches every outgoing lifecycle before any incoming lifecycle and finally runs
   `SideEffect`. All synchronous operations are serialized and attempted despite unrelated
   failures. The first failure is reported with later failures suppressed.
5. `SideEffect` without keys runs after every successful invocation. Keyed overloads run on initial
   commit and when structural key equality changes. Candidate abort discards both forms.
6. `LaunchedEffect` and `rememberCoroutineScope` use the render session's coroutine context.
   `LaunchedEffect` cancels its job on replacement or exit. A remembered coroutine scope owns a
   normal child `Job`; a supplied context cannot replace that job. Invalid job-bearing input returns
   a failed scope rather than detaching ownership.
7. Composition lifetime, render visibility, Android lifecycle, and process lifetime remain distinct.
   UI Foundation owns composition effects. `viewcompose-lifecycle-androidx` owns start/resume paired
   effects and lifecycle state observation. Retaining a hidden session does not automatically pause
   arbitrary composition coroutines.
8. Effect lambdas capture resolved Local values during declaration. The runtime does not reinstall
   the entire Local stack around arbitrary synchronous or asynchronous work. A marked effect
   callback cannot read a Local and accidentally consume a default or an unrelated provider active
   on its thread; deferred child composition continues to use explicit Local snapshots.
9. Effect APIs are Q3. Their KDoc and compiled samples define key comparison, positional identity,
   phase order, rollback, cancellation, dispatcher/thread ownership, cleanup, failure behavior, and
   the compiler-independent structural limitations.
10. ViewCompose promises behavior protected by its own tests, not identical Compose internals.
    Fixed-arity overloads improve source ergonomics but cannot create compiler-generated identity or
    skipping semantics.

## Public API and module impact

- `viewcompose-runtime` hardens `ComposerLite` remember lifecycle and committed-value behavior. The
  low-level disposable slot API is removed from its alpha public surface.
- `viewcompose-ui-foundation` hard-cuts `DisposableEffect`, adds keyed `SideEffect` overloads,
  aligns coroutine ownership, and changes the implementation contract of `rememberUpdatedState`.
- `viewcompose-lifecycle-androidx` adds Q3 paired lifecycle effects and lifecycle-state observation.
- first-party integrations migrate in the same change; no deprecated compatibility layer retains
  the unsafe disposable cleanup shape.

## Consequences

- A failed View-tree candidate can no longer update callbacks seen by the committed frame.
- Disposable and launched effects receive the same pending/active/terminal accounting as other
  remembered resources, including correct abandonment after complex structural change.
- Cleanup and replacement ordering is simpler to reason about and test. A throwing callback is
  observable but cannot cause double cleanup or prevent unrelated transitions from being attempted.
- Consumers must migrate every `DisposableEffect` call to explicit keys and `onDispose`.
- A child launched from `rememberCoroutineScope` can fail its remembered scope. The session-level
  supervisor still isolates independent composition-owned scopes while preserving local structured
  ownership.
- Exact Compose compiler behavior remains unavailable. Positional identity still depends on
  ViewCompose structural groups and explicit `key` usage.

## Rejected alternatives

### Keep `DisposableEffect` as a separate runtime slot type

Rejected because it duplicates identity, rollback, removal, error, and disposal logic already
required by remembered resources. The duplication caused ordering differences and invalid states.

### Update `rememberUpdatedState` from ordinary `SideEffect`

Rejected because lifecycle callbacks, including a newly launched coroutine, run before
`SideEffect`. The new coroutine could observe the previous value during its initial execution.

### Compose inside a long-lived mutable snapshot

Rejected for this change because applying arbitrary composition writes after the native tree commit
introduces snapshot conflicts and broadens composition-write semantics beyond the effect defect.
Committed holders solve the required isolation without turning every composition into a mutable
transaction.

### Reinstall all composition Locals around every effect callback

Rejected because asynchronous work can outlive its declaring stack, Locals may contain host-scoped
objects, and implicit capture would retain stale contexts. Explicit value capture and specialized
Local snapshots keep ownership visible.

### Pause all effects when rendering is inactive

Rejected because render visibility is not Android lifecycle. Background synchronization,
navigation retention, and an off-screen stopped owner require different policies. Lifecycle-bound
work uses the lifecycle integration APIs.

## Validation and rollout

Implementation was completed under the archived
[transactional effect runtime convergence plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/effect-runtime-convergence.md).
Retention requires failure-injection tests for every lifecycle transition and phase, public API
samples, first-party migration, module and Compose-migration documentation, Chinese mirrors,
immutable release changesets, and the repository's quick and full quality gates.
