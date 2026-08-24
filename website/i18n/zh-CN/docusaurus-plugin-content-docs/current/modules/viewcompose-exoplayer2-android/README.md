---
translation_source: modules/viewcompose-exoplayer2-android/README.md
translation_source_hash: 8104f4470c9e9441b07b715b1f3a2505c186b8bf3bf9de5137fbd2f5000a7e3f
translation_status: current
---

# 旧版 ExoPlayer 2 集成

`viewcompose-exoplayer2-android` 使用 `StyledPlayerView` 托管调用方持有的旧版
`com.google.android.exoplayer2.Player`。它是冻结兼容模块，不是 Media3 别名。

## 产物

```kotlin
implementation("com.viewcompose:viewcompose-exoplayer2-android:0.1.0-alpha01")
```

Alpha：`ExoPlayerView` Q3、配置 Q2、枚举 Q1；API 24+；已停止维护的 SDK 2.19.1。Core 对 API
可见，UI/Lifecycle 仅用于实现。可选产物不进入聚合，不依赖或别名化 Media3；Apache-2.0 记录在
`THIRD_PARTY_NOTICES.md`。新开发优先使用 Media3。

## 使用与契约

```kotlin
ExoPlayerView(player = ownedPlayer, surfaceType = ExoPlayerSurfaceType.SurfaceView)
```

调用方拥有 Player 的命令、状态和释放。必须提供最近的 `LocalLifecycleOwner`；挂载始于 `ON_START`，
跟随 resume/pause，并在 stop、Owner 变化、reset 或 release 时结束。解除挂载后再释放；后台音频
由应用决定。

`surfaceType` 是构造标识（`SurfaceView`、`TextureView`、`None`）。Surface 不变时替换 Player 会
清除旧输出并复用 View，其他状态可重放。不伪造旧 SDK 不支持的 Media3 控制器动画开关；首帧代际
在清理前失效。

## 验证与迁移

Media3/旧版可共存，隔离测试拒绝跨命名空间运行时类。2026-08-24，6 个模块测试与 46 个 Demo 测试
通过；1 个 API 31 真机测试在 2.016 秒内通过，替换 Player/Surface、切后台并返回后画面仍可见。
相比没有适配器，结论为 **improved**；证据仅覆盖一台设备的一份静音本地 H.264/AAC 样片，不对
编解码、特性、性能或功耗作结论。下一步：Maps。

先使用[上游迁移指南](https://developer.android.com/media/media3/exoplayer/migration-guide)，再以
`Media3PlayerView` 替换 `ExoPlayerView`；ViewCompose 不做转换。完整参考：
[`viewcompose-exoplayer2-android` API](https://docs.viewcompose.com/api/viewcompose-exoplayer2-android/current/)。
