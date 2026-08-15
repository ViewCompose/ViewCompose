# Native Widget Contract Convergence Plan

## Status

Complete. Renderer correctness and the public state, collection, input, identity, accessibility,
and layout hard cuts are implemented with focused local and physical-device regression coverage,
durable documentation, compiled samples, migration guidance, and an immutable release changeset.
The selected-module API documentation audit, `qaQuick`, `qaFull`, and focused Demo gesture smoke all
pass.

This archived execution record is canonical English-only under the documentation governance
policy. Durable contracts and accepted evidence now live in architecture, guides, migration pages,
and owning module manuals.

Last verified: 2026-08-15.

## Maven release changesets

- `release/changes/20260815-native-widget-contract-convergence.json`

## Objective

Converge every first-party Android `View` backend on a deliberate ViewCompose contract: common,
portable, semantically important behavior belongs in the DSL and immutable NodeSpec; low-frequency
appearance belongs in typed overrides or complete Basic styles; cross-node layout and semantics
belong in `Modifier`; Android-only root-View configuration remains available through
`Modifier.nativeView`; and behavior that cannot be expressed safely through those layers remains
an explicit `AndroidView` escape path.

The goal is not to mirror every setter exposed by each Android widget. It is to ensure that an
application can build accessible, configurable, state-observable UI without dropping to Android
interop for ordinary product behavior, while keeping the base DSL compact and independent of any
single design system.

## Quality levels and compatibility policy

| Contract family | Quality level | Acceptance requirement |
| --- | --- | --- |
| `ScrollState`, expanded `PagerState`, adaptive-grid cell policy | Q3 | Canonical KDoc, compiled public sample, state/connector tests, renderer lifecycle tests, module documentation |
| Slider stepping, refresh enablement, stable collection-item identity | Q3 | Canonical KDoc, compiled public sample where stateful, input/callback ordering tests, migration note |
| Layout constraint modifiers and immutable NodeSpec additions | Q2, promoted to Q3 when stateful | Measurement tests in LTR/RTL and custom-renderer compatibility note |
| Renderer-only RTL, gravity, accessibility, and settled-callback repairs | Existing public contract repair | Regression tests plus owning-document correction; no compatibility shim for incorrect behavior |

The repository is on an alpha line. A hard cut is preferred when retaining the old signature would
keep two sources of truth, preserve an untestable lifecycle, or make custom renderers guess which
contract is authoritative. Breaking changes receive explicit compatibility notes and an immutable
release changeset in the same implementation.

## Architectural boundaries

1. Foundation exposes renderer-neutral intent. It never exposes `TextView`, `RecyclerView`,
   `ViewPager2`, Material 3 policy, or OEM-specific widget setters.
2. NodeSpecs are immutable resolved snapshots. They may carry state connectors and callbacks but
   never own Android `View` instances or consult process-global configuration.
3. Renderer state connectors attach to one live backend and detach on replacement or disposal.
   Commands are main-thread confined on Android; renderer observations update one immutable
   snapshot at a time.
4. Logical order and collection positions remain stable in RTL. Only physical placement reverses.
5. Custom composite backends publish the same accessibility collection, selection, enabled, and
   role semantics that a tree of ordinary semantic nodes would publish.
6. `key` is logical identity, not display text. Locale changes, label edits, and reorder operations
   must not silently replace identity. Duplicate sibling keys fail deterministically.
7. Design systems resolve appearance before emission. Foundation does not grow a union of
   Material, One UI, or product-specific properties.
8. `Modifier.nativeView` configures only the mapped root `View`, must be replay-safe, and does not
   replace a portable DSL contract. `AndroidView` remains the lifecycle-aware escape path for
   external platform behavior.

## Baseline findings

The canonical backend inventory contains 36 mapped node types. The audit found five correctness
defects in existing contracts: text horizontal alignment also forces vertical centering; Flow and
Tab containers place children physically left-to-right in RTL; generic navigation, segmented, and
tab controls omit collection accessibility metadata; pager callbacks fire on selection rather
than settled idle state; and progress indicators carry an `enabled` field that neither Foundation
nor the backend exposes as real behavior.

