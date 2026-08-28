---
schema_version: 2
document_id: architecture.retained-viewmodel-scope-ownership
doc_type: architecture
slug: /architecture/decisions/retained-viewmodel-scope-ownership
owner:
  kind: capability
  id: viewmodel.owner-boundaries
version_lane: version-agnostic
capability_ids:
  - viewmodel.owner-boundaries
  - viewmodel.scoped-owners
  - viewmodel.store-resolution
  - viewmodel.saved-state
artifact_ids:
  - viewcompose-viewmodel-androidx
  - viewcompose-navigation-android
  - viewcompose-host-android
  - viewcompose-android
sample_ids: []
invariants:
  - A ViewModelStore is the only ViewModel instance cache; composition retains only provider bindings and reference-owning leases.
  - Every child scope has caller-owned stable identity, reference-protected temporary retention, and an explicit terminal-removal signal.
  - Navigation, ordinary composition subtrees, and custom retained containers use one provider core while keeping their distinct lifecycle adapters.
evidence:
  - docs/archive/viewmodel-androidx-optimal-architecture-and-compose-parity.md
  - viewcompose-viewmodel-androidx/src/test/java/com/viewcompose/viewmodel/ViewModelScopeProviderTest.kt
  - viewcompose-viewmodel-androidx/src/test/java/com/viewcompose/viewmodel/ViewModelScopeCompositionTest.kt
  - AndroidX Lifecycle 2.11 ViewModelStoreProvider reference and source contracts
---

# ADR-0023: Retained ViewModel scope ownership

- Status: Accepted
- Date: 2026-08-29

## Context

Before this decision, ViewCompose resolved Activity-, Fragment-, navigation-entry-, and navigation-
graph-scoped ViewModels but had no general child-scope facility for Pager pages, tabs, lazy items,
overlays, or application containers. Navigation compensated with a specialized
`NavEntryOwnerStore`, while `viewModel()` also remembered the resolved instance in composition even
though `ViewModelStore` already owned that identity. The standalone `savedStateHandle()` helper
added a second holder model instead of using ViewModel construction and `CreationExtras`.

