---
schema_version: 2
document_id: adr.correlated-render-diagnostics
doc_type: architecture
owner:
  kind: project
  id: diagnostics
version_lane: released
capability_ids:
  - diagnostics.correlated-events
  - diagnostics.session-inspection
  - diagnostics.node-timing
  - diagnostics.failure-aggregation
  - renderer.diagnostics
artifact_ids:
  - viewcompose-ui-foundation
  - viewcompose-renderer-android
  - viewcompose-diagnostics
  - viewcompose-preview
sample_ids:
  - adr.correlated-diagnostics-identity
  - adr.correlated-diagnostics-event-model
invariants:
  - One process-local Session identity and parent graph correlates lifecycle, failure, frame, source, node, and timing evidence.
  - Optional inspection performs no traversal, serialization, recurring observation, or per-node clock reads until an explicit request.
evidence:
  - Correlation, propagation, failure isolation, bounded aggregation, node inspection, timing, release-isolation, and device acceptance suites.
---

# ADR-0021: Correlated render diagnostics ownership

- Status: Accepted
- Date: 2026-08-23
- Related: ADR-0009 development tooling isolation, ADR-0011 prefetched-session activation,
  ADR-0012 lazy logical/physical ownership, and ADR-0015 observed-property transactions

## Context

Diagnostics are split across `onRenderStats`, `onRenderResult`, and `onRenderFailure`. Roots can
install all three, lazy and pager sessions propagate only results, navigation can propagate only
failures, and overlays propagate none. A delayed item may also retain an old result listener after
a Local snapshot omits it, crossing logical ownership. Frame IDs, source-tooling IDs, and
`UiSourceSessionRole` cannot correlate these paths or distinguish physical View reuse from a new
logical owner.

The alpha line permits a hard cut. Adapters would preserve multiple incomplete ownership systems.
ADR-0009 also forbids recurring callbacks, traversal, serialization, writes, or per-node clock
reads while optional tooling is inactive.

## Decision

### Identity and ownership graph

Phase 1 introduces the following public contract:

{/* non-executable sample_id="adr.correlated-diagnostics-identity" reason="This accepted ADR preserves a condensed declaration model and intentionally omits package context and constructor visibility details." visible_explanation="Treat this fence as accepted identity vocabulary; use the versioned API reference for copy-ready declarations." */}
```kotlin
@JvmInline
value class RenderSessionTraceId internal constructor(val value: Long)

enum class RenderSessionRole {
    Host, Preview, NavigationDestination, LazyItem, PagerPage, OverlaySurface,
}

data class RenderDiagnosticContext(
    val sessionId: RenderSessionTraceId,
    val parentSessionId: RenderSessionTraceId?,
    val role: RenderSessionRole,
    val frameId: Long?,
    val eventSequence: Long,
    val monotonicTimestampNanos: Long,
)
```

A trace ID is a non-zero, process-local, monotonically allocated `Long` with an internal
constructor. It is not random, saveable, stable across process recreation, or valid as an
application, analytics, navigation, lazy-item, account, or accessibility identity.
`eventSequence` increases within one session; events across sessions have no total order beyond
their monotonic timestamps. `frameId` remains session-local and is non-null only when attribution
to a synchronous frame is proven; otherwise it is `null`.

| Creator | Role and lifetime | Parent |
| --- | --- | --- |
| Activity, Fragment, low-level public root | `Host`; host creation through disposal | None |
| Static or interactive Preview root | `Preview`; one Preview session | None |
| Navigation candidate or retained destination | `NavigationDestination`; failed candidate ends, retention preserves | Owning `NavHost` |
| Lazy item, sticky header, or grid item | `LazyItem`; follows logical item, not recycled tree | Captured item Local |
| Horizontal or vertical pager page | `PagerPage`; keyed moves preserve, replacement renews | Captured page Local |
| Dialog, popup, or modal surface | `OverlaySurface`; creation through dismissal/disposal | Captured overlay content |

Eager children, tabs, snackbar/toast entries, and synthetic renderer Views remain in their owning
session. A private parent context is captured in immutable Local snapshots and consumed once when a
child session is constructed. A missing context creates a root. A View or container cannot retain
or restore an edge. Explicit root diagnostics start a new tree.

Phase 1 deletes `UiSourceSessionRole` and `UiSourceSessionContainerHandle`. Source tooling uses the
same trace ID, parent, and role for eligible Host, navigation, and pager sessions. Lazy items remain
ineligible for request-independent source-stack capture but are correlated when their root has a
sink.

### Unified event contract and hard cut

