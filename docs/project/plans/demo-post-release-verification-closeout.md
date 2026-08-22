# Demo Post-release Verification Closeout Plan

## Status

Active. The coordinated Maven and ViewCompose Preview 1.1.0 release and the preempting focus
visibility hard cut are complete. The release-bearing Demo rearchitecture and its renderer/overlay
repairs are archived in `docs/archive/demo-benchmark-verification-harness-rearchitecture.md`. This
plan now resumes ownership of the remaining internal harness baseline, broad visual matrix, golden
coverage, and dead-infrastructure cleanup.

Phase 1 is partially complete. The rooted Xiaomi run accepted the revision-3 eight-cycle mutation
absolute baseline at run-P50 CV `0.025`. Scroll remains unaccepted after two complete v4 batches at
CV `0.191` and `0.196`; Perfetto attributes the two plateaus to system RenderThread/BufferQueue
wait while application work and controlled clocks remain materially equal. No fixture or measured
action was changed in response.

A same-device root audit found no remaining exposed control that can be added without changing the
rendering path: the panel has one fixed 60 Hz mode, its framebuffer idle/dynamic-update controls are
already disabled, and SurfaceFlinger already uses the available backpressure/latch controls. Scroll
recapture on this device is therefore deferred rather than tuned into a passing batch.

Last verified: 2026-08-22.

The configuration matrix exposed an unstable framework-level focus-visibility design. That slice
was completed by the higher-priority
[archived focus visibility and scroll ownership hard-cut plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/focus-visibility-scroll-ownership-hard-cut.md)
plan and was not closed by adding more tests to the rejected Boolean policy. This plan retains the
popup, navigation, theme, segmented, rounded-grid, and nested-scroll slices.

Next action: execute the remaining visual/configuration matrix and popup golden, then recapture
revision-3 scroll on another root-controllable reference device. Do not remove production harness
infrastructure or close Phase 1 until scroll passes the unchanged `0.15` gate.

## Maven release changesets

- None.

## Release intent rationale

The remaining scope is confined to the Demo application, its instrumentation, the internal
Macrobenchmark harness, and validation assets. It owns no current published-artifact production
change. If later work changes production source, publication inputs, or compiled API samples for a
published artifact, this section must be replaced in the same pull request with the immutable
Changeset that owns that impact; the work cannot remain hidden under `- None.`.

## Objective

Close the verification breadth intentionally left after the release-bearing rearchitecture without
reopening its architecture or delaying the coordinated framework and Preview-plugin release. The
finished state has:

1. one accepted fixed-clock `collection.stress@3` baseline with an interpreted stability result;
2. a repeatable bilingual, theme, layout-direction, font-scale, and representative-density visual
   matrix for the repaired pressure slices;
3. automated popup-shadow pixel coverage that complements, rather than replaces, real-device
   interaction;
4. no obsolete monolithic tag or routing compatibility infrastructure; and
5. durable conclusions in current tooling/capability documents before this plan is archived.

## Scope boundary

### In scope

- `collection.stress@3` scroll and mutation under the existing five-iteration, per-method cooling,
  ready/action/state/reset, fixed-clock, and `run-from-apk` protocol;
- the focused menu-shadow, theme-swatch, NavigationBar quick-tap, One UI navigation, segmented
  shape, nested-scroll, and rounded-grid pressure slices;
- English and Simplified Chinese, light and dark themes, LTR and RTL where supported, font scales
  `1.0` and `1.3`, and at least one non-default representative density;
- popup visual-outset pixel assertions at representative anchor positions plus real-device
  dismissal, alignment, and clipping checks; and
- deletion of the remaining obsolete tag registry, module-route compatibility, or other harness
  infrastructure only after caller and selector guards prove it is unused.

### Out of scope

- new framework APIs, new design-system components, public diagnostics redesign, or benchmark
  workloads unrelated to the remaining Demo closure;
- focus visibility and scroll ownership, now owned by the separate highest-priority hard-cut plan;
- changing a fixture merely to produce a more favorable performance number;
- weakening locale-independent selector, deterministic reset, direct-scenario routing, or
  workload-revision contracts; and
- treating screenshots or goldens as substitutes for touch, IME, lifecycle, overlay dismissal, or
  accessibility behavior.

## Work packages

### Phase 0: contract freeze — complete

- Preserve the existing scenario IDs, workload revisions, resource-ID selectors, five-run
  stability ceiling, and fixed-clock Xiaomi protocol.
- Carry forward the exact remaining scope from the archived rearchitecture record rather than
  silently redefining its completion criteria.
- Keep the first post-release pull request publication-neutral unless implementation actually
  reaches a published artifact.

