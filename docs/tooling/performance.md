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

Renderer-sensitive fixed-clock diagnostics must control every clock domain that can execute the
measured frame. A CPU-only lock is insufficient when RenderThread or GPU work remains under DVFS.
Record the CPU policy minima and maxima, GPU devfreq governor and bounds, and, when exposed, the
KGSL power-level bounds in the durable `clockPolicy`; verify the current frequencies again after
target launch. If CPU/GPU memory-interconnect devfreq domains such as Qualcomm `cpubw` or `gpubw`
change RenderThread or buffer-queue timing, snapshot and control those votes as part of the same
policy; a stable core/GPU frequency alone does not validate the batch. An OEM performance service
may be stopped only after proving that it overrides the requested bounds. Its prior state, every
modified clock bound or bandwidth vote, charging/input state, and all root or policy changes must
be captured before the batch and restored after the last result is pulled.

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
List scroll was therefore `inconclusive` in that 2026-08-15 batch; Section 2.4.2 supersedes this
device limitation with the corrected revision-4 root-controlled matrix. The accepted same-run
memory maxima were also mixed: versus Android
Views, ViewCompose heap/RSS was `12.2%/2.8%` higher for list mutation, `19.9%/3.5%` higher for
complex scroll, and `15.3%/7.9%` lower for complex update. Those process-level maxima do not support
a universal memory winner.

`DemoInteractionBenchmark` retains focused fixture baselines outside the Compose comparisons:

1. `diagnosticsThemeLongFlingToBottomAndBackRevision2` executes eight fixed forceful flings in each
   direction and proves the real bottom and top anchors after their respective gesture sequences.
2. `collectionsScrollRevision3` captures the direct scenario LazyColumn bounds during setup, then executes
   eight fixed swipes in each direction without performing Accessibility queries inside the measured
   block. Each swipe has a 500 ms physical settle window because benchmark setup disables
   UiAutomator's implicit idle timeout; omitting that window overlaps inertial scrolls and causes
   non-workload `Buffer Stuffing` in FrameTimeline.
3. `collectionsStressMutationRevision3` executes eight complete rotate/insert/reset cycles and
   asserts that every reset restores the original logical order.
4. All three wait through the same 5-second unmeasured launch-settling window. Formal raw results
   record `scenario`, `workloadRevision`, and `clockPolicy` through AndroidX benchmark payload.

Accepted Samsung SM-G991B / Android 13 fixture baselines from 2026-08-15 use five iterations,
per-method `NONE`/`LIGHT` starts, `CompilationMode.Partial`, and clock policy
`unlocked-dvfs-preflight-v1`:

| Workload | Frame CPU P50/P95 | Run-P50 CV |
| --- | ---: | ---: |
| `diagnostics.theme@2` fixed long-fling round trip | 3.067 / 7.336 ms | 0.008 |
| `collection.stress@2` nested-list scroll round trip (retired fixture) | 3.357 / 6.288 ms | 0.018 |
| `collection.stress@2` eight-cycle mutation (retired fixture) | 4.358 / 10.507 ms | 0.018 |

The 2026-08-17 manual-review repair removes the nested outer/inner list hierarchy from
`collection.stress` and advances the scenario to revision 3. The revision 2 numbers remain useful
only as historical evidence for the retired fixture and are not a baseline for revision 3.

The 2026-08-21 post-release run used the rooted Xiaomi MI 6 / Android 9 reference device, target
source `9443edef`, benchmark harness source `93afee0f`, R8/resource-shrunk target APK SHA-256
`179f26d15b35d9add9bfacccf03be046ff4e5dccac633c827e90fb3cada126f2`, and measured benchmark APK
SHA-256 `b5d70245eeebbbdb90260b3157728772cce0241c3fb522ef9cf1cc52b6457b28`. The harness addition only
captures `MemoryUsageMetric(Mode.Max)` for the two revision-3 methods; it does not change the target
fixture or measured action. Every method used five iterations, a 5-second unmeasured ready settle,
per-method screen-off cooling to at most 37 degrees Celsius, eight online CPUs, suspended charging,
stopped vendor performance services, and the actual `run-from-apk` identity. AndroidX reported
`cpuLocked=true` and zero thermal-throttle sleep.

The initial v3 policy fixed CPU policies 0/4 at 1.4016/1.8048 GHz and Adreno at 515 MHz. Its scroll
repeat reached run-P50 CV `0.004`, but two mutation batches failed at `0.192` and `0.224`. Perfetto
showed unchanged application main-thread work while the slow mutation iteration increased
RenderThread time from `2.05` to `3.25 s`; average `dequeueBuffer` increased from `0.319` to
`1.748 ms/frame`, and the GPU/CPU memory-interconnect vote occupied a lower plateau. Because the
renderer-sensitive clock contract requires every executing domain to be controlled, all v3 values
remain diagnostic rather than formal revision-3 baselines.

Policy v4 additionally fixed `cpubw` and `gpubw` to their maximum `13763` performance vote and
records
`root-fixed-cpu-1401600-1804800-gpu-515000000-cpubw-gpubw-13763-perf-hal-off-v4` in the raw payload:

| Revision-3 action/run | Frame CPU P50/P95/P99 | Frame count range | Median peak heap (range) | Run-P50 CV | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Scroll, first v4 batch | 4.206 / 6.221 / 6.680 ms | 803--804 | 4,824 KiB (4,115--5,793) | 0.191 | Rejected |
| Scroll, complete v4 repeat | 4.173 / 6.248 / 6.765 ms | 803--805 | 5,149 KiB (4,294--6,251) | 0.196 | Rejected |
| Eight-cycle mutation | 2.861 / 14.320 / 23.554 ms | 279--299 | 6,662 KiB (6,371--6,732) | 0.025 | Accepted absolute baseline |

Both scroll batches formed the same two run-P50 plateaus: about `6.0 ms` and `4.03--4.11 ms`.
Their paired traces held CPU, GPU, and interconnect controls constant and kept application
main-thread totals materially equal, while the slow run increased RenderThread `DrawFrame` from
`3.298` to `5.093 ms/frame` and `dequeueBuffer` from `0.261` to `2.261 ms/frame`. A diagnostic
benchmark APK that restarted the target before every iteration still failed at CV `0.197`; it
changed no production source and was removed after disproving mixed process lifecycle as the
cause. The remaining scroll variance is therefore attributed to the API-28 display/BufferQueue
pipeline, not to revision-3 list reconciliation, but it still invalidates a timing baseline.

