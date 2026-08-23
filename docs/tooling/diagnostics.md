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

## 6. Demo inspector

`Diagnostics -> Renderer` provides the render tree, patch timeline, recomposition reasons,
CompositionLocal browser, and aggregate metrics. Cross-session correlation is implemented; real
View-boundary highlighting, per-node timing, and bounded production failure aggregation remain in
the active
[diagnostics correlation, inspection, and production observability plan](../project/plans/diagnostics-correlation-inspection-observability.md).

## 7. Remaining expansion contract

[ADR-0021](../architecture/decisions/0021-correlated-render-diagnostics-ownership.md) freezes Phase 1.
A failure-only sink activates no frame detail. The optional `viewcompose-diagnostics` artifact owns
production aggregation; `viewcompose-preview` keeps highlighting and timing request-driven. The
active plan owns their delivery and inspector closeout.
