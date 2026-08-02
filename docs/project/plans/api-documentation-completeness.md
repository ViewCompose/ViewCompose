# API Documentation Completeness

## Status

Complete.

## Scope

Bring the generated API reference for every published ViewCompose artifact to the quality contract
defined by the
[Source Documentation and API Comment Standard](../api-documentation-quality.md). The work covers
KDoc/Javadoc content, compiled samples, Dokka warnings, immutable source links, package/module
overviews, and staged enforcement.

## Non-goals

- changing public behavior while documenting it;
- adding comments that merely restate names or signatures;
- translating the generated symbol tree;
- enabling a repository-wide warning gate before existing debt is repaired;
- treating internal, demo, or test declarations as published API.

## Current baseline

- Dokka 2.2 generates HTML for all 25 independently published artifacts.
- normal documentation builds retain deprecated APIs and suppress generated files and obvious
  synthetic members;
- public and protected declarations are included in the documentable surface;
- `auditViewComposeApiDocs` exposes missing KDoc/Javadoc without blocking the build;
- strict `reportUndocumented + failOnWarning` checking is mandatory for all 25 modules;
- `viewcompose-runtime` has a reviewed Q2/Q3 baseline, compiled Q3 samples, a module manual, and an
  always-on strict Dokka warning gate;
- `viewcompose-ui-contract` has a reviewed Q2/Q3 baseline covering node specifications, modifier
  phases, environment, interaction connectors, lazy/pager state, compiled samples, and an always-on
  strict Dokka warning gate;
- `viewcompose-widget-core` has a reviewed Q2/Q3 baseline covering the component DSL, theme and
  locals, composition effects, saveable state, overlays, render-session recovery, compiled samples,
  and an always-on strict Dokka warning gate;
- `viewcompose-renderer` has a reviewed Q2/Q3 baseline covering reconciliation, native View
  ownership, lazy-list identity, decoration SPI, render diagnostics, layout sampling, compiled
  samples, and an always-on strict Dokka warning gate;
- `viewcompose-host-android` has a reviewed Q2/Q3 baseline covering Activity, Fragment, custom
  container, native View transaction, saved-state, frame scheduling, animation, and graphics
  interop contracts, compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-text-core` has a reviewed Q2/Q3 baseline covering UTF-16 ranges, rich documents,
  edit transactions, IME composition history, input transformations, Receive Content, save codecs,
  compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-navigation-core` has a reviewed Q2/Q3 baseline covering graphs, deep links,
  rollback-safe transactions, retained stacks, lifecycle planning, pane scenes, compiled samples,
  and an always-on strict Dokka warning gate;
- `viewcompose-navigation` has a reviewed Q2/Q3 baseline covering destination and graph owners,
  native host transactions, process-death restore, system and predictive Back, Android-aligned
  motion, adaptive panes, compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-overlay-android` has a reviewed Q2/Q3 baseline covering optional provider discovery,
  session-isolated platform surfaces, anchored popup recovery, Material surface policy, transient
  feedback lifetimes, compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-lifecycle` has a reviewed Q2/Q3 baseline covering owner propagation, commit-aware Flow
  collection, structured coroutine ownership, repeat-on-lifecycle restart, compiled samples, and an
  always-on strict Dokka warning gate;
- `viewcompose-viewmodel` has a reviewed Q2/Q3 baseline covering owner propagation, keyed store
  identity, factory and CreationExtras precedence, SavedStateHandle persistence, navigation scope,
  compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-animation-core` has a reviewed Q2/Q3 baseline covering normalized specifications,
  easing, vector conversion, deterministic sampling, frame-loop cancellation, low-level mutation,
  shared transition timing, compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-animation` has a reviewed Q2/Q3 baseline covering composition frame ownership,
  state retargeting, last-mutation cancellation, shared transitions, infinite channels, content
  retention, measured-size renderer cost, compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-gesture-core` has a reviewed Q2/Q3 baseline covering axis locking, transform
  activation, swipe arbitration, anchor validation and replacement, anchored settle thresholds,
  compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-gesture` has a reviewed Q2/Q3 baseline covering renderer-owned recognition, raw
  pointer and click modifiers, latest-callback state, anchored reconciliation, transform lifecycle,
  priority, nested scroll, compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-graphics-core` has a reviewed Q2/Q3 baseline covering coordinate and color
  conventions, geometry mutability, paths, paint and filter capability, command ordering, validated
  scenes, recorder lifetime, caching, compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-graphics` has a reviewed Q2/Q3 baseline covering draw-pass execution, Canvas sizing,
  content ordering, semantic cache keys, Android replay fidelity, compiled samples, and an always-on
  strict Dokka warning gate;