The scoped conclusion is `mixed`: mutation now has a stable fixed-clock absolute baseline, with
directional comparison `inconclusive` because revision 2 is a retired fixture; scroll remains
`inconclusive`, so the post-release Phase 1 gate is not complete. Limitations are one API-28 device,
`run-from-apk` JIT/code placement, and an unresolved system display-buffer plateau. The next action
is to preserve revision 3 and the `0.15` gate, then either establish an additional display-pipeline
control that does not change the measured action or recapture scroll on another root-controllable
reference device. Do not change swipe count, pacing, or fixture merely to obtain a passing batch.

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

<div className="benchmark-evidence">

The 2026-08-17 Pixel 5 / Android 14 run compares only equivalent ViewCompose and Compose methods at
`performance.shadow-list@3`; Android Views is excluded because its shadow implementation differs.
It used commit `0cf6515f305a7d230018f72599b1b52b2a0acf26`, target APK SHA-256
`fad1d21cbbf25ba40da5e507a4de997e35f4b3f23dda81bd0e17cfd726c34ea7`, run-from-APK,
`Auto` (`ExactBitmap`), five iterations, a 5-second setup, four up/down scroll cycles, and eight
mutation cycles. Fixed-performance CPU policies were 1.8048/2.208/2.4 GHz, active GPU was 625 MHz,
and thermal status stayed `NONE`. AndroidX Benchmark 1.5.0-beta01 supplied only the runner to avoid
the 1.4.1 Perfetto shutdown timeout on this device; measured application code was unchanged.

| Workload/run | ViewCompose P50/P95 | Compose P50/P95 | ViewCompose/Compose run-P50 CV | ViewCompose delta | Classification |
| --- | ---: | ---: | ---: | ---: | --- |
| `performance.shadow-list@3` scroll | 2.968 / 5.979 ms | 3.590 / 6.499 ms | 0.013 / 0.052 | 17.3% / 8.0% lower | `improved` |
| `performance.shadow-list@3` mutation, first run | 2.406 / 6.707 ms | 4.529 / 10.249 ms | 0.203 / 0.061 | 46.9% / 34.6% lower | `inconclusive` |
| `performance.shadow-list@3` mutation, full retry | 3.319 / 6.947 ms | 4.529 / 10.249 ms | 0.211 / 0.061 | 26.7% / 32.2% lower | `inconclusive` |

Scroll P50 crosses the 10% and 0.3 ms gates; P95 has the same favorable direction. Its heap/RSS
medians are 14,481/68,332 KiB versus Compose at 12,973/65,112 KiB (+11.6%/+4.9%), below the memory
gates, so scroll is `improved` with a non-material memory cost. Both complete ViewCompose mutation
attempts fail the `0.15` stability gate and form two iteration-P50 bands; mutation is therefore
`inconclusive`, and the retry does not replace the first run. Diagnose session/cache startup state
before accepting mutation. Device and workload revisions differ, so this is not a longitudinal
comparison with the Samsung revision-2 baseline.

The same Pixel 5, application binary, runner, and clock policy then covered all three
`performance.complex-layout@4` actions with ViewCompose, Compose, and Android Views, plus both
equivalent ViewCompose/Compose actions at `performance.shadow-complex-layout@3`. The primary matrix
produced 65 traces; complete three-engine retries of the two unstable ordinary update actions added
30 traces. Every correctness, action/reset, and restoration assertion passed. Battery temperature
stayed between 29.8 and 32.3 degrees Celsius and Android thermal status stayed `NONE`.

| Workload/run | ViewCompose P50/P95 | Compose P50/P95 | Android Views P50/P95 | Run-P50 CV (VC/C/Views) | Frame CPU conclusion |
| --- | ---: | ---: | ---: | ---: | --- |
| `performance.complex-layout@4` scroll | 3.737 / 4.139 ms | 3.844 / 8.222 ms | 3.123 / 4.075 ms | 0.009 / 0.052 / 0.008 | `improved` vs Compose; `regressed` vs Views |
| property update, first run | 4.388 / 10.646 ms | 7.099 / 22.834 ms | 3.126 / 15.741 ms | 0.157 / 0.066 / 0.914 | `inconclusive` |
| property update, full retry | 3.666 / 10.486 ms | 7.204 / 22.434 ms | 3.904 / 8.200 ms | 0.171 / 0.020 / 0.159 | `inconclusive` |
| structure update, first run | 3.651 / 6.409 ms | 6.198 / 12.763 ms | 3.001 / 7.802 ms | 0.055 / 0.175 / 0.189 | `inconclusive` |
| structure update, full retry | 3.821 / 6.750 ms | 6.470 / 12.441 ms | 3.597 / 8.006 ms | 0.131 / 0.059 / 0.227 | `improved` vs Compose; Views `inconclusive` |
| `performance.shadow-complex-layout@3` scroll | 4.104 / 4.586 ms | 4.096 / 9.299 ms | n/a | 0.011 / 0.057 / n/a | `improved` |
| shadow property update | 3.869 / 11.523 ms | 7.559 / 22.915 ms | n/a | 0.147 / 0.020 / n/a | `improved` |

The accepted ordinary-scroll frame result is directionally split: relative to Compose, P50 is a
non-material 2.8% lower while P95 is 49.7% lower; relative to Android Views, P50 is materially 19.7%
higher while P95 is only 1.6% higher. ViewCompose heap/RSS medians are 18,770/85,580 KiB, versus
Compose at 18,373/74,964 KiB and Views at 10,281/73,864 KiB. The RSS cost crosses the gate against
both engines, and both memory metrics cross it against Views, so the complete scroll classification
is `mixed`.

The structure-update retry establishes a stable ViewCompose/Compose pair: ViewCompose P50/P95 are
40.9%/45.7% lower, while heap/RSS medians are 33,984/107,536 KiB versus 27,874.5/83,624 KiB
(+21.9%/+28.6%). Its complete ViewCompose/Compose classification is therefore `mixed`, not a general
win. Android Views remained unstable at CV `0.227`, so that pair is `inconclusive`. Property update
remained unstable across both complete attempts (ViewCompose CV `0.157` then `0.171`; Views `0.914`
then `0.159`), and neither attempt is interpreted despite favorable-looking individual numbers.

