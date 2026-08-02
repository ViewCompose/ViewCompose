# Render failures and Android interop effects

`RenderSession` keeps render failures observable without turning recoverable frame failures into
process crashes. Both `renderInto` and `setUiContent` accept `onRenderFailure`.

```kotlin
val session = renderInto(
    container = root,
    onRenderFailure = { failure ->
        report(
            phase = failure.phase,
            recovery = failure.recovery,
            frameId = failure.frameId,
            operation = failure.operation,
            cause = failure.cause,
        )
    },
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
- commit, side-effect, overlay, diagnostics, and native commit failures report `FrameCommitted`.
  These happen after the new View tree has become authoritative and are isolated so that one
  callback does not prevent the remaining callbacks from running.
- composition-coroutine failures report `FrameUnchanged`.
- disposal failures report `SessionDisposed`; cleanup continues across remaining nodes and hosts.

`RenderFailureOperation` and `nodeKey` identify `AndroidView` factory, update, reset, commit, and
release failures without parsing exception messages.

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
        // One-shot resource release after removal commit or session disposal.
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
3. `onRelease` is resource cleanup, not a general commit effect. It runs at most once for a mounted
   node after a successful removal or during session disposal.
4. Native platform state cannot be cloned generically. The rollback guarantee therefore covers
   framework-owned tree structure plus replay of the previous View configuration, not arbitrary
   state hidden inside a third-party View.
