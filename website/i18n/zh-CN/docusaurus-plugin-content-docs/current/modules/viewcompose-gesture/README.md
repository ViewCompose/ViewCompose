---
translation_source: modules/viewcompose-gesture/README.md
translation_source_hash: ac5e04fa75424a6ab901e7c3c20a6e310516cc83eb689a58fa2ec21472c806ee
translation_status: current
---

# Gesture 模块

`viewcompose-gesture` 是 ViewCompose 面向组合的手势 DSL。它向 `Modifier` 添加原始 Pointer、组合
点击、拖动、锚点拖动、Transform、优先级与嵌套滚动元素，并为 Renderer 回传提供可记忆的回调和
状态容器。该模块声明行为；Android Renderer 拥有原生指针流和识别引擎。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="gesture-module-dependency" sample_id="module.gesture-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-gesture:0.1.0-alpha05")
}
```

- 稳定性：**Alpha**。Modifier 形态与当前状态语义已经审查并测试；手势仲裁和更完整的 Mutation
  API 在 Alpha 版本间仍可能演进。
- 平台：Android Library API，但公开值保持 Renderer-neutral。
- Gesture Core、Runtime、UI Contract 和 UI Foundation 会被传递暴露，因为它们的策略、State、
  Modifier 和 Builder 类型出现在公开手势 API 中。
- 大多数应用应依赖本产物，而不是直接依赖 Gesture Core。

## 识别所有权

手势 Modifier 是不可变描述。构建时不会安装 Android Listener，也不会自行识别输入。节点挂载后
由 Renderer 解释它们，并拥有 `MotionEvent`、Pointer ID、`VelocityTracker`、Touch Slop、布局
方向解析、识别器竞争、嵌套滚动顺序，以及替换或释放引起的取消。

回调在 Renderer 派发线程同步执行，Android 下一般是 UI 线程。回调应保持短小，把阻塞或挂起工作
交给应用拥有的协程。距离和 Pan 通常是物理像素；拖动终止速度是像素/秒。

## Pointer 与点击输入

`pointerInput` 暴露归一化的 Down、Move、Up 与 Cancel 事件，不暴露原生事件对象。Key 是 Handler
的身份输入。仅在后续识别应视为已消费时返回 `Consumed`。

`combinedClickable` 让一个 Renderer Recognizer 协调单击、双击与长按。计时和 Slop 来自
Android。禁用或没有任何回调时会原样返回 Modifier，不增加空闲原生识别器。

{/* compiled-region source="viewcompose-gesture/src/test/samples/com/viewcompose/gesture/samples/GestureSamples.kt" region="gesture-combined-click" sample_id="module.gesture-combined-click" build_target=":viewcompose-gesture:compileDebugUnitTestKotlin" */}
```kotlin
val actions = Modifier.combinedClickable(
    onClick = { openItem() },
    onLongClick = { openContextMenu() },
)
```

## 一维拖动

`rememberDraggableState` 返回稳定对象，同时始终转发给最新回调。它不累计或限制 Offset，规则由
应用状态拥有。`draggable` 声明方向和生命周期回调。识别跨过 Slop 并产生本地移动后才调用 Start；
正常 Stop 携带有符号的轴速度，取消使用独立回调。

Free 方向锁定到主轴。取消原因可能是系统取消、Transform 接管、Pointer 被消费、Modifier 替换
或释放。不能把取消当作零速度的正常 Stop。

## 锚点拖动

`DraggableAnchors` 是经过验证、不可变、非空的有限且唯一像素 Offset 集。输入顺序会自动排序。
语义值允许重复，但建议保持唯一，因为 `offsetOf` 选择第一个匹配；`valueAt` 使用 Float 精确相等。

{/* compiled-region source="viewcompose-gesture/src/test/samples/com/viewcompose/gesture/samples/GestureSamples.kt" region="gesture-anchored-drag" sample_id="module.gesture-anchored-drag" build_target=":viewcompose-gesture:compileDebugUnitTestKotlin" */}
```kotlin
val anchors = draggableAnchors<SheetValue> {
    anchor(0f, SheetValue.Collapsed)
    anchor(480f, SheetValue.Expanded)
}
val sheet = rememberAnchoredDraggableState(SheetValue.Collapsed)
val modifier = Modifier.anchoredDraggable(
    state = sheet,
    anchors = anchors,
    orientation = GestureOrientation.Vertical,
)
```

Modifier 每次组合调用都会把最新 Anchors 同步安装进 State。当前值仍存在时，等价 Anchor 集合的
重新安装会保留正在拖动的 Offset；值消失时，最接近当前视觉 Offset 的 Anchor 成为当前值。原始
移动更新限制在已安装范围内的 Offset。正常结束会提交 Renderer 选择的最近目标，并可通过
`onValueSettled` 报告；取消会先恢复最后一次已提交 Anchor，再调用取消 Callback。当前版本立即
完成 Settle，没有动画，因此 `targetValue` 通常与 `currentValue` 同时变化。Q3 State 契约会为
每次 Delta、Snap、Settle、取消或 Anchor 对齐，通过一次 Snapshot Transaction 发布 Semantic
Value、Target、Offset 与 Dragging 状态。Mutation 仍须串行运行在所属 UI 线程。

`rememberAnchoredDraggableState` 只在首次 Remember 时读取 `initialValue`。之后改变参数不会重置；
应显式调用 `snapTo`。Snap 到当前 Anchors 不包含的值会保存语义值并清除 Offset，直到下一次
Anchor 对齐。Anchored Drag 只接受 Horizontal 或 Vertical。

## 受控双状态拖动

`rememberToggleDragState` 与 `toggleDraggable` 会把 Anchored Drag 适配为由调用方持有状态的
双状态组件，例如 Design System 自有的 Switch。Checked Anchor 是相对于 Unchecked 零点的有符号
物理像素偏移；对外的 `progress` 始终是从 `0f` Unchecked 到 `1f` Checked 的逻辑进度，因此 RTL
可以传入负数 Checked 偏移，而绘制逻辑不需要反转。

{/* compiled-region source="viewcompose-gesture/src/test/samples/com/viewcompose/gesture/samples/GestureSamples.kt" region="gesture-toggle-drag" sample_id="module.gesture-toggle-drag" build_target=":viewcompose-gesture:compileDebugUnitTestKotlin" */}
```kotlin
val drag = rememberToggleDragState(
    checked = checked,
    checkedAnchorOffsetPx = density.toPx(if (rtl) (-20).dp else 20.dp),
    onCheckedChange = onCheckedChange,
)
val target = Modifier
    .clickable { onCheckedChange(!checked) }
    .toggleDraggable(drag)
