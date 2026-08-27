---
draft: true
schema_version: 2
document_id: plan.lazy-list-tail-performance-diagnostics
doc_type: plan
owner:
  kind: project
  id: performance
version_lane: version-agnostic
capability_ids:
  - lazy.collections
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
sample_ids: []
status: active
scope: Use the shipped finite diagnostics to attribute and reduce the accepted LazyColumn tail-latency gaps.
non_goals:
  - Replace Macrobenchmark or Perfetto with instrumented node timing.
  - Weaken lazy identity, lifecycle, prefetch, accessibility, or benchmark-equivalence contracts.
baseline: The accepted performance.list@5 matrix regresses in scroll P95 against Compose and Android Views and in mutation P95 against Android Views.
ordered_work:
  - Freeze the workload and collect pre-change diagnostic, Macrobenchmark, and trace evidence.
  - Isolate one attributed tail source, implement the minimum reversible correction, and remeasure.
  - Interpret both performance and diagnostic-utility results in their active owners before archival.
completion:
  - Close the accepted list-tail regression or assign every remaining material gap to a separately evidenced owner while proving whether the shipped diagnostics changed the investigation decision.
last_verified: 2026-08-27
next_action: Commit this plan, then capture the unchanged Debug list workload with the correlated inspector before changing production source.
maven_release_changesets: []
---

# Lazy List Tail Performance and Diagnostics Utility Plan

## Status

Active. This work runs in a dedicated worktree and branch based on `main` revision `6ab095a7`, so
the concurrent Documentation Governance V2 work remains isolated. No production implementation has
changed yet.

Last verified: 2026-08-27.

Next action: commit this planning baseline, then run the unchanged Debug `performance.list@5`
fixture through the correlated Session inspector and finite timing capture before creating any
candidate optimization.

## Maven release changesets

- None.

## Release intent rationale

This planning step changes only a repository-only active plan and its index. It does not change a
published artifact, publication input, or compiled API sample. The first production change in
`viewcompose-ui-foundation`, `viewcompose-renderer-android`, or another published artifact must add
one immutable `release/changes/*.json` file in the same pull request, classify the direct artifact
as `fix`, `feature`, `breaking`, or concretely `ignored`, and replace `- None.` above with every
Changeset owned by this plan. Reverse-dependency impact remains release-planner output and must not
be written by hand.

## Objective

Use the completed diagnostics upgrade as a real troubleshooting instrument against the accepted
LazyColumn tail-latency gap, then retain only a measured, architecture-compatible correction.

The work has two equal outcomes:

1. reduce the remaining `performance.list@5` scroll and mutation tails without shifting work into
   startup, prefetch, layout, memory, or lifecycle cleanup; and
2. determine whether correlated Session discovery, source navigation, mounted-node inspection, and
   finite composition/reconciliation/binding timing produce an actionable decision on a real
   performance defect.

The diagnostics result is not assumed to be positive. A truthful finding that the material tail is
outside the shipped timing domains is useful only when the bounded capture makes that exclusion
explicit and a platform trace independently confirms the next owner. An empty capture without that
cross-check is inconclusive.

## Baseline and accepted evidence

The current performance owner records the following fixed-clock Xiaomi MI 6 / Android 9 results for
the unchanged 1,000-row `performance.list@5` workload. Frame values are P50/P95 milliseconds and
heap is median peak KiB.

| Workload | ViewCompose | Compose | Android Views | Current conclusion |
| --- | --- | --- | --- | --- |
| Scroll | `5.328/9.538`, heap `7650` | `4.743/7.616`, `7398` | `4.991/7.188`, `4049` | ViewCompose P95 is `25.2%` above Compose and `32.7%` above Views; `regressed` against both. |
| Mutation | `4.247/12.698`, heap `8128` | `5.207/18.568`, `8597` | `4.287/7.849`, `5864` | `improved` versus Compose, but P95 is `61.8%` above Views and remains `regressed` versus the native control. |

