---
schema_version: 2
document_id: module.viewcompose-viewmodel-androidx
doc_type: module
owner:
  kind: module
  id: viewcompose-viewmodel-androidx
version_lane: released
capability_ids:
  - viewmodel.owner-boundaries
  - viewmodel.scoped-owners
  - viewmodel.store-resolution
  - viewmodel.saved-state
artifact_ids:
  - viewcompose-viewmodel-androidx
sample_ids:
  - module.viewmodel-dependency
  - module.viewmodel-owner-boundary
  - module.viewmodel-resolution
  - module.viewmodel-saved-state
  - module.viewmodel-scoped-owners
coordinate: com.viewcompose:viewcompose-viewmodel-androidx:0.1.0-alpha02
minimal_usage_sample_id: module.viewmodel-dependency
---

# AndroidX ViewModel Integration

`viewcompose-viewmodel-androidx` connects ViewCompose composition scopes to AndroidX `ViewModelStoreOwner`,
`ViewModelProvider`, creation extras, and `SavedStateHandle`. Android hosts provide owner locals;
navigation destinations and graph scopes override them so model lifetime follows declarative page
ownership rather than always expanding to the Activity.

## Artifact and stability

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="viewmodel-androidx-module-dependency" sample_id="module.viewmodel-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-viewmodel-androidx:0.1.0-alpha02")
}
```

- Stability: **Alpha**. Owner, key, factory, and saved-state contracts are reviewed and tested;
  naming may still evolve between alphas.
- Platform: Android library with a minimum SDK inherited from the repository Android policy.
- UI Foundation and AndroidX Lifecycle 2.11 ViewModel/SavedState support are exposed transitively
  because their builder, owner, factory, creation-extra, ViewModel, and `SavedStateHandle` types
  appear in public APIs.
- It does not create or clear host owners; ownership remains with Activity, Fragment, navigation, or
  a custom container.

## Owner propagation

Standard Android hosts install their nearest owner as `LocalViewModelStoreOwner`. Navigation renders
install a destination owner by default; `ProvideNavGraphOwner` replaces it with the selected graph
owner for that subtree. This is what allows page ViewModels to clear on pop while graph-scoped models
survive across multiple destinations in one graph instance.

`LocalViewModelStoreOwner.current` is nullable for optional infrastructure. `viewModel()` requires
an owner and reports a direct configuration error when none is installed. Custom hosts can use
`ProvideViewModelStoreOwner(owner) { ... }`. Providing an owner never clears its store; the
component that created it must clear at the intended terminal lifecycle boundary.

Delayed child sessions capture this local with their declaration context, avoiding accidental
fallback to a different Activity owner when overlay or retained navigation content renders later.

{/* compiled-region source="viewcompose-viewmodel-androidx/src/test/samples/com/viewcompose/viewmodel/samples/ViewModelSamples.kt" region="viewmodel-owner-boundary" sample_id="module.viewmodel-owner-boundary" build_target=":viewcompose-viewmodel-androidx:compileDebugUnitTestKotlin" */}
```kotlin
/** Installs a custom store owner for a nested subtree. */
fun UiTreeBuilder.provideViewModelStoreOwnerSample(
    owner: ViewModelStoreOwner,
): ProfileViewModel {
    lateinit var model: ProfileViewModel
    ProvideViewModelStoreOwner(owner) {
        model = viewModel()
    }
    return model
}
```

## Retained child scopes

Use one `ViewModelScopeProvider` when a Pager page, tab, lazy item, overlay, or custom container
needs a lifetime below its Activity or Fragment but longer than one visible render. The provider
delegates child-store allocation and reference counting to Lifecycle 2.11
`ViewModelStoreProvider`; ViewCompose adds prepared-composition commit/rollback, stable-key
namespacing, terminal no-resurrection, and idempotent lease closure.

{/* compiled-region source="viewcompose-viewmodel-androidx/src/test/samples/com/viewcompose/viewmodel/samples/ViewModelSamples.kt" region="viewmodel-scoped-owners" sample_id="module.viewmodel-scoped-owners" build_target=":viewcompose-viewmodel-androidx:compileDebugUnitTestKotlin" */}
```kotlin
/** Retains one profile subtree below a stable parent and child identity. */
fun UiTreeBuilder.retainedViewModelScopeSample(
    parentOwner: ViewModelStoreOwner,
    parentLifecycleOwner: LifecycleOwner,
): ProfileViewModel {
    val provider = rememberViewModelScopeProvider(
        key = "profile-pane-provider",
        parentOwner = parentOwner,
        lifecycleOwner = parentLifecycleOwner,
    )
    val profileOwner = rememberViewModelStoreOwner(
        key = "primary-profile-pane",
        provider = provider,
    )
    lateinit var model: ProfileViewModel
    ProvideViewModelStoreOwner(profileOwner) {
        model = viewModel()
    }
    return model
}