{/* non-executable sample_id="adr.correlated-diagnostics-event-model" reason="This accepted ADR condenses the sealed event family and omits the concrete event declarations that complete the production hierarchy." visible_explanation="Treat this fence as the accepted event-contract outline; use the compiled Tutorial and versioned API reference for copy-ready code." */}
```kotlin
enum class RenderFrameDiagnosticLevel { None, Stats, Tree }

data class RenderDiagnosticCollection(
    val lifecycle: Boolean = true,
    val failures: Boolean = true,
    val frameLevel: RenderFrameDiagnosticLevel = RenderFrameDiagnosticLevel.None,
)

class RenderDiagnostics(
    val collection: RenderDiagnosticCollection,
    val sink: RenderDiagnosticsSink,
)

fun interface RenderDiagnosticsSink {
    fun onEvent(event: RenderDiagnosticEvent)
}

sealed interface RenderDiagnosticEvent {
    val context: RenderDiagnosticContext
}
```

The sealed family is `RenderSessionStarted`, `RenderSessionActivityChanged`,
`RenderFailureObserved`, `RenderFrameCompleted`, and `RenderSessionEnded`.
`RenderFrameCompleted` carries the authoritative `RenderFrameReport` and nullable `RenderStats`
and `RenderTreeResult`. `Stats` collects counters; `Tree` also collects the bounded tree, patches,
warnings, and composition details; `None` builds neither. Rolled-back frames expose no candidate
stats or tree.

Ordering is normative:

1. subscribed start is first;
2. failures follow occurrence order after recovery is known;
3. one frame-completed event follows every synchronous attempt after `lastFrameReport` is final;
4. activity publishes only after a real transition;
5. end follows logical cleanup and is terminal; and
6. successful preparation is silent until activation; preparation failure emits the minimal
   start, failure, rolled-back frame, end sequence.

Calls are synchronous, serialized per session, and use the session platform thread. Sink re-entry
into render, activity mutation, or disposal fails fast. A thrown sink cannot alter the frame or
original failure and is never recursively published. It is platform-logged, stored locally as a
`DiagnosticsSink` failure, and disables that sink for the session. Optional composite sinks isolate
children.

Phase 1 removes `onRenderStats`, `onRenderResult`, and `onRenderFailure` from all root APIs, deletes
`LocalRenderResultListener`, renames `RenderFailurePhase.DiagnosticsCallback` to
`DiagnosticsSink`, and migrates Demo, Preview, tests, and tutorials together. There are no
deprecated overloads, aliases, adapters, or dual publication. `lastRenderFailure` and
`lastFrameReport` remain direct queries. `debug` remains a logging/slow-warning option; only an
immutable, non-null root `RenderDiagnostics` selects collection and child inheritance.

### Module ownership

`viewcompose-runtime` owns the finite composition-timing port; `viewcompose-ui-foundation` owns the
neutral identities, events, parent Local, failures, and timing records; renderer owns request-gated
reconciliation/binding timing and weak View/token snapshots; host owns platform installation,
clock/thread handoff, and neutral discovery. New optional `viewcompose-diagnostics` owns bounded
aggregation and sink helpers; Preview owns the debug protocol, capture, highlight, timing control,
responses, and cache; the Studio plugin owns requests, validation, UI, and stale/timeout handling.

There is no process-global runtime sink. Applications explicitly pass diagnostics to a root. The
optional aggregator has no vendor, network, database, worker, manifest component, persistence, or
upload.

### Production failure aggregation

Q3 `BoundedRenderFailureAggregator` defaults to 64 fingerprints (valid `1..128`) and a 15-minute
monotonic window (valid 1 minute through 24 hours). Immutable snapshot/reset results do not change
live trace identity. The default fingerprint contains only failure phase, recovery, optional
operation, exception binary type, and at most three `com.viewcompose.*` class/method stack frames.
It excludes messages, application frames, file/line data, raw keys, View text, Locals, URLs, media,
credentials, causes, full stacks, and the `Throwable` itself.

An aggregate contains count, first/last monotonic time, latest safe context, window ID, and
dropped/evicted counters. Counts saturate at `Long.MAX_VALUE`; capacity evicts the least recently
updated entry deterministically. Expiration occurs on record or snapshot without a timer. Sink or
export failure is not fed back. Persistence, consent, account association, cross-process sampling,
network upload, and vendor metadata remain application responsibilities.

### Highlighting and timing

`RenderNodeToken` is an opaque Q2 process-local value from a bounded requested snapshot. It contains
no application key and becomes stale on replacement, cross-owner reuse, incompatible generation,
session disposal, or expiry. A nonce/trace/token request resolves a weak current View on Android's
main thread, captures screen and clipped bounds plus attachment/visibility, and shows one
non-interactive overlay. Results distinguish selected, missing, stale, recycled, hidden, fully
clipped, unsupported synthetic node, ended session, and rejected request.

The overlay never mutates layout, state, focus, semantics, accessibility focus, touch, or callbacks.
It clears after five seconds, explicit clear, replacement, View release, session end, window/host
destruction, or process stop. It holds only weak View/window references and installs no recurring
layout, scroll, draw, touch, frame, or recomposition listener.

Timing supports only composition evaluation (inclusive and child-subtracted self), reconciliation
decisions (inclusive and self), and direct renderer binding/patch operations. One injected
monotonic-nanosecond clock serves a capture. Android measure/layout, draw, GPU, RenderThread,
SurfaceFlinger, decode, network, database, and external SDK work are explicitly unsupported.

