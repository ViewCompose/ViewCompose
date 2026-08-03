---
translation_source: modules/viewcompose-image-coil/README.md
translation_source_hash: 810653b803ccd85dfb3cab34ceca132d9aa759f1acfc6217fb65509c32f5925b
translation_status: current
---

# Coil 图像加载模块

`viewcompose-image-coil` 是 ViewCompose 远程图片节点的可选 Coil 3 适配器。它把平台无关的加载
契约转换为 Android `ImageView` 请求，同时避免 Renderer 或 Widget 模块依赖具体网络与图片加载实现。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-image-coil:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。适配器边界已经建立，请求策略遵循 Coil 3。
- 平台：Android 7.0（API 24）及以上。
- 可选：没有该产物时，本地资源与核心 Renderer 仍可工作。
- 它依赖 `viewcompose-ui-contract` 与 `viewcompose-renderer`，二者不会反向依赖它。

## 安装

使用应用级 Coil `ImageLoader` 创建一个 `CoilRemoteImageLoader`，并把它传给负责远程图片加载的
ViewCompose Host 或 Renderer 配置。共享 Loader 能保留应用网络配置，并最大化内存与磁盘缓存复用。

便捷的 `Context` 构造函数会创建专用 Loader。它适合小型集成；需要集中管理生命周期或缓存时，
调用方应注入自己的 Loader。

## 请求与目标模型

Renderer 只为非空远程 URL 调用适配器。每个请求包含 URL，以及可选的 Android Placeholder、Error
和 Fallback 资源 ID。适配器只接受暴露 Android `ImageView` 的 Renderer Target，其他平台对象会被
安全忽略。

加载是异步的。Coil 直接把结果写入目标 View，并负责感知 Target 的请求替换与取消。因此同一个
`ImageView` 重新绑定时，过期任务保护交给 Coil。ViewCompose 契约有意不暴露请求句柄。

## 缓存与所有权

内存缓存、磁盘缓存、网络行为、Transformation 与 URL 解释都属于 Coil 策略。适配器不会增加第二层
缓存，也不会合成 Cache Key。调用方传入的 `ImageLoader` 仍归调用方所有，
`CoilRemoteImageLoader` 永远不会关闭它。

资源 ID 会原样转发。无效资源与请求失败遵循 Android 和 Coil 的普通 Error/Fallback 行为。

## 测试与运维

- 复用一个应用级 Loader，让跨页面缓存行为保持确定。
- 测试快速重新绑定或回收列表项，验证旧请求不会覆盖新数据。
- 分别覆盖 Placeholder、Error、Fallback、空 URL 与离线路径。
- 在注入的 Coil Loader 上配置认证、Interceptor、缓存预算与可观测性，而不是放进 ViewCompose。

## 相关文档

- [UI Contract 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-ui-contract)
- [Renderer 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-renderer)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-image-coil` API 树](https://docs.viewcompose.com/api/viewcompose-image-coil/current/)。

## 兼容性说明

`0.1.0-alpha01` 直接把平台无关请求转发给 Coil 3。它不会在声明式图片契约中暴露 Coil
Transformation，不管理全局 Loader，也不承诺独立于所配置 Coil 版本的缓存策略。
