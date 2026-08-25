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

`PagingPerformanceBenchmark` is the release authority for the optional AndroidX Paging
integration:

1. It drives the ViewCompose-only `performance.paging@1` route because the measured contract is the
   official `PagingDataPresenter` integration, not an engine-ranking workload.
2. The deterministic local source exposes 1,000,000 positions with `pageSize = 32`,
   `prefetchDistance = 2`, `maxSize = 96`, placeholders, jumps, and query-separated stable keys.
3. `pagingAppendDrop`, `pagingQueryReplacement`, and `pagingScroll` respectively exercise eight
   append/drop advances, eight latest-generation replacements around target 256, and eight
   down/up gestures while checking the bounded loaded window.
4. Each Release method uses five `CompilationMode.None` iterations plus frame timing and maximum
   process-memory metrics. A fixed settling window and readiness checks remain outside measurement.
5. `tools/performance/summarize_paging_macrobenchmark.py` accepts only the exact three-method,
   five-run, matching-context set; it reports P50/P90/P95/P99, median peak heap, optional RSS, and
   run-P50 CV, and rejects CV above `0.15` when `--enforce` is used.

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
2. `diagnosticsThemeDebugToolingIdleLongFlingRevision1` reuses that exact gesture and anchor
   contract with `CompilationMode.None` so a Debug APK containing optional Preview tooling can be
   compared without baseline-profile installation.
3. `collectionsScrollRevision3` captures the direct scenario LazyColumn bounds during setup, then executes
   eight fixed swipes in each direction without performing Accessibility queries inside the measured
   block. Each swipe has a 500 ms physical settle window because benchmark setup disables
   UiAutomator's implicit idle timeout; omitting that window overlaps inertial scrolls and causes
   non-workload `Buffer Stuffing` in FrameTimeline.
4. `collectionsStressMutationRevision3` executes eight complete rotate/insert/reset cycles and
   asserts that every reset restores the original logical order.
5. All four wait through the same 5-second unmeasured launch-settling window. Formal raw results
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

A same-device root audit after reconnection found no additional exposed control that can be added
without changing the rendering pipeline: the panel advertises one fixed 60 Hz mode; framebuffer
`idle_time`, dynamic partial update, command-mode auto-refresh, and dynamic FPS are already `0`;
and SurfaceFlinger already has `debug.sf.disable_backpressure=1` and
`debug.sf.latch_unsignaled=1`. Forcing MDP/HWC composition, changing SurfaceFlinger phase offsets,
or replacing the HWUI renderer would change the system path being measured rather than control an
existing variable, so those experiments are excluded from baseline acceptance. This device audit
therefore closes the safe-control search, not the scroll gate.

The scoped conclusion is `mixed`: mutation now has a stable fixed-clock absolute baseline, with
directional comparison `inconclusive` because revision 2 is a retired fixture; scroll remains
`inconclusive`, so the post-release Phase 1 gate is not complete. Limitations are one API-28 device,
`run-from-apk` JIT/code placement, and an unresolved system display-buffer plateau. The next action
is to preserve revision 3 and the `0.15` gate, then recapture scroll on another root-controllable
reference device whose clocks and display pipeline can be held stable. Do not change swipe count,
pacing, or fixture merely to obtain a passing batch.

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

### 2.4 Current accepted evidence

This section keeps the durable decisions and the absolute values needed to apply them. Detailed
device preparation, APK identities, rejected runs, traces, and candidate-by-candidate investigations
remain historical evidence in the
[Android Views control](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/android-views-performance-control.md),
[observed-property](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/observed-property-transactions.md),
[lazy-collection](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/lazy-collection-three-layer-hard-cut.md),
[ConstraintLayout](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/constraintlayout-parity-performance-expansion.md),
and [animation](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/animation-compose-capability-expansion.md)
records. Those archives explain history; the classifications below are the current specification.

Unless a row says otherwise, the accepted physical evidence used an R8/resource-shrunk,
non-debuggable target on the rooted Xiaomi MI 6 / Android 9 reference device, five
`run-from-apk` iterations, fixed CPU policies at 1.4016/1.8048 GHz, GPU at 515 MHz, fixed exposed
interconnect votes, suspended charging, stopped vendor performance services, and zero reported
thermal-throttle sleep. All modified device state was restored after each batch.

#### 2.4.1 Renderer, collection, and memory conclusions

