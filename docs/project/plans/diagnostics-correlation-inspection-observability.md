# Diagnostics Correlation, Inspection, and Production Observability Plan

## Status

Active. Phases 0 through 4 are complete; inspector and documentation convergence is next.
This plan was split out on 2026-08-18 from the remaining diagnostics candidates in the unified
roadmap, the Diagnostics guide, and Phase 3 of the Performance specification. Those documents now
point here; this file is the only active plan that owns cross-session diagnostic correlation,
production failure aggregation, real View-boundary highlighting, and per-node timing.

[ADR-0021](../../architecture/decisions/0021-correlated-render-diagnostics-ownership.md) freezes one
process-local trace and parent graph for Host, Preview, navigation, lazy, pager, and overlay
sessions; one event sink; the alpha-line hard removal of the three legacy callbacks and their
result-only Local; the optional `viewcompose-diagnostics` artifact; privacy-safe production
fingerprinting; request-driven highlighting; finite timing domains; and absolute request,
cardinality, and response budgets. The worktree audit found the existing listener propagation
semantically incomplete and capable of retaining an old result listener when a later delayed-child
snapshot omits it, so Phase 1 must hard-cut the model rather than add another adapter.

The Demo benchmark and verification harness plan continues to own its scenario and benchmark
infrastructure. It does not own the diagnostics contracts or features defined here. Historical
documents under `docs/archive/` remain evidence only and are not rewritten as current status.

Last verified: 2026-08-24.

Next action: begin Phase 5 inspector and documentation convergence over the correlated session,
frame, failure, highlight, and timing results. Phase 4 completed the finite requested controller,
composition/reconciliation/binding timing records, inclusive/self semantics, caps, truncation and
overhead reporting, deterministic fixtures, and Studio/Demo presentation while preserving exactly
zero per-node clock reads when inactive.

## Maven release changesets

- `release/changes/20260823-correlated-render-diagnostics-phase1.json`
- `release/changes/20260823-bounded-render-failure-aggregation.json`
- `release/changes/20260823-request-driven-node-highlighting.json`
- `release/changes/20260824-sampled-node-timing.json`

## Objective

Turn the existing single-session diagnostic snapshots into a bounded observability chain that can
answer four practical questions without making ordinary rendering pay for an inactive tool:

1. which root, navigation destination, lazy item, pager page, overlay, or preview session produced
   a frame, patch, failure, or timing record;
2. which recurring structured failures affect production and how often they occur without retaining
   arbitrary application data or unbounded exception history;
3. which mounted Android View corresponds to a selected declarative node and what its current
   on-screen bounds are; and
4. which composition, reconciliation, binding, or explicitly supported layout work dominates one
   requested diagnostic sample.

The completed work must preserve transactional rendering, weak View ownership, development-tooling
isolation, release-classpath separation, deterministic failure reporting, and the current behavior
when no diagnostic consumer is active.

## Planning origin and ownership transfer

| Previous active location | Previous responsibility | Status after this split |
| --- | --- | --- |
| [Unified roadmap](../roadmap.md), Runtime Effects / Transactions | Listed production diagnostic aggregation and exception sampling as a future focus | Superseded for execution tracking. The roadmap retains a summary and links to this plan. |
| [Unified roadmap](../roadmap.md), Diagnostics and Milestone D | Listed node highlighting, cross-session correlation, per-node timing, and a panel for high-frequency problems | Superseded for execution tracking. This plan owns the remaining diagnostics scope; baseline-profile work remains in the performance roadmap. |
| [Diagnostics guide](../../tooling/diagnostics.md) | Recorded the three missing inspector capabilities | The baseline remains authoritative; delivery status and API work move here. |
| [Performance specification](../../tooling/performance.md), Phase 3 | Recorded the remaining diagnostics performance work | The performance budgets remain authoritative; execution status moves here. |
| `docs/archive/` | Historical diagnostics and Demo evidence | Unchanged. Archived pages never carry current status. |

No other active plan owns these four capabilities. A prerequisite discovered in another module may
remain in its owning plan only when it is independently useful; this plan continues to own the
diagnostic contract, end-to-end correlation, aggregation, tooling behavior, inspector experience,
and acceptance evidence.

## Delivery order and planning estimate

The order is intentional. Correlation identity is a prerequisite for trustworthy failure and timing
attribution. Production aggregation then delivers high troubleshooting value with limited hot-path
change. Highlighting is request-driven and reuses the identity chain. Per-node timing is last because
it has the greatest risk of perturbing the work being measured.

