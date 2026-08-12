# ADR-0007: Host-owned Android resource environment

- Status: Accepted
- Date: 2026-08-12

## Context

ViewCompose uses Android View as its rendering engine, but its application DSL currently accepts
already resolved values for common resource-backed properties. `Text` and `Button`, for example,
accept `String` values and the framework does not provide a composition-aware equivalent of
Android's `getString`, `getColor`, `getDimension`, or plural lookup APIs. Applications can reach a
`Context` through unrelated native objects, but that access is neither a declared composition
dependency nor a consistent resource-resolution contract.

The standard Android hosts also read `UiEnvironmentValues` only once when a root is installed.
Density, font scale, locales, and layout direction therefore remain stale when a long-lived host
handles a configuration change without Activity recreation. Resource qualifiers may additionally
change for night mode, orientation, screen size, density, locale, or other configuration axes even
when the currently modeled environment fields compare equal.

Material 3 currently owns the only mounted `ComponentCallbacks` observer and refreshes only its
token snapshot. This is an architectural defect rather than a Material-specific feature gap:
ordinary Android resources, neutral hosts, One UI, product design systems, resource-backed images,
and delayed child sessions do not share that invalidation. Moving more resource behavior into
Material would violate the multi-design-system boundary.

Applications can work around the problem by keeping locale or theme choices in `MutableState` and
making each page read that state. That refreshes only the pages wired to the application state and
does not prove that Android resource qualifiers, environment values, resource IDs, or retained
child sessions have converged.

## Decision

1. `viewcompose-host-android` owns one design-system-neutral Android resource environment. It
   provides the themed root `Context`, current `Resources`, common typed lookup functions, Android
   escape hatches, configuration observation, imperative refresh, and lifecycle cleanup.
2. Standard Activity and Fragment hosts install this environment automatically. Low-level custom
   hosts and preview hosts install the same provider explicitly. Resource lookup outside an active
   provider fails with a clear error instead of reading process-global resources.
3. The first public lookup family mirrors the common read-only Android resource operations:
   `stringResource`, formatted strings, `pluralStringResource`, `colorResource`,
   `dimensionResource`, pixel-size dimensions, booleans, integers, string arrays, and integer
   arrays. DSL components continue to accept resolved values; resource-ID overloads are not copied
   across every `Text`, `Button`, and component API.
4. `LocalAndroidContext` and `LocalAndroidResources` are controlled escape hatches for uncommon
   Android resource APIs. Their values are host-scoped and must not be retained beyond the owning
   session or used as process-global configuration state.
5. `UiEnvironmentValues` gains a monotonic `resourceRevision`. It is a platform-neutral invalidation
   identity, not a persisted version or a semantic configuration model. Every emitted `VNode`
   captures it, so a revision change forces native rebinding even when a drawable or image resource
   ID remains numerically equal.
6. The Android resource environment advances that revision after every relevant Android
   configuration callback and after `AndroidResourceRefreshController.refresh()`. Refresh first
   updates any stable themed-context wrapper, then rereads Android environment values, then
   publishes one observable snapshot. Calls and callbacks are confined to the Android main thread.
7. Material 3 no longer owns standard-host configuration observation. It rereads its tokens when
   the host resource revision changes. Other design systems may consume the same revision without
   being named by Host, Renderer, or UI Foundation. A design system may still own how its stable
   themed-context wrapper is refreshed before the host publishes the new snapshot.
8. Local snapshots carry the Android resource environment and revision through lazy items, pagers,
   navigation destinations, and overlay surfaces. Parent recomposition updates retained child
   sessions before newly visible or rebound content is presented; a child session never falls back
   to a process-global resource source.
9. Resource-backed image requests include the captured resource revision when any source,
   placeholder, error, or fallback uses a resource ID. Renderer and image adapters therefore do
   not suppress a reload solely because the integer IDs are unchanged across configurations.
10. Native preview and Compose-preview bridges use their configured Android Context as the same
    resource source used by the DSL. Static preview configuration remains deterministic; preview
    does not invent a second resource resolver.
11. The Demo includes configuration controls and resource-backed evidence for locale, night mode,
    font scale/density, layout direction, dimensions, colors, plurals, and resource images. Pages
    derive their values from the resource environment and do not subscribe individually to a Demo
    language state merely to trigger recomposition.
