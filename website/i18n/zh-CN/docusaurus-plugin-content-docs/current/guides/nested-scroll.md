---
translation_source: guides/nested-scroll.md
translation_source_hash: 54fb72436802acb7e57857bfea5148f7702d4db09f2be5e6bf6ca61f1190a688
translation_status: current
---

# 协调嵌套滚动

## 消费祖先效果

把一个稳定的 `NestedScrollConnection` 附加到拥有该效果的祖先。pre 回调在子项消费之前
运行；post 回调会收到子项已消费量和剩余量。只返回实际消费的带方向距离或速度。

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/FocusAndNestedScrollGuideSamples.kt" region="nested-scroll-toolbar" sample_id="guide.nested-scroll-toolbar" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
fun UiTreeBuilder.CollapsingToolbar(
    collapseBy: (deltaY: Float) -> Float,
) {
    val latestCollapseBy = rememberUpdatedState(collapseBy)
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: ScrollDelta,
                source: NestedScrollSource,
            ): ScrollDelta {
                return ScrollDelta(
                    x = 0f,
                    y = latestCollapseBy.value(available.y),
                )
            }
        }
    }

    Column(modifier = Modifier.nestedScroll(connection)) {
        Text("Collapsing toolbar")
        ScrollableColumn {
            repeat(40) { index -> Text("Row $index") }
        }
    }
}
```

在 `collapseBy` 内限制应用状态；Android Renderer 还会拒绝非有限值或超量消费结果。当用户
输入、fling 延续和命令式副作用需要不同策略时，使用 `NestedScrollSource` 区分来源。

## 分发自定义滚动来源

当自定义手势或程序代码必须进入相同父链时，向 `nestedScroll` 传入稳定的
`NestedScrollDispatcher`。先分发 pre-scroll，在本地消费余量，再以本地消费量和剩余量分发
post-scroll。速度使用对应的 pre/post fling 对。

Lazy 集合、Pager、Eager 滚动容器、PullToRefresh，以及框架 Drag 或 Transform Pan 都参与同一
条链。只有实现 Android Nested Scrolling 的原生 `AndroidView` 才会自动加入，否则应显式
分发。阶段顺序、Modifier 顺序、AndroidX 映射和旧式原生 fling 限制见
[Modifier 架构](../architecture/modifier.md)。
