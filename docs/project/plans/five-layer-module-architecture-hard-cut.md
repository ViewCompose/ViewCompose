# Five-Layer Module Architecture Hard-Cut Plan

## Status

Completed; awaiting release-time archival. The post-implementation ownership audit is closed:
published artifacts now have exclusive canonical package roots, UI Foundation coordinates Android
execution only through opaque platform handles, and Host Android owns the concrete Android
environment, focus, diagnostics, and overlay-provider discovery boundaries. Compatibility facades
for superseded Maven coordinates or package roots remain explicitly out of scope. Code,
publication metadata, samples, tooling, active English documentation, reviewed Chinese mirrors,
and every required gate agree again.

Last verified: 2026-08-06.

Next action: review and commit the completed hard cut, then select its artifacts for a Maven
release. The normal release workflow will freeze the source revision, archive this plan
immediately before publication, and then permit the selected artifacts to enter Maven Central
staging.

## Maven release changesets

- `release/changes/20260806-five-layer-module-hard-cut.json` records the breaking coordinate and
  API changes plus affected existing artifacts.

## Objective

Replace the overloaded `foundation` and `optional capability` classification with five durable
runtime layers whose responsibilities, dependency direction, public API exposure, and naming are
mechanically enforceable:

1. **Kernel** owns deterministic state, immutable contracts, and policy algorithms.
2. **UI Foundation** owns design-system-neutral declarative UI semantics and component contracts.
3. **Android Engine** owns Android View creation, reconciliation, hosting, and AndroidX platform
   adaptation.
4. **Design System** owns Material 3 theme resolution, concrete visual tokens, and Material-backed
   presentation policy.
5. **Integrations** adapt optional AndroidX or third-party capabilities without becoming
   prerequisites of lower layers.

Tooling remains orthogonal and downstream of all five runtime layers. A consumer-facing aggregate
may expose a reviewed default stack, but aggregation never permits a lower layer to depend upward.

## Non-negotiable design principles

### Classify by responsibility, not dependency vendor

AndroidX is permitted infrastructure for Android Engine and explicit AndroidX integrations.
Material is a design-system implementation. A module is not Kernel because it has no Material
dependency, and it is not an Integration merely because it imports AndroidX.

### Dependency direction is one way

The allowed runtime direction is:

```text
Kernel <- UI Foundation <- Design System
   ^             ^
   |             |
Android Engine --+
   ^
Integrations and consumer aggregates
```

An arrow points from a consumer to a dependency. Tooling may consume any reviewed runtime layer;
no runtime layer may consume tooling. Integrations may consume lower layers and narrowly reviewed
peer contracts, but lower layers never depend on integrations.

### Public semantics do not expose implementation widgets

Kernel and UI Foundation public APIs use ViewCompose types. Android Engine may expose Android or
AndroidX types only at explicit interop and host boundaries. Material widget classes and Material
resource identifiers never appear in Kernel, UI Foundation, or Android Engine public/protected
signatures.

### Design defaults and render mechanics evolve independently

Material color, typography, shape, sizing, state-layer, and component-default changes must not
require a Kernel or Android renderer release unless a stable lower-layer contract actually changes.
The renderer consumes resolved node semantics and visual values; it does not choose Material
defaults.

### Optional means removable

Removing an Integration or Design System artifact from a consumer classpath must not prevent the
Kernel, UI Foundation, or Android Engine from compiling. Missing optional capabilities must either
remove only that capability or select an explicit documented fallback.

### Names state the owned responsibility

Artifact names that imply platform neutrality, generic Android behavior, or core ownership while
actually owning a different responsibility are renamed in this hard cut. No deprecated Maven
aliases or forwarding artifacts are retained.

### Ease of use belongs in an aggregate

Ordinary Android applications receive a one-dependency default stack from `viewcompose-android`.
The aggregate may expose Android Host, UI Foundation, and Material 3 intentionally through `api`;
it owns no runtime semantics. Advanced consumers can select lower artifacts directly.

## Target module classification and renames

### Kernel

These names already match their responsibilities and remain unchanged:

- `viewcompose-runtime`
- `viewcompose-text-core`
- `viewcompose-ui-contract`
- `viewcompose-navigation-core`
- `viewcompose-animation-core`
- `viewcompose-gesture-core`
- `viewcompose-graphics-core`

The `-core` suffix is retained only where the artifact is the platform-neutral kernel of a named
capability. Kernel production sources forbid `android.*`, `androidx.*`, and Material imports.

### UI Foundation

- `viewcompose-widget-core` -> `viewcompose-ui-foundation`
- `viewcompose-animation`
- `viewcompose-gesture`
- `viewcompose-graphics`

