# ADR-0011: Prefetched session activation boundary

- Status: Accepted
- Date: 2026-08-13

## Context

Android `RecyclerView` can create and bind holders before they are attached, but a ViewCompose lazy
item previously staged only its immutable item snapshot during that interval. The first attachment
then created the child `RenderSession`, composed the VNode tree, created and bound Android Views,
and committed effects on the same fling frame. A page containing several independently composed,
View-heavy items therefore moved composition and View inflation directly into
`LinearLayoutManager.layoutChunk`, producing visible long-fling stalls even though RecyclerView
prefetch was enabled.

Calling the existing `render` operation from detached binding is not valid. A successful render is
a committed frame: remember observers become active, `SideEffect` and native commit callbacks run,
overlays publish, and coroutine effects can start. Prefetch is speculative and may be discarded
without the holder ever becoming visible, so it cannot cross that commit boundary.

## Decision

1. `LazyListItemSession` is a Q3 three-phase lifecycle: optional `prepare`, one `activate`, then zero
   or more active `render` operations, followed by terminal `dispose`. At ADR-0011 adoption,
   `prepare` defaulted to no work and `activate` delegated to `render`. ADR-0012 later hard-cut both
   commit operations to return whether their installed revision committed, so custom sessions must
   now implement the explicit Boolean commit contract.
2. A detached, never-activated RecyclerView holder may call `prepare`. The standard widget session
   composes its candidate VNode tree and establishes the native View tree, but retains the prepared
   composition transaction instead of committing it.
3. Preparation runs no remember activation, `SideEffect`, `DisposableEffect`, `LaunchedEffect`,
   native `AndroidView.onCommit`, overlay publication, or committed-frame diagnostics callback.
   Recycling a prepared holder aborts the composition and releases its native tree without starting
   candidate effects.
4. First attachment calls `activate`. If no observed state changed after preparation, activation
   commits the retained composition and then preserves the normal effect, native commit, overlay,
   and diagnostics order. It does not rebuild the already prepared native tree.
5. State reads remain observed while a composition is prepared. If a relevant state changes before
   attachment, activation aborts the stale candidate and synchronously renders the latest state;
   stale effects never start.
6. A session that has activated remains active across ordinary RecyclerView detach/cache events and
   is disposed on holder recycle or container disposal. Detach is not a general application
   lifecycle signal, and this change does not introduce pausable effects. A newer submission for an
   active detached holder is staged and rendered when that holder reattaches.
7. Submission revision and identity rules remain authoritative. Preparation never marks a revision
   committed, duplicate revisions do not prepare or activate twice, and a replacement candidate
   disposes the previous uncommitted session.
8. Prefetch is an optimization, not a semantic guarantee. RecyclerView may decline work under its
   deadline. Applications and demos should still avoid splitting one coherent, static fixture into
   many expensive independent sessions when no item-level recycling benefit exists.

## Public API and compatibility impact

`LazyListItemSession.prepare` and `LazyListItemSession.activate` were additive Q3 lifecycle methods
in `viewcompose-ui-contract` when this decision was adopted. Custom renderers can opt into
native-tree preparation, but must keep preparation free of externally observable committed work
and must make `dispose` safe before activation. ADR-0012 subsequently made `activate` and `render`
return Boolean commit success; that intentional hard cut allows rollback to remain retryable instead
of falsely advancing an item revision.

The standard Android renderer and UI Foundation integration adopt the full protocol. ADR-0012 later
hard-cuts the item revision, logical-session, and physical-tree ownership rules; those newer rules
supersede this record's original refresh assumptions while retaining the prepared-activation
boundary defined here.

## Consequences

- RecyclerView adjacent prefetch can move composition and Android View construction ahead of the
  frame that attaches a new item during a fling.
- Speculative holders cannot publish business effects or overlays.
- A prepared transaction temporarily retains its candidate composition and native tree until
  activation, replacement, or recycle; cache and prefetch hints therefore remain bounded resource
  controls.
- Stateful items stay correct when their observed input changes between prepare and attach.
- Already-active cached items retain the established lifecycle semantics instead of repeatedly
  stopping and restarting coroutine or disposable work at viewport edges.

## Rejected alternatives

### Render normally during detached binding

Rejected because it commits effects for speculative content and makes an item that never attaches
externally observable.

### Add a debug-only or Demo-only performance switch

Rejected because the expensive attach path is a production renderer lifecycle defect. Coarsening
the affected Demo fixture is still appropriate, but cannot replace the framework correction.

### Pause every effect whenever a holder detaches

Rejected because `DisposableEffect`, arbitrary remember observers, and coroutine scopes are not
generally pausable. RecyclerView detach also occurs during benign cache and layout churn and is not
equivalent to logical removal or host lifecycle stop.

### Commit composition but defer only named effect primitives

Rejected because arbitrary `RememberObserver` implementations and future commit callbacks would
escape a partial allowlist. Retaining the existing composition transaction keeps one authoritative
commit boundary.

## Validation

The architecture requires deterministic coverage for:

1. prepare followed by attach activation without a second native render;
2. no remember, side, native commit, overlay, or diagnostics work before activation;
3. state invalidation between prepare and attach aborting the stale candidate;
4. replacement, duplicate revision, recycle-before-attach, and active detached reattach behavior;
5. unchanged key and revision behavior, as superseded by ADR-0012;
6. a forceful, long-running Diagnostics Theme fling that reaches the list bottom and returns to the
   top under `FrameTimingMetric`, rather than a short top-of-page swipe.