| Phase group | Relative difficulty | Primary benefit | Planning estimate for one engineer |
| --- | --- | --- | --- |
| Contract and budget freeze | Medium | Prevents incompatible IDs, privacy leaks, and ambiguous timing | 0.5--1 week |
| Cross-session correlation | Medium-high | Gives every later result a trustworthy owner and parent chain | 1--1.5 weeks |
| Bounded production failure aggregation | Medium | Makes recurring production failures countable and actionable | 0.5--1 week |
| Request-driven node highlighting | Medium | Maps a declarative node to its real View boundary quickly | 1--1.5 weeks |
| Sampled per-node timing | High | Identifies expensive composition, reconciliation, and binding work | 1.5--2.5 weeks |
| Inspector, documentation, device, and release closeout | Medium-high | Makes the contracts usable and proves inactive-path safety | 1.5 weeks |

The total planning range is approximately 6--9 engineering weeks. It excludes integration with a
specific external telemetry vendor, server-side storage, dashboards outside the repository, and a
general-purpose live layout inspector.

## Target architecture

### Diagnostic correlation envelope

ADR-0021 freezes a process-local, opaque identity model capable of representing root and delayed
child sessions without exposing internal object identity or a persistable compatibility key:

```kotlin
@JvmInline
value class RenderSessionTraceId internal constructor(
    val value: Long,
)

data class RenderDiagnosticContext(
    val sessionId: RenderSessionTraceId,
    val parentSessionId: RenderSessionTraceId?,
    val frameId: Long?,
    val role: RenderSessionRole,
    val eventSequence: Long,
    val monotonicTimestampNanos: Long,
)

sealed interface RenderDiagnosticEvent {
    val context: RenderDiagnosticContext
}

fun interface RenderDiagnosticsSink {
    fun onEvent(event: RenderDiagnosticEvent)
}
```

This is the approved Phase 1 contract. The existing `onRenderStats`, `onRenderResult`, and
`onRenderFailure` callbacks, the result-only Local, and the separate source-role marker are removed
through the alpha-line hard-cut policy. Host, Preview, navigation destination, lazy item, pager
page, and overlay surface have distinct roles and one Local-snapshot parent propagation model.

The trace identifier is valid only for one process lifetime. It must not be saved, used as an
application key, transmitted as stable user identity, or compared across process recreation. A
reused physical View tree receives the logical session identity of its current owner; it never
carries the previous logical session's trace identity across keys.

### Bounded production failure aggregation

The production path consumes structured `RenderFailure` events plus the correlation context. It
groups them through the ADR-0021 privacy-safe fingerprint: phase, recovery, optional operation,
exception binary type, and at most three class/method-only `com.viewcompose.*` stack frames. The
default capacity is 64, the hard maximum is 128, the default monotonic window is 15 minutes, and
the valid window range is one minute through 24 hours.

The aggregator must:

1. remain explicitly installed and application-owned;
2. retain immutable summaries rather than the original `Throwable`;
3. exclude raw application keys, messages, arbitrary `toString()` output, Local values, View text,
   media, URLs, credentials, and complete stack traces by default;
4. bound distinct fingerprints and samples through deterministic eviction;
5. distinguish synchronous frame failures, post-commit failures, asynchronous effect failures,
   diagnostics-callback failures, and session disposal failures;
6. expose counts, first/last monotonic time, most recent safe context, recovery result, and dropped
   record count; and
7. delegate persistence, network upload, consent, account association, and vendor SDK behavior to an
   application or a separately approved optional adapter.

The accepted optional `viewcompose-diagnostics` artifact owns the aggregator and neutral sink
helpers and depends on `viewcompose-ui-foundation`. UI Foundation owns only the minimum neutral
event/context contract needed by render sessions; it cannot gain storage, transport, vendor SDK,
or process-global reporting policy.

### Request-driven node highlighting

Highlighting belongs to the optional debug tooling path. A valid request selects one active session
and one node token from a captured diagnostic tree. On the Android main thread, tooling resolves the
currently mounted View through weak state, snapshots its visible bounds and clipping context, and
installs a bounded highlight overlay. The response echoes a nonce and reports selected, missing,
stale, recycled, hidden, clipped, or unsupported state explicitly.

The implementation must:

1. activate only when the optional tooling artifact is present, the process is debuggable, and an
   explicit valid request is active;
2. avoid recurring layout, scroll, draw, touch, frame, or recomposition listeners;
3. remove the overlay on replacement, timeout, session disposal, View release, process stop, or
   explicit clear;
4. never retain a View, Activity, Fragment, window, or mounted tree strongly beyond the request;
5. distinguish logical nodes from synthetic renderer infrastructure and explain unsupported nodes;
6. support Studio selection and a deterministic Demo/debug fixture without placing tooling code on
   the release runtime classpath; and
