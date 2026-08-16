# ViewCompose Performance

## 1. Scope

This is the current performance specification. It defines the established baseline, gate metrics,
design and implementation constraints, and the next optimization phases.

For historical analysis, see
[PERFORMANCE_FULL_2026-03-06.md](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/PERFORMANCE_FULL_2026-03-06.md).

## 2. Current baseline (2026-08)

### 2.1 Established capabilities

1. `viewcompose-benchmark` provides stable benchmark entry points.
2. The renderer decides whether each node is rebound and reports `rebound/skipped`.
3. Diagnostics expose baseline render/layout metrics.
4. Runtime uses SlotTable Lite: `RenderSession` performs initial composition and node-group
   incremental recomposition, retaining `VNode` references for clean groups.
5. `InvalidationQueue` merges and deduplicates ancestor invalidations and marks changed
   `emit(spec/modifier)` inputs dirty.
6. The patch pipeline supports `SkipSubtree`, reports `skippedSubtrees`, and uses the
   `previousVNode === nextVNode` identity fast path.
7. Delayed-session keyed diffing uses DiffUtil with missing/duplicate-key fallback.
8. Framework RecyclerView containers do not share `RecycledViewPool` by default and retain the
   platform `itemAnimator`. A container can opt into pool sharing and animator policy through
   `reusePolicy/motionPolicy`.
9. Renderer dimension conversion uses `viewcompose-renderer-android/view/DimensionUtils.kt`; containers do
   not duplicate density or dp-to-pixel behavior.
10. State uses `SnapshotMutationPolicy + MVCC + MutableSnapshot` transactions, and recomposition
    reads from one consistent snapshot.
11. `RenderSession` invalidations are merged on a Choreographer-aligned frame. Explicit `render()`
    remains immediate.
12. Animation uses `MonotonicFrameClock` with the host Choreographer implementation, aligning
    `animate*AsState/Animatable/Transition` with recomposition scheduling.
13. `graphicsLayer` participates in resolved modifier and patch semantics, avoiding full rebinds for
    state-driven transforms.
14. One per-View gesture dispatcher uses `gesture > clickable fallback` consumption and supports
    direction lock, slop, and priority arbitration.
15. List and Pager insert/remove/move/change motion is opt-in and cooperates with DiffUtil and
    ItemAnimator without changing defaults.
16. The graphics pipeline implements Canvas nodes and drawing modifiers. `drawWithCache` retains
    commands between frames until dependencies change.
17. Graphics v2 preserves four-corner `DrawRoundRect`, applies `DrawPaint` to Drawable rendering,
    and recursively combines `ImageFilterModel.Chain`.
18. Release baselines use an R8-optimized, resource-shrunk, non-debuggable benchmark target.
    `ReleaseBaselineBenchmark` covers cold start and state-patch frames without ART precompilation.
19. List comparison runs ViewCompose LazyColumn, Compose LazyColumn, and an idiomatic Android Views
    `RecyclerView` in the same target with the same 1,000 items and interaction script, covering
    bidirectional fast scroll and keyed reorder plus payload updates.
20. Complex-layout comparison runs the same 18-card dashboard through ViewCompose
    `ScrollableColumn`, Compose `Column.verticalScroll`, and a retained Android Views
    `ScrollView`/ViewGroup tree, exercising deep scrolling, all-card updates, and conditional detail
    subtrees.
21. Both comparisons collect frame timing and peak heap/RSS. `compare_macrobenchmarks.py` emits
    engine-neutral report-v2 Markdown/JSON with absolute values, ViewCompose/Compose and
    ViewCompose/Android Views observations, while retaining the same-run Compose control for the
    historical longitudinal gate and reading accepted report-v1 baselines.
22. Advanced shadows have independent bounded outer/inner raster caches. Translation, scale,
    rotation, and alpha reuse rasters. `ShadowPerformanceComparisonBenchmark` covers 1,000-item Lazy
    and complex-layout scrolling/mutation with Compose as the same-run noise control.
23. Application-process development tooling follows a zero-recurring-work contract. The optional
    running-device DSL locator performs no report write or live View inspection during scrolling;
    one explicit nonce-bearing IDE request produces one bounded snapshot and response.
24. Warm interaction benchmarks wait 5 seconds after launch and fixture positioning outside the
    measured block. Cold-start workloads do not wait because launch is the measured operation.
25. Navigation motion keeps push and pop separate while executing eight same-direction transitions
    per measured iteration. Pop setup preloads eight destinations outside measurement.
26. Reused nodes whose type, environment, and NodeSpec are unchanged use a modifier-only patch.
    Visual-only updates retain LayoutParams and skip full Node binding; layout modifiers replace
    LayoutParams without recreating or semantically rebinding the native View.