AndroidX Lifecycle 2.11 adds
[`ViewModelStoreProvider`](https://developer.android.com/reference/kotlin/androidx/lifecycle/viewmodel/ViewModelStoreProvider),
whose child stores survive configuration changes through a parent store and whose reference tokens
defer terminal clearing during exit animation or another temporary consumer. Its low-level contract
does not know whether a ViewCompose candidate committed or rolled back, whether a render absence is
temporary, or which navigation event is terminal. Copying its Compose adapter would therefore be
insufficient for ViewCompose's prepared-composition, delayed-session, and retained-stack model.

## Decision

### One store and one scoped-provider core

1. Upgrade the executable AndroidX Lifecycle baseline to 2.11. `ViewModelStoreProvider` is the sole
   child-store allocation and reference-counting primitive. ViewCompose does not implement a
   parallel child-store map.
2. `ViewModelStore` is the sole cache of ViewModel instances. `viewModel()` performs a provider
   lookup on every executed composition call and never remembers a resolved ViewModel instance.
3. The stable capability identity for the new public family is `viewmodel.scoped-owners`. Its
   capability record is added with the first declarations because governance records describe the
   current compiled inventory and cannot be pre-created.
4. The module-owned `ViewModelScopeProvider` wraps the AndroidX provider with ViewCompose commit,
   rollback, no-resurrection, and terminal-disposal state. `ViewModelStoreOwnerLease` is the core
   reference-owning handle used by navigation and custom retained containers. Closing a lease ends
   one use; it does not by itself declare the logical scope permanently removed.

The wrapper namespaces provider and child identities before passing them to AndroidX. Private
provider and child metadata ViewModels live in AndroidX-owned marker and child stores, so commit,
terminal, and no-resurrection state survives recreation of the facade without introducing a second
child-store map. Metadata keeps only weak references to active lifecycle and saved-state owners and
releases those references when the last lease closes. AndroidX remains the sole allocator and
reference counter for child stores.
5. `rememberViewModelScopeProvider` is the composition adapter. It binds provider lifetime to a
   retained parent `ViewModelStoreOwner`, a parent `LifecycleOwner`, and a caller-supplied stable
   provider key. `rememberViewModelStoreOwner` is the child adapter. Existing
   `ProvideViewModelStoreOwner` remains the only local-publication API.
6. The core is shared, while scenario adapters remain separate:
   ordinary DSL content remembers an owner and publishes it; navigation acquires leases and drives
   entry/graph lifecycle plus terminal clear; a custom retained container acquires and closes leases
   directly. Pager, tabs, overlays, and lazy content do not receive parallel provider APIs.

### Identity, reference, and removal protocol

1. Provider and child keys are non-null stable values supplied by the caller or owning container.
   Call position, collection position, incrementing counters, and referentially unstable objects are
   not durable identities. ViewCompose deliberately exposes no automatic-position overload for a
   retained provider.
2. Equal provider keys in the same parent store share provider state. Equal child keys share one
   owner only inside that provider. Equal child keys under different provider keys remain isolated.
3. Preparing a composition binding acquires a reference before application code can use the owner.
   Commit makes the binding durable. Abort releases the candidate reference and clears a scope that
   was created only by the failed candidate; it must not clear a previously committed scope or
   consume its restored state.
4. Temporary render absence closes references but does not request removal. The owning container
   calls `clear(key)` exactly once when the logical destination, item, page, tab, or overlay is
   permanently removed. Active leases defer the underlying clear; after removal is requested, a new
   lease for that identity fails until the final old lease closes. Reusing the key afterward creates
   a fresh scope rather than resurrecting the removed store.
5. Normal provider-subtree removal while its parent lifecycle is not `DESTROYED` requests
   provider-wide terminal cleanup, including removal before the parent reaches `CREATED`. Disposal
   while the parent is `DESTROYED` does not request that cleanup: configuration recreation must
   recover shared provider state, while a finishing parent clears its own store. Clearing the parent
   store remains the final safety boundary.
6. Provider creation, lease operations, ViewModel lookup, and clear operations are Android-main-
   thread confined. They perform bounded in-memory map, provider, and reference-count operations;
   they do no I/O, blocking, scheduling, or global discovery.

### Factory, extras, saved state, and lifecycle

1. Child owners inherit the parent Factory and initial `CreationExtras` unless explicitly
   overridden when the provider is first created. Scoped default arguments take precedence over
   default arguments already present in the extras, following AndroidX. Later recomposition does not
   mutate an existing provider's creation policy.
2. Saved-state support is enabled only when the child owner delegates to a valid
   `SavedStateRegistryOwner`. Standard combined Activity, Fragment, navigation, and Preview owners
   resolve naturally; a custom boundary with separate owners must pass the saved-state owner
   explicitly.
3. A provider requires a parent `LifecycleOwner`, normally the same object as the parent store
   owner. A custom split boundary passes it explicitly. Missing, already-invalid, or inconsistent
   boundaries fail directly instead of falling back to an Activity, static registry, or root store.
4. Navigation continues to own route arguments, entry and graph `LifecycleRegistry` transitions,
   saved-state registry namespaces, transition retention, stack retention, and terminal pop. It
   obtains destination and graph stores from `ViewModelScopeProvider` and deletes its independent
   store-allocation policy after equivalent tests pass.

### ViewModel creation and state interoperability

1. Only `null` selects AndroidX's class-derived ViewModel key. Every non-null string, including
   empty and whitespace-only strings, is an explicit key and is passed unchanged.
2. The existing reified and `KClass` factory/extras overloads remain. Two initializer overloads are
   added: a reified form and a `KClass` form whose `CreationExtras.() -> VM` initializer receives the
   resolved owner's default extras. All overloads delegate to one store-only internal resolver.
3. `savedStateHandle()` and `SavedStateHandleHolderViewModel` have been removed without aliases.
   Business state obtains a handle in a ViewModel constructor or initializer through
   `CreationExtras.createSavedStateHandle()`.
4. No ViewCompose snapshot-state adapter for `SavedStateHandle` is added. UI-only state uses
   `rememberSaveable`; ViewModel business state uses `SavedStateHandle.getMutableStateFlow()` and is
   observed through the existing state-collection integration. This preserves one writable owner
   and one restoration path instead of creating API symmetry with two sources of truth.
5. The released `viewmodel.saved-state` capability record remains only as the alpha01 historical
   identity required by immutable deletion-impact records. Current generated Reference entries are
   derived from compiled declarations and expose neither removed symbol; the record is not a
   compatibility API or an alternate ownership path.

## Frozen public surface

The implementation phases may add overload annotations required by Kotlin/JVM, but they do not
change these consumer-visible roles:

- `ViewModelScopeProvider.acquireOwner(key, savedStateRegistryOwner)` returns a
  `ViewModelStoreOwnerLease`; `clear(key)` and `clearAll()` provide the terminal signals.
- `ViewModelStoreOwnerLease` implements `AutoCloseable` and exposes its read-only
  `ViewModelStoreOwner` as `owner`.
- `rememberViewModelScopeProvider(key, parentOwner, lifecycleOwner, defaultArgs,
  defaultCreationExtras, defaultFactory)` returns one `ViewModelScopeProvider`. `parentOwner`
  defaults to `LocalViewModelStoreOwner.current`; `lifecycleOwner` defaults to the parent cast;
  Factory and extras default to the parent contracts.
- `rememberViewModelStoreOwner(key, provider, savedStateRegistryOwner)` returns a
  `ViewModelStoreOwner`. Its saved-state owner defaults to the current local owner when that owner
  implements `SavedStateRegistryOwner`.

`acquireOwner` exists for navigation and retained-container engines. Ordinary DSL content uses the
two `remember` functions and `ProvideViewModelStoreOwner`; it does not manually retain a lease.
`clear` and `clearAll` are terminal signals, not visibility callbacks.

## Alternatives considered

### Expose only AndroidX `ViewModelStoreProvider`

Rejected because a raw provider cannot distinguish a new owner created by a failed ViewCompose
candidate from an already committed owner, cannot reject resurrection after terminal removal, and
does not bind provider cleanup to ViewCompose's parent-lifecycle rule. It remains the internal
storage primitive rather than the complete public contract.

### Give navigation, Pager, lazy items, and overlays separate owner stores

Rejected because the policies differ only in lifecycle inputs and terminal events. Separate stores
would reproduce the existing navigation specialization, multiply restoration bugs, and prevent one
reference/removal test matrix from protecting every container.

### Clear a child whenever its content leaves composition

Rejected because exit animation, retained navigation stacks, Pager offscreen limits, lazy reuse,
and delayed rendering all make visibility shorter than logical ownership. Reference release and
terminal removal must remain distinct events.

### Retain providers in a process-global registry

Rejected because it outlives Activity and Fragment owners, cannot model process restoration, leaks
application keys, and bypasses AndroidX's configuration-retained parent store.

### Keep blank-key and standalone-handle compatibility

Rejected because the artifact is Alpha and both paths preserve defective ownership. Empty or blank
keys are valid AndroidX explicit identities; a public handle-only holder duplicates the ViewModel
constructor/factory model and reserves an application-visible store key.

## Consequences

- ViewCompose gains the material Lifecycle 2.11 scoped-owner capability without adding Compose as a
  dependency or copying its position-derived persistent identity.
- A provider and one lightweight lease are additional bounded objects around AndroidX state. The
  wrapper complexity is accepted because it protects prepared-composition rollback, terminal clear,
  delayed references, and no-resurrection behavior that a raw adapter cannot express.
- Navigation migrated in one hard cut after parity tests proved entry, graph, multi-stack,
  restoration, transition, and cleanup behavior. It retains identity/lifecycle coordination but no
  longer allocates an independent ViewModelStore.
- Applications using blank keys or the standalone SavedStateHandle helper receive a compile-time or
  behavior break with explicit migration guidance; no deprecated compatibility window is provided.

## Validation and rollout

1. Phase 1 proves store-only lookup, null/non-null keys, Factory/extras precedence, initializer
   failure, `onCleared`, and lookup after clear.
2. Phase 2 proves provider sharing/isolation, commit/abort, multiple leases, temporary absence,
   terminal clear, no resurrection, configuration recreation, provider disposal, saved-state
   defaults, Pager/lazy/overlay reorder, and lifecycle-boundary diagnostics through 20 focused
   scoped-owner contracts. The owning module passes all 44 tests after combining this evidence with
   Phase 1 resolution coverage.
3. Phase 3 passed 151/151 Navigation Android tests and 21/21 aggregate-host cases. Navigation now
   leases entry and graph stores from the shared provider, keeps them across configuration
   recreation through a saved host-scope identity, and clears them at permanent removal. Activity
   hosts discover the installed ViewTree owner; the explicit Fragment owner wins over its
   shorter-lived View owner; nested explicit providers retain precedence. `renderInto` remains
   owner-free. Conclusion: **improved** for ownership and retention relative to the 148-test
   navigation baseline. Device process-kill, memory, and performance evidence remains
   **inconclusive**.
4. Phase 4 passed both constructor/default-Factory and initializer process-style restoration
   contracts and all 45/45 owning-module tests. The holder APIs and reserved key are absent;
   `rememberSaveable` owns UI-only state, while one business ViewModel owns each mutable
   `SavedStateHandle` flow. Conclusion: **improved**. JVM restoration does not replace a device
   process-kill journey, which remains **inconclusive** for Phase 5.
5. Phase 5 passed all 52/52 owning-module tests after adding seven negative and deletion guards;
   the clean affected-layer run passed 276/276 tests, and repository `qaQuick qaPreview` completed
   all 2,270 tasks. On a Xiaomi MI 6 running Android 9/API 28, two Debug process-death journeys
   changed PID and preserved the normalized Activity-root and multi-stack navigation state exactly.
   Conclusion: **improved**. One device does not establish release-mode, memory, performance, or
   platform-matrix behavior; those dimensions remain **inconclusive**.
6. Each phase lands Q3 KDoc, compiled samples, capability-impact records, module and migration
   documentation, immutable release intent, and focused tests with interpreted evidence.
