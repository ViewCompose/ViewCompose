# Maven Dependency Contract Convergence

## Status

- State: Active
- Started: 2026-08-05
- Scope: published ViewCompose artifacts, generated Maven metadata, external-consumer verification,
  installation documentation, and dependency-governance gates

## Maven release changesets

- `release/changes/20260805-maven-dependency-contract-convergence.json`

## Decision summary

ViewCompose will follow the dependency-management shape used by mature AndroidX libraries:

1. an advertised entry artifact must be sufficient to compile its advertised API;
2. dependencies whose types form that public surface are exported with `api`;
3. implementation services remain private with `implementation`;
4. users declare capability artifacts, not the framework's internal layering;
5. published-repository consumers and metadata checks protect the contract;
6. every published module records an explicit, reviewable dependency-exposure contract.

For an ordinary Android application, `viewcompose-host-android` is the base entry artifact. It
transitively supplies runtime state, UI contracts, and widget APIs. Direct foundation coordinates
remain supported for custom hosts, custom renderers, platform-neutral use, and libraries that expose
foundation types from their own API.

## Motivation and baseline

The previous getting-started path required applications to declare runtime, UI contract, widget
core, and Android host separately. That made the internal module graph part of installation and
allowed documentation to compensate for incorrect publication exposure.

The initial publication smoke test also proved only that feature-core marker types were visible. It
did not compile a real `setUiContent` tree from a consumer that declared only the host artifact.
Consequently, an `implementation` dependency could leak into a public signature without failing the
release gate.

AndroidX is the directional reference, not a requirement to copy every artifact or release
mechanism. ViewCompose keeps its independent module versions and Android View engine.

## Goals

- Reduce the base ViewCompose installation to one explicit ViewCompose artifact.
- Make each advertised feature artifact sufficient for its principal public APIs.
- Keep lifecycle, ViewModel, renderer, and optional backends private when their types are not part of
  the advertised surface.
- Validate both Gradle Module Metadata consumption and Maven POM scopes.
- Require future published modules to classify every internal dependency edge explicitly.
- Document direct-versus-transitive dependency ownership consistently.

## Non-goals

- Merge runtime, UI contract, widget core, and host into one AAR.
- Hide transitive packages or prevent advanced consumers from importing them.
- Convert every dependency to `api`.
- Add compiler-specific optimization or Compose compiler behavior.
- Force consumer versions with `force` or an enforced platform.
- Introduce a second umbrella artifact while `viewcompose-host-android` remains the standard entry.

## Target contract

The base dependency path is:

```text
application
  -> viewcompose-host-android
       -> api: viewcompose-runtime
       -> api: viewcompose-ui-contract
       -> api: viewcompose-widget-core
       -> implementation: viewcompose-lifecycle
       -> implementation: viewcompose-viewmodel
       -> implementation: viewcompose-renderer
```

Widget and feature artifacts independently export the modules required to compile their advertised
surface. For example, widget core exports runtime and UI contracts; animation exports animation
core, runtime, UI contracts, and widget core. Duplicate direct and transitive references to the same
Maven coordinate remain safe because Gradle resolves one selected version.

## Execution phases

### Phase 0: Baseline and diagnostics

- [x] Record the previous four-coordinate getting-started baseline.
- [x] Add an isolated Android consumer that declares only `viewcompose-host-android` plus the Android
  libraries directly used by its source and theme.
- [x] Compile representative state, Modifier, widget, and `setUiContent` usage from that consumer.
- [x] Extend local-repository inspection to validate ViewCompose dependency scopes in generated
  POMs.

Exit condition: the test fails when the host stops exporting any required base module and passes
against locally published artifacts.

### Phase 1: Public dependency convergence

- [x] Export runtime, UI contract, and widget core from the Android host.
- [x] Export runtime and UI contract from widget core.
- [x] Correct feature and foundation edges whose types occur in advertised public signatures,
  including animation, gesture, lifecycle, renderer, graphics contracts, shadows, overlays, image
  adapters, and ConstraintLayout.
- [x] Keep lifecycle, ViewModel, renderer installation, optional backends, and tooling internals
  private where they do not form the advertised surface.
