# Lazy Collection Memory Efficiency Plan

## Status

Completed and archived on 2026-08-20. Phase 1's shared strategy/payload hard cut, Phase 2's compact
adapter metadata, Phase 3's lazy drawing resources, post-GC attribution, repository validation,
and the final fixed-clock P99/peak-heap decision are accepted. Optional Phase 4 remains rejected as
non-material.

Last verified: 2026-08-20.

Next action: none for this plan. Cross-engine scroll gaps, RSS/native-resource accounting, startup,
and monotonic-feed workloads remain separate future performance questions.

## Maven release changesets

- `release/changes/20260817-lazy-collection-memory-efficiency.json`

## Release intent rationale

The searchable summary is that typed lazy declarations now share one strategy, adapter key and
view-type metadata are compact and bounded, common shape resources are lazy, post-GC attribution
improved, and fixed-clock P99/peak-heap acceptance remains. Public behavior stays searchable in
the owning architecture, guide, migration, module, and performance documents; the phase-by-phase
execution record below remains directly linkable without duplicating that detail in full-text
indexes.

<div className="search-partition-detail">

## Objective

Reduce retained memory and object count for large lazy collections without regressing initial
render, steady scrolling, keyed mutation, preparation, activation, or physical-tree reuse. Keep the
existing logical-item contract: key-owned State and effects remain isolated from RecyclerView
holders, equal key and revisions skip rendering, changed revisions update only that item, and
cross-key native reuse cannot transfer logical identity.

The accepted Xiaomi MI 6 / Android 9 `performance.list@5` matrix is the timing and memory baseline.
ViewCompose scroll retained `7650 KiB` peak heap versus Compose `7398 KiB` and Android Views
`4049 KiB`, while its frame P50/P95 was `5.328/9.538 ms` versus `4.743/7.616 ms` and
`4.991/7.188 ms`. The memory gap is small against Compose but large against direct Views, and the
scroll timing already trails both controls. An optimization is not accepted when it lowers memory
by moving construction, hashing, closure creation, or session setup into scrolling.

A same-build Samsung SM-G991B heap attribution after complete list flings found approximately
`115 KiB` more reachable Java self-size, `42 KiB` more tracked native size, and 4,832 more reachable
objects than Compose. The dominant avoidable sources were 1,000 `LazyListItem` values, 1,000
`WidgetLazyItemSessionBinding` objects, 1,000 item-capturing closures, overlapping key maps with
boxed values, and eager shape drawing resources. This attribution is diagnostic evidence, not a
replacement accepted benchmark baseline.

## Architectural contract

The searchable contract is that typed declarations share one strategy, logical keys retain State
and effect identity independently of RecyclerView holders, adapter metadata remains bounded, and
common shape rendering avoids unused native resources without shifting work into bind or fling.

<div className="search-partition-detail">

### Logical declaration snapshot

1. A typed `items` declaration owns one content/session strategy and one selector set, not one
   strategy object or content closure per element.
2. Each logical entry retains only the data required to establish key, content type, semantic and
   environment revisions, kind, span, and the declaration-owned payload needed when a Session
   becomes active.
3. A holder creates or updates a Session directly from the selected entry. The bind path must not
   allocate an item-capturing callback as a substitute for removing one from the committed table.
4. Single-entry declarations may retain a declaration-specific callback because their storage is
   constant in collection size; typed and snapshot declarations must scale with element data, not
   callback wrappers.
5. The current and immediately previous strong-snapshot fast paths remain bounded and preserve
   constant-time identity hits. A monotonic stream cannot retain an unbounded history.

### Key metadata

1. One accepted adapter submission owns one authoritative unique-key position index.
2. Stable ID and view-type metadata may share compact storage with that index when doing so
   preserves collision-free stable IDs across reorder and holder notifications.
3. Duplicate keys remain observable and force the conservative reload path. Compact storage cannot
   turn an ambiguous key into first-match ownership.