27. `LocalContext` stores the installed immutable `LocalSnapshot` rather than rebuilding a snapshot
    object for every group or emitted node. Snapshot creation scales with provider boundaries; a
    batch `ProvideLocals` call installs one snapshot for all of its bindings.

### 2.2 Release benchmark entry points

Build gate:

```bash
./gradlew qaRelease
```

It builds the R8 release target, non-debuggable benchmark target, and benchmark instrumentation APK,
catching shrink, R8, and variant regressions without a device.

Device benchmark:

```bash
./gradlew benchmarkRelease
```

Device benchmark plus engine comparison report:

```bash
./gradlew benchmarkCompare
```

Default reports:

1. `build/reports/benchmarks/engine-comparison.md`
2. `build/reports/benchmarks/engine-comparison.json`

Regenerate from an existing result:

```bash
./gradlew benchmarkComparisonReport \
  -PbenchmarkResult=/path/to/current-benchmarkData.json
```

For a thermally constrained physical device, run each required ViewCompose, Compose, or Android
Views method from the same `NONE`/`LIGHT` starting state, cool between methods, and place the
resulting JSON files in one clean directory. Passing that directory as `benchmarkResult` merges the
split methods only when their device, OS, clock-policy, and compilation identities match. Duplicate
benchmark names, incomplete engine sets, and context mismatches fail closed; the tool never selects
an arbitrary newest partial run. Legacy results without an explicit clock policy continue to
require the AndroidX `cpuLocked` snapshots to match.

Install the benchmark and target APKs once before a split-method batch. After installation, stop
both processes, turn the screen off, and wait until the accepted thermal state and normal CPU
minimum-frequency state have returned. Then invoke each method directly through the installed
`AndroidJUnitRunner`, pull its `com.viewcompose.benchmark-benchmarkData.json`, stop both processes,
and cool again. Do not use a fresh `connectedBenchmarkAndroidTest` installation for every method:
AndroidX snapshots `cpuLocked` when the instrumentation process starts, and an OEM package-install
or wake boost can temporarily raise `scaling_min_freq` and misclassify an unlocked device. The
host-verified consumer-device protocol passes
`androidx.benchmark.output.payload.clockPolicy=unlocked-dvfs-preflight-v1` to every method. Reports
then compare that durable protocol and retain all observed AndroidX lock snapshots, including a
mixed value, as diagnostic metadata. A missing or different policy fails closed; raw lock snapshots
are never rewritten after collection.

An explicit unlocked-DVFS policy identifies the host protocol; it does not claim that an OEM will
hold a stable working frequency. The run-P50 CV gate remains mandatory. If a method produces two
frequency plateaus or changes `scaling_max_freq` while thermal status is unchanged, reject it
instead of rerunning until one sample happens to pass. An AndroidX Runtime Image warning is
workload-sensitive: reject it for cold start, first build, navigation, or any result that measures
class loading or claims a clean `CompilationMode.None` context. A steady-state interaction may be
accepted only when the complete target is ready, a fixed settling window is outside measurement,
all controls share the reported compilation identity, and stability passes; record that evidence
as `run-from-apk` and never relabel it as clean uncompiled startup data. The warning narrows the
claim rather than disappearing from the evidence. `cmd power set-fixed-performance-mode-enabled`
is not equivalent to a clock lock unless the device proves stable minimum and maximum frequencies
throughout measurement. Use a rootable or otherwise clock-controllable reference device when the
consumer device cannot satisfy this gate.

Compare with a same-device historical baseline and apply the regression gate. The baseline input is
the previously generated revisioned comparison report, not raw Macrobenchmark JSON:

```bash
./gradlew benchmarkComparisonReport \
  -PbenchmarkResult=/path/to/current-benchmarkData.json \
  -PbenchmarkBaseline=/path/to/baseline-engine-comparison.json
```

Both Markdown and JSON comparison reports identify every row by scenario ID, workload revision,
engine, and measured action. Report v2 reads accepted `compose-comparison.json` report-v1 baselines
and preserves their ViewCompose/Compose meaning; it never manufactures missing Android Views data.
The gate rejects a baseline row whose scenario identity or workload revision differs from the
current row. Raw `benchmarkData.json` remains the current-run input, but it is not a valid
longitudinal baseline because it does not preserve the Demo workload contract.

`ReleaseBaselineBenchmark` is the release authority:

1. The target is R8-optimized, resource-shrunk, and non-debuggable.
2. `CompilationMode.None` isolates ART precompilation benefit and exposes the delivered binary.
3. Fixed scenarios are cold start and state patch.
4. Formal physical interaction methods use five clean iterations. Cold startup uses ten because its
   genuine first-run cold-cache variance otherwise lets one sample dominate the stability result.
   Start each method at Android thermal status `NONE` or `LIGHT`, stop and cool between methods,
   and reject a batch that reaches `SEVERE`.
