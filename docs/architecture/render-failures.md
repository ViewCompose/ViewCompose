# Render failures and Android interop effects

`RenderSession` keeps render failures observable without turning recoverable frame failures into
process crashes. Root hosts accept one correlated `RenderDiagnostics` sink.

```kotlin
val session = renderInto(
    container = root,
    diagnostics = RenderDiagnostics(
        collection = RenderDiagnosticCollection(
            lifecycle = false,
            failures = true,
            frameLevel = RenderFrameDiagnosticLevel.None,
        ),
        sink = { event ->
            if (event is RenderFailureObserved) report(event.context, event.failure)
        },
    ),
) {
    App()
}
```

The host `RenderSession` also exposes `lastRenderFailure` and `lastFrameReport`. A frame report is
either `Committed` or `RolledBack` and contains every synchronous failure observed in that frame.
Asynchronous composition-coroutine failures are reported separately and do not rewrite an already
completed frame report.

## Recovery guarantees

- `CompositionPrepare` and `ViewTreeRender` failures abort the candidate composition and report
  `PreviousFrameRestored`. The renderer restores the previous VNode bindings, mounted children,
  layout parameters, View order, and releases newly inserted nodes on a best-effort basis.
- `ObservedPropertyPrepare` reports `FrameUnchanged`: candidate values and dependency guards are
  abandoned before any native mutation. `ObservedPropertyRender` reports
  `PreviousFrameRestored`: the renderer preflights the complete exact-target batch and rebinds every
  earlier target to its committed VNode when one patch fails. `ObservedPropertyCommit` reports
  `FrameCommitted` because native values are already authoritative; dependency commit failures are
  observable and do not silently trigger a whole-tree fallback.
- commit, side-effect, overlay, and native commit failures report `FrameCommitted`.
  These happen after the new View tree has become authoritative and are isolated so that one
  failure does not prevent the remaining operations from running. A throwing remembered activation
  stays pending and is retried by a later successful composition commit; successful siblings are
  not activated twice, and removal before success abandons the pending value.
- composition-coroutine failures report `FrameUnchanged`.
- disposal failures report `SessionDisposed`; cleanup continues across remaining nodes and hosts.
- a throwing diagnostics sink is stored locally as `DiagnosticsSink`, disabled for that session,
  and never changes the authoritative frame report or recursively emits a failure event.

`RenderFailureOperation` and `nodeKey` identify `AndroidView` factory, update, reset, commit, and
release failures without parsing exception messages.

## Optional bounded production aggregation

Applications that need recurring-failure counts can install `BoundedRenderFailureAggregator` from
the optional `viewcompose-diagnostics` artifact as the failure-only root sink. The default
fingerprint retains phase, recovery, optional Android View operation, direct exception binary type,
and at most three class/method-only `com.viewcompose.*` frames. It never retains the message,
cause chain, application frames, file/line data, `nodeKey`, or original `Throwable`.

The aggregator defaults to 64 distinct fingerprints in a 15-minute monotonic window, with hard
valid ranges of `1..128` and one minute through 24 hours. Capacity evicts the least recently
updated fingerprint and reports both lost observations and evicted entries. Expiration occurs only
on record or snapshot; there is no timer, storage, transport, vendor SDK, or process-global sink.
Snapshots are immutable application-owned values. Export them outside synchronous sink delivery so
network or persistence work cannot block a render session.

See the [Diagnostics module manual](../modules/viewcompose-diagnostics/README.md) for exact
redaction, synchronization, reset, and counter contracts.

## AndroidView side-effect boundary

`AndroidView` has two deliberately different update paths:

```kotlin
AndroidView(
    key = playerId,
    factory = { context -> PlayerView(context) },
    update = { view ->
        // Replay-safe View configuration only.
        view.isEnabled = enabled
    },
    onReset = { view ->
        // Replay-safe cleanup before the View is rebound.
        view.player = null
    },
    onCommit = { view ->
        // Non-replayable external action. Runs only after a successful tree transaction.
        analytics.recordPlayerAttached(playerId)
    },
    onRelease = { view ->
        // One-shot resource release after any permanent abandonment.
        view.player = null
    },
)
```

The rules are strict:

1. `factory`, `update`, `onReset`, and `Modifier.nativeView` are part of the renderer transaction.
   `update` can run again while an old node is restored after a later binding fails. Keep these
   callbacks idempotent and limited to the supplied View.
2. Put network writes, analytics, database writes, service calls, or other non-replayable external
   effects in `onCommit`. The renderer publishes these callbacks only after the complete recursive
   View-tree transaction commits. A rolled-back candidate never publishes or runs them.
3. `onRelease` is resource cleanup, not a general commit effect. It runs at most once whenever a
   created node is permanently abandoned, including candidate rollback, successful removal, final
   reuse-cache eviction, or session disposal.
4. Native platform state cannot be cloned generically. The rollback guarantee therefore covers
   framework-owned tree structure plus replay of the previous View configuration, not arbitrary
   state hidden inside a third-party View.
