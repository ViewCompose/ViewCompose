# Android Resource Environment Plan

## Status

Implementation complete; final connected validation blocked. ADR-0007 is accepted, the
implementation and durable documentation are landed in the isolated worktree, and this plan
remains the source of truth until the connected-device gate completes.

This plan is canonical English-only under the documentation-governance policy. Durable public
behavior will move into active architecture, guide, migration, tooling, and module documentation
before this file moves to `docs/archive/`.

Last verified: 2026-08-12.

Next action: wake and manually unlock connected device `RFCR301L2JN`, rerun `./gradlew qaFull`, and
archive this plan only if the connected suites pass and all durable contracts remain current.

## Maven release changesets

- `release/changes/20260812-android-resource-environment.json`

## Objective

Close the Android resource and configuration gap in the View-based engine with one host-owned,
design-system-neutral architecture that:

1. resolves common Android resources directly in declarative content;
2. invalidates mounted roots automatically after configuration changes;
3. keeps Android environment values, resource qualifiers, named design-system tokens, native
   bindings, images, and retained child sessions coherent;
4. supports imperative application locale/theme resource changes through one host-scoped refresh
   controller;
5. keeps constructor-sensitive root Context replacement explicit; and
6. proves the contract through compiled samples, automated tests, previews, and a reproducible Demo
   matrix rather than page-local invalidation state.

## Scope

The implementation may change:

- `viewcompose-ui-contract`: environment and image-request revision identity;
- `viewcompose-ui-foundation`: resource-revision Local/environment propagation and delayed Local
  snapshot behavior;
- `viewcompose-host-android`: Android resource environment, lifecycle, controller, common lookup
  APIs, escape hatches, samples, and tests;
- `viewcompose-android`: automatic Activity/Fragment installation and advanced stable-context
  refresh assembly;
- `viewcompose-renderer-android`: resource-ID rebinding and normalized image request construction;
- `viewcompose-image-coil` and `viewcompose-image-glide`: resource-revision cache/request identity;
- `viewcompose-material3` and `viewcompose-material3-android`: consume the neutral host revision and
  remove standard-host duplicate configuration observation;
- native preview runner and Compose preview host: provide matching configured Android resources;
- `app`: resource fixtures, a configuration verification surface, stable test tags, and connected
  device coverage; and
- active architecture, guides, migration, tooling, module manuals, compiled samples, public API
  comments, Chinese mirrors, and immutable release Changesets.

## Non-goals

This plan does not:

- make UI Foundation or Android Renderer depend on Material, One UI, or any named design system;
- add `@StringRes`, `@ColorRes`, or `@DimenRes` overloads to every component;
- turn Android resource IDs into a platform-neutral cross-platform resource model;
- expose or retain mutable `Drawable`, `TypedArray`, XML parser, raw stream, or font ownership
  through the common typed lookup family;
- automatically reconstruct native Views whose constructors consumed a different style identity;
- support in-place switching between structurally different root design systems;
- use `Resources.getSystem()` or a process-global application Context as root resource truth;
- make Demo language/theme state part of a framework module; or
- optimize configuration invalidation before correctness, lifecycle, and retained-session behavior
  are protected.

## Verified baseline

Verified from commit `08bb5f7179a8533950762283b046396e90ba25cb` on 2026-08-12:

1. `Text`, `Button`, and related DSL APIs accept resolved `String`, ARGB, and `UiDp` values. No
   composition-aware Android resource lookup family exists.
2. `ComponentActivity.setUiContent` and `Fragment.setUiContent` call
   `AndroidEnvironmentBridge.fromContext` once before creating the render session and retain the
   resulting immutable `UiEnvironmentValues`.
3. `UiEnvironmentValues` contains density/font scale, locales, and layout direction. It has no
   identity for qualifier changes such as night mode, orientation, screen size, or imperative
   resource mutation.
4. `NodeBindingDiffer` performs a full rebind when the captured environment changes. Equal resource
   IDs otherwise remain equal through modifier, NodeSpec, and image request comparison.
5. A resource-backed image may be routed through an installed image loader.
   `ImageRequestBindingController` suppresses replacement when loader identity and normalized
   request equality are unchanged.
6. `Material3ThemeTokenLifecycle` is the only mounted Android configuration observer. It refreshes
   Material tokens and a Material context wrapper but does not invalidate ordinary resources or
   neutral/other design-system roots.
7. Lazy, pager, overlay, and navigation child sessions capture `UiLocalSnapshot`. Their update
   paths can carry a new snapshot, but no host resource revision currently causes the parent to
   publish one.
8. Native preview constructs a configured Android Context and separately supplies static
   environment values. Neither native nor Compose preview installs a resource lookup Local.