##### 2.4.1 Complex-layout update tail-latency investigation {/* #241-complex-layout-update-tail-latency-investigation */}

##### 2.4.2 Root-controlled revision-4 acceptance and remaining tails {/* #242-root-controlled-revision-4-acceptance-and-remaining-tails */}

##### 2.4.3 Lazy collection and RecyclerView tail-latency hard cut {/* #243-lazy-collection-and-recyclerview-tail-latency-hard-cut */}

Frame values below are P50/P95 milliseconds; heap is median peak KiB.

| Evidence | Absolute result | Normalized result | Classification |
| --- | --- | --- | --- |
| `performance.list@5` scroll | ViewCompose `5.328/9.538`, heap `7650`; Compose `4.743/7.616`, `7398`; Android Views `4.991/7.188`, `4049` | Versus Compose `+12.3%/+25.2%`; versus Android Views `+6.8%/+32.7%` | `regressed` against both controls because P95 crosses both gates. |
| `performance.list@5` mutation | ViewCompose `4.247/12.698`, heap `8128`; Compose `5.207/18.568`, `8597`; Android Views `4.287/7.849`, `5864` | Versus Compose `-18.4%/-31.6%` and `-5.5%` heap; versus Android Views `-0.9%/+61.8%` and `+38.6%` heap | `improved` versus Compose; `regressed` versus Android Views because the native tail remains materially lower. |
| `performance.complex-layout@4` property update | ViewCompose `5.709/33.050`; Compose `7.663/46.852`; Android Views `6.137/19.270` | Versus Compose `-25.5%/-29.5%`; versus Android Views `-7.0%/+71.5%` | `improved` versus Compose; `regressed` versus Android Views at P95. |
| `performance.complex-layout@4` structure update | ViewCompose `5.590/46.009`; Compose `7.255/26.844`; Android Views `5.444/15.051` | Versus Compose `-22.9%/+71.4%`; versus Android Views `+2.7%/+205.7%` | `mixed` versus Compose and `regressed` versus Android Views. |
| Strong `LazyItemsSnapshot` tradeoff | Plain candidate `5.022/26.428`, heap `8254`; accepted snapshot runs `6.432/17.268`, `8419` and `6.133/17.638`, `8533` | Snapshot P50 `+28.1%/+22.1%` and P95 `-34.7%/-33.3%` versus plain; peak heap `+2.0%/+3.4%` | `mixed` overall and `improved` for the explicitly selected transaction-tail objective. |
| Lazy allocation hard cut | Control `5.218/9.248/11.004` P50/P95/P99, heap `7709`; candidate `5.342/9.304/10.523`, `7591` | `+2.37%/+0.60%/-4.37%`; heap `-118 KiB` (`-1.53%`). Post-GC attribution: `-129,518` shallow bytes and `-6,276` objects | Timing `no material change`; allocation result `improved`. |

The list matrix is post-Ready steady-state evidence for preconstructed snapshots, not startup,
snapshot-construction, monotonic-feed, total-energy, or clean uncompiled-ART evidence. Different
engines may emit different frame counts, so cross-engine conclusions use accepted per-frame
distributions rather than manufactured transactions. The next collection targets are ViewCompose
scroll P95/heap, the Android Views mutation-tail gap, cold construction, and monotonic feeds.

Observed-property transactions materially reduce complete-tree work and beat the same-run Compose
property control, but native property invalidation and traversal still own the lower tail. The
accepted API-33 trace proves `VC.ObservedPropertyRead` and `VC.ObservedPropertyRender` are entered
without returning to root `VC.Compose`/`VC.RenderTree`; unlocked DVFS limits that trace to phase
presence, while the fixed-clock Xiaomi matrix owns timing.

#### 2.4.2 Navigation and design-system baselines

##### 2.4.4 Navigation and design-system diagnostics {/* #244-navigation-and-design-system-diagnostics */}

| Navigation action | P50/P95/P99, ms | Run-P50 CV | Conclusion |
| --- | ---: | ---: | --- |
| Push, no precompilation | `5.552/12.598/41.929` | `0.039` | Accepted absolute baseline. |
| Push, requested profile-guided compilation | `5.601/11.173/42.148` | `0.070` | `no material change`; P95 is 11.3% lower and P99 is unchanged. |
| System Back, no precompilation | `5.558/15.618/40.089` | `0.039` | Accepted absolute baseline. |
| System Back, requested profile-guided compilation | `5.409/13.864/41.685` | `0.064` | `no material change`; P95 is 11.2% lower and P99 remains about 42 ms. |