7. avoid recomposition, application callback invocation, focus changes, accessibility focus, input
   interception, or mutation of application layout parameters.

### Sampled per-node timing

Per-node timing is an explicitly requested, finite diagnostic capture. ADR-0021 separates
composition-scope inclusive/self time, reconciliation-decision inclusive/self time, and direct
renderer binding/patch time. Android measure/layout, draw, GPU, RenderThread, SurfaceFlinger,
decode, network, database, and external SDK time are unsupported in the first release rather than
being combined into one ambiguous duration.

Each timing record must state whether it is inclusive or exclusive, its phase, clock, frame/session
context, node token, repetition count, and truncation/dropped-record state. Parent time cannot be
presented as additive to already included child time. Unsupported asynchronous work, GPU time,
draw-time cost, image decoding, network work, and external SDK work must not be mislabeled as node
render time.

Timing collection must:

1. perform zero per-node clock reads while inactive;
2. run for a bounded requested frame/sample count and stop automatically;
3. cap nodes, depth, strings, history, and serialized response size;
4. use a monotonic clock and retain raw nanoseconds while presenting appropriately rounded values;
5. keep diagnostic aggregation outside the measured inner interval where practical;
6. report instrumentation overhead and incomplete/truncated samples; and
7. validate measurement repeatability with deterministic synthetic fixtures before making an
   optimization claim.

## Module ownership

ADR-0021 accepts the following ownership:

| Layer or artifact | Allowed ownership |
| --- | --- |
| `viewcompose-runtime` | Bounded composition-scope diagnostics and optional timing hooks only; no Android, session transport, storage, or inspector UI |
| `viewcompose-ui-foundation` | Opaque process-local session context, neutral event/sink port, lifecycle publication, and no compatibility adapters for the removed callbacks |
| `viewcompose-renderer-android` | Opt-in node timing hooks and weak mounted-View lookup behind neutral renderer contracts; no IDE protocol or report writer |
| `viewcompose-host-android` | Neutral installation/discovery and Android-thread handoff only; no concrete tooling lifecycle, history, or transport |
| Optional `viewcompose-diagnostics` | Bounded production failure aggregation and application-owned sink helpers, with no vendor dependency |
| `viewcompose-preview` | Debug-only request protocol, live-session snapshot, node resolution, highlight overlay, and bounded response serialization |
| Android Studio plugin | Request creation, nonce validation, session/tree selection, highlight controls, timing visualization, source navigation, timeout, and stale-response handling |
| Demo and benchmark modules | Deterministic fixtures, UI validation points, performance probes, and accepted evidence; no canonical public contract ownership |

## Scope

### Correlation and lifecycle

- allocate one opaque trace identity per logical render session;
- attach parent identity and role when a child session is created;
- propagate context to tree, patch, composition, timing, frame, and failure events;
- publish session start, activation/visibility changes where required, and one terminal event;
- keep navigation retention, lazy/pager reuse, overlay surfaces, prepared-but-uncommitted frames, and
  process recreation semantically distinct; and
- tolerate missing optional sinks and sink failure without changing render success or recovery.

### Aggregation and export

- deterministic fingerprinting, deduplication, bounded counters, eviction, and reset/snapshot APIs;
- safe classification for callback, render, commit, asynchronous effect, and cleanup failures;
- application-controlled installation and export with explicit threading and backpressure behavior;
- tests for high-cardinality exception streams, recursive sink failure, concurrent publication,
  disposal, and process-local reset; and
- a vendor-neutral sample that demonstrates forwarding sanitized aggregates without adding a
  production network SDK.

### Tooling and inspector

- session tree and recent-frame correlation view;
- select-node and clear-highlight actions with structured result states;
- finite timing capture with phase filters and a top-cost view;
- navigation from node, patch, timing, or failure to the same session/source context when available;
- explicit indicators for stale sessions, truncated data, unsupported nodes, missing source mapping,
  inactive timing, and dropped aggregate records; and
- stable automation roles and deterministic fake data for Demo and Studio-plugin tests.

## Non-goals

This plan does not:

- turn diagnostics on continuously in every debug or release session;
- build a full Android Studio Layout Inspector replacement;
- stream every frame, View hierarchy, screenshot, input event, or application State value;
- attribute GPU, RenderThread, SurfaceFlinger, network, database, image decoding, or third-party SDK
  time to a declarative node without a separately verified boundary;
- retain arbitrary exceptions, application keys, user text, Local values, URLs, credentials, or
  full application stack traces;
