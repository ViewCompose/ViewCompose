# Layered Task-List Tutorials Plan

## Status

Active. Phase 1 completed on 2026-08-03; Phase 2 is next.

## Goal

Build a progressive tutorial series around one standalone `:samples:task-list` application. Each
chapter must be a complete learning experience, use public ViewCompose APIs, copy non-trivial code
from compiled source, and ship with a current Simplified Chinese mirror.

The minimal `:samples:counter` application remains the shortest first-success path and is not
expanded into the realistic application tutorial.

## Delivery phases

1. Foundations and data entry
   - add the standalone application and device behavior test;
   - teach snapshot state, `Column`/`Row`, modifiers, and event handling;
   - evolve the same task model into `TextField` plus keyed `LazyColumn`;
   - enforce exact source-to-Markdown snippets and compile from `qaQuick`;
   - run the application behavior test from `qaFull`.
2. Theme and navigation
   - use semantic theme tokens from the Android host bridge;
   - add list and detail destinations with framework-owned navigation.
3. Overlays and Android View interoperability
   - confirm task deletion through an overlay;
   - mount and update one native Android View inside the declarative tree.
4. Animation and gestures
   - animate task insertion or completion from observable state;
   - add one bounded gesture interaction with deterministic fallback controls.
5. Performance and diagnostics
   - explain stable keys, content types, reuse, and measurement boundaries;
   - collect renderer diagnostics and connect the sample to repeatable inspection commands.

## Progress log

### 2026-08-03 — Phase 1 complete

- added `:samples:task-list` with separately compiled foundations and input/list stages;
- made the input/list stage the runnable Activity content;
- added a real-device test for task insertion and completion;
- published two canonical tutorials and current required Chinese mirrors;
- added exact source-region verification through `verifyTutorialSamples`;
- connected application compilation to `qaQuick` and device behavior to `qaFull`;
- passed `qaQuick`, production site build, translation verification, and the new device test on a
  Samsung SM-G991B running Android 13.

## Verification contract

- `./gradlew verifyTutorialSamples`
- `./gradlew verifyDocumentationStructure`
- `./gradlew :samples:task-list:assembleDebug`
- `./gradlew :samples:task-list:compileDebugAndroidTestKotlin`
- `./gradlew qaQuick`
- `./gradlew :samples:task-list:connectedDebugAndroidTest` on a device or emulator
- `cd website && npm run verify:translations && npm run build`

## Completion condition

The plan is complete when all five delivery phases are merged, every chapter is indexed from
`docs/README.md`, all documented non-trivial snippets are verified against compiled source, both
locales build, and the final application behavior is covered by `qaFull`. At completion this file
moves to `docs/archive/` and the active-plan index returns to its empty state.