Android 9 reports both requested compilation variants as `run-from-apk`, so they do not prove a
distinct ART compilation state. They do reject ordinary warm-up as a sufficient explanation for
the navigation P99. Application trace sections are unavailable in a non-debuggable API-28 target.

The design-system revision-3 matrix is an absolute baseline, not a ranking of unlike visual systems:

| Action | Cut Contrast | Rounded Reference | Cupertino Pressure | Conclusion |
| --- | ---: | ---: | ---: | --- |
| Initial display median, ms | `531.254` | `558.753` | `561.880` | Stable; normalized direction `inconclusive`. |
| Patch P50/P95/P99, ms | `7.934/23.008/25.716` | `7.959/23.855/26.222` | `7.842/15.907/24.841` | Stable absolute baseline. |
| Scroll P50/P95/P99, ms | `3.798/7.582/9.000` | `3.731/8.071/9.124` | `3.730/7.572/8.905` | Stable; every P99 is below one 60 Hz frame. |
| Active animation P50/P95/P99, ms | `7.661/17.285/20.884` | `7.617/15.146/21.664` | `8.045/15.736/18.455` | Stable; individual tails remain monitored. |
| Cut Contrast overlay P50/P95/P99, ms | `4.535/27.499/39.833` | — | — | Stable; overlay tail is the next design-bundle target. |

The run-P50 CV range is `0.009..0.110`. A matching prior or future baseline is required before a
directional design-system claim.

#### 2.4.3 ConstraintLayout release-safety conclusion

##### 2.4.5 ConstraintLayout first-release safety {/* #245-constraintlayout-first-release-safety */}

##### 2.4.6 ConstraintLayout Phase 1 reconciliation preflight {/* #246-constraintlayout-phase-1-reconciliation-preflight */}

##### 2.4.7 ConstraintLayout Phase 4 controlled matrix {/* #247-constraintlayout-phase-4-controlled-matrix */}

The revision-6 matrix used 16 direct accessibility update/reset cycles per iteration. Seven
Released/Candidate pairs were stable and five remained `inconclusive` because at least one arm
exceeded the `0.15` run-P50 CV ceiling.

| Action | Released P50/P95/P99, ms | Candidate P50/P95/P99, ms | Direct AndroidX P50/P95, ms | Final CV Released/Candidate | Conclusion |
| --- | ---: | ---: | ---: | ---: | --- |
| `stable-10` | `9.116/10.918/13.854` | `8.803/11.462/14.612` | `3.438/4.676` | `0.143/0.120` | `no material change` |
| `stable-50` | `10.614/19.107/23.141` | `11.237/17.137/19.951` | `4.402/5.971` | `0.117/0.180` | `inconclusive` |
| `stable-100` | `12.674/24.434/26.283` | `12.585/24.398/28.491` | `5.635/7.486` | `0.111/0.121` | `no material change` |
| `scalar-10` | `10.478/13.931/14.977` | `9.776/13.316/14.977` | `4.828/6.421` | `0.179/0.171` | `inconclusive` |
| `scalar-50` | `11.588/21.484/24.060` | `11.553/22.947/25.301` | `7.392/9.032` | `0.229/0.205` | `inconclusive` |
| `scalar-100` | `15.597/36.150/41.827` | `16.100/34.624/38.874` | `11.538/14.207` | `0.021/0.125` | `no material change` |
| `helper-10` | `7.955/10.926/13.923` | `8.320/11.380/13.513` | `4.558/6.126` | `0.128/0.124` | `no material change` |
| `helper-50` | `7.678/12.484/14.134` | `7.346/12.243/14.959` | `6.301/8.201` | `0.227/0.140` | `inconclusive` |
| `helper-100` | `8.303/15.280/19.498` | `7.826/14.579/16.399` | `9.373/10.830` | `0.109/0.082` | `no material change` |
| `topology-10` | `9.774/12.489/14.589` | `10.390/14.101/19.010` | `4.811/6.366` | `0.124/0.137` | `no material change` |
| `topology-50` | `13.570/22.272/27.262` | `12.367/21.920/25.432` | `7.312/8.683` | `0.201/0.140` | `inconclusive` |
| `topology-100` | `15.719/32.688/34.876` | `15.390/34.778/38.771` | `11.409/12.850` | `0.110/0.098` | `no material change` |

