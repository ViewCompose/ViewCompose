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
19. List comparison runs ViewCompose and Compose LazyColumn in the same target with the same 1,000
    items and interaction script, covering bidirectional fast scroll and keyed reorder plus payload
    updates.
20. Complex-layout comparison runs the same 18-card dashboard through ViewCompose
    `ScrollableColumn` and Compose `Column.verticalScroll`, mounting the complete tree and exercising
    deep scrolling, all-card updates, and conditional detail subtrees.
21. Both comparisons collect frame timing and peak heap/RSS. `compare_macrobenchmarks.py` emits
    paired Markdown/JSON and can gate normalized regression against the same-run Compose control.
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

Device benchmark plus Compose comparison report:

```bash
./gradlew benchmarkCompare
```

Default reports:

1. `build/reports/benchmarks/compose-comparison.md`
2. `build/reports/benchmarks/compose-comparison.json`

Regenerate from an existing result:

```bash
./gradlew benchmarkComparisonReport \
  -PbenchmarkResult=/path/to/current-benchmarkData.json
```

For a thermally constrained physical device, run each ViewCompose or Compose method from the same
`NONE`/`LIGHT` starting state, cool between methods, and place the resulting JSON files in one clean
directory. Passing that directory as `benchmarkResult` merges the split methods only when their
device, OS, clock-policy, and compilation identities match. Duplicate benchmark names and context
mismatches fail closed; the tool never selects an arbitrary newest partial run. Legacy results
without an explicit clock policy continue to require the AndroidX `cpuLocked` snapshots to match.

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
frequency plateaus, changes `scaling_max_freq` while thermal status is unchanged, or reports that
AndroidX cannot clear the Runtime Image, reject it instead of rerunning until one sample happens to
pass. `cmd power set-fixed-performance-mode-enabled` is not equivalent to a clock lock unless the
device proves stable minimum and maximum frequencies throughout measurement. Use a rootable or
otherwise clock-controllable reference device when the consumer device cannot satisfy this gate.

Compare with a same-device historical baseline and apply the regression gate. The baseline input is
the previously generated revisioned comparison report, not raw Macrobenchmark JSON:

```bash
./gradlew benchmarkComparisonReport \
  -PbenchmarkResult=/path/to/current-benchmarkData.json \
  -PbenchmarkBaseline=/path/to/baseline-compose-comparison.json
```

Both Markdown and JSON comparison reports identify every row by scenario ID, workload revision,
and measured action. The gate rejects a baseline row whose scenario identity or workload revision
differs from the current row. Raw `benchmarkData.json` remains the current-run input, but it is not
a valid longitudinal baseline because it does not preserve the Demo workload contract.

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

`ListPerformanceComparisonBenchmark` is the Compose list control:

1. Both engines run in one R8 target, removing application/resource/process differences.
2. `CompilationMode.None` prevents precompilation from hiding framework delivery cost.
3. `viewComposeListScroll/composeListScroll` use identical gestures.
4. `viewComposeListMutation/composeListMutation` use the same 37-item rotation and update every
   sixteenth item. Each measured iteration executes eight complete mutate/reset cycles so the
   run-level frame distribution is large enough for the stability gate.
5. Every iteration waits 5 seconds after the target is ready, outside the measured block, so OEM
   Activity-launch boosting cannot make the first interaction artificially fast.
6. Conclusions come from one device run; never divide results from different devices.

`ComplexLayoutPerformanceComparisonBenchmark` is the complex-tree control:

1. `viewComposeComplexLayoutScroll/composeComplexLayoutScroll` compare non-Lazy whole-tree scroll.
2. `viewComposeComplexLayoutUpdate/composeComplexLayoutUpdate` update all 18 cards and toggle the
   conditional detail subtree through eight complete update/reset cycles per measured iteration.
3. The same 5-second unmeasured post-launch settling window applies to both engines.
4. Cards, metrics, labels, conditional counts, and nesting order are equal.
5. This scenario measures ViewGroup depth, whole-tree measure/layout, and local patches; it does not
   evaluate Lazy containers.

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
2. ViewCompose and Compose for one scenario come from the same benchmark JSON.
   That JSON may be the deterministic in-memory merge of separately cooled method results from the
   same context.
