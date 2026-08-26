---
translation_source: modules/viewcompose-shadow-android/README.md
translation_source_hash: 55e09ee7cf243f17cd77fd944a4d9a135bb34abe5ba4617fe8ee43116401de96
translation_status: current
---

# Android 阴影模块

`viewcompose-shadow-android` 是 ViewCompose 外阴影与内阴影的可选 Android Backend。它把声明式
Shadow Modifier 解析成像素规格，栅格化精确的多层效果，并接入 Renderer 的 Parent Drawing Plane。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="shadow-dependency" sample_id="module.shadow-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-shadow-android:0.1.0-alpha04")
}
```

- 稳定性：**Alpha**。Modifier Contract 在 Alpha 线内稳定；Backend 选择与栅格保真可能随设备证据演进。
- 平台：Android 7.0（API 24）及以上。
- 可选：没有该产物时，核心渲染仍可编译并运行。
- Renderer 与 UI Contract 会被传递暴露，因为它们的 Decoration 与 Drawing Contract 类型
  出现在公开 Backend API 中；核心模块不会反向依赖本产物。

## 安装与依赖边界

打包该产物会通过 `ServiceLoader` 注册 `ShadowViewDecorationBackend`。第一个非空 Decoration
Request 需要 Backend 时最多发现一次。应用也可在初始化阶段调用 `ShadowDecorationLayer.install()`。

Backend 使用 Renderer 已有的 Decoration Host Contract。它不会替换每个 Root Layout，也不会给
被装饰 Child 增加 Wrapper `View`。已经参与 Decoration Drawing 的 Parent 只会对解析后 Presence
非空的 Child，在普通绘制前后调用 Backend。

没有该产物时，Shadow Modifier 会退化为缺少可选 Backend；普通 Node、Layout、Input 与 Rendering
继续正常工作。

## 解析与顺序

`ShadowSpecResolver` 与 `InnerShadowSpecResolver` 在 Android Binding 边界把 dp 一次性转换为物理
像素。Shadow Group 的显式 Shape 优先于 Node 默认 Shape，二者都没有时使用矩形。Element 与 Layer
顺序保持不变。

外阴影紧邻 Child Content 之前绘制。内阴影在 Child 的 Background、Content、Subtree 和 Foreground
之后绘制。两个 Plane 共享 Child 的 Sibling/z 顺序，并跟随 Matrix、Alpha、相对 Scroll 位置和
Layout Direction，不改变 Layout 与 Input Dispatch。

## 栅格与内存模型

外阴影和内阴影默认各用 8 MiB LRU Cache。单个 Raster 超过 32 MiB 或任一 Bitmap 维度超过 8192
像素时会跳过。大于 Cache Budget 但仍有效的 Entry 可供当前 Draw 使用，但不会保留。

Cache Identity 包含 Content Width/Height、Layout Direction 和完整 Resolved Spec。Translation 与普通
Invalidation 复用 Bitmap；Size、Density、Outline 或 Shadow 变化会产生新 Key。Rasterizer 限定在 UI
线程。返回的 Bitmap 归 Cache 所有，不能修改或 recycle。

显式内存压力处理可调用 `ShadowDecorationLayer.clearCache()` 立即驱逐 Raster 与 Display-list Entry；
诊断计数会保留。

## 回放策略

- `Auto`：跨设备证据形成前使用直接精确 Bitmap 回放。
- `ExactBitmap`：显式使用 `Canvas.drawBitmap`。
- `RenderNodeDisplayList`：在 Android 10+ Hardware Canvas 上缓存 Bitmap 的 RenderNode 回放。

API 29 以下、Software Canvas 或 RenderNode 运行时失败都会回退到 Bitmap。策略只影响 Bitmap 回放，
不会替换精确栅格化。可通过 `backendStats()`、`cacheStats()` 与 `innerCacheStats()` 诊断。

验证请求策略与已知平台、Canvas 能力的组合时，应使用确定性的 Selector。应用代码通过
`ShadowDecorationLayer` 修改实时策略，并且必须在隔离实验结束后恢复 `Auto`。

{/* compiled-region source="viewcompose-shadow-android/src/test/samples/com/viewcompose/shadow/android/samples/ShadowAndroidSamples.kt" region="shadow-backend-selection" sample_id="module.shadow-backend-selection" build_target=":viewcompose-shadow-android:compileDebugUnitTestKotlin" */}
```kotlin
fun selectShadowBackendSample(): ShadowRenderBackendDecision {
    return ShadowRenderBackendSelector.select(
        policy = ShadowRenderPolicy.RenderNodeDisplayList,
        sdkInt = 35,
        hardwareAccelerated = true,
    )
}
```

## 测试与运维

- Resolver Output 与像素结果分开测试。
- Blur、Spread、Offset、Corner Family、RTL 与 Clip 保真使用截图或真机测试。
- 显式选择 RenderNode 前覆盖 Software Canvas 与 API 29 以下回退。
- 大型 Surface 关注 Oversized Skip 与 Cached Bytes。
- 需要确定性启动时，在第一次装饰渲染前安装 Backend。

## 相关文档

- [高级阴影指南](../../guides/shadows.md)
- [Modifier 架构](../../architecture/modifier.md)
- [Renderer 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer-android)
- [UI Contract 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-contract)
- [Graphics 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-graphics)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-shadow-android` API 树](https://docs.viewcompose.com/api/viewcompose-shadow-android/current/)。

## 兼容性说明

`0.1.0-alpha03` 建立可选 ServiceLoader 发现、无 Wrapper Decoration Plane、精确多层栅格、受字节限制
的 Cache，以及带 Capability Fallback 的显式 RenderNode 回放。它不承诺等同 Native Elevation，也不是
通用 RenderEffect Pipeline。
