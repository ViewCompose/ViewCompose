---
translation_source: modules/viewcompose-media3-androidx/README.md
translation_source_hash: ad71ee66b88f4f36616aecc987aa081e16b48e8a9521c82506680876992365d8
translation_status: current
---

# Media3 AndroidX 集成

`viewcompose-media3-androidx` 使用原生 `PlayerView` 托管由调用方持有的 AndroidX Media3
`Player`。它负责 View 挂载、监听器、视频 Surface 和最近 AndroidX 生命周期之间的协同，但不接管
播放控制或 Player 释放职责。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="media3-dependency" sample_id="module.media3-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-media3-androidx:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。`Media3PlayerView` 是需要指导使用的 Q3 生命周期/资源 API；
  `Media3PlayerViewConfiguration` 为 Q2；封闭策略枚举为 Q1。
- 平台：Android 7.0（API 24）及以上。
- SDK 版本：AndroidX Media3 1.10.1。Media3 1.11.0 使用 Kotlin 2.2 元数据编译，无法由本项目的
  Kotlin 2.0 编译器消费；变更该版本必须单独进行项目工具链兼容性审查。
- 可选：`viewcompose-android` 和设计系统聚合产物均不包含本模块。
- `media3-common` 会暴露给 API，因为组件参数使用 `Player`；`media3-ui` 和
  `viewcompose-lifecycle-androidx` 保持为实现依赖。

## 基本用法

{/* compiled-region source="viewcompose-media3-androidx/src/test/samples/com/viewcompose/media3/samples/Media3Samples.kt" region="media3-player" sample_id="module.media3-player" build_target=":viewcompose-media3-androidx:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.media3PlayerViewSample(player: Player) {
    Media3PlayerView(
        player = player,
        surfaceType = Media3SurfaceType.SurfaceView,
        configuration = Media3PlayerViewConfiguration(
            useController = true,
            showBuffering = Media3ShowBuffering.WhenPlaying,
            contentDescription = "Episode video",
        ),
        onRenderedFirstFrame = {
            // Update caller-owned UI state or diagnostics here.
        },
    )
}
```

调用方负责创建、配置、控制并最终释放 `Player`。集成层不会调用 `play`、`pause`、`stop` 或
`release`。只有在 Activity、Fragment View 或嵌套 ViewCompose Host 结束、集成层已经移除监听器和
Surface 后，调用方才应释放 Player。按照 `PlayerView` 要求，Player 的应用 Looper 必须是 Android
主 Looper。

## 生命周期与标识

最近的 `LocalLifecycleOwner` 是必需项。集成层在 `ON_START` 挂载已提交 Player，转发
`PlayerView.onResume()` 与 `onPause()`，并在 `ON_STOP`、Owner 替换、挂载树重置或 View 永久释放时
移除监听器、Player 引用和视频输出。因此，仅仅因为 Activity 仍处于 resumed 状态，隐藏且保留的导航
目的地也不能继续占用视频 Surface。后台音频与 Service/Session 策略仍属于调用方。

`surfaceType` 是原生构造标识。在 `SurfaceView`、`TextureView` 与 `None` 之间切换会原子替换
`PlayerView`，不会借助 reset 或可变 SDK 字段模拟。默认使用 `SurfaceView`，因为 Media3 针对低功耗、
帧时序、HDR 和安全输出推荐它；只有变换或动画需要时才选择 `TextureView`。Surface 类型不变时替换
Player 会复用原生 View，并先解除前一个 Player。

## 可重放配置与回调

`Media3PlayerViewConfiguration` 是完整的可重放状态，覆盖缩放模式、控制器启用与超时/可见策略、
缓冲显示、Artwork、Shutter 颜色、内容保留、无障碍描述、常亮行为和自定义错误文案。构造校验会在
View 工作开始前拒绝负数控制器超时。

`onRenderedFirstFrame` 只为当前已提交且 started 的挂载安装，并在 Android 主线程执行。替换、
stop、reset 和 release 会先使旧监听器失效，再开始任何新挂载。回调可以更新调用方状态，但不能阻塞
分发或持有框架 Scope。

## Demo、Preview 与验证

Demo 路由 `media.media3-player-view` 使用两个由 Activity 持有的 ExoPlayer 和仓库自有的两秒 MP4
资源；其元数据记录生成方式、编解码信息、所有权和 SHA-256。同一页面提供不含 Player 的静态 Preview
占位，Preview 不会启动解码或网络工作。

Robolectric 覆盖 started 状态挂载、Player 替换、精确原生 Surface 选择、完整配置、首帧回调失效、
清理和调用方释放所有权。真机验收还覆盖本地首帧、两种 Surface、后台/前台重新挂载，以及每次转换后
视频仍然可见。

## 相关文档

- [Host Android 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-host-android)
- [Lifecycle AndroidX 模块](https://docs.viewcompose.com/zh-CN/modules/viewcompose-lifecycle-androidx)
- [Android View 教程](https://docs.viewcompose.com/zh-CN/tutorials/android-view)
- [源码文档与 API 注释规范](https://docs.viewcompose.com/zh-CN/project/api-documentation-quality)

完整生成参考位于
[`viewcompose-media3-androidx` API 树](https://docs.viewcompose.com/api/viewcompose-media3-androidx/current/)。