`viewcompose-ui-foundation` owns the tree builder, composition-facing locals and effects, generic
theme/token schemas, generic components, overlay declarations, and render-session-neutral
contracts. Concrete Material theme reading and values move out. Android execution types that are
not declarative semantics move to Android Engine.

### Android Engine

- `viewcompose-renderer` -> `viewcompose-renderer-android`
- `viewcompose-host-android`

The renderer becomes independent of Material Components. Shape/surface and progress rendering use
engine-owned generic Android implementations driven by resolved node values. Binder and patch
registries remain closed unless a separately justified extension use case requires a narrow SPI;
this refactor does not introduce a general per-node plugin registry merely to move two controls.

The host owns retained Android render sessions, Android environment and focus adaptation, frame
scheduling, lifecycle attachment points, and low-level `renderInto`. It accepts framework token
providers through UI Foundation and has no direct Material dynamic-color policy; the aggregate
performs the default Material 3 assembly above it.

### Design System

- Add `viewcompose-material3`.

This artifact owns Material 3 light/dark defaults, Android Material theme snapshots, Dynamic Color,
Material component tokens, and the provider that resolves a Material-themed Android context into
UI Foundation token schemas. Material dependencies remain `implementation` unless a public API
deliberately exposes an upstream type; the intended public surface exposes ViewCompose token and
resolver types instead.

### Integrations and entry aggregate

- `viewcompose-navigation` -> `viewcompose-navigation-android`
- `viewcompose-lifecycle` -> `viewcompose-lifecycle-androidx`
- `viewcompose-viewmodel` -> `viewcompose-viewmodel-androidx`
- `viewcompose-widget-constraintlayout` -> `viewcompose-constraintlayout-androidx`
- `viewcompose-overlay-android` -> `viewcompose-overlay-material3-android`
- Keep `viewcompose-image-coil`, `viewcompose-image-glide`, and `viewcompose-shadow-android`.
- Add `viewcompose-android` as the recommended consumer aggregate.

The overlay rename records that Snackbar and modal-bottom-sheet presentation are Material-backed;
generic overlay declarations remain in UI Foundation. `viewcompose-android` intentionally exposes
the default Android Host, UI Foundation, Material 3 design system, and reviewed AndroidX lifecycle
and ViewModel integrations while keeping renderer internals private.

### Tooling

Preview, preview runner/worker/Gradle plugin, benchmark, demo, and sample modules keep their current
names. Their dependency declarations are migrated to the new runtime artifacts, and tooling remains
forbidden from leaking into published runtime dependencies.

## Scope

The hard cut includes:

- Gradle project directory and path renames;
- Maven artifact renames and independent first-release metadata;
- production and test source moves required by layer ownership;
- removal of Material from UI Foundation, Renderer Android, and Host Android dependencies;
- a generic UI Foundation token-provider boundary and Material 3 implementation;
- a Material-free renderer shape/progress path;
- the `viewcompose-android` one-dependency aggregate;
- all project dependency declarations, API exposure declarations, samples, tests, preview/runtime
  classpaths, service registrations, release changesets, and publishing smoke consumers;
- the final current architecture, module catalog/manuals, guides, tutorials, migration pages,
  project workflow documents, and Chinese mirrors affected by the hard cut;
- deletion of superseded active module manuals after their replacement manuals and historical
  release evidence remain reachable.

## Non-goals

This plan does not include:

- cross-platform rendering or a non-Android host;
- a compiler plugin or compiler-generated optimization;
- a general third-party renderer plugin API;
- replacing stable AndroidX facilities with framework-owned copies;
- changing Runtime snapshot, recomposition, VNode identity, or renderer transaction semantics;
- preserving source, binary, or Maven-coordinate compatibility for the renamed alpha artifacts;
- splitting every integration into contract/backend pairs when the current seam is already narrow;
- completing unrelated active optimization or Compose convergence plans.

## Execution phases

### Phase 0 — Baseline and hard-cut ledger

1. Run the current focused compile/test baseline and record the revision and result.
2. Capture the current published dependency contract and Material import inventory.
3. Record current Material progress, shape, light/dark theme, and host smoke behavior using existing
   unit/preview coverage before replacing implementations.
4. Add immutable release changesets once the first publication input changes.

Exit gate: failures in the retained baseline are understood before structural changes begin.

### Phase 1 — Layer registry and project renames

1. Replace foundation/optional build classification with explicit five-layer maps and allowed-edge
   checks.
2. Rename Gradle directories/projects and update every project dependency.
3. Update QA task paths, package-root checks, purity checks, and publishing dependency contracts.
4. Keep source movement minimal until all renamed projects configure successfully.