The accepted allocation hard cut removed 6,276 objects and 129,518 shallow bytes. Its fixed-clock
P50/P95 changed only `+2.37%/+0.60%`, so declaration sharing, compact adapter metadata, and lazy
drawing resources are retained but do not close the cross-engine tail. The current owner names
scroll P95/heap and the native mutation-tail gap as the next collection targets.

The diagnostic upgrade provides one process-local Session tree across Host and LazyItem sessions,
bounded source and mounted-node inspection, and an explicitly requested timing capture of at most
eight frames or two seconds. It measures executed composition scope, reconciliation, and direct
binding intervals. It deliberately excludes Android measure/layout, View/drawable draw, GPU,
RenderThread, SurfaceFlinger, decode, network, database, and external SDK work. Inactive timing
performs zero per-node clock reads and the concrete tooling is excluded from optimized Release
artifacts.

Two physical devices are currently attached. Formal longitudinal timing uses the rooted Xiaomi MI
6 only after the existing CPU, GPU, interconnect, thermal, charging, and vendor-service controls are
revalidated. The Pixel 4 XL may supply functional or API-level diagnostic evidence, but its missing
fixed-performance Power HAL contract prevents it from replacing the accepted fixed-clock timing
authority.

## Scope

### In scope

- the existing `ListPerformanceComparisonBenchmark` scroll and mutation actions at workload
  revision `performance.list@5`;
- the ViewCompose LazyColumn declaration, logical item Sessions, RecyclerView adapter, holder
  activation/preparation, mounted-tree reuse, binding, layout request, and decoration paths;
- request-driven diagnostic captures for the Host and representative LazyItem sessions, including
  source location, node role, timing phase, inclusion rule, raw nanoseconds, record/drop counts,
  terminal reason, unsupported domains, and clock overhead;
- platform traces and focused counters needed for measure/layout/draw, RecyclerView, RenderThread,
  or buffer-queue work that the finite node timer intentionally does not own;
- one-factor-at-a-time A/B candidates for an attributed source such as cold Session activation,
  prefetch placement, redundant attach/bind work, layout invalidation, or wrapper/decoration cost;
- focused unit and device regression coverage, the complete three-engine control after candidate
  selection, release intent, module/API documentation impact, and active English/Chinese evidence
  closure; and
- a written assessment of diagnostic utility, limitations, friction, and any justified follow-up.

### Out of scope

- changing row count, row hierarchy, gestures, mutation/reset count, stable keys, content type,
  snapshot identity, spacing, colors, item animation policy, readiness window, or engine controls to
  make the candidate appear faster;
- comparing raw values across different devices, workload revisions, compilation identities,
  display modes, clock policies, or thermal states;
- accepting Debug timing as a Release frame-time result or adding diagnostic clock reads to the
  inactive path;
- disabling RecyclerView behavior, accessibility, focus visibility, nested scrolling, prefetch, or
  physical reuse globally without isolated behavioral and performance evidence;
- reducing cache residency merely to improve peak heap when creation, binding, or tail latency
  moves onto the fling path;
- adding a continuous observer or broadening the first timing-domain contract without a separate
  ADR-backed scope that preserves ADR-0009 and ADR-0021; and
- reopening completed lazy ownership or memory plans without new contradictory evidence.

## Investigation questions and hypotheses

The investigation answers these questions in order:

1. Does a list mutation spend its material direct time in composition, reconciliation, or native
   binding, and which authored row/session produces that time?
2. Does pure scroll execute any measured ViewCompose phase, or is its tail dominated by the
   explicitly unsupported native layout/draw/RenderThread path?
3. When a new row enters the viewport, is the cost cold logical Session activation, effect-free
   preparation, physical-tree adoption, holder binding, measurement/layout, or drawing?
4. Does the candidate remove work, move it earlier under an explicit bounded prefetch contract, or
   merely shift it to another unmeasured phase?
5. Did the shipped diagnostic output change which source path was investigated or rejected, and
   did source/session correlation remain correct under holder recycling?