4. `contentType` is a finite physical-compatibility class. Adapter storage must not grow without
   bound when submitted content types disappear; diagnostics or tests must expose a violated
   finite-type contract.
5. Sticky-header lookup remains primitive and ordered; it must not add a second full item index.

### Drawing resources

1. Uniform rounded rectangles use the platform round-rect path without allocating or rebuilding a
   `Path`.
2. Stroke `Paint`, `Path`, and geometry exist only while a visible non-empty border needs them.
3. Outline production shares immutable shape resolution or a lightweight provider; it must not
   allocate a second fully drawable surface solely to report an outline.
4. Gradients, non-uniform corners, continuous corners, cut corners, borders, ripple masks, clipping,
   layout direction, density changes, and drawable bounds retain pixel-equivalent behavior.

### Explicit non-goals

1. Do not lower RecyclerView item-cache, prefetch, recycled-pool, or mounted-tree defaults without
   independent evidence. The diagnostic heap retained approximately one viewport of holders and
   did not show excessive physical-tree residency.
2. Do not disable or routinely clear the shared shadow bitmap cache. The Pixel 5
   `performance.shadow-list@3` gap is almost entirely one reused `1,472,976`-byte raster, and
   removing that reuse would trade memory for repeated rasterization during scrolling.
3. Do not flatten arbitrary Android View trees in this plan. Node fusion changes measurement,
   semantics, patch ownership, and interop boundaries and requires separate evidence.
4. Do not optimize `UiLazyListConnector` allocations as part of this baseline: the accepted list
   fixture passes no `LazyListState`, so that connector is not active in the measured workload.

</div>

## Implementation phases

Phases 1--3 completed declaration sharing, compact adapter metadata, and lazy shape resources.
Post-GC attribution rejected Phase 4 small-object pooling as non-material; Phase 5 retains only the
fixed-clock acceptance follow-up.

<div className="search-partition-detail">

### Phase 0: decision, baseline, and gates — complete

- Register this plan and the exact logical, physical, memory, and no-render-regression boundaries.
- Add structural allocation tests or counters that distinguish committed entry objects,
  declaration strategies, active Sessions, holder creation, and bind-time callback creation.
- Re-run the unchanged control before interpreting a candidate when device, benchmark binary, or
  clock policy differs from the accepted baseline.

### Phase 1: declaration-shared typed content — complete

- Replace per-element `WidgetLazyItemSessionBinding` and item-capturing content closures in typed
  and strong-snapshot declarations with one declaration-owned strategy plus element payload.
- Preserve public Q3 lifecycle semantics across create, prepare, activate, revision update,
  rollback, detach-for-reuse, adoption, and disposal. If the renderer-neutral contract must change,
  hard-cut it with canonical KDoc, compiled samples, API dumps, module manuals, migration text, and
  a breaking changeset in the same phase.
- Cover stable submission, revision change, environment change, alternating snapshots, monotonic
  snapshots, duplicate keys, failure retry, key replacement, saveable-state retention, effects,
  AndroidView reset/release, and no bind-time content-wrapper allocation.

### Phase 2: compact adapter metadata — complete

- Replace overlapping position and stable-ID maps with one submission-owned metadata structure or
  demonstrate with retained-size evidence why a map must remain separate.
- Avoid boxed `Int`/`Long` values on the common indexed path while retaining arbitrary application
  key equality and collision-safe IDs.
- Bound or prune view-type metadata without reassigning a type still referenced by RecyclerView.
- Preserve all diff, rotation, payload acknowledgement, sticky-header, focus-anchor, attached-holder
  refresh, and duplicate-key behavior.

The accepted implementation stores keys, positions, and stable IDs in one collision-safe open
addressed submission table. A separate compact registry preserves RecyclerView view-type identity
without `Pair` keys, map nodes, or boxed IDs, and rejects more than 1,024 distinct kind/type
compatibility classes in one mounted container. Focused renderer tests cover reorder, hash
collisions, disappearance and reappearance, duplicate-key fallback, attached-holder refresh, and
the finite view-type boundary.