Both shadow-complex frame comparisons are stable and `improved`: scroll has a neutral +0.2% P50 and
a 50.7% lower P95, while property update is 48.8%/49.7% lower at P50/P95. Memory reverses part of
that result. Scroll heap is +2.1% but RSS is +21.9%; property-update heap is 17.5% lower but RSS is
16.8% higher. Because each RSS delta crosses the 10% and 4,096 KiB gate, both complete shadow
classifications are `mixed`.

These are accepted per-frame distributions, not synthetic transaction-latency measurements:
Compose update methods emit 46 measured frames while ViewCompose and Android Views emit roughly
240--260. Android Views remains excluded from shadow ranking because its shadow implementation is
not equivalent. The next work is to instrument update action/reset scheduling and initial
session/cache state before accepting the unstable ordinary actions, reduce ViewCompose complex-tree
RSS, and close the ordinary-scroll P50 gap to Views. The Pixel 5 workload revisions are not a
longitudinal comparison with the Samsung baselines.

</div>

At that point, navigation revision 6 and design-bundle revision 3 had no accepted physical baselines.
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
   threshold and the same-run control-normalized ratio threshold are exceeded. Compose remains the
   preferred control; a scenario without Compose uses Android Views. If either subject or control
   is unstable in the current or baseline report, that row is `INCONCLUSIVE`, not PASS or FAIL.
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

The 2026-08-15 accepted-batch conclusion was therefore scoped, not universal:

1. mutation and whole-tree update workloads are consistently faster than the Compose control in
   this accepted batch;
2. scrolling is not universally faster. The Samsung revision-2 shadow-list P95 remains a
   historical directional target, while the Pixel 5 revision-3 shadow-list pair is favorable but
   cannot close that target across a different device and workload revision; non-Lazy
   complex-layout P50 and ordinary-list P95 also remain monitored gaps;
3. the two complex update workloads remain absolute tail-latency targets even though their
   relative comparison is favorable;
4. diagnostics and collection fixtures have accepted ViewCompose-only stability baselines, not a
   Compose ranking;
5. navigation revision 6 and design-bundle revision 3 now have stable root-controlled absolute
   baselines in Section 2.4.2; the design systems remain directionally `inconclusive` without a
   matching prior baseline, and Android 9 cannot provide non-debuggable custom trace attribution;
6. the accepted same-run memory directions are mixed, so no universal memory winner is claimed;
7. that partial Android Views batch proves three steady-state actions, not the complete scenario:
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

This Samsung evidence is `inconclusive` as a formal baseline despite the material and repeatable
direction.
The three final runs had run-P50 CV values of `0.201`, `0.208`, and `0.215`, above the `0.15`
acceptance ceiling. The non-rooted device also emitted the Runtime Image warning and became faster
across reruns because Macrobenchmark could not clear application profiles. Section 2.4.2 supplies
the later root-controlled six-method acceptance matrix. The residual ViewCompose-versus-native P95
gap remains Android property invalidation plus measure/draw tail, not complete-tree reconciliation.
The timing distribution is not accepted, but its API-33 R8 trace is accepted as phase-attribution
evidence: property frames entered `VC.ObservedPropertyRead` and `VC.ObservedPropertyRender`
without returning to the root `VC.Compose` and complete-tree `VC.RenderTree` path. Unlocked DVFS
changes phase duration, not whether those sections were entered. This functional trace evidence is
therefore paired with, rather than substituted for, the fixed-clock timing matrix below.

#### 2.4.2 Root-controlled revision-4 acceptance and remaining tails

The 2026-08-16 acceptance batch used a rooted Xiaomi MI 6 / Android 9 device, an R8 benchmark
target, `CompilationMode.None`, five iterations, all eight CPUs online, charging suspended, and
fixed `performance` governors at 1.4016 GHz for policy 0 and 1.8048 GHz for policy 4. Every current
core method reports `run-from-apk`, `cpuLocked=true`, and
`root-fixed-1401600-1804800-v1`. Battery temperature remained between 30 and 39 degrees Celsius and
AndroidX reported no thermal-throttle wait. Every accepted run-P50 CV is at or below `0.111`; one
Compose scroll run at `0.159` was retained as rejected evidence and replaced by the accepted
`0.060` rerun.

The first list batch exposed a workload defect: ViewCompose retained RecyclerView item animation
while Compose did not request `animateItem` and the Android Views control explicitly disabled its
animator. That produced about 217 measured ViewCompose mutation frames versus 41/48 control frames
and diluted the real tail. The fixture now disables the unmatched animator and bumps the workload
to `performance.list@4`; the corrected ViewCompose mutation records 48 frames. Revision-3 list
results remain historical evidence and must not be compared directly with revision 4.

| Workload | ViewCompose P50/P95, ms | Compose P50/P95, ms | Android Views P50/P95, ms | Run-P50 CV, VC/C/Android | Versus Compose | Versus Android Views |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| `performance.list@4` scroll | 5.294 / 10.825 | 5.651 / 9.790 | 4.110 / 8.679 | 0.070 / 0.076 / 0.019 | `mixed`: P50 is 6.3% lower while P95 is 10.6% higher; neither direction crosses both gates. | `regressed`: P50/P95 are 28.8%/24.7% higher. |
| `performance.list@4` mutation | 4.558 / 40.332 | 7.147 / 23.978 | 5.648 / 9.583 | 0.099 / 0.096 / 0.051 | `mixed`: P50 is 36.2% lower but P95 is 68.2% higher. | `mixed`: P50 is 19.3% lower but P95 is 320.9% higher. |
| `performance.complex-layout@4` scroll | 5.798 / 13.935 | 6.023 / 11.192 | 4.636 / 10.422 | 0.065 / 0.060 / 0.013 | `regressed`: P50 is neutral while P95 is 24.5% higher. | `regressed`: P50/P95 are 25.1%/33.7% higher. |
| `performance.complex-layout@4` property update | 5.709 / 33.050 | 7.663 / 46.852 | 6.137 / 19.270 | 0.067 / 0.076 / 0.059 | `improved`: P50/P95 are 25.5%/29.5% lower. | `regressed`: P50 is neutral while P95 is 71.5% higher. |
| `performance.complex-layout@4` structure update | 5.590 / 46.009 | 7.255 / 26.844 | 5.444 / 15.051 | 0.028 / 0.111 / 0.082 | `mixed`: P50 is 22.9% lower but P95 is 71.4% higher. | `regressed`: P50 is neutral while P95 is 205.7% higher. |