Across stable rows, Direct-normalized Candidate P50 spans `-5.7%..+8.4%` and P95
`-4.0%..+14.3%`. Candidate peak heap spans `-3.8%..+10.5%`; no row crosses the combined 15% and
2,048 KiB gate. Direct AndroidX is faster at P95 in all twelve actions and at P50 in eleven. The
release-safety result is `no material change`: structural fast paths retain exact zero-work and
bounded-write evidence, but there is no whole-frame performance-leadership claim. Further
whole-frame work requires a newly attributed plan rather than repeated sampling.

#### 2.4.4 Animation and shared-motion conclusions

##### 2.4.8 Animation revision-1 pre-physics baseline {/* #248-animation-revision-1-pre-physics-baseline */}

##### 2.4.9 Animation revision-1 Phase 1 physical candidate {/* #249-animation-revision-1-phase-1-physical-candidate */}

##### 2.4.10 Animation revision-2 AnimatedContent comparison {/* #2410-animation-revision-2-animatedcontent-comparison */}

##### 2.4.11 Animation revision-3 rich-visibility release-safety comparison {/* #2411-animation-revision-3-rich-visibility-release-safety-comparison */}

##### 2.4.12 Animation revision-2 seekable-transition baseline {/* #2412-animation-revision-2-seekable-transition-baseline */}

##### 2.4.13 Animation revision-1 real-bounds comparison {/* #2413-animation-revision-1-real-bounds-comparison */}

##### 2.4.14 Navigation revision-1 shared-content comparison {/* #2414-navigation-revision-1-shared-content-comparison */}

The physical-animation hard cut retained the revision-1 actions and fixed-clock policy:

| Workload | Duration baseline P50/P95, ms; heap KiB | Physical candidate P50/P95, ms; heap KiB | P50/P95/heap change | Conclusion |
| --- | --- | --- | --- | --- |
| `animation.specs@1` | `8.854/13.067; 8113` | `6.114/8.303; 8123` | `-30.9%/-36.5%/+0.1%` | Frame CPU `improved`. |
| `animation.content@1` | `7.102/10.454; 7341` | `5.291/8.444; 7776` | `-25.5%/-19.2%/+5.9%` | Frame CPU `improved`. |
| `animation.content-size@1` | `4.850/7.258; 6514` | `2.835/6.727; 6383` | `-41.5%/-7.3%/-2.0%` | P50 `improved`; no P95 regression. |
| `animation.transition@1` | `8.231/12.388; 8283` | `6.322/8.408; 8387` | `-23.2%/-32.1%/+1.3%` | Frame CPU `improved`. |

Every run-P50 CV is at most `0.012` and heap is `no material change`. Physical settling changes
frame counts, so this is per-frame CPU evidence, not total-duration or energy evidence.

| Capability | Control or baseline | Candidate | Normalized result | Classification |
| --- | --- | --- | --- | --- |
| `animation.content@2` AnimatedContent | Crossfade `5.680/8.678/10.785` P50/P95/P99, heap `8022` | `5.589/9.329/10.996`, heap `8334` | `-1.6%/+7.5%/+2.0%`; heap `+3.9%` | Frame and heap `no material change`. |
| Rich visibility revision 3 | Pre-Phase-3 `8.138/10.760/12.343`, heap `7846` | `8.334/11.115/15.723`, heap `8149` | `+2.4%/+3.3%/+27.4%`; heap `+3.9%` | P50/P95/heap `no material change`; P99 is a watch item below one 60 Hz frame. |
| `animation.transition@2` seeking | None | `7.775/10.493/11.718`, heap `8474`, CV `0.011` | No compatible earlier workload | Stable absolute baseline; normalized direction `inconclusive`. |
| `animation.bounds@1` | Snap `8.727/25.762/28.556`, heap `6868` | Bounds `5.124/6.438/18.503`, heap `6714` | `-41.3%/-75.0%/-35.2%`; heap `-2.2%` | Active per-frame CPU `improved`; heap `no material change`. |
| `navigation.shared-motion@1` | Ordinary `3.989/8.487/30.020`, heap `6651` | Two shared pairs `4.073/8.096/36.099`, heap `6971` | `+2.1%/-4.6%/+20.3%`; heap `+4.8%` | P50/P95/heap `no material change`; P99 remains a navigation-tail watch item. |