### Phase 3: lazy shape resources — complete

- Make uniform rounded fills path-free and borders lazy.
- Replace the outline-only `UiShapeDrawable` with a lightweight, bounds-aware outline source or
  safely reuse the installed shape drawable.
- Add exact drawing, outline, alpha, color-filter, bounds, density, direction, border, gradient,
  ripple, and clipping regression coverage before measuring.

The accepted implementation retains no `Path` for a uniform rounded shape, creates and releases
stroke paint/path state with the visible border, and uses a paint-free bounds-cached outline
provider instead of a second full drawable. Shared primitive path construction preserves rounded,
continuous, and cut-corner pixels without allocating temporary arc rectangles. Renderer tests
cover native round rectangles, generic paths, gradients, RTL, density, border insets, bounds,
outline insets, clipping, alpha, and color-filter propagation.

### Phase 4: optional small object convergence — omitted

- Reuse one aggregate `UiEnvironmentValues` per captured Local snapshot or build frame when identity
  and environment invalidation semantics remain unchanged.
- Canonicalize empty decoration and resolved-modifier values only when profiling still attributes a
  material retained-object contribution after Phases 1–3.
- Omit this phase when the saving is below the measurement noise floor; do not add pooling or
  mutable global caches for small immutable values.

The post-GC control/candidate heap pair retained exactly 124 `UiEnvironmentValues` instances with
`3,968` bytes of aggregate shallow size in each arm. That is neither a growth source nor a material
share of the live set. Canonicalizing it or other small immutable values would add identity,
invalidation, and cache ownership complexity without addressing the measured gap, so this phase is
intentionally omitted.

### Phase 5: benchmark acceptance and documentation — complete

- Run focused module tests, compiled API samples, API checks, documentation gates, `qaQuick`, and
  the relevant physical-device scenarios.
- Compare ordinary list scroll and mutation before/after with identical APK mode, fixture revision,
  action protocol, compilation identity, thermal state, and fixed-clock policy. Capture P50, P95,
  P99, frame count, run-P50 CV, peak heap, RSS anon, holder create/bind counts, and live-object
  attribution after GC.
- Re-run `performance.shadow-list@3` after drawing changes to prove visual and frame-time stability;
  do not classify the retained composite raster as a Phase 1–3 failure.
- Interpret accepted evidence in `docs/tooling/performance.md`, update all affected module and guide
  contracts with matching Simplified Chinese mirrors, and archive this plan only after all gates
  pass.

The same-device Samsung SM-G991B / Android 13 diagnostic used exact control `ea33297b` and candidate
`06a411e7`, five `run-from-apk` iterations per arm, identical actions, and the unlocked-DVFS
preflight policy. Ordinary-list frame P50/P95 changed by `+0.3%/-0.5%`; shadow-list P50/P95 changed
by `-2.7%/-3.7%`. Both run-P50 CV pairs were below `0.04`, and neither timing pair establishes a
material regression. Ordinary-list P99 moved `+12.1%` (`+0.989 ms`), below the combined gate but
retained as a fixed-clock follow-up. Whole-process peak heap/RSS moved within approximately one
percent in conflicting directions and is inconclusive on this unlocked device.

An identical debug control/candidate full-fling protocol followed by `am dumpheap` produced the
attribution needed to interpret that noisy process metric. The candidate retained 6,276 fewer
indexed instances and arrays and `129,518` fewer shallow bytes. It removed all 1,000
`WidgetLazyItemSessionBinding` objects, all 1,000 item-capturing collector lambdas, 1,000
`HashMap.Node` objects, and 608 `LinkedHashMap.Entry` objects. It also retained 136 fewer `Paint`
objects, 184 fewer `Path` objects, 184 fewer `RectF` objects, and 320 fewer native-allocation cleaner
wrappers. These exact class deltas match Phases 1--3 rather than a smaller RecyclerView cache or
deferred bind work. The result accepts the structural memory reduction but does not replace the
fixed-clock peak-memory gate.

