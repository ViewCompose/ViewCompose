# API Documentation Completeness

## Status

Active.

## Scope

Bring the generated API reference for every published ViewCompose artifact to the quality contract
defined by [API Documentation Quality Standard](../api-documentation-quality.md). The work covers
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
- the first `viewcompose-runtime` audit reports 35 undocumented declarations and strict mode fails
  with those warnings as intended;
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
2. **Runtime foundation — next**
   - classify all runtime warnings and existing comments;
   - repair `viewcompose-runtime` to Q2/Q3;
   - add focused compiled samples;
   - enable strict checking for the module.
3. **Core UI chain**
   - repair `viewcompose-ui-contract`, `viewcompose-widget-core`, `viewcompose-renderer`, and
     `viewcompose-host-android` in dependency order;
   - add module/package overviews and cross-links.
4. **Remaining families**
   - text, navigation, lifecycle, ViewModel, overlay, animation, gesture, graphics, shadows,
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

2026-08-02: non-blocking and strict audits were exercised against `viewcompose-runtime`; the
non-blocking task succeeded and strict mode rejected 35 undocumented declarations.

## Next action

Classify and repair the 35 `viewcompose-runtime` warnings, review its already documented symbols
against Q2/Q3, add focused samples, and enable module-level strict enforcement without changing API
behavior.
