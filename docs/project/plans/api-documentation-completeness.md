# API Documentation Completeness

## Status

Active.

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
- strict `reportUndocumented + failOnWarning` checking is available per selected module;
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
- comment language, depth, tags, and lifecycle/failure detail remain inconsistent across modules;
- generated source links are not yet pinned to release tags.

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
4. **Remaining families**
   - text, both navigation layers, the Android overlay backend, lifecycle, and ViewModel are complete;
   - animation, gesture, graphics, shadows,
     constraint layout, image loading, and preview tooling;
   - enable strict checking after each module baseline is clean.
5. **Immutable source and release integration**
   - derive source-link revisions from the published version/tag contract;
   - verify `current`, stable `latest`, and immutable version routes;
   - activate the complete-catalog regression gate.

## Validation

- `./gradlew auditViewComposeApiDocs -PviewComposeDocsModules=<artifact>`
- strict selected-module Dokka generation
- compiled sample tasks for the owning module
- `./gradlew verifyDocumentationStructure`
- `./gradlew assembleViewComposeApiDocs`
- Docusaurus type check and production build

## Last verified

2026-08-02: `viewcompose-gesture` was repaired from 21 initially undocumented declarations to a
zero-warning strict baseline. Renderer recognition ownership, callback thread and cancellation,
initial-value lifetime, synchronous anchor reconciliation, immediate settling, transform delivery,
priority, and nested-scroll attachment were reviewed; compiled samples and a bilingual module manual
were added.

## Next action

Audit and repair `viewcompose-graphics-core`, add its module manual and focused samples, then enable its
module-level strict warning gate without changing API behavior.
