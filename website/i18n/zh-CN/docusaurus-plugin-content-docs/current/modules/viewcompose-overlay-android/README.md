---
translation_source: modules/viewcompose-overlay-android/README.md
translation_source_hash: 6c18cc7060394ba5bd964a1c7123842322ec2aa461a1e24d0fc1b359da42db8a
translation_status: current
---

# Overlay Android

`viewcompose-overlay-android` 是 ViewCompose Dialog、锚定 Popup、模态 Bottom Sheet、Snackbar
和 Toast 的可选 Android 呈现后端。声明式浮层协议、DSL、队列策略、定位模型及嵌套 Surface
会话位于 `viewcompose-widget-core`；本产物负责把这些契约映射为 Android 与 Material 平台窗口。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-overlay-android:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。窗口呈现和 Material 集成在 Alpha 版本之间仍可能演进。
- 平台：Android 库，最低 SDK 跟随仓库 Android 策略。
- 后端依赖 widget core 和 host Android，但核心渲染不反向依赖它。
- 不引入本产物时 renderer 仍可运行：浮层请求会使用 core 的 no-op host，并只输出一次诊断信息，
  不会导致应用启动失败。

## 安装与发现

本产物通过 Java `ServiceLoader` 注册 `AndroidOverlayHostFactoryProvider`。标准 ViewCompose
宿主调用 `OverlayHostDefaults.androidOrNoOp(rootView)`，因此添加依赖即可，无需初始化
Application 或声明 Manifest 组件。

自定义渲染宿主可以显式创建 `AndroidOverlayHost(rootView)`，再通过 `ProvideOverlayHost`
安装。每个已连接渲染根节点应创建一个 host；宿主销毁时清理各渲染会话，并且不得让 host
存活得比根 View 所属窗口更久。

所有 commit、dismiss 调用和平台回调都在 Android 主线程执行。

## 会话 Ownership 与协调

每个浮层 identity 由渲染会话 ID 和 DSL request key 共同组成。一次 commit 表示某个会话
完整的期望请求集合：

- 新增 key 会创建平台 handle；
- 修改同 key 请求时，支持更新的浮层会复用已有 handle；
- 省略之前的 key 只会关闭当前会话的 handle；
- 清理会话会关闭其 Surface 并移除等待中的瞬时反馈；
- 其他会话的请求不会被连带移除。

Dialog、Popup 和 Bottom Sheet 内容由嵌套 `OverlaySurfaceSession` 渲染。会话在声明内容时
捕获 ViewCompose Local，并持有自己的 renderer，直到平台 handle 被关闭。宿主主动清理时
会抑制 `onDismissRequest`；只有用户或平台窗口关闭才会请求应用状态移除声明式请求。

## Dialog

Dialog 使用透明 Android `Dialog` 和嵌套 ViewCompose Surface。返回键关闭和点击外部关闭
会映射到平台 cancelable 配置；逻辑 top、center、bottom 位置映射到 Window gravity，scrim
透明度会被限制在 Android 支持的 `0f..1f` 范围。

同 key 下修改内容、位置、关闭策略或 scrim 透明度时会更新已有窗口和渲染会话。移除请求时，
会先释放嵌套会话，再关闭窗口。

## 锚定 Popup

Popup 请求会在当前原生 View 树中解析 `anchorId`。Renderer 使用内部 tag 标记匹配的 DSL
锚点；Android 后端找到对应 View，并结合锚点边界、Popup 尺寸、窗口可见区域、布局方向、
逻辑对齐、偏移、边距和越界策略计算物理坐标。

Handle 会观察 attach、全局布局和滚动。内容变化后会重新测量，并且仅在位置或尺寸实际改变时
更新 `PopupWindow`。锚点暂时消失或尚无几何尺寸时，Popup 会隐藏但不会报告关闭；锚点恢复后
会重新显示。这覆盖 Lazy item 回收、滚动、IME 变化和窗口尺寸变化。

同一个渲染根节点内应使用稳定且唯一的锚点 ID。后端以深度优先顺序选取第一个匹配 View，
因此重复 ID 会让定位结果依赖 View 层级顺序。

## 模态 Bottom Sheet

模态 Sheet 使用 Material `BottomSheetDialog`。同 key 更新会保留 Dialog 与嵌套 Surface，
同时应用关闭策略、scrim 透明度、展开策略和内容变化。

`skipPartiallyExpanded` 映射为立即展开，并跳过 Material collapsed 中间状态。显式导航栏颜色
会应用到 Sheet Window；未覆盖时，后端恢复 Dialog 默认值并保留 Android 对比度强制策略。

## Snackbar 与 Toast 队列

Widget core 拥有共享 Snackbar/Toast 队列；本模块只提供平台 presenter。因此队列顺序、
替换、丢弃和会话移除在测试与 Android 运行时保持相同语义。

Material Snackbar 提供真实终态回调。点击 action 会先调用应用回调，随后平台回调把 `Action`、
`Timeout`、`Gesture`、`Replaced` 或通用平台原因报告给队列。框架主动关闭时提供的原因优先于
Material 回调事件。

Android Toast 没有可靠的完成回调。后端使用 Application Context，并通过主线程定时器近似
平台 short/long 时长。这样既能继续排空队列，也不会持有 Activity；但 timeout 完成不应被当作
系统绘制 Toast 已消失的逐帧精确信号。

## 资源与生命周期边界

- Dialog、Popup 与 Bottom Sheet 因属于根 View 的窗口而持有其 Context；清理渲染会话会释放
  平台窗口和嵌套 Renderer。
- Toast 只持有 Application Context。
- Popup handle 关闭时会移除 attach、layout 和 scroll listener。
- 隐藏的 Popup 在等待锚点时仍逻辑活跃；移除请求或清理会话仍会立即释放它。
- 后端有意不拥有 Activity、Fragment、ViewModel 或 SavedState 命名空间。

## 测试与自定义 Host

大多数应用测试应通过 widget-core 的 recording host 和队列 snapshot 验证声明式请求。验证
Android Window flag、Material 回调、Popup 几何或系统栏外观时，再使用 Robolectric 或真机测试。

自定义平台后端可以实现 widget-core 的 presenter 与 handle 接口，而无需依赖本产物。实现仍需
保留会话隔离、幂等更新、单次终态关闭回调和资源释放保证。

## 相关文档

- [Widget core 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-widget-core)
- [Android host 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-host-android)
- [浮层指南](https://docs.viewcompose.com/zh-CN/guides/overlays)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成式参考位于
[`viewcompose-overlay-android` API 目录](https://docs.viewcompose.com/api/viewcompose-overlay-android/current/)。

## 兼容性说明

`0.1.0-alpha01` 建立了可选 Service Provider 发现、会话隔离的平台 handle、嵌套 Surface 渲染、
锚定 Popup 恢复、Material Bottom Sheet 与 Snackbar，以及使用 Application Context 的 Toast。
Android Window 对象属于后端实现细节；应用状态应始终通过声明式浮层请求保持权威。