9. The Settings Demo keeps a remembered language selection index, displays that language switching
   is not implemented on this baseline, and contains only `app_name` in `values/strings.xml`.

## Required architecture and API contract

### Resource environment ownership

1. One `AndroidResourceEnvironment` provider owns the active root Context, resource resolution,
   configuration callbacks, revision publication, and cleanup.
2. Standard Activity/Fragment roots install it automatically. A low-level `renderInto` caller must
   install it explicitly when content uses Android resource APIs.
3. The provider registers at most once while mounted and unregisters on composition/session
   disposal. Recomposition does not duplicate callbacks or retain a dead Activity/Fragment.
4. Initial resolution and every refresh use the same stable root Context identity that created the
   root and overlays.
5. Refresh ordering is: assert main thread, update an optional stable context wrapper, reread
   resources/environment, advance revision, then schedule affected composition work.
6. A refresh failure does not publish a partial snapshot. The previous resource/environment
   snapshot remains active and the failure follows the existing render/effect reporting boundary.

### Public resource family

1. Common APIs are standalone resolved-value functions used as normal component arguments:
   `Text(stringResource(R.string.title))`, not component-specific resource overloads.
2. The Q3 family covers strings, formatted strings, plurals, colors, logical dimensions, pixel
   dimensions, booleans, integers, string arrays, and integer arrays.
3. Every lookup uses the provider's themed Context/Resources and preserves Android's normal
   `Resources.NotFoundException` and formatting failure behavior.
4. Returned arrays/collections are caller-owned snapshots. `dimensionResource` returns `UiDp` by
   converting Android's resolved pixel value with the same resource density; the pixel API retains
   Android's rounded pixel-size semantics.
5. `LocalAndroidContext.current` and `LocalAndroidResources.current` are Q2 escape hatches for
   uncommon lookups and interop. Access without a provider throws `IllegalStateException` with an
   installation path.
6. Public KDoc states host requirements, observation behavior, main-thread/session ownership,
   Android theme/configuration semantics, results, and failures. The Q3 compiled sample uses the
   full common family without private APIs.

### Invalidation and renderer behavior

1. `resourceRevision` starts at zero for deterministic non-Android/default environments and is
   monotonic within one mounted Android resource environment.
2. Every VNode captures the revision through `UiEnvironmentValues`. A changed revision makes
   `NodeBindingDiffer` rebind the node even if its NodeSpec and modifiers compare equal.
3. Direct resource-ID drawables and images resolve again under the current Context.
4. `UiImageRequest` carries the revision only as resource invalidation identity. URL/URI/model-only
   requests do not restart solely because the Android configuration changed.
5. Coil and Glide include the revision in request/cache identity when the primary or fallback chain
   uses Android resources. They retain normal cache behavior for resource-free requests.
6. Custom renderers and loaders may ignore the new alpha field only if they document the resulting
   inability to refresh resource-qualified content; first-party implementations must honor it.

### Design-system and root-context boundary

1. Host Android observes configuration; Material 3 does not run a parallel observer under the
   standard host.
2. Material's stable context wrapper refreshes before the host rereads environment/resources, and
   Material token mapping runs from the resulting resource revision.
3. One UI/static/product token providers need no Android callback of their own. If they read Android
   resources, they use the same host provider or an explicit application mapping above the engine.
4. An imperative resource/theme mutation that does not dispatch Android configuration calls
   `AndroidResourceRefreshController.refresh()` once for the owning host.
5. Replacing a root design system, theme wrapper identity, or constructor-sensitive style still
   calls `setUiContent`/`setMaterial3UiContent` again and reconstructs the root/session.

### Delayed sessions and previews

1. Lazy items, pager pages, overlays, and navigation destinations receive the updated resource
   Local and revision through their captured snapshot update paths.
2. A retained child newly entering the visible set renders the latest snapshot before presentation.
3. Hidden retained children are not all eagerly recreated merely because a revision advanced.
4. Native preview resource APIs resolve from `PreviewAndroidContextFactory`'s configured Context;
   locale, direction, density, font scale, dimensions, and night qualifiers agree with preview
   metadata.
5. Compose preview bridge installs the same provider for its mounted Android container. Session
   replacement on preview configuration change remains deterministic.

## Implementation phases

### Phase 1: Portable revision transport

1. Add and document `UiEnvironmentValues.resourceRevision` with a deterministic zero default.
2. Add the matching UI Foundation Local and `Environment.resourceRevision` query.
3. Preserve the field through `UiEnvironment`, `Environment.values`, VNode emission, equality, and
   existing custom/default environment construction.
4. Add contract/foundation tests for default, nested override, VNode capture, and Local snapshot
   restore behavior.