Median peak heap in the same rows was respectively
`8541/7531/4904`, `10443/9374/6007`, `7052/9421/7014`, `11757/14102/7670`, and
`12925/13642/9048 KiB` for ViewCompose/Compose/Android Views. The batch therefore does not support
a universal memory winner.

The property transaction reduces P95 by 19.8% from the fresh revision-3 ViewCompose diagnostic
control (`41.187` to `33.050 ms`) and clearly beats the same-run Compose control. It does not erase
Android View traversal and property invalidation cost, and the native P95 gap remains material.
Correctness tests also cover a State read shared by an observed property and a structural
`RecomposeBoundary`: the full structural frame now refreshes the dirty observed value inside the
same Snapshot instead of committing a mixed old/new frame. Android 9 cannot expose application
trace sections from a non-debuggable APK because manifest `profileable` support begins on API 29;
therefore the six-method timing matrix is accepted from this root-controlled device, while the
API-33 R8 trace above independently closes the `VC.ObservedProperty*` phase-attribution
requirement. Neither result is used outside the dimension it can support: Xiaomi owns stable
timing, and Samsung owns phase presence.

The corrected list mutation trace localizes its remaining tail to the frame-aligned framework
transaction rather than Android traversal. Representative worst frames spent 27.7--41.3 ms in
Choreographer's `animation` phase and at most 9.5 ms in traversal; ART also JIT-compiled the large
`LazyListAdapter.submitItems` path. That path synchronously performs keyed identity analysis,
`DiffUtil`, key/sticky indexes, changed-key discovery, notification, and attached-holder refresh
for 1,000 items. The next list optimization must reduce this transaction's duplicated whole-list
work or split its compilation surface without restoring unequal animation or weakening key,
revision, Session, reset, and release semantics.

That optimization was accepted later on 2026-08-16 under the same device, clock, build, workload,
and 48-frame protocol. ViewCompose list mutation reached `4.392/26.862 ms` P50/P95 with run-P50 CV
`0.083` and median peak heap `8507 KiB`. Against the preceding ViewCompose result this is an
`improved` longitudinal result: P50 is 3.7% lower, P95 is 33.4% lower, and median peak heap is 18.5%
lower. The implementation performs one key/sticky/identity index pass, avoids constructing an
unused public update sequence inside the adapter, limits synchronous refresh checks to attached
holders, and removes duplicate lazy-item collector copies and callback objects. The adapter JIT
hotspot disappeared from the follow-up trace. The cross-engine conclusion remains `mixed`: this
P50 is 38.6% below Compose and 22.2% below Android Views, while P95 remains 12.0% and 180.3% higher
respectively. The next target is therefore the higher-level frame transaction and native traversal,
not a relaxation of logical-session correctness.

A second-stage diagnostic on 2026-08-16 separated that transaction with temporary phase timing.
Repeated mutation frames spent about `2.1--15.5 ms` in `VC.Compose`, while `VC.RenderTree` usually
spent `1.0--3.8 ms`; reset composition was only about `0.5--1.3 ms`. The difference came from the
fixture rebuilding the same 1,000-row revision-1 model inside every mutation. The harness now keeps
the most recent immutable non-zero revision snapshot, preserving one cold construction per process
while making the next seven cycles measure submission and reconciliation. The frame dispatcher and
lazy holder hot paths also use dedicated internal hosts rather than generic captured callbacks. A
renderer-lowering preflight candidate was rejected and reverted after it produced `4.859/27.755 ms`
P50/P95, because its extra tree scan did not reduce the transaction tail.

The accepted five-run fixed-clock result is `4.514/25.677/29.374 ms` P50/P95/P99, run-P50 CV
`0.100`, median peak heap `8391 KiB`, and 48 frames in every run. Relative to the immediately
preceding ViewCompose result, P50 is 2.8% higher (`+0.122 ms`, no material change), P95 is 4.4%
lower (`-1.185 ms`), P99 is 21.7% lower, and heap is 1.4% lower. The longitudinal conclusion is
`improved`: the upper tail contracted without a material median regression. Because snapshot reuse
changes shared fixture preparation for all three engines, the earlier Compose and Android Views
numbers are not a valid cross-engine control for this revision; rerun the three-engine matrix before
making a new relative claim. The next framework target is cold composition/JIT surface, not another
renderer preflight or weaker item-session semantics.

#### 2.4.3 Lazy collection and RecyclerView tail-latency hard cut

This measured slice combined keyed logical-item reuse with RecyclerView submission changes instead
of adding another renderer preflight. Its benchmark APK also included a trial caller-owned
aggregate revision that could bypass all typed-list selector evaluation. A subsequent API audit
removed that public token: it duplicated the per-item revision contract, could produce stale order,
membership, or selector output when advanced incorrectly, and was disproportionately favored by
the benchmark's repeated two-snapshot update/reset fixture. The ordinary `List` DSL evaluates
order, membership, and selectors on every parent composition pass, then reuses committed logical
items and Session bindings by equal key, `contentRevision`, environment, content type, kind, and
span. The later `LazyItemsSnapshot` opt-in instead shallow-freezes ordered element references and
gives the framework an opaque identity for a bounded evaluated-snapshot cache. It can skip selector
evaluation only after a successful commit of that identity; it does not weaken the per-item or
environment revision contract.

The Android adapter now plans same-order changes and cyclic rotations in linear time, emits the
minimum move sequence for a rotation, and reserves `DiffUtil` for other structural changes. Exact
submission-plus-item acknowledgement removes redundant queued payload binds; semantic-only changes
skip RecyclerView notifications when item animation is disabled, while a failed synchronous
Session commit receives one targeted retry. Prefetch accounting separates cold activation from
authoritative detached preparation cost and fails closed after an over-budget sample. These paths
retain key identity, logical Item Session ownership, native Holder reuse, and reset/release
boundaries.

