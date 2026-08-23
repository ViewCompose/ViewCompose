---
translation_source: modules/viewcompose-preview-runner/README.md
translation_source_hash: 368b358b4305b7a73b3843a23dbe3481a757081d116950c56bbc66fe4ce0f110
translation_status: current
---

# Preview Runner 模块

`viewcompose-preview-runner` 是 ViewCompose 确定性静态预览的 Android 执行层。它负责解析已编译的
预览入口、创建符合预览配置的 Android Context、把 DSL 挂载为原生 View 层级、捕获不可变的图片与
诊断产物，并在导出后释放帧级所有者。

## 产物与稳定性

```kotlin
dependencies {
    implementation("com.viewcompose:viewcompose-preview-runner:0.1.0-alpha04")
}
```

- 稳定性：**Alpha**。这是预览工具基础设施，不是应用运行时依赖。
- 运行环境：设备宿主、Paparazzi 或隔离 Layoutlib Worker 中的 Android API 24 及以上。
- 常规安装：由 ViewCompose 预览 Gradle 插件和 Worker Host 解析；应用模块通常只依赖预览注解。
- 公开 API 依赖：preview-core 提供协议，`viewcompose-preview` 提供可选的应用主题 Provider 契约。

## 执行管线

`PreviewJvmEntryPointResolver` 使用传入的应用 ClassLoader 加载描述符声明的类。合法入口必须是唯一且
无歧义的 public static JVM 方法，只接收一个 `UiTreeBuilder` receiver，并返回 `Unit`。可选的应用主题
Provider 通过 Kotlin `INSTANCE` 字段或 public 无参构造函数创建。

随后 `StaticPreviewRenderer.mount` 校验描述符与 API 级别的一致性，解析 Android 配置和主题，安装
Lifecycle、ViewModel、可保存状态、环境与主题所有者，同步渲染并布局原生层级。返回的
`StaticPreviewFrame` 持有这些资源，必须关闭。

`StaticPreviewWorker` 在请求输出目录生成 `preview.png` 和 `render-tree.json`。临时文件以原子方式替换，
Gradle 和 Studio 不会读取到只写了一部分的产物。响应会记录入口解析、挂载/布局、图片导出和快照导出的
耗时。

## 配置与主题一致性

`PreviewAndroidContextFactory` 把 density、字体比例、视口尺寸、语言、布局方向和亮/暗模式同步到
Android 资源配置。Renderer 会把相同值安装到关闭观察的 `AndroidResourceEnvironment`，使原生
View、资源查询函数、限定符、Android View 互操作和 DSL 使用同一份确定性配置。静态帧由 Preview
Descriptor 负责替换，不会由运行时 Configuration Callback 修改。

当描述符指定 `PreviewThemeProvider` 时，其 Context 和 `UiThemeTokens` 是权威结果。否则 Android 主题
桥接会在关闭动态色的前提下解析配置 Context，使 Studio、Gradle 与 CI 的结果可复现。请求指定 API
级别时，Worker 的 Android API 必须精确匹配。

## 尺寸与捕获

固定高度请求按配置视口测量。自动高度请求会先布局一个真实视口，再只扩展那些会随根节点增长的可滚动
后代。扩展同时受共享最大 dp 高度和 1600 万像素捕获预算限制；未完整展开或触及预算时会产生说明性
警告。

`AndroidBitmapCaptureBackend` 把已测量的 View 绘制到 ARGB Bitmap，并输出无损 PNG。其他宿主可以
实现 `StaticPreviewCaptureBackend`，但原子发布产物和生成响应仍由 Worker 负责。

## 诊断与源码映射

不可变快照包含渲染统计、VNode 与原生 View 树、Patch 记录、组合 Scope 与失效原因、源码调用点、
捕获的 View 属性、裁剪状态和布局诊断。协议模型不会持有运行时 View。

Runner 会安装一棵使用 `Preview` 角色、Tree 级别的 `RenderDiagnostics` 根。它消费权威的
`RenderFrameCompleted.tree` 与 `RenderFailureObserved.failure` 事件；已移除的 Result 和
Failure 回调不存在 Runner 专用兼容路径。

预期内的发现、主题、渲染、布局、捕获与导出失败会转为带源码位置的 `RenderFailure` 响应。线程终止和
内存溢出会继续抛出，让 Worker Host 退役进程。借用的应用 ClassLoader 不会被安装为线程上下文
ClassLoader，也不会由 Runner 关闭。

## 测试与扩展规则

- 使用和生产预览任务相同的 Layoutlib API、资源、density 与主题 Provider 输入测试 Runner。
- 每个成功帧都必须关闭，包括只检查快照的测试。
- 自定义捕获后端必须同步完成，并在返回前失败，不能留下半成品图片。
- 扩展发现逻辑时保持描述符相等校验与精确 JVM 签名要求。
- 协议数据先加入 preview-core；序列化模型不能暴露活的 Android 对象。
- 覆盖固定高度、自动高度、嵌套固定滚动容器、捕获预算、RTL、语言、字体比例、应用主题和每个失败阶段。

## 相关文档

- [Preview Core 模块](/modules/viewcompose-preview-core/)
- [Preview Worker Host 模块](/modules/viewcompose-preview-worker-host/)
- [Preview Gradle Plugin 模块](/modules/viewcompose-preview-gradle-plugin/)
- [源码文档与 API 注释规范](/project/api-documentation-quality/)

完整生成参考见
[`viewcompose-preview-runner` API 树](https://docs.viewcompose.com/api/viewcompose-preview-runner/current/)。

## 兼容性说明

`0.1.0-alpha03` 建立了精确编译入口校验、配置与主题一致性、帧级 Android 所有者、有界自动高度测量、
原子 PNG/快照导出和不可变诊断。预览协议兼容性仍由 preview-core 统一管理。
