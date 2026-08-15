# ViewCompose State Snapshots

## 1. Scope

This document defines snapshot semantics and usage constraints for the `viewcompose-runtime` state
system.

Goals:

1. Provide consistent read/write semantics for `MutableState`.
2. Define conflict handling for concurrent writes.
3. Prevent the runtime from regressing to direct assignment without transactions.

## 2. Public API

1. `mutableStateOf(value, policy)`
   - default policy: `structuralEqualityPolicy()`
2. `SnapshotMutationPolicy<T>`
   - `equivalent(a, b)`: determines whether a write is treated as unchanged;
   - `merge(previous, current, applied)`: merges a concurrent conflict; `null` means the conflict
     cannot be merged.
3. `Snapshot`
   - `takeSnapshot()`
   - `takeMutableSnapshot()`
   - `withMutableSnapshot { ... }`
   - `currentGlobalId()`
4. `MutableSnapshot`
   - `enter { ... }`
   - `apply()`
   - `dispose()`

## 3. Core semantics

1. `MutableState` uses an MVCC `StateRecord` chain; a read selects the version visible to its
   `readId`.
2. Outside an explicit snapshot context, `state.value = x` runs an internal autocommit transaction
   through `takeMutableSnapshot + apply`.
3. `MutableSnapshot.apply()` publishes serially:
   - without conflict, it commits directly;
   - with conflict, it calls `policy.merge(previous, current, applied)`;
   - if merge fails, `apply()` returns `Failure`.
4. Read snapshots are isolated: `Snapshot.takeSnapshot().enter { ... }` always reads the versions
   visible to that snapshot, regardless of later global commits.
5. Every `ComposerLite` composition runs within a consistent read snapshot, so reads cannot drift
   within one pass.
6. The runtime tracks the `readId` of active snapshots. Commits retain versions required by active
   readers and prune history that is no longer visible after snapshots are released.
7. One successful global apply gathers affected `Observation` instances in stable unique order and
   invokes each at most once on the applying thread after runtime and state locks are released.
   Separate applies are never debounced together. A conflict or no-op apply emits no invalidation.
8. Framework-owned fields that form one public logical tuple use one existing mutable-snapshot
   transaction. Writer serialization, including `synchronized`, does not make separate commits
   atomically visible to snapshot readers.

## 4. Concurrency and conflict constraints

1. Conflict detection uses state-record versions. A new record created after the transaction
   `readId` is a concurrent write.
2. `equivalent(a, b)` decides only whether one assignment creates a record; it does not infer
   transaction concurrency.
3. Conflicts do not overwrite by default. A commit is allowed only when `merge` supplies a merged
   value.
4. Without merge support, including the default policy, a conflict fails and the caller decides
   whether to retry.

## 5. Development constraints

1. Do not add a runtime state-write path that bypasses snapshots.
2. A new state container integrated with `RuntimeObservation` must implement snapshot visibility.
3. Any policy or conflict-semantic change must add concurrent-transaction and composition
   consistency tests.
4. Call `dispose()` on `Snapshot`/`MutableSnapshot` after use, or close it through `use`, to avoid
   retaining historical versions indefinitely.
5. When adding several framework-owned observable fields, classify whether they are one invariant
   or independent events. Group only the invariant writes in `Snapshot.withMutableSnapshot` and add
   a test that reads the complete tuple from an invalidation callback.

## 6. Related documents

1. [Architecture overview](overview.md)
2. [Performance](../tooling/performance.md)
3. [Development workflow](../project/workflow.md)