Historical revision-4 diagnostics first established the Xiaomi MI 6 / API 28 root-controlled
protocol later reused below. Both `2695fbfb` controls were rejected (`0.192/0.157` run-P50 CV), and
the second also omitted the policy payload. The hard-cut candidate was stable at
`5.505/16.534/30.841 ms` P50/P95/P99, `8212 KiB` peak heap, and `0.075` CV, but combined the later
removed aggregate skip with retained item/adapter changes. Its longitudinal classification remains
`inconclusive`; it is only an absolute result for APK `020582a9`. A Material-host JIT experiment
also regressed the cold tail and was reverted. The revision-5 A/B below supplied the valid
same-policy ViewCompose control, and the 2026-08-17 matrix closes the cross-engine follow-up.

<div className="benchmark-evidence">

On 2026-08-17, exact `2695fbfb` was rebuilt under the same Xiaomi device, five-run `run-from-apk`,
and v3 fixed-clock policy as `9ac164af`. Revision-4 scroll P50/P90/P95/P99 was
`5.356/8.914/9.603/11.523 ms` (heap `7833 KiB`, CV `0.016`); mutation was
`4.244/15.852/22.681/24.947 ms` (`8593 KiB`, `0.079`). Revision 5 changes those percentiles by
`-0.5%/+0.6%/-0.7%/-7.1%` and `+0.1%/-31.2%/-44.0%/-39.3%`, with heap 2.3%/5.4% lower. Both runs
pass protocol and stability gates and do not reproduce a regression. Classification remains
`inconclusive` because the workload changed from `performance.list@4` to `@5`; next benchmark the
exact diagnostics-tab switch followed by an immediate fling if the symptom persists.

</div>

The 2026-08-16 revision-5 A/B used the same Xiaomi MI 6 / API 28 device, R8 benchmark target,
five-run and 48-frame protocol, `run-from-apk` compilation identity, and
`root-fixed-cpu-1401600-1804800-gpu-515000000-perf-hal-off-v3` policy. The workload contains 1,000
rows and performs eight update/reset cycles per iteration. Every arm prepares the immutable row
lists for revisions 0 and 1 before the Ready marker. The candidate B0/B1 fixture additionally
constructs and retains both strong snapshot wrappers before Ready; B0 still submits the raw lists,
so it is a conservative plain-path control for the candidate's extra wrapper residency outside the
timed mutation. The measured steady path always alternates two prebuilt inputs. A1 and A2 are
independent plain-`List` reference repetitions from `bb542f00`; B0 exercises the candidate
implementation through its plain-`List` overload; B1 runs the same candidate through
`LazyItemsSnapshot`.

| Run | Route | Frame P50/P90/P95/P99, ms | Three-frame sum P50/P95, ms | Three-frame maximum P50/P95, ms | Median peak heap, KiB | Run-P50 CV | Acceptance |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| A1 | `bb542f00`, plain `List` | 4.437 / 25.468 / 26.364 / 30.505 | 29.910 / 37.459 | 24.383 / 27.705 | 8313 | 0.127 | Accepted reference repetition. |
| A2 | `bb542f00`, plain `List` | 4.825 / 25.580 / 26.293 / 28.353 | 30.165 / 36.434 | 24.899 / 27.175 | 8667 | 0.111 | Accepted reference repetition. |
| B0 | Candidate, plain `List` | 5.022 / 25.750 / 26.428 / 29.727 | 30.963 / 38.229 | 24.955 / 28.198 | 8254 | 0.047 | Accepted plain-path control. |
| B1 | Candidate, `LazyItemsSnapshot` | 6.432 / 14.723 / 17.268 / 25.068 | 23.694 / 33.306 | 13.160 / 24.549 | 8419 | 0.096 | Accepted snapshot run. |
| B1 repeat | Candidate, `LazyItemsSnapshot` | 6.175 / 15.098 / 19.683 / 23.989 | 22.918 / 32.346 | 13.408 / 22.855 | 8872 | 0.193 | Rejected: run-P50 CV exceeds 0.15; direction only. |
| B1 third | Candidate, `LazyItemsSnapshot` | 6.133 / 14.610 / 17.638 / 26.508 | 23.500 / 34.097 | 13.386 / 23.994 | 8533 | 0.131 | Accepted snapshot replication. |

Pooling the two accepted A reference repetitions gives frame P50/P95/P99 of
`4.589/26.322/29.134 ms`. B0 is respectively 9.5% (`+0.434 ms`), 0.4%
(`+0.107 ms`), and 2.0% (`+0.593 ms`) higher. Its three-frame sum P50/P95 is
3.4%/2.7% higher and its three-frame maximum P50/P95 is 1.1%/1.9% higher, while median peak heap
is 2.8% lower. No decision metric crosses its combined absolute and normalized gate, so the plain
`List` longitudinal classification is `no material change`: the new collector and cache machinery
does not establish a material regression on the ordinary path.

Against B0, accepted snapshot runs B1 and B1-third make frame P50 28.1% (`+1.409 ms`) and 22.1%
(`+1.111 ms`) higher, but reduce frame P95 by 34.7% (`-9.160 ms`) and 33.3%
(`-8.790 ms`) and P99 by 15.7% (`-4.659 ms`) and 10.8% (`-3.219 ms`). Their three-frame
transaction-sum P50/P95 falls by 23.5%/12.9% and 24.1%/10.8%; transaction maximum P50/P95 falls by
47.3%/12.9% and 46.4%/14.9%. Excluding each iteration's first cold transaction, transaction-maximum
P95 falls from `27.237 ms` to `20.517/19.972 ms`, a 24.7%/26.7% reduction. Peak heap is only
2.0%/3.4% higher. B1-repeat points in the same tail direction, but its `0.193` CV rejects it from
all normalized decisions. The primary snapshot classification is therefore `mixed`: frame median
regresses materially, while P95/P99 and the three-frame transaction tail are materially improved.
The narrower tail-latency conclusion is `improved`; this is not a claim of universal frame-time
improvement.

The accepted 2026-08-17 three-engine matrix ran commit `3e0cc43a` and
`performance.list@5` on the same Xiaomi MI 6 / API 28 device. The target APK SHA-256 was
`88eeacc3e4add75551088a9fdab7c0514414be747909223a22ec266b858ca55d`. AndroidX Benchmark 1.4.1
uses the AOSP-only `su root` command form, which Magisk 30.6 does not execute on this device. The
test APK was therefore adapted only at that command-transport boundary: original SHA-256
`d36f6a138c949fddd334c2c1b55f65b6ba02b2d296a2a45efa79439c53701c9c`, adapted SHA-256
`cc9cce7c00de8c6f530c713257f91ecc2012473b644384cec971c3f2ef73d562`, with an equal-length
command alias forwarding to `magisk su -c`. The target APK, benchmark workload, metric capture,
and result JSON were not rewritten. The alias, installed benchmark packages, and temporary APKs
were removed after collection.

