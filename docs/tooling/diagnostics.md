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
Activity events represent actual transitions only; session end follows cleanup and is terminal.
Successful candidate preparation is silent until activation, while a failed preparation emits the
minimal start, failure, rolled-back frame, and end sequence.

Sink calls are synchronous and serialized per session. Re-entry into the emitting session fails
fast. A throwing sink is platform-logged, stored as a local `DiagnosticsSink` failure, and disabled
for that session; it cannot change the frame report, replace the original recovery, or recursively
publish another event.

## 5. Alpha migration from callbacks

The alpha API removes `onRenderStats`, `onRenderResult`, and `onRenderFailure` together. Replace a
stats callback with `RenderFrameCompleted.stats`, a tree callback with
`RenderFrameCompleted.tree`, and a failure callback with `RenderFailureObserved.failure`. There are
no deprecated overloads or result-only Local adapters. `lastRenderFailure` and `lastFrameReport`
remain available for direct session queries.

## 6. Demo inspector

`Diagnostics -> Renderer` currently provides:

1. a render-tree list;
2. a patch timeline;
3. recomposition reasons and scope counts;
4. a CompositionLocal browser;
5. existing aggregate render/layout metrics.

Cross-session correlation is now implemented. The inspector does not yet provide real
View-boundary highlighting or per-node timing. Delivery of those capabilities, together with bounded production failure
aggregation, has moved to the active
[diagnostics correlation, inspection, and production observability plan](../project/plans/diagnostics-correlation-inspection-observability.md).

## 7. Remaining expansion contract

[ADR-0021](../architecture/decisions/0021-correlated-render-diagnostics-ownership.md) freezes the
implemented Phase 1 boundary. Host, Preview, navigation, lazy, pager, and overlay sessions now
share one identity model; a failure-only sink does not activate stats or tree collection.
Production aggregation will live in the optional
`viewcompose-diagnostics` artifact, while highlighting and timing remain request-driven in
`viewcompose-preview` under ADR-0009.

The active plan owns production aggregation, highlighting, timing, and inspector closeout.
