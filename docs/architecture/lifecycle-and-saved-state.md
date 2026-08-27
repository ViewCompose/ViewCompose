---
schema_version: 2
document_id: architecture.lifecycle-saved-state
doc_type: architecture
owner:
  kind: capability
  id: lifecycle.owner-boundaries
version_lane: released
capability_ids:
  - lifecycle.owner-boundaries
  - lifecycle.flow-collection
  - lifecycle.effects
  - lifecycle.android-view
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-host-android
  - viewcompose-lifecycle-androidx
  - viewcompose-android
sample_ids: []
invariants:
  - Lifecycle and saved-state ownership begins only at a committed host, composition, or native View boundary.
  - Replacement, destruction, and rollback complete old ownership before publishing a new owner.
evidence:
  - Lifecycle module tests, Android host integration tests, compiled module samples, and retained-session tests.
---

# Lifecycle and Saved State

## 1. Purpose

This document defines the commit and restoration boundaries for host lifecycles, lifecycle-aware
Flow collection, committed Android Views, and `rememberSaveable`.

Core principles:

1. An uncommitted composition must not permanently consume restored values.
2. A host save during composition preparation must not lose values that are currently claimed.
3. Independent child compositions must not share one flat provider-key namespace.
4. A Flow has at most one active collector while the lifecycle changes rapidly.
5. A destroyed host cannot create a new render session or SavedState binding.
6. A renderer-owned View cannot observe or publish an external owner before its transaction commits.

## 2. Host lifecycle

A `ComponentActivity.setUiContent` session is bound to the Activity lifecycle. A
`Fragment.setUiContent` session is bound to the Fragment view lifecycle and is released when the
view is destroyed. Activity content uses the Activity for both lifecycle and saved state. Fragment
content deliberately uses the Fragment View owner for lifecycle and the Fragment for saved state,
so View work ends at `onDestroyView` while compatible SDK state can survive View recreation.

Activity/Fragment entry and automatic owner installation belong to `viewcompose-android`.
`viewcompose-host-android` owns the low-level session, scheduler, `renderInto`, and Android saved-
state bridge; it must not regain Activity/Fragment convenience APIs. Lifecycle-aware collection
and ViewModel access remain in their named AndroidX integrations.

Boundary rules:

1. Calling `setUiContent` again releases the previous session first.
2. `ON_DESTROY` releases the session, composition effects, coroutines, and platform resources.
3. Calling `setUiContent` on a `DESTROYED` host fails immediately without creating a partially
   bound session.
4. A `LifecycleBoundDisposer` releases immediately when it is bound to an already destroyed owner.

## 3. Lifecycle-aware Flow collection

`collectAsStateWithLifecycle` accepts only these active thresholds:

- `CREATED`
- `STARTED`
- `RESUMED`

`INITIALIZED` and `DESTROYED` cannot be active thresholds.

The implementation uses the serial cancel-and-restart semantics of `repeatOnLifecycle`. The
previous collector must finish cancellation and `finally` cleanup before the next collector starts,
preventing concurrent collection during a rapid `STOP -> START` sequence. Disposing the
composition cancels the complete structured collection scope.

## 4. Committed Android View owner coordination

Reusable native-View integrations use the typed Android Host adapter for transaction ownership and
the AndroidX lifecycle integration for owner coordination. Replay-safe View construction and update
run during renderer apply. Lifecycle and saved-state bindings begin only from the post-commit hook;
an abandoned or rolled-back candidate therefore cannot observe an owner, consume restored SDK state,
or publish a provider.

One View has at most one lifecycle binding. Initial attachment catches up to the captured owner's
current state in Android event order. Replacing an owner completes the old View-side downward
sequence and removes its observer before the new commit work and upward catch-up start. A retained
navigation destination supplies its capped destination owner, not the Activity owner, so hidden
content cannot keep media surfaces, map work, or camera capture active merely because its View
remains mounted. Reset, final release, owner destruction, and callback failure perform bounded,
one-shot cleanup.

The lifecycle adapter coordinates View-side events; it does not control application-owned playback,
permissions, credentials, lifecycle state, or SDK object ownership. The Host records the adapter's
lifecycle mode for diagnostics but does not install observers itself.

## 5. rememberSaveable restoration transaction

Restoration has four steps:

1. During composition preparation, claim the restored value with a stable key through
   `claimRestored`.
2. The claimed value participates in restoration but remains included in the `performSave()`
   snapshot.
3. After composition commit, register the provider and then commit the claim.
4. If composition aborts or the new value is abandoned, release the claim so a later retry can
   restore the same value.

Composition exceptions, renderer apply rollback, and interleaved save/render operations therefore
cannot discard a restored value early.

Changing the inputs to `rememberSaveable(inputs...)` still means an intentional reset. The old
holder leaves only during commit, the new holder takes over the provider synchronously, and the
replacement value is what is eventually saved.

## 6. Child-composition ownership

The host registry is the root persistence boundary, not a global key namespace for every nested
`RenderSession`. Framework containers that create delayed child compositions remember a state
holder in their parent composition. The holder provides one child registry per stable lazy-item,
Pager-page, tab, or overlay-surface identity.

Automatic and explicit `rememberSaveable` keys are local to the receiving child registry. Nested
containers create another holder inside that registry, so ownership follows the composition tree:

```text
host owner
└── container holder
    ├── logical item A registry
    │   └── nested container holder
    └── logical item B registry
```

Recycling closes an item registry lease and retains its saved map in the holder; reattaching the
same logical key restores it. A keyed reorder therefore moves the logical state with the key rather
than with the View holder position. A concurrent renderer-created presentation replica may restore
the owner's current snapshot, but it is not a second persistence owner.

The holder itself is saved through the parent registry's normal transaction. A failed parent frame
cannot publish candidate child ownership, and a failed child frame retains the child's previous
providers and restored claims. See
[ADR-0010](./decisions/0010-hierarchical-saveable-state-ownership.md) for the hard-cut rationale and
compatibility boundary.

## 7. Android Bundle boundary

The Android host saves:

- `null`;
- platform values supported by Bundle;
- recursive `List` values;
- `Map` values with String keys.

The host class loader is installed during restoration. An unknown format version is ignored as a
whole; one corrupt entry is isolated and does not prevent the remaining valid entries from being
restored.

Transient system sessions are not SavedState. IME composition, undo history, in-progress gestures,
and animations are not restored.

An SDK View that owns a Bundle payload registers one provider only after its adapter commit. Its
stable provider key is scoped to the nearest saved-state owner and is separate from renderer
reconciliation identity. The SDK integration owns the payload schema and a positive format version;
the framework owns registration order, one-shot restored-value consumption, defensive Bundle
copies, replacement, and cleanup. A format mismatch or corrupt nested SDK payload is treated as
absent without invalidating other providers. Later commits replace only the saver so host saving
always reads the latest committed View.

## 8. Verification

Core regression coverage includes:

1. nullable values, nested collections, and custom Savers;
2. restoration retry after composition abort;
3. host saving while a claim is in flight;
4. collector serialization during rapid lifecycle stop/restart;
5. destroyed owners;
6. sibling and nested child compositions using identical automatic and explicit keys;
7. keyed child recycling, reorder, host recreation, and concurrent presentation replicas;
8. unknown Bundle versions and isolation of one corrupt entry;
9. Android View post-commit lifecycle catch-up, serial owner replacement, and callback failure;
10. retained-destination lifecycle capping, SDK Bundle recreation, format isolation, and provider
    cleanup.
