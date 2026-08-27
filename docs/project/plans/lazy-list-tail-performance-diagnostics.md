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
  - diagnostics.session-inspection
  - diagnostics.node-timing
  - preview.integration
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
  - viewcompose-preview
sample_ids: []
status: active
scope: Use and, when evidence proves necessary, minimally extend the finite diagnostics to attribute and reduce the accepted LazyColumn tail-latency gaps.
non_goals:
  - Replace Macrobenchmark or Perfetto with instrumented node timing.
  - Weaken lazy identity, lifecycle, prefetch, accessibility, or benchmark-equivalence contracts.
baseline: The accepted performance.list@5 matrix regresses in scroll P95 against Compose and Android Views and in mutation P95 against Android Views.
ordered_work:
  - Freeze the workload and collect pre-change diagnostic, Macrobenchmark, and trace evidence.
  - When repeated captures cannot observe the cold interaction owner, add one bounded interaction-armed correlation seam before testing more optimization candidates.
  - Isolate one attributed tail source, implement the minimum reversible correction, and remeasure.
  - Interpret both performance and diagnostic-utility results in their active owners before archival.
completion:
  - Close the accepted list-tail regression or assign every remaining material gap to a separately evidenced owner while proving whether the shipped diagnostics changed the investigation decision.
last_verified: 2026-08-27
next_action: Continue platform-tail attribution in measure/layout/draw, input, and RenderThread before testing another production candidate; the unresolved Android Views P95 gaps remain open.
maven_release_changesets:
  - release/changes/20260827-armed-lazy-item-diagnostics.json
---

# Lazy List Tail Performance and Diagnostics Utility Plan

## Status

Active. This work runs in a dedicated worktree and branch based on `main` revision `290b1fc0`, so
the concurrent documentation-governance work remains isolated. The plan-first baseline is commit
`754e018d`; the evidence-triggered Phase 1A amendment is commit `9f245448`. Seven one-factor
implementation/configuration probes have been measured and reverted; no performance implementation
is retained because none met the acceptance threshold. The request-driven diagnostics correction
passed focused behavior, optimized-Release isolation, exact-control performance acceptance, and the
repository quality suites. The Xiaomi connected suites also passed independently; one aggregate
`qaFull` invocation cannot stay green because MIUI intermittently rejects UTP install sessions, as
recorded below.

Last verified: 2026-08-27.

Next action: continue attribution in the unsupported measure/layout/draw, input, and RenderThread
domains before testing another production candidate. The plan remains active because the Android
Views scroll and mutation P95 gaps are still material and no production performance correction has
passed acceptance.

## Execution record: 2026-08-27

### Diagnostic attribution

The unchanged Debug fixture produced the expected Host plus logical LazyItem parent graph and
resolved the authored row to `app/src/main/java/com/viewcompose/performance/ViewComposeListPerformanceScreen.kt`.
Repeated finite captures completed without drops or truncation. Representative LazyItem captures
ranked direct Text binding at `1.72..1.89 ms`, composition self time at `1.10..1.47 ms`, and
reconciliation self time at `0.76..0.87 ms`. A Host capture during pure scrolling contained only
the structural frame forced by starting the capture; the subsequent scroll did not execute a
supported Host composition, reconciliation, or direct-binding phase.

The identity checks were correct but exposed an important operating limit. Moving the selected key
out of the viewport ended that logical Session, and the newly visible key received a new Session
ID. The current inspector cannot arm a capture for that future Session or follow replacement by
logical role/key. Starting timing also requests an immediate structural render, so that first frame
must not be mistaken for the later interaction. These limits prevent the current tool from directly
ranking cold LazyItem activation during the fling.

An independently captured Debug platform trace contained 133 `doFrame`, 127 `RV Scroll`, 125
`RV Prefetch`, 27 holder binds, and 26 each of `VC.DirectRender`, `VC.Compose`, and
`VC.RenderTree`. Every `VC.DirectRender` interval was owned by `RV Scroll`; none was owned by
`RV Prefetch`. The trace is phase-presence evidence only because it is Debug: direct render ranged
from `2.6..7.1 ms` with one `13.7 ms` outlier, composition from `0.8..2.3 ms`, and render-tree work
from `1.4..4.0 ms`. Release Perfetto comparison attributed the remaining cross-engine work to the
unsupported input/traversal and RenderThread domains: representative ViewCompose input/traversal/
RenderThread DrawFrame averages were `1.369/0.945/4.097 ms`, compared with Android Views
`0.561/0.872/3.775 ms` and Compose `0.608/1.039/3.494 ms`.

