---
translation_source: modules/viewcompose-camerax-androidx/README.md
translation_source_hash: fa5d4eeaee9f154fe951c7b95fac0c9c205a97c5ddf318a0476c9beb6735ba9b
translation_status: current
---

# CameraX AndroidX 集成

`viewcompose-camerax-androidx` 使用原生 `PreviewView` 托管一个 CameraX 1.6.1 `Preview`。它协调
预览 Surface 与最近的 AndroidX 生命周期，但不会申请权限、选择进程 provider 配置，也不会
干扰无关的 CameraX 用例。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-camerax-androidx:0.1.0-alpha01")

    // The application, not the integration, selects the CameraX hardware backend.
    implementation("androidx.camera:camera-camera2:1.6.1")
}
```

- 稳定性：**Alpha**。`CameraXPreviewView` 是引导型 Q3 生命周期/资源 API；
  `CameraXPreviewConfiguration` 与失败报告为 Q2；封闭策略枚举为 Q1。
- 平台：Android 7.0（API 24）及以上；compile SDK 36；Java 11 字节码。
- SDK 版本线：AndroidX CameraX 1.6.1。
- 可选性：该产物不会由 `viewcompose-android` 或设计系统聚合模块引入。
- API 可见依赖：`camera-core` 提供 `Camera`，`camera-lifecycle` 提供
  `ProcessCameraProvider`；`camera-view` 与 `viewcompose-lifecycle-androidx` 仍是实现依赖。

## 基本用法

在应用代码中解析权限和进程 provider，再把 provider 作为受控状态传入：

```kotlin
CameraXPreviewView(
    cameraProvider = resolvedProvider,
    lensFacing = CameraXLensFacing.Back,
    configuration = CameraXPreviewConfiguration(
        contentDescription = "Document camera preview",
    ),
    onFailure = { failure -> showCameraFailure(failure.reason) },
)
```

`ProcessCameraProvider.getInstance(context)` 尚未完成时，`resolvedProvider` 可以为 `null`。
此时组件不执行相机工作，并报告 `WaitingForProvider`。如果应用不采用 CameraX 默认值，应在
解析 provider 之前完成进程配置。模块不依赖 `camera-camera2`；应用可以明确选择该后端或
其他兼容 CameraX 配置。

## 所有权与生命周期

应用持有运行时权限、`ProcessCameraProvider`、全局 CameraX 配置、相机策略，以及所有拍摄或
分析用例。集成只创建并持有一个 `Preview` 绑定。它仅在 ViewCompose 成功提交后、且最近的
`LifecycleOwner` 至少处于 `STARTED` 时绑定；在停止、provider 或镜头替换、生命周期 owner
替换以及最终 View 释放之前，它会解绑这一个精确的 `Preview`。

调用 provider 前，集成会检查应用是否已经持有 `android.permission.CAMERA`。如果没有授权，
它会报告 `PermissionDenied`，但不会打开相机或弹出权限界面。集成从不调用 `unbindAll()`、
`shutdownAsync()` 或权限 API，因此不会静默移除调用方持有的 `ImageCapture`、
`ImageAnalysis` 或视频用例。首版刻意不组装多用例组合会话：如果应用需要预览与拍摄共享一个
原子的分辨率或效果策略，应继续持有完整 CameraX 会话，而不是混合单独绑定的用例。

`cameraProvider = null` 是等待状态，不是错误。绑定被拒绝时，组件会先执行有界清理，再报告
`Failed` 和 `onFailure`。失败原因区分缺少权限、相机选择不可用、用例冲突、不受支持的
provider 状态和未知 SDK 失败，同时保留原始异常。

## 配置、Surface 与回调

`CameraXPreviewConfiguration` 是可重放状态。缩放类型会立即更新 `PreviewView`；旋转变化会
原位更新活动 `Preview` 而不重新绑定，默认值跟随当前显示方向，并在布局变化后刷新；内容
描述提供原生预览的无障碍标签。

`CameraXPreviewImplementationMode.Compatible` 是安全默认值，选择 CameraX 中便于变换的
`TextureView` 路径。集成会把它挂载到专用裁剪宿主中，因此即便外围 ViewCompose 宿主为了装饰
效果允许视觉溢出，CameraX 的 Fill 变换也不会覆盖相邻声明式 UI。`Performance` 优先使用
CameraX 中合成开销更低的 `SurfaceView` 路径，但集成只允许它与 `FitCenter`、`FitStart` 或
`FitEnd` 组合。Fill 变换可能让外部 Surface 超出 `PreviewView`；Android 无法保证这类 Surface
在所有受支持设备上都被裁剪到声明式边界，因此不安全组合会在构造原生 View 前失败，而不会
覆盖相邻 UI。CameraX 要求在安装 Surface provider 之前选择实现模式，因此该参数属于构造身份，
变化时会替换原生 View。镜头变化会保留 `PreviewView`，但替换并精确解绑集成持有的 `Preview`。

所有回调都在 Android 主线程运行，并使用最近一次已提交的函数。每次成功绑定都会调用一次
`onCameraBound` 并交付可由调用方发出命令的 `Camera`；后续状态不再处于绑定时，不要继续使用
该对象。`onStreamStateChanged(Streaming)` 表示 CameraX 正向原生 Surface 交付帧，并不表示
已经识别了特定场景或视觉质量。

## Demo、Preview 与验证

Demo 路由 `camera.camerax-preview-view` 让权限申请保持显式，由 Activity 持有 provider 初始化，
并提供前后镜头与 Surface 模式开关。Compatible 模式使用 Fill 裁剪，Performance 模式使用 Fit
缩放，让 Demo 也能明确展示安全的 Surface 边界。静态 Preview 不提供 provider，只渲染有界
加载占位；Layoutlib 不会宣称输出了真机帧。

确定性 Robolectric 测试覆盖等待状态、可重放配置、构造替换、精确持有用例清理、镜头替换、
旋转、权限分类、回调失败和硬件后端隔离。真机验收另行覆盖拒绝权限、真实 Surface 帧流、
两个镜头、两种实现模式、前后台、显示旋转、Activity 重建、无障碍和已释放绑定标签。

### 已接受的 Pixel 4 XL 证据 — 2026-08-24

对比环境：Pixel 4 XL、Android 13、1440 × 3040、同一个 Debug APK 与 CameraX 1.6.1。被否决的
修复前 Compatible + Fill 实现中，声明目标为 `[84,1693][1356,2673]`，但 `TextureView` 暴露到
`[83,1334][1357,3032]`：上下各越界 359 px，左右各越界 1 px，并实际覆盖状态文字和人工验收
说明。加入集成自有裁剪宿主后，Compatible + Fill 和 Performance + Fit 在同一目标外的渲染子
节点都为 0；受影响边从 4 条降为 0 条，几何越界消除 100%。人工截图同时确认两段文字完整可见、
Compatible 填满目标、Performance 只在目标内留黑，且前后镜头帧画面不同。

拒绝权限链路通过 1/1，且没有打开相机；授权链路通过 1/1，覆盖真实帧流、两个镜头、
Compatible 到 Performance 的 View 替换、旧绑定精确释放、停止/恢复、旋转、Activity 重建、
无障碍和渲染 Surface 边界。结论：**improved（已改善）**。限制：这里只覆盖一个 CameraX/OS/
设备组合与较暗的静止场景，不是功耗、延迟、画质或广泛 OEM 基准。下一步：把两条真机链路保留
为发版门禁；产物离开 Alpha 前增加第二家 OEM；如果 `Performance` 成为产品要求，再单独测量
功耗和延迟。

## 相关文档

- [Host Android 模块](../viewcompose-host-android/README.md)
- [Lifecycle AndroidX 模块](../viewcompose-lifecycle-androidx/README.md)
- [Android View 教程](../../tutorials/android-view.md)
- [源代码文档与 API 注释规范](../../project/api-documentation-quality.md)

完整参考：[`viewcompose-camerax-androidx` API](https://docs.viewcompose.com/api/viewcompose-camerax-androidx/current/)。
