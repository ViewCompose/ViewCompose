---
translation_source: guides/nested-scroll.md
translation_source_hash: 870b2e1a6993df7a4a4c4f09cabeb4b89fb0196bf91115f63cfb02970257c2b2
translation_status: current
---

# 嵌套滚动

## 1. 契约

`Modifier.nestedScroll(connection, dispatcher)` 安装一个平台无关 connection，包含四个同步阶段：

1. `onPreScroll(available, source)`；
2. child/本地消费；
3. `onPostScroll(consumed, available, source)`；
4. `onPreFling` / `onPostFling`。

`NestedScrollSource` 区分直接用户输入、fling 延续和命令式副作用。消费量使用 `ScrollDelta`
与 `ScrollVelocity`；渲染器把每个结果限制在对应 available 值的方向和大小之内，无效或非有限
数值按零处理。

## 2. 传播顺序

- pre-scroll 和 pre-fling 从最外层 connection 向 child 传播；
- post-scroll 和 post-fling 从 child 返回外层 connection；
- child 只收到 pre-consumption 后的余量；
- post 阶段中，parent 收到 child 消费量以及剩余量。

多个 `nestedScroll` modifier 会生成多个透明宿主，并保持 modifier 顺序。

## 3. Android 映射

渲染器在带有该 modifier 的节点外加入透明 `NestedScrollHost`。宿主实现 AndroidX
`NestedScrollingParent3`，并通过 `NestedScrollingChildHelper` 向上参与链路。

因此普通 `Box`、`Column` 等声明节点上的 connection 可以协调：

- `LazyColumn`、`LazyRow`、`LazyVerticalGrid`；
- `HorizontalPager`、`VerticalPager`；
- 基于 `NestedScrollView` 的 `ScrollableColumn`；
- 使用水平 nested-child bridge 的 `ScrollableRow`；
- `PullToRefresh`；
- 框架 `draggable`、`anchoredDraggable` 和 transform pan。

已实现 Android nested scrolling 的原生 View 会自动加入同一链路。其他 `AndroidView` child
不能发出原生滚动阶段，可显式使用 `NestedScrollDispatcher`。

## 4. 示例

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

自定义副作用先调用 `dispatcher.dispatchPreScroll(...)`，在本地消费余量，再调用
`dispatcher.dispatchPostScroll(...)`。

## 5. 原生 fling 限制

Android 旧式 nested-fling 回调只用 Boolean 报告是否消费，无法返回速度大小。ViewCompose
connection 与命令式 dispatcher 之间会保留部分速度；跨越任意原生 parent 时，`true` 必然表示
该 parent 消费了剩余 fling。