- choose a telemetry vendor, upload endpoint, storage schema, privacy consent model, account key, or
  alerting policy for applications;
- make session trace identifiers stable across process death or suitable for business analytics;
- add an always-on process singleton, worker, database, file writer, broadcast receiver, or View
  listener to core runtime artifacts; or
- claim a performance improvement solely because a timing panel exists. Optimization work still
  requires same-build benchmark evidence and an owning implementation plan.

## Current baseline

Verified from the worktree on 2026-08-18:

1. `RenderTreeResult` already exposes aggregate stats, tree structure, bounded patch records,
   warnings, recomposition decisions, and Local summaries when diagnostics are enabled.
2. `RenderFailure` already exposes frame identity within one session, phase, recovery, cause,
   optional operation, and optional node key. `RenderFrameReport` groups synchronous frame failures.
3. `ViewNodeToolingRegistry` weakly associates a mounted Android View with source metadata only when
   tooling metadata exists.
4. The Demo stores up to twelve recent render snapshots in one process-global history, but those
   snapshots do not identify their producing render session.
5. Frame identifiers are monotonic only within one `RenderSession`; public diagnostic snapshots and
   failures do not carry an end-to-end parent/session correlation envelope.
6. Existing platform tracing covers coarse composition, render-tree, and observed-property phases;
   no public per-node timing contract exists.
7. Development tooling is request-driven under ADR-0009. The documented locator regression proved
   that recurring debug callbacks can approximately double median frame CPU on the reference
   device, so inactive-path safety is a release condition rather than an optimization preference.
8. The roadmap, Diagnostics guide, and Performance Phase 3 previously carried the remaining work as
   candidates. They now delegate execution ownership to this plan.

## Locked architectural rules

1. Inactive diagnostics perform no per-node clock reads, event-history allocation, View traversal,
   report serialization, file/network I/O, or recurring listener registration.
2. Debug inspection requires optional tooling artifact presence, a debuggable process, and an
   explicit nonce-bearing request. A debuggable flag alone never activates live inspection.
3. Production failure aggregation is separately opt-in, bounded, vendor-neutral, privacy-safe, and
   incapable of activating tree, highlight, or timing capture.
4. Runtime and renderer modules expose neutral ports only. Concrete protocols, overlays, histories,
   report writers, and Studio behavior remain downstream.
5. Diagnostic sink, aggregator, serialization, overlay, and plugin failures are isolated and cannot
   fail rendering, suppress the original `RenderFailure`, or change transaction recovery.
6. Logical session identity never follows a recycled physical tree across application keys.
7. Session identifiers and node tokens are process-local tooling identities, not persistence,
   saveable-state, analytics, accessibility, or application identity.
8. Highlighting is read-only with respect to application layout, state, focus, accessibility focus,
   input, and callbacks; only the tooling-owned overlay is mutated.
9. Timing output names its phase and inclusion semantics and reports truncation and instrumentation
   overhead. It never combines incompatible clocks or implies unsupported causal attribution.
10. Every public or protected API receives a Q level, complete applicable contract fields,
    canonical-English KDoc, compiled Q3 samples, owning-module documentation, Chinese public-page
    mirrors, binary/API validation, and immutable release Changesets.

## Execution plan

| Phase | Status | Deliverable | Exit gate |
| --- | --- | --- | --- |
| 0. Contract, privacy, and budget freeze | Complete in ADR-0021 | Session-role graph; process-local ID and event envelope; hard callback removal; module/artifact names; failure fingerprint/redaction/capacity policy; highlight protocol; timing domains; inactive/request budgets; deterministic fixtures | The accepted ADR covers every current session creator and callback, assigns Q levels, proves dependency direction, records explicit non-applicable fields, and freezes absolute limits |
| 1. Cross-session correlation | Complete | Session/context lifecycle, parent propagation, event ordering, legacy callback hard removal, navigation/lazy/pager/overlay/preview coverage, and bounded correlation fixtures | Every emitted frame/tree/patch/failure can be attributed to one live or terminal logical session; reuse and recreation cannot inherit stale identity |
| 2. Bounded production failure aggregation | Complete | Optional vendor-neutral aggregator, safe fingerprints, counts/windows/eviction/drop reporting, application sink sample, concurrency/failure isolation, and release-path tests | High-cardinality and recursive-failure tests remain bounded; raw application data and retained Throwables are absent; disabled aggregation has no recurring work |
| 3. Request-driven node highlighting | Complete | Node token resolution, weak mounted-View lookup, bounds/clipping snapshot, debug overlay lifecycle, request/response states, Demo fixture, and Studio controls | Valid, stale, recycled, hidden, unsupported, timeout, replacement, and disposal cases pass with zero idle listeners and no release tooling classpath |
| 4. Sampled per-node timing | Complete | Finite capture controller, composition/reconciliation/binding timing records, inclusive/self semantics, caps/truncation, top-cost summaries, synthetic calibration, and Studio/Demo presentation | Inactive path performs zero node clock reads; requested samples stop automatically, remain bounded, report overhead, and reproduce known fixture ordering within the accepted tolerance |
| 5. Inspector and documentation convergence | Not started | Correlated session/frame/failure/timing inspector, source navigation, stable automation roles, module manuals, API references, tutorials, troubleshooting and privacy guidance, Chinese mirrors, and migration notes | Demo and plugin tests, compiled samples, documentation/localization, API compatibility, and consumer-build gates pass |
| 6. Performance, device, and release closeout | Not started | Same-device debug regression, request-cost characterization, release-classpath proof, leak/lifecycle matrix, final Changesets, Maven consumer verification, and durable conclusions | Idle P50/P95 and zero-write budgets pass; request cost is bounded and documented; no accepted leak, privacy, correctness, or release regression remains |

