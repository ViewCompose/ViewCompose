---
translation_source: guides/theming-dynamic-color.md
translation_source_hash: 42557205adeaec9907f37f910b286332841f226f9629e3a1f7b52686c43a34f6
translation_status: current
---

# 启用 Material 3 动态颜色

标准 Material Android Host 是首选集成，因为它会为原生树、Overlay 和框架 token 解析同一个
Context。应用希望在支持的 Android 版本使用壁纸派生颜色时，请使用本任务。token 所有权和
Renderer 隔离规则见[主题架构](../architecture/theming.md)。

## 选择 Host 策略

`UseIfAvailable` 是默认值。显式声明可让产品策略更清楚：

{/* compiled-region source="samples/tutorials/src/main/java/com/viewcompose/samples/tutorials/ThemingGuideSamples.kt" region="material3-dynamic-color" sample_id="guide.theming-dynamic-color" build_target=":samples:tutorials:compileDebugKotlin" */}
```kotlin
class DynamicColorGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setMaterial3UiContent(
            dynamicColorPolicy = Material3DynamicColorPolicy.UseIfAvailable,
        ) {
            Text("Dynamic Material color")
        }
    }
}
```

产品需要固定 Android XML 调色板时选择 `Disabled`。在不支持动态颜色的平台上，
`UseIfAvailable` 会保留已经解析的 Material 主题和静态降级规则，应用代码无需增加另一套版本
分支。

## 保持唯一的解析 Context

`setMaterial3UiContent` 会先解析主题 Context，再用它创建原生子节点、`AndroidView`、默认
Overlay 和 `Material3Theme`。低层自定义 Host 必须通过 `Material3ThemeBridge.resolveContext`
遵循同一规则，并从 `resolvedTheme.context` 创建所有相关 View。用解析后的 Wrapper 读取 token，
却从原始 Activity Context 构建 View，属于无效集成。

标准 Host 观察 Configuration 变化并推进 `Environment.resourceRevision`。随后
`Material3Theme` 刷新稳定 Wrapper 并映射新的不可变快照。不要在 Material 层增加第二个
Configuration Callback。

## 刷新命令式资源变化

有些 Locale Wrapper、Theme Overlay 或 `setTheme` 调用会改变资源，却不派发 Host 所观察的
Configuration Callback。向 `setMaterial3UiContent` 传入一个 `AndroidResourceRefreshController`，
应用资源变化后在主线程调用其 `refresh()`。该 Controller 会按顺序同时刷新 Host 环境和
Material Wrapper。

只有未安装标准 Android 资源环境的自定义低层 Host 才使用 `Material3ThemeRefreshController`。
普通 Material Android Host 中不能再建立一条平行刷新路径。

## 验证任务

通过 `./gradlew :samples:tutorials:compileDebugKotlin` 编译，然后验证：

1. 在 Android 12 或更高版本修改壁纸颜色并重建 Activity；根 Surface、框架控件、
   `AndroidView` 和 Overlay 必须使用同一个完整动态调色板。
2. 在更旧或不支持的设备上，确认配置的 Material XML/静态降级仍然可读，并且没有动态颜色
   异常。
3. 切换明暗配置，确认 `Theme.current.metadata.revision` 推进，且不会混合新旧 token 家族。
4. 应用一个命令式测试主题或 Locale 变化，调用 Host Resource Controller，确认原生资源和框架
   token 在同一根节点同步更新。

Overlay 与页面调色板分裂、`AndroidView` 过期、重复刷新工作或后台线程刷新，都属于集成失败。