All six methods used five runs, `run-from-apk`, the exact v3 policy, locked CPU/GPU, stopped
performance HAL, zero thermal-throttle sleep, and per-method screen-off cooling at 34--37 degrees
Celsius. MIUI resets CPU policy on screen-off, so it was reapplied after wake; UiAutomation required
an interactive screen, and the failed off-screen preflight produced no sample.

| Action | Engine | Frames by run | P50/P90/P95/P99, ms | Median peak heap, KiB | Run-P50 CV |
| --- | --- | --- | ---: | ---: | ---: |
| Scroll | ViewCompose | `160/163/166/162/163` | 5.328 / 8.964 / 9.538 / 10.702 | 7650 | 0.107 |
| Scroll | Compose | `163/163/163/162/162` | 4.743 / 7.063 / 7.616 / 8.495 | 7398 | 0.091 |
| Scroll | Android Views | `112/114/112/112/113` | 4.991 / 6.425 / 7.188 / 8.826 | 4049 | 0.045 |
| Mutation | ViewCompose | `48/48/48/48/48` | 4.247 / 10.907 / 12.698 / 15.155 | 8128 | 0.082 |
| Mutation | Compose | `41/41/41/41/41` | 5.207 / 15.040 / 18.568 / 26.250 | 8597 | 0.141 |
| Mutation | Android Views | `48/48/48/48/48` | 4.287 / 6.658 / 7.849 / 9.076 | 5864 | 0.125 |

Every engine passes the `0.15` stability gate. For scroll, ViewCompose is 12.3% (`+0.585 ms`)
higher at P50 and 25.2% (`+1.922 ms`) higher at P95 than Compose; it is 6.8%
(`+0.337 ms`) higher at P50 and 32.7% (`+2.350 ms`) higher at P95 than Android Views. P99 is
26.0% and 21.3% higher respectively. Both scroll comparisons are `regressed`: the P95 increase
crosses the combined normalized and absolute gate even where the Android Views P50 increase does
not. ViewCompose scroll heap is 3.4% higher than Compose and 88.9% higher than Android Views.

For mutation, ViewCompose is 18.4% (`-0.960 ms`) lower at P50, 31.6% (`-5.870 ms`) lower at P95,
and 42.3% (`-11.095 ms`) lower at P99 than Compose, with 5.5% lower heap. That comparison is
`improved`. Against Android Views, ViewCompose P50 is effectively equal at 0.9% (`-0.040 ms`)
lower, but P95 is 61.8% (`+4.849 ms`) higher, P99 is 67.0% (`+6.079 ms`) higher, and heap is
38.6% higher. That comparison is `regressed`. The matrix-level conclusion is therefore `mixed`:
the strong-snapshot mutation path beats Compose and reaches native median cost, but native
RecyclerView still owns the mutation tail and both controls beat ViewCompose scrolling.

The action protocol is identical, but engines may coalesce or emit different numbers of measured
frames; Compose mutation consistently emitted 41 while the other two emitted 48. This matrix
therefore compares the accepted per-frame distributions and does not manufacture three-frame
cross-engine transactions. It is also post-Ready steady-state evidence for two preconstructed
snapshots under `run-from-apk`, not startup, snapshot-construction, monotonic-feed, or clean
uncompiled-ART evidence. The next actions are to profile the ViewCompose scroll P95/heap gap and the
remaining Android Views mutation-tail gap, then add cold-construction and monotonic-feed workloads.

The first memory-efficiency follow-up compared exact control `ea33297b` with candidate `06a411e7`
on Samsung SM-G991B / Android 13. Each arm used the same benchmark APK mode, five
`run-from-apk` iterations, unchanged fixtures and actions, no thermal throttling, and the
`unlocked-dvfs-preflight-v1` policy. This device was not root-controlled, so these results are
same-device diagnostics rather than a replacement for the Xiaomi fixed-clock baseline:

<div className="search-partition-detail">

| Scenario | Arm | Frames by run | P50/P90/P95/P99, ms | Median peak heap/RSS anon, KiB | Run-P50 CV |
| --- | --- | --- | ---: | ---: | ---: |
| `performance.list@5` scroll | Control | `195/193/197/194/188` | 4.356 / 6.362 / 6.996 / 8.155 | 10518 / 55900 | 0.032 |
| `performance.list@5` scroll | Candidate | `198/197/195/192/195` | 4.370 / 6.619 / 6.963 / 9.144 | 10638 / 56436 | 0.032 |
| `performance.shadow-list@3` scroll | Control | `192/192/182/196/190` | 4.673 / 8.622 / 9.210 / 14.019 | 11079 / 60504 | 0.038 |
| `performance.shadow-list@3` scroll | Candidate | `191/179/193/190/179` | 4.548 / 8.359 / 8.865 / 13.246 | 11040 / 61176 | 0.038 |

For the ordinary list, candidate P50/P90/P95/P99 changed by `+0.3%/+4.0%/-0.5%/+12.1%` and peak
heap/RSS by `+1.1%/+1.0%`. P99's `+0.989 ms` direction remains a fixed-clock follow-up, but no
timing metric crosses the combined gate; the timing conclusion is `no material change`. The process
memory direction is `inconclusive`: run-level heap values were noisy and four of five paired
candidate runs were lower even though their median ordering reversed. For the shadow list, those
timing percentiles changed by `-2.7%/-3.0%/-3.7%/-5.5%`, heap by `-0.4%`, and RSS by `+1.1%`.
That favorable timing direction also stays below the materiality gate, so the conclusion is
`no material change` rather than a claimed improvement. Both methods emitted the Runtime Image
cleanup warning; it does not invalidate these post-Ready interactions but excludes startup or ART
state conclusions.

