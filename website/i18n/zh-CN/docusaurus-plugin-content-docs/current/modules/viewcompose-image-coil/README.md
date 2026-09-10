---
translation_source: modules/viewcompose-image-coil/README.md
translation_source_hash: 2fe4cee040d0b6ecec5bcfe5a393a43812aefd25d8ee19c2f30fc1f8e4e91012
translation_status: current
---

# Coil 图像加载模块

`viewcompose-image-coil` 是 ViewCompose 图片节点的可选 Coil 3 适配器。它把平台无关的 source
与 request 契约转换为 Android `ImageView` 请求，同时避免 Renderer 或 Widget 模块依赖具体网络与图片加载实现。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="image-coil-dependency" sample_id="module.image-coil-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-image-coil:0.1.0-alpha05")
}
```

- 稳定性：**Alpha**。适配器边界已经建立，请求策略遵循 Coil 3。
- 平台：Android 7.0（API 24）及以上。
- 可选：没有该产物时，本地资源与核心 Renderer 仍可工作。
- UI Contract 会被传递暴露，因为平台无关图片 Request 类型出现在公开 Adapter API 中。
  Renderer 保持为实现依赖；二者都不会反向依赖本产物。
- Coil Core 是 API 依赖，因为 `CoilImageLoaderAdapter` 的公开构造参数使用
  `coil3.ImageLoader`；OkHttp fetcher 仍是实现依赖。

## 安装

使用应用级 Coil `ImageLoader` 创建一个 `CoilImageLoaderAdapter`，并把它传给 `ProvideImageLoader`
或负责图片加载的 Host 配置。共享 Loader 能保留应用网络配置，并最大化内存与磁盘缓存复用。
适配器不会创建或关闭 Loader，因此创建过程和应用生命周期清理都会明确保留在集成边界。

## Source、Request 与目标模型

适配器接受 `ImageSource.Resource`、`Url`、`Uri`、`File` 和带 key 的 `Model`。每个 `UiImageRequest`
还携带可选的 Placeholder 与 Error 资源 ID，以及平台无关的解码尺寸、缓存、过渡、content scale
和类型化扩展 options。空 source 的 fallback 会在 request 创建前由 Renderer 解析。适配器只接受
包装 Android `ImageView` 的 Renderer Target，并忽略不归自己所有的扩展类型。固定 `UiDp` 解码
边界会使用 request 中由 Renderer 捕获的密度进行转换，再以物理像素尺寸交给 Coil。

加载是异步的。适配器会为已启动的 Coil 请求返回可释放句柄；渲染器负责在替换请求或移除挂载
节点前释放句柄。传入的 Coil `ImageLoader` 与单个 View 的生命周期相互独立。

## 缓存与所有权

Coil 拥有网络和远程缓存策略，适配器不增加第二层缓存。当前未发布实现对 Android 本地资源使用
`resourceCacheScope`、资源 ID 与修订号组成内存缓存键；共享 Loader 的不同挂载宿主即使修订号相同也不会冲突。
缺少作用域时禁用资源内存缓存。由于临时宿主或主题身份不是持久内容指纹，本地资源磁盘缓存被禁用。
远程主图即使有资源 Placeholder，也沿用 Coil 的缓存标识。`ImageLoader` 的关闭仍由调用方负责。

资源 ID 会原样转发。无效资源与请求失败遵循 Android 和 Coil 的普通 Error 行为。

## 测试与运维

- 复用一个应用级 Loader，让跨页面缓存行为保持确定。
- 测试快速重新绑定或回收列表项，验证旧请求不会覆盖新数据。
- 分别覆盖 Placeholder、Error、Resource、带 key 的 Model、缓存策略与离线路径。
- 在注入的 Coil Loader 上配置认证、Interceptor、缓存预算与可观测性，而不是放进 ViewCompose。

## 相关文档

- [UI Contract 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-contract)
- [Renderer 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer-android)
- [图片加载指南](https://docs.viewcompose.com/zh-CN/guides/image-loading)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-image-coil` API 树](https://docs.viewcompose.com/api/viewcompose-image-coil/current/)。

## 兼容性说明

`0.1.0-alpha03` 直接把平台无关请求转发给 Coil 3。它不会在声明式图片契约中暴露 Coil
Transformation，不管理全局 Loader，也不承诺独立于所配置 Coil 版本的缓存策略。

## 当前检出版本的回归证据

审查基线将相同资源 ID 与宿主内修订号映射为相同的共享缓存键。候选版本的 Gradle 适配器套件通过
7 项测试，失败和跳过均为零，覆盖不同挂载作用域、修订变化、缺少作用域时的内存策略，以及资源磁盘
策略禁用。Glide 套件另验证 Android 夜间模式签名不同，并使用目标主题。测试请求中的跨宿主键冲突得到消除，
正确性结论为改进。跨挂载缓存复用和资源磁盘缓存按设计减少，因此性能结论在测量前尚不确定。
Robolectric 请求构建不代表解码像素或真机验收；后续验证真机主题、资源外观与缓存命中率。