Initial hypotheses, ordered by proximity to the accepted evidence, are:

- **H1 — cold item activation/binding:** new visible keys perform composition, reconciliation, or
  direct native binding on the fling path. Finite LazyItem timing should expose this when the
  selected logical Session executes the path.
- **H2 — prefetch placement:** RecyclerView prefetch plus `LazyPreparationCostTracker` schedules
  bounded detached preparation close enough to the fling that it contributes to the tail. A
  controlled policy seam can test placement without deleting the feature.
- **H3 — native layout/draw:** ViewCompose phase time is small or absent while RecyclerView child
  measurement, wrapper/decorated layout, View drawing, RenderThread, or buffer-queue work owns the
  tail. Perfetto and focused layout/draw counters, not a wider diagnostic claim, must prove this.
- **H4 — redundant attach or semantic refresh:** holder attachment, queued notifications, or
  revision acknowledgement repeats activation/binding/layout work that stable keys should skip.
  Adapter/session counters and captured patch records should expose duplicate work.

No hypothesis authorizes production modification until the pre-change capture either supports it
or rules out the higher-priority alternatives.

## Diagnostic utility contract

The diagnostics evaluation records the following for every accepted capture:

1. target workload/action, source revision, build variant, device/API, selected Session role and
   parent, request phases, start status, terminal reason, completed frames, and capture duration;
2. timing records ranked by additive self/direct duration, with inclusive parents kept separate;
3. attempted and retained clock reads, empty-pair overhead, dropped node/record/string counts,
   truncation, and unsupported domains;
4. whether the displayed source location identifies the code eventually inspected or changed;
5. whether holder recycle/replacement produces a new logical Session instead of reusing stale
   identity; and
6. the investigation decision created by the capture: prioritize, reject, or hand off one
   hypothesis to an explicitly named platform tool/domain.

The tool is considered **actionable** only when repeated bounded captures satisfy identity and
cardinality rules and either identify a source-owned measured phase or correctly exclude all three
supported phases with independent trace agreement. It is **partially useful** when it reliably
narrows the owner but cannot rank the material unsupported work. It is **not useful for this
defect** when output is stale, truncated without explanation, dominated by instrumentation,
misattributes recycled content, or does not alter the investigation decision.

This classification describes the list-tail investigation only. It neither reverses the completed
diagnostics acceptance nor generalizes from one workload to all troubleshooting tasks.

## Ordered execution

### Phase 0: plan and contract freeze

1. Add this active plan and index entry before implementation.
2. Freeze `performance.list@5`, the three-engine source parity, accepted control values, current
   diagnostic domains, materiality thresholds, stability gate, and device policy.
3. Record the dedicated branch baseline and keep the concurrent governance branch untouched.
4. Commit the plan separately before collecting candidate evidence.

Exit condition: the repository contains one reviewable plan commit and no production change.

### Phase 1: pre-change diagnostics and reproducibility

1. Build and install the unchanged Debug Demo with optional Preview tooling, launch the ViewCompose
   list route directly, and obtain a protocol-v7 source report.
2. Verify the Host plus visible/inactive LazyItem parent graph and record representative source
   locations before timing.
3. Automate or reproducibly document three bounded capture classes:
   - selected visible LazyItem mutation/rebind;
   - Host capture during pure scroll;
   - selected LazyItem capture across detach/recycle or replacement.
4. Repeat each capture enough to distinguish a stable phase ranking from a one-off result. Preserve
   raw JSON outside the repository and summarize only accepted observations in the plan.
5. Run the unchanged Release list methods or a scoped same-binary preflight to prove the current
   fixture and device policy still reproduce a material tail before optimization.

Exit condition: supported-phase evidence is repeatable with no unexplained drop/truncation, and the
remaining tail owner is either inside a measured phase or explicitly handed to a platform trace.

### Phase 2: platform attribution and one-factor A/B seams

1. Capture Perfetto/system trace evidence for the same action when Phase 1 points to an unsupported
   domain. Keep ViewCompose trace sections, RecyclerView work, measure/layout/draw, RenderThread,
   scheduling, and buffer-queue waits distinguishable.
