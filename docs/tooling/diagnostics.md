# ViewCompose Diagnostics

## 1. Correlated event entry point

Install one immutable `RenderDiagnostics` at a Host or Preview root. Child navigation, lazy, pager,
and overlay sessions inherit that sink and receive process-local session and parent IDs. Passing a
new diagnostics instance to a low-level nested session deliberately starts a new tree.

```kotlin
val diagnostics = RenderDiagnostics(
    collection = RenderDiagnosticCollection(
        lifecycle = true,
        failures = true,
        frameLevel = RenderFrameDiagnosticLevel.Tree,
    ),
    sink = { event ->
        when (event) {
            is RenderFrameCompleted -> inspect(event.context, event.report, event.tree)
            is RenderFailureObserved -> report(event.context, event.failure)
            else -> recordLifecycle(event)
        }
    },
)

val session = renderInto(container = root, diagnostics = diagnostics) { App() }
```

`None` builds no renderer counters or tree details, `Stats` builds aggregate counters, and `Tree`
also builds the bounded tree, patches, warnings, and composition diagnostics. `debug` controls
logging and slow-operation warnings only; it does not select event collection.

`RenderTreeResult` currently contains:

1. `stats / structure / warnings`: aggregate binding work, tree size, and warnings;
2. `tree`: the node tree consumed by the renderer, including node type, key, and hierarchy;
3. `patches`: ordered `Insert / Remove / Rebind / Patch / SkipSelf / SkipSubtree` records for the
   frame, including parent key, position, moves, and patch type;
4. `composition`: invalidated, recomposed, and skipped scope counts, plus each scope path,
   signature, recomposition reason, and Local snapshot.

## 2. Recomposition reasons

The runtime distinguishes:

1. `InitialComposition`
2. `StateInvalidation`
3. `AncestorInvalidation`
4. `InputsChanged`
5. `ExplicitRequest`
6. `StructureChanged`

Scope diagnostics are capped at 500 records and signatures are truncated so diagnostic cost does
not grow without bound with page size.

## 3. CompositionLocal diagnostics

`uiLocalOf(debugName = ..., debugValueFormatter = ...)` supplies a stable name and safe summary.
Built-in core Locals such as Theme, Environment, LifecycleOwner, SavedState, and ContentColor have
explicit names.

The default summary displays only Strings, numbers, Booleans, Chars, and enums directly. Other
objects display only their type and do not invoke an arbitrary application `toString()`. Crop a
sensitive application value deliberately through a custom formatter, or omit the formatter.

## 4. Ordering and failure isolation

Subscribed lifecycle starts first. Failures are emitted after recovery is known, and one
`RenderFrameCompleted` follows every synchronous attempt after `lastFrameReport` is authoritative.
Activity events represent real transitions; terminal end follows cleanup. Preparation stays silent
until activation. Delivery is synchronous and session-serialized; re-entry fails fast, while a
throwing sink is recorded and disabled without changing recovery or recursively publishing.

## 5. Alpha migration from callbacks

The alpha API removes all three callbacks and the result-only Local without adapters. Read stats
and trees from `RenderFrameCompleted`, failures from `RenderFailureObserved`, or poll
`lastFrameReport` / `lastRenderFailure` when no stream is needed.

## 6. Bounded production failure aggregation

The optional `viewcompose-diagnostics` artifact now ships `BoundedRenderFailureAggregator`. Install
it with lifecycle disabled, failures enabled, and frame level `None` to count recurring structured
failures without activating frame trees or debug inspection. Its fixed privacy fingerprint excludes
messages, application frames, file/line data, raw keys, View text, Local values, causes, and the
original `Throwable`.

Aggregation is application-owned, thread-safe, and bounded to 64 fingerprints by default with a
hard maximum of 128. A 15-minute default monotonic window expires lazily on record or snapshot;
least-recently-updated eviction and count saturation are visible in immutable snapshot counters.
Storage, consent, scheduling, upload, vendor metadata, and downstream failure policy remain outside
the framework. See the [module manual](../modules/viewcompose-diagnostics/README.md).

## 7. Correlated running-device inspector

Add `viewcompose-preview` through `debugImplementation`, foreground the debuggable application, and
choose **Inspect Device Diagnostics**. The single inspector hard-replaces the earlier source,
highlight, clear, and timing actions. Its Session tree preserves parent/child roles and keeps the
latest committed frame distinct from the latest completed attempt and latest failure. A failure
shows only typed phase, recovery, optional Android View operation, and a bounded exception binary
class name; the original exception, message, cause, stack, key, and application content never cross
the tooling boundary.