## Acceptance matrix

| Scenario | Required evidence |
| --- | --- |
| Root and retained navigation | Parent/session identity is stable while retained, terminal on permanent release, and new after process recreation |
| Lazy and pager reuse | Logical identity follows the item/page key; recycled mounted trees never retain an earlier session token |
| Overlay and independent surfaces | Parent chain is explicit and one surface cannot consume another session's frames or failures |
| Failed and rolled-back frames | Correlation records the attempted frame and recovery without publishing a failed candidate as committed |
| Asynchronous and disposal failure | Safe aggregates distinguish work outside a completed synchronous frame and continue cleanup |
| High-cardinality production failures | Capacity, eviction, dropped counts, redaction, and snapshot/reset behavior remain deterministic |
| Sink or tooling failure | Rendering and original failure publication continue; recursion is bounded and classified separately |
| Highlight selection | Real bounds, clipping, hidden/stale/recycled states, replacement, timeout, and cleanup are correct |
| Timing capture | Phase definitions, parent/child inclusion, caps, auto-stop, overhead, and known synthetic cost ordering are correct |
| Inactive application path | No node clocks, histories, inspection traversals, serialization, report writes, or recurring View callbacks occur |
| Release isolation | Concrete preview/highlight/Studio tooling is absent; only explicitly installed production aggregation remains |
| Privacy | Default output contains no raw key, text, Local value, message, URL, credential, full stack, or persistent user/session identity |

## Performance and cardinality gates

The ADR-0009 debug-tooling gate remains authoritative. For the same device, build, workload,
refresh rate, power mode, and thermal state:

- idle P50 frame CPU fails only when it regresses by more than both 5% and 0.3 ms;
- idle P95 fails only when it regresses by more than both 10% and 0.8 ms;
- idle scrolling performs exactly zero tooling report writes;
- inactive per-node timing performs exactly zero per-node clock reads; and
- one explicit request is measured separately and cannot be amortized into idle results.

ADR-0021 freezes absolute limits for active sessions, event records, failure fingerprints, source
records, highlighted nodes, timing frames, timed nodes, strings, depth, serialized bytes, and
request duration. The owning implementation phase must reach every limit and prove deterministic
truncation or eviction.

## Verification commands

ADR-0021 freezes the Phase 1 targeted module tasks. The completed plan must include at least:

```bash
./gradlew verifyDevelopmentToolingIsolation
./gradlew verifyModuleDependencyBoundaries
./gradlew verifyDocumentationStructure
./gradlew qaQuick
./gradlew qaPreview
./gradlew qaFull
```

It also requires the repository's same-device debug-tooling comparison, targeted renderer/runtime
tests, Studio-plugin protocol/UI tests, release APK classpath inspection, leak/lifecycle tests, and
Maven consumer verification for every changed published artifact. Accepted timing or performance
evidence must be interpreted in active documentation with comparison context, absolute results,
normalized change, conclusion, limitations, and next action.

## API and documentation impact

ADR-0021 assigns the configuration, sink/event family, host overloads, aggregator, highlight, and
timing request/result families Q3 because they establish lifecycle, threading, privacy,
boundedness, failure, compatibility, and ownership behavior. Immutable identity, role, context,
collection level, fingerprint, aggregate, node token, and timing values are Q2. Its API table records
every applicable contract field and explicitly non-applicable Android, I/O, persistence,
restoration, cancellation, or attribution field before source is added.