/** Sends the terminal signal only when the logical profile pane is permanently removed. */
fun removeRetainedProfileScope(provider: ViewModelScopeProvider) {
    provider.clear("primary-profile-pane")
}
```

The APIs separate three roles while sharing one implementation core:

1. `rememberViewModelScopeProvider` binds a stable provider key to the parent store and lifecycle.
   Normal removal of the final committed binding clears all children. Parent destruction preserves
   them for configuration recreation, while the parent's own store remains the finishing fallback.
2. `rememberViewModelStoreOwner` transactionally acquires one child lease. A failed first candidate
   is cleared; aborting a candidate for an already committed child preserves the existing store.
   Forgetting the call releases only temporary use.
3. Retained container engines call `acquireOwner` and close the returned
   `ViewModelStoreOwnerLease` directly. They call `clear(key)` exactly once for permanent logical
   removal and `clearAll()` for permanent provider disposal.

Provider and child keys must be non-null stable values owned by the application or container.
Equal provider keys in one parent share state; equal child keys share only inside that provider.
Position, mutable objects, and incrementing counters are invalid retained identities. Calling
`clear` with active leases marks the child terminal and defers physical cleanup; new acquisition
fails until all old leases close, after which the same key creates a fresh scope. `close`, `clear`,
and `clearAll` are idempotent cleanup operations.

The default child Factory and `CreationExtras` come from the parent and are captured when the
provider is created. Pass a `SavedStateRegistryOwner` to `acquireOwner`, or let
`rememberViewModelStoreOwner` use the current combined owner, when scoped models need
`SavedStateHandle`. Equal live scopes reject inconsistent saved-state or lifecycle boundaries
instead of falling back to an Activity or process-global store.

## Resolving a ViewModel

{/* compiled-region source="viewcompose-viewmodel-androidx/src/test/samples/com/viewcompose/viewmodel/samples/ViewModelSamples.kt" region="viewmodel-resolution" sample_id="module.viewmodel-resolution" build_target=":viewcompose-viewmodel-androidx:compileDebugUnitTestKotlin" */}
```kotlin
class ProfileViewModel : ViewModel()

class SavedProfileViewModel(
    val handle: SavedStateHandle,
    val profileId: String,
) : ViewModel()

/** Resolves one instance from the owner installed by the current Android host. */
fun UiTreeBuilder.viewModelSample(): ProfileViewModel {
    return viewModel()
}

/** Keeps two instances of the same class in one store under stable application keys. */
fun UiTreeBuilder.keyedViewModelSample(
    owner: ViewModelStoreOwner,
): Pair<ProfileViewModel, ProfileViewModel> {
    val primary = viewModel(
        modelClass = ProfileViewModel::class,
        key = "primary-profile",
        owner = owner,
    )
    val comparison = viewModel(
        modelClass = ProfileViewModel::class,
        key = "comparison-profile",
        owner = owner,
    )
    return primary to comparison
}

/** Creates a ViewModel with constructor dependencies and the owner's restored state handle. */
fun UiTreeBuilder.initializerViewModelSample(
    owner: ViewModelStoreOwner,
): SavedProfileViewModel {
    return viewModel(owner = owner) {
        SavedProfileViewModel(
            handle = createSavedStateHandle(),
            profileId = "primary-profile",
        )
    }
}