The audit also found high-value missing contracts: eager scroll containers have no observable
state or command owner; pager state exposes only page/offset and one ambiguous scrolling command;
the integer slider cannot declare a step or interaction-finished callback; pull-to-refresh cannot
be disabled without structural branching; navigation and segmented identity defaults to labels;
the generic layout modifier family cannot express maximum bounds or aspect ratio; and the grid
requires a fixed span count even when the desired contract is a minimum cell size.

The following remain intentionally outside this plan: obscure widget setter parity, design-system
specific structure, platform text-classification/autofill customization beyond the existing text
input guide, exotic RecyclerView policies without benchmark evidence, and Android APIs already
covered safely by `Modifier.nativeView` or `AndroidView`.

## Phase 0: Contract and inventory lock

Status: complete.

1. Treat `ViewNodeBackendInventoryTest` as the canonical mapped-backend inventory.
2. Classify each missing property into DSL/NodeSpec, Modifier, overrides/Basic style,
   `Modifier.nativeView`, `AndroidView`, or intentional omission.
3. Pin the architectural boundaries and Q levels in this plan before production source changes.

Keep criteria:

- every proposed API has one owner and one renderer-neutral semantic meaning;
- no proposal mirrors a platform setter merely because it exists;
- the plan preserves the existing multi-design-system separation.

## Phase 1: Existing-contract correctness

Status: complete.

1. Make `TextAlign` affect horizontal gravity only; preserve top/default vertical placement.
2. Mirror FlowRow, FlowColumn, and TabRow physical placement in RTL while retaining logical child
   indexes, callbacks, pager interpolation, and accessibility positions.
3. Publish single-selection collection semantics for generic NavigationBar, SegmentedControl, and
   TabRow parents and items.
4. Deliver pager page-change callbacks once the backend reaches idle settled state, without
   duplicate initial or declarative-bind callbacks.
5. Remove the dead progress-indicator `enabled` snapshot field instead of exposing a meaningless
   interaction state.

Keep criteria:

- focused RTL and accessibility tests protect logical versus physical ordering;
- horizontal and vertical pager tests cover drag, fling, programmatic animation, direct scroll,
  rebinding, and disposal;
- all existing Foundation and renderer tests remain green.

## Phase 2: Observable state and input behavior

Status: complete.

1. Add Q3 `ScrollState` with immutable value/range/progress snapshots plus immediate and animated
   scroll commands; attach it to eager vertical and horizontal scroll containers.
2. Hard-cut `PagerState` to immutable snapshots containing current, settled, target, offset,
   page-count, progress, and directional capability; split immediate and animated commands.
3. Make the integer slider's discrete contract explicit with a positive step, deterministic range
   validation, and start/change/finish callback ordering.
4. Add controlled `enabled` behavior to pull-to-refresh without disabling descendant input.
5. Require explicit stable keys for navigation and segmented items, add per-item enabled state,
   reject duplicates, and reconcile keyed child presentations without transferring logical state.

Keep criteria:

- connector replacement and disposal cannot leak observations or accept stale commands;
- callback order is identical for native touch, accessibility, and keyboard paths where Android
  exposes equivalent interaction phases;
- equal immutable state snapshots do not invalidate observers;
- Q3 samples compile from the owning module's maintained sample source set.

## Phase 3: Portable layout and adaptive collection policy

Status: complete.

1. Add maximum-width, maximum-height, and aspect-ratio modifier contracts with deterministic
   constraint validation and one renderer-owned measurement boundary.
2. Replace fixed-only grid configuration with a sealed cell policy supporting fixed columns and
   adaptive minimum cell size.
3. Replace integer grid spans with a renderer-neutral item-span policy that distinguishes one
   cell, fixed cell count, and full line when the adaptive column count is not known at composition
   time.