5. Compare results longitudinally only on the same device, system version, iteration protocol, and
   thermal policy.

`ListPerformanceComparisonBenchmark` is the three-engine list control:

1. All three engines run in one R8 target, removing application/resource/process differences.
2. `CompilationMode.None` prevents precompilation from hiding framework delivery cost.
3. `viewComposeListScroll/composeListScroll/androidViewsListScroll` use identical gestures.
4. `viewComposeListMutation/composeListMutation/androidViewsListMutation` use the same 37-item
   rotation and update every sixteenth item. Each measured iteration executes eight complete
   mutate/reset cycles so the run-level frame distribution is large enough for the stability gate.
5. Every iteration waits 5 seconds after the target is ready, outside the measured block, so OEM
   Activity-launch boosting cannot make the first interaction artificially fast.
6. Conclusions come from one device run; never divide results from different devices.
7. The Android Views control uses `RecyclerView`, stable IDs, synchronous `DiffUtil`, and
   payload-aware binding. It represents idiomatic direct-platform reuse rather than a simulated
   declarative full-tree rebuild.

`ComplexLayoutPerformanceComparisonBenchmark` is the three-engine complex-tree control:

1. `viewComposeComplexLayoutScroll/composeComplexLayoutScroll/androidViewsComplexLayoutScroll`
   compare non-Lazy whole-tree scroll.
2. `viewComposeComplexLayoutUpdate/composeComplexLayoutUpdate/androidViewsComplexLayoutUpdate`
   update all 18 cards and toggle the conditional detail subtree through eight complete
   update/reset cycles per measured iteration.
3. The same 5-second unmeasured post-launch settling window applies to all three engines.
4. Cards, metrics, labels, conditional counts, and nesting order are equal.
5. This scenario measures ViewGroup depth, whole-tree measure/layout, and local patches; it does not
   evaluate Lazy containers.
6. The Android Views control retains each card hierarchy, patches its text fields, and genuinely
   adds or removes the conditional detail row. It is the idiomatic imperative reference, not an
   assertion of algorithmic equivalence with either declarative engine.

Accepted Samsung SM-G991B / Android 13 replacement baselines from 2026-08-15 use five iterations,
per-method `NONE`/`LIGHT` starts, the 5-second setup settling window, and clock policy
`unlocked-dvfs-preflight-v1`:

| Workload | ViewCompose P50/P95 | Compose P50/P95 | ViewCompose/Compose run-P50 CV |
| --- | ---: | ---: | ---: |
| `performance.list@3` scroll | 4.620 / 9.048 ms | 5.098 / 8.554 ms | 0.041 / 0.072 |
| `performance.list@3` mutation | 4.651 / 9.278 ms | 9.163 / 24.855 ms | 0.009 / 0.034 |
| `performance.complex-layout@3` scroll | 5.596 / 8.603 ms | 5.221 / 8.457 ms | 0.011 / 0.037 |
| `performance.complex-layout@3` update | 6.063 / 42.505 ms | 9.527 / 50.296 ms | 0.079 / 0.082 |

These are revisioned baseline values, not a claim that one engine is universally faster. In
particular, list and complex-layout update exercise different framework strategies from scrolling.
The table remains the full two-engine historical baseline; it is not overwritten by a partial
three-engine batch.

The same-day fail-fast Android Views batch accepted three complete steady-state actions. All nine
methods started at `NONE`/`LIGHT`, used the same device/build, 5-second unmeasured settling window,
`unlocked-dvfs-preflight-v1`, and reported `run-from-apk`. Each emitted the Runtime Image warning,
so these values are valid only for post-ready interaction and are not clean uncompiled startup
evidence:

| Workload | ViewCompose P50/P95 | Compose P50/P95 | Android Views P50/P95 | Run-P50 CV (VC/C/AV) |
| --- | ---: | ---: | ---: | ---: |
| `performance.list@3` mutation | 4.237 / 9.990 ms | 8.236 / 22.222 ms | 3.940 / 8.666 ms | 0.013 / 0.091 / 0.030 |
| `performance.complex-layout@3` scroll | 5.412 / 7.363 ms | 5.141 / 7.920 ms | 4.662 / 6.913 ms | 0.082 / 0.070 / 0.008 |
| `performance.complex-layout@3` update | 5.780 / 36.928 ms | 8.620 / 40.324 ms | 6.912 / 16.007 ms | 0.122 / 0.032 / 0.141 |

The ViewCompose list-scroll control was rejected before its other engines ran. Its P50/P95 was
`4.212/8.624 ms`, but run P50 values `4.296/4.362/2.627/4.648/4.554 ms` produced CV `0.182`.
List scroll therefore remains `inconclusive`; the full twelve-method baseline requires a
clock-controllable device. The accepted same-run memory maxima are also mixed: versus Android
Views, ViewCompose heap/RSS was `12.2%/2.8%` higher for list mutation, `19.9%/3.5%` higher for
complex scroll, and `15.3%/7.9%` lower for complex update. Those process-level maxima do not support
a universal memory winner.

