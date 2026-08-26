---
title: 迁移图片加载
slug: /migration/image-loading
translation_source: migration/image-loading.md
translation_source_hash: 4fd2be6f2440c46cf6c3192f133a0bc47fff57778fbcaa5f64e9fe917186392b
translation_status: current
---

# 迁移图片加载

通用图片管线用平台无关的 source 和 request 契约替换了旧的仅远程协议。为旧 loader 实现过
协议或保存过旧 request 类型的应用需要进行源码和二进制不兼容迁移。

## API 映射

| 旧 API | 当前 API | 迁移动作 |
| --- | --- | --- |
| `RemoteImageLoader` | `UiImageLoader` | 实现 `load(UiImageTarget, UiImageRequest)`，返回可释放句柄。 |
| `RemoteImageRequest` | `UiImageRequest` 加 node fallback | 映射 source、placeholder、error、content scale 和 `UiImageRequestOptions`；无 source fallback 保留在 `Image`、`Icon` 或 `IconButton` 上。 |
| `RemoteImageTarget` | `UiImageTarget` | 接收通用 target，并在适配器中验证平台对象。 |
| `PlatformRemoteImageTarget` | `PlatformUiImageTarget` | 使用通用 platform target 包装器。 |
| `ProvideRemoteImageLoader` | `ProvideImageLoader` | 在最小图片子树外层安装 loader。 |
| `CoilRemoteImageLoader` | `CoilImageLoaderAdapter` | 替换适配器，并保持传入 Coil `ImageLoader` 由调用方所有。 |
| `ImageSource.Remote(url)` | `ImageSource.Url(url)` | URL 使用 `Url`；其他来源使用 `Uri`、`File`、`Resource` 或带 key 的 `Model`。 |

## 迁移前后

旧代码的概念形式如下：

{/* non-executable sample_id="migration.image-loading-before" reason="Removed remote-only APIs cannot compile against released current artifacts." visible_explanation="This conceptual source-only baseline identifies the names that must be replaced; do not copy it into current code." */}
```kotlin
ProvideRemoteImageLoader(CoilRemoteImageLoader(imageLoader)) {
    Image(source = ImageSource.Remote(url))
}
```

通用形式如下：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ImageLoadingGuideSamples.kt" region="image-migration-generalized" sample_id="migration.image-loading-generalized" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
ProvideImageLoader(CoilImageLoaderAdapter(imageLoader)) {
    Image(
        source = ImageSource.Url(url),
        requestOptions = UiImageRequestOptions(
            decodeSize = UiImageDecodeSize.Target,
        ),
    )
}
```

`CoilImageLoaderAdapter` 有意不提供 `Context` 构造函数。如果旧代码使用
`CoilRemoteImageLoader(context)`，请创建或获取一个应用级 Coil `ImageLoader`，传给适配器，
并仅在应用所有者结束时关闭它。

非 URL 来源应选择对应类型，不要再次编码成 URL：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ImageLoadingGuideSamples.kt" region="image-migration-model" sample_id="migration.image-loading-model" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
Image(source = ImageSource.Model(value = model, stableKey = modelId))
```

`ImageSource.Url` 现在验证绝对 HTTP(S) URL；其他绝对 scheme 使用 `ImageSource.Uri`。显式解码
尺寸通过 `UiImageDecodeSize.Fixed(width, height)` 使用 `UiDp`；Renderer 把捕获的密度放入
`UiImageRequest`，再由适配器转换为平台像素。`UiImageRequest` 有意不包含 fallback，因为空
source 不会启动 request。

## 适配器责任

适配器必须映射它声明支持的所有 source 类型，并为确实启动的操作返回句柄。释放必须幂等。
不要关闭注入的解码器，不要在释放后继续持有挂载 View，也不要把任意 model payload 当作框架
身份进行比较。仅消费适配器拥有的扩展类型，并忽略其他扩展类型。

如果应用没有某种 source 的解码器，应让 source 可为空或提供 resource fallback。没有适配器时，
只使用 resource 的图片仍然可以工作。

## 发布迁移顺序

1. 同时更新 UI Contract 和 Widget 的 import。
2. 替换 provider 与适配器名称。
3. 把 `ImageSource.Remote` 调用点改成最具体的当前 source 类型。
4. 如果旧适配器依赖隐式尺寸、缓存或过渡策略，补充 request options。
5. 运行 Renderer 生命周期测试和回收行手工验证路径。
6. 只有在仓库内生产引用全部消失后，才删除旧协议声明。

[图片加载指南](../guides/image-loading.md)详细说明所有权和释放规则；[Image Coil 模块手册](../modules/viewcompose-image-coil/README.md)
说明已发布适配器的兼容性边界。