Keep criteria:

- measurement tests cover bounded/unbounded parents, min/max conflicts, aspect-ratio selection,
  density changes, and RTL;
- adaptive columns recompute from available inner width without rebuilding logical item sessions;
- full-line and fixed spans remain correct after configuration and size changes.

## Phase 4: Documentation, migration, and release closure

Status: complete. Canonical KDoc, Q3 compiled samples, module/architecture/guide/migration
documentation, Chinese mirrors, the immutable release changeset, focused JVM and Robolectric
coverage, and physical-device validation are complete.

1. Update canonical KDoc, Q3 samples, the UI Contract, UI Foundation, Android Renderer, and Android
   Host manuals, relevant layout/lazy-collection guides, and all reviewed Chinese mirrors.
2. Record alpha migration guidance for every hard-cut signature and custom-renderer obligation.
3. Add one immutable release changeset covering every directly changed published artifact and a
   concrete ignored reason for any detected artifact whose public behavior does not change.
4. Run focused unit/Robolectric suites, API documentation audits, documentation structure and
   localization gates, development-tooling isolation, `qaQuick`, and the relevant full gate.
5. Interpret accepted test or benchmark evidence in the owning active documentation; raw output
   alone does not complete this plan.

The Android Host manual is intentionally unchanged. This hard cut does not alter host ownership,
installation, or environment propagation; the existing Host manual remains authoritative, while
all changed contracts are owned by UI Contract, UI Foundation, and Android Renderer.

## Validation matrix

| Area | Required evidence |
| --- | --- |
| Contract | UI Contract and Foundation JVM tests, Q3 sample compilation, API documentation audit |
| Android binding | Robolectric binder/container tests on the repository's supported SDK baseline |
| Direction/configuration | LTR/RTL plus density, locale, font scale, and resource-revision rebinding |
| Accessibility | parent collection metadata, child logical position, role, selected, enabled, and click action |
| Lifecycle | connector replacement, node disposal, retained-tree reuse, callback de-duplication |
| Layout | exact/at-most/unspecified measurement, min/max conflict, aspect ratio, adaptive grid resize |
| Repository | documentation structure/localization, changeset validation, tooling isolation, `qaQuick` |

## Final validation evidence

- Comparison context: current branch against `origin/main` at `ca3d7985`, using the repository's
  complete JVM/Robolectric, publication, sample, documentation, and structural quick gate.
- Absolute results: the final `qaQuick` run passed all 1,619 scheduled tasks in 1 minute 37 seconds;
  the API documentation audit passed for `viewcompose-ui-contract`, `viewcompose-ui-foundation`,
  and `viewcompose-renderer-android`; the final `qaFull` run passed all 1,760 scheduled tasks in
  9 minutes 34 seconds. Samsung SM-G991B completed 122 connected tests with no failures or skips:
  1 Counter test, 119 Demo tests, and 2 tutorial tests.
- Normalized change: not applicable because this is functional contract acceptance rather than a
  timing or benchmark comparison.
- Conclusion: `improved`. The accepted native contracts, state lifecycles, identity rules, layout
  behavior, accessibility semantics, and migration surface are protected without a detected
  repository or device regression. Focused device smoke also verified horizontal eager scrolling
  and a real vertical drag inside a same-axis LazyColumn parent; the latter exposed and closed the
  parent-interception defect, including deterministic edge handoff coverage.
- Limitation and next action: this acceptance makes no frame-time or throughput claim. Future
  performance changes still require the repository benchmark workflow and separately interpreted
  evidence; no implementation action remains for this plan.

## Completion criteria

This plan completes only when all four phases are implemented, every durable contract is moved to
the owning architecture/guide/module documentation, hard-cut migration notes and the immutable
release changeset exist, focused and repository gates pass, and no mapped backend still relies on
an undocumented ordinary-product escape through `AndroidView` for one of the accepted P0/P1
capabilities.