Production work must update the architecture overview, render-failure architecture, effects
architecture where asynchronous failure context changes, ADR-0009 if its activation contract
changes, Diagnostics guide, Performance specification, unified roadmap, relevant module manuals,
Studio tooling documentation, tutorials, API references, Demo verification, privacy guidance, and
all required Simplified Chinese mirrors.

The first publication-relevant production-source or publication-input change must add immutable
`release/changes/<unique>.json` entries for every directly affected artifact. This section must then
replace `- None.` with the exact filenames.

## Completion criteria

This plan is complete only when:

1. every supported frame, tree, patch, failure, highlight, and timing result carries trustworthy
   process-local session attribution;
2. production failure aggregation is opt-in, bounded, redacted, vendor-neutral, failure-isolated,
   and incapable of activating debug inspection;
3. node highlighting is accurate, request-driven, weakly owned, automatically cleaned up, and
   absent from the release tooling classpath;
4. per-node timing is finite, phase-specific, bounded, repeatable enough for diagnosis, transparent
   about overhead, and completely inactive without a request;
5. the complete acceptance, privacy, lifecycle, device, performance, leak, documentation,
   localization, API, Changeset, and Maven consumer gates pass;
6. accepted results and limitations are interpreted in active owning documentation rather than left
   only as raw logs; and
7. the roadmap, Diagnostics guide, and Performance specification describe shipped behavior, after
   which this plan moves to `docs/archive/` and no active page still presents it as pending.

## Evidence ledger

