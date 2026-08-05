---
translation_source: modules/viewcompose-image-glide/README.md
translation_source_hash: f58b7ce6c360ca817fd9a8e7fb0a8d6beb413405ad8e349e8cbe8160c83de35b
translation_status: current
---

# Glide 图像加载模块

`viewcompose-image-glide` 是 ViewCompose 图片节点的可选 Glide 5 适配器。它把平台无关的 source
与 request 契约转换为 Android `ImageView` 请求，同时避免 Renderer 或 Widget 模块依赖 Glide。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-image-glide:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。适配器边界已经建立，请求执行遵循 Glide 5。
- 平台：Android 7.0（API 24）及以上。
- 可选：没有该产物时，本地资源与核心 Renderer 仍可工作。
- 它依赖 `viewcompose-ui-contract` 与 `viewcompose-renderer`，二者不会反向依赖它。
- Glide 保持为实现依赖，因为适配器公开 API 中没有 Glide 类型。应用仍通过常规生成 API 与
  `AppGlideModule` 配置 Glide。

## 安装

创建一个 `GlideImageLoaderAdapter`，并把它传给 `ProvideImageLoader` 或负责图片加载的 Host 配置：

```kotlin
val imageLoader = GlideImageLoaderAdapter()

ProvideImageLoader(imageLoader) {
    Image(
        source = ImageSource.Url("https://example.test/banner.png"),
        contentDescription = "Banner",
    )
}
```

适配器会为每个请求解析 `Glide.with(imageView)`。这样既能保留 Glide 为挂载目标选择的生命周期
作用域，也能继续使用由应用所有的 registry、网络、缓存与默认请求配置。

## Source、Request 与目标模型

适配器接受 `ImageSource.Resource`、`Url`、`Uri`、`File` 和带 key 的 `Model`。每个
`UiImageRequest` 还携带可选的 Placeholder 与 Error 资源 ID，以及平台无关的解码尺寸、缓存、
过渡、content scale 和类型化扩展 options。空 source 的 fallback 会在 request 创建前由
Renderer 解析。适配器只接受包装 Android `ImageView` 的 Renderer Target，并忽略不归自己所有
的扩展类型。

`UiImageDecodeSize.Target` 保留 Glide 的目标尺寸解析，`Original` 映射到
`Target.SIZE_ORIGINAL`，固定 `UiDp` 边界会使用 request 中由 Renderer 捕获的密度进行转换，再以
物理像素尺寸交给 Glide。Content scale 会映射到 Glide 的 crop、fit、inside 或不变换请求选项。

## 缓存、过渡与所有权

默认缓存与过渡策略会保留应用的 Glide 配置。禁用内存缓存映射为
`skipMemoryCache(true)`，禁用磁盘缓存映射为 `DiskCacheStrategy.NONE`，显式的 `None` 或
`Crossfade` 会覆盖当前请求已配置的默认过渡。

适配器返回的可释放句柄会清理对应 Glide target request。Renderer 会在替换请求或移除挂载节点
前释放它。适配器不拥有目标 `ImageView`、Glide singleton、应用缓存或 `AppGlideModule`。

## 测试与运维

- 在 Glide 中配置认证、Model Loader、Decoder、缓存预算和可观测性。
- 测试快速重新绑定或回收行，确保已释放请求不会覆盖更新内容。
- 分别覆盖 Resource、URL、URI、File、带 key 的 Model、Placeholder、Error、缓存和过渡路径。
- 适配器特有选项应使用具有稳定身份的不可变类型化 request extension。

## 相关文档

- [UI Contract 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-contract)
- [Renderer 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer)
- [图片加载指南](https://docs.viewcompose.com/zh-CN/guides/image-loading)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-image-glide` API 树](https://docs.viewcompose.com/api/viewcompose-image-glide/current/)。

## 兼容性说明

`0.1.0-alpha01` 面向 Glide 5.0.7。它不会在声明式契约中暴露 Glide request builder，不会创建第二
层缓存，也不会替换应用级 Glide 配置。
