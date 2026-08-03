# Layered Task-List Tutorials

## Status

Completed on 2026-08-03. This file is historical evidence; current learning paths start from
[`docs/README.md`](../README.md).

## Goal delivered

One standalone `:samples:task-list` application now backs a progressive six-chapter tutorial
series. Every chapter uses public ViewCompose APIs, copies its non-trivial code from compiled
source, and has a current required Simplified Chinese mirror.

The minimal `:samples:counter` application remains the shortest first-success path.

## Delivered phases

1. Foundations and data entry
   - snapshot state, immutable task records, `Column`/`Row`, modifiers, and events;
   - `TextField`, immutable collection updates, keyed `LazyColumn`, and completion state.
2. Theme and navigation
   - Android-host semantic theme tokens;
   - remembered list/detail navigation with a typed task-ID argument and Back behavior.
3. Overlays and Android View interoperability
   - explicit Android overlay hosts and confirmed deletion through `Dialog`;
   - a native `TextView` updated from the same observable collection.
4. Animation and gestures
   - completion content controlled by `AnimatedVisibility`;
   - click and long-click gestures with visible deterministic fallback buttons.
5. Performance and diagnostics
   - stable key/content-type contracts and explicit prefetch/reuse hints;
   - loop-free host diagnostics sampled from immutable `RenderStats` snapshots.

## Verification delivered

- `verifyTutorialSamples` compiles the sample and checks exact source regions in both locales;
- `qaQuick` assembles the application and compiles its instrumentation test;
- `qaFull` runs `:samples:task-list:connectedDebugAndroidTest`;
- `TaskListAppTest` verifies task insertion/completion, navigation, native-view updates, confirmed
  overlay deletion, and diagnostic sampling through Android Views;
- all six task-list tutorials are required by the translation policy;
- the final device test passed on 2026-08-03 on a Samsung SM-G991B running Android 13.

## Completion evidence

The series is indexed from `docs/README.md`, every non-trivial documented snippet is verified
against compiled source, both locales pass translation verification, and the final behavior test is
connected to `qaFull`. The active-plan index was therefore returned to its empty state.