| Date | Evidence | Result | Interpretation / next action |
| --- | --- | --- | --- |
| 2026-08-18 | Worktree, active-document, and current diagnostics-contract review | Planning baseline established | Structured single-session snapshots, failures, coarse traces, and weak View/source mapping exist. Cross-session identity, bounded production aggregation, real View highlighting, and per-node timing do not. Complete Phase 0 before adding source. |
| 2026-08-23 | CodeGraph impact review, every production `RenderSession` creator, callback/Local propagation, ADR-0009 budgets, source-tooling limits, module dependency direction, and API documentation fields | Phase 0 accepted in ADR-0021 | The three callback paths are incomplete and the delayed result Local can retain an old observer. Hard-cut them to one correlated event sink in Phase 1; retain zero detailed collection below the selected level and no compatibility adapter. |
| 2026-08-23 | Phase 1 implementation and same-worktree acceptance: 379 UI Foundation, 45 Host Android, 23 Preview, and 12 Preview Runner unit tests passed; Studio plugin ran 115 tests with 113 passed and 2 skipped; `qaQuick` passed all 1,622 tasks with 216 executed and 1,406 up-to-date; two Xiaomi MI 6 / Android 9 device tests passed for transactional failure isolation and diagnostics-page refresh; documentation, translation, release-intent, and development-tooling-isolation gates passed; the complete site passed at 48,217,723 non-API bytes after consolidating 131,925 bytes of repeated evidence and guidance without raising its 46 MiB budget | Phase 1 improved diagnostic ownership and completed the hard cut. All six frozen roles emit without aliasing; root and nested sessions carry process-local parent correlation; lifecycle, frame, and failure events have deterministic ordering; `None` performs zero platform clock reads; `Stats` builds zero diagnostic trees; and production Kotlin source has zero references to the three removed callbacks or two removed source-session markers | Comparison is the pre-cut callback/result-Local/source-marker implementation and the new implementation at the same source revision and toolchain. The accepted evidence proves semantics, bounded collection selection, inactive clock behavior, API/sample migration, and one-device Android integration; it does not claim frame-time improvement, production aggregation, highlighting, or per-node timing. Visual layout is intentionally unchanged, one Android 9 device is not a compatibility matrix, and full idle P50/P95, leak, release-classpath, and request-cost evidence remains owned by Phase 6. Start Phase 2 with the optional aggregator artifact. |
| 2026-08-23 | Phase 2 same-worktree acceptance: the new Diagnostics module passed 14/14 focused tests in 0.069 seconds; the 1,000-fingerprint case retained exactly 128, reported 872 dropped observations and 872 evictions, and preserved deterministic update order; eight concurrent publishers retained all 8,000 same-fingerprint observations; ignored events performed zero clock reads. Strict Dokka, package/namespace/layer, development-tooling-isolation, dependency, publication, release-intent, documentation, and 109-page Chinese translation gates passed. Selective local Maven publication validated the new AAR, POM, sources, documentation, and public Foundation edge. Final `qaQuick` passed all 1,670 tasks with 158 executed and 1,512 up-to-date. | Functional observability improved: recurring failures can now be counted through an explicitly installed, vendor-neutral artifact while retained output contains no exception message, cause, application frame, file/line, key, or `Throwable`. Capacity, windows, count saturation, reset, concurrent publication, export failure, disposal, and process-local replacement remain bounded. The inactive application path has no new runtime dependency or recurring work; an installed aggregator also ignores non-failure events before a clock read. | Comparison is the Phase 1 tree with no production aggregator and the Phase 2 optional artifact under the same source toolchain. Absolute unit, cardinality, concurrency, API, documentation, and Maven results substantiate behavior but not frame-time or power; the performance conclusion is therefore inconclusive rather than an improvement claim. The module owns no Android UI, so no visual Demo or device geometry case applies in this phase. Application queue/backpressure, storage, consent, upload, and vendor behavior remain deliberately untested application responsibilities. Begin Phase 3 request-driven highlighting; Phase 6 still owns same-device idle/request cost and release-classpath measurements. |
| 2026-08-23 | Phase 2 documentation-site closeout on the complete 100-version corpus plus one unpublished current tree: Phase 1 baseline 48,217,723 bytes; initial candidate 48,480,209 bytes; consolidated candidate 47,801,356 bytes; English/Chinese search 5,432,660/5,916,011 bytes; 432-page accessibility, compatibility anchors, API routing, and all budgets passed in 28.7 seconds. | Representation improved. Consolidation removed 678,853 bytes from the failed candidate and leaves 433,140 bytes below the unchanged 46 MiB ceiling while retaining current decision metrics and archived execution links. | The initial candidate was 262,486 bytes (+0.544%) above Phase 1 and 245,713 bytes over budget; final output is 416,367 bytes (-0.863%) below Phase 1. The comparison reused one generated API corpus on local macOS and does not cover deployment or another host. Keep the compact decision ledger as Phase 3 adds highlighting guidance. |
| 2026-08-23 | Phase 3 same-worktree acceptance: 382 UI Foundation, 47 Host Android, 26 Preview, and 46 Demo unit tests passed; the Studio plugin ran 117 tests with 115 passed and 2 skipped; strict API documentation, `qaPreview` (1,115 tasks: 232 executed, 883 up-to-date), and final `qaQuick` plus development-tooling isolation (1,670 tasks: 209 executed, 1,461 up-to-date) passed. On one rooted Xiaomi MI 6 / Android 9, the source report exposed the Host plus live and inactive LazyItem sessions without capturing LazyItem source stacks; a generation-1 AndroidView node resolved to the exact 936 x 264 px visible bounds, selected and timed out after five seconds, became stale after mounted-target replacement, then generation 2 selected and cleared successfully. The tooling overlay was visually inspected on-device and matched the real child-owned View boundary. | Functional troubleshooting improved. A selected declarative node now reaches its real mounted Android View through request-only inspection, weak ownership, clipping-aware geometry, bounded overlay lifetime, nonce/generation validation, and Studio select/clear actions. The device-discovered coupling between session registration and source capture was hard-cut into `RenderSessionInspectionPolicy`: Host/navigation/pager may capture sources, while LazyItem/overlay/preview sessions remain inspectable without source-stack capture. Ignored sessions allocate no inspection state, tracked sessions install no idle traversal, listener, serialization, or report loop, and tooling failures remain isolated from rendering. | Comparison is the Phase 2 tree, where no real View-boundary highlight existed and the source-only port silently omitted LazyItem inspection. The conclusion is improved for functional diagnosis, but performance remains inconclusive: no frame-time or power claim is made from unit tests or visual inspection. One Android 9 phone and one viewport are not a compatibility matrix; hidden, clipped, recycled, disposal, and unsupported cases are primarily deterministic test evidence, while the device run directly covered select, timeout, replacement/stale, reselection, and clear. Phase 6 retains same-device idle P50/P95, explicit request-cost, leak, and release-classpath proof. Begin Phase 4 finite per-node timing. |
| 2026-08-24 | Phase 4 same-worktree acceptance: 1,198 affected-module and Demo unit tests passed with no failure; the Studio plugin ran 122 tests with 120 passed and 2 skipped, and verified compatible with all three configured Android Studio 2026.1 builds. Strict Dokka generation passed for all six changed published modules. `qaPreview` passed 1,115 tasks with 247 executed and 868 up-to-date; final `qaQuick` passed 1,670 tasks with 227 executed and 1,443 up-to-date. On one rooted Xiaomi MI 6 / Android 9, a requested renderer-actions LazyItem capture retained all 8 frames and 338 records over 719,057,187 ns, performed 600 attempted and 600 retained timing reads with an 8,125 ns empty-pair overhead, covered composition, reconciliation, and binding with inclusive, self, and direct semantics, reported all 9 deliberately unsupported domains, ended by `frame_limit`, and recorded zero dropped nodes, records, or strings with no truncation. The visible Demo workload advanced from 0/8 to 8/8 frames and the focused device test passed in 0.927 seconds after accepting MIUI's first-run test-package dialog. | Functional diagnosis improved. Compared with Phase 3's zero supported per-node timing phases and records, the same requested workflow now exposes 3 distinct phases and 338 bounded records, with deterministic synthetic tests proving inclusion, self-time, direct-time, fixture ordering, cap, timeout, arbitration, serializer-prefix, and failure-isolation behavior. Inactive-path tests still perform exactly zero per-node clock reads, and one process can own only one finite capture. | The normalized functional change is from 0 to 3 supported phases and from 0 to 338 records for the requested device sample; this is an **improved** diagnostic-capability result. Performance remains **inconclusive** because the 719.057 ms capture duration includes the deliberately animated Demo workload and active instrumentation and is not an idle or frame-time comparison. One Android 9 device, one session, and deterministic unit fixtures do not establish a compatibility or performance distribution; the first root-installed instrumentation run also required one visible MIUI launch authorization. Begin Phase 5 inspector convergence. Phase 6 retains same-device idle P50/P95, request-cost, power, leak/lifecycle, release-classpath, and device-matrix proof. |