3. A historical comparison requires the same device model, system fingerprint, explicit clock
   policy, and compilation mode. Legacy results without a clock policy fall back to strict AndroidX
   CPU-lock snapshot matching.
4. The gate fails only when both the raw ViewCompose threshold and normalized ViewCompose/Compose
   ratio threshold are exceeded.
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
- `no material change`: observed movement remains inside the applicable normalized and absolute
  noise floors;
- `inconclusive`: instability, environment mismatch, insufficient coverage, or another validity
  failure prevents a directional claim.

For frame CPU duration, lower is better. The normalized delta is
`(ViewCompose / control - 1) * 100`; reports use the less ambiguous words `lower` and `higher`
instead of relying on the sign. Interpretation uses the owning gate's normalized and absolute
thresholds together. It must report P50 and P95 separately, retain absolute values when a relative
result is favorable but still misses a frame budget, and preserve rejected runs as capability
evidence rather than silently selecting a passing sample. Raw data, a green task, or a favorable
single metric is not a conclusion.

### 2.4 Current comparative conclusion

The accepted 2026-08-15 Samsung SM-G991B / Android 13 data above produces this durable comparison
against the same-run Compose control:

| Workload | P50 delta | P95 delta | Classification | Interpretation |
| --- | ---: | ---: | --- | --- |
| `performance.list@3` scroll | 9.4% lower | 5.8% higher | `mixed` | The median improves materially; the tail is directionally worse but remains below the P95 regression gate. |
| `performance.list@3` mutation | 49.2% lower | 62.7% lower | `improved` | Keyed mutation and payload update are a clear comparative strength. |
| `performance.complex-layout@3` scroll | 7.2% higher | 1.7% higher | `regressed` | P50 exceeds both its normalized and absolute regression thresholds; P95 remains close. |
| `performance.complex-layout@3` update | 36.4% lower | 15.5% lower | `improved` | Whole-tree update is faster than the control, but the absolute 42.505 ms P95 remains a tail-latency risk. |
| `performance.shadow-list@2` scroll | 8.1% lower | 10.8% higher | `mixed` | Median work improves, while the 9.650 ms P95 exceeds both tail-regression thresholds and is the highest-priority comparative scrolling gap. |
| `performance.shadow-list@2` mutation | 46.2% lower | 60.7% lower | `improved` | Shadowed keyed mutation retains the non-shadow mutation advantage. |
| `performance.shadow-complex-layout@2` scroll | 4.5% higher | 0.3% higher | `no material change` | Both absolute changes remain inside the noise floors; the direction is slightly slower but does not support a regression claim. |
| `performance.shadow-complex-layout@2` update | 39.7% lower | 11.4% lower | `improved` | Relative update cost improves, but the absolute 41.506 ms P95 remains a tail-latency risk. |

The current conclusion is therefore scoped, not universal:

1. mutation and whole-tree update workloads are consistently faster than the Compose control in
   this accepted batch;
2. scrolling is not consistently faster: shadow-list P95 is the first comparative optimization
   target, followed by non-Lazy complex-layout P50; ordinary-list P95 remains a monitored
   directional gap below the failure threshold;
3. the two complex update workloads remain absolute tail-latency targets even though their
   relative comparison is favorable;
4. diagnostics and collection fixtures have accepted ViewCompose-only stability baselines, not a
   Compose ranking;
5. navigation revision 6 and design-bundle revision 3 remain `inconclusive` until a
   clock-controllable device can produce valid evidence;
6. the accepted tables do not yet contain a durable same-run memory comparison, so no memory winner
   is claimed.

Correct reuse, group-level invalidation plus skipped work, and stable container refresh semantics
remain the optimization strategy. SlotTable Lite and subtree recomposition are on the main path and
`qaQuick` passes, but those implementation facts do not override the measured mixed scrolling
result. Device-gate status remains recorded in the [roadmap](../project/roadmap.md).

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

Status: list and complex-layout Compose controls, memory metrics, automated reports, and normalized
gates are established. Continue reducing layout cost in hot containers and complex pages.

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
