# Lifecycle and Saved State

## 1. Purpose

This document defines the commit and restoration boundaries for host lifecycles, lifecycle-aware
Flow collection, and `rememberSaveable`.

Core principles:

1. An uncommitted composition must not permanently consume restored values.
2. A host save during composition preparation must not lose values that are currently claimed.
3. A Flow has at most one active collector while the lifecycle changes rapidly.
4. A destroyed host cannot create a new render session or SavedState binding.

## 2. Host lifecycle

A `ComponentActivity.setUiContent` session is bound to the Activity lifecycle. A
`Fragment.setUiContent` session is bound to the Fragment view lifecycle and is released when the
view is destroyed.

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

## 4. rememberSaveable restoration transaction

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

## 5. Android Bundle boundary

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

## 6. Verification

Core regression coverage includes:

1. nullable values, nested collections, and custom Savers;
2. restoration retry after composition abort;
3. host saving while a claim is in flight;
4. collector serialization during rapid lifecycle stop/restart;
5. destroyed owners;
6. unknown Bundle versions and isolation of one corrupt entry.