## Decision history

1. 2026-08-18 — Implement cross-session correlation first because every later aggregate, highlight,
   and timing record requires reliable ownership.
2. 2026-08-18 — Implement bounded production failure aggregation before visual tooling to capture a
   high troubleshooting return from the existing structured failure path.
3. 2026-08-18 — Keep node highlighting request-driven and downstream of runtime layers.
4. 2026-08-18 — Implement per-node timing last and only as a finite requested sample because its
   instrumentation can perturb the hot path.
5. 2026-08-18 — Preserve ADR-0009 inactive-path and release-isolation rules; convenience cannot
   justify continuous callbacks or reports.
6. 2026-08-18 — Transfer the remaining work from the roadmap, Diagnostics guide, and Performance
   Phase 3 into this single active plan while leaving archive evidence unchanged.
7. 2026-08-23 — Accept ADR-0021 and complete Phase 0. Use one process-local trace identity and six
   explicit session roles; hard-remove the three callbacks, result-only Local, and separate source
   role marker; publish one synchronous failure-isolated event sink; add the optional vendor-neutral
   `viewcompose-diagnostics` artifact only when Phase 2 begins.
8. 2026-08-23 — Freeze the privacy and cardinality boundary before implementation: at most 64
   tooling sessions, 512 recent request events, 128 failure fingerprints, one five-second
   highlight, one eight-frame/two-second timing capture, 512 timing records, and one 256 KiB
   response. Inactive timing performs zero per-node clock reads.
9. 2026-08-23 — Complete Phase 1 as an alpha-line hard cut. One `RenderDiagnostics` sink now owns
   lifecycle, frame, and failure publication; six typed roles and one Local-snapshot parent context
   replace the callback-specific propagation and separate source marker. Keep production aggregation
   out of UI Foundation and begin it only in the optional Phase 2 artifact.
10. 2026-08-23 — Complete Phase 2 in the optional `viewcompose-diagnostics` artifact. Retain only
    immutable safe fingerprints and process-local contexts; use lazy monotonic windows,
    deterministic least-recently-updated eviction, saturated counters, and synchronized
    snapshot/reset. Keep export scheduling, persistence, consent, upload, and vendors outside the
    framework, and proceed to request-driven highlighting without adding a Demo visual surface for
    this non-UI phase.
11. 2026-08-23 — Complete Phase 3 with request-only mounted-node snapshots and a downstream Preview
    overlay. Hard-cut the source-only tooling port after device validation proved that coupling
    registration to source capture excluded LazyItem sessions: one inspection policy now separates
    passive session tracking from bounded source capture, and no compatibility adapter preserves
    the invalid contract. Keep per-node timing out of the implementation until Phase 4.
12. 2026-08-24 — Complete Phase 4 with one request-owned eight-frame/two-second capture, one
    monotonic nanosecond clock, explicit composition/reconciliation/binding semantics, deterministic
    prefix truncation, process-wide arbitration, and bounded Studio/Demo presentation. Preserve the
    hard inactive-path rule of zero per-node clock reads and leave idle/request performance claims
    to Phase 6 rather than interpreting active instrumentation duration as a rendering benchmark.
