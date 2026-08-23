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

## 7. Request-driven mounted-node highlighting

Add `viewcompose-preview` through `debugImplementation`, keep the debuggable application in the
foreground, and choose **Tools → Highlight Device DSL Node**. Studio first selects one correlated
visible Session, requests a bounded current mounted-tree snapshot, and lists its declarative nodes.
Choosing a node draws its clipped real Android View boundary for at most five seconds. **Tools →
Clear Device DSL Highlight** clears it immediately.

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

## 8. Demo inspector

`Diagnostics -> Renderer` provides the render tree, patch timeline, recomposition reasons,
CompositionLocal browser, aggregate metrics, and the mounted-node highlight fixture. Cross-session
correlation, production aggregation, and real View-boundary highlighting are implemented;
per-node timing remains in the active
[diagnostics correlation, inspection, and production observability plan](../project/plans/diagnostics-correlation-inspection-observability.md).

## 9. Remaining expansion contract

[ADR-0021](../architecture/decisions/0021-correlated-render-diagnostics-ownership.md) freezes Phase 1.
A failure-only sink activates no frame detail. The optional `viewcompose-diagnostics` artifact owns
production aggregation; `viewcompose-preview` owns shipped request-driven highlighting and will
keep timing request-driven. The active plan owns timing and inspector closeout.