/** Uses the initializer contract when the model class is selected at runtime. */
fun UiTreeBuilder.kClassInitializerViewModelSample(
    owner: ViewModelStoreOwner,
): SavedProfileViewModel {
    return viewModel(
        modelClass = SavedProfileViewModel::class,
        owner = owner,
    ) {
        SavedProfileViewModel(
            handle = createSavedStateHandle(),
            profileId = "runtime-selected-profile",
        )
    }
}
```

Resolution follows AndroidX `ViewModelProvider`:

1. use the explicit owner, otherwise `LocalViewModelStoreOwner.current`;
2. use the explicit factory, otherwise the owner's default factory, otherwise
   `NewInstanceFactory`;
3. use explicit creation extras, otherwise copy the owner's default extras, otherwise use empty
   extras;
4. query the owner's store by explicit key or AndroidX's class-derived default key.

The initializer overloads accept either a reified type or a runtime `KClass`. Their
`CreationExtras.() -> VM` callback receives the owner's default extras, so constructor dependencies
and `createSavedStateHandle()` remain one creation operation. Existing entries ignore later
initializer callbacks; a failed callback publishes no entry and can be retried.

Calls must run on the Android main thread during composition. The owner's `ViewModelStore` is the
only ViewModel instance cache. Each executed call performs a bounded provider query, so clearing the
store is observable on the next composition instead of returning a stale remembered model.

## Keys and lookup identity

A null key selects the default identity derived from the ViewModel class. Every non-null string is
an explicit AndroidX key and is preserved byte-for-byte, including empty and whitespace-only
strings. Supply a stable application key to keep multiple instances of the same type in one owner,
as shown by the compiled `keyedViewModelSample` above.

Every executed call performs a fresh provider lookup. Changing owner or key can address a different
entry; changing Factory, extras, or initializer does not force recreation when the addressed entry
already exists. Requesting a different model class under one explicit key follows AndroidX
replacement semantics and clears the previous model.

Do not use a changing object or call-order counter as a key. Navigation already supplies independent
owners for distinct destination and graph instances; add application keys only for multiple models
that intentionally share one store.

## Factory and CreationExtras

An explicit factory has priority over the owner's default. Explicit extras likewise have priority.
When defaults are used, ViewCompose copies the owner's extras into `MutableCreationExtras`; it does
not expose or mutate a potentially shared owner object.

Factories and extras affect initial creation, not existing store entries. If a model needs a
`SavedStateHandle`, use an owner that implements AndroidX's saved-state factory/extras contract or
use the initializer overload and `createSavedStateHandle()`. Constructor, initializer, and Factory
failures propagate without publishing a partial model; model recoverable creation failures
explicitly at the host boundary.

## SavedStateHandle ownership

{/* compiled-region source="viewcompose-viewmodel-androidx/src/test/samples/com/viewcompose/viewmodel/samples/ViewModelSamples.kt" region="viewmodel-saved-state" sample_id="module.viewmodel-saved-state" build_target=":viewcompose-viewmodel-androidx:compileDebugUnitTestKotlin" */}
```kotlin
class ProfileFiltersViewModel(
    handle: SavedStateHandle,
) : ViewModel() {
    val selectedFilter = handle.getMutableStateFlow("selected-filter", "all")
}