2. Add focused counters or test-only trace markers only when existing evidence cannot separate
   activation, preparation, binding, layout requests, and drawing. Such probes stay out of Release
   behavior or are strictly inactive without a request.
3. Test one hypothesis at a time against the unchanged ViewCompose action. Reject candidates that
   move cost to startup, first attach, later fling, heap, or disposal.
4. Preserve rejected measurements and state why they did not justify a production change.

Exit condition: one production-owned cause has source/test ownership and a reversible candidate,
or evidence proves the material gap is entirely outside ViewCompose-owned work and names the next
valid owner without pretending the objective is complete.

### Phase 3: minimum correction

1. Analyze the owning module and call-path impact with CodeGraph, then inspect recurring patterns
   structurally before editing.
2. Implement the smallest correction that removes the attributed work while preserving logical
   key identity, transaction rollback, preparation silence, effect ownership, AndroidView reset and
   release, focus/accessibility, state publication, and RecyclerView behavior.
3. Add focused tests that fail on the old behavior and prove both the optimized fast path and its
   fallback/failure path.
4. Resolve documentation impact before any public/protected change. A public API change requires a
   stable capability owner, Governance V2 impact record, Q level, complete canonical-English KDoc,
   Q3 compiled sample where required, generated Reference update, module manual, and all explicit
   dispositions in the same change.
5. Add and list the immutable Maven Changeset when a published artifact production or publication
   input changes.

Exit condition: focused behavioral tests pass and the candidate has a direct evidence link to the
Phase 1/2 cause rather than only a favorable benchmark result.

### Phase 4: performance and diagnostic acceptance

1. Re-run the same Debug captures on the candidate and state whether the attributed phase/node work
   disappeared, decreased, or moved. Do not compare active diagnostic durations directly with the
   Release Macrobenchmark.
2. On the fixed-clock Xiaomi reference path, build exact control and candidate APKs, record hashes,
   and run at least five accepted iterations per required method with run-P50 CV at or below `0.15`.
3. Run ViewCompose, Compose, and Android Views scroll and mutation methods from the same candidate
   binary/context. Record frame P50/P95/P99, frame counts, median peak heap and RSS when available,
   thermal state, clock policy, and rejected batches.
4. Classify every control using the active combined thresholds: P50 is material only beyond both
   `10%` and `0.3 ms`; P95 only beyond both `15%` and `0.8 ms`. Preserve important absolute frame
   budget and P99 risks even when outside the primary gate.
5. Reject a candidate that improves a median while materially worsening P95, P99, heap/RSS,
   holder creation/binding, first attachment, or lifecycle cleanup.

Primary performance success requires list-scroll P95 to improve materially against the exact
pre-change control and no longer be classified `regressed` against both same-run Compose and Android
Views controls. Mutation success requires no loss of the accepted Compose advantage and either
closure of the Android Views P95 regression or a second independently attributed correction in
this plan. A partial movement is recorded as `mixed` and does not complete the plan.

Exit condition: the candidate meets the performance target with stable evidence, and the diagnostic
utility classification is supported by before/after captures and platform-trace agreement.

### Phase 5: repository closure and archive

1. Run focused owning-module tests, API/sample/documentation gates, release-intent verification,
   `qaQuick`, `qaPreview` when Preview/tooling changes, `qaRelease`, and the applicable connected
   tests. Run `qaFull` before completion unless a scoped device exception is recorded with an owner
   and deadline.
2. Update `docs/tooling/performance.md` and its reviewed Simplified Chinese mirror with comparison
   context, absolute values, normalized changes, one required conclusion, limitations, and next
   action.
3. Update `docs/tooling/diagnostics.md` and its mirror when this investigation changes the durable
   troubleshooting guidance, exposes a limitation users must know, or justifies a diagnostic
   contract change. Otherwise record a concrete no-documentation-impact rationale.