12. This mechanism updates resource-derived values and rebindable native properties. It does not
    promise to reconstruct a View whose constructor consumed a different style identity. Changing
    the root design system or another constructor-sensitive root Context still replaces the root
    and its render session under the existing host contract.

## Public API and module impact

- `viewcompose-ui-contract` adds the Q2 immutable `resourceRevision` environment field and the
  resource revision carried by normalized image requests. Both additions change binary data-class
  contracts in the alpha line.
- `viewcompose-ui-foundation` exposes the Q2 resource revision through `Environment` and preserves
  it in Local snapshots and emitted VNodes.
- `viewcompose-host-android` owns the Q3 Android resource provider, refresh controller, lookup
  functions, escape hatches, lifecycle, and compiled samples.
- `viewcompose-android` installs the environment for standard roots and exposes a Q3 host-scoped
  refresh option plus the bounded pre-refresh hook needed by stable themed Context wrappers.
- `viewcompose-renderer-android`, `viewcompose-image-coil`, and `viewcompose-image-glide` honor
  revision changes for resource-ID-backed rendering and loading.
- `viewcompose-material3` and `viewcompose-material3-android` consume the host revision and retain
  Material context/token ownership without retaining a parallel standard-host observer.
- preview runtime modules install the same host-owned resource environment for configured preview
  contexts.
- `app` supplies Demo and connected-device verification only; it owns no framework resource logic.

All new state-owning, Android-boundary, and configuration-sensitive APIs are Q3. Their applicable
contract fields are state ownership, observation and recomposition, host lifecycle and disposal,
main-thread confinement, callback ordering, resource/theme behavior, failure outside a provider,
snapshot return ownership, and retained-session propagation. Scalar immutable environment fields
and read-only escape-hatch accessors are Q2.

## Consequences

- Applications can write `Text(stringResource(R.string.title))` and equivalent resolved-value
  calls without adding resource-ID overloads to every component.
- One host invalidation updates ordinary resources, environment values, named design-system tokens,
  resource-ID drawables, and delayed child-session snapshots coherently.
- The Android Engine owns platform observation while design systems retain only their context and
  token interpretation policy.
- A configuration callback may conservatively rebind nodes whose resolved visual values did not
  change. Correctness takes precedence over a qualifier-diff optimization; profiling may later
  justify a narrower invalidation model without changing the public lookup API.
- Direct constructors and custom renderers using changed alpha data classes must rebuild.
- Custom low-level hosts that want resource APIs or automatic configuration handling must install
  the provider explicitly; `renderInto` remains a low-level mount and does not silently choose a
  Context.

## Rejected alternatives

### Add a resource-ID overload to every DSL component

Rejected because it duplicates resolution policy across components, creates overload growth for
strings, plurals, colors, dimensions, drawables, and formatting, and still leaves arbitrary
application calculations without a resource API.

### Keep configuration observation in Material 3

Rejected because Android resources and configuration are platform-host concerns. It would leave
neutral and other named design systems stale and would make Material an accidental substrate.

### Require every page to observe application language or theme state

Rejected because application state is the policy input, not proof that Android resources changed.
It also misses resource IDs and retained sessions whose value equality does not reflect a qualifier
change.

### Read `Resources.getSystem()` or an application-global Context

Rejected because system resources do not carry the root's application resources, locale override,
theme, dynamic-color wrapper, or preview configuration. A process-global Context also breaks
root/session ownership.

### Store a complete Android `Configuration` in every VNode

Rejected because it leaks a mutable Android platform object into the platform-neutral contract,
inflates equality work, and still does not represent imperative resource/theme mutations. A
monotonic revision is the smallest sufficient invalidation identity.

### Recreate the complete root for every configuration callback

Rejected as the default because it discards retained View/session identity for changes that can be
resolved and rebound safely. Root replacement remains available and required for
constructor-sensitive Context identity changes.

## Validation and rollout

Implementation follows the active
[Android resource environment plan](../../project/plans/android-resource-environment.md). Retention
requires focused unit tests for lookup and lifecycle ordering, renderer/image revision tests,
delayed-session propagation tests, preview tests, Demo instrumentation covering non-recreating
configuration changes, API documentation audits, both-locale documentation gates, and the
repository's quick/full quality gates.