Post-GC attribution resolves what the peak process metric cannot. A debug build of each exact arm
was cold-launched into the same ordinary-list route, driven through 12 full upward and 12 full
downward fast flings, allowed to settle, and dumped with `am dumpheap`. Indexed instances and arrays
fell from 387,380 objects and 18,276,640 shallow bytes to 381,104 objects and 18,147,122 shallow
bytes: `-6,276` objects and `-129,518` bytes. The candidate removed all 1,000
`WidgetLazyItemSessionBinding` objects (`-24,000` bytes), all 1,000 item-capturing collector lambdas
(`-16,000` bytes), 1,000 `HashMap.Node` objects (`-24,000` bytes), and 608
`LinkedHashMap.Entry` objects (`-19,456` bytes). Lazy drawing state removed 136 `Paint`, 184 `Path`,
184 `RectF`, and 320 native-allocation cleaner-wrapper objects. The 124
`UiEnvironmentValues` instances and their 3,968 shallow bytes were unchanged, rejecting additional
small-value pooling as non-material. This is an `improved` structural live-set result that matches
the implemented allocation cuts; it does not quantify native resource bytes or replace a formal
fixed-clock peak-memory run. The next action is one root-controlled ordinary-list control/candidate
pair, with P99 and peak heap as the remaining acceptance decisions.

The fixed-clock closure ran on 2026-08-20 on the same rooted Xiaomi MI 6 / Android 9 reference
device. Exact control `ea33297b` and candidate `06a411e7` were rebuilt as R8 benchmark targets;
their target APK SHA-256 values were respectively
`ecd201dd3f3843b9abac7cb42011ad2a398612b7a31053a30e2036114a61aa99` and
`f2fc39ab7add472d3627382672e5eaa7a81ce2cef77ebb704b3e064eb2ae67d5`. Both used the same
benchmark APK (`0580ce4e8a6b6f93a369fccff2acf23fcc7e0d8519cf869a421e10f2816070fd`),
`performance.list@5`, five `run-from-apk` iterations, CPU policies fixed at 1.4016/1.8048 GHz,
Adreno fixed at 515 MHz, suspended charging, stopped vendor performance services, and 35--36
degrees Celsius starts. The temporary Magisk compatibility wrapper and every device control were
restored after each arm.

| Arm | Frames by run | P50/P90/P95/P99, ms | Median peak heap, KiB | Run-P50 CV |
| --- | --- | ---: | ---: | ---: |
| Control | `162/162/164/162/161` | 5.218 / 8.517 / 9.248 / 11.004 | 7709 | 0.089 |
| Candidate | `162/161/161/164/163` | 5.342 / 8.506 / 9.304 / 10.523 | 7591 | 0.068 |

Candidate P50/P90/P95/P99 changed by `+2.37%/-0.13%/+0.60%/-4.37%`; no frame metric crosses
the combined gate, every stability value passes `0.15`, and the previously adverse unlocked P99
direction disappears. Timing is therefore `no material change`. Median peak heap falls by
`118 KiB` (`1.53%`). The peak samples remain too variable to claim a general process-memory win by
themselves, but this fixed-clock direction and magnitude agree with the independently attributed
`129,518`-byte and 6,276-object live-set reduction. The scoped memory conclusion is therefore
`improved`: the implemented allocation cuts are retained without moving work into scrolling.
Limitations are explicit: this pair does not measure RSS, native-resource bytes, startup,
monotonic feeds, or cross-engine ranking. Those are future workload questions rather than blockers
for the completed allocation plan.

</div>

The searchable conclusion is `no material change` for ordinary-list and shadow-list frame timing,
`inconclusive` for peak process memory in isolation, and `improved` for the attributed allocation
result corroborated by the fixed-clock peak-heap direction. The allocation plan's P99 and memory
acceptance decisions are complete.

The preceding A/B evidence covers only a steady alternation between two already-constructed
revision-5 snapshots, which directly favors the bounded two-generation identity cache. It does not
measure `toLazyItemsSnapshot()` construction, first evaluation, a monotonic stream of never-reused
identities, or list scrolling, and therefore cannot be extrapolated to those costs. Accept the
strong snapshot path as an explicit tail-latency tradeoff while retaining the plain `List` path for
general feeds; the three-engine matrix above is the separate cross-engine conclusion.

#### 2.4.4 Navigation and design-system diagnostics

Navigation revision 6 also produced stable fixed-clock diagnostics:

| Navigation action | P50/P95/P99, ms | Run-P50 CV | Conclusion |
| --- | ---: | ---: | --- |
| Push, no precompilation | 5.552 / 12.598 / 41.929 | 0.039 | Accepted absolute baseline. |
| Push, requested profile-guided compilation | 5.601 / 11.173 / 42.148 | 0.070 | `no material change`; P95 improves 11.3%, below the combined 15% gate, while P99 is unchanged. |
| System Back, no precompilation | 5.558 / 15.618 / 40.089 | 0.039 | Accepted absolute baseline. |
| System Back, requested profile-guided compilation | 5.409 / 13.864 / 41.685 | 0.064 | `no material change`; P95 improves 11.2%, below the combined gate, while P99 remains about 42 ms. |

Android 9 reports both requested compilation variants as `run-from-apk`, so the profile-guided rows
are diagnostics rather than proof of a distinct ART compilation state. They nevertheless reject
ordinary warm-up as a sufficient explanation for the approximately 42 ms navigation P99. Custom
navigation `TraceSectionMetric` values are intentionally omitted below API 29 instead of reporting
misleading zeros.

The design-system revision-3 matrix establishes the first root-controlled absolute baseline; it
does not rank unlike visual systems, so directional comparison remains `inconclusive` until a
matching prior or future baseline exists:

| Scenario | Cut Contrast | Rounded Reference | Cupertino Pressure | Run-P50 CV range | Conclusion |
| --- | ---: | ---: | ---: | ---: | --- |
| Initial display, median ms | 531.254 | 558.753 | 561.880 | 0.039--0.088 | Stable absolute baseline; directional result `inconclusive`. |
| Patch P50/P95/P99, ms | 7.934 / 23.008 / 25.716 | 7.959 / 23.855 / 26.222 | 7.842 / 15.907 / 24.841 | 0.056--0.079 | Stable absolute baseline; directional result `inconclusive`. |
| Scroll P50/P95/P99, ms | 3.798 / 7.582 / 9.000 | 3.731 / 8.071 / 9.124 | 3.730 / 7.572 / 8.905 | 0.009--0.034 | Stable and within one 60-Hz frame at P99; directional result `inconclusive`. |
| Active animation P50/P95/P99, ms | 7.661 / 17.285 / 20.884 | 7.617 / 15.146 / 21.664 | 8.045 / 15.736 / 18.455 | 0.077--0.110 | Stable absolute baseline; the individual P95/P99 tails remain monitored. |
| Cut Contrast overlay lifecycle P50/P95/P99, ms | 4.535 / 27.499 / 39.833 | — | — | 0.055 | Stable baseline; the overlay P95/P99 is the next design-bundle tail target. |