Only a request activates timing. One process owns at most one capture, ending after eight completed
frames or two seconds. It retains at most 64 timed nodes per frame, 512 records total, depth 32, and
128 distinct strings truncated to 256 UTF-16 units. Results include attempted/retained clock reads,
empty-pair overhead, truncation, unsupported domains, and drops. Inactive timing performs zero
per-node clock reads and allocates no record/history; runtime and renderer may perform only an
approved nullable-port/request-state check.

#### Bounded future-session extension (2026-08-27)

An explicit development-tool request may arm one exact parent Session for a future `LazyItem`
child. Matching uses only the parent Session ID, `LazyItem` role, and a Session-ID floor captured at
arm time. The arm expires after ten monotonic seconds and the match records at most one completed
frame. `viewcompose-ui-foundation` permits that matched Session to register immediately before its
initial frame; starting timing during that registration attaches to the entering frame and cannot
request a nested structural render. Preview owns the concrete arm, deadline, terminal outcomes, and
an opaque process-local physical-container token used only to correlate logical Session replacement
with holder reuse. No application key or native object becomes a selector or serialized identity.

### Absolute limits

| Resource | Hard maximum and overflow |
| --- | --- |
| Live tooling sessions | 64/process; remove dead weak entries, then oldest inactive; report drop |
| Recent events | 512/request and 64/session; truncate oldest deterministically |
| Source candidates | 32/session; 24 callsites/candidate; 1,024 chars/string; 12 KiB/candidate; 48 KiB/session |
| Mounted-node request | 2,048 visited; 512 returned; depth 64; stop and report truncation |
| Highlight | One/process for five seconds |
| Failure fingerprints | 128 absolute, public default 64 |
| Timing | One/process; selected Session: 8 frames or 2 seconds; future `LazyItem`: 10-second arm then 1 frame; 64 nodes/frame; 512 records; depth 32 |
| Nonce | 1--128 ASCII `[A-Za-z0-9._-]` |
| Other strings | 256 UTF-16 units |
| Serialized response | 256 KiB UTF-8 including envelope/truncation metadata |

Limits apply before retaining/encoding the next item. Responses stay valid and report drops;
rendering never changes because a limit is reached.

### API quality

| Family | Level | Required contract fields |
| --- | --- | --- |
| Trace ID, role, context | Q2 | behavior, output, identity, process lifecycle, ordering, compatibility, privacy |
| Collection and frame level | Q2 | inputs/defaults, collection behavior, state, performance, compatibility |
| Diagnostics, sink, events | Q3 | behavior, ownership, lifecycle, thread, callback ordering/frequency/re-entry, isolation, performance, compatibility |
| Changed host APIs | Q3 | defaults, inheritance, lifecycle, main thread, callback/failure behavior, performance, hard-cut migration |
| Node token and highlight | Q3 family/Q2 values | identity, inputs, coordinates, weak ownership, timeout, main thread/window, states, performance, compatibility |
| Timing | Q3 family/Q2 values | units/clock, inclusion, lifecycle/cancellation, thread, truncation, overhead, performance, compatibility |
| Aggregator and snapshot/reset | Q3 | ranges, output, ownership, window/reset, synchronization, isolation, bounds, privacy, compatibility |
| Fingerprint and aggregate values | Q2 | meaning, redaction, identity, time units, saturation, compatibility |

Android resources/UI, persistence/network I/O, restoration, or cancellation are non-applicable
where the family does not own them. Each Q3 family lands with canonical-English KDoc, compiled
sample, owning-module documentation, Chinese public documentation, and callback-to-event migration.

## Consequences

Results gain one logical owner independent of View reuse, and failure-only collection remains cheap.
Alpha callers must migrate. Slow I/O requires an application queue after copying bounded immutable
data. The optional artifact requires catalog, manual, API, dependency, Changeset, and Maven-consumer
work. Requested inspection may be visibly expensive but is finite and leaves no recurring work.

## Rejected alternatives

IDs on existing callbacks or deprecated adapters preserve inconsistent propagation and a second
ownership system. Application keys may be sensitive; View identity follows physical life; UUIDs
imply unneeded cross-process stability. Foundation must not own optional policy or storage, and
continuous trees violate ADR-0009. Android layout/GPU timing is rejected until node attribution is
verifiable.

## Validation

Phase 1 must prove event order for committed, rolled-back, async, prepared, retained, reused, and
disposed sessions; all six parent graphs; all collection levels; sink throw/re-entry isolation;
complete legacy callback/role deletion; Q3 samples/docs/Chinese mirrors; API checks; Changesets; and
Maven consumers. Later phases must exercise every limit, deterministic eviction, concurrent and
high-cardinality failures, highlight cleanup/staleness, calibrated timing, release classpaths, and
same-device inactive/requested performance.

The [completed diagnostics plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/diagnostics-correlation-inspection-observability.md)
records the exact targeted checks. Device and performance evidence was required before each
implementation phase was accepted, not for this documentation-only freeze.
