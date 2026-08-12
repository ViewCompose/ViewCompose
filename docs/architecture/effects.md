# Transactional effects and structured work

ViewCompose effects connect a successful declarative frame to imperative work. They are part of the
render transaction, not callbacks that run merely because a DSL function was evaluated.

## Frame order

A standard `RenderSession` prepares composition and renders the candidate View tree first. Once the
renderer makes that tree authoritative, the runtime commits remembered values and then dispatches:

1. committed-value publication;
2. outgoing remember, disposable, and launched-effect lifecycles;
3. incoming remember, disposable, and launched-effect lifecycles;
4. `SideEffect` callbacks in declaration order;
5. renderer-owned native `AndroidView.onCommit` callbacks; and
6. overlay reconciliation and diagnostics.

An error before the native tree commits aborts the candidate. Candidate effects never start and the
previous committed effects remain active. An error in the list above occurs after the frame became
authoritative. It is reported as a committed-frame failure, every independent synchronous operation
is still attempted, and later rendering can continue.

## Choosing an effect

| Need | API | Lifetime |
| --- | --- | --- |
| Publish a synchronous value after each successful frame | `SideEffect { ... }` | One callback per successful invocation |
| Publish only when explicit identity changes | `SideEffect(key) { ... }` | Initial commit and changed keys |
| Subscribe or own a resource with paired cleanup | `DisposableEffect(key) { ... }` | Key or structural presence |
| Run suspending composition-owned work | `LaunchedEffect(key) { ... }` | Key or structural presence |
| Launch work from an event callback | `rememberCoroutineScope()` | Structural presence of the remembered scope |
| Keep a running effect pointed at the latest callback/value | `rememberUpdatedState(value)` | Stable holder; candidate published at commit |
| Produce observable state from suspending work | `produceState(...)` | Stable state holder plus keyed producer |
| Bind paired work to Android started/resumed state | lifecycle integration effects | Lifecycle state and composition presence |

`DisposableEffect` setup returns `onDispose { ... }` and requires at least one key. Each successful
setup receives one terminal cleanup. Cleanup runs before a replacement setup. If setup throws, no
cleanup exists; if cleanup throws, it is not invoked a second time.

`LaunchedEffect` is for work caused by entering a declarative identity. Event handlers should launch
through a remembered scope instead of moving event values into a key merely to restart work.

## Keys and structural identity

Keys compare with structural equality. They decide whether one positional effect is retained or
replaced; they do not make unrelated call sites globally unique. ViewCompose has no Compose compiler
to generate call-site groups. Code that repeats, conditionally inserts, or reorders effects must use
stable `key(...)` groups at the structural boundary. Lazy containers additionally require their own
stable item keys.

Unkeyed `SideEffect` is intentionally different: it runs after every successful invocation. Use a
keyed overload for change-only synchronous publication.

## Rollback and `rememberUpdatedState`

`rememberUpdatedState` uses one stable holder. During a candidate composition, reads from that
composition see the candidate value so declaration code remains coherent. Already-running effects
continue to see the committed value until the frame commits. Abort discards the candidate, while
successful commit publishes it before outgoing or incoming lifecycle callbacks.

This API is intended to update values captured by long-lived effects. Ordinary UI data should be
read from its source `State`; reading the returned holder in emitted UI can cause a follow-up
invalidation when the committed value publishes.

## Coroutine ownership

Each `RenderSession` owns a supervisor root in the platform-installed coroutine context. Independent
composition scopes therefore do not tear down the complete session when one child fails.
`LaunchedEffect` owns one job below that root. A remembered coroutine scope owns a normal child job,
so failure and cancellation remain structured within that scope rather than being hidden by another
supervisor.

Replacing keys or leaving composition requests cancellation. Cancellation cleanup may suspend; the
runtime guarantees cancellation is requested before replacement work starts, not that arbitrary
non-cancellable cleanup finishes synchronously. Disposing the session cancels the session coroutine
root before releasing mounted Views and composition resources.

Render inactivity is not a coroutine pause signal. Work that must stop below `STARTED` or `RESUMED`
uses `viewcompose-lifecycle-androidx`.

## Locals and Android capabilities

Resolve composition-scoped values while declaring the effect and capture the result:

```kotlin
val theme = Theme.current
val activity = LocalAndroidHostCapabilities.current.activity

SideEffect(theme, activity) {
    activity?.applyWindowAppearance(theme)
}
```

Do not read `Theme.current`, `Environment`, or another context-only Local for the first time inside
an effect callback. The provider stack has already returned, and an asynchronous callback may
outlive it. ViewCompose does not retain and reinstall every Local implicitly. Deferred child
composition uses the dedicated captured Local snapshot path instead.

Android resources, Activity/Window capabilities, lifecycle ownership, and named design-system
tokens are separate contracts. Selecting one does not imply the others are present.

## Compose comparison boundary

The observable lifecycle, key, rollback, and serialization rules above deliberately align where
ViewCompose can protect them. The following Compose features require its compiler/runtime protocol
and are not implied by similar API names:

- compiler-generated restart and replaceable groups;
- automatic call-site identity and changed flags;
- stability inference and smart skipping;
- compile-time restrictions on composable and non-composable calls;
- full movable-group semantics for arbitrary sibling reordering; and
- identical recomposer apply dispatchers, frame clocks, tooling metadata, or stack traces.

ViewCompose uses explicit DSL groups, runtime validation, stable keys, and diagnostics at those
boundaries. Migration should preserve effect ownership and transaction semantics rather than assume
source-name equivalence.

## Related documentation

- [State and snapshot architecture](./state-snapshots.md)
- [Render failures and Android interop effects](./render-failures.md)
- [Lifecycle and SavedState](./lifecycle-and-saved-state.md)
- [Compose state and recomposition migration](../migration/compose-state-recomposition-and-restoration.md)
- [ADR-0008: Transactional effect lifecycle](./decisions/0008-transactional-effect-lifecycle.md)