Exit gate: `./gradlew projects` and configuration-only publication verification recognize only the
new artifact names.

### Phase 2 — UI Foundation and Android Engine separation

1. Remove Material theme snapshot/bridge code from UI Foundation.
2. Move Android execution/session/environment code to Host Android where it is not declarative UI
   semantics.
3. Keep `UiTheme` as the generic token-provider boundary without adding an Android resolver SPI.
4. Update Host, Navigation, Overlay, and Preview callers to the new ownership boundary.

Exit gate: UI Foundation compiles without Material; public API checks contain no Material types.

### Phase 3 — Material-free Renderer Android

1. Replace Material shape drawable usage with the generic Android shape bridge.
2. Replace Material progress widgets with engine-owned implementations while preserving node,
   patch, accessibility, animation, measurement, and disposal behavior.
3. Remove Material from Renderer Android and Host Android dependency declarations.
4. Add a classpath smoke test that resolves and runs the base engine without Material.

Exit gate: the Android Engine dependency graph contains no `com.google.android.material` artifact
and focused renderer regressions pass.

### Phase 4 — Material 3 and integration assembly

1. Add Material 3 static defaults and Android theme/Dynamic Color resolver.
2. Assemble the resolver through the explicit `viewcompose-android` composition root rather than
   adding it to the low-level host contract.
3. Rename and reconnect AndroidX/Material integrations.
4. Add `viewcompose-android` and verify the recommended one-dependency application path.

Exit gate: both a Material-free host consumer and the Material3 aggregate consumer compile and run
their smoke checks.

### Phase 5 — Publication and tooling convergence

1. Register every new artifact and retire every superseded coordinate from current publication
   metadata without rewriting immutable release history.
2. Rewrite published `api`/`implementation` contracts and validate generated POM/Gradle metadata.
3. Update Preview, worker, sample, benchmark, and demo dependency paths.
4. Add release changesets for directly changed/new artifacts and concrete ignore reasons where
   appropriate.

Exit gate: local Maven publication and all publishing smoke consumers pass with new coordinates.

### Phase 6 — Documentation convergence

Documentation structure may be inconsistent during Phases 1 through 5. At this phase:

1. update this architecture decision in the active architecture overview;
2. replace module catalog rows and manuals with the final artifact set;
3. update installation snippets, tutorials, guides, migration references, publishing workflow, and
   all reviewed Chinese mirrors;
4. preserve old coordinates only in immutable release history, migration guidance, or archives;
5. update plan status, changeset ledger, and final evidence.

Exit gate: documentation structure, localization, generated API selection, and link checks pass.

### Phase 7 — Full verification and closeout

Run focused tests continuously, then finish with `qaQuick`, `qaRelease`, documentation gates, local
Maven publication smoke tests, and every repository verification task required by the changed
artifacts. Fix regressions until the retained hard-cut tree passes; do not stop at a partially
renamed or compatibility-shim state.

### Phase 8 — Package ownership and responsibility convergence

This phase was added after the initial closeout audit. It is part of the same hard cut because the
affected replacement artifacts have not entered their first Maven release.

1. Replace the retired `com.viewcompose.widget.core` root with
   `com.viewcompose.ui.foundation`, retaining a concise public DSL surface while using focused
   subpackages for platform/session implementation where ownership requires it.
2. Keep composition orchestration in UI Foundation only behind platform-neutral container and
   engine handles. Move Android `ViewGroup`, tracing, logging, environment, focus, and lifecycle
   adaptation to Android Engine owners; do not introduce a reverse UI Foundation -> Host edge.
3. Give the consumer aggregate exclusive ownership of `com.viewcompose.android`; keep
   `com.viewcompose.host.android` exclusive to the low-level host.
4. Replace `com.viewcompose.widget.constraintlayout` with
   `com.viewcompose.constraintlayout`, and replace the incomplete overlay root with
   `com.viewcompose.overlay.material3.android`.
5. Update service registrations, compiled samples, source documentation, module manuals,
   architecture pages, migration pages, and reviewed Chinese mirrors in the same change.
6. Strengthen verification so a self-declared prefix cannot legalize a legacy root, two published
   artifacts cannot own the same root, and Android namespace exceptions require an explicit
   reviewed reason. The completed hard cut targets zero namespace exceptions.

Exit gate: no production source uses either retired `com.viewcompose.widget.*` root; every
published artifact exclusively owns its declared root; namespace, dependency, public API,
documentation, publication, and full quick/release gates pass.

## Validation matrix