For this investigation, the diagnostics are **actionable for triage but insufficient for
optimization acceptance**. They changed the decision by rejecting Host recomposition, duplicate
attach, and detached-preparation ownership as the primary pure-scroll explanation, identified the
authored Text/direct-binding path to test for mutation, and handed the unsupported remainder to
Perfetto. The original selected-session path did not identify a correction that passed the Release
frame gate; its forced first frame and inability to follow future cold LazyItem Sessions triggered
Phase 1A instead of another speculative runtime probe.

### Phase 1A armed-capture result

The retained correction adds an explicit future-LazyItem request. It matches only a live exact
parent Host, the `LazyItem` role, and a Session ID above the arm-time floor. The process owns one arm,
waits at most ten monotonic seconds, captures one completed frame, and reports matched, duration
limit, parent ended, superseded, or capture rejected. Registering the matched Session immediately
before its initial frame lets the timing request observe the frame already entering preparation;
it does not manufacture a second structural render. The original selected-session request remains
unchanged.

Two initial two-second arms expired while the operator moved between Studio, ADB, and the device.
That result was operationally deterministic but too short for the supported human workflow, so the
fixed arm window changed to ten seconds and the Studio response timeout to fifteen seconds. Five
correctly targeted captures then matched the `performance.list` Host rather than the still-live
catalog Host. Every capture reported one `LazyItem` frame, `frame_limit`, 68 attempted and retained
clock reads, 34 records, no drops, and no truncation. Frame elapsed values were
`28.705/14.881/6.123/5.532/4.456 ms`; these active-Debug values rank supported work only and are not
Release frame-time evidence. Reconciliation root time was `21.873/9.301/4.020/3.510/3.070 ms`,
while direct-binding sums were `11.608/2.575/1.457/1.258/1.270 ms`. The first cold row ranked a
`Row` reconciliation-self record at `2.825 ms` across three recorded intervals and Text/Surface
direct binding at `2.264/2.206/1.405/1.365 ms`.

The first five otherwise valid arms accidentally targeted the catalog Host; their nodes included
`FlowRow` and `Button`, so their attribution was discarded. This exposed a necessary operating
guard: verify both the Host source candidate and node types before attributing a timing report.

The Phase 1A output was still unable to distinguish a newly created physical holder from a new
logical Session installed over a recycled holder. A bounded process-local opaque physical-container
token was therefore added to the same concrete Debug tooling. Twelve consecutive target arms
matched logical Session IDs `17/21/27/32/37/42/47/52/57/62/68/73` to physical tokens
`5/9/3/8/13/6/11/4/9/2/8/13`. Repeated tokens `9`, `8`, and `13` across different logical IDs prove
physical holder reuse on the real fling. All twelve captures still retained 68 clock reads and the
complete row-tree phase shape. The decision is therefore no longer “create more holders” or “raise
the mounted-tree cache”; it is that a reused holder adopts a tree for a new logical owner and the
ownership-safe path recomposes, reconciles, and rebinds the row while unsupported traversal and
RenderThread work remains outside the finite timer.

For this defect the upgraded diagnostics are **actionable but partial**. They correctly changed the
next decision, correlated source, parent, logical Session, and physical holder without application
keys, and removed the future-Session blind spot. They do not make active Debug durations comparable
to Release, and they still require Perfetto for measure/layout/draw, input, RenderThread, GPU, and
SurfaceFlinger ownership.

### Fixed-clock comparison baseline

The Xiaomi MI 6 remained at CPU `1.4016/1.8048 GHz`, GPU `515 MHz`, fixed exposed `cpubw`/`gpubw`
votes, suspended charging, stopped vendor performance services, and `35 °C` or lower. The exact
target APK SHA-256 was `5c0ea909553bdb7d7fd7d242c8144b44039bff3f8ef3b371aed292ab57cc7755`;
the benchmark APK was `1430a42a222b172fa4eac30f10ae7e0c4c9bfb64dcfd28c7b211f97c5eee4bb7`.

