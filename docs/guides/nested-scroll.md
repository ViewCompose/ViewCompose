# Nested Scroll

## 1. Contract

`Modifier.nestedScroll(connection, dispatcher)` installs one platform-independent connection with
four synchronous phases:

1. `onPreScroll(available, source)`
2. child/local consumption
3. `onPostScroll(consumed, available, source)`
4. `onPreFling` / `onPostFling`

`NestedScrollSource` distinguishes direct user input, fling continuation, and imperative side
effects. Consumption uses `ScrollDelta` and `ScrollVelocity`, and the renderer clamps every result
to the sign and magnitude of the corresponding available value. Invalid or non-finite consumption
is treated as zero.

## 2. Propagation order

- Pre-scroll and pre-fling travel from the outermost connection toward the child.
- Post-scroll and post-fling travel from the child back toward outer connections.
- A child receives only the remainder left after pre-consumption.
- A parent receives the child's consumption plus any remainder offered during the post phase.

Multiple `nestedScroll` modifiers create multiple transparent hosts and preserve modifier order.

## 3. Android mapping

The renderer inserts a transparent `NestedScrollHost` around a node carrying the modifier. The
host implements AndroidX `NestedScrollingParent3` and participates upward through
`NestedScrollingChildHelper`.

This allows a connection on a normal `Box`, `Column`, or other declarative node to coordinate:

- `LazyColumn`, `LazyRow`, and `LazyVerticalGrid`
- `HorizontalPager` and `VerticalPager`
- `ScrollableColumn` through `NestedScrollView`
- `ScrollableRow` through its horizontal nested-child bridge
- `PullToRefresh`
- framework `draggable`, `anchoredDraggable`, and transform pan

Native views that already implement Android nested scrolling join the same chain automatically.
Arbitrary `AndroidView` children that do not implement Android nested scrolling cannot emit native
scroll phases; they can use a `NestedScrollDispatcher` explicitly.

## 4. Example

```kotlin
val dispatcher = remember { NestedScrollDispatcher() }
val connection = remember {
    object : NestedScrollConnection {
        override fun onPreScroll(
            available: ScrollDelta,
            source: NestedScrollSource,
        ): ScrollDelta {
            val collapse = collapseToolbarBy(available.y)
            return ScrollDelta(x = 0f, y = collapse)
        }

        override fun onPostFling(
            consumed: ScrollVelocity,
            available: ScrollVelocity,
        ): ScrollVelocity {
            settleToolbar(available.y)
            return ScrollVelocity.Zero
        }
    }
}

Column(
    modifier = Modifier.nestedScroll(
        connection = connection,
        dispatcher = dispatcher,
    ),
) {
    LazyColumn(/* ... */)
}
```

For a custom side effect, call `dispatcher.dispatchPreScroll(...)`, consume the remainder locally,
then call `dispatcher.dispatchPostScroll(...)`.

## 5. Native fling limitation

Android's legacy nested-fling callbacks report consumption as a Boolean rather than a velocity
amount. Partial velocity is preserved across ViewCompose connections and imperative dispatchers;
when crossing an arbitrary native parent, `true` necessarily means that parent consumed the
remaining fling.