#### 2.4.5 ConstraintLayout first-release safety

The 2026-08-19 first-release matrix used the rooted Xiaomi MI 6 / Android 9 device, the R8 and
resource-shrunk benchmark target, five iterations per method, and the actual `run-from-apk`
compilation identity reported by AndroidX Benchmark. CPU policies 0/4 were fixed at
1.4016/1.8048 GHz, the Adreno GPU at 515 MHz, all CPUs were online, charging was suspended, vendor
performance services were stopped, and each method started at or below 37 degrees Celsius. The
pre-hard-cut ViewCompose APK SHA-256 was
`2b32ca7539be121615fb3e7b61953101be7b9a2e4ac55215690d88a480b25161`; the final candidate was
`a7d681b90941a8d318108d709b3a7b77147b614180a8d2124840416d07148fac`. A temporary root-shell
compatibility wrapper adapted AndroidX's AOSP `su root` form to Magisk only while instrumentation
ran; the target, workload, metrics, and result JSON were unchanged, and the original Magisk entry
was restored after each completed method batch.

Each cell below is frame CPU P50/P95 in milliseconds. Delta is candidate versus the pre-hard-cut
ViewCompose baseline. CV is baseline/candidate iteration-P50 coefficient of variation. One adjacent
repeat replaced a run only when the original exceeded 0.15; scalar-100 was also repeated after its
cross-APK median changed direction. The replaced raw runs remain retained as evidence. A scenario
is directional only when baseline, candidate, and direct-native controls are stable.

| Action | Baseline ViewCompose | Candidate ViewCompose | Direct Android Views | Candidate delta | CV | Conclusion |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `stable-10` | `5.270/11.178` | `4.778/12.009` | `3.643/5.279` | `-9.3%/+7.4%` | `0.094/0.131` | `no material change` |
| `stable-50` | `6.705/20.751` | `5.105/19.603` | `3.774/5.788` | `-23.9%/-5.5%` | `0.074/0.130` | `improved` median with no tail regression |
| `stable-100` | `7.632/25.856` | `5.642/25.353` | `3.873/7.141` | `-26.1%/-1.9%` | `0.089/0.146` | `improved` median with no tail regression |
| `scalar-10` | `5.495/14.622` | `5.093/13.574` | `4.190/6.327` | `-7.3%/-7.2%` | `0.143/0.185` | `inconclusive` |
| `scalar-50` | `5.675/23.466` | `5.796/23.724` | `4.400/6.871` | `+2.1%/+1.1%` | `0.128/0.091` | `no material change` |
| `scalar-100` | `6.411/32.187` | `6.051/35.986` | `5.009/8.973` | `-5.6%/+11.8%` | `0.141/0.202` | `inconclusive` |
| `helper-10` | `5.158/10.878` | `5.033/11.617` | `4.014/6.327` | `-2.4%/+6.8%` | `0.077/0.074` | `no material change` |
| `helper-50` | `5.221/12.193` | `5.280/11.730` | `3.968/6.657` | `+1.1%/-3.8%` | `0.105/0.123` | `no material change` |
| `helper-100` | `5.868/11.977` | `6.169/13.773` | `4.598/8.284` | `+5.1%/+15.0%` | `0.129/0.068` | `no material change`; exact change remains below the 15% gate |
| `topology-10` | `5.130/15.123` | `5.251/14.080` | `4.099/6.471` | `+2.4%/-6.9%` | `0.095/0.217` | `inconclusive` |
| `topology-50` | `6.304/23.003` | `6.162/23.609` | `4.780/6.850` | `-2.3%/+2.6%` | `0.147/0.148` | `no material change` |
| `topology-100` | `6.296/30.919` | `9.222/32.056` | `4.923/10.525` | `+46.5%/+3.7%` | `0.231/0.043` | `inconclusive`; baseline unstable |

The original candidate exposed a stable topology-50 P50 regression of 12.3% (`+0.772 ms`). The
renderer then removed an O(n-squared) child-index lookup from rollback snapshot capture and avoided
an identical second snapshot when no accepted Group, Layer, or Placeholder content overlay had
been released. With that causally scoped fix, topology-50 changed from `7.076/22.001 ms` to
`6.162/23.609 ms`; versus the pre-hard-cut baseline it is 2.3% lower at P50 and 2.6% higher at P95,
so the accepted result is `no material change`. The corrected report gate prefers Compose but uses
Android Views for these two-engine scenarios and marks unstable rows `INCONCLUSIVE`; `--enforce`
passes with zero stable timing or memory regressions.

Candidate median peak heap changed from -14.4% to +5.3% across the twelve actions; no row crossed
the combined 15% and 2048 KiB memory gate. The matrix-level first-release conclusion is
`no material change` for performance safety: eight actions are stable on both ViewCompose arms,
four are `inconclusive`, and no stable action regresses. This is not performance leadership.
Direct Android Views remains materially faster, especially at P95, and owns the post-release
optimization target.

Limitations are one API 28 device, `run-from-apk` JIT/code-placement sensitivity, four unresolved
CV rows, peak rather than post-GC retained memory, and no P99 for this workload. The next action is
the source-frozen first-release window. After Central publication and tagging, the ConstraintLayout
expansion plan may investigate classified scalar/topology fast paths with a stable multi-device
protocol; the first-release train does not claim those wins.

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
reasons are readable. Node highlighting, cross-session correlation, per-node timing, and their
inactive-path performance proof have moved to the active
[diagnostics plan](../project/plans/diagnostics-correlation-inspection-observability.md).

### Phase 4: Containers and layout

Status: list and complex-layout Compose controls, Android Views source controls, memory metrics,
engine-neutral reports, and normalized ViewCompose/Compose gates are established. The corrected
revision-4 root-controlled fifteen-method matrix accepts all five actions across all three engines.
Continue reducing list-mutation, structural-update, and container-layout tails while keeping the
three-engine workload contract aligned.

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
6. [Archived Android Views performance control plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/android-views-performance-control.md)