The bounds arms intentionally emit 16 versus 464 frames; the comparison says nothing about total
CPU work or energy. Rich visibility also increases covered choreography and frame count. Shared
motion covers committed Push snapshot preparation and release, not a separate predictive-Back
frame benchmark. These rows cover one OEM/API-28 device, peak rather than post-GC retained memory,
no per-object allocation events, and no direct energy measurement. Deterministic tests own
retargeting, one-writer, bounded-retention, rollback, cleanup, and lifecycle correctness.

#### 2.4.5 Paging integration release baseline

The first accepted Paging Release baseline was collected on 2026-08-25 from a rooted Xiaomi MI 6 /
Android 9 at 60 Hz. Little and big CPU policies were fixed at `1,401,600` and `1,804,800 kHz`, GPU
at `515 MHz`, and `cpubw`/`gpubw` at `13,763`; charging was suspended and overriding vendor
performance services were stopped. The three methods used the same R8 target and benchmark APKs,
`CompilationMode.None` reported as `run-from-apk`, and five measured iterations. Start temperature
was `33/34/35°C` for append/query/scroll and the restored device ended at `36°C`. Independent
post-run reads confirmed default CPU/GPU/bandwidth governors and bounds, charging, input, and
performance-service state were restored.

| Action | P50/P90/P95/P99, ms | Median peak heap, KiB | Median peak RSS, KiB | Run-P50 CV | Conclusion |
| --- | ---: | ---: | ---: | ---: | --- |
| Append/drop | `4.281/29.189/33.973/43.592` | `117,797` | n/a | `0.077` | Stable absolute baseline. |
| Query replacement | `4.215/13.810/40.809/48.345` | `128,433` | n/a | `0.021` | Stable absolute baseline. |
| Scroll | `2.581/3.699/4.066/6.511` | `119,087` | n/a | `0.006` | Stable absolute baseline. |

All rows pass the frozen `0.15` stability ceiling. Because this is the first compatible workload,
the normalized direction is **inconclusive**: these numbers establish an absolute release baseline
and improve bounded-window, generation-replacement, and device confidence, but do not prove a
performance improvement or an engine advantage.

Android 9's `MemoryUsageMetric` emitted peak process heap but no RSS. The values are process peaks,
not deltas or post-GC retained memory; the batch covers one OEM, immediate local pages, no database,
network, disk, calibrated energy, startup, or total-duration measurement. Accessibility polling and
action settling are part of the frozen interaction contract. A future directional claim requires
the same route revision, device/system, fixed-clock policy, iteration and APK context:

```bash
python3 tools/performance/summarize_paging_macrobenchmark.py \
  /path/to/paging-results \
  --output /path/to/paging-baseline.md \
  --json-output /path/to/paging-baseline.json \
  --enforce
```

#### 2.4.6 Current decision boundary

The current evidence supports targeted claims only:

1. ViewCompose mutation/property work is competitive with or faster than Compose in the accepted
   rows, while Android Views still owns important scrolling, traversal, and mutation tails.
2. Strong lazy snapshots are an explicit median-for-tail tradeoff; the ordinary `List` path remains
   the default for general feeds.
3. ConstraintLayout structural fast paths are retained for deterministic zero-work bounds, not a
   whole-frame leadership claim.
4. Animation and shared-motion slices pass their scoped release-safety gates; changed frame counts
   prevent total-work or energy claims.
5. Paging owns a stable first absolute Release baseline; no compatible prior exists for a
   normalized performance claim.
6. No matrix establishes a universal frame-time or memory winner.

Next work must start from the named remaining gap, preserve the same workload identity and controls,
and record new absolute, normalized, stability, limitation, and next-action evidence. A result from
another device or workload revision may establish an absolute baseline but cannot silently replace
a longitudinal control.

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

### 2.5.1 Animation timeline tooling comparison

The Phase 7 acceptance run on 2026-08-23 used a rooted Xiaomi MI 6 / Android 9 at 60 Hz with the
little and big CPU policies fixed at `1,401,600` and `1,804,800 kHz`, GPU fixed at `515 MHz`, and
Qualcomm CPU minimum-frequency votes cleared. Charging was suspended and the vendor performance
services remained stopped for the batch. In-run reads confirmed that all three clocks stayed
fixed. Battery temperature moved from `36` to `37°C`; AndroidX reported zero thermal-throttle
sleep. Both arms used the same debuggable target APK
`56b94faf26f5dc0f94b976343e3d3a1c868953027cf696d8c704a8122a605fad` and benchmark APK
`1ccd78d371ee5ed5890714511fe79c9464cc7be4cf9baf5f03cdb8669506f687`, with
`CompilationMode.None` reported as `run-from-apk`.