/** Gives one ViewModel sole write ownership of restored business state. */
fun UiTreeBuilder.savedStateViewModelSample(): ProfileFiltersViewModel {
    return viewModel(key = "profile-filters") {
        ProfileFiltersViewModel(createSavedStateHandle())
    }
}
```

The ViewModel is the only writable owner of restored business state. Use its constructor with the
owner's default saved-state Factory, or call `createSavedStateHandle()` inside a `viewModel`
initializer. Expose `getMutableStateFlow()` or read-only domain operations from the ViewModel and
observe them through the lifecycle integration. Do not create a second snapshot-state adapter or
handle-only model for the same value.

Process-death restoration additionally requires a saved-state-aware owner, default factory, and
creation extras. Activity, Fragment, navigation destination, and navigation graph owners provide
that integration. A bare `ViewModelStoreOwner` with `NewInstanceFactory` cannot construct or persist
the handle automatically.

UI-only state remains owned by ViewCompose `rememberSaveable`; it must not also be written through a
`SavedStateHandle`. The removed `savedStateHandle()` and `SavedStateHandleHolderViewModel` APIs have
no compatibility aliases. Migrate their stable key to the actual business ViewModel key and move
each value into that model's handle before upgrading.

## Navigation ownership

- Navigation entries and graphs lease stores from the same `ViewModelScopeProvider` used by
  arbitrary retained subtrees; navigation owns identity, lifecycle, and terminal signals rather
  than a second store allocator.
- A destination-scoped ViewModel survives recomposition, transition, temporary invisibility, and
  retained-tab switching, and configuration recreation below the same parent store, then clears
  when its entry permanently leaves all stacks.
- A graph-scoped ViewModel survives destination changes inside that graph instance and clears after
  its final descendant leaves retained navigation state.
- Pushing the same route twice creates separate destination owners.
- Entering the same graph route again later creates a new graph owner and model store.
- Activity-scoped models require an explicit Activity owner when destination content has overridden
  the current local.

These rules keep page state independent without requiring Activity or Fragment per destination.

## Testing

Phase 1 runs 21 focused resolution tests, compared with seven in the same test owner before this
change: 14 additional contracts and a normalized threefold suite size (`+200%`). Phase 2 adds 20
scoped-owner contract tests, bringing the owning module to 44/44 passing tests with zero skips,
failures, or errors. The new cases cover provider sharing and isolation, multiple leases,
idempotent close, temporary absence, terminal clear, no resurrection, parent-store cleanup,
Factory/extras/default arguments, inconsistent saved-state boundaries, composition commit and
abort, configuration recreation, delayed-local capture, Pager/lazy/overlay reorder, and
`INITIALIZED`/`DESTROYED` lifecycle diagnostics.
Phase 3 additionally passes 151/151 Navigation Android tests and 21/21 aggregate-host cases. The
navigation suite grew from 148 tests with three focused contracts for missing-owner failure,
configuration-retained entry ViewModels, and prior-format state migration. Aggregate-host source
coverage grew from 10 to 11 test methods and now distinguishes Activity ViewTree discovery from
Fragment explicit-owner precedence.

Phase 4 replaces the removed helper guard with two `SavedStateViewModelIntegrationTest` contracts:
the default Factory injects default arguments into a constructor handle, and an initializer-created
handle plus mutable state flow survives a process-style new-owner/new-store restoration. The owning
module now passes 45/45 tests with zero skips, failures, or errors; Navigation remains 151/151,
Preview runner remains 12/12, and the migrated Demo compiles. Relative to Phase 3, the module suite
has one net additional test because two restoration contracts replace one helper-only guard.

The aggregate Phase 4 acceptance command, `./gradlew qaQuick qaPreview
-PviewComposeReleaseBaseRevision=8c79f2b4`, also completed successfully: 2270 actionable tasks,
with 237 executed and 2033 up-to-date. This confirms that the hard cut remains compatible with the
repository-wide quick and preview gates. Because most aggregate tasks reused verified outputs, the
clean focused runs above remain the absolute test-result evidence; the aggregate run is integration-
gate evidence rather than a fresh performance comparison.

Phase 5 adds seven defect-pressure contracts: explicit-owner precedence over an unrelated local,
nested-local restoration after failure, isolated SavedStateHandle namespaces, single initializer
and provider registration per key, successive restoration without replay, removed-API runtime and
source guards, and a one-store-allocator structural guard. The module grows from 45 to 52 tests,
an absolute increase of seven and a normalized increase of 15.6%; all 52/52 pass with zero skips,
failures, or errors.

The clean affected-layer rerun also passed 151/151 Navigation Android, 21/21 aggregate Android,
and 52/52 Host Android tests. Together with the owning module, this is 276/276 with a normalized
pass rate of 100% and zero skips, failures, or errors. This is broader contract and integration
coverage, not evidence of a runtime-performance change.

Two Debug journeys then passed on one Xiaomi MI 6 running Android 9/API 28. The navigation journey
changed PID from 19002 to 19078 and preserved two stacks plus five independently seeded destination
or graph namespaces, including every `rememberSaveable` and `SavedStateHandle` value. The ordinary
Activity-root journey changed PID from 19210 to 19286 and restored the exact value 41. After PID
normalization, both before/after status records had zero differences.

Conclusion: **improved**. Lookup, creation, general scoped ownership, navigation integration, host
owner selection, regression deletion, and real process recreation now have direct evidence, while
restored business state has one ViewModel owner instead of a framework holder. The device result is
one Android 9 model and one Debug build; release-mode behavior, a broader Android/device matrix,
memory retention, and runtime performance remain **inconclusive** for Phase 6.

Use a real `ViewModelStore` in unit tests, render the same call repeatedly, and clear the store
during teardown. Saved-state-aware Robolectric or instrumented owners remain required for process-
death `SavedStateHandle` tests.

## Related documentation

- [Android host module](../viewcompose-host-android/README.md)
- [Navigation Android module](../viewcompose-navigation-android/README.md)
- [Lifecycle and saved-state architecture](../../architecture/lifecycle-and-saved-state.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-viewmodel-androidx` API tree](https://docs.viewcompose.com/api/viewcompose-viewmodel-androidx/current/).

## Compatibility notes

The Lifecycle 2.11 baseline hard-cuts two Alpha behaviors. Only `null` selects the default key, so a
caller that previously passed `""` or whitespace as a default sentinel must pass `null`; blank keys
now identify explicit entries. The resolved ViewModel is no longer remembered by composition, so a
store clear becomes visible immediately. Initializer overloads replace ad hoc one-class factories
for constructor dependencies. For a lifetime below the host, migrate custom child-store maps to one
stable-keyed `ViewModelScopeProvider`; keep logical removal separate from temporary render absence.
The owner and stable scope key—not composition call position—remain the authoritative lifetime
boundary.