`DemoInteractionBenchmark` retains focused fixture baselines outside the Compose comparisons:

1. `diagnosticsThemeLongFlingToBottomAndBackRevision2` executes eight fixed forceful flings in each
   direction and proves the real bottom and top anchors after their respective gesture sequences.
2. `collectionsScrollRevision2` captures the nested LazyColumn bounds during setup, then executes
   eight fixed swipes in each direction without performing Accessibility queries inside the measured
   block. Each swipe has a 500 ms physical settle window because benchmark setup disables
   UiAutomator's implicit idle timeout; omitting that window overlaps inertial scrolls and causes
   non-workload `Buffer Stuffing` in FrameTimeline.
3. `collectionsStressMutationRevision2` executes eight complete rotate/insert/reset cycles and
   asserts that every reset restores the original logical order.
4. All three wait through the same 5-second unmeasured launch-settling window. Formal raw results
   record `scenario`, `workloadRevision`, and `clockPolicy` through AndroidX benchmark payload.

Accepted Samsung SM-G991B / Android 13 fixture baselines from 2026-08-15 use five iterations,
per-method `NONE`/`LIGHT` starts, `CompilationMode.Partial`, and clock policy
`unlocked-dvfs-preflight-v1`:

| Workload | Frame CPU P50/P95 | Run-P50 CV |
| --- | ---: | ---: |
| `diagnostics.theme@2` fixed long-fling round trip | 3.067 / 7.336 ms | 0.008 |
| `collection.stress@2` nested-list scroll round trip | 3.357 / 6.288 ms | 0.018 |
| `collection.stress@2` eight-cycle mutation | 4.358 / 10.507 ms | 0.018 |

The collection-scroll preflight is also the reference for gesture-driver contamination. Repeated
target lookup inside measurement first added Accessibility traversal. After that was removed,
back-to-back swipes still produced run-P50 plateaus near 3.6, 7.2, and 14.7 ms. Perfetto showed
stable `RV Scroll`, display-list recording, and RenderThread draw cost across those runs; only
`dequeueBuffer` wait changed, and FrameTimeline classified the slow spans as `Buffer Stuffing`.
Changing refresh-rate or ART compilation policy did not remove it. The explicit per-gesture settle
did, reducing run-P50 CV to 0.018. Never interpret an unpaced synthetic input loop as framework
scroll cost.

`ShadowPerformanceComparisonBenchmark` is the shadow control:

1. ViewCompose and Compose use the same layers, colors, sizes, shapes, list data, and complex layout.
2. Eight paired methods cover shadow-list scroll/mutation and shadow-complex scroll/update. Every
   mutation/update iteration executes eight complete action/reset cycles and asserts restoration,
   matching the accepted non-shadow comparison protocol and producing enough frames for the
   run-stability gate.
3. `shadowRenderPolicy=exact_bitmap|render_node|auto` changes only the ViewCompose backend; the
   Compose result normalizes thermal and background noise.
4. Ten runs per backend on Samsung SM-G991B / Android 13 on 2026-07-30 showed mixed P50, P95, and RSS
   direction for RenderNode versus ExactBitmap, with no stable benefit.
5. `Auto` therefore remains `ExactBitmap`; `RenderNodeDisplayList` is an explicit experiment, not a
   release default.

Accepted Samsung SM-G991B / Android 13 `Auto` (`ExactBitmap`) replacement baselines from
2026-08-15 use five iterations, per-method `NONE`/`LIGHT` starts, the 5-second setup settling
window, eight mutation/update cycles, and clock policy `unlocked-dvfs-preflight-v1`:

| Workload | ViewCompose P50/P95 | Compose P50/P95 | ViewCompose/Compose run-P50 CV |
| --- | ---: | ---: | ---: |
| `performance.shadow-list@2` scroll | 4.613 / 9.650 ms | 5.022 / 8.708 ms | 0.052 / 0.044 |
| `performance.shadow-list@2` mutation | 4.860 / 9.750 ms | 9.035 / 24.787 ms | 0.023 / 0.117 |
| `performance.shadow-complex-layout@2` scroll | 5.728 / 8.724 ms | 5.481 / 8.695 ms | 0.016 / 0.046 |
| `performance.shadow-complex-layout@2` update | 6.236 / 41.506 ms | 10.348 / 46.824 ms | 0.049 / 0.044 |

All eight methods pass the `0.15` run-stability gate. The comparison report preserves mixed raw
AndroidX `cpuLocked` snapshots while accepting the batch through its explicit host-verified clock
policy. High update P95 values remain part of the baseline: both engines rebuild many shadowed
cards and a conditional subtree, so P50 alone is not an adequate interpretation.