- [x] Record the publication-metadata release impact in an immutable Changeset.

Rollback condition: revert an individual exposure change when published-consumer tests show no
public compile requirement and the wider compile classpath causes a measured regression. Do not
revert the host/widget base chain while their public signatures still require it.

### Phase 2: Governance and documentation

- [x] Add one machine-readable dependency-exposure contract covering every registered artifact.
- [x] Reject missing modules, unclassified edges, duplicate configurations, and mismatches between
  the contract and Gradle build declarations.
- [x] Run that gate before local or Central publication.
- [x] Define the normative `api`/`implementation` policy in publishing and contributor workflow
  documentation, with current Chinese mirrors.
- [x] Update module manuals to distinguish public and private dependency exposure.

Exit condition: a new published module cannot pass normal QA until every ViewCompose dependency
edge has a reviewed exposure classification.

### Phase 3: Public Maven rollout

- [ ] Release every artifact selected by release-intent and reverse-dependency planning.
- [ ] Confirm Maven Central serves the new Gradle Module Metadata and POM scopes.
- [ ] After that publication is available, switch README, getting-started tutorials, and Maven-backed
  samples from the previously published four-coordinate baseline to the host-only ViewCompose
  coordinate.
- [ ] Run the tutorials from a clean repository that has no generated local Maven repository.

The installation example must not move early: current tutorials intentionally remain valid for the
already-published version. Advertising host-only installation against a version whose metadata still
marks foundation dependencies private would make clean consumer builds fail.

### Phase 4: Compatibility platform evaluation

- [ ] Establish at least two independently versioned release sets and record which combinations are
  verified together.
- [ ] Prototype a generated `viewcompose-bom` backed by the release planner's compatibility set.
- [ ] Add a consumer that imports the regular Gradle platform and omits versions from feature
  coordinates.
- [ ] Verify selective publication, documentation history, Maven Central requirements, and reverse
  dependency release propagation for a POM-only platform artifact.
- [ ] Publish the BOM only if it removes real version-selection failures without coupling otherwise
  independent releases.

Rollback condition: discard the prototype if it requires manual duplicate version tables, forces
consumer versions, or makes selective releases ambiguous. Until those conditions are satisfied,
exact transitive dependency versions plus documented verified sets remain authoritative.

## Verification matrix

| Contract | Verification |
| --- | --- |
| Host-only base installation | isolated Maven-backed Android consumer compiles a real counter tree |
| Feature-to-core exposure | isolated publication smoke sources compile public feature/core types |
| Declared dependency intent | dependency-contract gate compares every registered module and edge |
| Maven compatibility | generated POM dependency scopes match `api` and private classifications |
| Gradle compatibility | consumers resolve the generated Gradle Module Metadata variants |
| Documentation | structure, tutorial dependency, language, and translation-fingerprint gates pass |
| Release intent | immutable Changeset classifies every changed publication input |

## Explicitly rejected or deferred work

- **Fat AAR or shaded foundation classes:** rejected because it duplicates classes, obscures
  ownership, and weakens independent updates.
- **Make every edge `api`:** rejected because it expands compile classpaths and turns implementation
  details into compatibility promises.
- **New umbrella artifact now:** rejected because host Android already owns the normal application
  entry and another coordinate would add no capability.
- **Strict or forced dependency versions:** rejected because applications must retain normal Gradle
  conflict resolution; known incompatibilities should fail through documented constraints only.
- **BOM before compatibility evidence:** deferred to Phase 3 because a BOM maps tested independent
  versions; it must not merely repeat today's coincidentally equal versions.
- **Package moves to conceal foundation modules:** rejected because package ownership remains useful
  for advanced consumers and moving it would create avoidable API churn.

## Completion condition

Phases 0 through 2 are complete when the local publication, host-only consumption, documentation,
and release-intent gates pass. Phase 3 completes the public rollout after Maven Central availability.
The plan remains active for the conditional Phase 4 evaluation and is archived after the BOM
decision is either implemented with evidence or explicitly rejected with recorded compatibility and
release results.