### Phase 2: Android resource environment and lookup APIs

1. Add `AndroidResourceRefreshController`, the mounted environment lifecycle, main-thread checks,
   registration/disposal, and atomic snapshot publication in host-android.
2. Add `AndroidResourceEnvironment`, `LocalAndroidContext`, `LocalAndroidResources`, and the common
   typed lookup family.
3. Install the provider in neutral Activity/Fragment roots with an advanced pre-refresh hook for
   stable themed Context wrappers.
4. Add Robolectric coverage for qualifiers, formatting/plurals, dimensions, theme colors,
   arrays, callback ordering, manual refresh, duplicate registration, disposal, and missing-host
   failures.
5. Add canonical Q2/Q3 KDoc and host-android/Android aggregate compiled samples.

### Phase 3: Renderer, images, and retained child sessions

1. Rebind resource-ID-backed modifiers and direct media after revision changes.
2. Extend normalized resource-backed image request identity and update Coil/Glide mappings without
   invalidating resource-free requests.
3. Test lazy/pager session updater behavior, overlay surface snapshots, and navigation retained-page
   refresh with a changed resource revision.
4. Verify failure rollback and session/owner identity remain unchanged.

### Phase 4: Material and preview convergence

1. Make Material 3 token resolution observe `Environment.resourceRevision` and retain the explicit
   low-level imperative refresh path where required.
2. Remove Material's duplicate standard-host `ComponentCallbacks` lifecycle.
3. Wire the named Material Android host so its stable wrapper refreshes before the neutral resource
   snapshot is published.
4. Install the resource provider in native preview runner and Compose preview host using their
   configured Context/environment snapshots.
5. Add Material host and preview tests for locale/night/density changes and lifecycle cleanup.

### Phase 5: Demo configuration verification

1. Add English and Simplified Chinese string resources plus plural, color, dimension, boolean,
   integer, string-array, integer-array, and qualifier-specific drawable fixtures.
2. Replace the Settings placeholder with a focused configuration verification entry or surface.
3. Let the Demo select locale, day/night, font scale/density, and direction by updating one stable
   configuration Context and calling the host refresh controller. Pages read resource/environment
   values directly; they do not subscribe to a per-page language invalidation state.
4. Display stable facts for current resource revision, locale/direction/density/font scale,
   resolved strings/plurals, color, dimension, arrays, and drawable variant.
5. Add test tags and instrumentation that changes every supported axis without recreating the
   verification Activity, then asserts existing Text/native View identity where appropriate and
   updated resource evidence.
6. Keep the Demo evidence inside a lazy session and protect pager/lazy token updates, overlay
   snapshots, and retained navigation destinations with focused automated tests so delayed-session
   propagation cannot depend on manual inspection.

### Phase 6: Durable documentation, release intent, and gates

1. Update architecture overview, multi-design-system standard, theming/image/migration/preview
   pages, and every affected published module manual only after implementation matches them.
2. Update all required Simplified Chinese mirrors and reviewed source fingerprints.
3. Add immutable Changesets for every published artifact whose production source, publication
   input, or compiled sample changes. Classify data-class ABI changes as breaking and do not
   hand-write reverse-dependency propagation.
4. Replace this plan's `- None.` entry with every owned Changeset path.
5. Run API documentation audits, both-locale documentation checks, focused unit/instrumentation
   tests, `qaQuick`, and `qaFull` before moving durable conclusions and archiving this plan.

## Validation

### Focused JVM/Robolectric tests

```bash
./gradlew :viewcompose-ui-contract:test
./gradlew :viewcompose-ui-foundation:testDebugUnitTest
./gradlew :viewcompose-host-android:testDebugUnitTest
./gradlew :viewcompose-android:testDebugUnitTest
./gradlew :viewcompose-renderer-android:testDebugUnitTest
./gradlew :viewcompose-image-coil:testDebugUnitTest
./gradlew :viewcompose-image-glide:testDebugUnitTest
./gradlew :viewcompose-material3:testDebugUnitTest
./gradlew :viewcompose-material3-android:testDebugUnitTest
./gradlew :viewcompose-preview-runner:testDebugUnitTest
./gradlew :viewcompose-preview:testDebugUnitTest
```

Exact task names will be verified against Gradle before execution; unavailable aggregate tasks will
be replaced by the owning module's declared test task and recorded here.

Completed focused validation on 2026-08-12:

- the UI contract, UI Foundation, Host Android, neutral Android host, Renderer, Coil, Glide,
  Material, Material Android host, Preview Runner, Preview, and Navigation focused JVM/Robolectric
  tasks passed;