Navigation revision 6 and design-bundle revision 3 do not yet have accepted physical baselines.
On the same Samsung device, four navigation transitions supplied 202-223 frames per run, but OEM
frequency ceilings alternated between full and capped values even when Android thermal status ended
at `NONE`. Unlocked and fixed-performance trials produced run-P50 CV values from `0.308` to `0.372`.
The representative Cut Contrast patch method also failed at `0.262`. The device denies clearing ART
profile data to shell, and fixed-performance plus enhanced-processing modes did not provide a real
clock lock. These results are rejected capability evidence, not framework regressions or baselines;
finish those two matrices on a clock-controllable reference device.

Same-device backend commands:

```bash
./gradlew :viewcompose-benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.benchmark.ShadowPerformanceComparisonBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.shadowRenderPolicy=exact_bitmap

./gradlew :viewcompose-benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.viewcompose.benchmark.ShadowPerformanceComparisonBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.shadowRenderPolicy=render_node
```

See the
[advanced-shadow execution record](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/ADVANCED_SHADOW_EXECUTION_PLAN_2026-07.md)
for complete data and the decision.

Automated report and regression rules:

1. Comparison output always includes frame CPU P50/P95, frame overrun P50/P95, heap max, and RSS anon
   max.
2. Every non-shadow scenario requires ViewCompose, Compose, and Android Views entries from the same
   benchmark context; shadow scenarios continue to require ViewCompose and Compose. The JSON may be
   the deterministic in-memory merge of separately cooled method results from the same context.
3. A historical comparison requires the same device model, system fingerprint, explicit clock
   policy, and compilation mode. Legacy results without a clock policy fall back to strict AndroidX
   CPU-lock snapshot matching.
4. The longitudinal gate remains report-v1 compatible and fails only when both the raw ViewCompose
   threshold and normalized ViewCompose/Compose ratio threshold are exceeded. Android Views is an
   absolute and pairwise observation until an explicit future policy changes the control.
5. Defaults live in `tools/performance/benchmark_policy.json`; changes below the absolute noise floor
   do not fail.
6. The report computes coefficient of variation across iteration P50 values for positive
   ratio-scale frame CPU duration. A value above `0.15` is unstable and must be rerun rather than
   interpreted. Signed `frameOverrunMs` remains a reported and gated result, but does not use CV:
   division by a mean near its meaningful zero would manufacture instability.
7. More repetitions are not automatically stronger evidence: a continuously heating run is
   invalid even when its aggregate coefficient of variation is below the threshold.

### 2.3 Benchmark conclusion contract

An accepted run is not complete documentation until its result is interpreted here or in a more
specific owning active page. Every conclusion records the workload and revision, comparison
environment, absolute values, normalized deltas, stability result, limitations, decision, and next
action. Use exactly one primary classification:

- `improved`: decision metrics are materially better and no important counter-metric regresses;
- `regressed`: at least one decision metric is materially worse and the other decision metrics do
  not reverse the interpretation;
- `mixed`: important metrics move in opposite directions, including a better median with a worse
  tail;
- `no material change`: no decision metric crosses the applicable combined normalized and absolute
  gate, and opposing directions do not require a `mixed` classification;
- `inconclusive`: instability, environment mismatch, insufficient coverage, or another validity
  failure prevents a directional claim.

For frame CPU duration, lower is better. The normalized delta is
`(ViewCompose / control - 1) * 100`; reports use the less ambiguous words `lower` and `higher`
instead of relying on the sign. Interpretation uses the owning gate's normalized and absolute
thresholds together. The current comparison policy treats P50 as material only beyond both 10% and
0.3 ms, and P95 only beyond both 15% and 0.8 ms; the Debug Tooling policy below is intentionally
different. Conclusions must report P50 and P95 separately, retain absolute values when a relative
result is favorable but still misses a frame budget, and preserve rejected runs as capability
evidence rather than silently selecting a passing sample. Raw data, a green task, or a favorable
single metric is not a conclusion.

### 2.4 Current comparative conclusion

The accepted 2026-08-15 Samsung SM-G991B / Android 13 data above produces this durable comparison
against the same-run Compose control:

| Workload | P50 delta | P95 delta | Classification | Interpretation |
| --- | ---: | ---: | --- | --- |
| `performance.list@3` scroll | 9.4% lower | 5.8% higher | `mixed` | The median is directionally lower and the tail directionally higher; neither crosses the combined comparison gate. |
| `performance.list@3` mutation | 49.2% lower | 62.7% lower | `improved` | Keyed mutation and payload update are a clear comparative strength. |
| `performance.complex-layout@3` scroll | 7.2% higher | 1.7% higher | `no material change` | Both metrics are directionally slower, but neither crosses the combined comparison gate. |
| `performance.complex-layout@3` update | 36.4% lower | 15.5% lower | `improved` | Whole-tree update is faster than the control, but the absolute 42.505 ms P95 remains a tail-latency risk. |
| `performance.shadow-list@2` scroll | 8.1% lower | 10.8% higher | `mixed` | Median and tail move in opposite directions. The 0.942 ms absolute P95 gap exceeds the noise floor, but its 10.8% increase remains below the 15% failure threshold. |
| `performance.shadow-list@2` mutation | 46.2% lower | 60.7% lower | `improved` | Shadowed keyed mutation retains the non-shadow mutation advantage. |
| `performance.shadow-complex-layout@2` scroll | 4.5% higher | 0.3% higher | `no material change` | Both absolute changes remain inside the noise floors; the direction is slightly slower but does not support a regression claim. |
| `performance.shadow-complex-layout@2` update | 39.7% lower | 11.4% lower | `improved` | Relative update cost improves, but the absolute 41.506 ms P95 remains a tail-latency risk. |

The current conclusion is therefore scoped, not universal:

1. mutation and whole-tree update workloads are consistently faster than the Compose control in
   this accepted batch;
2. scrolling is not consistently faster, although no accepted scrolling row crosses the automated
   regression gate: shadow-list P95 is the first directional optimization target, followed by
   non-Lazy complex-layout P50; ordinary-list P95 also remains a monitored directional gap;
3. the two complex update workloads remain absolute tail-latency targets even though their
   relative comparison is favorable;
4. diagnostics and collection fixtures have accepted ViewCompose-only stability baselines, not a
   Compose ranking;
5. navigation revision 6 and design-bundle revision 3 remain `inconclusive` until a
   clock-controllable device can produce valid evidence;
6. the accepted same-run memory directions are mixed, so no universal memory winner is claimed;
7. the accepted Android Views batch proves three steady-state actions, not the complete scenario:
   list mutation has a native tail regression, complex scroll has a native median regression, and
   complex update has a better median but a much worse tail; list scroll remains `inconclusive`.

Against the same-run Android Views control, the accepted partial batch is:

| Workload | P50 delta | P95 delta | Classification | Interpretation |
| --- | ---: | ---: | --- | --- |
| `performance.list@3` mutation | 7.5% higher | 15.3% higher | `regressed` | P50 remains inside the combined gate, but the `+1.323 ms` P95 gap crosses both tail thresholds. |
| `performance.complex-layout@3` scroll | 16.1% higher | 6.5% higher | `regressed` | The `+0.750 ms` median gap crosses both P50 thresholds; P95 remains inside the gate. |
| `performance.complex-layout@3` update | 16.4% lower | 130.7% higher | `mixed` | The median is `1.132 ms` better, but P95 is `20.921 ms` worse; patch throughput and tail latency move in opposite directions. |

Correct reuse, group-level invalidation plus skipped work, and stable container refresh semantics
remain the optimization strategy. SlotTable Lite and subtree recomposition are on the main path and
`qaQuick` passes, but those implementation facts do not override the measured mixed scrolling
result. Device-gate status remains recorded in the [roadmap](../project/roadmap.md).

#### 2.4.1 Complex-layout update tail-latency investigation

A 2026-08-16 follow-up on the same Samsung SM-G991B / Android 13 device investigated the native
tail gap without replacing the accepted revision-3 baseline. The fresh ViewCompose control used
the DSL-convergence branch, an R8 benchmark build, `CompilationMode.None`, five iterations, a
5-second unmeasured settle, `NONE`/`LIGHT` thermal starts, and
`unlocked-dvfs-preflight-v1`. It reported `6.023/41.187 ms` frame-CPU P50/P95. A separately cooled
Android Views control reported `7.253/16.222 ms`. Both runs emitted the Runtime Image warning, and
the device cannot lock CPU frequency, so these are adjacent diagnostic controls rather than new
longitudinal baselines.

Perfetto associated the slow ViewCompose samples with the actual update/reset frames rather than
the automation polling frames. Representative worst frames spent `16.985-17.166 ms` in
`VC.Compose`, `42.555-55.960 ms` in `VC.RenderTree`, and another `36-38 ms` in the following Android
View traversal. The application thread stayed runnable: there was no blocking I/O, lock wait, or
foreground GC in those spans. The OEM scheduler placed the long declarative work on a LITTLE CPU in
the worst samples, multiplying ordinary `4-6 ms` composition and `12-20 ms` render phases. The
native control performs the same field changes through retained View references and therefore
usually finishes before that scheduling sensitivity is amplified.

The following adjacent experiments preserve `performance.complex-layout@3`; every rejected source
candidate was removed after its run:

| Candidate | ViewCompose P50/P95 | Change from fresh control | Conclusion |
| --- | ---: | ---: | --- |
| Return the original `String` for plain `TextDocument` binding | 6.197 / 40.785 ms | P50 2.9% higher; P95 1.0% lower | `no material change`; retain the allocation-free plain-text conversion because it is deterministic and preserves rich-text spans, but it does not solve the tail. |
| Remove about 90 redundant Demo `Surface` wrappers | 6.042 / 40.330 ms | P50 0.3% higher; P95 2.1% lower | `no material change`; physical View depth is not the primary cause, and the workload fixture remains unchanged. |
| Recursively prove value-equal rebuilt subtrees stable | 6.254 / 44.171 ms | P50 3.8% higher; P95 7.2% higher | `no material change` with an unfavorable direction; recursive proof cost exceeded its skip benefit, so it was reverted. |
| Stream same-position reuse without generic keyed-plan intermediates | 6.137 / 41.163 ms | P50 1.9% higher; P95 0.1% lower | `no material change`; reconcile/preflight container allocation is not the tail root, so the path was reverted. |

Full ART compilation reached `6.261/38.589 ms`, only 6.3% lower at P95 than the fresh control, and
other checkpoint, grouping, and exact-reference fast paths either had no material benefit or made
P95 worse. The combined evidence classifies the current result as `mixed`: the one-line plain-text
allocation fix is valid, while the absolute tail remains unresolved. The root boundary is the
top-level State read: one revision invalidates a declaration that synchronously rebuilds and diffs
all 18 cards, whereas the native control directly updates retained fields. Constant-factor changes
inside the complete-tree transaction cannot erase that algorithmic difference.

The Q3 observed-property transaction architecture now implements that algorithmic cut. Opted-in
State reads are owned by a `RenderSession` property registry, all dirty readers use one Snapshot,
and Android Renderer receives one exact-target batch that bypasses root composition, tree wrapping,
and child reconciliation. Candidate dependencies use prepared replacement with invalidation guards;
the renderer preflights the whole batch and restores all earlier native values if one patch fails.
`performance.complex-layout@4` therefore separates the primary property action from a secondary
structural add/remove action instead of letting either cost hide the other.

Three final-build property runs on the same device reported `6.261/25.087 ms`, `5.601/20.436 ms`,
and `5.436/20.206 ms` frame-CPU P50/P95. The first run's P95 is 39.1% below the fresh revision-3
whole-tree control; its paired revision-4 Compose and Android Views controls were respectively
`9.066/42.353 ms` and `7.922/16.006 ms`. Against those controls, ViewCompose was 30.9%/40.8% lower
than Compose and 21.0% lower at P50 but 56.7% higher at P95 than direct Android Views. Perfetto on
the first final run measured 16 property frames: `VC.FrameRender` averaged/maxed
`5.895/13.469 ms`, `VC.ObservedPropertyRead` `1.572/4.216 ms`, and
`VC.ObservedPropertyRender` `3.640/8.391 ms`; remaining maxima included `10.334 ms` measure and
`17.048 ms` draw work in Android traversal.

This evidence is `inconclusive` as a formal baseline despite the material and repeatable direction.
The three final runs had run-P50 CV values of `0.201`, `0.208`, and `0.215`, above the `0.15`
acceptance ceiling. The non-rooted device also emitted the Runtime Image warning and became faster
across reruns because Macrobenchmark could not clear application profiles. The implementation and
correctness gates can land, but revision 4 must not replace an accepted longitudinal baseline until
all three engines' property and structural actions are rerun on a clock-controllable device with
stable compilation state. The next action is that six-method acceptance matrix; the residual
ViewCompose-versus-native P95 gap remains Android property invalidation plus measure/draw tail, not
complete-tree reconciliation.

### 2.5 Debug tooling regression gate

Release macrobenchmarks cannot detect costs that exist only in debuggable builds. Any tooling that
executes in an application process therefore adds a same-device debug comparison for every hot path
it can observe. Hold device model, system build, application commit, workload, refresh rate, power
mode, and thermal state constant. Record frame CPU P50/P95 and a tooling-operation counter.

The default acceptance rule is conjunctive: P50 fails only when it regresses by more than both 5%
and 0.3 ms; P95 fails only when it regresses by more than both 10% and 0.8 ms. Idle scrolling must
record exactly zero tooling report writes. A deliberately invoked inspection request is measured
separately and cannot be amortized into the idle result.

The 2026-08-13 locator incident is the reference failure: on Samsung SM-G991B / Android 13 with
SurfaceFlinger active at 60 Hz, the Demo home-list frame CPU P50 moved from approximately 5--7 ms
to 11--12 ms after continuous scroll/layout publication entered `viewcompose-host-android`;
removing the scroll publication restored approximately 7 ms. The architectural correction moved
the implementation to the optional `viewcompose-preview` artifact and made publication
request-driven.