The same selected Session owns three views: source candidates, mounted nodes, and finite timing.
Source, node, and timing records can each navigate to their bounded current-project call site.
Every navigable row displays the resolved authored-project location that the navigation action will
open; internal framework stack frames are neither shown as the destination nor opened instead.
Components expose stable `viewcompose.deviceDiagnostics.*` automation roles; the Demo retains stable
tags for refresh, highlight replacement, timing action, visible timing status, and its deterministic
eight-frame fixture.

### Mounted-node highlighting

Request one current mounted-tree snapshot, choose a declarative node, and use **Highlight node** or
**Clear highlight**. The selected node draws its clipped real Android View boundary for at most five
seconds.

Tokens are opaque, process-local, and snapshot-scoped. They contain no application key. A newer
snapshot, node replacement, View reuse by another logical owner, Session disposal, or process
restart makes them stale. The response distinguishes selected, partially clipped, missing, stale,
recycled, hidden, fully clipped, synthetic/unsupported, ended, rejected, and cleared outcomes.
Bounds are screen coordinates plus the globally visible clipped rectangle.

The request visits at most 2,048 mounted nodes, returns 512 to depth 64, retains only weak native
targets, and serializes at most 256 KiB. Inactive tooling performs no traversal, geometry read,
overlay mutation, report write, or listener installation. An active overlay is non-interactive and
cannot recompose, invoke application callbacks, change focus or accessibility focus, intercept
input, or mutate layout. The Diagnostics → Renderer page includes a unique AndroidView target and
a replacement action for deterministic manual validation.

### Finite per-node timing

Use **Capture timing** in the selected Session and trigger the interaction under investigation. The
Diagnostics → Renderer page provides **Run 8-frame timing workload**; its visible counter advances
from `0/8` to `8/8` so manual acceptance does not depend on an invisible state change.

Each capture stops after at most eight completed frame attempts or two monotonic seconds. It records
only executed composition scopes, renderer reconciliation, and direct native binding. Composition
and reconciliation report both inclusive and self duration; binding reports direct duration. One
opaque capture-scoped node token connects phases without exposing application keys. Skipped scopes
perform no timing callback or clock read.

The response retains at most 64 nodes per frame, 512 aggregate records, depth 32, 128 distinct
strings of at most 256 characters, and 256 KiB of JSON. It reports attempted and retained clock
reads, an empty-pair overhead estimate, unsupported domains, drops, truncation, completion, and the
terminal reason. Studio ranks additive self/direct records to avoid double-counting inclusive
parents. The first contract deliberately excludes measure/layout/draw, GPU, RenderThread,
SurfaceFlinger, image decode, network, database, and external-SDK work; use platform profilers for
those domains.

Only one process capture may be active. Ordinary rendering supplies no collector and performs zero
per-node clock reads, timing-record allocation, report writes, polling, or recurring observation.
The timing result is diagnostic evidence, not a frame-time benchmark: instrumentation overhead and
the small finite sample remain visible limitations.

## 8. Demo inspector

`Diagnostics -> Renderer` provides the render tree, patch timeline, recomposition reasons,
CompositionLocal browser, aggregate metrics, the mounted-node highlight fixture, and the explicit
eight-frame timing workload. Cross-session correlation, production aggregation, real View-boundary
highlighting, finite per-node timing, and the correlated Studio inspector are implemented.
Same-device idle/request performance, weak lifecycle ownership, optimized-Release exclusion, and
isolated Maven consumption have also closed without an accepted regression. The execution record
is retained in the [archived diagnostics correlation, inspection, and production observability
plan](https://github.com/ViewCompose/ViewCompose/blob/main/docs/archive/diagnostics-correlation-inspection-observability.md).

## 9. Remaining expansion contract

[ADR-0021](../architecture/decisions/0021-correlated-render-diagnostics-ownership.md) freezes Phase 1.
A failure-only sink activates no frame detail. The optional `viewcompose-diagnostics` artifact owns
production aggregation; `viewcompose-preview` owns the shipped request-driven correlated inspector,
highlighting, and finite timing. No diagnostics expansion is currently active. A future continuous
observer, new timing domain, or broader device contract requires a new attributed plan and must
preserve ADR-0009's inactive and Release isolation rules.

`./gradlew verifyDemoReleaseToolingApk` assembles the optimized Demo Release APK and rejects the
device request action, v7 report path, receiver, service registration, or concrete inspection class
in any packaged entry. `qaQuick` runs this artifact-level gate in addition to the Release runtime
dependency-graph check.