4. Update lazy architecture, guides, migration, module manuals, and API comments only when their
   owned behavior or contract changes; do not duplicate the performance ledger across them.
5. Move durable conclusions before archiving this plan, update both plan indexes, and preserve all
   accepted Changesets.

Exit condition: required repository/device gates pass, current documents own both conclusions, no
material tail remains unowned, and this plan has no pending next action.

## Validation matrix

| Area | Required evidence |
| --- | --- |
| Workload integrity | exact `performance.list@5` route, 1,000 rows, stable keys/type, eight bidirectional gestures, eight mutation/reset cycles, equal three-engine presentation |
| Diagnostics identity | Host/LazyItem parent graph, logical identity across recycle, current source location, nonce/session validity, terminal capture state |
| Diagnostics bounds | at most eight frames/two seconds, raw clock overhead, attempted/retained reads, drops, truncation, strings/depth/response limits, unsupported domains |
| Supported phases | composition inclusive/self, reconciliation inclusive/self, binding direct; no double-counted parent ranking |
| Unsupported phases | measure/layout/draw/RenderThread/GPU/SurfaceFlinger claims come from platform evidence, not node-timing inference |
| Lazy behavior | key/revision State and effects, preparation activation boundary, attached refresh, reuse/reset/release, rollback, focus/accessibility, disposal |
| Performance | fixed-clock control/candidate plus same-run Compose/Views, P50/P95/P99, frame counts, CV, peak heap/RSS, thermal and clock policy |
| Release/tooling isolation | inactive capture has zero node clock reads; optimized Release excludes concrete Preview tooling; no recurring observer is added |
| Documentation/release | impact matrix, English/Chinese active owners, immutable Changeset when required, generated Reference/API sample rules, release planner |
| Repository | focused tests, `verifyDocumentationStructure`, `verifyDevelopmentToolingIsolation`, `verifyViewComposeReleaseIntent`, `qaQuick`, applicable `qaPreview`, `qaRelease`, and device gates |

## Hard-cut rules

1. Do not change the benchmark workload after seeing a poor result without advancing its revision
   and establishing a new baseline; a new revision cannot replace the accepted longitudinal row.
2. Do not call an instrumented timing capture a frame-time benchmark or add its phase durations to
   unsupported platform durations.
3. Do not add recurring listeners, clocks, histories, traversal, serialization, or report writes
   to the inactive application path.
4. Do not keep a speculative optimization because one unpaired run is favorable.
5. Do not reduce native reuse, prefetch, or cache policy globally to hide tail work; prove total
   placement, memory, lifecycle, and behavior first.
6. Do not preserve a parallel slow path or compatibility alias after an accepted internal hard cut,
   except when the public contract explicitly requires the fallback.
7. Do not mark the plan complete while accepted evidence, diagnostic limitations, or remaining
   material gaps exist only in raw reports, chat, or this temporary plan.

## Completion criteria

This plan completes only when:

1. the unchanged workload has a reproducible pre-change diagnostic and Release baseline;
2. the investigation identifies one source-owned tail cause or truthfully proves a supported-domain
   exclusion with matching platform evidence;
3. the minimum correction preserves every lazy logical/physical/lifecycle contract and has focused
   regression coverage;
4. fixed-clock candidate evidence closes the list-scroll regression against both same-run controls,
   preserves the Compose mutation advantage, and closes every material native mutation-tail gap
   through an accepted correction rather than a benchmark change;
5. the diagnostics upgrade receives an evidence-backed `actionable`, `partially useful`, or `not
   useful for this defect` classification with setup friction and domain limitations recorded;
6. accepted test and benchmark evidence is interpreted in the owning active English document and
   reviewed Chinese mirror with absolute results, normalized change, conclusion, limitations, and
   next action;
7. release intent, API/source documentation, module manuals, samples, and Governance V2 records are
   complete for the actual source changes; and
8. all required repository, Release, Preview/tooling, and device gates pass before the plan moves to
   `docs/archive/` and both plan indexes are updated.
