---
translation_source: modules/viewcompose-google-maps-android/README.md
translation_source_hash: de80d428b4e5bff0115e7f2b27ff2ca1b1d2ebd0dddacb7996ee74a3a5853c45
translation_status: current
---

# Google Maps Android 集成

`viewcompose-google-maps-android` 在 ViewCompose 中托管 Google Maps SDK 20.0.0 `MapView`。
模块负责原生 View 生命周期、状态保存桥接、可重放地图配置，以及带键的 Marker 和 Polyline
协调；凭据、数据、网络与隐私策略仍由应用负责。

## 产物与稳定性

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/TutorialDependencySnippets.kt" region="google-maps-dependency" sample_id="module.google-maps-dependency" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-google-maps-android:0.1.0-alpha01")
}
```

- 稳定性：**Alpha**。`GoogleMapView` 及其 Scope 为 Q3，状态类型为 Q2，封闭枚举为 Q1。
- 平台：Android 7.0（API 24）及以上；compile SDK 36；Google Maps SDK 20.0.0。
- 依赖：`viewcompose-host-android` 与 Maps SDK 模型类型对 API 可见；
  `viewcompose-lifecycle-androidx` 仅用于实现。该可选模块不进入聚合产物。

## 配置与使用

按照 Google 的凭据与限制指南，在应用 Manifest 中配置 Maps SDK API Key。模块不会读取、保存
或初始化凭据。

{/* compiled-region source="viewcompose-google-maps-android/src/test/samples/com/viewcompose/maps/google/samples/GoogleMapSamples.kt" region="google-map-view" sample_id="module.google-map-view" build_target=":viewcompose-google-maps-android:compileDebugUnitTestKotlin" */}
```kotlin
fun UiTreeBuilder.googleMapViewSample() {
    val office = LatLng(31.2304, 121.4737)
    GoogleMapView(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        properties = GoogleMapProperties(
            colorScheme = GoogleMapColorScheme.FollowSystem,
            cameraPosition = CameraPosition.fromLatLngZoom(office, 13f),
            contentDescription = "Office map",
        ),
        uiSettings = GoogleMapUiSettings(zoomControlsEnabled = true),
        saveableStateKey = "office-map",
    ) {
        googleMapContentSample()
    }
}
```

必须提供最近的 `LocalLifecycleOwner`。设置 `saveableStateKey` 时还必须提供最近的
`LocalSavedStateRegistryOwner`，同一 Owner 内的键必须唯一。适配器会严格一次转发 create、
start、resume、pause、stop、destroy、低内存与状态保存事件。Owner、保存键或
`GoogleMapViewOptions` 变化会替换原生 View；普通属性、UI 设置、回调、Marker 和 Polyline
变化会复用它。

## 所有权与更新契约

`GoogleMapProperties` 和 `GoogleMapUiSettings` 是受控输入。地图异步就绪后会重放最新值，此后
按差异更新。非空相机会立即移动且不执行动画；需要持续受控时，应用必须保存手势产生的临时位置。
`styleJson` 通过 `onMapStyleApplied` 返回 SDK 是否接受。

Scope 内的 Marker 和 Polyline 由适配器持有，按键分别更新，缺失时移除；重复键会在组合阶段失败。
`onMapReady` 可访问原生 `GoogleMap` 以实现未封装能力，但不得在 View 释放后继续持有，也不得覆盖
适配器持有的监听器或托管图层。定位权限与“我的位置”图层属于应用策略。

{/* compiled-region source="viewcompose-google-maps-android/src/test/samples/com/viewcompose/maps/google/samples/GoogleMapSamples.kt" region="google-map-content" sample_id="module.google-map-content" build_target=":viewcompose-google-maps-android:compileDebugUnitTestKotlin" */}
```kotlin
fun GoogleMapScope.googleMapContentSample() {
    val office = LatLng(31.2304, 121.4737)
    Marker(
        key = "office",
        position = office,
        style = GoogleMapMarkerStyle(title = "Office"),
    )
    Polyline(
        key = "walking-route",
        points = listOf(office, LatLng(31.2320, 121.4770)),
    )
}
```

模块不选择渲染器、不调用 `MapsInitializer`、不申请权限、不提供网络降级，也不伪造 Wear 环境模式
事件；应用应在挂载 View 前配置这些全局能力。Manifest 将 `org.apache.http.legacy` 声明为可选，
以符合 Maps SDK 20.0.0 对旧版 Google Play 服务的兼容指引。

## 验证

无凭据单元测试与 Robolectric 测试覆盖生命周期顺序、过期异步回调、低内存、状态保存、View 替换、
受控差异更新、Callback Identity、带键图层清理、校验与释放。2026-08-24，16 个模块测试和 46 个
Demo 测试全部通过。一条 Pixel 4 XL / API 33 无 Key 真机方法在 0.874 秒内通过。人工检查了操作前后
两张 1440 x 3040 截图：配色/城市状态、重置、重建、控件、占位与说明均正确。

Pixel 有凭据路径使用只通过本地 Gradle 用户属性提供、受 Package 与证书限制的外部 Key。最终真机
方法在 19.422 秒内通过，使用 LATEST Renderer 与远程 `maps_core` 260830204。测试证明真实瓦片加载、
每个原生实例仅回调一次 `onMapLoaded`、JSON Style 被接受、同一 `MapView` 上的 Camera/Marker/
Polyline 变化、后台返回后复用、Activity 重建后创建新 View、旧 View Binding 已清理、UI Context
以及地图 Content Description。人工检查了浅色上海与深色杭州两张 1440 x 3040 截图；瓦片、图层、
控件、状态标签与布局均正确。

线程与 VM `StrictMode` 覆盖首次 Composition 与全部测试更新，最终运行中归属于集成代码的违规为零。
控制进入 Google SDK 后，Google Maps 自身记录了 18 次 `IncorrectContextUseViolation` 与 5 次
`UntaggedSocketViolation`，即使适配器提供的是 UI Context；这是明确记录的第三方限制，不能静默计入
ViewCompose 成功。相比没有适配器，结论为 **improved**。证据证明集成正确性与有界 Release 清理，
但不包含全堆泄漏分析、帧性能、功耗、Renderer 对比、离线、权限或定位图层结论。该方法依赖网络，
其耗时不作为性能测量。

Demo 仅在应用构建属性 `viewComposeMapsApiKey` 配置后启用真实地图。凭据始终由应用持有，模块不会
提交凭据。

完整参考：[`viewcompose-google-maps-android` API](https://docs.viewcompose.com/api/viewcompose-google-maps-android/current/)。