| Boundary | Required proof |
| --- | --- |
| Kernel purity | No Android, AndroidX, Material, tooling, or integration imports/dependencies |
| UI Foundation independence | No Material dependency or Material public type |
| Android Engine independence | No Material dependency; generic host/renderer consumer succeeds |
| Material isolation | All Material imports occur only in Material3 or explicitly Material-backed integrations/demo/tooling |
| Integration direction | No lower-layer dependency on an integration; optional removal preserves base compilation |
| Public exposure | Every project edge matches reviewed `api`/`implementation`; no accidental upstream type leak |
| Consumer ergonomics | `viewcompose-android` is sufficient for the minimal documented application |
| Publishing | New coordinates publish locally; old coordinates are absent from current publication metadata |
| Behavior | Theme, progress, shape, overlay, input, lifecycle, and render transaction coverage passes |
| Documentation | Active English and Chinese pages, module catalog/manuals, API docs, and plan indexes agree |

## Rollback policy

The user-authorized hard cut rejects partial compatibility states. Individual implementation
experiments inside a phase may be reverted when they fail behavior or performance gates, but the
branch must return to the last complete phase boundary and continue toward the target architecture.
Do not retain deprecated coordinates, duplicate production implementations, reverse dependencies,
or permanent layer exceptions to make an intermediate build green.

If a Material-free replacement cannot preserve required progress or shape behavior, revert that
replacement, add the missing focused baseline, and choose a narrower generic engine implementation.
A broad public Binder plugin SPI requires a separate documented decision and is not an automatic
fallback.

## Completion criteria

This plan is complete only when:

1. every runtime artifact is assigned to exactly one of the five layers or to the explicit consumer
   aggregate, and tooling remains orthogonal;
2. all target renames are complete and superseded Gradle projects/Maven coordinates are absent from
   active configuration;
3. Kernel, UI Foundation, and Android Engine meet their dependency/import/public-type gates;
4. Material code exists only in `viewcompose-material3`, Material-backed integrations, or
   non-runtime demo/tooling code;
5. both base-engine and one-dependency Material3 consumer smoke tests pass;
6. publication metadata, dependency contracts, API documentation selection, changesets, and local
   Maven output use the final coordinates;
7. source comments, compiled samples, module manuals, active architecture/guides/tutorials, and
   reviewed Chinese mirrors describe the retained design;
8. all focused, quick, release, documentation, localization, and publishing gates pass;
9. this plan records the final evidence and is ready for release-time archival under the repository
   plan lifecycle.

## Evidence ledger