AndroidX 1.4.1 tests root capability with `su root id`. That command does not complete under this
device's Magisk configuration, so the library otherwise uninstalls the target and MIUI rejects the
shell reinstall with `INSTALL_FAILED_USER_RESTRICTED`. The accepted workaround reproduces the
library's own pre-API-34 rooted branch before every individual method: root
`cmd package compile --reset`, an explicit ProfileInstaller `WRITE_SKIP_FILE` broadcast that
returns result `10`, and a target force-stop; instrumentation then disables only the duplicate
library-managed compilation step. Every method therefore begins from the same reset ART state
without changing the target binary or system policy. AndroidX still labels the result
`run-from-apk`, so these are steady-state interaction results rather than clean startup evidence.

Frame values are P50/P95/P99 milliseconds; heap is median peak KiB.

| Action | ViewCompose | Compose | Android Views | Run-P50 CV | Interpretation |
| --- | --- | --- | --- | --- | --- |
| Scroll | `5.615/9.230/10.682`, heap `7724` | `5.592/8.066/9.256`, `7848` | `5.204/6.862/8.256`, `4291` | `0.055/0.089/0.012` | Versus Compose `+0.4%/+14.4%`: `no material change`. Versus Views `+7.9%/+34.5%`: P95 remains `regressed`; heap is `+3433 KiB` (`+80.0%`). |
| Mutation | `4.924/12.092/21.111`, heap `7859` | `6.005/15.771/35.524`, `8776` | `4.922/8.024/9.247`, `5840` | `0.027/0.072/0.088` | Versus Compose `-18.0%/-23.3%`: `improved`. Versus Views `+0.0%/+50.7%`: P95 remains `regressed`. |

All six stability values pass the `0.15` gate. The corrected protocol also exposed why the earlier
unreset exploratory batches could not accept a candidate: the exact original APK moved from
`10.368` to `12.814 ms` mutation P95 and from `8.545` to `9.284 ms` scroll P95 as method order and
JIT history changed. The hard threshold correctly prevented those batches from manufacturing a
favorable result.

### Rejected candidates

The following one-factor probes were reverted:

1. a bounded detached-preparation probe did not materially improve scroll and worsened mutation;
2. cross-owner targeted diffing did not materially change scroll or mutation;
3. flattening the benchmark row reduced heap directionally but changed the frozen row hierarchy
   and improved scroll P95 by only `2.6%`, so it was both insufficient and out of scope;
4. disabling RecyclerView prefetch worsened scroll P95, confirming that staged prefetch remains
   beneficial even though it does not own the captured direct-render slices;
5. increasing the mounted-item cache to 12 worsened scroll P95; and
6. combining direct cross-owner reconciliation with a narrow plain-Text equality guard produced
   mutation `5.300/13.165`, heap `8102`, and scroll `5.862/8.909`, heap `7126`, against an adjacent
   exact-original control of `5.224/12.814`, `7949`, and `5.813/9.284`, `6971`. The normalized
   changes were mutation `+1.5%/+2.7%/+1.9%` and scroll `+0.8%/-4.0%/+2.2%`, all below the combined
   acceptance thresholds; and
7. removing the inactive `LayoutPassTracker` calls from only the `Surface`/`Box` and `Row`/`Column`
   containers used by the row tested whether old diagnostic branches owned the unsupported
   traversal tail. The Release-like candidate produced scroll `5.087/8.860/9.688 ms`, heap
   `7849 KiB`, against the adjacent exact-original `5.813/9.284 ms` control. P95 changed only
   `-4.6%` (`-0.424 ms`), remained materially behind Android Views, and did not justify weakening
   the published layout-pass diagnostic. The probe and both source edits were reverted.

The closest performance candidate remains `no material change`, not an optimization. Retaining one
would add renderer complexity or weaken diagnostics without closing either Android Views P95 gap.
Every renderer performance probe is reverted. A Maven Changeset is nevertheless required for the
retained Foundation inspection seam and Preview diagnostic behavior.

