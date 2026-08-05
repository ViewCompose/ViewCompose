# ViewModel Integration

`viewcompose-viewmodel` connects ViewCompose composition scopes to AndroidX `ViewModelStoreOwner`,
`ViewModelProvider`, creation extras, and `SavedStateHandle`. Android hosts provide owner locals;
navigation destinations and graph scopes override them so model lifetime follows declarative page
ownership rather than always expanding to the Activity.

## Artifact and stability

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-viewmodel:0.1.0-alpha01")
}
```

- Stability: **Alpha**. Owner, key, factory, and saved-state contracts are reviewed and tested;
  naming may still evolve between alphas.
- Platform: Android library with a minimum SDK inherited from the repository Android policy.
- Widget core and AndroidX ViewModel/SavedState support are exposed transitively because their
  builder, owner, factory, creation-extra, ViewModel, and `SavedStateHandle` types appear in public
  APIs.
- It does not create or clear host owners; ownership remains with Activity, Fragment, navigation, or
  a custom container.

## Owner propagation

Standard Android hosts install their nearest owner as `LocalViewModelStoreOwner`. Navigation renders
install a destination owner by default; `ProvideNavGraphOwner` replaces it with the selected graph
owner for that subtree. This is what allows page ViewModels to clear on pop while graph-scoped models
survive across multiple destinations in one graph instance.

`LocalViewModelStoreOwner.current` is nullable for optional infrastructure. `viewModel()` and
`savedStateHandle()` require an owner and report a direct configuration error when none is installed.
Custom hosts can use `ProvideViewModelStoreOwner(owner) { ... }`. Providing an owner never clears its
store; the component that created it must clear at the intended terminal lifecycle boundary.

Delayed child sessions capture this local with their declaration context, avoiding accidental
fallback to a different Activity owner when overlay or retained navigation content renders later.

## Resolving a ViewModel

```kotlin
class ProfileViewModel : ViewModel()

fun UiTreeBuilder.ProfilePage() {
    val model = viewModel<ProfileViewModel>()
    // Render observable model state.
}
```

Resolution follows AndroidX `ViewModelProvider`:

1. use the explicit owner, otherwise `LocalViewModelStoreOwner.current`;
2. use the explicit factory, otherwise the owner's default factory, otherwise
   `NewInstanceFactory`;
3. use explicit creation extras, otherwise copy the owner's default extras, otherwise use empty
   extras;
4. query the owner's store by explicit key or AndroidX's class-derived default key.

Calls must run on the Android main thread during composition. The owner's `ViewModelStore` is the
authoritative cache: recomposition and repeated calls return the same instance until the store is
cleared.

## Keys and lookup identity

A null or blank key selects the default identity derived from the ViewModel class. Supply a stable,
nonblank key to keep multiple instances of the same type in one owner:

```kotlin
val primary = viewModel<ProfileViewModel>(key = "primary")
val comparison = viewModel<ProfileViewModel>(key = "comparison")
```

Owner, key, factory, extras, and model class form the composition's provider-lookup identity. When
one changes, ViewCompose performs a fresh provider lookup. That does not force recreation: if the new
lookup addresses an existing owner/key entry, AndroidX returns the stored instance and ignores the
new factory or extras for that instance.

Do not use a changing object or call-order counter as a key. Navigation already supplies independent
owners for distinct destination and graph instances; add application keys only for multiple models
that intentionally share one store.

## Factory and CreationExtras

An explicit factory has priority over the owner's default. Explicit extras likewise have priority.
When defaults are used, ViewCompose copies the owner's extras into `MutableCreationExtras`; it does
not expose or mutate a potentially shared owner object.

Factories and extras affect initial creation, not existing store entries. If a model needs a
`SavedStateHandle`, use an owner that implements AndroidX's saved-state factory/extras contract or
provide a compatible override. Constructor or factory failures propagate to the caller; model
recoverable creation failures explicitly at the host boundary rather than returning a partial model.

## SavedStateHandle convenience

```kotlin
fun UiTreeBuilder.Filters() {
    val handle = savedStateHandle(key = "filters")
    // Read and write values supported by AndroidX SavedStateHandle.
}
```

`savedStateHandle()` stores one handle inside `SavedStateHandleHolderViewModel`. Repeated calls with
the same owner and key return the same handle, and the holder survives configuration changes with its
store. Use distinct stable keys for independent handle namespaces; the default key represents one
general-purpose handle per owner.

Process-death restoration additionally requires a saved-state-aware owner, default factory, and
creation extras. Activity, Fragment, navigation destination, and navigation graph owners provide
that integration. A bare `ViewModelStoreOwner` with `NewInstanceFactory` cannot construct or persist
the handle automatically.

The holder class is public only so AndroidX factories can construct it. Application code should use
`savedStateHandle()` rather than request the holder directly.

## Navigation ownership

- A destination-scoped ViewModel survives recomposition, transition, temporary invisibility, and
  retained-tab switching, then clears when its entry permanently leaves all stacks.
- A graph-scoped ViewModel survives destination changes inside that graph instance and clears after
  its final descendant leaves retained navigation state.
- Pushing the same route twice creates separate destination owners.
- Entering the same graph route again later creates a new graph owner and model store.
- Activity-scoped models require an explicit Activity owner when destination content has overridden
  the current local.

These rules keep page state independent without requiring Activity or Fragment per destination.

## Testing

Use a real `ViewModelStore` in unit tests, render the same call repeatedly, and clear the store during
teardown. Verify stable reuse, different keys, explicit owner replacement, factory priority, extras,
missing-owner failure, and `onCleared` at the owning boundary. Use saved-state-aware Robolectric or
instrumented owners for process-death `SavedStateHandle` tests.

## Related documentation

- [Android host module](../viewcompose-host-android/README.md)
- [Navigation Android module](../viewcompose-navigation/README.md)
- [Lifecycle and saved-state architecture](../../architecture/lifecycle-and-saved-state.md)
- [Source documentation and API comment standard](../../project/api-documentation-quality.md)

The complete generated reference is available in the
[`viewcompose-viewmodel` API tree](https://docs.viewcompose.com/api/viewcompose-viewmodel/current/).

## Compatibility notes

The `0.1.0-alpha01` line establishes nullable owner lookup, nested owner provision, AndroidX store
identity, explicit/default factory and extras precedence, keyed instances, and SavedStateHandle
holders. Keep the owner—not composition call position—as the authoritative lifetime boundary.
