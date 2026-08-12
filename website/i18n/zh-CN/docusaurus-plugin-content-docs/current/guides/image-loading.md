---
title: 图片加载
slug: /guides/image-loading
translation_source: guides/image-loading.md
translation_source_hash: b755e96a7b0d7ece51e2ea375a3791f7fd8e383eb94ec8172a90c183d1876832
translation_status: current
---

# 图片加载

ViewCompose 把图片声明、Android View 绑定和图片解码分在不同层中。UI Contract 提供
`ImageSource` 与 `UiImageRequest`，Widget 边界注入可选的 `UiImageLoader`，Renderer 负责每个
`ImageView` 上操作的生命周期。

## 选择最小集成

| 需求 | 配置 |
| --- | --- |
| 内置 drawable 或 resource | 使用 `ImageSource.Resource`，不需要 loader。 |
| 自定义解码器或测试 fake | 实现 `UiImageLoader`，并返回可释放的 `UiImageLoadHandle`。 |
| Coil 3 网络、缓存和解码 | 添加 `viewcompose-image-coil`，安装 `CoilImageLoaderAdapter`。 |
| Glide 5 网络、缓存和解码 | 添加 `viewcompose-image-glide` 并安装 `GlideImageLoaderAdapter`。 |
| 其他平台解码器 | 保持适配器位于独立可选模块，在 Android 边界映射通用 request。 |

核心模块不假设网络、缓存或解码器。没有 loader 是合法配置，不是错误。

## 安装 loader

把 loader 安装在真正需要它的最小子树上。Provider 会在 `Image` 或 `Icon` 发射
`NodeSpec` 时读取：

```kotlin
val imageLoader = CoilImageLoaderAdapter(applicationCoilImageLoader)

ProvideImageLoader(imageLoader) {
    Image(
        source = ImageSource.Url("https://example.test/banner.png"),
        contentDescription = "Banner",
        placeholder = ImageSource.Resource(R.drawable.image_placeholder),
        error = ImageSource.Resource(R.drawable.image_error),
        fallback = ImageSource.Resource(R.drawable.image_fallback),
        requestOptions = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Target,
            memoryCachePolicy = UiImageCachePolicy.Default,
            diskCachePolicy = UiImageCachePolicy.Default,
            transition = UiImageTransition.Crossfade(durationMillis = 180),
        ),
    )
}
```

`ImageSource.Resource` 可以在有 loader 或没有 loader 时使用。`Url`、`Uri`、`File` 和 `Model`
需要 loader 才能得到解码结果。没有 loader 时，Renderer 会清理旧操作，并直接应用 fallback、
error 或 placeholder resource。

## Source 与 Request 策略

`ImageSource` 保持有意精简，不保存解码器特有状态：

- `Resource` 标识 Android drawable resource；
- `Url` 保存绝对 HTTP 或 HTTPS URL；
- `Uri` 保存使用任意 loader 支持 scheme 的绝对 URI；
- `File` 保存非空文件路径；
- `Model` 保存任意适配器 payload 和显式稳定 key。

`UiImageRequestOptions` 携带可由多个适配器共享的策略：

- `UiImageDecodeSize.Target`、`Original`，或用 `UiDp` 表达的正数逻辑 `Fixed` 尺寸；
- 相互独立的内存与磁盘默认/禁用缓存策略；
- 默认、无过渡或 crossfade 过渡；以及
- 以具体类型和 `stableKey` 共同标识身份的不可变类型化扩展列表。

适配器忽略不归自己所有的扩展类型。影响加载的选项变化时，扩展的 `stableKey` 必须同步变化。
Placeholder 与 error 是 resource ID，而不是 drawable 实例。Fallback 有意属于 node 状态而非
request 状态：仅当 `source == null` 时由 Renderer 应用，并且不会为该场景启动 loader。这样
request 保持可移植，node spec 也不会持有 View 或 Drawable。Renderer 会把子树捕获的
`UiDensity` 复制到每个 `UiImageRequest`；适配器必须使用它，把 `Fixed` 解码边界转换为解码器
所需的物理像素。

只要 Source、Placeholder、Error 或 Fallback 使用资源，Renderer 还会复制子树捕获的
`resourceRevision`。Locale、Night、Density 或主题资源变化后，即使整数资源 ID 相等也能重新加载。
第一方 Coil 与 Glide Adapter 会把该版本加入 Primary Resource Cache 标识，同时保持纯远端 Cache
标识不变。