| Date | Revision | Phase | Command or evidence | Result | Decision and next action |
| --- | --- | --- | --- | --- | --- |
| 2026-08-06 | `4811e9ad` | Planning | CodeGraph dependency/public API survey and production import inventory | Direct Material use is concentrated, but theme/default and host public APIs have broad blast radius | Use the staged hard cut; establish the build baseline before renames |
| 2026-08-06 | `4811e9ad` | Baseline | `./gradlew qaQuick -x verifyDocumentLanguages -x verifyDocumentationStructure` | Passed, 633 tasks in 12 s | Begin the coordinate and responsibility hard cut from a green implementation baseline |
| 2026-08-06 | working tree | Boundaries | `./gradlew verifyDocumentationStructure verifyTutorialSamples verifyDesignSystemIsolation verifyModuleDependencyBoundaries` | Passed, 31 tasks | Retain the five-layer registry and Material/AndroidX isolation gates in `qaQuick` |
| 2026-08-06 | working tree | API documentation | `./gradlew verifyCompleteViewComposeApiDocs` | Passed, reconstructing 69 immutable API versions and 9 unpublished working-tree API trees | Preserve retired history and publish no fabricated immutable route for first-release artifacts |
| 2026-08-06 | working tree | Documentation site | `npm run test:scripts`, `npm run typecheck`, and `npm run build` | Passed; 41 script tests, 80 current Chinese mirrors, 69 versioned manuals, 310 audited site pages, and all site budgets | Budget unpublished `current` Dokka as API trees; keep redirects alone in the routing allowance |
| 2026-08-06 | working tree | Maven publication | `./gradlew verifyViewComposeLocalRepository verifyViewComposePublishedConsumption` | Passed, 696 tasks; all artifacts/POMs and core, material-free engine, feature, and aggregate consumers verified | The final coordinates and reviewed transitive exposure are locally publishable |
| 2026-08-06 | working tree | Release intent | `./gradlew verifyViewComposeReleaseIntent` | Passed: 18 release artifacts, 0 ignored artifacts, and 3 shared-path classifications against `4811e9ad` | Run `planViewComposeRelease` only after the reviewed implementation is committed because planning intentionally requires a clean tree |
| 2026-08-06 | working tree | Final quick gate | `./gradlew qaQuick` | Passed, 1358 tasks in 1 min 40 s | All compile, unit, boundary, documentation, release-intent, publication, and sample gates agree |
| 2026-08-06 | working tree | Final optimized artifacts | `./gradlew qaRelease` | Passed, 693 tasks in 2 min 34 s | Release and benchmark R8, resource optimization, and lint-vital outputs are valid; implementation is complete |
| 2026-08-06 | working tree | Phase 8 baseline | Focused UI Foundation, Host, Overlay, ConstraintLayout, aggregate, and Navigation unit tests plus package/namespace gates | Passed, 248 tasks before the ownership correction | Retain behavior while changing package ownership and Android execution boundaries |
| 2026-08-06 | working tree | Phase 8 ownership gates | `./gradlew verifyModulePackageRoots verifyAndroidModuleNamespaces verifyUiFoundationPlatformBoundary verifyModuleDependencyBoundaries` | Passed with unique and longest-prefix ownership, zero namespace overrides, no legacy production packages, and no Android execution imports in UI Foundation | Keep all four checks in `qaQuick` |
| 2026-08-06 | working tree | Phase 8 focused regressions | UI Foundation, Host Android, and aggregate unit tests | Passed after moving native-container validation to Host and fixing the aggregate test's explicit Host interop import | Responsibility-specific tests now guard both sides of the opaque handle boundary |
| 2026-08-06 | working tree | Phase 8 documentation | `npm run verify:translations`, `npm run verify:languages`, `npm run test:scripts`, `npm run typecheck`, and `npm run build` | Passed: 81 current and 0 stale Chinese mirrors, 312 audited pages, and all site budgets | ADR-0003 and all affected module/migration pages are current in both locales |
| 2026-08-06 | working tree | Phase 8 API and release intent | `./gradlew verifyCompleteViewComposeApiDocs verifyViewComposeReleaseIntent` | Passed after classifying 20 changed artifacts, one sample-only ignore, and three shared publication inputs | The new package and platform-boundary APIs are documented and release-plannable |
| 2026-08-06 | working tree | Phase 8 final gates | `./gradlew qaQuick` and `./gradlew qaRelease` | Passed | Package ownership closeout is complete and ready for release-time archival |

## Decision history

| Date | Decision | Rationale |
| --- | --- | --- |
| 2026-08-06 | Adopt five runtime layers with tooling orthogonal | Responsibility and independent evolution are more durable than vendor or convenience grouping |
| 2026-08-06 | Execute one hard cut without old-coordinate facades | The artifacts are alpha and the user explicitly chose a complete boundary correction over compatibility scaffolding |
| 2026-08-06 | Rename only misleading capability artifacts; retain `-core` for actual platform-neutral kernels | `core` remains precise inside one capability, while `widget-core`, generic renderer, and hidden Material/AndroidX integrations are misleading |
| 2026-08-06 | Keep generic node types and a closed renderer registry | Design-system defaults belong above resolved nodes; two Material controls do not justify a broad runtime plugin surface |
| 2026-08-06 | Add `viewcompose-android` as the ease-of-use boundary | Consumers get one dependency without coupling Host Android or Renderer Android upward to Material3 |
| 2026-08-06 | Keep theme injection at the existing token-provider boundary | A new generic Android resolver SPI would add lifecycle and compatibility surface without a second design-system implementation; the aggregate can assemble Material3 explicitly |
| 2026-08-06 | Defer documentation convergence until code/publication topology stabilizes | Intermediate module moves would create repeated document churn; completion still requires every documentation gate to pass |
| 2026-08-06 | Keep superseded coordinates only as retired immutable documentation history | A hard cut removes them from active publication without deleting evidence needed by readers of previously released artifacts |
| 2026-08-06 | Treat unpublished working-tree Dokka as a full API tree in site budgets | First-release modules need truthful current API documentation, but their generated output must obey the same average and per-tree limits as immutable releases |
| 2026-08-06 | Give every published artifact one exclusive canonical package root | Artifact names and packages now describe the same responsibility; duplicate ownership and namespace exceptions would recreate hidden coupling |
| 2026-08-06 | Keep session orchestration in UI Foundation behind opaque handles | Moving lazy and overlay child-session orchestration wholesale into Host would create an upward dependency; Host injection preserves direction while owning all Android execution details |
| 2026-08-06 | Do not retain legacy package forwarding facades | The artifacts have not entered their first release, so compatibility aliases would add permanent ambiguity without protecting a released consumer |
