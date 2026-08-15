# Android Views Performance Control Plan

## Status

Active. The first slice covers the high-value non-shadow list and complex-layout workloads. Three
of four actions now have accepted same-context three-engine evidence on the consumer reference
device; list scroll remains blocked by the device stability gate. Shadow parity remains deferred
until it has an equivalent platform rendering contract.

Last verified: 2026-08-15.

Next action: collect the complete twelve-method physical batch on a clock-controllable device so
list scroll can join the three accepted steady-state actions without mixing device contexts.

## Maven release changesets

- `release/changes/20260815-android-views-performance-control.json`

Only the Demo, internal benchmark target, report tooling, and documentation change. No published
artifact or public/protected API is in scope; the Changeset records the root build task wiring as a
concrete release-neutral shared input.

## Objective and baseline

Add an idiomatic native control to separate ViewCompose overhead from declarative and direct
platform cost. The accepted baseline has ViewCompose/Compose data for all workload families. This
slice adds Android Views to `performance.list@3` and `performance.complex-layout@3`. List mutation,
complex-layout scroll, and complex-layout update now have accepted steady-state native evidence;
list scroll remains `inconclusive`. Compose stays the longitudinal environmental control.

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
- Complete — collect same-context three-engine evidence for list mutation, complex-layout scroll,
  and complex-layout update on Samsung SM-G991B / Android 13. All nine methods passed the `0.15`
  run-P50 CV gate, and `docs/tooling/performance.md` records their absolute results, normalized
  comparisons, classifications, limitations, and next action.
- Blocked — the list-scroll ViewCompose control produced run-P50 CV `0.182`, including a
  `2.627 ms` plateau against four `4.296`-`4.648 ms` runs. The result is rejected and the Compose
  and Android Views methods were deliberately skipped under the fail-fast protocol. Complete the
  twelve-method baseline on a clock-controllable device; do not rerun this consumer device until it
  happens to pass.

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

Complete device acceptance runs all twelve non-shadow methods under matching system, compilation,
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