### Phase 1: collection-stress revision 3 baseline

Progress on 2026-08-21: mutation is accepted as a stable absolute baseline; scroll and therefore
the phase remain incomplete. The complete interpretation, APK hashes, rejected batches,
BufferQueue attribution, limitations, and next action live in
[`docs/tooling/performance.md`](../../tooling/performance.md).

1. Build one release-like target and benchmark APK from a reviewed source revision.
2. Run scroll and mutation separately on the rooted reference device with CPU/GPU and required
   renderer-interconnect policy, thermal start, compilation identity, frame count, heap, and APK
   hashes recorded.
3. Accept only five-run batches whose run-P50 CV is at most `0.15`; unstable results are rerun, not
   averaged into acceptance.
4. Record P50/P95/P99, frame count, peak heap, absolute values, normalized direction, conclusion,
   limitations, and next action in `docs/tooling/performance.md` and its Simplified Chinese mirror.
5. Do not compare revision 3 against retired revision 2 as if the fixture were unchanged.

### Phase 2: visual and configuration matrix

1. Produce a deterministic checklist keyed by scenario ID, configuration, action, and expected
   invariant rather than by translated copy.
2. Execute the repaired pressure slices in both supported locales and themes, with RTL applied to
   directional scenarios, font scale `1.3`, and one representative density override.
3. Capture intermediate pressed/released states for both standard and One UI navigation quick taps;
   selection alone does not prove ripple survival.
4. Verify nested same-axis scrolling in both directions before and after edge handoff, complete IME
   editor reveal, popup semantic anchoring and shadow outset, equal theme-swatch width, segmented
   inset geometry, rounded clipping, and reset/relaunch determinism.
5. Record actual failures as framework, fixture, or harness defects before changing code.

### Phase 3: popup golden and harness cleanup

1. Add an automated popup image assertion that samples transparent outset and non-background shadow
   pixels around every rounded edge at representative density and edge placement.
2. Keep real-device popup interaction in the gate because a bitmap cannot prove outside-touch
   dismissal, anchor updates, or window lifecycle.
3. Use call-site and structural searches plus selector guards to prove obsolete routing/tag helpers
   have zero callers before hard deletion.
4. Remove compatibility infrastructure rather than preserving aliases once all callers are gone.
5. Run the complete Demo unit, instrumentation, benchmark-compilation, documentation, and release
   quality gates affected by the cleanup.

### Phase 4: documentation and archive

- Interpret every accepted benchmark or visual result in the owning current document with context,
  absolute observations, normalized direction where applicable, conclusion, limitations, and next
  action.
- Update the capability-verification workflow if the executable matrix changes its durable contract.
- Reassess publication impact and Changesets before merge.
- Archive this plan only when every completion criterion below is met.

## Hard-cut rules

1. Do not restore visible-copy selectors, module-key routing, chapter wrappers, or a monolithic
   Activity for test convenience.
2. Do not lower workload duration, remove assertions, or alter the fixture after seeing a poor
   result without advancing its revision and explaining the semantic change.
3. Do not replace manual interaction with screenshots where timing, gesture ownership, IME,
   lifecycle, or dismissal is the behavior under test.
4. Do not retain dead compatibility APIs or helper registries after the zero-caller proof.
5. Do not add a Maven Changeset for app-only work, and do not omit one if published production
   source becomes involved.

## Validation matrix

| Area | Required evidence |
| --- | --- |
| Collection performance | fixed-clock revision-3 scroll/mutation, five stable iterations, P50/P95/P99, frame count, peak heap, hashes, and interpreted conclusion |
| Locale/theme | English and Simplified Chinese in light/dark modes with no selector dependence on copy |
| Direction/font/density | RTL-sensitive paths, font scale 1.3, and representative density without clipping or ambiguous fixtures |
| Interaction | real-touch quick-tap feedback, nested edge handoff, IME reveal, popup dismissal/anchor behavior, reset and relaunch |
| Visual | focused screenshots plus popup pixel golden; expected invariants and limitations recorded |
| Cleanup | zero callers before hard deletion and selector/routing guards remain green afterward |
| Repository | affected JVM/device suites, benchmark compilation, `qaQuick`, documentation/translation gates, and release-intent verification |

## Completion criteria

This plan completes only when collection-stress revision 3 has an accepted fixed-clock baseline;
the full frozen configuration matrix passes or every accepted limitation is assigned to a separate
active owner; popup shadow has both real-device and pixel-level evidence; obsolete harness
infrastructure is removed with zero-caller proof; current English and Simplified Chinese documents
interpret the evidence; publication impact is correctly classified; and all affected repository
gates pass.