- `:app:compileDebugKotlin` and `:app:compileDebugAndroidTestKotlin` passed; and
- `ResourceConfigurationDeviceTest` passed on a connected Samsung SM-G991B running Android 13,
  preserving Activity/root identity across locale, night, font-scale, density, and RTL changes.
- the strict API documentation audits for all 11 affected published modules passed;
- `./gradlew verifyViewComposeReleaseIntent qaQuick` passed 1,615 tasks, including published-module
  tests, local publication, API documentation, and compiled tutorial/sample verification;
- website type checking, language placement, translation freshness, and both locale static builds
  passed; the final route verifier then reported the clean worktree's pre-existing absence of
  generated historical/current API pages; and
- `./gradlew qaFull` reached the connected-device preflight after its cached `qaQuick` dependency,
  but the preflight correctly refused to bypass the Samsung device's secure keyguard. No connected
  suite ran during that attempt; the focused resource-configuration device test above remains the
  completed device evidence for this change.

### API and documentation gates

```bash
./gradlew auditViewComposeApiDocs \
  -PviewComposeDocsModules=viewcompose-ui-contract,viewcompose-ui-foundation,viewcompose-host-android,viewcompose-android,viewcompose-renderer-android,viewcompose-image-coil,viewcompose-image-glide,viewcompose-material3,viewcompose-material3-android,viewcompose-preview-runner,viewcompose-preview
./gradlew verifyDocumentationStructure
cd website && npm run verify:languages && npm run verify:translations && npm run typecheck && npm run build
```

### Repository and device gates

```bash
./gradlew qaQuick
./gradlew qaFull
```

`qaFull` requires an unlocked online device. If it cannot run, record the exact preflight blocker
and do not mark the device-dependent completion criteria satisfied.

### Manual Demo matrix

1. Open the resource/configuration verification surface and record its root/session identity plus
   initial resource revision.
2. Switch Chinese/English without recreating the Activity. Verify title, formatted string, plural,
   array, locale tag, and direction update together.
3. Switch day/night and verify resource color, qualifier drawable, Material token evidence, and
   native bound properties update under the same root.
4. Change font scale and density. Verify `Environment.density`, an Android dimension resource, and
   text/layout evidence converge without per-page state wiring.
5. Reveal a previously retained pager/lazy/navigation surface and open an overlay. Verify their
   first visible frame uses the latest resource revision.
6. Trigger an imperative no-configuration refresh and verify the revision advances once while an
   unchanged resource-free image request is not restarted.
7. Replace the root Context/design system and verify the old session is disposed and constructor-
   sensitive Views are reconstructed under the new Context.

## Documentation and API impact

- Q3 API families: Android resource provider, refresh controller, typed resource lookups, standard
  host refresh options, and configuration-sensitive Material integration.
- Q2 API values: resource revision fields and Android Context/Resources escape-hatch accessors.
- Applicable contract fields: resolved behavior, units and formatting inputs, snapshot ownership,
  state observation/equality, root and callback lifecycle, main-thread confinement, callback
  ordering, missing-provider and Android lookup failures, configuration/theme behavior, cache and
  rebind cost, alpha binary compatibility, and root-replacement limits.
- Compiled Q3 samples: common resource lookup, imperative host refresh, Material named-host refresh,
  and configured preview resource resolution.
- Behavior/default/lifecycle documentation: host, UI Foundation, UI contract, renderer/image,
  Material, preview, migration, and Demo verification pages.
- Publication impact: every changed published production source or compiled sample receives one
  immutable per-PR Changeset classification before implementation is considered complete.

## Completion criteria

This plan is complete only when:

1. standard Activity and Fragment roots automatically refresh environment/resources after Android
   configuration callbacks and dispose their observers with the owning lifecycle;
2. common typed resource APIs and escape hatches have Q2/Q3 contracts, compiled samples, and
   missing-provider/qualifier tests;
3. Material and non-Material roots consume the same host revision without a Material-owned neutral
   observer;
4. resource-ID modifiers, direct images, Coil, and Glide honor resource revision while
   resource-free requests retain stable identity;
5. lazy, pager, overlay, and navigation child sessions present the latest captured resource
   snapshot without losing retained session/owner identity;
6. native and Compose previews resolve resources from their configured Context;
7. the Demo changes locale, day/night, font scale/density, and direction without Activity
   recreation or per-page invalidation state and passes connected-device verification;
8. constructor-sensitive root Context changes remain explicit root/session replacement;
9. public KDoc, compiled samples, active docs, module manuals, Chinese mirrors, and immutable
   Changesets are current;
10. focused tests, API docs, documentation gates, `qaQuick`, and available `qaFull` device gates
    pass; and
11. durable conclusions move to active documentation before this plan moves to `docs/archive/` and
    both plan indexes are updated.
