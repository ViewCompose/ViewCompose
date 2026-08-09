---
translation_source: guides/overlays.md
translation_source_hash: 147ed70a926e9037a0d72d10791fb9462049fc8425b3e120115633023355a8e7
translation_status: current
---

# Overlay 定位与瞬态反馈

`Dialog`、`Popup` 和 `ModalBottomSheet` 使用绑定到 Session 的 overlay surface。`Snackbar`
和 `Toast` 共用宿主持有的瞬态反馈通道，因此即使两者在同一次渲染中声明，顺序也保持确定。

## Backend 选择

`viewcompose-overlay-android` 是中立 Android 传输层，负责 Dialog、PopupWindow、Toast、嵌套
Overlay Render Session、锚点观察与清理，不依赖 Material。中立 `setUiContent` 与 Android
Navigation Host 会显式选择它。

`viewcompose-overlay-material3-android` 只增加 Material Snackbar 与 Modal Bottom Sheet
Presenter。`setMaterial3UiContent` 显式选择该 Adapter；把产物放入 Runtime Classpath 不会改变
中立或 One UI Root。

`viewcompose-overlay-oneui7-android` 增加不依赖 Material 的 One UI Snackbar 与底部 Dialog
Presenter。One UI Root 继续使用中立 `setUiContent`，通过 `overlayHostFactory` 构造该 Adapter，
并把其 `integrationAttribution` 传给 `OneUi7Theme`。由于 One UI 不需要不同的 Root Context，
这里不会复制一套 One UI Activity/Fragment Host Extension。

自定义底层 Host 可以使用 `AndroidOverlayHostDefaults.androidOrNoOp`，但
Service Discovery 只接受一个中立 Provider，永远不选择设计系统。

当前设计系统 Attribution 会报告每种 Overlay 的 Transport、Presenter、Conformance 与 Fallback。
未安装 Adapter 的 One UI Theme 会把 Snackbar 与 Modal Bottom Sheet 报告为 `Unsupported`；显式
装配 Adapter 后升级为 `Equivalent`，始终不会静默回退 Material。

## Popup 定位

`Popup` 在 window 坐标中解析 anchor 和 popup。Android presenter 观察全局布局与滚动变化，
所以打开的 popup 会跟随移动的 anchor，而不是停留在首帧坐标。

```kotlin
Popup(
    visible = menuVisible,
    anchorId = "profile-menu-anchor",
    alignment = PopupAlignment.BelowEnd,
    overflowPolicy = PopupOverflowPolicy.FlipThenClamp,
    windowMargin = 8.dp,
    offsetY = 4.dp,
    onDismissRequest = { menuVisible = false },
) {
    ProfileMenu()
}
```

alignment 覆盖上方/下方、逻辑 start/end 侧和 anchor 中心；逻辑 start/end 按 anchor 的布局
方向解析。

溢出策略：

- `FlipThenClamp`：对侧溢出更少时先翻转，然后把结果限制在可见 window 内；这是默认值。
- `Clamp`：保持请求侧，只限制最终坐标。
- `None`：保留精确请求坐标并关闭平台裁切。

`PopupPositioner` 是平台无关定位契约。自定义宿主可使用自己的 anchor bounds、可见 viewport
和 popup 测量结果复用相同计算。

## Snackbar 与 Toast 队列

Snackbar 与 Toast 声明共用一条 FIFO 通道。请求由 `(render session, requestKey)` 标识；声明
保持可见时只投递一次。相同声明的重组不会重复入队；同 key 内容变化会替换该版本。

```kotlin
Snackbar(
    visible = saveMessageVisible,
    requestKey = "save-result",
    message = "Saved",
    queuePolicy = TransientFeedbackQueuePolicy.Enqueue,
    onDismiss = { reason ->
        saveMessageVisible = false
        log("save-result ended: $reason")
    },
)
```

队列策略：

- `Enqueue`：追加到活跃请求之后；
- `ReplaceCurrent`：关闭活跃请求，并把新请求放到队首；
- `ReplaceSameKey`：更新相同 key 的活跃或排队声明，否则正常入队；
- `DropIfBusy`：通道繁忙时消费请求但不展示。

关闭原因是结构化的 `Timeout`、`Action`、`Gesture`、`Replaced`、`Removed`、
`SessionCleared`、`Dropped` 或 `Platform`。移除声明或清理 RenderSession 都会关闭活跃平台
对象，并让下一个有效请求前进。

Android 并非在所有支持的 API 上都提供可靠 Toast 隐藏回调。因此 presenter 按平台短/长显示
时长推进通道；请求被显式移除或替换时取消该计时器。