## 生命周期与回收 View

Renderer 在 UI 线程执行图片绑定，并把返回的句柄存放在挂载的 `ImageView` 上。完全相同的
request 会保留已加载结果与当前句柄。当 source、loader 或 request option 变化时，它会：

1. 清理绑定 tag 并释放旧句柄；
2. 应用新的 placeholder 状态；
3. 启动新的 request；
4. 仅在 loader 成功启动后保存新句柄。

节点移除、挂载树释放或未提交候选回滚都会先清理 tag，再执行释放。loader 必须让句柄具备
幂等释放能力，并停止可能写入已释放或已回收 View 的回调。注入的 loader 仍归调用方所有；
Renderer 释放不能关闭它。

这套顺序用于防止 Lazy 列表和快速复用行中的乱序工作覆盖新内容。句柄释放后不要继续持有
`ImageView`，也不要用解码器的全局取消 API 替代按 request 的句柄。

## 实现适配器

适配器需要验证 target，映射声明支持的 `ImageSource` 子类型，转发通用 request options，启动
解码器工作，并返回只取消该工作的句柄：

```kotlin
class TestImageLoader : UiImageLoader {
    override fun load(target: UiImageTarget, request: UiImageRequest): UiImageLoadHandle {
        val imageView = (target as PlatformUiImageTarget).target as ImageView
        imageView.setImageResource(R.drawable.image_placeholder)
        return UiImageLoadHandle { /* 只取消当前 request */ }
    }
}
```

生产适配器还应测试 target 验证、每种 source 映射、解码尺寸、缓存和过渡策略、幂等释放，以及
注入解码器的所有权。适配器不能增加第二层框架缓存，也不能改变 `ImageSource.Model` 的相等
语义。

## Coil 3 集成

`viewcompose-image-coil` 是已发布的可选适配器。应用负责网络和缓存策略时，应使用应用级 Coil
`ImageLoader`：

```kotlin
val imageLoader = CoilImageLoaderAdapter(applicationCoilImageLoader)
ProvideImageLoader(imageLoader) {
    Image(source = ImageSource.Uri(contentUri), contentDescription = "Content")
}
```

适配器把 placeholder、error resource、尺寸、缓存策略、过渡和 content scale 转发给 Coil。
空 source 的 fallback 仍由 Renderer 负责；适配器永远不会关闭传入的 `ImageLoader`。

具体兼容性与运维说明请参阅[Image Coil 模块手册](../modules/viewcompose-image-coil/README.md)。

## Glide 5 集成

`viewcompose-image-glide` 提供 `GlideImageLoaderAdapter`。它从每个目标 `ImageView` 解析生命周期
关联的 `RequestManager`，请求则继承应用的 `AppGlideModule`、registry、缓存和默认请求配置：

```kotlin
val imageLoader = GlideImageLoaderAdapter()
ProvideImageLoader(imageLoader) {
    Image(
        source = ImageSource.File(file),
        contentDescription = "Downloaded image",
        requestOptions = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Fixed(width = 640.dp, height = 360.dp),
            transition = UiImageTransition.None,
        ),
    )
}
```

`Default` 过渡保留 Glide 已配置的默认值；`None` 与 `Crossfade` 会显式覆盖它。Target、原始
尺寸和按密度解析后的固定解码尺寸会直接映射到 Glide，不增加框架缓存。适配器只清理其返回句柄
所代表的 request target，不拥有目标 `ImageView` 或 Glide singleton。

兼容性、所有权与运维说明见
[Image Glide 模块手册](https://docs.viewcompose.com/zh-CN/modules/viewcompose-image-glide)。

## 验证清单

- 在没有 loader 时验证 resource-only 渲染。
- 验证自定义 loader 收到 platform target 和完整通用 request。
- 在首个操作完成前，把一行从一个 source 重新绑定到另一个 source。
- 释放挂载树，并确认延迟回调不会写入旧 `ImageView`。
- 测试 placeholder、error、fallback、禁用缓存、显式尺寸和过渡行为。
- 保持适配器依赖可选；UI Contract、UI Foundation 与 Renderer 必须在没有它们时仍能编译。

## 相关文档

- [UI Contract 模块](../modules/viewcompose-ui-contract/README.md)
- [UI Foundation 模块](../modules/viewcompose-ui-foundation/README.md)
- [Renderer 模块](../modules/viewcompose-renderer-android/README.md)
- [迁移图片加载](../migration/image-loading.md)