### Retained diagnostics no-regression acceptance

The exact control is a detached worktree at retained-plan commit `9f245448`. Its target APK
SHA-256 is `b650d46b8bd80a2712a965dcc613b4a11e81dc0046aa6941b439fc1edede1cab`; the candidate target is
`13ef6933a17a3261e51a583c01e5450cf86de30ee0f0321530a81bdf5ba72182`. Both target APKs are
`6,988,419` bytes. The benchmark APK is byte-identical in both worktrees at SHA-256
`1430a42a222b172fa4eac30f10ae7e0c4c9bfb64dcfd28c7b211f97c5eee4bb7` and `40,023,416` bytes.
Every accepted arm used five iterations, the same rooted-reset procedure, fixed Xiaomi clocks and
interconnect votes, stopped vendor performance services, suspended charging, and a temperature no
higher than `36 °C`.

Frame values are P50/P95/P99 milliseconds; heap is median peak KiB.

| Action | Exact control | Retained candidate | Normalized candidate change | Run-P50 CV control/candidate | Interpretation |
| --- | --- | --- | --- | --- | --- |
| Scroll | `4.995/8.331/9.731`, heap `7562` | `4.871/8.294/9.076`, `7955` | `-2.5%/-0.4%/-6.7%`; heap `+393 KiB` (`+5.2%`) | `0.092/0.051` | Timing `no material change`; the earlier adjacent pair had the opposite heap direction, so no repeatable heap regression is assigned. |
| Mutation | `4.492/11.452/16.249`, heap `7879` | `4.309/11.515/14.237`, `7944` | `-4.1%/+0.6%/-12.4%`; heap `+65 KiB` (`+0.8%`) | `0.047/0.120` | `no material change`; neither frame gate is crossed. |

An earlier mutation pair produced control `4.274/9.708/13.450`, heap `7459`, and candidate
`4.339/11.742/20.297`, heap `7570`. Its P95 appeared to regress by `20.9%` and `2.034 ms`, so it was
not accepted. The exact control immediately repeated at `4.432/11.202/21.666`, heap `8109`; that
control movement alone was `+15.4%` and `+1.494 ms` at P95. The adjacent candidate repeat was
`4.309/11.515/14.237`, heap `7944`, or `+2.8%` and `+0.313 ms` at P95. The later stable comparison
above independently retained the same candidate result and produced a control within `0.063 ms` at
P95. This preserves the rejected batch instead of averaging away a false alert.

Conclusion: the inactive Foundation seam and downstream armed tooling cause `no material change`
in the two ViewCompose paths and pass the no-regression gate. The comparison is release-safety
evidence for a diagnostic change, not a performance-correction matrix; Compose and Android Views
were not rerun because neither their code nor the frozen workload changed. Their accepted same-run
matrix continues to own the residual cross-engine gap. This limitation keeps the plan active and
does not satisfy Phase 4 performance success.

### Repository and device acceptance

Focused Foundation, Preview, and standalone Studio protocol/model tests pass. Documentation
structure, language, translation, Governance V2, documentation scripts, release intent, dependency
and boundary checks pass. `verifyDevelopmentToolingIsolation` and the optimized Release APK scan
pass; the Release artifact excludes the concrete future-arm request and physical-container token
markers. `qaQuick`, `qaPreview`, and `qaRelease` pass in one invocation with 2,414 tasks considered.

`qaFull` was bound explicitly to Xiaomi serial `e5e70042`. Its first connected attempt exposed a
MIUI confirmation dialog saying that ViewCompose was trying to start the test package; the dialog
caused an empty `AnimationBoundsDeviceTest` failure and then blocked later installs. After explicitly
allowing that device action, the failed method passed alone in `2.62 s`, and the complete main App
suite passed with 153 executed tests, zero failures, and three credential/device-condition skips.
MIUI then intermittently rejected the UTP install sessions for the Counter and Tutorials packages
with `INSTALL_FAILED_USER_RESTRICTED` or `Connection refused`. Root preinstallation of the identical
APKs allowed the unchanged Gradle connected tasks to pass independently: Counter `1/1` and
Tutorials `2/2`. A final aggregate retry again failed before running tests because MIUI rejected all
three UTP install sessions; it did not produce a test assertion failure.