- `viewcompose-shadow-android` has a reviewed Q2/Q3 baseline covering optional backend discovery,
  wrapper-free drawing planes, pixel resolution, raster ownership and budgets, replay fallbacks,
  diagnostics, compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-widget-constraintlayout` has a reviewed Q2/Q3 baseline covering references, anchors,
  dimensions, inline and reusable sets, virtual helpers, native merging and failure recovery,
  compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-image-coil` has a reviewed Q2/Q3 baseline covering Android target acceptance,
  asynchronous replacement, Coil-owned cancellation and caching, loader ownership, compiled
  samples, and an always-on strict Dokka warning gate;
- `viewcompose-preview-core` has a reviewed Q2/Q3 baseline covering annotation signatures,
  deterministic configuration matrices, stable identity, build/worker boundaries, exact protocol
  negotiation, JSON compatibility, source-aware diagnostics, compiled samples, and an always-on
  strict Dokka warning gate;
- `viewcompose-preview-gradle-plugin` has a reviewed Q2/Q3 baseline covering Android variant
  registration, compiled discovery, canonical fingerprints, single and gallery rendering, fast
  refresh, worker isolation/reuse verification, production annotation stripping, compiled samples,
  and an always-on strict Dokka warning gate;
- `viewcompose-preview-runner` has a reviewed Q2/Q3 baseline covering compiled entry resolution,
  Android configuration and application theme fidelity, frame-scoped owners, bounded auto-height,
  atomic image and snapshot export, immutable diagnostics, compiled samples, and an always-on strict
  Dokka warning gate;
- `viewcompose-preview-worker-host` has a reviewed Q2/Q3 baseline covering one-shot and loopback
  server modes, validation, Layoutlib ownership, reloadable class-loader isolation, atomic responses,
  bounded retirement, compiled samples, and an always-on strict Dokka warning gate;
- `viewcompose-preview` has a reviewed Q2/Q3 baseline covering coherent application theme
  resolution, Compose bridge session ownership, root interop, catalog and Paparazzi coverage,
  compiled samples, and an always-on strict Dokka warning gate;
- every published module has an available English and current Chinese module manual;
- generated source links are pinned per module to a full immutable source commit;
- version, `current`, prerelease `latest` exclusion, manifest, manual catalog, and complete-catalog
  parity are enforced in build and deployment tasks.

The warning count is an inventory, not a quality score. A declaration with a comment can still fail
Q2 or Q3 manual review.

## Completion criteria

1. every published module has a reviewed Q-level baseline;
2. all normal public and protected APIs meet Q2, with documented low-risk Q1 exceptions;
3. high-risk APIs meet Q3 and link to compiled samples;
4. KDoc/Javadoc symbol links resolve without warnings;
5. generated source links target the matching release tag or immutable revision;
6. strict Dokka checking blocks regression for every repaired module;
7. module and package overviews lead readers from concepts to symbols;
8. the complete versioned API catalog and documentation site build successfully.

## Ordered work

1. **Quality foundation — complete**
   - define Q0–Q3 and the contract matrix;
   - include public and protected APIs;
   - add non-blocking audit and opt-in strict mode;
   - record the initial runtime baseline.
2. **Runtime foundation — complete**
   - classify all runtime warnings and existing comments;
   - repair `viewcompose-runtime` to Q2/Q3;
   - add focused compiled samples;
   - enable strict checking for the module.
3. **Core UI chain — complete**
   - `viewcompose-ui-contract` baseline and strict gate are complete;
   - `viewcompose-widget-core` baseline and strict gate are complete;
   - `viewcompose-renderer` baseline and strict gate are complete;
   - `viewcompose-host-android` baseline and strict gate are complete;
   - add module/package overviews and cross-links.
4. **Remaining families — complete**
   - text, both navigation layers, the Android overlay backend, lifecycle, and ViewModel are complete;
   - animation, gesture, graphics, shadows, constraint layout, image loading, and preview tooling are
     complete;
   - enable strict checking after each module baseline is clean.
5. **Immutable source and release integration — complete**
   - derive source-link revisions from the published version/tag contract;
   - verify `current`, stable `latest`, and immutable version routes;
   - activate the complete-catalog regression gate.

## Validation

- `./gradlew auditViewComposeApiDocs -PviewComposeDocsModules=<artifact>`
- strict selected-module Dokka generation
- compiled sample tasks for the owning module
- `./gradlew verifyDocumentationStructure`
- `./gradlew verifyCompleteViewComposeApiDocs`
- Docusaurus type check and production build

## Last verified

2026-08-02: all 25 published modules passed repository-wide strict Dokka generation with zero
warnings. Every module has compiled samples where required, an available English manual, a current
Chinese mirror, version/current route verification, prerelease latest-alias exclusion, and source
links pinned to the immutable `fbe1614dd2a278f06517d775c373cb88ce5674a2` source freeze.

## Next action

Maintain the gate with every public API or module release. Freeze changed module source first, then
update its independent version and immutable source revision together before publishing.