Repository acceptance passed `:viewcompose-ui-contract:test`,
`:viewcompose-ui-foundation:testDebugUnitTest`,
`:viewcompose-renderer-android:testDebugUnitTest`, `verifyDocumentationStructure`,
`verifyDevelopmentToolingIsolation`, `verifyViewComposeReleaseIntent`, and the complete
`qaQuick` gate on 2026-08-17.

The final 2026-08-20 fixed-clock pair rebuilt exact control `ea33297b` and candidate `06a411e7`
for the rooted Xiaomi MI 6 / Android 9 reference device. Both arms used the same benchmark APK,
`performance.list@5`, five `run-from-apk` iterations, CPU policies fixed at 1.4016/1.8048 GHz,
Adreno fixed at 515 MHz, stopped vendor performance services, suspended charging, and 35--36
degrees Celsius starts. Control/candidate frame P50/P95/P99 were respectively
`5.218/9.248/11.004 ms` and `5.342/9.304/10.523 ms`, with run-P50 CV `0.089/0.068` and stable
161--164 frame counts. Candidate changes of `+2.37%/+0.60%/-4.37%` are `no material change`, and
the adverse unlocked P99 direction did not reproduce. Median peak heap changed from `7709` to
`7591 KiB`, a `118 KiB` (`1.53%`) reduction. Peak samples alone remain noisy, but their direction
and magnitude corroborate the independently attributed `129,518`-byte and 6,276-object live-set
reduction. The scoped memory conclusion is `improved`, without claiming a universal process-memory
winner. The complete interpretation, APK hashes, limitations, and next action are recorded in
[`docs/tooling/performance.md`](../../tooling/performance.md).

</div>

## Acceptance gates

Acceptance requires isolated logical identity, bounded allocation and adapter metadata, equivalent
drawing, an attributed memory improvement, no material frame-time regression, stable repeated
runs, and complete repository gates.

<div className="search-partition-detail">

| Area | Required evidence |
| --- | --- |
| Logical identity | key State, saveable state, effects, failure retry, and AndroidView reset/release remain isolated |
| Allocation shape | typed 1,000-row declaration retains O(1) strategy/callback objects and creates none per bind |
| Adapter metadata | one bounded current key index; stable IDs survive reorder; disappearing types do not grow storage indefinitely |
| Drawing | uniform rounded fill has no `Path`; border resources are absent without a border; pixel/outline tests pass |
| Memory | ordinary-list peak heap and post-GC live-set improve beyond noise with object attribution matching the implemented phase |
| Frame time | no material P50/P95/P99, transaction-tail, holder-create, or holder-bind regression in accepted A/B runs |
| Stability | at least five accepted iterations per compared arm and run-P50 CV at or below `0.15` |
| Repository | focused tests, API/sample checks, documentation/translation checks, `qaQuick`, and release intent pass |

The combined comparison gate remains the repository rule for directional classification, but this
plan is stricter than merely avoiding a formal regression: a repeatable adverse timing direction
that plausibly comes from shifted bind-time work blocks that phase even when it stays below the
materiality threshold. Memory wins are accepted only when heap/live-object attribution and frame
evidence agree.

</div>

## Completion criteria

The plan completes when typed and snapshot declarations no longer retain per-element binding and
content-wrapper objects, adapter key metadata is bounded and non-duplicative, common rounded
surfaces avoid unnecessary native drawing objects, logical Session and physical View reuse
contracts remain fully tested, ordinary-list memory improves beyond measurement noise, and no
accepted scroll, mutation, preparation, or shadow-list timing metric regresses because work moved
onto the hot path.

These criteria are met: structural tests and heap attribution prove the intended allocation cuts,
the fixed-clock peak-heap direction independently agrees, every accepted timing metric remains
inside the no-regression gate, and the previously adverse unlocked P99 direction reverses under the
root-controlled protocol.

</div>