## 3. Performance gate metrics

Every performance change evaluates at least:

1. rebuild cost: tree production and reconciliation after state changes;
2. binding cost: View rebind and patch execution;
3. layout cost: measure/layout count and depth;
4. container cost: refresh and reuse stability in delayed sessions.

Standard evidence includes same-device/same-path viewcompose-benchmark data, render stats including
rebound/skipped, and key layout-pass counts.

## 4. Design constraints

1. Define the hot path and acceptable cost before expanding a new component API.
2. A new modifier or spec cannot force unconditional full rebind.
3. A reuse container must refresh visible content while structure remains stable.
4. Treat `AndroidView` as a performance isolation boundary: replayable configuration belongs in
   `update/onReset/nativeView`, external commit effects in `onCommit`, and final resource unbinding in
   one-time `onRelease`.
5. Do not trade module boundaries or maintainability for a local optimization.
6. Keep node-group keys stable or explicitly accept ancestor fallback recomposition plus warning.
7. Validate concurrent state writes through snapshot apply; optimizations cannot bypass merge and
   failure paths.
8. Do not return recomposition scheduling to `container.post`; frame alignment is the default
   boundary.
9. Animation reuses `MonotonicFrameClock`; do not add a parallel scheduler inside an animation API.
10. A gesture-arbitration change validates Lazy, Scrollable, and Pager scenarios and cannot hide a
    regression through universal interception.
11. Graphics optimization preserves `drawWithCache` dependency-based rebuild semantics and cannot
    return cache creation to every frame.
12. Image optimization preserves semantics: Drawable applies `DrawPaint`, and `ImageFilter.Chain`
    cannot silently become a no-op.
13. A static-shadow key includes dimensions, density, layout direction, shape, and every layer. It
    never caches a View, Session, or mutable application object.
14. Translation/scale/rotation/alpha reuse existing shadow rasters; only blur/spread/shape/dimensions
    may rebuild.
15. A default shadow-backend change requires same-device/build/workload paired runs and the Compose
    normalized gate.
16. Large or per-frame blur/spread/RenderEffect paths define memory and off-screen budgets before
    entering a default list or transition.
17. Application-process tooling cannot attach recurring listeners to scroll, global layout, draw,
    touch, animation-frame, or recomposition hot paths. A justified continuous observer requires a
    new ADR, an explicit static-gate allowlist, and same-device Debug benchmark evidence.

## 5. Anti-patterns

1. Deep nesting in place of an explicit container policy.
2. Complex optimization without benchmark evidence.
3. Blaming runtime for every cost while ignoring page and container structure.
4. Modifying a renderer hot path without regression coverage.

## 6. Phases

### Phase 1: Baseline and observability

Status: foundation complete. The benchmark path is stable and core metrics are readable.

### Phase 2: Skip capability

Status: complete for this cycle. Node-level skip coverage has expanded and unnecessary rebinds are
reduced. Lazy/Pager keyed diff uses DiffUtil and reports `SkipSubtree + skippedSubtrees`; later work
continues in Phases 3 and 4.

### Phase 3: Diagnostics

Status: core visualization complete. Render tree, patches, CompositionLocal, and recomposition
reasons are readable; node highlighting, cross-session correlation, and per-node time remain.

### Phase 4: Containers and layout

Status: list and complex-layout Compose controls, Android Views source controls, memory metrics,
engine-neutral reports, and normalized ViewCompose/Compose gates are established. Three
same-context Android Views actions are accepted; list scroll remains `inconclusive` until a
clock-controllable device can complete the twelve-method batch. Continue reducing layout cost in
hot containers and complex pages while keeping the three-engine workload contract aligned.

### Phase 5: Release optimization

Status: R8 release baseline established; baseline profile remains. Quantify delivery improvements
against the current no-ART-precompilation baseline.

### Phase 6: Advanced-shadow backend

Status: exact static backend, cache, Compose control, and first device decision complete; dynamic
RenderEffect remains research. Keep `Auto = ExactBitmap`, grow the device matrix, and evaluate
dynamic blur/transition shadows only inside explicit memory and frame budgets.

## 7. Review and submission

A performance PR states the affected cost class, provides before/after metrics, explains any
container refresh impact, and updates this or another owning specification.

Visible layout, interaction, overlay, or input changes add instrumentation regression or record a
scoped exemption and deadline.

See [Development workflow](../project/workflow.md).

## 8. Related documents

1. [Architecture overview](../architecture/overview.md)
2. [Delayed-session containers](../architecture/session-containers.md)
3. [Unified roadmap](../project/roadmap.md)
4. [Documentation entrance](../README.md)
5. [State snapshots](../architecture/state-snapshots.md)
6. [Android Views performance control plan](../project/plans/android-views-performance-control.md)