```

移动尚未形成 Drag 时，Renderer 会把 Tap 留给 Click Modifier。已识别的 Drag 会消费结束事件，按
位置或速度 Settle，并只请求一次替换状态。组件继续拥有几何、Density/Layout Direction 转换、
Settled 动画、Checked Semantics 与持久化。拖动中使用 `isDragging` 直接绘制 Follow-finger 进度，
空闲时使用 Design System 自己的 Motion 契约。`lastCompletion` 会在替换状态 Callback 之前同步
发布，并保留正常 Settle 或取消恢复端点之前的逻辑进度。组件可用其中逐 State 递增的序列号与
起始进度继续 Settled 动画，避免短暂跳回旧端点。

## Transform 手势

`rememberTransformableState` 转发增量乘法 Zoom、物理像素 Pan 和顺时针角度 Rotation。它不累计、
限制或动画化 Transform。`transformable` 提供 Start、正常 Stop 与 Cancel 回调。多指 Transform
激活可以接管并取消同一派发路径上的活跃 Drag。

应用状态应累计 Scale 和 Translation 并应用自身边界。避免保留逐事件对象，或每个 Transform
Delta 都启动新协程。

## 手势优先级与嵌套滚动

`gesturePriority(High)` 请求更早的识别机会，但不保证消费。高优先级识别器拒绝事件后，其他
识别器仍可运行。

`nestedScroll` 把 `NestedScrollConnection` 连接到已挂载的祖先链。Pre 回调在 Child 消费前，
Post 回调在之后。可选 `NestedScrollDispatcher` 支持应用主动派发；未连接时消费零，Renderer
释放会断开旧 Connector，且不会干扰更新的 Mount。Connection 只能返回实际消费的距离或速度。

## 测试手势 UI

- 将状态累计和 Anchor 替换与原生识别分开做单元测试。
- 测试 Start、Delta、正常 Stop 与每种取消原因的回调顺序。
- 在 Renderer 或设备测试中覆盖 Touch Slop、Drag/Transform 竞争和 RTL Swipe 方向。
- 测试 Nested Scroll 的 Pre 由外到内、Post 由内到外，以及 Renderer 边界的过度消费限制。
- 用新 Lambda 重组，验证 Remember 的 State 转发到最新回调。

模块测试覆盖 Modifier 编码、无操作点击声明、Drag 与 Transform 转发、Anchored
边界/重组/取消/Settle 行为、受控 LTR/RTL Toggle 进度、Settle 前 Toggle Completion 快照、
非法 Free 方向、优先级编码和 Nested Scroll 连接。

## 相关文档

- [Gesture Core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-gesture-core)
- [UI Contract 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-contract)
- [Renderer 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer-android)
- [协调嵌套滚动](../../guides/nested-scroll.md)
- [Modifier 架构](../../architecture/modifier.md)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-gesture` API 树](https://docs.viewcompose.com/api/viewcompose-gesture/current/)。

## 兼容性说明

`0.1.0-alpha04` 建立同步回调传递、Latest-lambda Remember State、Renderer 所有识别、立即 Anchor
Settle、显式取消和可断开的 Nested Scroll 派发。API 名称类似 Jetpack Compose Gesture Modifier
不代表具有相同的挂起 Mutation、`MutatorMutex` 或动画行为。
