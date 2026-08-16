# Android Views Performance Control Plan

## Status

Complete. The first high-value non-shadow slice has accepted same-context three-engine evidence for
all five list and complex-layout actions on a root-controlled reference device. Report tooling,
documentation/site gates, `qaQuick`, and 119 physical-device Demo tests pass. Shadow parity remains
deferred until it has an equivalent platform rendering contract and does not block this slice.

This archived execution record is canonical English-only under the documentation governance
policy. Durable results, limitations, and follow-up targets live in
[`docs/tooling/performance.md`](../tooling/performance.md).

Last verified: 2026-08-16.

Next action: optimize list-mutation and structural-update P95 through separately activated work;
the native-control coverage itself is complete.

## Maven release changesets

- `release/changes/20260815-android-views-performance-control.json`

Only the Demo, internal benchmark target, report tooling, and documentation change. No published
artifact or public/protected API is in scope; the Changeset records the root build task wiring as a
concrete release-neutral shared input.

## Objective and baseline

Add an idiomatic native control to separate ViewCompose overhead from declarative and direct
platform cost. The accepted baseline has ViewCompose/Compose data for all workload families. This
slice adds Android Views to `performance.list@4` and `performance.complex-layout@4`. List scroll,
list mutation, complex-layout scroll, property update, and structural update now have accepted
steady-state native evidence. Compose stays the longitudinal environmental control.

## Scope

Included:

1. `RecyclerView` list and retained `ScrollView`/ViewGroup complex-layout controls;
2. identical data, automation, interactions, metrics, settling, and assertions across engines;
3. engine-keyed report v2, report-v1 compatibility, and deterministic tests.

Deferred:

Shadow controls, startup/first-inflate measurement, changing the longitudinal control, and
framework publication remain out of scope.

## Measurement contract

Native reuse means stable-ID `RecyclerView`, synchronous `DiffUtil`, payload binding, and an
in-place-patched complex tree whose conditional row is truly added or removed. Engines share only
immutable workload/copy/visual/automation inputs.

Non-shadow scenarios require all three engines; shadow scenarios require the existing pair. Split
results require one device/OS/build/clock context and unique methods. State changes only after the
platform container accepts the logical update.

Report v2 stores absolute engine values and explicit pairs, reads report-v1 baselines without
inventing native data, and keeps the raw plus Compose-normalized gate unchanged.

Adding an engine does not change workload identity. A later parity change to data, nodes,
interaction, settling, or actions must bump the owning revision everywhere.

## Implementation phases

### Phase 0: contract

- Complete — fix scope, non-goals, native reuse semantics, and historical normalization behavior.

### Phase 1: native fixtures

- Complete — add the strict `android_views` wire value, both native controls, matching automation
  targets, and engine/device contract tests.

### Phase 2: benchmark and report

- Complete — add four native benchmark methods and replace pair-shaped report metadata with
  engine-keyed measurements while preserving report-v1 loading and the existing gate.
- Complete — cover partial classes, split results, stability, old baselines, and CLI output in
  report tests.

### Phase 3: acceptance

- Complete — run focused app, benchmark compilation, report, and documentation tests.
- Complete — run `qaQuick`, release assembly, site build, device contract test, and diff review.
- Complete — collect the fifteen-method root-controlled ViewCompose, Compose, and Android Views
  matrix for list scroll/mutation and complex-layout scroll/property/structure updates on Xiaomi
  MI 6 / Android 9. Every accepted run-P50 CV is at or below `0.111`.
- Complete — correct the list workload before acceptance. ViewCompose alone retained RecyclerView
  item animation, producing about 217 mutation frames versus 41/48 control frames. The fixture now
  disables that unmatched animation, reports 48 frames, and bumps the workload from revision 3 to
  `performance.list@4`.
- Complete — interpret absolute results, normalized ViewCompose/Compose and ViewCompose/Android
  Views comparisons, memory maxima, limitations, conclusions, and next actions in
  `docs/tooling/performance.md`.

### Phase 4: deferred shadow decision

- Deferred — choose an equivalent native shadow contract before adding methods. The control must
  neither reuse ViewCompose shadow code nor present unlike effects as equivalent.

## Validation

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :viewcompose-benchmark:compileBenchmarkKotlin
./gradlew testBenchmarkComparisonTool
./gradlew verifyDocumentationStructure
./gradlew qaQuick
```

Complete device acceptance runs all fifteen non-shadow methods under matching system, compilation,
refresh-rate, thermal, and clock-policy conditions. Action-level collection is fail-fast: when the
first control fails an environment or stability gate, the remaining engines for that action are
skipped and the rejection is recorded. Raw output alone does not complete the plan; accepted
evidence must be interpreted in the active performance specification.

## Completion criteria

The first slice closes when all three engines expose the same non-shadow automation contract,
report v2 preserves the old gate, repository gates pass, and accepted same-context results are
documented. The separate shadow phase does not block the non-shadow control.

## Evidence ledger

| Date | Evidence | Result |
| --- | --- | --- |
| 2026-08-15 | Samsung SM-G991B / Android 13 fail-fast physical batch | List mutation, complex-layout scroll, and complex-layout update completed all three engines with run-P50 CV ranges `0.013`-`0.091`, `0.008`-`0.082`, and `0.032`-`0.141`. The batch records `unlocked-dvfs-preflight-v1` and `run-from-apk`; the Runtime Image warning limits it to post-ready steady-state interaction evidence. |
| 2026-08-15 | Rejected list-scroll control | ViewCompose frame CPU P50/P95 was `4.212/8.624 ms`, but run P50 values `4.296/4.362/2.627/4.648/4.554 ms` produced CV `0.182`. Classification is `inconclusive`; Compose and Android Views were not run for this action. |
| 2026-08-16 | Xiaomi MI 6 / Android 9 root-controlled fifteen-method batch | All methods passed with run-P50 CV `0.013`-`0.111`, fixed performance governors at 1.4016/1.8048 GHz, eight CPUs online, charging suspended, and `run-from-apk`. List scroll and complex scroll regress against native; property update improves against Compose but retains a native P95 gap; list mutation and structural update are mixed because their medians are competitive while their tails regress. |
| 2026-08-16 | Corrected `performance.list@4` mutation workload | Removing the ViewCompose-only RecyclerView item animation reduces the measured mutation sequence from about 217 frames to 48, matching the native control's action duration. The corrected P95 is `40.332 ms`; the old lower P95 was diluted by animation frames and is not comparable. |