The connected evidence is accepted as passing constituent suites with a scoped device-environment
exception, not as a green aggregate `qaFull` invocation. Owner: this active performance plan. Before
archival, rerun the aggregate on a device with persistent `Install via USB` permission or a managed
CI device. The Xiaomi CPU/GPU/interconnect governors, charging input, vendor performance services,
original Magisk entry point, and trace-tool modes were restored after benchmarking; no temporary
APK, shell wrapper, or fixed-clock state remains.

## Maven release changesets

- `release/changes/20260827-armed-lazy-item-diagnostics.json`

## Release intent rationale

The retained implementation changes the published Q3 Foundation inspection policy and the optional
Preview artifact, so the immutable Changeset classifies UI Foundation as breaking and Preview as a
feature. Concrete request parsing, arm ownership, physical-container correlation, report writing,
and Studio controls remain downstream in Preview/Studio. Foundation retains only an optional
policy branch and performs no recurring observation or per-node timing without an explicit request.

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

### Phase 1A: evidence-triggered diagnostic escalation

This phase is now required. Phase 1 and six one-factor probes narrowed the work to cold LazyItem
activation but could not observe the future logical Session that owns it. Repeating the same
selected-session capture would add samples without removing that blind spot, so another
optimization candidate must not be accepted before this seam is available or independently proven
unnecessary.

1. Preserve the current selected-session capture as the backward-compatible default. Add a
   separately explicit interaction-armed request that waits for a future Session instead of
   requesting an immediate structural render.
2. Match only privacy-safe framework identity: an exact live parent Session plus the expected
   `LazyItem` child role and a Session created after the request. Do not accept, retain, serialize,
   hash, or expose an application item key, node content, callback, native object, or source string
   as a selector.
3. Own at most one armed request per process. Give it a request nonce, a ten-monotonic-second
   deadline, a one-completed-frame ceiling after attachment, and terminal outcomes for
   matched, timed out, parent ended, superseded, or process disposed. Registration and timing work
   exist only while an explicit debuggable-process request is armed.
4. Ensure the request can attach before the matched Session's first supported render phase. A
   post-commit discovery callback is insufficient because it would systematically miss the cold
   frame under investigation.
5. Keep Preview as the concrete application-process owner under ADR-0009. The runtime-facing seam
   may be a no-op optional hook, but the inactive and Release-excluded paths must not add recurring
   observers, Session scans, per-node clocks, allocations, or application-key retention.
6. Return enough bounded state to distinguish `armed`, the opaque matched Session identity, capture
   completion, and every terminal reason. Preserve stale-nonce, foreground-package, byte-budget,
   record/drop/truncation, and fail-closed disposal rules.
7. Add focused tests for pre-first-frame attachment, unrelated/old child rejection, exact-parent
   matching, timeout, parent disposal, supersession, selected-session backward compatibility, idle
   zero work, and Release-classpath exclusion. If the smallest correct implementation changes a
   public/protected contract, stop first to add its stable capability owner, structured impact
   dispositions, Q level, canonical-English KDoc, Q3 compiled sample, generated Reference, module
   manual, reviewed Chinese mirror, and immutable Maven Changeset.
8. Re-run the unchanged fling. The upgrade is useful for this defect only if it captures a future
   cold LazyItem Session without a synthetic first frame and changes the next source-level decision;
   otherwise record it as still insufficient and escalate to a platform/holder correlation design
   instead of testing more speculative runtime changes.

Exit condition: a bounded capture either ranks the first supported phases of the future cold
LazyItem Session and names the next owned hypothesis, or produces a tested fail-closed result that
proves this diagnostic design cannot observe the material owner. Only the first outcome authorizes
another performance candidate in this phase.

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
| Diagnostics bounds | selected-session capture remains at most eight frames/two seconds; future-LazyItem arm is at most one frame/ten seconds; raw clock overhead, attempted/retained reads, drops, truncation, strings/depth/response limits, unsupported domains |
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