Each arm ran five measured iterations of `animation.transition`, four complete forward/reverse
round trips per iteration, and exactly 200 frames per run. The inactive arm deleted the report
before setup and produced zero report writes. The requested arm performed 40 measured 500 ms
captures; AndroidX's unmeasured validation workload added eight more responses, and every response
matched its nonce, selected identity, success status, and `1..64` sample bound.

| Arm | P50/P90/P95/P99, ms | Median peak heap (run range), KiB | Run-P50 CV |
| --- | --- | --- | --- |
| Inactive | `12.236 / 14.113 / 15.311 / 18.265` | `9,493` (`9,462--9,537`) | `0.039` |
| Requested capture | `12.398 / 14.482 / 15.464 / 20.422` | `9,823` (`9,411--10,951`) | `0.036` |

Requested capture changes P50 by `+0.162 ms` (`+1.32%`), P95 by `+0.153 ms` (`+1.00%`), and
median peak heap by `+330 KiB` (`+3.48%`). None crosses both parts of its frozen gate, so the scoped
classification is **no material change**. P99 is `+2.157 ms` (`+11.81%`) and remains an explicit
debug-tooling tail watch item outside the frozen P50/P95 decision gate.

Pre-acceptance diagnostics exposed two allocation defects: multiple channel commits at one logical
frame were retained as partial samples, and every JSON boundary check re-encoded a growing prefix.
The accepted implementation coalesces equal segment-version/play-time commits and encodes directly
into one bounded builder with incremental UTF-8 accounting. This reduced the accepted requested
heap delta to `330 KiB` without reducing the frozen 500 ms, 64-sample, 32-channel, or 256 KiB
limits. Limits are one OEM/API-28 device, a debuggable/JIT target, peak rather than post-GC retained
memory, no per-object allocation trace, and no direct energy measurement. The next action is full
repository/device acceptance, not additional sampling for a more favorable P99.

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

Status: complete. Render tree, patches, CompositionLocal, recomposition reasons, cross-session
correlation, bounded failure aggregation, node highlighting, and finite per-node timing are
readable. A timing request is diagnostic sampling with measured clock overhead, not a replacement
for Macrobenchmark.

The 2026-08-24 closeout compared Phase 4 commit `da67ad78` with the Phase 6 candidate on the same
rooted Xiaomi MI 6 / Android 9. Both Debug APKs used the same five-iteration, sixteen-fling
`diagnostics.theme` workload with CPU fixed at 1.4016/1.8048 GHz, GPU at 515 MHz, and `cpubw` and
`gpubw` at 13763. Frame CPU P50 changed from 2.684531 to 2.769011 ms (+0.084480 ms, +3.15%) and P95
from 5.012443 to 5.200990 ms (+0.188547 ms, +3.76%); run-P50 CV remained 0.0155/0.0153. Neither
metric crossed both its relative and absolute failure thresholds, so the idle conclusion is
**no material change**. Both runs performed exactly zero v6/v7 tooling report writes.

Twenty separately requested protocol-v7 source refreshes returned a fixed 32,633-byte, two-Session
response with host broadcast-through-matching-report P50/P95/max of 161.304/175.494/175.936 ms at
34.0 °C start and end. This is bounded below the two-second request budget and is not amortized into
idle results. The comparison deliberately suppresses AndroidX's `DEBUGGABLE` warning because it
measures optional Debug tooling rather than Release performance. Host latency includes adb and
polling; one Android 9 phone and fixed clocks do not establish an OEM, calibrated-power, or Release
matrix. The completed execution record is retained in the
[archived diagnostics plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/diagnostics-correlation-inspection-observability.md).

Reproduce the explicit-request characterization against a foreground Debug Demo with:

```bash
python3 tools/performance/measure_device_diagnostics_request.py \
  --serial "$ANDROID_SERIAL" \
  --operation source \
  --warmups 5 \
  --iterations 20 \
  --clock-policy <recorded-policy> \
  --output build/diagnostics-request.json
./gradlew testDeviceDiagnosticsRequestMeasurementTool
```

The tool validates protocol, nonce, operation, and package identity before accepting a response and
records raw latency samples rather than only aggregates.

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
