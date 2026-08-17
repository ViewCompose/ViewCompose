# Lazy Collection Memory Efficiency Plan

## Status

Active. Runtime attribution and the implementation boundary are accepted. Phase 1's shared
strategy/payload hard cut and focused contract tests are complete; adapter metadata convergence is
next.

Last verified: 2026-08-17.

Next action: converge adapter position and stable-ID ownership into one compact submission index,
then prove stable reorder, duplicate-key fallback, attached-holder refresh, and bounded view-type
behavior before changing shape resources.

## Maven release changesets

- `release/changes/20260817-lazy-collection-memory-efficiency.json`

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

## Implementation phases

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

### Phase 2: compact adapter metadata — pending

- Replace overlapping position and stable-ID maps with one submission-owned metadata structure or
  demonstrate with retained-size evidence why a map must remain separate.
- Avoid boxed `Int`/`Long` values on the common indexed path while retaining arbitrary application
  key equality and collision-safe IDs.
- Bound or prune view-type metadata without reassigning a type still referenced by RecyclerView.
- Preserve all diff, rotation, payload acknowledgement, sticky-header, focus-anchor, attached-holder
  refresh, and duplicate-key behavior.

### Phase 3: lazy shape resources — pending

- Make uniform rounded fills path-free and borders lazy.
- Replace the outline-only `UiShapeDrawable` with a lightweight, bounds-aware outline source or
  safely reuse the installed shape drawable.
- Add exact drawing, outline, alpha, color-filter, bounds, density, direction, border, gradient,
  ripple, and clipping regression coverage before measuring.

### Phase 4: optional small object convergence — pending

- Reuse one aggregate `UiEnvironmentValues` per captured Local snapshot or build frame when identity
  and environment invalidation semantics remain unchanged.
- Canonicalize empty decoration and resolved-modifier values only when profiling still attributes a
  material retained-object contribution after Phases 1–3.
- Omit this phase when the saving is below the measurement noise floor; do not add pooling or
  mutable global caches for small immutable values.

### Phase 5: benchmark acceptance and documentation — pending

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

## Acceptance gates

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

## Completion criteria

The plan completes when typed and snapshot declarations no longer retain per-element binding and
content-wrapper objects, adapter key metadata is bounded and non-duplicative, common rounded
surfaces avoid unnecessary native drawing objects, logical Session and physical View reuse
contracts remain fully tested, ordinary-list memory improves beyond measurement noise, and no
accepted scroll, mutation, preparation, or shadow-list timing metric regresses because work moved
onto the hot path.
